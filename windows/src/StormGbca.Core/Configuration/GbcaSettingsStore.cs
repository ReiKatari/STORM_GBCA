// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Apache License, Version 2.0.

using System.Text.Json;
using System.Text.Json.Serialization;
using StormGbca.Core.Native;
using StormGbca.Core.Paths;

namespace StormGbca.Core.Configuration;

public enum GbConsoleSkin
{
    None = 0,
    DmgClassicGray = 1,
    DmgPlayItLoudRed = 2,
    DmgPlayItLoudBlue = 3,
    DmgPlayItLoudYellow = 4,
    DmgPlayItLoudBlack = 5,
    PocketSilver = 6,
    GameBoyLightTeal = 7,
    StormNeonGb = 8,
}

public enum GbcConsoleSkin
{
    None = 0,
    AtomicPurple = 1,
    Berry = 2,
    Dandelion = 3,
    Kiwi = 4,
    Teal = 5,
    Grape = 6,
    MidnightGold = 7,
    StormNeonGbc = 8,
}

public enum GbaConsoleSkin
{
    None = 0,
    GbaIndigo = 1,
    GbaGlacier = 2,
    GbaArcticWhite = 3,
    GbaSpiceOrange = 4,
    GbaFuchsia = 5,
    GbaSpCobalt = 6,
    GbaSpOnyx = 7,
    GbaSpFlameRed = 8,
    GbmFamicom = 9,
    StormNeonGba = 10,
}

public enum OverlayBezelStyle
{
    None = 0,
    MinimalFrame = 1,
    ClassicBezel = 2,
    GlassDeck = 3,
    RetroPlastic = 4,
    StormNeonDeck = 5,
    CyberGrid = 6,
    PocketMono = 7,
}

public enum OverlayButtonStyle
{
    DmgMagenta = 0,
    GbcAtomicPurple = 1,
    GbaIndigo = 2,
    GbaGlacier = 3,
    GbaSpCobalt = 4,
    GbmFamicom = 5,
    StormNeon = 6,
    MatteDark = 7,
    CrystalGlass = 8,
    Cyberpunk = 9,
    Sakura = 10,
}

public enum GbcaScaleMode
{
    AspectFit = 0,
    IntegerPixelPerfect = 1,
    StretchFill = 2,
}

public sealed class SystemOverlayProfile
{
    public OverlayBezelStyle OverlayLayout { get; set; } = OverlayBezelStyle.GlassDeck;
    public OverlayButtonStyle ButtonStyle { get; set; } = OverlayButtonStyle.StormNeon;
    public StormVideoFilter LcdFilter { get; set; } = StormVideoFilter.LcdGrid;
    public float Opacity { get; set; } = 0.88f;
    public float Scale { get; set; } = 1.0f;
    public bool ShowControlsDeck { get; set; } = true;
    public bool ShowLR { get; set; } = false;
}

public sealed class GbcaAppSettings
{
    // Appearance & Window
    public string Theme { get; set; } = "STORM MIDNIGHT";
    public string AccentName { get; set; } = "DefaultThemeAccent";
    public string TypographyName { get; set; } = "Inter";
    public string Language { get; set; } = "ru";
    public double UiScaleFactor { get; set; } = 0.0;
    public double WindowWidth { get; set; } = 1320;
    public double WindowHeight { get; set; } = 840;
    public bool WindowMaximized { get; set; } = false;
    public bool UiTactileAudioEnabled { get; set; } = true;
    public bool AutoCheckUpdates { get; set; } = true;
    public string SkippedUpdateVersion { get; set; } = string.Empty;
    public bool Show3DBootSplash { get; set; } = true;

    // Emulation Cores & Hardware
    public StormCoreBackend PreferredCoreGb { get; set; } = StormCoreBackend.SameBoy;
    public StormCoreBackend PreferredCoreGbc { get; set; } = StormCoreBackend.SameBoy;
    public StormCoreBackend PreferredCoreGba { get; set; } = StormCoreBackend.Mgba;
    public StormGbModel GbModel { get; set; } = StormGbModel.Auto;
    public bool SgbBorders { get; set; } = true;
    public bool SkipBiosIntro { get; set; } = true;
    public int RunAheadFrames { get; set; } = 0;
    public bool RewindEnabled { get; set; } = true;
    public int RewindBufferMb { get; set; } = 16;
    public int RewindInterval { get; set; } = 1;
    public float FastForwardMultiplier { get; set; } = 3.0f;

    // BIOS Files
    public string BiosGbPath { get; set; } = string.Empty;
    public string BiosGbcPath { get; set; } = string.Empty;
    public string BiosGbaPath { get; set; } = string.Empty;
    public string BiosSgbPath { get; set; } = string.Empty;

    // Video & Color
    public StormColorCorrectionMode ColorCorrectionMode { get; set; } = StormColorCorrectionMode.ModernBalanced;
    public int DmgPaletteIndex { get; set; } = 0;
    public uint[] CustomDmgPalette { get; set; } = [0x9BBC0F, 0x8BAC0F, 0x306230, 0x0F380F];
    public int LcdGhosting { get; set; } = 15;
    public StormVideoFilter VideoFilter { get; set; } = StormVideoFilter.LcdGrid;
    public GbcaScaleMode ScaleMode { get; set; } = GbcaScaleMode.AspectFit;

    // Audio & 6-Channel Mixer
    public StormAudioHighpassMode AudioHighpassMode { get; set; } = StormAudioHighpassMode.Accurate;
    public StormAudioInterpMode AudioInterpMode { get; set; } = StormAudioInterpMode.Hermite;
    public float MasterVolume { get; set; } = 1.0f;
    public float[] ChannelVolumes { get; set; } = [1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f];
    public bool DynamicRateControl { get; set; } = true;
    public float DrcMaxSkew { get; set; } = 0.005f;
    public bool AudioMuted { get; set; } = false;

    // Independent Per-System 3D Console Skins
    public GbConsoleSkin GbConsoleSkin { get; set; } = GbConsoleSkin.DmgClassicGray;
    public GbcConsoleSkin GbcConsoleSkin { get; set; } = GbcConsoleSkin.AtomicPurple;
    public GbaConsoleSkin GbaConsoleSkin { get; set; } = GbaConsoleSkin.GbaIndigo;
    public bool SkinNightDimming { get; set; } = false;

    // Independent Per-System Overlay Profiles
    public SystemOverlayProfile GbOverlayProfile { get; set; } = new()
    {
        OverlayLayout = OverlayBezelStyle.ClassicBezel,
        ButtonStyle = OverlayButtonStyle.DmgMagenta,
        LcdFilter = StormVideoFilter.LcdGrid,
        Opacity = 0.90f,
        Scale = 1.0f,
        ShowControlsDeck = true,
        ShowLR = false,
    };

    public SystemOverlayProfile GbcOverlayProfile { get; set; } = new()
    {
        OverlayLayout = OverlayBezelStyle.GlassDeck,
        ButtonStyle = OverlayButtonStyle.GbcAtomicPurple,
        LcdFilter = StormVideoFilter.RgbSubpixel,
        Opacity = 0.90f,
        Scale = 1.0f,
        ShowControlsDeck = true,
        ShowLR = false,
    };

    public SystemOverlayProfile GbaOverlayProfile { get; set; } = new()
    {
        OverlayLayout = OverlayBezelStyle.StormNeonDeck,
        ButtonStyle = OverlayButtonStyle.GbaIndigo,
        LcdFilter = StormVideoFilter.SharpBilinear,
        Opacity = 0.90f,
        Scale = 1.0f,
        ShowControlsDeck = true,
        ShowLR = true,
    };

    // Peripherals & Sensors
    public int SolarLuxLevel { get; set; } = 5;
    public bool UseHostRtc { get; set; } = true;
    public bool RumbleToXInput { get; set; } = true;
    public float GyroSensitivity { get; set; } = 1.0f;

    // Keyboard Mappings (Avalonia Key enum string names)
    public Dictionary<string, string> KeyBindings { get; set; } = new()
    {
        ["Up"] = "Up",
        ["Down"] = "Down",
        ["Left"] = "Left",
        ["Right"] = "Right",
        ["A"] = "X",
        ["B"] = "Z",
        ["L"] = "A",
        ["R"] = "S",
        ["Start"] = "Return",
        ["Select"] = "Back",
        ["FastForward"] = "Space",
        ["Rewind"] = "R",
        ["QuickSave"] = "F5",
        ["QuickLoad"] = "F8",
    };

    // RetroAchievements
    public string RetroAchievementsUsername { get; set; } = string.Empty;
    public string RetroAchievementsToken { get; set; } = string.Empty;
    public bool RetroAchievementsHardcoreMode { get; set; } = false;
    public bool RetroAchievementsRichPresence { get; set; } = true;

    // Library
    public List<string> RomDirectories { get; set; } = [];
    public List<string> FavoriteRoms { get; set; } = [];
    public List<string> RecentRoms { get; set; } = [];

    public SystemOverlayProfile GetOverlayProfileForPlatform(StormPlatform platform) => platform switch
    {
        StormPlatform.Gb or StormPlatform.Sgb => GbOverlayProfile,
        StormPlatform.Gbc => GbcOverlayProfile,
        _ => GbaOverlayProfile,
    };
}

public sealed class GbcaRomRecord
{
    public string FilePath { get; set; } = string.Empty;
    public string FileName { get; set; } = string.Empty;
    public StormPlatform Platform { get; set; } = StormPlatform.Gba;
    public string Title { get; set; } = string.Empty;
    public string GameCode { get; set; } = string.Empty;
    public string Maker { get; set; } = string.Empty;
    public string Region { get; set; } = "World";
    public uint Crc32 { get; set; }
    public string Md5 { get; set; } = string.Empty;
    public uint RomSize { get; set; }
    public StormHwFlags HardwareFlags { get; set; } = StormHwFlags.None;
    public string ActiveCoreHint { get; set; } = string.Empty;
    public string RaHash { get; set; } = string.Empty;
    public string? CoverLocalPath { get; set; }
    public bool IsFavorite { get; set; }
    public int PlayCount { get; set; }
    public DateTime? LastPlayedUtc { get; set; }

    [JsonIgnore]
    public string Crc32Hex => $"{Crc32:X8}";

    [JsonIgnore]
    public string PlatformShortName => Platform switch
    {
        StormPlatform.Gb => "GB",
        StormPlatform.Gbc => "GBC",
        StormPlatform.Gba => "GBA",
        StormPlatform.Sgb => "SGB",
        _ => "ROM",
    };

    [JsonIgnore]
    public string RegionBadgeCode => Region switch
    {
        "USA" => "USA",
        "Europe" => "EUR",
        "Japan" => "JPN",
        "Russia" => "RUS",
        _ => "WLD",
    };

    public string GetDisplayTitle()
    {
        string fileTitle = Path.GetFileNameWithoutExtension(FileName);
        if (!string.IsNullOrWhiteSpace(fileTitle)) return fileTitle;
        return !string.IsNullOrWhiteSpace(Title) ? Title : GameCode;
    }
}

public sealed class GbcaCheatEntry
{
    public string Name { get; set; } = string.Empty;
    public string Code { get; set; } = string.Empty;
    public bool Enabled { get; set; } = true;
}

public sealed class RetroAchievementsQueueItem
{
    public int AchievementId { get; set; }
    public string GameHash { get; set; } = string.Empty;
    public string Username { get; set; } = string.Empty;
    public DateTime TimestampUtc { get; set; } = DateTime.UtcNow;
}

[JsonSourceGenerationOptions(WriteIndented = true)]
[JsonSerializable(typeof(GbcaAppSettings))]
[JsonSerializable(typeof(SystemOverlayProfile))]
[JsonSerializable(typeof(List<GbcaRomRecord>))]
[JsonSerializable(typeof(List<GbcaCheatEntry>))]
[JsonSerializable(typeof(List<RetroAchievementsQueueItem>))]
internal partial class GbcaJsonContext : JsonSerializerContext
{
}

public static class GbcaSettingsStore
{
    public static GbcaAppSettings Load()
    {
        StormGbcaPaths.EnsureDirectories();
        string path = StormGbcaPaths.SettingsFilePath;
        if (File.Exists(path))
        {
            try
            {
                string json = File.ReadAllText(path);
                var loaded = JsonSerializer.Deserialize(json, GbcaJsonContext.Default.GbcaAppSettings);
                if (loaded != null)
                {
                    Normalize(loaded);
                    return loaded;
                }
            }
            catch (Exception ex)
            {
                Console.Error.WriteLine($"[GbcaSettingsStore] Notice: Failed reading settings.json: {ex.Message}");
            }
        }

        var fresh = new GbcaAppSettings();
        Normalize(fresh);
        return fresh;
    }

    public static void Save(GbcaAppSettings settings)
    {
        try
        {
            Normalize(settings);
            StormGbcaPaths.EnsureDirectories();
            string json = JsonSerializer.Serialize(settings, GbcaJsonContext.Default.GbcaAppSettings);
            File.WriteAllText(StormGbcaPaths.SettingsFilePath, json);
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine($"[GbcaSettingsStore] Warning: Failed saving settings.json: {ex.Message}");
        }
    }

    private static void Normalize(GbcaAppSettings s)
    {
        if (s.CustomDmgPalette == null || s.CustomDmgPalette.Length != 4)
        {
            s.CustomDmgPalette = [0x9BBC0F, 0x8BAC0F, 0x306230, 0x0F380F];
        }
        if (s.ChannelVolumes == null || s.ChannelVolumes.Length != 6)
        {
            s.ChannelVolumes = [1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f];
        }
        s.GbOverlayProfile ??= new SystemOverlayProfile { ButtonStyle = OverlayButtonStyle.DmgMagenta, ShowLR = false };
        s.GbcOverlayProfile ??= new SystemOverlayProfile { ButtonStyle = OverlayButtonStyle.GbcAtomicPurple, ShowLR = false };
        s.GbaOverlayProfile ??= new SystemOverlayProfile { ButtonStyle = OverlayButtonStyle.GbaIndigo, ShowLR = true };
        s.KeyBindings ??= new Dictionary<string, string>();
        s.RomDirectories ??= [];
        s.FavoriteRoms ??= [];
        s.RecentRoms ??= [];
    }
}
