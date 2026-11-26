// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Apache License, Version 2.0.

using Avalonia.Media;
using StormGbca.Core.Configuration;
using StormGbca.Core.Engine;
using StormGbca.Core.Library;
using StormGbca.Core.Native;
using StormGbca.Core.Subsystems;

namespace StormGbca.UI.Common;

public enum NavSectionKind
{
    Library = 0,
    Favorites = 1,
    Recent = 2,
    SkinsAndOverlays = 3,
    Achievements = 4,
    Settings = 5,
}

public enum GbcaViewMode
{
    BentoCards = 0,
    CollectorTable = 1,
}

public sealed class StormGbcaUiState
{
    private static readonly Lazy<StormGbcaUiState> s_instance = new(() => new StormGbcaUiState());
    public static StormGbcaUiState Instance => s_instance.Value;

    public GbcaAppSettings Settings { get; }
    public GbcaRomLibrary Library { get; }
    public GbcaEmulatorSession Emulator { get; }
    public GbcaCheatService Cheats { get; }
    public RetroAchievementsService RetroAchievements { get; }

    public NavSectionKind ActiveNavSection { get; private set; } = NavSectionKind.Library;
    public GbcaPlatformFilter ActiveFilter { get; private set; } = GbcaPlatformFilter.All;
    public GbcaSortMode ActiveSort { get; private set; } = GbcaSortMode.Title;
    public GbcaViewMode ActiveViewMode { get; private set; } = GbcaViewMode.BentoCards;
    public string SearchQuery { get; private set; } = string.Empty;

    public GbcaRomRecord? SelectedRom { get; private set; }
    public bool IsEmulationActive { get; private set; }
    public bool IsPaused { get; private set; }
    public bool IsHiddenToTray { get; private set; }
    public bool IsFullscreen { get; private set; }
    public GbcaFrameMetrics LastMetrics { get; set; }

    public event Action? StateChanged;
    public event Action<GbcaRomRecord?>? RomSelected;
    public event Action<GbcaRomRecord>? EmulationStarted;
    public event Action? EmulationStopped;
    public event Action<NavSectionKind>? NavSectionChanged;
    public event Action? FilterChanged;
    public event Action? SkinOrOverlayChanged;
    public event Action? UiScaleChanged;
    public event Action? FullscreenToggled;
    public event Action<bool>? FullscreenChanged;
    public event Action<bool>? UpdateCheckRequested;
    public event Action<GbcaRomRecord, int>? RomModalRequested;
    public event Action<GbcaRomRecord>? CoverViewerRequested;
    public event Action<bool>? HiddenToTrayChanged;
    public event Action? SettingsSaved;
    public event Action<string>? StatusNotification;

    private StormGbcaUiState()
    {
        Settings = GbcaSettingsStore.Load();
        Library = new GbcaRomLibrary();
        Emulator = new GbcaEmulatorSession();
        Cheats = new GbcaCheatService();
        RetroAchievements = new RetroAchievementsService();

        Library.SyncFavoritesAndRecents(Settings);
        SyncServicesFromSettings();

        Library.LibraryChanged += () =>
        {
            Library.SyncFavoritesAndRecents(Settings);
            if (SelectedRom == null && Library.AllRoms.Count > 0)
            {
                SelectRom(Library.AllRoms[0]);
            }
            if (Settings.ShowLibretroCovers)
            {
                _ = Library.PreloadAllMissingCoversAsync();
            }
            StateChanged?.Invoke();
        };

        Emulator.StatusMessage += msg => NotifyStatus(msg);
    }

    public void SyncServicesFromSettings()
    {
        RetroAchievements.Username = Settings.RetroAchievementsUsername;
        RetroAchievements.Token = Settings.RetroAchievementsToken;
        RetroAchievements.IsHardcoreMode = Settings.RetroAchievementsHardcoreMode;
        RetroAchievements.IsRichPresenceEnabled = Settings.RetroAchievementsRichPresence;
        RetroAchievements.Points = Settings.RetroAchievementsPoints;
        RetroAchievements.SoftcorePoints = Settings.RetroAchievementsSoftcorePoints;
        RetroAchievements.Rank = Settings.RetroAchievementsRank;
        RetroAchievements.IsEnabled = Settings.RetroAchievementsEnabled;
        RetroAchievements.IsEncoreMode = Settings.RetroAchievementsEncoreMode;
        RetroAchievements.IsUnofficialEnabled = Settings.RetroAchievementsUnofficial;
        RetroAchievements.NotificationsEnabled = Settings.RetroAchievementsNotifications;
        RetroAchievements.LeaderboardsEnabled = Settings.RetroAchievementsLeaderboards;
    }

    public void SaveSettings(bool notify = false)
    {
        GbcaSettingsStore.Save(Settings);
        SyncServicesFromSettings();
        if (IsEmulationActive)
        {
            Emulator.ApplySettings(Settings, RetroAchievements.IsHardcoreMode);
        }
        SettingsSaved?.Invoke();
        SkinOrOverlayChanged?.Invoke();
        if (notify) NotifyStatus("Конфигурация сохранена");
    }

    public void NotifyStatus(string message) => StatusNotification?.Invoke(message ?? string.Empty);

    public void SelectRom(GbcaRomRecord? rom)
    {
        SelectedRom = rom;
        RomSelected?.Invoke(rom);
        StateChanged?.Invoke();
        if (rom != null && Settings.ShowLibretroCovers && string.IsNullOrWhiteSpace(rom.CoverLocalPath))
        {
            _ = Library.EnsureCoverArtAsync(rom);
        }
    }

    public void SetNavSection(NavSectionKind section)
    {
        ActiveNavSection = section;
        if (section == NavSectionKind.Favorites)
        {
            ActiveFilter = GbcaPlatformFilter.Favorites;
            FilterChanged?.Invoke();
        }
        else if (section == NavSectionKind.Library && ActiveFilter == GbcaPlatformFilter.Favorites)
        {
            ActiveFilter = GbcaPlatformFilter.All;
            FilterChanged?.Invoke();
        }
        NavSectionChanged?.Invoke(section);
        StateChanged?.Invoke();
    }

    public void SetFilter(GbcaPlatformFilter filter)
    {
        ActiveFilter = filter;
        FilterChanged?.Invoke();
        StateChanged?.Invoke();
    }

    public void SetSort(GbcaSortMode sort)
    {
        ActiveSort = sort;
        FilterChanged?.Invoke();
        StateChanged?.Invoke();
    }

    public void SetViewMode(GbcaViewMode mode)
    {
        ActiveViewMode = mode;
        StateChanged?.Invoke();
    }

    public void SetSearchQuery(string query)
    {
        SearchQuery = query ?? string.Empty;
        FilterChanged?.Invoke();
        StateChanged?.Invoke();
    }

    public void ToggleFavorite(GbcaRomRecord rom)
    {
        Library.ToggleFavorite(rom, Settings);
        GbcaSettingsStore.Save(Settings);
        StateChanged?.Invoke();
    }

    public bool StartEmulation(GbcaRomRecord? rom = null)
    {
        var target = rom ?? SelectedRom;
        if (target == null)
        {
            NotifyStatus("Выберите игру в библиотеке или перетащите ROM-файл в окно");
            return false;
        }

        SelectRom(target);
        bool ok = Emulator.LoadAndStartRom(target, Settings, Cheats, RetroAchievements);
        if (!ok)
        {
            NotifyStatus($"Не удалось запустить {target.FileName}");
            return false;
        }

        Library.RecordPlay(target, Settings);
        if (string.IsNullOrWhiteSpace(target.RaHash))
        {
            target.RaHash = RetroAchievements.ComputeRomHash(target.FilePath, target.Platform);
        }
        if (RetroAchievements.IsLoggedIn && !string.IsNullOrWhiteSpace(target.RaHash))
        {
            _ = RetroAchievements.StartSessionAndFetchSummaryAsync(target.RaHash);
        }

        GbcaSettingsStore.Save(Settings);
        IsEmulationActive = true;
        IsPaused = false;
        EmulationStarted?.Invoke(target);
        StateChanged?.Invoke();
        return true;
    }

    public void StartRomFromPath(string romPath)
    {
        var rec = Library.ImportSingleFile(romPath);
        if (rec != null)
        {
            StartEmulation(rec);
        }
        else
        {
            NotifyStatus($"Неподдерживаемый или поврежденный файл: {Path.GetFileName(romPath)}");
        }
    }

    public void StopEmulation()
    {
        Emulator.Stop();
        IsEmulationActive = false;
        IsPaused = false;
        EmulationStopped?.Invoke();
        StateChanged?.Invoke();
    }

    public void TogglePause()
    {
        if (!IsEmulationActive) return;
        IsPaused = !IsPaused;
        Emulator.SetPaused(IsPaused);
        StateChanged?.Invoke();
    }

    public void SetPaused(bool paused)
    {
        if (!IsEmulationActive) return;
        IsPaused = paused;
        Emulator.SetPaused(paused);
        StateChanged?.Invoke();
    }

    public void SetHiddenToTray(bool hidden)
    {
        if (IsHiddenToTray == hidden) return;
        IsHiddenToTray = hidden;
        if (IsEmulationActive)
        {
            Emulator.SetPaused(hidden || IsPaused);
        }
        HiddenToTrayChanged?.Invoke(hidden);
        StateChanged?.Invoke();
    }

    public void ApplyLiveCheats()
    {
        if (!IsEmulationActive || Emulator.NativeContext == nint.Zero) return;
        bool allowed = RetroAchievements.IsFeatureAllowedInCurrentMode("cheats");
        Cheats.ApplyToNative(Emulator.NativeContext, allowed);
    }

    public void NotifySkinOrOverlayChanged()
    {
        GbcaSettingsStore.Save(Settings);
        if (IsEmulationActive)
        {
            Emulator.ApplySettings(Settings, RetroAchievements.IsHardcoreMode);
        }
        SkinOrOverlayChanged?.Invoke();
        StateChanged?.Invoke();
    }

    public void NotifyUiScaleChanged() => UiScaleChanged?.Invoke();
    public void RequestToggleFullscreen() => FullscreenToggled?.Invoke();

    public void SetFullscreen(bool active)
    {
        IsFullscreen = active;
        FullscreenChanged?.Invoke(active);
    }

    public void RequestUpdateCheck(bool manual = true) => UpdateCheckRequested?.Invoke(manual);
    public void RequestRomModal(GbcaRomRecord rom, int tabIndex = 0) => RomModalRequested?.Invoke(rom, tabIndex);
    public void RequestCoverViewer(GbcaRomRecord rom) => CoverViewerRequested?.Invoke(rom);

    public IReadOnlyList<GbcaRomRecord> GetFilteredRoms()
    {
        var list = Library.Query(SearchQuery, ActiveFilter, ActiveSort);
        if (ActiveNavSection == NavSectionKind.Favorites)
        {
            return list.Where(r => r.IsFavorite).ToList();
        }
        if (ActiveNavSection == NavSectionKind.Recent)
        {
            return list.Where(r => r.PlayCount > 0).OrderByDescending(r => r.LastPlayedUtc).ToList();
        }
        return list;
    }

    public static Color GetPlatformBadgeColor(StormPlatform platform) => platform switch
    {
        StormPlatform.Gb => Color.Parse("#8BAC0F"),
        StormPlatform.Gbc => Color.Parse("#7B42F6"),
        StormPlatform.Gba => Color.Parse("#4F46E5"),
        StormPlatform.Sgb => Color.Parse("#F59E0B"),
        _ => Color.Parse("#64748B"),
    };
}
