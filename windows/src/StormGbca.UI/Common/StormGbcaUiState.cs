// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Mozilla Public License, Version 2.0 (MPL-2.0).

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
    Catalog = 6,
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
    public SgwCatalogService CatalogService { get; }

    public IReadOnlyList<SgwCatalogGame> CatalogGames { get; private set; } = [];
    public IReadOnlyDictionary<string, SgwGameUpdateInfo> AvailableRomUpdates { get; private set; } =
        new Dictionary<string, SgwGameUpdateInfo>(StringComparer.OrdinalIgnoreCase);

    public NavSectionKind ActiveNavSection { get; private set; } = NavSectionKind.Library;
    public GbcaPlatformFilter ActiveFilter { get; private set; } = GbcaPlatformFilter.All;
    public GbcaSortMode ActiveSort { get; private set; } = GbcaSortMode.Title;
    public GbcaViewMode ActiveViewMode { get; private set; } = GbcaViewMode.BentoCards;
    public string SearchQuery { get; private set; } = string.Empty;

    private readonly object _metricsLock = new();
    private GbcaFrameMetrics _lastMetrics;

    public GbcaRomRecord? SelectedRom { get; private set; }
    public bool IsEmulationActive { get; private set; }
    public bool IsPaused { get; private set; }
    public bool IsHiddenToTray { get; private set; }
    public bool IsFullscreen { get; private set; }
    public GbcaFrameMetrics LastMetrics
    {
        get { lock (_metricsLock) return _lastMetrics; }
        set { lock (_metricsLock) _lastMetrics = value; }
    }

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
    public event Action? RomUpdatesChanged;

    private StormGbcaUiState()
    {
        Settings = GbcaSettingsStore.Load();
        Library = new GbcaRomLibrary();
        Emulator = new GbcaEmulatorSession();
        Cheats = new GbcaCheatService();
        RetroAchievements = new RetroAchievementsService();
        CatalogService = new SgwCatalogService();

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

        Emulator.FrameCompleted += metrics => LastMetrics = metrics;
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
        RetroAchievements.ShowLeaderboardPopups = Settings.RetroAchievementsShowLeaderboardPopups;
    }

    public void SetHardcoreModeFromUi(bool enabled)
    {
        Settings.RetroAchievementsHardcoreMode = enabled;
        if (enabled)
        {
            Settings.RetroAchievementsRichPresence = true;
            Settings.RetroAchievementsLeaderboards = true;
            Settings.RetroAchievementsUnofficial = false;
        }
        SyncServicesFromSettings();
        GbcaSettingsStore.Save(Settings);
        if (IsEmulationActive)
        {
            Emulator.SetHardcoreMode(enabled, Settings, Cheats, RetroAchievements);
        }
        SettingsSaved?.Invoke();
        StateChanged?.Invoke();
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
        string raHash = !string.IsNullOrWhiteSpace(Emulator.LoadedRaHash)
            ? Emulator.LoadedRaHash
            : target.RaHash;
        if (string.IsNullOrWhiteSpace(raHash))
        {
            raHash = RetroAchievements.ComputeRomHash(target.FilePath, target.Platform);
        }
        if (!string.IsNullOrWhiteSpace(raHash))
        {
            target.RaHash = raHash;
        }
        if (RetroAchievements.IsEnabled && RetroAchievements.IsLoggedIn && !string.IsNullOrWhiteSpace(raHash))
        {
            _ = Task.Run(async () =>
            {
                var session = await RetroAchievements.FetchGameSessionAsync(raHash).ConfigureAwait(false);
                if (session != null && IsEmulationActive)
                {
                    await Emulator.ActivateRaSessionAsync(session, raHash).ConfigureAwait(false);
                    NotifyStatus($"RetroAchievements: {session.GameTitle} ({session.UnlockedCount}/{session.TotalCount} | {session.EarnedPoints} pts)");
                }
            });
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
        bool allowed = !Emulator.IsHardcoreMode && RetroAchievements.IsFeatureAllowedInCurrentMode("cheats");
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

    public SgwGameUpdateInfo? GetUpdateForRom(GbcaRomRecord? rom)
    {
        if (rom == null || string.IsNullOrWhiteSpace(rom.FilePath)) return null;
        return AvailableRomUpdates.TryGetValue(rom.FilePath, out var info) ? info : null;
    }

    public void RecomputeLibraryUpdates()
    {
        if (CatalogGames.Count == 0)
        {
            AvailableRomUpdates = new Dictionary<string, SgwGameUpdateInfo>(StringComparer.OrdinalIgnoreCase);
            RomUpdatesChanged?.Invoke();
            return;
        }
        AvailableRomUpdates = CatalogService.FindUpdatesForLibrary(Library.AllRoms, CatalogGames);
        RomUpdatesChanged?.Invoke();
    }

    public async Task RefreshCatalogAndUpdatesAsync(bool forceRefresh = false, CancellationToken ct = default)
    {
        var games = await CatalogService.GetCatalogAsync(forceRefresh, ct).ConfigureAwait(false);
        CatalogGames = games;
        var updates = CatalogService.FindUpdatesForLibrary(Library.AllRoms, games);
        AvailableRomUpdates = updates;
        await Avalonia.Threading.Dispatcher.UIThread.InvokeAsync(() =>
        {
            RomUpdatesChanged?.Invoke();
            StateChanged?.Invoke();
        });
    }

    public async Task<SgwDownloadResult> DownloadCatalogGameAsync(
        SgwCatalogGame game,
        GbcaRomRecord? replacingRom = null,
        IProgress<SgwDownloadProgress>? progress = null,
        CancellationToken ct = default)
    {
        if (replacingRom != null && IsEmulationActive &&
            string.Equals(Emulator.ActiveRomKey, replacingRom.FileName, StringComparison.OrdinalIgnoreCase))
        {
            await Avalonia.Threading.Dispatcher.UIThread.InvokeAsync(StopEmulation);
        }

        bool wasSelected = replacingRom != null && SelectedRom != null &&
                           string.Equals(SelectedRom.FilePath, replacingRom.FilePath, StringComparison.OrdinalIgnoreCase);

        var res = await CatalogService.DownloadOrUpdateRomAsync(game, Settings, Library, replacingRom, progress, ct).ConfigureAwait(false);
        if (res.Success)
        {
            var refreshedUpdates = CatalogGames.Count > 0
                ? CatalogService.FindUpdatesForLibrary(Library.AllRoms, CatalogGames)
                : new Dictionary<string, SgwGameUpdateInfo>(StringComparer.OrdinalIgnoreCase);
            AvailableRomUpdates = refreshedUpdates;

            await Avalonia.Threading.Dispatcher.UIThread.InvokeAsync(() =>
            {
                if (wasSelected && res.NewRecord != null)
                {
                    SelectRom(res.NewRecord);
                }
                RomUpdatesChanged?.Invoke();
                StateChanged?.Invoke();
                NotifyStatus(string.Format(System.Globalization.CultureInfo.InvariantCulture,
                    StormGbca.Core.Localization.GbcaLoc.T("CatalogDoneMsgFmt"),
                    res.NewRecord?.GetDisplayTitle() ?? game.Title));
            });
        }
        return res;
    }

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
