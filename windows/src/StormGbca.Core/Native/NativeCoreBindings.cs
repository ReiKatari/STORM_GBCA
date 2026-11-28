// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Mozilla Public License, Version 2.0 (MPL-2.0).

using System.Runtime.InteropServices;
using System.Text;

namespace StormGbca.Core.Native;

[Flags]
public enum StormKeyMask : uint
{
    None = 0,
    A = 1u << 0,
    B = 1u << 1,
    Select = 1u << 2,
    Start = 1u << 3,
    Right = 1u << 4,
    Left = 1u << 5,
    Up = 1u << 6,
    Down = 1u << 7,
    R = 1u << 8,
    L = 1u << 9,
}

public enum StormPlatform : int
{
    None = 0,
    Gb = 1,
    Gbc = 2,
    Gba = 3,
    Sgb = 4,
}

public enum StormCoreBackend : int
{
    Mgba = 0,
    SameBoy = 1,
}

public enum StormGbModel : int
{
    Auto = 0,
    Dmg = 1,
    Mgb = 2,
    Sgb = 3,
    Sgb2 = 4,
    Cgb = 5,
    Agb = 6,
}

public enum StormPixelFormat : int
{
    Rgba8888 = 0,
    Bgra8888 = 1,
}

public enum StormColorCorrectionMode : int
{
    Disabled = 0,
    CorrectCurves = 1,
    ModernBalanced = 2,
    ModernBoost = 3,
    ReduceContrast = 4,
    LowContrast = 5,
    ModernAccurate = 6,
    GbaAgbAgs = 7,
}

public enum StormVideoFilter : int
{
    Nearest = 0,
    Bilinear = 1,
    LcdGrid = 2,
    RgbSubpixel = 3,
    XbrzLite = 4,
    CrtScanlines = 5,
    SharpBilinear = 6,
    Fsr1 = 7,
    Fsr3 = 8,
}

public enum StormAudioHighpassMode : int
{
    Off = 0,
    Accurate = 1,
    RemoveDc = 2,
}

public enum StormAudioInterpMode : int
{
    Nearest = 0,
    Linear = 1,
    Hermite = 2,
}

[Flags]
public enum StormHwFlags : uint
{
    None = 0,
    Rtc = 1u << 0,
    Rumble = 1u << 1,
    Solar = 1u << 2,
    Gyro = 1u << 3,
    Tilt = 1u << 4,
    Sgb = 1u << 5,
    Cgb = 1u << 6,
}

public enum StormMemoryRegion : int
{
    CartRom = 0,
    CartRam = 1,
    Wram = 2,
    Iwram = 3,
    Ewram = 4,
    Vram = 5,
    Oam = 6,
    Hram = 7,
    Io = 8,
    SystemBus = 9,
}

public enum StormUpscaleMode : int
{
    None = 0,
    Xbrz = 1,
    ScaleEpx = 2,
    Mmpx = 3,
    LcdHd = 4,
    CrtHd = 5,
}

public enum StormSearchCmp : int
{
    Eq = 0,
    Neq = 1,
    Gt = 2,
    Lt = 3,
    Changed = 4,
    Unchanged = 5,
    Increased = 6,
    Decreased = 7,
}

public enum StormOptimizationProfile : int
{
    Lite = 0,
    Balanced = 1,
    Performance = 2,
    HighAccuracy = 3,
}

public enum StormLinkMode : int
{
    None = 0,
    LanHost = 1,
    LanClient = 2,
    WirelessHost = 3,
    WirelessClient = 4,
    IrHost = 5,
    IrClient = 6,
    DolphinJoyBus = 7,
    GbPrinter = 8,
    LocalLoopback = 9,
}

public enum StormRaEventType : int
{
    None = 0,
    AchTriggered = 1,
    AchPrimed = 2,
    AchUnprimed = 3,
    AchMeasured = 4,
    LboardStarted = 5,
    LboardUpdated = 6,
    LboardCanceled = 7,
    LboardTriggered = 8,
    AchReset = 9,
}

[StructLayout(LayoutKind.Sequential)]
public unsafe struct StormGbcaRaEvent
{
    public int type;
    public uint id;
    public int value;
    public int target;
    public fixed byte formatted[64];

    public readonly string GetFormatted()
    {
        fixed (byte* p = formatted) return StormGbcaRomInfo.ReadFixedUtf8(p, 64);
    }
}

[StructLayout(LayoutKind.Sequential)]
public unsafe struct StormGbcaLinkStatus
{
    public int mode;
    public int connected;
    public int player_id;
    public int player_count;
    public int ping_ms;
    public int port;
    public fixed byte room_name[64];
    public fixed byte peer_info[128];
    public fixed byte status_text[128];

    public readonly string GetRoomName() { fixed (byte* p = room_name) return StormGbcaRomInfo.ReadFixedUtf8(p, 64); }
    public readonly string GetPeerInfo() { fixed (byte* p = peer_info) return StormGbcaRomInfo.ReadFixedUtf8(p, 128); }
    public readonly string GetStatusText() { fixed (byte* p = status_text) return StormGbcaRomInfo.ReadFixedUtf8(p, 128); }
}

[StructLayout(LayoutKind.Sequential)]
public unsafe struct StormGbcaLanRoom
{
    public fixed byte host_ip[64];
    public int port;
    public int mode;
    public int platform;
    public int player_count;
    public int max_players;
    public fixed byte room_name[64];
    public fixed byte game_title[64];

    public readonly string GetHostIp() { fixed (byte* p = host_ip) return StormGbcaRomInfo.ReadFixedUtf8(p, 64); }
    public readonly string GetRoomName() { fixed (byte* p = room_name) return StormGbcaRomInfo.ReadFixedUtf8(p, 64); }
    public readonly string GetGameTitle() { fixed (byte* p = game_title) return StormGbcaRomInfo.ReadFixedUtf8(p, 64); }
}

[StructLayout(LayoutKind.Sequential)]
public unsafe struct StormGbcaRomInfo
{
    public int platform;
    public fixed byte title[64];
    public fixed byte game_code[16];
    public fixed byte maker[8];
    public fixed byte region[16];
    public uint crc32;
    public fixed byte md5[33];
    public uint rom_size;
    public uint hw_flags;
    public fixed byte active_core[16];

    public readonly string GetTitle()
    {
        fixed (byte* p = title) return ReadFixedUtf8(p, 64);
    }

    public readonly string GetGameCode()
    {
        fixed (byte* p = game_code) return ReadFixedUtf8(p, 16);
    }

    public readonly string GetMaker()
    {
        fixed (byte* p = maker) return ReadFixedUtf8(p, 8);
    }

    public readonly string GetRegion()
    {
        fixed (byte* p = region) return ReadFixedUtf8(p, 16);
    }

    public readonly string GetMd5()
    {
        fixed (byte* p = md5) return ReadFixedUtf8(p, 33);
    }

    public readonly string GetActiveCore()
    {
        fixed (byte* p = active_core) return ReadFixedUtf8(p, 16);
    }

    internal static string ReadFixedUtf8(byte* ptr, int maxLen)
    {
        int len = 0;
        while (len < maxLen && ptr[len] != 0) len++;
        return len == 0 ? string.Empty : Encoding.UTF8.GetString(ptr, len);
    }
}

[StructLayout(LayoutKind.Sequential)]
public unsafe struct StormGbcaAudioConfig
{
    public int highpass_mode;
    public int interp_mode;
    public fixed float channel_volumes[6];
    public float master_volume;
    public int dynamic_rate_control;
    public float drc_max_skew;
    public int target_sample_rate;
}

[StructLayout(LayoutKind.Sequential)]
public unsafe struct StormGbcaConfig
{
    public int preferred_core_gb;
    public int preferred_core_gbc;
    public int preferred_core_gba;
    public int gb_model;
    public int sgb_borders;
    public int color_correction_mode;
    public int dmg_palette_index;
    public fixed uint custom_dmg_palette[4];
    public int lcd_ghosting;
    public int video_filter;
    public int audio_highpass_mode;
    public int audio_interp_mode;
    public fixed float channel_volumes[6];
    public float master_volume;
    public int run_ahead_frames;
    public int rewind_enabled;
    public int rewind_buffer_mb;
    public int rewind_interval;
    public fixed byte bios_gb_path[512];
    public fixed byte bios_gbc_path[512];
    public fixed byte bios_gba_path[512];
    public fixed byte bios_sgb_path[512];
    public int skip_bios_intro;
    public int dynamic_rate_control;
    public float drc_max_skew;

    public void SetBiosGbPath(string? path)
    {
        fixed (byte* p = bios_gb_path) WriteFixedUtf8(p, 512, path);
    }

    public void SetBiosGbcPath(string? path)
    {
        fixed (byte* p = bios_gbc_path) WriteFixedUtf8(p, 512, path);
    }

    public void SetBiosGbaPath(string? path)
    {
        fixed (byte* p = bios_gba_path) WriteFixedUtf8(p, 512, path);
    }

    public void SetBiosSgbPath(string? path)
    {
        fixed (byte* p = bios_sgb_path) WriteFixedUtf8(p, 512, path);
    }

    private static void WriteFixedUtf8(byte* dst, int maxLen, string? value)
    {
        new Span<byte>(dst, maxLen).Clear();
        if (string.IsNullOrWhiteSpace(value)) return;
        byte[] utf8 = Encoding.UTF8.GetBytes(value.Trim());
        int count = Math.Min(utf8.Length, maxLen - 1);
        utf8.AsSpan(0, count).CopyTo(new Span<byte>(dst, count));
    }
}

/// <summary>
/// Exact P/Invoke bindings for storm_gbca_core.dll matching core/include/storm_gbca_core.h.
/// Uses CallingConvention.Cdecl, explicit UTF-8 marshaling, and zero bool marshaling bugs.
/// </summary>
public static unsafe class NativeCoreBindings
{
    public const string DllName = "storm_gbca_core";

    static NativeCoreBindings()
    {
        NativeLibrary.SetDllImportResolver(typeof(NativeCoreBindings).Assembly, (name, assembly, searchPath) =>
        {
            if (!string.Equals(name, DllName, StringComparison.OrdinalIgnoreCase))
            {
                return nint.Zero;
            }

            string baseDir = AppContext.BaseDirectory;
            string[] candidates =
            [
                Path.Combine(baseDir, "storm_gbca_core.dll"),
                Path.Combine(baseDir, "native", "storm_gbca_core.dll"),
                Path.Combine(baseDir, "runtimes", "win-x64", "native", "storm_gbca_core.dll"),
                @"F:\STORM GBCA\core\build_win\storm_gbca_core.dll"
            ];

            foreach (string candidate in candidates)
            {
                if (File.Exists(candidate) && NativeLibrary.TryLoad(candidate, out nint handle))
                {
                    return handle;
                }
            }

            return NativeLibrary.TryLoad(name, assembly, searchPath, out nint fallback) ? fallback : nint.Zero;
        });
    }

    public static bool IsAvailable
    {
        get
        {
            try
            {
                return !string.IsNullOrEmpty(GetVersionString());
            }
            catch
            {
                return false;
            }
        }
    }

    public static string GetVersionString()
    {
        byte* ptr = storm_gbca_version();
        if (ptr == null) return string.Empty;
        return Marshal.PtrToStringUTF8((nint)ptr) ?? string.Empty;
    }

    public static string GetDmgPaletteNameString(int index)
    {
        byte* ptr = storm_gbca_get_dmg_palette_name(index);
        if (ptr == null) return string.Empty;
        return Marshal.PtrToStringUTF8((nint)ptr) ?? string.Empty;
    }

    // Lifecycle
    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern byte* storm_gbca_version();

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern nint storm_gbca_create();

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern void storm_gbca_destroy(nint ctx);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern void storm_gbca_reset(nint ctx);

    // Configuration
    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern void storm_gbca_get_default_config(StormGbcaConfig* out_cfg);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_set_config(nint ctx, StormGbcaConfig* cfg);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_set_audio_config(nint ctx, StormGbcaAudioConfig* cfg);

    // ROM loading & inspection
    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_load_rom_file(nint ctx, [MarshalAs(UnmanagedType.LPUTF8Str)] string rom_path);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_load_rom_buffer(nint ctx, void* data, nuint size, [MarshalAs(UnmanagedType.LPUTF8Str)] string virtual_name);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern void storm_gbca_unload_rom(nint ctx);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_inspect_rom_file([MarshalAs(UnmanagedType.LPUTF8Str)] string rom_path, StormGbcaRomInfo* out_info);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_inspect_rom_buffer(void* data, nuint size, [MarshalAs(UnmanagedType.LPUTF8Str)] string virtual_name, StormGbcaRomInfo* out_info);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_get_rom_info(nint ctx, StormGbcaRomInfo* out_info);

    // IPS/UPS/BPS soft patching
    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_apply_patch_file(nint ctx, [MarshalAs(UnmanagedType.LPUTF8Str)] string patch_path);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_apply_patch_buffer(void* rom_data, nuint rom_size, void* patch_data, nuint patch_size, byte** out_patched, nuint* out_size);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern void storm_gbca_free_buffer(void* ptr);

    // Execution
    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern void storm_gbca_set_keys(nint ctx, uint mask);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_run_frame(nint ctx);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern void storm_gbca_set_fast_forward(nint ctx, float mult);

    // Video
    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_get_video_info(nint ctx, int* out_w, int* out_h, float* out_fps);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_copy_frame(nint ctx, uint* dst, int dst_stride_px, int pixel_format);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_get_raw_frame_rgba(nint ctx, uint** out_ptr, int* out_w, int* out_h);

    // Audio
    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_read_audio(nint ctx, short* dst_interleaved, int max_frames);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern void storm_gbca_set_audio_buffer_level(nint ctx, float fill_ratio);

    // Savestates & SRAM
    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_save_state_file(nint ctx, [MarshalAs(UnmanagedType.LPUTF8Str)] string path);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_load_state_file(nint ctx, [MarshalAs(UnmanagedType.LPUTF8Str)] string path);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_save_state_buffer(nint ctx, void* dst, nuint capacity, nuint* out_size);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_load_state_buffer(nint ctx, void* src, nuint size);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_save_sram_file(nint ctx, [MarshalAs(UnmanagedType.LPUTF8Str)] string path);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_load_sram_file(nint ctx, [MarshalAs(UnmanagedType.LPUTF8Str)] string path);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_is_sram_dirty(nint ctx);

    // Rewind
    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_rewind_step(nint ctx);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_rewind_available_frames(nint ctx);

    // Sensors & Peripherals
    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern void storm_gbca_set_solar_lux(nint ctx, int lux_0_to_10);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern void storm_gbca_set_tilt(nint ctx, float x, float y);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern void storm_gbca_set_gyro(nint ctx, float z);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern float storm_gbca_get_rumble(nint ctx);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern void storm_gbca_set_rtc(nint ctx, int use_host, long epoch);

    // Cheats
    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern void storm_gbca_cheats_clear(nint ctx);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_cheat_add(nint ctx, [MarshalAs(UnmanagedType.LPUTF8Str)] string name, [MarshalAs(UnmanagedType.LPUTF8Str)] string code, int enabled);

    // Memory & RetroAchievements
    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern nuint storm_gbca_read_memory(nint ctx, int region, uint addr, byte* dst, nuint len);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_compute_ra_hash([MarshalAs(UnmanagedType.LPUTF8Str)] string rom_path, int platform, byte* out_hash_33);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_compute_ra_hash_buffer(void* data, nuint size, int platform, byte* out_hash_33);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_get_loaded_ra_hash(nint ctx, byte[] outHash33);

    // Palette Info
    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_get_dmg_palette_count();

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern byte* storm_gbca_get_dmg_palette_name(int index);

    // Upscaling, Performance, Memory Write & RAM Cheat Finder
    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_write_memory(nint ctx, int region, uint addr, byte* src, nuint len);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern void storm_gbca_set_upscale(nint ctx, int scale_1_to_4, int upscale_mode);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_get_upscaled_size(nint ctx, int* out_w, int* out_h);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_copy_upscaled_frame(nint ctx, uint* dst, int dst_stride_px, int pixel_format, int* out_w, int* out_h);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern void storm_gbca_set_performance(nint ctx, int cpu_overclock_pct, int frame_skip);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_mem_search_reset(nint ctx, int region, int value_size_bytes);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_mem_search_filter(nint ctx, int cmp_op, uint target_value);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_mem_search_get_results(nint ctx, uint* out_addrs, uint* out_vals, int max_results);

    // Device Optimization & Full Multiplayer / Link Cable / Wireless / JoyBus / GB Printer
    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern void storm_gbca_set_optimization_profile(nint ctx, int profile);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_link_start(
        nint ctx,
        int mode,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string host_ip,
        int port,
        int clock_port,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string room_name);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern void storm_gbca_link_stop(nint ctx);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_link_get_status(nint ctx, StormGbcaLinkStatus* out_status);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_lan_discover(StormGbcaLanRoom* out_rooms, int max_rooms, int timeout_ms);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_printer_poll_image(nint ctx, uint* dst_rgba, int max_pixels, int* out_w, int* out_h);

    // -----------------------------------------------------------------------
    // RetroAchievements Hardcore Compliance & Live rcheevos Runtime
    // -----------------------------------------------------------------------
    public const int StormErrHardcoreBlocked = -10;

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_set_hardcore_mode(nint ctx, int enabled);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_get_hardcore_mode(nint ctx);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern void storm_gbca_ra_reset(nint ctx);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern void storm_gbca_ra_clear_all(nint ctx);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_ra_activate_achievement(nint ctx, uint id, [MarshalAs(UnmanagedType.LPUTF8Str)] string memaddr);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern void storm_gbca_ra_deactivate_achievement(nint ctx, uint id);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_ra_activate_leaderboard(nint ctx, uint id, [MarshalAs(UnmanagedType.LPUTF8Str)] string memaddr, [MarshalAs(UnmanagedType.LPUTF8Str)] string format);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern void storm_gbca_ra_deactivate_leaderboard(nint ctx, uint id);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_ra_activate_rich_presence(nint ctx, [MarshalAs(UnmanagedType.LPUTF8Str)] string script);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_ra_poll_event(nint ctx, StormGbcaRaEvent* out_event);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_ra_get_rich_presence(nint ctx, byte* out_buf, int buf_size);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_ra_get_achievement_measured(nint ctx, uint id, uint* out_val, uint* out_target);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_ra_get_achievement_state(nint ctx, uint id);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_ra_serialize_progress(nint ctx, byte* dst, int capacity);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_ra_deserialize_progress(nint ctx, byte* src, int size);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_ra_build_login_request(
        [MarshalAs(UnmanagedType.LPUTF8Str)] string username,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string password,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string api_token,
        byte* out_url, int url_cap, byte* out_post, int post_cap);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_ra_build_start_session_request(
        [MarshalAs(UnmanagedType.LPUTF8Str)] string username,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string api_token,
        uint game_id,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string game_hash,
        int hardcore,
        byte* out_url, int url_cap, byte* out_post, int post_cap);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_ra_build_award_request(
        [MarshalAs(UnmanagedType.LPUTF8Str)] string username,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string api_token,
        uint achievement_id,
        int hardcore,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string game_hash,
        byte* out_url, int url_cap, byte* out_post, int post_cap);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_ra_build_lboard_request(
        [MarshalAs(UnmanagedType.LPUTF8Str)] string username,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string api_token,
        uint lboard_id,
        int score,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string game_hash,
        byte* out_url, int url_cap, byte* out_post, int post_cap);

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_ra_build_ping_request(
        [MarshalAs(UnmanagedType.LPUTF8Str)] string username,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string api_token,
        uint game_id,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string rich_presence,
        [MarshalAs(UnmanagedType.LPUTF8Str)] string game_hash,
        int hardcore,
        byte* out_url, int url_cap, byte* out_post, int post_cap);

    public static bool PollRaEvent(nint ctx, out StormGbcaRaEvent ev)
    {
        ev = default;
        if (ctx == nint.Zero || !IsAvailable) return false;
        StormGbcaRaEvent tmp = default;
        if (storm_gbca_ra_poll_event(ctx, &tmp) != 0)
        {
            ev = tmp;
            return true;
        }
        return false;
    }

    public static string GetRichPresence(nint ctx)
    {
        if (ctx == nint.Zero || !IsAvailable) return string.Empty;
        byte* buf = stackalloc byte[512];
        int len = storm_gbca_ra_get_rich_presence(ctx, buf, 512);
        return len > 0 ? StormGbcaRomInfo.ReadFixedUtf8(buf, 512) : string.Empty;
    }

    public static string? GetLoadedRaHash(nint ctx)
    {
        if (ctx == nint.Zero || !IsAvailable) return null;
        byte[] buf = new byte[33];
        if (storm_gbca_get_loaded_ra_hash(ctx, buf) != 0) return null;
        int len = Array.IndexOf(buf, (byte)0);
        if (len < 0) len = buf.Length;
        if (len <= 0) return null;
        string hash = Encoding.UTF8.GetString(buf, 0, len);
        return hash.Length == 32 ? hash : null;
    }

    public static byte[] SerializeRaProgress(nint ctx)
    {
        if (ctx == nint.Zero || !IsAvailable) return [];
        int need = storm_gbca_ra_serialize_progress(ctx, null, 0);
        if (need <= 0) return [];
        byte[] buf = new byte[need];
        fixed (byte* p = buf)
        {
            int written = storm_gbca_ra_serialize_progress(ctx, p, buf.Length);
            if (written <= 0) return [];
            if (written < buf.Length) Array.Resize(ref buf, written);
            return buf;
        }
    }

    public static bool DeserializeRaProgress(nint ctx, byte[] data)
    {
        if (ctx == nint.Zero || data == null || data.Length == 0 || !IsAvailable) return false;
        fixed (byte* p = data)
        {
            return storm_gbca_ra_deserialize_progress(ctx, p, data.Length) == 0;
        }
    }

    public static bool BuildLoginRequest(string username, string password, string apiToken, out string url, out string postBody)
    {
        url = string.Empty;
        postBody = string.Empty;
        if (!IsAvailable) return false;
        byte[] uBuf = new byte[1024];
        byte[] pBuf = new byte[2048];
        fixed (byte* pu = uBuf)
        fixed (byte* pp = pBuf)
        {
            if (storm_gbca_ra_build_login_request(username ?? string.Empty, password ?? string.Empty, apiToken ?? string.Empty, pu, uBuf.Length, pp, pBuf.Length) != 0)
                return false;
            url = StormGbcaRomInfo.ReadFixedUtf8(pu, uBuf.Length);
            postBody = StormGbcaRomInfo.ReadFixedUtf8(pp, pBuf.Length);
            return !string.IsNullOrWhiteSpace(url);
        }
    }

    public static bool BuildStartSessionRequest(string username, string apiToken, uint gameId, string gameHash, bool hardcore, out string url, out string postBody)
    {
        url = string.Empty;
        postBody = string.Empty;
        if (!IsAvailable) return false;
        byte[] uBuf = new byte[1024];
        byte[] pBuf = new byte[2048];
        fixed (byte* pu = uBuf)
        fixed (byte* pp = pBuf)
        {
            if (storm_gbca_ra_build_start_session_request(username ?? string.Empty, apiToken ?? string.Empty, gameId, gameHash ?? string.Empty, hardcore ? 1 : 0, pu, uBuf.Length, pp, pBuf.Length) != 0)
                return false;
            url = StormGbcaRomInfo.ReadFixedUtf8(pu, uBuf.Length);
            postBody = StormGbcaRomInfo.ReadFixedUtf8(pp, pBuf.Length);
            return !string.IsNullOrWhiteSpace(url);
        }
    }

    public static bool BuildAwardRequest(string username, string apiToken, uint achievementId, bool hardcore, string gameHash, out string url, out string postBody)
    {
        url = string.Empty;
        postBody = string.Empty;
        if (!IsAvailable) return false;
        byte[] uBuf = new byte[1024];
        byte[] pBuf = new byte[2048];
        fixed (byte* pu = uBuf)
        fixed (byte* pp = pBuf)
        {
            if (storm_gbca_ra_build_award_request(username ?? string.Empty, apiToken ?? string.Empty, achievementId, hardcore ? 1 : 0, gameHash ?? string.Empty, pu, uBuf.Length, pp, pBuf.Length) != 0)
                return false;
            url = StormGbcaRomInfo.ReadFixedUtf8(pu, uBuf.Length);
            postBody = StormGbcaRomInfo.ReadFixedUtf8(pp, pBuf.Length);
            return !string.IsNullOrWhiteSpace(url);
        }
    }

    public static bool BuildLboardRequest(string username, string apiToken, uint lboardId, int score, string gameHash, out string url, out string postBody)
    {
        url = string.Empty;
        postBody = string.Empty;
        if (!IsAvailable) return false;
        byte[] uBuf = new byte[1024];
        byte[] pBuf = new byte[2048];
        fixed (byte* pu = uBuf)
        fixed (byte* pp = pBuf)
        {
            if (storm_gbca_ra_build_lboard_request(username ?? string.Empty, apiToken ?? string.Empty, lboardId, score, gameHash ?? string.Empty, pu, uBuf.Length, pp, pBuf.Length) != 0)
                return false;
            url = StormGbcaRomInfo.ReadFixedUtf8(pu, uBuf.Length);
            postBody = StormGbcaRomInfo.ReadFixedUtf8(pp, pBuf.Length);
            return !string.IsNullOrWhiteSpace(url);
        }
    }

    public static bool BuildPingRequest(string username, string apiToken, uint gameId, string richPresence, string gameHash, bool hardcore, out string url, out string postBody)
    {
        url = string.Empty;
        postBody = string.Empty;
        if (!IsAvailable) return false;
        byte[] uBuf = new byte[1024];
        byte[] pBuf = new byte[4096];
        fixed (byte* pu = uBuf)
        fixed (byte* pp = pBuf)
        {
            if (storm_gbca_ra_build_ping_request(username ?? string.Empty, apiToken ?? string.Empty, gameId, richPresence ?? string.Empty, gameHash ?? string.Empty, hardcore ? 1 : 0, pu, uBuf.Length, pp, pBuf.Length) != 0)
                return false;
            url = StormGbcaRomInfo.ReadFixedUtf8(pu, uBuf.Length);
            postBody = StormGbcaRomInfo.ReadFixedUtf8(pp, pBuf.Length);
            return !string.IsNullOrWhiteSpace(url);
        }
    }
}
