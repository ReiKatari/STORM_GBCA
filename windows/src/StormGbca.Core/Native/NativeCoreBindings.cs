// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Apache License, Version 2.0.

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

    // Palette Info
    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern int storm_gbca_get_dmg_palette_count();

    [DllImport(DllName, CallingConvention = CallingConvention.Cdecl)]
    public static extern byte* storm_gbca_get_dmg_palette_name(int index);
}
