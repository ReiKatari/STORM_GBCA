// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Apache License, Version 2.0.

using System.Buffers.Binary;
using System.Diagnostics;
using System.IO.Compression;
using System.Net.Sockets;
using StormGbca.Core.Audio;
using StormGbca.Core.Configuration;
using StormGbca.Core.Input;
using StormGbca.Core.Library;
using StormGbca.Core.Native;
using StormGbca.Core.Paths;
using StormGbca.Core.Subsystems;

namespace StormGbca.Core.Engine;

public readonly record struct GbcaLinkStatusInfo(
    StormLinkMode Mode,
    bool Connected,
    int PlayerId,
    int PlayerCount,
    int PingMs,
    int Port,
    string RoomName,
    string PeerInfo,
    string StatusText
);

public readonly record struct GbcaLanRoomInfo(
    string HostIp,
    int Port,
    StormLinkMode Mode,
    StormPlatform Platform,
    int PlayerCount,
    int MaxPlayers,
    string RoomName,
    string GameTitle
);

public readonly record struct GbcaFrameMetrics(
    int Width,
    int Height,
    float TargetFps,
    double MeasuredFps,
    double FrameTimeMs,
    float RumbleIntensity,
    int RewindAvailableFrames,
    string ActiveCore
);

/// <summary>
/// Thread-safe emulation session driving storm_gbca_core.dll at 59.7275 Hz with Stopwatch frame pacing,
/// WinMM 48kHz audio output, XInput gamepad + rumble forwarding, 10 savestate slots, auto-SRAM flush,
/// IPS/UPS/BPS soft patching, rewind, run-ahead, fast-forward 1x..8x, and sensors.
/// </summary>
public sealed unsafe class GbcaEmulatorSession : IDisposable
{
    public const double NominalGbaFps = 59.7275;
    public const int SavestateSlotCount = 10;

    private readonly object _coreLock = new();
    private readonly object _frameLock = new();
    private readonly WinMmAudioOutput _audioOut = new();
    private readonly XInputGamepadService _gamepad = new();
    private readonly short[] _audioScratch = new short[4096 * 2];

    private nint _ctx;
    private Thread? _runnerThread;
    private volatile bool _stopRequested;
    private volatile bool _isPaused;
    private volatile bool _isRunning;

    private uint _keyboardMask;
    private uint _overlayMask;
    private float _fastForwardMult = 1.0f;
    private bool _fastForwardActive;

    private byte[] _frameBgra = new byte[240 * 160 * 4];
    private int _frameWidth = 240;
    private int _frameHeight = 160;
    private float _targetFps = (float)NominalGbaFps;
    private long _frameCounter;
    private double _measuredFps = NominalGbaFps;
    private double _lastFrameMs = 16.74;
    private float _lastRumble;
    private string _activeCoreName = "mGBA";
    private string _activeRomKey = string.Empty;
    private string _activeSramPath = string.Empty;
    private bool _sramLoaded;

    public bool IsRunning => _isRunning;
    public bool IsPaused => _isPaused;
    public bool IsFastForward => _fastForwardActive;
    public float FastForwardMultiplier => _fastForwardMult;
    public nint NativeContext => _ctx;
    public StormPlatform ActivePlatform { get; private set; } = StormPlatform.None;
    public StormHwFlags ActiveHardwareFlags { get; private set; } = StormHwFlags.None;
    public string ActiveRomKey => _activeRomKey;
    public XInputGamepadService Gamepad => _gamepad;

    public event Action<GbcaFrameMetrics>? FrameCompleted;
    public event Action<string>? StatusMessage;

    public GbcaEmulatorSession()
    {
        if (NativeCoreBindings.IsAvailable)
        {
            _ctx = NativeCoreBindings.storm_gbca_create();
        }
    }

    public static IReadOnlyList<string> GetDmgPaletteNames()
    {
        var list = new List<string>();
        if (!NativeCoreBindings.IsAvailable)
        {
            list.Add("DMG Olive (Classic)");
            return list;
        }
        int count = NativeCoreBindings.storm_gbca_get_dmg_palette_count();
        for (int i = 0; i < count; i++)
        {
            string name = NativeCoreBindings.GetDmgPaletteNameString(i);
            list.Add(string.IsNullOrWhiteSpace(name) ? $"Palette {i + 1}" : name);
        }
        return list;
    }

    public void ApplySettings(GbcaAppSettings settings, bool hardcoreActive = false)
    {
        if (_ctx == nint.Zero || !NativeCoreBindings.IsAvailable) return;

        StormGbcaConfig cfg = default;
        NativeCoreBindings.storm_gbca_get_default_config(&cfg);

        cfg.preferred_core_gb = (int)settings.PreferredCoreGb;
        cfg.preferred_core_gbc = (int)settings.PreferredCoreGbc;
        cfg.preferred_core_gba = (int)settings.PreferredCoreGba;
        cfg.gb_model = (int)settings.GbModel;
        cfg.sgb_borders = settings.SgbBorders ? 1 : 0;
        cfg.color_correction_mode = (int)settings.ColorCorrectionMode;
        cfg.dmg_palette_index = settings.DmgPaletteIndex;
        for (int i = 0; i < 4; i++)
        {
            cfg.custom_dmg_palette[i] = settings.CustomDmgPalette[i];
        }
        cfg.lcd_ghosting = Math.Clamp(settings.LcdGhosting, 0, 100);
        cfg.video_filter = (int)settings.VideoFilter;
        cfg.audio_highpass_mode = (int)settings.AudioHighpassMode;
        cfg.audio_interp_mode = (int)settings.AudioInterpMode;
        for (int i = 0; i < 6; i++)
        {
            cfg.channel_volumes[i] = Math.Clamp(settings.ChannelVolumes[i], 0f, 1.5f);
        }
        cfg.master_volume = Math.Clamp(settings.MasterVolume, 0f, 2.0f);
        cfg.run_ahead_frames = Math.Clamp(settings.RunAheadFrames, 0, 2);
        cfg.rewind_enabled = (settings.RewindEnabled && !hardcoreActive) ? 1 : 0;
        cfg.rewind_buffer_mb = Math.Clamp(settings.RewindBufferMb, 4, 128);
        cfg.rewind_interval = Math.Clamp(settings.RewindInterval, 1, 10);
        cfg.skip_bios_intro = settings.SkipBiosIntro ? 1 : 0;
        cfg.dynamic_rate_control = settings.DynamicRateControl ? 1 : 0;
        cfg.drc_max_skew = Math.Clamp(settings.DrcMaxSkew, 0.001f, 0.05f);

        if (File.Exists(settings.BiosGbPath)) cfg.SetBiosGbPath(settings.BiosGbPath);
        if (File.Exists(settings.BiosGbcPath)) cfg.SetBiosGbcPath(settings.BiosGbcPath);
        if (File.Exists(settings.BiosGbaPath)) cfg.SetBiosGbaPath(settings.BiosGbaPath);
        if (File.Exists(settings.BiosSgbPath)) cfg.SetBiosSgbPath(settings.BiosSgbPath);

        lock (_coreLock)
        {
            NativeCoreBindings.storm_gbca_set_config(_ctx, &cfg);
            NativeCoreBindings.storm_gbca_set_upscale(_ctx, Math.Clamp(settings.ResolutionScale, 1, 4), (int)settings.UpscaleMode);
            NativeCoreBindings.storm_gbca_set_performance(_ctx, Math.Clamp(settings.CpuOverclockPct, 50, 400), Math.Clamp(settings.FrameSkip, 0, 4));
            NativeCoreBindings.storm_gbca_set_solar_lux(_ctx, settings.SolarLuxLevel);
            NativeCoreBindings.storm_gbca_set_rtc(_ctx, settings.UseHostRtc ? 1 : 0, 0);
            RefreshVideoInfoLocked();
        }

        _audioOut.IsMuted = settings.AudioMuted;
        _audioOut.Volume = 1.0f;
        _fastForwardMult = Math.Clamp(settings.FastForwardMultiplier, 1f, 8f);
    }

    public bool LoadAndStartRom(
        GbcaRomRecord rom,
        GbcaAppSettings settings,
        GbcaCheatService cheatService,
        RetroAchievementsService raService)
    {
        Stop();
        if (_ctx == nint.Zero || !File.Exists(rom.FilePath)) return false;

        ApplySettings(settings, raService.IsHardcoreMode);

        int rc = -1;
        StormGbcaRomInfo info = default;
        bool isZip = string.Equals(Path.GetExtension(rom.FilePath), ".zip", StringComparison.OrdinalIgnoreCase);

        lock (_coreLock)
        {
            if (isZip)
            {
                if (!GbcaRomLibrary.TryExtractRomFromZip(rom.FilePath, out byte[] zipRomBytes, out string innerEntryName))
                {
                    return false;
                }
                fixed (byte* p = zipRomBytes)
                {
                    rc = NativeCoreBindings.storm_gbca_load_rom_buffer(_ctx, p, (nuint)zipRomBytes.Length, innerEntryName);
                }
            }
            else
            {
                rc = NativeCoreBindings.storm_gbca_load_rom_file(_ctx, rom.FilePath);
                if (rc != 0)
                {
                    byte[] raw = File.ReadAllBytes(rom.FilePath);
                    fixed (byte* p = raw)
                    {
                        rc = NativeCoreBindings.storm_gbca_load_rom_buffer(_ctx, p, (nuint)raw.Length, rom.FileName);
                    }
                }
            }
            if (rc != 0) return false;

            // Auto-apply matching .ips / .ups / .bps soft patch if present alongside ROM
            TryAutoApplyAdjacentPatchLocked(rom.FilePath);

            NativeCoreBindings.storm_gbca_get_rom_info(_ctx, &info);
            ActivePlatform = (StormPlatform)info.platform;
            ActiveHardwareFlags = (StormHwFlags)info.hw_flags;
            _activeCoreName = info.GetActiveCore();
            _activeRomKey = !string.IsNullOrWhiteSpace(rom.FileName) ? rom.FileName : rom.GameCode;
            _activeSramPath = StormGbcaPaths.GetSaveFilePath(_activeRomKey);

            if (File.Exists(_activeSramPath))
            {
                _sramLoaded = NativeCoreBindings.storm_gbca_load_sram_file(_ctx, _activeSramPath) == 0;
            }
            else
            {
                _sramLoaded = false;
            }

            RefreshVideoInfoLocked();
            NativeCoreBindings.storm_gbca_set_solar_lux(_ctx, settings.SolarLuxLevel);
            NativeCoreBindings.storm_gbca_set_rtc(_ctx, settings.UseHostRtc ? 1 : 0, 0);

            bool cheatsAllowed = raService.IsFeatureAllowedInCurrentMode("cheats");
            cheatService.LoadCheatsForRom(_activeRomKey);
            cheatService.ApplyToNative(_ctx, cheatsAllowed);
        }

        _frameCounter = 0;
        _isPaused = false;
        _fastForwardActive = false;
        _stopRequested = false;
        _isRunning = true;
        _audioOut.BeginHighResTimer();

        _runnerThread = new Thread(EmulationLoop)
        {
            IsBackground = true,
            Name = "STORM_GBCA_EmuLoop",
            Priority = ThreadPriority.AboveNormal
        };
        _runnerThread.Start();
        return true;
    }

    public bool LoadRomBufferForTest(byte[] romBytes, string virtualName, StormCoreBackend preferredCore = StormCoreBackend.Mgba)
    {
        if (_ctx == nint.Zero || romBytes.Length == 0) return false;
        lock (_coreLock)
        {
            StormGbcaConfig cfg = default;
            NativeCoreBindings.storm_gbca_get_default_config(&cfg);
            cfg.preferred_core_gb = (int)preferredCore;
            cfg.preferred_core_gbc = (int)preferredCore;
            cfg.preferred_core_gba = (int)preferredCore;
            cfg.rewind_enabled = 1;
            NativeCoreBindings.storm_gbca_set_config(_ctx, &cfg);

            int rc;
            fixed (byte* p = romBytes)
            {
                rc = NativeCoreBindings.storm_gbca_load_rom_buffer(_ctx, p, (nuint)romBytes.Length, virtualName);
            }
            if (rc != 0) return false;

            StormGbcaRomInfo info = default;
            NativeCoreBindings.storm_gbca_get_rom_info(_ctx, &info);
            ActivePlatform = (StormPlatform)info.platform;
            _activeCoreName = info.GetActiveCore();
            RefreshVideoInfoLocked();
            return true;
        }
    }

    public bool RunSingleFrameForTest(out int width, out int height)
    {
        width = _frameWidth;
        height = _frameHeight;
        if (_ctx == nint.Zero) return false;

        lock (_coreLock)
        {
            if (NativeCoreBindings.storm_gbca_run_frame(_ctx) != 0) return false;
            RefreshVideoInfoLocked();
            CopyFrameLocked();
            width = _frameWidth;
            height = _frameHeight;
            return true;
        }
    }

    public void Stop()
    {
        _stopRequested = true;
        _isRunning = false;
        if (_runnerThread != null && _runnerThread.IsAlive)
        {
            _runnerThread.Join(1500);
        }
        _runnerThread = null;
        _gamepad.StopRumble();
        _audioOut.ResetQueue();
        _audioOut.EndHighResTimer();

        lock (_coreLock)
        {
            FlushSramLocked();
            if (_ctx != nint.Zero)
            {
                NativeCoreBindings.storm_gbca_unload_rom(_ctx);
            }
        }
    }

    public void SetPaused(bool paused)
    {
        _isPaused = paused;
        if (paused)
        {
            _gamepad.StopRumble();
            _audioOut.ResetQueue();
            lock (_coreLock)
            {
                FlushSramLocked();
            }
        }
    }

    public void Reset()
    {
        lock (_coreLock)
        {
            if (_ctx != nint.Zero && _isRunning)
            {
                FlushSramLocked();
                NativeCoreBindings.storm_gbca_reset(_ctx);
            }
        }
        StatusMessage?.Invoke("Консоль перезапущена");
    }

    public void SetKeyboardMask(uint mask) => _keyboardMask = mask;
    public void SetOverlayMask(uint mask) => _overlayMask = mask;

    public void SetFastForward(bool enabled, float multiplier = 3.0f)
    {
        _fastForwardActive = enabled;
        _fastForwardMult = Math.Clamp(multiplier, 1.0f, 8.0f);
        lock (_coreLock)
        {
            if (_ctx != nint.Zero)
            {
                NativeCoreBindings.storm_gbca_set_fast_forward(_ctx, enabled ? _fastForwardMult : 1.0f);
            }
        }
    }

    public void SetSolarLux(int lux0To10)
    {
        lock (_coreLock)
        {
            if (_ctx != nint.Zero) NativeCoreBindings.storm_gbca_set_solar_lux(_ctx, Math.Clamp(lux0To10, 0, 10));
        }
    }

    public void SetTilt(float x, float y)
    {
        lock (_coreLock)
        {
            if (_ctx != nint.Zero) NativeCoreBindings.storm_gbca_set_tilt(_ctx, x, y);
        }
    }

    public void SetGyro(float z)
    {
        lock (_coreLock)
        {
            if (_ctx != nint.Zero) NativeCoreBindings.storm_gbca_set_gyro(_ctx, z);
        }
    }

    public bool ApplyPatchFile(string patchPath)
    {
        if (string.IsNullOrWhiteSpace(patchPath) || !File.Exists(patchPath)) return false;
        lock (_coreLock)
        {
            if (_ctx == nint.Zero || !_isRunning) return false;
            int rc = NativeCoreBindings.storm_gbca_apply_patch_file(_ctx, patchPath);
            if (rc == 0)
            {
                RefreshVideoInfoLocked();
                StatusMessage?.Invoke($"Применен патч: {Path.GetFileName(patchPath)}");
                return true;
            }
            return false;
        }
    }

    public bool SaveState(int slot)
    {
        if (slot < 0 || slot >= SavestateSlotCount || string.IsNullOrWhiteSpace(_activeRomKey)) return false;
        Directory.CreateDirectory(StormGbcaPaths.StatesDirectory);
        string path = StormGbcaPaths.GetSaveStateFilePath(_activeRomKey, slot);
        bool ok;
        lock (_coreLock)
        {
            if (_ctx == nint.Zero || !_isRunning) return false;
            ok = NativeCoreBindings.storm_gbca_save_state_file(_ctx, path) == 0;
            FlushSramLocked();
            if (ok)
            {
                CopyFrameLocked();
                try
                {
                    string thumbPath = StormGbcaPaths.GetSaveStateThumbnailPath(_activeRomKey, slot);
                    lock (_frameLock)
                    {
                        if (_frameBgra.Length >= _frameWidth * _frameHeight * 4 && _frameWidth > 0 && _frameHeight > 0)
                        {
                            WriteBgraPng(thumbPath, _frameBgra, _frameWidth, _frameHeight);
                        }
                    }
                }
                catch
                {
                }
            }
        }
        if (ok) StatusMessage?.Invoke($"Состояние сохранено в слот {slot + 1}");
        return ok;
    }

    public bool LoadState(int slot)
    {
        if (slot < 0 || slot >= SavestateSlotCount || string.IsNullOrWhiteSpace(_activeRomKey)) return false;
        string path = StormGbcaPaths.GetSaveStateFilePath(_activeRomKey, slot);
        if (!File.Exists(path)) return false;
        lock (_coreLock)
        {
            if (_ctx == nint.Zero || !_isRunning) return false;
            bool ok = NativeCoreBindings.storm_gbca_load_state_file(_ctx, path) == 0;
            if (ok)
            {
                CopyFrameLocked();
                StatusMessage?.Invoke($"Состояние загружено из слота {slot + 1}");
            }
            return ok;
        }
    }

    public bool RewindStep()
    {
        lock (_coreLock)
        {
            if (_ctx == nint.Zero || !_isRunning) return false;
            if (NativeCoreBindings.storm_gbca_rewind_step(_ctx) == 0)
            {
                CopyFrameLocked();
                return true;
            }
            return false;
        }
    }

    public int GetRewindAvailableFrames()
    {
        lock (_coreLock)
        {
            return _ctx != nint.Zero ? NativeCoreBindings.storm_gbca_rewind_available_frames(_ctx) : 0;
        }
    }

    public int CopyCurrentFrameBgra(ref byte[] dst, out int width, out int height)
    {
        lock (_frameLock)
        {
            width = _frameWidth;
            height = _frameHeight;
            int need = width * height * 4;
            if (dst.Length != need) dst = new byte[need];
            Buffer.BlockCopy(_frameBgra, 0, dst, 0, Math.Min(need, _frameBgra.Length));
            return need;
        }
    }

    private void EmulationLoop()
    {
        var sw = Stopwatch.StartNew();
        double nextFrameDueSec = sw.Elapsed.TotalSeconds;
        long fpsSampleStartTicks = sw.ElapsedTicks;
        int fpsSampleFrames = 0;
        int sramFlushTick = 0;

        while (!_stopRequested)
        {
            if (_isPaused)
            {
                Thread.Sleep(16);
                nextFrameDueSec = sw.Elapsed.TotalSeconds;
                continue;
            }

            double frameStartMs = sw.Elapsed.TotalMilliseconds;
            int steps = _fastForwardActive ? Math.Clamp((int)Math.Round(_fastForwardMult), 2, 8) : 1;

            uint gpMask = _gamepad.PollButtonMask(out float stickX, out float stickY);
            uint combinedMask = _keyboardMask | _overlayMask | gpMask;

            int rwAvail = 0;
            float rumble = 0f;

            lock (_coreLock)
            {
                if (_ctx == nint.Zero || _stopRequested) break;

                NativeCoreBindings.storm_gbca_set_keys(_ctx, combinedMask);
                if (_gamepad.IsConnected && (Math.Abs(stickX) > 0.1f || Math.Abs(stickY) > 0.1f))
                {
                    NativeCoreBindings.storm_gbca_set_tilt(_ctx, stickX, -stickY);
                }

                for (int s = 0; s < steps; s++)
                {
                    NativeCoreBindings.storm_gbca_run_frame(_ctx);
                }

                // Drain audio & push to WinMM (discard ring cleanly during >2x fast-forward to prevent stall/crackle)
                NativeCoreBindings.storm_gbca_set_audio_buffer_level(_ctx, _audioOut.GetQueueFillRatio());
                fixed (short* aPtr = _audioScratch)
                {
                    bool submitAudio = !_fastForwardActive || steps <= 2;
                    while (true)
                    {
                        int gotFrames = NativeCoreBindings.storm_gbca_read_audio(_ctx, aPtr, 4096);
                        if (gotFrames <= 0) break;
                        if (submitAudio)
                        {
                            _audioOut.SubmitSamples(_audioScratch.AsSpan(0, gotFrames * 2), gotFrames);
                        }
                        if (gotFrames < 4096) break;
                    }
                }

                CopyFrameLocked();
                rumble = NativeCoreBindings.storm_gbca_get_rumble(_ctx);
                _lastRumble = rumble;
                _gamepad.SetRumble(rumble);

                rwAvail = NativeCoreBindings.storm_gbca_rewind_available_frames(_ctx);

                sramFlushTick++;
                if (sramFlushTick % 30 == 0)
                {
                    PollGbPrinterLocked();
                }
                if (sramFlushTick >= 180)
                {
                    sramFlushTick = 0;
                    FlushSramLocked();
                }
            }

            _frameCounter++;
            fpsSampleFrames++;
            _lastFrameMs = sw.Elapsed.TotalMilliseconds - frameStartMs;

            double elapsedFpsWindow = (double)(sw.ElapsedTicks - fpsSampleStartTicks) / Stopwatch.Frequency;
            if (elapsedFpsWindow >= 0.5)
            {
                _measuredFps = fpsSampleFrames / elapsedFpsWindow;
                fpsSampleFrames = 0;
                fpsSampleStartTicks = sw.ElapsedTicks;
            }

            FrameCompleted?.Invoke(new GbcaFrameMetrics(
                _frameWidth,
                _frameHeight,
                _targetFps,
                _measuredFps,
                _lastFrameMs,
                rumble,
                rwAvail,
                _activeCoreName));

            // High-precision Stopwatch 59.7275 Hz frame pacing
            double stepPeriod = 1.0 / Math.Max(30.0, _targetFps);
            nextFrameDueSec += stepPeriod;
            double now = sw.Elapsed.TotalSeconds;
            if (now - nextFrameDueSec > 0.1)
            {
                nextFrameDueSec = now;
            }

            while (true)
            {
                double rem = nextFrameDueSec - sw.Elapsed.TotalSeconds;
                if (rem <= 0 || _stopRequested) break;
                if (rem > 0.0025)
                {
                    Thread.Sleep(1);
                }
                else
                {
                    Thread.SpinWait(64);
                }
            }
        }
    }


    // -----------------------------------------------------------------------
    // Multiplayer / Link Cable / Wireless RFU / IR / Dolphin JoyBus / GB Printer
    // -----------------------------------------------------------------------
    public Task<bool> StartLinkAsync(StormLinkMode mode, string hostIp, int port, int clockPort, string roomName)
        => Task.Run(() => StartLink(mode, hostIp, port, clockPort, roomName));

    public bool StartLink(StormLinkMode mode, string hostIp, int port, int clockPort, string roomName)
    {
        if (mode is StormLinkMode.LanClient or StormLinkMode.WirelessClient or StormLinkMode.IrClient or StormLinkMode.DolphinJoyBus)
        {
            string targetIp = string.IsNullOrWhiteSpace(hostIp) ? "127.0.0.1" : hostIp.Trim();
            int targetPort = port is > 0 and < 65535 ? port : (mode == StormLinkMode.DolphinJoyBus ? 54970 : 54320);
            if (!ProbeTcpEndpoint(targetIp, targetPort, 450))
            {
                StatusMessage?.Invoke($"Link timeout: {targetIp}:{targetPort}");
                return false;
            }
        }

        lock (_coreLock)
        {
            if (_ctx == nint.Zero || !NativeCoreBindings.IsAvailable) return false;
            int rc = NativeCoreBindings.storm_gbca_link_start(
                _ctx,
                (int)mode,
                hostIp ?? string.Empty,
                port,
                clockPort,
                roomName ?? "STORM GBCA Room");
            if (rc == 0)
            {
                StatusMessage?.Invoke($"Link active: {mode}");
                return true;
            }
            StatusMessage?.Invoke($"Link error ({rc}): {mode}");
            return false;
        }
    }

    private static bool ProbeTcpEndpoint(string hostIp, int port, int timeoutMs)
    {
        try
        {
            using var client = new TcpClient();
            var ar = client.BeginConnect(hostIp, port, null, null);
            if (!ar.AsyncWaitHandle.WaitOne(timeoutMs))
            {
                return false;
            }
            client.EndConnect(ar);
            return true;
        }
        catch
        {
            return false;
        }
    }

    public void StopLink()
    {
        lock (_coreLock)
        {
            if (_ctx != nint.Zero && NativeCoreBindings.IsAvailable)
            {
                NativeCoreBindings.storm_gbca_link_stop(_ctx);
            }
        }
        StatusMessage?.Invoke("Link disconnected");
    }

    public GbcaLinkStatusInfo GetLinkStatus()
    {
        lock (_coreLock)
        {
            if (_ctx == nint.Zero || !NativeCoreBindings.IsAvailable)
            {
                return new GbcaLinkStatusInfo(StormLinkMode.None, false, 0, 0, 0, 0, "", "", "Disconnected");
            }
            StormGbcaLinkStatus st = default;
            if (NativeCoreBindings.storm_gbca_link_get_status(_ctx, &st) == 0)
            {
                return new GbcaLinkStatusInfo(
                    (StormLinkMode)st.mode,
                    st.connected != 0,
                    st.player_id,
                    st.player_count,
                    st.ping_ms,
                    st.port,
                    st.GetRoomName(),
                    st.GetPeerInfo(),
                    st.GetStatusText());
            }
            return new GbcaLinkStatusInfo(StormLinkMode.None, false, 0, 0, 0, 0, "", "", "Disconnected");
        }
    }

    public List<GbcaLanRoomInfo> DiscoverLanRooms(int timeoutMs = 1200)
    {
        var result = new List<GbcaLanRoomInfo>();
        if (!NativeCoreBindings.IsAvailable) return result;
        StormGbcaLanRoom[] rooms = new StormGbcaLanRoom[16];
        fixed (StormGbcaLanRoom* p = rooms)
        {
            int count = NativeCoreBindings.storm_gbca_lan_discover(p, rooms.Length, Math.Clamp(timeoutMs, 200, 4000));
            for (int i = 0; i < count; i++)
            {
                result.Add(new GbcaLanRoomInfo(
                    rooms[i].GetHostIp(),
                    rooms[i].port,
                    (StormLinkMode)rooms[i].mode,
                    (StormPlatform)rooms[i].platform,
                    rooms[i].player_count,
                    rooms[i].max_players,
                    rooms[i].GetRoomName(),
                    rooms[i].GetGameTitle()));
            }
        }
        return result;
    }

    private uint[]? _printerBuf;
    private void PollGbPrinterLocked()
    {
        if (_ctx == nint.Zero || !NativeCoreBindings.IsAvailable) return;
        _printerBuf ??= new uint[160 * 512];
        int w = 0, h = 0;
        fixed (uint* p = _printerBuf)
        {
            int got = NativeCoreBindings.storm_gbca_printer_poll_image(_ctx, p, _printerBuf.Length, &w, &h);
            if (got > 0 && w > 0 && h > 0 && w * h <= _printerBuf.Length)
            {
                try
                {
                    string dir = Path.Combine(StormGbcaPaths.ScreenshotsDirectory, "GBPrinter");
                    Directory.CreateDirectory(dir);
                    string file = Path.Combine(dir, $"gb_printer_{DateTime.Now:yyyyMMdd_HHmmss_fff}.png");
                    WriteRgbaPng(file, _printerBuf, w, h);
                    StatusMessage?.Invoke($"Game Boy Printer PNG: {Path.GetFileName(file)}");
                }
                catch { }
            }
        }
    }

    private static void WriteRgbaPng(string path, uint[] rgbaPixels, int w, int h)
    {
        WritePngCore(path, w, h, (raw, y) =>
        {
            int rowOff = y * (1 + w * 4);
            raw[rowOff] = 0; // filter None
            for (int x = 0; x < w; x++)
            {
                uint px = rgbaPixels[y * w + x];
                int o = rowOff + 1 + x * 4;
                raw[o] = (byte)(px & 0xFF);
                raw[o + 1] = (byte)((px >> 8) & 0xFF);
                raw[o + 2] = (byte)((px >> 16) & 0xFF);
                raw[o + 3] = (byte)((px >> 24) & 0xFF);
            }
        });
    }

    private static void WriteBgraPng(string path, byte[] bgraPixels, int w, int h)
    {
        WritePngCore(path, w, h, (raw, y) =>
        {
            int rowOff = y * (1 + w * 4);
            raw[rowOff] = 0;
            for (int x = 0; x < w; x++)
            {
                int srcOff = (y * w + x) * 4;
                int o = rowOff + 1 + x * 4;
                raw[o] = bgraPixels[srcOff + 2];     // R
                raw[o + 1] = bgraPixels[srcOff + 1]; // G
                raw[o + 2] = bgraPixels[srcOff];     // B
                raw[o + 3] = bgraPixels[srcOff + 3]; // A
            }
        });
    }

    private static void WritePngCore(string path, int w, int h, Action<byte[], int> fillRow)
    {
        using var fs = File.Create(path);
        fs.Write([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]);

        byte[] ihdr = new byte[13];
        BinaryPrimitives.WriteInt32BigEndian(ihdr.AsSpan(0, 4), w);
        BinaryPrimitives.WriteInt32BigEndian(ihdr.AsSpan(4, 4), h);
        ihdr[8] = 8; // bit depth
        ihdr[9] = 6; // RGBA
        WritePngChunk(fs, "IHDR", ihdr);

        byte[] raw = new byte[h * (1 + w * 4)];
        for (int y = 0; y < h; y++)
        {
            fillRow(raw, y);
        }
        using var compMs = new MemoryStream();
        using (var zs = new ZLibStream(compMs, CompressionLevel.Fastest, leaveOpen: true))
        {
            zs.Write(raw, 0, raw.Length);
        }
        WritePngChunk(fs, "IDAT", compMs.ToArray());
        WritePngChunk(fs, "IEND", []);
    }

    private static void WritePngChunk(Stream s, string type, byte[] data)
    {
        Span<byte> len = stackalloc byte[4];
        BinaryPrimitives.WriteInt32BigEndian(len, data.Length);
        s.Write(len);
        byte[] t = System.Text.Encoding.ASCII.GetBytes(type);
        s.Write(t);
        if (data.Length > 0) s.Write(data);
        uint crc = 0xFFFFFFFFu;
        crc = UpdateCrc32(crc, t);
        crc = UpdateCrc32(crc, data);
        crc ^= 0xFFFFFFFFu;
        Span<byte> cb = stackalloc byte[4];
        BinaryPrimitives.WriteUInt32BigEndian(cb, crc);
        s.Write(cb);
    }

    private static uint UpdateCrc32(uint crc, ReadOnlySpan<byte> buf)
    {
        foreach (byte b in buf)
        {
            crc ^= b;
            for (int k = 0; k < 8; k++)
            {
                crc = (crc & 1) != 0 ? (crc >> 1) ^ 0xEDB88320u : (crc >> 1);
            }
        }
        return crc;
    }

    public int MemSearchReset(StormMemoryRegion region, int valueSizeBytes)
    {
        lock (_coreLock)
        {
            return _ctx != nint.Zero ? NativeCoreBindings.storm_gbca_mem_search_reset(_ctx, (int)region, valueSizeBytes) : 0;
        }
    }

    public int MemSearchFilter(StormSearchCmp cmpOp, uint targetValue)
    {
        lock (_coreLock)
        {
            return _ctx != nint.Zero ? NativeCoreBindings.storm_gbca_mem_search_filter(_ctx, (int)cmpOp, targetValue) : 0;
        }
    }

    public List<(uint Offset, uint Value)> MemSearchGetResults(int maxResults = 64)
    {
        var list = new List<(uint, uint)>();
        lock (_coreLock)
        {
            if (_ctx == nint.Zero) return list;
            int cap = Math.Clamp(maxResults, 1, 256);
            uint* addrs = stackalloc uint[cap];
            uint* vals = stackalloc uint[cap];
            int count = NativeCoreBindings.storm_gbca_mem_search_get_results(_ctx, addrs, vals, cap);
            for (int i = 0; i < count; i++) list.Add((addrs[i], vals[i]));
        }
        return list;
    }

    public bool WriteMemoryValue(StormMemoryRegion region, uint offset, uint value, int sizeBytes)
    {
        lock (_coreLock)
        {
            if (_ctx == nint.Zero) return false;
            int sz = Math.Clamp(sizeBytes, 1, 4);
            byte* buf = stackalloc byte[4];
            buf[0] = (byte)(value & 0xFF);
            buf[1] = (byte)((value >> 8) & 0xFF);
            buf[2] = (byte)((value >> 16) & 0xFF);
            buf[3] = (byte)((value >> 24) & 0xFF);
            return NativeCoreBindings.storm_gbca_write_memory(_ctx, (int)region, offset, buf, (nuint)sz) == 0;
        }
    }

    private void RefreshVideoInfoLocked()
    {
        if (_ctx == nint.Zero) return;
        int w = 0, h = 0;
        float fps = 0f;
        if (NativeCoreBindings.storm_gbca_get_video_info(_ctx, &w, &h, &fps) == 0 && w > 0 && h > 0)
        {
            int upW = w, upH = h;
            NativeCoreBindings.storm_gbca_get_upscaled_size(_ctx, &upW, &upH);
            if (upW > 0 && upH > 0) { w = upW; h = upH; }
            lock (_frameLock)
            {
                _frameWidth = w;
                _frameHeight = h;
                _targetFps = fps > 10f ? fps : (float)NominalGbaFps;
                int bytes = w * h * 4;
                if (_frameBgra.Length != bytes) _frameBgra = new byte[bytes];
            }
        }
    }

    private void CopyFrameLocked()
    {
        if (_ctx == nint.Zero) return;
        int upW = 0, upH = 0;
        NativeCoreBindings.storm_gbca_get_upscaled_size(_ctx, &upW, &upH);
        lock (_frameLock)
        {
            int w = upW > 0 ? upW : _frameWidth;
            int h = upH > 0 ? upH : _frameHeight;
            _frameWidth = w;
            _frameHeight = h;
            int need = w * h * 4;
            if (_frameBgra.Length != need) _frameBgra = new byte[need];
            fixed (byte* dst = _frameBgra)
            {
                int outW = w, outH = h;
                NativeCoreBindings.storm_gbca_copy_upscaled_frame(_ctx, (uint*)dst, w, (int)StormPixelFormat.Bgra8888, &outW, &outH);
            }
        }
    }

    private void FlushSramLocked()
    {
        if (_ctx == nint.Zero || string.IsNullOrWhiteSpace(_activeSramPath)) return;
        if (NativeCoreBindings.storm_gbca_is_sram_dirty(_ctx) != 0 || !_sramLoaded)
        {
            Directory.CreateDirectory(StormGbcaPaths.SavesDirectory);
            if (NativeCoreBindings.storm_gbca_save_sram_file(_ctx, _activeSramPath) == 0)
            {
                _sramLoaded = true;
            }
        }
    }

    private void TryAutoApplyAdjacentPatchLocked(string romPath)
    {
        string dir = Path.GetDirectoryName(romPath) ?? string.Empty;
        string stem = Path.GetFileNameWithoutExtension(romPath);
        string[] exts = [".bps", ".ups", ".ips"];
        foreach (string ext in exts)
        {
            string candidate = Path.Combine(dir, stem + ext);
            if (File.Exists(candidate))
            {
                NativeCoreBindings.storm_gbca_apply_patch_file(_ctx, candidate);
                return;
            }
        }
    }

    public void Dispose()
    {
        Stop();
        _audioOut.Dispose();
        lock (_coreLock)
        {
            if (_ctx != nint.Zero)
            {
                NativeCoreBindings.storm_gbca_destroy(_ctx);
                _ctx = nint.Zero;
            }
        }
    }
}
