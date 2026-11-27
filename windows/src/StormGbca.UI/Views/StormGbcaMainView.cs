// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Apache License, Version 2.0.

using Avalonia;
using Avalonia.Controls;
using Avalonia.Input;
using Avalonia.Layout;
using Avalonia.Media;
using Avalonia.Platform.Storage;
using Avalonia.Threading;
using Storm.AuroraFluid.Kit.Controls;
using Storm.AuroraFluid.Kit.Platform;
using Storm.AuroraFluid.Kit.Services;
using StormGbca.Core.Configuration;
using StormGbca.Core.Library;
using StormGbca.Core.Localization;
using StormGbca.Core.Native;
using StormGbca.UI.Common;
using StormGbca.UI.Views.Bento;
using StormGbca.UI.Views.Emulation;
using StormGbca.UI.Views.Inspector;
using StormGbca.UI.Views.Modals;
using StormGbca.UI.Views.Navigation;
using StormGbca.UI.Views.Sections;
using StormGbca.UI.Views.Settings;
using StormGbca.UI.Views.Skins;
using StormGbca.UI.Views.StatusBar;
using StormGbca.UI.Views.TitleBar;

namespace StormGbca.UI.Views;

public class StormGbcaMainView : Grid
{
    private readonly Window? _hostWindow;
    private readonly CustomTitleBar? _titleBar;
    private readonly Grid _bodyHeaderGrid;
    private readonly Grid _centerGrid;
    private readonly NavRailView _navRail;
    private readonly NavDockView _navDock;
    private readonly BentoWorkspaceView _bentoWorkspace;
    private readonly GbcaSkinAndOverlayViews _skinsView;
    private readonly AchievementsView _achievementsView;
    private readonly SettingsView _settingsView;
    private readonly RomInspectorPane _inspectorPane;
    private readonly SingleScreenSessionView _emulationView;
    private readonly StormStatusBar _statusBar;
    private readonly DropImportOverlay _dropOverlay;
    private readonly AuroraModalHost _modalHost;

    private AuroraSearchBox? _searchBox;
    private Button? _fileMenuBtn;
    private Button? _emuMenuBtn;
    private Button? _skinsMenuBtn;
    private Button? _configMenuBtn;
    private Button? _helpMenuBtn;
    private TextBlock? _stormTitleText;
    private TextBlock? _gbcaTitleText;
    private TextBlock? _subTitleText;

    public StormGbcaMainView(Window? hostWindow = null, bool includeTitleBar = true)
    {
        _hostWindow = hostWindow;
        ClipToBounds = true;
        Focusable = true;

        int rowOffset = includeTitleBar ? 1 : 0;
        int totalRows = includeTitleBar ? 4 : 3;
        RowDefinitions = includeTitleBar
            ? new RowDefinitions("Auto,Auto,*,Auto")
            : new RowDefinitions("Auto,*,Auto");

        if (includeTitleBar)
        {
            _titleBar = new CustomTitleBar(_hostWindow);
            Children.Add(_titleBar);
            Grid.SetRow(_titleBar, 0);
        }

        _bodyHeaderGrid = BuildTopCommandAndHeader();
        Children.Add(_bodyHeaderGrid);
        Grid.SetRow(_bodyHeaderGrid, rowOffset);

        var workspaceGrid = new Grid { ColumnDefinitions = new ColumnDefinitions("Auto,*,Auto") };

        _navRail = new NavRailView();
        workspaceGrid.Children.Add(_navRail);

        _centerGrid = new Grid();
        _bentoWorkspace = new BentoWorkspaceView();
        _skinsView = new GbcaSkinAndOverlayViews { IsVisible = false };
        _achievementsView = new AchievementsView { IsVisible = false };
        _settingsView = new SettingsView { IsVisible = false };
        _emulationView = new SingleScreenSessionView { IsVisible = false };

        _centerGrid.Children.Add(_bentoWorkspace);
        _centerGrid.Children.Add(_skinsView);
        _centerGrid.Children.Add(_achievementsView);
        _centerGrid.Children.Add(_settingsView);
        _centerGrid.Children.Add(_emulationView);
        workspaceGrid.Children.Add(_centerGrid);
        Grid.SetColumn(_centerGrid, 1);

        _inspectorPane = new RomInspectorPane((rom, tab) => ShowRomModal(rom, tab));
        workspaceGrid.Children.Add(_inspectorPane);
        Grid.SetColumn(_inspectorPane, 2);

        Children.Add(workspaceGrid);
        Grid.SetRow(workspaceGrid, rowOffset + 1);

        _navDock = new NavDockView { IsVisible = false };
        Children.Add(_navDock);
        Grid.SetRow(_navDock, rowOffset + 1);

        _statusBar = new StormStatusBar();
        Children.Add(_statusBar);
        Grid.SetRow(_statusBar, rowOffset + 2);

        _dropOverlay = new DropImportOverlay { IsVisible = false };
        Children.Add(_dropOverlay);
        Grid.SetRowSpan(_dropOverlay, totalRows);

        _modalHost = new AuroraModalHost { IsOpen = false };
        Children.Add(_modalHost);
        Grid.SetRowSpan(_modalHost, totalRows);

        DragDrop.SetAllowDrop(this, true);
        AddHandler(DragDrop.DragEnterEvent, (_, _) => _dropOverlay.IsVisible = true);
        AddHandler(DragDrop.DragLeaveEvent, (_, _) => _dropOverlay.IsVisible = false);
        AddHandler(DragDrop.DropEvent, OnDrop);

        KeyDown += OnRootKeyDown;
        StormGbcaUiState.Instance.NavSectionChanged += OnNavSectionChanged;
        StormGbcaUiState.Instance.EmulationStarted += _ => SwitchToEmulation(true);
        StormGbcaUiState.Instance.EmulationStopped += () => SwitchToEmulation(false);
        StormGbcaUiState.Instance.FullscreenChanged += OnFullscreenChanged;
        StormGbcaUiState.Instance.UpdateCheckRequested += manual => _ = CheckAndShowUpdateModalAsync(manual);
        StormGbcaUiState.Instance.RomModalRequested += ShowRomModal;
        StormGbcaUiState.Instance.CoverViewerRequested += ShowCoverViewer;
        GbcaLoc.LanguageChanged += _ => ApplyHeaderTextsAndTheme();
        StormThemeBrushes.BindTheme(this, ApplyHeaderTextsAndTheme);

        _ = StormGbcaUiState.Instance.Library.ScanDirectoriesAsync(StormGbcaUiState.Instance.Settings.RomDirectories);

        if (StormGbcaUiState.Instance.Settings.AutoCheckUpdates)
        {
            DispatcherTimer.RunOnce(() => _ = CheckAndShowUpdateModalAsync(manual: false), TimeSpan.FromMilliseconds(1400));
        }
    }

    private Grid BuildTopCommandAndHeader()
    {
        var root = new Grid
        {
            RowDefinitions = new RowDefinitions("Auto,Auto"),
            Margin = new Thickness(0, 0, 0, 4)
        };

        var menuStrip = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 4, Margin = new Thickness(14, 2, 14, 2) };
        _fileMenuBtn = CreateMenuButton(GbcaLoc.T("MenuFile"), ShowFileMenu);
        _emuMenuBtn = CreateMenuButton(GbcaLoc.T("MenuEmulation"), ShowEmulationMenu);
        _skinsMenuBtn = CreateMenuButton(GbcaLoc.T("MenuSkins"), _ => StormGbcaUiState.Instance.SetNavSection(NavSectionKind.SkinsAndOverlays));
        _configMenuBtn = CreateMenuButton(GbcaLoc.T("MenuSettings"), _ => StormGbcaUiState.Instance.SetNavSection(NavSectionKind.Settings));
        _helpMenuBtn = CreateMenuButton(GbcaLoc.T("MenuHelp"), ShowHelpMenu);

        menuStrip.Children.Add(_fileMenuBtn);
        menuStrip.Children.Add(_emuMenuBtn);
        menuStrip.Children.Add(_skinsMenuBtn);
        menuStrip.Children.Add(_configMenuBtn);
        menuStrip.Children.Add(_helpMenuBtn);
        root.Children.Add(menuStrip);

        var headerBar = new Grid
        {
            Margin = new Thickness(16, 6, 16, 6),
            ColumnDefinitions = new ColumnDefinitions("Auto,*,Auto")
        };
        var brandStack = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 5, VerticalAlignment = VerticalAlignment.Center };
        _stormTitleText = new TextBlock { Text = "STORM", FontSize = 18, FontWeight = FontWeight.Black, Foreground = StormThemeBrushes.Accent, VerticalAlignment = VerticalAlignment.Center };
        _gbcaTitleText = new TextBlock { Text = "GBCA", FontSize = 18, FontWeight = FontWeight.Black, Foreground = StormThemeBrushes.PrimaryText, VerticalAlignment = VerticalAlignment.Center };
        _subTitleText = new TextBlock { Text = "- " + GbcaLoc.T("AppSubtitle"), FontSize = 12, Foreground = StormThemeBrushes.SecondaryText, VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(4, 0, 0, 0) };
        brandStack.Children.Add(_stormTitleText);
        brandStack.Children.Add(_gbcaTitleText);
        brandStack.Children.Add(_subTitleText);
        headerBar.Children.Add(brandStack);

        _searchBox = new AuroraSearchBox
        {
            PlaceholderText = GbcaLoc.T("SearchLibraryPlaceholder"),
            HotkeyHint = "Ctrl+K",
            MinWidth = 220,
            MaxWidth = 340,
            HorizontalAlignment = HorizontalAlignment.Right,
            VerticalAlignment = VerticalAlignment.Center
        };
        _searchBox.TextChanged += (_, _) => StormGbcaUiState.Instance.SetSearchQuery(_searchBox.Text ?? string.Empty);
        headerBar.Children.Add(_searchBox);
        Grid.SetColumn(_searchBox, 2);

        headerBar.PropertyChanged += (_, ch) =>
        {
            if (ch.Property == BoundsProperty && headerBar.Bounds.Width > 0)
            {
                _subTitleText.IsVisible = headerBar.Bounds.Width >= 900;
            }
        };

        root.Children.Add(headerBar);
        Grid.SetRow(headerBar, 1);
        return root;
    }

    private void ApplyHeaderTextsAndTheme()
    {
        _bodyHeaderGrid.Background = StormThemeBrushes.ChromeSurface;
        if (_stormTitleText != null) _stormTitleText.Foreground = StormThemeBrushes.Accent;
        if (_gbcaTitleText != null) _gbcaTitleText.Foreground = StormThemeBrushes.PrimaryText;
        if (_subTitleText != null)
        {
            _subTitleText.Foreground = StormThemeBrushes.SecondaryText;
            _subTitleText.Text = "- " + GbcaLoc.T("AppSubtitle");
        }
        if (_searchBox != null)
        {
            _searchBox.PlaceholderText = GbcaLoc.T("SearchLibraryPlaceholder");
        }
        SetMenuText(_fileMenuBtn, GbcaLoc.T("MenuFile"));
        SetMenuText(_emuMenuBtn, GbcaLoc.T("MenuEmulation"));
        SetMenuText(_skinsMenuBtn, GbcaLoc.T("MenuSkins"));
        SetMenuText(_configMenuBtn, GbcaLoc.T("MenuSettings"));
        SetMenuText(_helpMenuBtn, GbcaLoc.T("MenuHelp"));
    }

    private static void SetMenuText(Button? btn, string text)
    {
        if (btn == null) return;
        btn.Resources["ButtonBackgroundPointerOver"] = StormThemeBrushes.HoverSurface;
        btn.Resources["ButtonBackgroundPressed"] = StormThemeBrushes.RowSurface;
        if (btn.Content is TextBlock tb)
        {
            tb.Text = text;
            tb.Foreground = StormThemeBrushes.BodyText;
        }
    }

    private static Button CreateMenuButton(string text, Action<Button> onClick)
    {
        var btn = new Button
        {
            Padding = new Thickness(8, 4),
            Background = Brushes.Transparent,
            BorderThickness = new Thickness(0),
            CornerRadius = new CornerRadius(6),
            Cursor = new Cursor(StandardCursorType.Hand),
            Content = new TextBlock { Text = text, FontSize = 11.5, FontWeight = FontWeight.Medium, Foreground = StormThemeBrushes.BodyText }
        };
        btn.Resources["ButtonBackground"] = Brushes.Transparent;
        btn.Resources["ButtonBackgroundPointerOver"] = StormThemeBrushes.HoverSurface;
        btn.Resources["ButtonBackgroundPressed"] = StormThemeBrushes.RowSurface;
        btn.PointerEntered += (_, _) => btn.Background = StormThemeBrushes.HoverSurface;
        btn.PointerExited += (_, _) => btn.Background = Brushes.Transparent;
        btn.Click += (_, _) =>
        {
            AuroraTactileFeedbackService.Instance.PlayClick();
            onClick(btn);
        };
        return btn;
    }

    private static MenuItem CreateMenuItem(string header, Action action, bool enabled = true)
    {
        var item = new MenuItem { Header = header, IsEnabled = enabled };
        item.Click += (_, _) =>
        {
            AuroraTactileFeedbackService.Instance.PlayClick();
            action();
        };
        return item;
    }

    private void ShowFileMenu(Button anchor)
    {
        var flyout = new MenuFlyout();
        flyout.Items.Add(CreateMenuItem(GbcaLoc.T("MenuOpenRom"), () => _ = PickAndOpenRomAsync()));
        flyout.Items.Add(CreateMenuItem(GbcaLoc.T("MenuScanFolder"), () => _ = PickAndScanFolderAsync()));
        flyout.Items.Add(CreateMenuItem(GbcaLoc.T("MenuRescanLibrary"), () => _ = StormGbcaUiState.Instance.Library.ScanDirectoriesAsync(StormGbcaUiState.Instance.Settings.RomDirectories)));
        flyout.Items.Add(new Separator());
        flyout.Items.Add(CreateMenuItem(GbcaLoc.T("MenuExit"), () => AuroraTrayManager.Instance.RequestExit()));
        flyout.ShowAt(anchor, true);
    }

    private void ShowEmulationMenu(Button anchor)
    {
        bool active = StormGbcaUiState.Instance.IsEmulationActive;
        bool paused = StormGbcaUiState.Instance.IsPaused;
        var flyout = new MenuFlyout();
        flyout.Items.Add(CreateMenuItem(active ? (paused ? GbcaLoc.T("MenuResume") : GbcaLoc.T("MenuPause")) : GbcaLoc.T("LaunchGame"), () =>
        {
            if (active) StormGbcaUiState.Instance.TogglePause();
            else StormGbcaUiState.Instance.StartEmulation();
        }));
        flyout.Items.Add(new Separator());
        flyout.Items.Add(CreateMenuItem(GbcaLoc.T("MenuReset"), () => StormGbcaUiState.Instance.Emulator.Reset(), active));
        flyout.Items.Add(CreateMenuItem(GbcaLoc.T("MenuQuickSave"), () => StormGbcaUiState.Instance.Emulator.SaveState(0), active));
        flyout.Items.Add(CreateMenuItem(GbcaLoc.T("MenuQuickLoad"), () =>
        {
            if (StormGbcaUiState.Instance.RetroAchievements.IsFeatureAllowedInCurrentMode("loadstate"))
            {
                StormGbcaUiState.Instance.Emulator.LoadState(0);
            }
        }, active));
        flyout.ShowAt(anchor, true);
    }

    private void ShowHelpMenu(Button anchor)
    {
        var flyout = new MenuFlyout();
        flyout.Items.Add(CreateMenuItem(GbcaLoc.T("MenuCheckUpdates"), () => StormGbcaUiState.Instance.RequestUpdateCheck(true)));
        flyout.Items.Add(CreateMenuItem(GbcaLoc.T("MenuAbout"), ShowAboutModal));
        flyout.ShowAt(anchor, true);
    }

    private void ShowAboutModal()
    {
        var card = new AuroraGlassCard { Width = 460, Padding = new Thickness(22), SpotlightEnabled = true };
        var sp = new StackPanel { Spacing = 10 };
        sp.Children.Add(new TextBlock { Text = $"STORM GBCA {GbcaLoc.AppVersion}", FontSize = 18, FontWeight = FontWeight.Black, Foreground = StormThemeBrushes.Accent });
        sp.Children.Add(new TextBlock { Text = GbcaLoc.T("AppSubtitle"), FontSize = 12.5, Foreground = StormThemeBrushes.PrimaryText, TextWrapping = TextWrapping.Wrap });
        sp.Children.Add(new TextBlock { Text = $"Ядра: mGBA + SameBoy | Версия storm_gbca_core: {NativeCoreBindings.GetVersionString()}", FontSize = 11.5, Foreground = StormThemeBrushes.SecondaryText, TextWrapping = TextWrapping.Wrap });
        var closeBtn = new AuroraVolumetricButton { Height = 32, Width = 140, HorizontalAlignment = HorizontalAlignment.Right };
        closeBtn.Content = new TextBlock { Text = GbcaLoc.T("ActionClose"), FontWeight = FontWeight.SemiBold, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
        closeBtn.Click += (_, _) => _modalHost.Dismiss();
        sp.Children.Add(closeBtn);
        card.Content = sp;
        _modalHost.Content = card;
        _modalHost.IsOpen = true;
    }

    private async Task PickAndOpenRomAsync()
    {
        var top = TopLevel.GetTopLevel(this);
        if (top?.StorageProvider == null) return;
        var files = await top.StorageProvider.OpenFilePickerAsync(new FilePickerOpenOptions
        {
            Title = GbcaLoc.T("MenuOpenRom"),
            AllowMultiple = false,
            FileTypeFilter =
            [
                new FilePickerFileType("Game Boy / GBC / GBA ROMs") { Patterns = ["*.gb", "*.gbc", "*.gba", "*.sgb", "*.zip", "*.7z"] },
                FilePickerFileTypes.All
            ]
        });
        string? path = files.Count > 0 ? files[0].TryGetLocalPath() : null;
        if (!string.IsNullOrWhiteSpace(path) && File.Exists(path))
        {
            StormGbcaUiState.Instance.StartRomFromPath(path);
        }
    }

    private async Task PickAndScanFolderAsync()
    {
        var top = TopLevel.GetTopLevel(this);
        if (top?.StorageProvider == null) return;
        var folders = await top.StorageProvider.OpenFolderPickerAsync(new FolderPickerOpenOptions { Title = GbcaLoc.T("MenuScanFolder"), AllowMultiple = false });
        string? folder = folders.Count > 0 ? folders[0].TryGetLocalPath() : null;
        if (!string.IsNullOrWhiteSpace(folder) && Directory.Exists(folder))
        {
            if (!StormGbcaUiState.Instance.Settings.RomDirectories.Contains(folder, StringComparer.OrdinalIgnoreCase))
            {
                StormGbcaUiState.Instance.Settings.RomDirectories.Add(folder);
                StormGbcaUiState.Instance.SaveSettings();
            }
            await StormGbcaUiState.Instance.Library.ScanDirectoriesAsync(StormGbcaUiState.Instance.Settings.RomDirectories);
        }
    }

    private async Task CheckAndShowUpdateModalAsync(bool manual)
    {
        StormUpdateModal? modal = null;
        if (manual)
        {
            modal = new StormUpdateModal(StormUpdateModal.UpdateModalMode.Checking, onRetry: () => _ = CheckAndShowUpdateModalAsync(true));
            modal.CloseRequested += () => _modalHost.Dismiss();
            _modalHost.Content = modal;
            _modalHost.IsOpen = true;
        }

        try
        {
            var curVer = AuroraDualMirrorUpdater.TryParseSemanticVersion(GbcaLoc.AppVersion, out var parsed) ? parsed : new Version(1, 2, 0);
            var updater = new AuroraDualMirrorUpdater();
            var res = await updater.CheckStormGbcaUpdateAsync(curVer);

            await Dispatcher.UIThread.InvokeAsync(() =>
            {
                if (res.IsUpdateAvailable)
                {
                    string cleanNew = res.LatestVersion.ToString().TrimStart('v', 'V');
                    if (!manual && string.Equals(StormGbcaUiState.Instance.Settings.SkippedUpdateVersion, cleanNew, StringComparison.OrdinalIgnoreCase))
                    {
                        return;
                    }
                    if (modal == null)
                    {
                        modal = new StormUpdateModal(StormUpdateModal.UpdateModalMode.UpdateAvailable, res, onRetry: () => _ = CheckAndShowUpdateModalAsync(true));
                        modal.CloseRequested += () => _modalHost.Dismiss();
                        _modalHost.Content = modal;
                        _modalHost.IsOpen = true;
                    }
                    else
                    {
                        modal.UpdateState(StormUpdateModal.UpdateModalMode.UpdateAvailable, res);
                    }
                }
                else if (manual && modal != null)
                {
                    modal.UpdateState(res.CheckSucceeded ? StormUpdateModal.UpdateModalMode.UpToDate : StormUpdateModal.UpdateModalMode.Error, res);
                }
            });
        }
        catch (Exception ex)
        {
            if (manual && modal != null)
            {
                var curVer = new Version(1, 2, 0);
                await Dispatcher.UIThread.InvokeAsync(() =>
                {
                    modal.UpdateState(StormUpdateModal.UpdateModalMode.Error, new AuroraUpdateCheckResult(false, curVer, curVer, string.Empty, string.Empty, null, "None", CheckSucceeded: false, ErrorMessage: ex.Message));
                });
            }
        }
    }

    private void ShowCommandPalette()
    {
        var palette = new CommandPaletteModal();
        palette.CloseRequested += () => _modalHost.Dismiss();
        _modalHost.Content = palette;
        _modalHost.IsOpen = true;
    }

    private void ShowRomModal(GbcaRomRecord rom, int tabIdx)
    {
        var modal = new RomDetailsModal(rom, tabIdx);
        modal.CloseRequested += () => _modalHost.Dismiss();
        _modalHost.Content = modal;
        _modalHost.IsOpen = true;
    }

    private void ShowCoverViewer(GbcaRomRecord rom)
    {
        var viewer = new CoverViewerModal(rom);
        viewer.CloseRequested += () => _modalHost.Dismiss();
        _modalHost.Content = viewer;
        _modalHost.IsOpen = true;
    }

    private void OnNavSectionChanged(NavSectionKind section)
    {
        if (StormGbcaUiState.Instance.IsEmulationActive) return;
        _bentoWorkspace.IsVisible = section is NavSectionKind.Library or NavSectionKind.Favorites or NavSectionKind.Recent;
        _skinsView.IsVisible = section == NavSectionKind.SkinsAndOverlays;
        _achievementsView.IsVisible = section == NavSectionKind.Achievements;
        _settingsView.IsVisible = section == NavSectionKind.Settings;
        if (_skinsView.IsVisible) _skinsView.RefreshBody();
        if (_achievementsView.IsVisible) _achievementsView.RefreshData();
    }

    private void SwitchToEmulation(bool active)
    {
        var sec = StormGbcaUiState.Instance.ActiveNavSection;
        _emulationView.IsVisible = active;
        _bodyHeaderGrid.IsVisible = !active;
        _bentoWorkspace.IsVisible = !active && sec is NavSectionKind.Library or NavSectionKind.Favorites or NavSectionKind.Recent;
        _skinsView.IsVisible = !active && sec == NavSectionKind.SkinsAndOverlays;
        _achievementsView.IsVisible = !active && sec == NavSectionKind.Achievements;
        _settingsView.IsVisible = !active && sec == NavSectionKind.Settings;
        UpdateResponsiveChrome();
        if (active) _emulationView.Focus();
    }

    private void OnFullscreenChanged(bool isFullscreen)
    {
        if (_titleBar != null) _titleBar.IsVisible = !isFullscreen;
        _statusBar.IsVisible = !isFullscreen;
        if (!isFullscreen)
        {
            _bodyHeaderGrid.IsVisible = !StormGbcaUiState.Instance.IsEmulationActive;
            UpdateResponsiveChrome();
        }
    }

    private void UpdateResponsiveChrome()
    {
        double w = Bounds.Width;
        if (w <= 0) return;
        bool emulating = StormGbcaUiState.Instance.IsEmulationActive;
        bool isDesktop = w >= 840;
        _navRail.IsVisible = !emulating && isDesktop;
        _navDock.IsVisible = !emulating && !isDesktop;
        _inspectorPane.IsVisible = !emulating && w >= 1240;
        _centerGrid.Margin = _navDock.IsVisible ? new Thickness(0, 0, 0, 76) : new Thickness(0);
    }

    public bool TryDismissOpenModal()
    {
        if (!_modalHost.IsOpen) return false;
        if (_modalHost.Content is StormUpdateModal upd) upd.RequestSafeClose();
        else _modalHost.Dismiss();
        return true;
    }

    protected override void OnPropertyChanged(AvaloniaPropertyChangedEventArgs change)
    {
        base.OnPropertyChanged(change);
        if (change.Property == BoundsProperty) UpdateResponsiveChrome();
    }

    private void OnRootKeyDown(object? sender, KeyEventArgs e)
    {
        if (e.Key == Key.K && (e.KeyModifiers & KeyModifiers.Control) != 0)
        {
            ShowCommandPalette();
            e.Handled = true;
            return;
        }
        if (e.Key == Key.Escape && TryDismissOpenModal())
        {
            e.Handled = true;
        }
    }

    private void OnDrop(object? sender, DragEventArgs e)
    {
        _dropOverlay.IsVisible = false;
        var files = e.DataTransfer?.TryGetFiles();
        if (files == null) return;

        foreach (var item in files)
        {
            string? path = item.TryGetLocalPath();
            if (string.IsNullOrWhiteSpace(path)) continue;
            if (Directory.Exists(path))
            {
                _ = StormGbcaUiState.Instance.Library.ScanDirectoriesAsync([path]);
            }
            else if (File.Exists(path) && GbcaRomLibrary.IsSupportedRomExtension(path))
            {
                StormGbcaUiState.Instance.StartRomFromPath(path);
                break;
            }
        }
    }
}
