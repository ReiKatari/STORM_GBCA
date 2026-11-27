// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Apache License, Version 2.0.

namespace StormGbca.Core.Paths;

/// <summary>
/// Resolves user data directories for STORM GBCA (%APPDATA%\STORM_GBCA or portable mode).
/// </summary>
public static class StormGbcaPaths
{
    private const string AppFolderName = "STORM_GBCA";
    private const string PortableMarkerFileName = "portable.txt";

    private static readonly Lazy<string> s_rootDirectory = new(ResolveRootDirectory);

    public static string RootDirectory => s_rootDirectory.Value;
    public static string SettingsFilePath => Path.Combine(RootDirectory, "settings.json");

    public static string RomsDirectory => Path.Combine(RootDirectory, "roms");
    public static string SavesDirectory => Path.Combine(RootDirectory, "saves");
    public static string StatesDirectory => Path.Combine(RootDirectory, "states");
    public static string BiosDirectory => Path.Combine(RootDirectory, "bios");
    public static string PatchesDirectory => Path.Combine(RootDirectory, "patches");
    public static string CheatsDirectory => Path.Combine(RootDirectory, "cheats");
    public static string ConfigDirectory => Path.Combine(RootDirectory, "config");
    public static string CacheDirectory => Path.Combine(RootDirectory, "cache");
    public static string CoversDirectory => Path.Combine(CacheDirectory, "covers");
    public static string ScreenshotsDirectory => Path.Combine(RootDirectory, "screenshots");
    public static string LogsDirectory => Path.Combine(RootDirectory, "logs");

    public static string RetroAchievementsQueuePath => Path.Combine(ConfigDirectory, "ra_offline_queue.json");
    public static string LibraryCachePath => Path.Combine(CacheDirectory, "rom_library.json");

    public static void EnsureDirectories()
    {
        try
        {
            Directory.CreateDirectory(RootDirectory);
            Directory.CreateDirectory(RomsDirectory);
            Directory.CreateDirectory(SavesDirectory);
            Directory.CreateDirectory(StatesDirectory);
            Directory.CreateDirectory(BiosDirectory);
            Directory.CreateDirectory(PatchesDirectory);
            Directory.CreateDirectory(CheatsDirectory);
            Directory.CreateDirectory(ConfigDirectory);
            Directory.CreateDirectory(CacheDirectory);
            Directory.CreateDirectory(CoversDirectory);
            Directory.CreateDirectory(ScreenshotsDirectory);
            Directory.CreateDirectory(LogsDirectory);
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine($"[StormGbcaPaths] Warning: Failed to create directories: {ex.Message}");
        }
    }

    public static string GetSaveFilePath(string romPathOrKey)
    {
        string safe = SanitizeFileKey(Path.GetFileNameWithoutExtension(romPathOrKey));
        return Path.Combine(SavesDirectory, $"{safe}.sav");
    }

    public static string GetSaveStateFilePath(string romPathOrKey, int slotIndex)
    {
        string safe = SanitizeFileKey(Path.GetFileNameWithoutExtension(romPathOrKey));
        return Path.Combine(StatesDirectory, $"{safe}.st{slotIndex}");
    }

    public static string GetSaveStateThumbnailPath(string romPathOrKey, int slotIndex)
    {
        string safe = SanitizeFileKey(Path.GetFileNameWithoutExtension(romPathOrKey));
        return Path.Combine(StatesDirectory, $"{safe}.st{slotIndex}.png");
    }

    public static string GetCheatsFilePath(string romKey)
    {
        string safe = SanitizeFileKey(romKey);
        return Path.Combine(CheatsDirectory, $"{safe}.json");
    }

    public static string SanitizeFileKey(string raw)
    {
        if (string.IsNullOrWhiteSpace(raw)) return "STORM_ROM";
        char[] invalid = Path.GetInvalidFileNameChars();
        var chars = raw.Trim().ToCharArray();
        for (int i = 0; i < chars.Length; i++)
        {
            if (Array.IndexOf(invalid, chars[i]) >= 0) chars[i] = '_';
        }
        return new string(chars);
    }

    public static IReadOnlyList<string> DiscoverRomDirectories()
    {
        var discovered = new List<string> { RomsDirectory };

        if (OperatingSystem.IsWindows())
        {
            string userProfile = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
            string documents = Environment.GetFolderPath(Environment.SpecialFolder.MyDocuments);
            string downloads = Path.Combine(userProfile, "Downloads");

            string[] candidates =
            [
                Path.Combine(downloads, "GBA"),
                Path.Combine(downloads, "GBC"),
                Path.Combine(downloads, "GB"),
                Path.Combine(downloads, "Game Boy"),
                Path.Combine(downloads, "Game Boy Advance"),
                Path.Combine(documents, "ROMs", "GBA"),
                Path.Combine(documents, "ROMs", "GBC"),
                Path.Combine(documents, "ROMs", "GB"),
                Path.Combine(userProfile, "Emulation", "roms", "gba"),
                Path.Combine(userProfile, "Emulation", "roms", "gbc"),
                Path.Combine(userProfile, "Emulation", "roms", "gb"),
                @"D:\ROMs\GBA",
                @"D:\ROMs\GBC",
                @"D:\ROMs\GB",
                @"E:\ROMs\GBA",
                @"F:\ROMs\GBA"
            ];

            foreach (string candidate in candidates)
            {
                if (Directory.Exists(candidate) && !discovered.Contains(candidate, StringComparer.OrdinalIgnoreCase))
                {
                    discovered.Add(candidate);
                }
            }
        }

        return discovered;
    }

    private static string ResolveRootDirectory()
    {
        string baseDirectory = AppContext.BaseDirectory;
        string dotPortableMarker = Path.Combine(baseDirectory, ".portable");
        if (File.Exists(dotPortableMarker))
        {
            return Path.Combine(baseDirectory, "data");
        }

        string portableMarker = Path.Combine(baseDirectory, PortableMarkerFileName);
        if (File.Exists(portableMarker))
        {
            return baseDirectory;
        }

        if (OperatingSystem.IsWindows())
        {
            string appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
            return Path.Combine(appData, AppFolderName);
        }

        string home = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
        return Path.Combine(home, ".config", AppFolderName);
    }
}
