// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Mozilla Public License, Version 2.0 (MPL-2.0).

using System.Diagnostics;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Controls.Primitives;
using Avalonia.Input;
using Avalonia.Layout;
using Avalonia.Media;
using Avalonia.Platform.Storage;
using Avalonia.Threading;
using Storm.AuroraFluid.Kit.Controls;
using Storm.AuroraFluid.Kit.Icons;
using Storm.AuroraFluid.Kit.Services;
using Storm.AuroraFluid.Kit.Themes;
using StormGbca.Core.Configuration;
using StormGbca.Core.Engine;
using StormGbca.Core.Localization;
using StormGbca.Core.Native;
using StormGbca.Core.Paths;
using StormGbca.UI.Common;
using StormGbca.UI.Views.Emulation;

namespace StormGbca.UI.Views.Settings;

/// <summary>
/// Full Settings View covering Core (mGBA/SameBoy + BIOS + ROM Folders + Run-Ahead + Rewind),
/// Video (7-mode Color Correction + 28 DMG Palettes + SGB Borders + LCD Ghosting + Filters),
/// Audio (48kHz + High-Pass + Hermite + DRC + 6-Channel Mixer),
/// Per-System 3D Skins & Overlays, Input (Keyboard with theme-aware 3D keycaps + XInput),
/// RetroAchievements, and 9 STORM Themes / 13 Accents / 10 Fonts / 11 Languages / Dual-Mirror Updater.
/// </summary>
public class SettingsView : Grid
{
    private readonly AuroraSearchBox _searchBox;
    private readonly ScrollViewer _tabsScroll;
    private readonly StackPanel _tabsRail;
    private readonly AuroraGlassCard _glassCard;
    private readonly ContentControl _contentPresenter;
    private readonly TextBlock _savedFeedbackText;
    private readonly TextBlock _savePillLabel;
    private readonly DispatcherTimer _saveDebounceTimer;
    private readonly List<(int Index, AuroraIconKind Icon, string Title, Border Button)> _tabButtons = [];

    private int _activeTabIndex;
    private string _filterQuery = string.Empty;
    private string? _listeningActionKey;
    private bool _pendingDebouncedBanner;
    private bool _isCompactLayout;
    private bool _isWideInputCols = true;
    private string _lastRefreshSignature = string.Empty;
    private UniformGrid? _inputBindingsGrid;

    private StormLinkMode _mpSelectedMode = StormLinkMode.LanHost;
    private readonly List<GbcaLanRoomInfo> _discoveredRooms = [];
    private string _mpFeedback = string.Empty;
    private bool _pendingHardcoreConfirm;
    private TextBlock? _videoRendererTitle;

    public SettingsView()
    {
        Margin = new Thickness(16, 8, 16, 12);
        RowDefinitions = new RowDefinitions("Auto,*");
        ColumnDefinitions = new ColumnDefinitions("200,*");

        _saveDebounceTimer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(250) };
        _saveDebounceTimer.Tick += (_, _) =>
        {
            _saveDebounceTimer.Stop();
            CommitSave(_pendingDebouncedBanner);
            _pendingDebouncedBanner = false;
        };

        var topHeader = new Grid
        {
            ColumnDefinitions = new ColumnDefinitions("*,Auto"),
            Margin = new Thickness(0, 0, 0, 10)
        };

        _searchBox = new AuroraSearchBox
        {
            PlaceholderText = GbcaLoc.T("SearchSettingsPlaceholder"),
            HotkeyHint = "Ctrl+F",
            Height = 36,
            MaxWidth = 360,
            HorizontalAlignment = HorizontalAlignment.Stretch,
            Margin = new Thickness(0, 0, 14, 0)
        };
        _searchBox.TextChanged += (_, _) =>
        {
            _filterQuery = _searchBox.Text ?? string.Empty;
            LoadTab(_activeTabIndex);
        };
        topHeader.Children.Add(_searchBox);

        var topRight = new StackPanel
        {
            Orientation = Orientation.Horizontal,
            Spacing = 12,
            HorizontalAlignment = HorizontalAlignment.Right,
            VerticalAlignment = VerticalAlignment.Center
        };
        _savedFeedbackText = new TextBlock
        {
            Text = string.Empty,
            FontSize = 11.5,
            FontWeight = FontWeight.SemiBold,
            Foreground = GetSuccessTextBrush(),
            VerticalAlignment = VerticalAlignment.Center,
            IsVisible = false
        };
        topRight.Children.Add(_savedFeedbackText);

        var savePill = new Border
        {
            Height = 36,
            CornerRadius = new CornerRadius(8),
            Background = new SolidColorBrush(Color.FromArgb(40, 16, 185, 129)),
            BorderBrush = new SolidColorBrush(Color.Parse("#10B981")),
            BorderThickness = new Thickness(1),
            Padding = new Thickness(14, 0),
            Cursor = new Cursor(StandardCursorType.Hand)
        };
        var spStack = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 6, VerticalAlignment = VerticalAlignment.Center };
        spStack.Children.Add(new AuroraVectorIcon { Kind = AuroraIconKind.CheckSuccess, Size = 14, VerticalAlignment = VerticalAlignment.Center });
        _savePillLabel = new TextBlock
        {
            Text = GbcaLoc.T("SaveSettingsBtn"),
            FontSize = 11.5,
            FontWeight = FontWeight.Bold,
            Foreground = GetSuccessTextBrush(),
            VerticalAlignment = VerticalAlignment.Center
        };
        spStack.Children.Add(_savePillLabel);
        savePill.Child = spStack;
        savePill.PointerPressed += (_, _) =>
        {
            AuroraTactileFeedbackService.Instance.PlayAchievementChime();
            CommitSave(showBanner: true);
        };
        topRight.Children.Add(savePill);
        topHeader.Children.Add(topRight);
        Grid.SetColumn(topRight, 1);

        Children.Add(topHeader);
        Grid.SetColumnSpan(topHeader, 2);

        _tabsScroll = new ScrollViewer
        {
            Padding = new Thickness(0, 0, 10, 0),
            HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled,
            VerticalScrollBarVisibility = ScrollBarVisibility.Auto
        };
        _tabsRail = new StackPanel { Spacing = 4, Orientation = Orientation.Vertical };
        AddTabHeader(0, AuroraIconKind.SettingsCore, "TabCoreOpt");
        AddTabHeader(1, AuroraIconKind.ShaderPrism, "TabVideo");
        AddTabHeader(2, AuroraIconKind.RomCartridge, "TabCoversBadges");
        AddTabHeader(3, AuroraIconKind.SyncDevices, "TabMultiplayer");
        AddTabHeader(4, AuroraIconKind.AudioWaveDsp, "TabAudio");
        AddTabHeader(5, AuroraIconKind.DualScreenLayout, "TabSkins");
        AddTabHeader(6, AuroraIconKind.GamepadController, "TabInput");
        AddTabHeader(7, AuroraIconKind.TrophyCrown, "TabRA");
        AddTabHeader(8, AuroraIconKind.ThemePalette, "TabThemes");
        _tabsScroll.Content = _tabsRail;
        Children.Add(_tabsScroll);
        Grid.SetRow(_tabsScroll, 1);

        _glassCard = new AuroraGlassCard { SpotlightEnabled = true, Margin = new Thickness(6, 0, 0, 0) };
        _contentPresenter = new ContentControl();
        _glassCard.Content = _contentPresenter;
        Children.Add(_glassCard);
        Grid.SetRow(_glassCard, 1);
        Grid.SetColumn(_glassCard, 1);

        KeyDown += OnKeyCaptureKeyDown;
        SizeChanged += (_, e) => UpdateResponsiveLayout(e.NewSize.Width);
        GbcaLoc.LanguageChanged += _ => Dispatcher.UIThread.Post(() => RefreshTabLabelsAndContent(force: false));
        StormThemeBrushes.BindTheme(this, () => RefreshTabLabelsAndContent(force: false));
        SkiaLcdViewportControl.RendererInfoDetected += info =>
        {
            if (_videoRendererTitle != null)
            {
                _videoRendererTitle.Text = $"{GbcaLoc.T("GpuRendererLabel")} {info}";
            }
        };
    }

    private static IBrush GetSuccessTextBrush()
        => new SolidColorBrush(StormThemeBrushes.IsDark ? Color.Parse("#34D399") : Color.Parse("#047857"));

    private static IBrush GetDangerTextBrush()
        => new SolidColorBrush(StormThemeBrushes.IsDark ? Color.Parse("#F87171") : Color.Parse("#DC2626"));

    private void UpdateResponsiveLayout(double width)
    {
        if (width <= 0) return;

        bool compact = width < 960;
        if (compact != _isCompactLayout)
        {
            _isCompactLayout = compact;
            if (compact)
            {
                RowDefinitions = new RowDefinitions("Auto,Auto,*");
                ColumnDefinitions = new ColumnDefinitions("*,Auto");
                _tabsRail.Orientation = Orientation.Horizontal;
                _tabsRail.Spacing = 6;
                _tabsScroll.Padding = new Thickness(0, 0, 0, 8);
                _tabsScroll.HorizontalScrollBarVisibility = ScrollBarVisibility.Auto;
                _tabsScroll.VerticalScrollBarVisibility = ScrollBarVisibility.Disabled;
                Grid.SetRow(_tabsScroll, 1);
                Grid.SetColumn(_tabsScroll, 0);
                Grid.SetColumnSpan(_tabsScroll, 2);

                _glassCard.Margin = new Thickness(0, 4, 0, 0);
                Grid.SetRow(_glassCard, 2);
                Grid.SetColumn(_glassCard, 0);
                Grid.SetColumnSpan(_glassCard, 2);
            }
            else
            {
                RowDefinitions = new RowDefinitions("Auto,*");
                ColumnDefinitions = new ColumnDefinitions("200,*");
                _tabsRail.Orientation = Orientation.Vertical;
                _tabsRail.Spacing = 4;
                _tabsScroll.Padding = new Thickness(0, 0, 10, 0);
                _tabsScroll.HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled;
                _tabsScroll.VerticalScrollBarVisibility = ScrollBarVisibility.Auto;
                Grid.SetRow(_tabsScroll, 1);
                Grid.SetColumn(_tabsScroll, 0);
                Grid.SetColumnSpan(_tabsScroll, 1);

                _glassCard.Margin = new Thickness(6, 0, 0, 0);
                Grid.SetRow(_glassCard, 1);
                Grid.SetColumn(_glassCard, 1);
                Grid.SetColumnSpan(_glassCard, 1);
            }
        }

        bool wideInput = width >= 900;
        if (wideInput != _isWideInputCols)
        {
            _isWideInputCols = wideInput;
            if (_inputBindingsGrid != null)
            {
                _inputBindingsGrid.Columns = wideInput ? 2 : 1;
            }
        }
    }

    private void CommitSave(bool showBanner = false)
    {
        _saveDebounceTimer.Stop();
        _pendingDebouncedBanner = false;
        StormGbcaUiState.Instance.SaveSettings(showBanner);
        _savedFeedbackText.Foreground = GetSuccessTextBrush();
        _savedFeedbackText.Text = GbcaLoc.T("SettingsSavedMsg");
        _savedFeedbackText.IsVisible = true;
        DispatcherTimer.RunOnce(() => _savedFeedbackText.IsVisible = false, TimeSpan.FromSeconds(2));
    }

    private void ScheduleDebouncedSave(bool showBanner = false)
    {
        _pendingDebouncedBanner = _pendingDebouncedBanner || showBanner;
        _saveDebounceTimer.Stop();
        _saveDebounceTimer.Start();
    }

    private void RefreshTabLabelsAndContent(bool force)
    {
        var theme = AuroraThemeManager.Instance.CurrentTheme;
        string signature = $"{theme.Id}|{AuroraThemeManager.Instance.CurrentAccentOverride}|{AuroraThemeManager.Instance.CurrentTypography}|{GbcaLoc.CurrentLanguage}";
        if (!force && string.Equals(_lastRefreshSignature, signature, StringComparison.Ordinal))
        {
            return;
        }
        _lastRefreshSignature = signature;

        _searchBox.PlaceholderText = GbcaLoc.T("SearchSettingsPlaceholder");
        _savePillLabel.Text = GbcaLoc.T("SaveSettingsBtn");
        _savePillLabel.Foreground = GetSuccessTextBrush();
        _savedFeedbackText.Foreground = GetSuccessTextBrush();

        foreach (var item in _tabButtons)
        {
            if (item.Button.Child is Grid g && g.Children.Count > 1 && g.Children[1] is TextBlock tb)
            {
                tb.Text = GbcaLoc.T(item.Title);
            }
        }
        LoadTab(_activeTabIndex);
    }

    private void AddTabHeader(int idx, AuroraIconKind icon, string title)
    {
        var btn = new Border
        {
            Height = 42,
            CornerRadius = new CornerRadius(10),
            Padding = new Thickness(10, 0),
            Background = Brushes.Transparent,
            Cursor = new Cursor(StandardCursorType.Hand)
        };
        var row = new Grid { ColumnDefinitions = new ColumnDefinitions("Auto,*") };
        row.Children.Add(new AuroraVectorIcon { Kind = icon, Size = 18, VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(0, 0, 10, 0) });
        var tb = new TextBlock { Text = GbcaLoc.T(title), FontSize = 12, FontWeight = FontWeight.Medium, VerticalAlignment = VerticalAlignment.Center, TextTrimming = TextTrimming.CharacterEllipsis };
        row.Children.Add(tb);
        Grid.SetColumn(tb, 1);
        btn.Child = row;
        btn.PointerPressed += (_, _) =>
        {
            AuroraTactileFeedbackService.Instance.PlayClick();
            if (_saveDebounceTimer.IsEnabled)
            {
                CommitSave(false);
            }
            LoadTab(idx);
        };
        _tabsRail.Children.Add(btn);
        _tabButtons.Add((idx, icon, title, btn));
    }

    private void LoadTab(int index)
    {
        _activeTabIndex = index;
        _inputBindingsGrid = null;
        var primary = StormThemeBrushes.AccentPrimaryColor;

        foreach (var item in _tabButtons)
        {
            bool sel = item.Index == index;
            item.Button.Background = sel ? new SolidColorBrush(Color.FromArgb(50, primary.R, primary.G, primary.B)) : Brushes.Transparent;
            item.Button.BorderBrush = sel ? new SolidColorBrush(primary) : Brushes.Transparent;
            item.Button.BorderThickness = new Thickness(sel ? 1 : 0);
            if (item.Button.Child is Grid g && g.Children.Count > 1 && g.Children[1] is TextBlock tb)
            {
                tb.Foreground = sel ? StormThemeBrushes.PrimaryText : StormThemeBrushes.SecondaryText;
            }
        }

        var scroll = new ScrollViewer { Padding = new Thickness(18, 16, 22, 16), HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled };
        var container = new StackPanel { Spacing = 12 };
        string q = _filterQuery.Trim();

        if (q.Length >= 2)
        {
            var all = new StackPanel { Spacing = 12 };
            BuildCoreTab(all);
            BuildVideoTab(all);
            BuildCoversBadgesTab(all);
            BuildMultiplayerTab(all);
            BuildAudioTab(all);
            BuildSkinsSummaryTab(all);
            BuildInputTab(all);
            BuildRaTab(all);
            BuildThemesTab(all);
            foreach (var c in all.Children.ToList())
            {
                if (ControlContainsText(c, q))
                {
                    all.Children.Remove(c);
                    container.Children.Add(c);
                }
            }
            if (container.Children.Count == 0)
            {
                container.Children.Add(new TextBlock { Text = $"По запросу '{q}' ничего не найдено.", FontSize = 12, Foreground = StormThemeBrushes.SecondaryText });
            }
        }
        else
        {
            switch (index)
            {
                case 0: BuildCoreTab(container); break;
                case 1: BuildVideoTab(container); break;
                case 2: BuildCoversBadgesTab(container); break;
                case 3: BuildMultiplayerTab(container); break;
                case 4: BuildAudioTab(container); break;
                case 5: BuildSkinsSummaryTab(container); break;
                case 6: BuildInputTab(container); break;
                case 7: BuildRaTab(container); break;
                case 8: BuildThemesTab(container); break;
            }
        }

        scroll.Content = container;
        _contentPresenter.Content = scroll;
    }

    private static bool ControlContainsText(Control control, string query)
    {
        if (control is TextBlock tb && tb.Text?.Contains(query, StringComparison.OrdinalIgnoreCase) == true) return true;
        if (control is AuroraNeonCheckBox cb && cb.Label?.Contains(query, StringComparison.OrdinalIgnoreCase) == true) return true;
        if (control is Panel p) return p.Children.Any(c => ControlContainsText(c, query));
        if (control is Border b && b.Child is Control bc) return ControlContainsText(bc, query);
        if (control is ContentControl cc && cc.Content is Control c2) return ControlContainsText(c2, query);
        return false;
    }

    private void BuildCoreTab(StackPanel p)
    {
        var s = StormGbcaUiState.Instance.Settings;
        p.Children.Add(CreateSectionTitle(GbcaLoc.T("OptProfileTitle")));
        p.Children.Add(CreateCombo(GbcaLoc.T("OptProfileTitle") + ":",
        [
            GbcaLoc.T("OptLite"),
            GbcaLoc.T("OptBalanced"),
            GbcaLoc.T("OptPerformance"),
            GbcaLoc.T("OptAccuracy")
        ], (int)s.OptimizationProfile, i =>
        {
            s.OptimizationProfile = (StormOptimizationProfile)i;
            if (s.OptimizationProfile == StormOptimizationProfile.Lite)
            {
                s.ResolutionScale = 1;
                s.UpscaleMode = StormUpscaleMode.None;
                s.RewindEnabled = false;
                s.RunAheadFrames = 0;
            }
            else if (s.OptimizationProfile == StormOptimizationProfile.Balanced)
            {
                s.RewindEnabled = true;
            }
            else if (s.OptimizationProfile == StormOptimizationProfile.Performance)
            {
                s.RewindEnabled = true;
                s.RunAheadFrames = Math.Max(s.RunAheadFrames, 1);
            }
            else if (s.OptimizationProfile == StormOptimizationProfile.HighAccuracy)
            {
                s.AudioInterpMode = StormAudioInterpMode.Hermite;
                s.AudioHighpassMode = StormAudioHighpassMode.Accurate;
                s.RewindBufferMb = Math.Max(s.RewindBufferMb, 24);
            }
        }, reloadAfter: true));
        p.Children.Add(new TextBlock
        {
            Text = $"CPU Cores: {Environment.ProcessorCount} | 64-bit OS: {Environment.Is64BitOperatingSystem} | Recommended: {(Environment.ProcessorCount <= 4 ? "Lite" : "Balanced")}",
            FontSize = 11,
            Foreground = StormThemeBrushes.SecondaryText,
            TextWrapping = TextWrapping.Wrap
        });

        // ROM Folders Manager Card
        p.Children.Add(CreateSectionTitle(GbcaLoc.T("RomFoldersTitle")));
        p.Children.Add(BuildRomFoldersCard(s));

        p.Children.Add(CreateSectionTitle("Выбор эмулирующих ядер, BIOS и перемотки"));
        p.Children.Add(CreateCombo("Ядро для Game Boy (GB):", ["mGBA (Высокая скорость)", "SameBoy (Максимальная точность)"], (int)s.PreferredCoreGb, i => s.PreferredCoreGb = (StormCoreBackend)i));
        p.Children.Add(CreateCombo("Ядро для Game Boy Color (GBC):", ["mGBA (Высокая скорость)", "SameBoy (Максимальная точность)"], (int)s.PreferredCoreGbc, i => s.PreferredCoreGbc = (StormCoreBackend)i));
        p.Children.Add(CreateCombo("Ядро для Game Boy Advance (GBA):", ["mGBA (Высокая точность)"], 0, _ => s.PreferredCoreGba = StormCoreBackend.Mgba));
        p.Children.Add(CreateCombo("Модель оборудования Game Boy:", ["Автоопределение", "DMG (Классический)", "MGB (Pocket)", "SGB (Super Game Boy)", "SGB2", "CGB (Color)", "AGB (GBA)"], (int)s.GbModel, i => s.GbModel = (StormGbModel)i));
        p.Children.Add(CreateCheck("Пропускать заставку BIOS при запуске", s.SkipBiosIntro, v => s.SkipBiosIntro = v));
        bool hcCore = StormGbcaUiState.Instance.IsEmulationActive && StormGbcaUiState.Instance.Emulator.IsHardcoreMode;
        var rwCheck = CreateCheck(hcCore ? "Включить перемотку времени назад (заблокировано в Hardcore)" : "Включить перемотку времени назад (Rewind)", hcCore ? false : s.RewindEnabled, v => { if (!hcCore) s.RewindEnabled = v; });
        if (hcCore)
        {
            rwCheck.IsEnabled = false;
            rwCheck.Opacity = 0.5;
        }
        p.Children.Add(rwCheck);
        p.Children.Add(CreateSlider("Упреждающие кадры (Run-Ahead):", 0, 2, s.RunAheadFrames, "кадра", v => s.RunAheadFrames = (int)v));
        p.Children.Add(CreateSlider("Буфер перемотки назад:", 8, 64, s.RewindBufferMb, "МБ", v => s.RewindBufferMb = (int)v));
        p.Children.Add(CreateSlider("Скорость ускорения (Fast-Forward):", 1, 8, s.FastForwardMultiplier, "x", v => s.FastForwardMultiplier = (float)v));

        p.Children.Add(CreateSectionTitle("Файлы BIOS (необязательно)"));
        p.Children.Add(CreatePathRow("BIOS Game Boy:", s.BiosGbPath, ["*.bin"], t => { s.BiosGbPath = t; ScheduleDebouncedSave(); }, tImmediate => { s.BiosGbPath = tImmediate; CommitSave(); }));
        p.Children.Add(CreatePathRow("BIOS Game Boy Color:", s.BiosGbcPath, ["*.bin"], t => { s.BiosGbcPath = t; ScheduleDebouncedSave(); }, tImmediate => { s.BiosGbcPath = tImmediate; CommitSave(); }));
        p.Children.Add(CreatePathRow("BIOS Game Boy Advance:", s.BiosGbaPath, ["*.bin"], t => { s.BiosGbaPath = t; ScheduleDebouncedSave(); }, tImmediate => { s.BiosGbaPath = tImmediate; CommitSave(); }));
        p.Children.Add(CreatePathRow("BIOS Super Game Boy:", s.BiosSgbPath, ["*.bin"], t => { s.BiosSgbPath = t; ScheduleDebouncedSave(); }, tImmediate => { s.BiosSgbPath = tImmediate; CommitSave(); }));
    }

    private Border BuildRomFoldersCard(GbcaAppSettings s)
    {
        var card = new Border
        {
            Padding = new Thickness(12, 10),
            CornerRadius = new CornerRadius(10),
            Background = StormThemeBrushes.CardSurface,
            BorderBrush = StormThemeBrushes.SubtleBorder,
            BorderThickness = new Thickness(1)
        };
        var col = new StackPanel { Spacing = 8 };
        col.Children.Add(new TextBlock
        {
            Text = $"{GbcaLoc.T("RomFoldersDesc")} ({StormGbcaPaths.RomsDirectory})",
            FontSize = 11,
            Foreground = StormThemeBrushes.SecondaryText,
            TextWrapping = TextWrapping.Wrap
        });

        if (s.RomDirectories.Count == 0)
        {
            col.Children.Add(new TextBlock
            {
                Text = GbcaLoc.T("RomFoldersEmpty"),
                FontSize = 11.5,
                Foreground = StormThemeBrushes.SecondaryText
            });
        }
        else
        {
            foreach (string dir in s.RomDirectories.ToList())
            {
                string captured = dir;
                var row = new Border
                {
                    Padding = new Thickness(10, 6),
                    CornerRadius = new CornerRadius(8),
                    Background = StormThemeBrushes.RowSurface,
                    BorderBrush = StormThemeBrushes.SubtleBorder,
                    BorderThickness = new Thickness(1)
                };
                var rg = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto") };
                rg.Children.Add(new TextBlock
                {
                    Text = captured,
                    FontSize = 11.5,
                    Foreground = StormThemeBrushes.PrimaryText,
                    VerticalAlignment = VerticalAlignment.Center,
                    TextTrimming = TextTrimming.CharacterEllipsis,
                    Margin = new Thickness(0, 0, 10, 0)
                });
                var removeBtn = new AuroraVolumetricButton
                {
                    Height = 28,
                    Padding = new Thickness(10, 0)
                };
                removeBtn.Content = new TextBlock
                {
                    Text = GbcaLoc.T("BtnRemoveFolder"),
                    FontSize = 11,
                    FontWeight = FontWeight.SemiBold,
                    Foreground = StormThemeBrushes.PrimaryText,
                    VerticalAlignment = VerticalAlignment.Center
                };
                removeBtn.Click += (_, _) =>
                {
                    s.RomDirectories.RemoveAll(d => string.Equals(d, captured, StringComparison.OrdinalIgnoreCase));
                    CommitSave();
                    _ = StormGbcaUiState.Instance.Library.RescanAsync(s.RomDirectories);
                    LoadTab(_activeTabIndex);
                };
                rg.Children.Add(removeBtn);
                Grid.SetColumn(removeBtn, 1);
                row.Child = rg;
                col.Children.Add(row);
            }
        }

        var actions = new WrapPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 2, 0, 0) };
        var addBtn = new AuroraVolumetricButton
        {
            Height = 32,
            Padding = new Thickness(12, 0),
            Margin = new Thickness(0, 0, 8, 4)
        };
        addBtn.Content = new TextBlock
        {
            Text = GbcaLoc.T("BtnAddFolder"),
            FontSize = 11.5,
            FontWeight = FontWeight.SemiBold,
            Foreground = StormThemeBrushes.PrimaryText,
            VerticalAlignment = VerticalAlignment.Center
        };
        addBtn.Click += async (_, _) =>
        {
            var top = TopLevel.GetTopLevel(this);
            if (top?.StorageProvider == null) return;
            var folders = await top.StorageProvider.OpenFolderPickerAsync(new FolderPickerOpenOptions
            {
                Title = GbcaLoc.T("MenuScanFolder"),
                AllowMultiple = false
            });
            string? folder = folders.Count > 0 ? folders[0].TryGetLocalPath() : null;
            if (!string.IsNullOrWhiteSpace(folder) && Directory.Exists(folder))
            {
                if (!s.RomDirectories.Contains(folder, StringComparer.OrdinalIgnoreCase))
                {
                    s.RomDirectories.Add(folder);
                    CommitSave();
                    _ = StormGbcaUiState.Instance.Library.RescanAsync(s.RomDirectories);
                    LoadTab(_activeTabIndex);
                }
            }
        };
        actions.Children.Add(addBtn);

        var rescanBtn = new AuroraVolumetricButton
        {
            Height = 32,
            Padding = new Thickness(12, 0),
            Margin = new Thickness(0, 0, 0, 4)
        };
        rescanBtn.Content = new TextBlock
        {
            Text = GbcaLoc.T("MenuRescanLibrary"),
            FontSize = 11.5,
            FontWeight = FontWeight.SemiBold,
            Foreground = StormThemeBrushes.PrimaryText,
            VerticalAlignment = VerticalAlignment.Center
        };
        rescanBtn.Click += (_, _) =>
        {
            _ = StormGbcaUiState.Instance.Library.RescanAsync(s.RomDirectories);
            StormGbcaUiState.Instance.NotifyStatus(GbcaLoc.T("MenuRescanLibrary"));
        };
        actions.Children.Add(rescanBtn);
        col.Children.Add(actions);

        card.Child = col;
        return card;
    }

    private void BuildVideoTab(StackPanel p)
    {
        var s = StormGbcaUiState.Instance.Settings;

        var gpuCard = new Border
        {
            Padding = new Thickness(12, 9),
            Margin = new Thickness(0, 0, 0, 8),
            CornerRadius = new CornerRadius(10),
            Background = StormThemeBrushes.RowSurface,
            BorderBrush = StormThemeBrushes.SubtleBorder,
            BorderThickness = new Thickness(1)
        };
        var gpuRow = new StackPanel { Spacing = 3 };
        var rendererTitle = new TextBlock
        {
            Text = $"{GbcaLoc.T("GpuRendererLabel")} {SkiaLcdViewportControl.ActiveRendererInfo}",
            FontSize = 12,
            FontWeight = FontWeight.SemiBold,
            Foreground = StormThemeBrushes.Accent,
            TextWrapping = TextWrapping.Wrap
        };
        _videoRendererTitle = rendererTitle;
        var probe = new SkiaRendererProbeControl();
        gpuRow.Children.Add(rendererTitle);
        gpuRow.Children.Add(new TextBlock
        {
            Text = $"{GbcaLoc.T("GpuMinReqLabel")} | SkiaSharp 3.119.4 GPU SkSL (FSR 1 EASU+RCAS / FSR 3 Temporal+RCAS / Frame Generation)",
            FontSize = 11,
            Foreground = StormThemeBrushes.SecondaryText,
            TextWrapping = TextWrapping.Wrap
        });
        gpuRow.Children.Add(probe);
        gpuCard.Child = gpuRow;
        p.Children.Add(gpuCard);

        p.Children.Add(CreateSectionTitle("Увеличение разрешения (1x..4x), апскейлинг и производительность"));
        p.Children.Add(CreateCombo("Внутреннее увеличение разрешения (Internal Scale):",
        [
            "1x (Нативное разрешение)",
            "2x HD (480x320 / 320x288)",
            "3x Full HD (720x480 / 480x432)",
            "4x Ultra HD (960x640 / 640x576)"
        ], Math.Clamp(s.ResolutionScale - 1, 0, 3), i =>
        {
            s.ResolutionScale = i + 1;
            if (s.ResolutionScale > 1 && s.UpscaleMode == StormUpscaleMode.None)
            {
                s.UpscaleMode = StormUpscaleMode.Xbrz;
            }
        }, reloadAfter: true));

        p.Children.Add(CreateCombo("Алгоритм апскейлинга изображения:",
        [
            "Nearest (Без сглаживания)",
            "xBRZ HD (Сглаживание диагоналей)",
            "Scale2x / EPX (Классический пиксель-арт)",
            "MMPX Sharp (Четкие контуры)",
            "LCD Subpixel HD (Субпиксельная сетка HD)",
            "CRT Scanlines HD (ЭЛТ-развертка HD)"
        ], (int)s.UpscaleMode, i =>
        {
            s.UpscaleMode = (StormUpscaleMode)i;
            if (s.UpscaleMode != StormUpscaleMode.None && s.ResolutionScale < 2)
            {
                s.ResolutionScale = 2;
            }
        }, reloadAfter: true));

        bool hcVideo = StormGbcaUiState.Instance.IsEmulationActive && StormGbcaUiState.Instance.Emulator.IsHardcoreMode;
        var ocSlider = CreateSlider(hcVideo ? "Разгон процессора консоли (Hardcore 100%):" : "Разгон процессора консоли (CPU Overclock):", 50, 400, hcVideo ? 100 : s.CpuOverclockPct, "%", v =>
        {
            if (!hcVideo) s.CpuOverclockPct = Math.Clamp(((int)v / 25) * 25, 50, 400);
        });
        if (hcVideo)
        {
            ocSlider.IsEnabled = false;
            ocSlider.Opacity = 0.5;
        }
        p.Children.Add(ocSlider);
        p.Children.Add(CreateCombo("Пропуск кадров (Frame Skip):", ["Выкл (0)", "1 кадр", "2 кадра", "3 кадра", "4 кадра"], Math.Clamp(s.FrameSkip, 0, 4), i => s.FrameSkip = i));

        p.Children.Add(CreateSectionTitle("Цветокоррекция LCD, палитры DMG и фильтры"));
        p.Children.Add(CreateCombo("Режим цветокоррекции (7 режимов + GBA):",
        [
            "Отключена (Сырой RGB)",
            "Точные кривые (Correct Curves)",
            "Современный баланс (Modern Balanced)",
            "Современный яркий (Modern Boost)",
            "Пониженный контраст (Reduce Contrast)",
            "Низкий контраст (Low Contrast)",
            "Современный точный (Modern Accurate)",
            "Оригинальный экран GBA AGB/AGS"
        ], (int)s.ColorCorrectionMode, i => s.ColorCorrectionMode = (StormColorCorrectionMode)i));

        var palNames = GbcaEmulatorSession.GetDmgPaletteNames().ToArray();
        p.Children.Add(CreateCombo($"Палитра монохромного Game Boy ({palNames.Length} вариантов):", palNames, Math.Clamp(s.DmgPaletteIndex, 0, palNames.Length - 1), i => s.DmgPaletteIndex = i));

        p.Children.Add(CreateCombo("Видеофильтр экрана:",
        [
            "Четкий пиксель (Nearest)",
            "Билинейное сглаживание (Bilinear)",
            "Сетка пикселей LCD (LCD Grid)",
            "Субпиксельная матрица (RGB Subpixel)",
            "Улучшение деталей (xBRZ Lite)",
            "Строки развертки (CRT Scanlines)",
            "Резкое сглаживание (Sharp Bilinear)",
            GbcaLoc.T("FilterFsr1"),
            GbcaLoc.T("FilterFsr3")
        ], (int)s.VideoFilter, i =>
        {
            var filter = (StormVideoFilter)i;
            s.VideoFilter = filter;
            s.GbOverlayProfile.LcdFilter = filter;
            s.GbcOverlayProfile.LcdFilter = filter;
            s.GbaOverlayProfile.LcdFilter = filter;
            StormGbcaUiState.Instance.NotifySkinOrOverlayChanged();
        }));

        p.Children.Add(CreateCombo(GbcaLoc.T("FsrQualityLabel"),
        [
            GbcaLoc.T("FsrQuality0"),
            GbcaLoc.T("FsrQuality1"),
            GbcaLoc.T("FsrQuality2"),
            GbcaLoc.T("FsrQuality3")
        ], Math.Clamp(s.FsrQualityMode, 0, 3), i => s.FsrQualityMode = i));

        p.Children.Add(CreateSlider(GbcaLoc.T("FsrSharpnessLabel"), 0, 100, s.FsrSharpness, "%", v => s.FsrSharpness = Math.Clamp((int)Math.Round(v), 0, 100)));

        p.Children.Add(CreateCheck(GbcaLoc.T("FrameGenToggle"), s.FrameGeneration, v =>
        {
            s.FrameGeneration = v;
            StormGbcaUiState.Instance.NotifySkinOrOverlayChanged();
        }));
        p.Children.Add(new TextBlock
        {
            Text = GbcaLoc.T("FrameGenNote"),
            FontSize = 10.5,
            Foreground = StormThemeBrushes.SecondaryText,
            TextWrapping = TextWrapping.Wrap,
            Margin = new Thickness(24, 0, 0, 6)
        });

        p.Children.Add(CreateCombo("Масштабирование экрана:", ["Вписать с сохранением пропорций", "Целочисленное (Pixel-Perfect)", "Растянуть на область экрана"], (int)s.ScaleMode, i =>
        {
            s.ScaleMode = (GbcaScaleMode)i;
            StormGbcaUiState.Instance.NotifySkinOrOverlayChanged();
        }));

        p.Children.Add(CreateSlider("Инерционность матрицы LCD (Ghosting):", 0, 100, s.LcdGhosting, "%", v => s.LcdGhosting = (int)v));
        p.Children.Add(CreateCheck("Отображать декоративные рамки Super Game Boy (SGB)", s.SgbBorders, v => s.SgbBorders = v));
        p.Children.Add(CreateCheck("Показывать 3D-заставку STORM при запуске игры", s.Show3DBootSplash, v => s.Show3DBootSplash = v));
    }

    private void BuildCoversBadgesTab(StackPanel p)
    {
        var s = StormGbcaUiState.Instance.Settings;
        var lib = StormGbcaUiState.Instance.Library;

        p.Children.Add(CreateSectionTitle(GbcaLoc.T("SecCoversTitle")));
        p.Children.Add(CreateCheck(GbcaLoc.T("OptLibretroCovers"), s.ShowLibretroCovers, v =>
        {
            s.ShowLibretroCovers = v;
            s.AutoFetchCovers = v;
            lib.ConfigureCoverSources(s.ShowLibretroCovers, s.ShowRaCovers, s.RetroAchievementsUsername, s.RetroAchievementsToken);
            if (v) _ = lib.PreloadAllMissingCoversAsync(s);
        }));
        p.Children.Add(CreateCheck(GbcaLoc.T("OptRaCovers"), s.ShowRaCovers, v =>
        {
            s.ShowRaCovers = v;
            lib.ConfigureCoverSources(s.ShowLibretroCovers, s.ShowRaCovers, s.RetroAchievementsUsername, s.RetroAchievementsToken);
            if (v) _ = lib.PreloadAllMissingCoversAsync(s);
        }));

        var covActions = new WrapPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 6, 0, 4) };
        var fetchBtn = new AuroraVolumetricButton { IsHero = true, Height = 34, Padding = new Thickness(14, 0), Margin = new Thickness(0, 0, 10, 4) };
        fetchBtn.Content = new TextBlock { Text = GbcaLoc.T("BtnFetchCoversNow"), FontSize = 11.5, FontWeight = FontWeight.Bold, Foreground = Brushes.White, VerticalAlignment = VerticalAlignment.Center };
        fetchBtn.Click += (_, _) =>
        {
            if (!s.ShowLibretroCovers && !s.ShowRaCovers)
            {
                s.ShowLibretroCovers = true;
                s.AutoFetchCovers = true;
            }
            lib.ConfigureCoverSources(s.ShowLibretroCovers, s.ShowRaCovers, s.RetroAchievementsUsername, s.RetroAchievementsToken);
            CommitSave();
            _ = lib.PreloadAllMissingCoversAsync(s);
            StormGbcaUiState.Instance.NotifyStatus("Downloading missing covers...");
            LoadTab(_activeTabIndex);
        };
        covActions.Children.Add(fetchBtn);

        var clearBtn = new AuroraVolumetricButton { Height = 34, Padding = new Thickness(14, 0), Margin = new Thickness(0, 0, 0, 4) };
        clearBtn.Content = new TextBlock { Text = GbcaLoc.T("BtnClearCoverCache"), FontSize = 11.5, FontWeight = FontWeight.SemiBold, Foreground = StormThemeBrushes.PrimaryText, VerticalAlignment = VerticalAlignment.Center };
        clearBtn.Click += (_, _) =>
        {
            int removed = lib.ClearCoverCache();
            StormGbcaUiState.Instance.NotifyStatus($"Cover cache cleared ({removed} files)");
            CommitSave();
        };
        covActions.Children.Add(clearBtn);
        p.Children.Add(covActions);

        p.Children.Add(CreateSectionTitle(GbcaLoc.T("SecBadgesTitle")));
        p.Children.Add(CreateCheck(GbcaLoc.T("OptPlatformBadge"), s.ShowPlatformBadge, v => s.ShowPlatformBadge = v));
        p.Children.Add(CreateCheck(GbcaLoc.T("OptRegionBadge"), s.ShowRegionBadge, v => s.ShowRegionBadge = v));
        p.Children.Add(CreateCheck(GbcaLoc.T("OptHardwareBadges"), s.ShowHardwareBadges, v => s.ShowHardwareBadges = v));
        p.Children.Add(CreateCheck(GbcaLoc.T("OptRaBadge"), s.ShowRaBadge, v => s.ShowRaBadge = v));
    }

    private void BuildMultiplayerTab(StackPanel p)
    {
        var s = StormGbcaUiState.Instance.Settings;
        var emu = StormGbcaUiState.Instance.Emulator;
        var status = emu.GetLinkStatus();

        p.Children.Add(CreateSectionTitle(GbcaLoc.T("MpSectionTitle")));

        var stBanner = new Border
        {
            Padding = new Thickness(12, 9),
            CornerRadius = new CornerRadius(10),
            Background = status.Connected ? new SolidColorBrush(Color.FromArgb(45, 16, 185, 129)) : StormThemeBrushes.RowSurface,
            BorderBrush = status.Connected ? new SolidColorBrush(Color.Parse("#10B981")) : StormThemeBrushes.SubtleBorder,
            BorderThickness = new Thickness(1)
        };
        var stCol = new StackPanel { Spacing = 2 };
        stCol.Children.Add(new TextBlock
        {
            Text = status.Mode == StormLinkMode.None
                ? $"Status: {GbcaLoc.T("MpDisconnected")}"
                : $"Mode: {status.Mode} | Players: {status.PlayerCount} | Port: {status.Port} | Ping: {status.PingMs} ms",
            FontSize = 12.5,
            FontWeight = FontWeight.Bold,
            Foreground = status.Connected ? GetSuccessTextBrush() : StormThemeBrushes.PrimaryText,
            TextWrapping = TextWrapping.Wrap
        });
        stCol.Children.Add(new TextBlock
        {
            Text = string.IsNullOrWhiteSpace(status.StatusText) ? "Supports GB/GBC 2P Link, GBA 2-4P Multi-SIO, AGB-015 Wireless RFU, GBC IR, Dolphin GameCube JoyBus & Game Boy Printer PNG." : status.StatusText,
            FontSize = 11,
            Foreground = StormThemeBrushes.SecondaryText,
            TextWrapping = TextWrapping.Wrap
        });
        stBanner.Child = stCol;
        p.Children.Add(stBanner);

        StormLinkMode[] modes =
        [
            StormLinkMode.LanHost, StormLinkMode.LanClient,
            StormLinkMode.WirelessHost, StormLinkMode.WirelessClient,
            StormLinkMode.IrHost, StormLinkMode.IrClient,
            StormLinkMode.DolphinJoyBus, StormLinkMode.GbPrinter, StormLinkMode.LocalLoopback
        ];
        string[] modeLabels =
        [
            GbcaLoc.T("MpModeLanHost"), GbcaLoc.T("MpModeLanClient"),
            GbcaLoc.T("MpModeWirelessHost"), GbcaLoc.T("MpModeWirelessClient"),
            GbcaLoc.T("MpModeIrHost"), GbcaLoc.T("MpModeIrClient"),
            GbcaLoc.T("MpModeDolphin"), GbcaLoc.T("MpModePrinter"), GbcaLoc.T("MpModeLoopback")
        ];
        int mIdx = Math.Max(0, Array.IndexOf(modes, _mpSelectedMode));
        p.Children.Add(CreateCombo("Link Mode:", modeLabels, mIdx, i => { _mpSelectedMode = modes[i]; LoadTab(_activeTabIndex); }));
        p.Children.Add(CreateTextRow("Room Name:", s.MultiplayerRoomName, t => s.MultiplayerRoomName = t));
        p.Children.Add(CreateTextRow("Host / Dolphin IP:", _mpSelectedMode == StormLinkMode.DolphinJoyBus ? s.DolphinIp : s.MultiplayerLastHostIp, t =>
        {
            if (_mpSelectedMode == StormLinkMode.DolphinJoyBus) s.DolphinIp = t;
            else s.MultiplayerLastHostIp = t;
        }));
        p.Children.Add(CreateTextRow("Port (Data / TCP):", (_mpSelectedMode == StormLinkMode.DolphinJoyBus ? s.DolphinDataPort : s.MultiplayerPort).ToString(), t =>
        {
            if (int.TryParse(t, out int pv) && pv is >= 1024 and <= 65535)
            {
                if (_mpSelectedMode == StormLinkMode.DolphinJoyBus) s.DolphinDataPort = pv;
                else s.MultiplayerPort = pv;
            }
        }));
        p.Children.Add(CreateTextRow(GbcaLoc.T("MpDolphinClockPort"), s.DolphinClockPort.ToString(), t =>
        {
            if (int.TryParse(t, out int pv) && pv is >= 1024 and <= 65535)
            {
                s.DolphinClockPort = pv;
            }
        }));

        var btnWrap = new WrapPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 6, 0, 2) };
        var startBtn = new AuroraVolumetricButton { IsHero = true, Height = 34, Padding = new Thickness(14, 0), Margin = new Thickness(0, 0, 8, 6) };
        startBtn.Content = new TextBlock { Text = GbcaLoc.T("MpBtnConnect"), FontSize = 11.5, FontWeight = FontWeight.Bold, Foreground = Brushes.White, VerticalAlignment = VerticalAlignment.Center };
        startBtn.Click += async (_, _) =>
        {
            CommitSave();
            string ip = _mpSelectedMode == StormLinkMode.DolphinJoyBus ? s.DolphinIp : s.MultiplayerLastHostIp;
            int port = _mpSelectedMode == StormLinkMode.DolphinJoyBus ? s.DolphinDataPort : s.MultiplayerPort;
            int clk = _mpSelectedMode == StormLinkMode.DolphinJoyBus ? s.DolphinClockPort : 0;
            _mpFeedback = $"Connecting: {_mpSelectedMode}...";
            LoadTab(_activeTabIndex);
            bool ok = await emu.StartLinkAsync(_mpSelectedMode, ip, port, clk, s.MultiplayerRoomName);
            await Dispatcher.UIThread.InvokeAsync(() =>
            {
                _mpFeedback = ok ? $"Link started: {_mpSelectedMode}" : "Start a game first or verify host IP/port";
                LoadTab(_activeTabIndex);
            });
        };
        btnWrap.Children.Add(startBtn);

        var stopBtn = new AuroraVolumetricButton { Height = 34, Padding = new Thickness(14, 0), Margin = new Thickness(0, 0, 8, 6) };
        stopBtn.Content = new TextBlock { Text = GbcaLoc.T("MpBtnDisconnect"), FontSize = 11.5, FontWeight = FontWeight.SemiBold, Foreground = StormThemeBrushes.PrimaryText, VerticalAlignment = VerticalAlignment.Center };
        stopBtn.Click += (_, _) =>
        {
            emu.StopLink();
            _mpFeedback = GbcaLoc.T("MpDisconnected");
            LoadTab(_activeTabIndex);
        };
        btnWrap.Children.Add(stopBtn);

        var scanBtn = new AuroraVolumetricButton { Height = 34, Padding = new Thickness(14, 0), Margin = new Thickness(0, 0, 0, 6) };
        scanBtn.Content = new TextBlock { Text = GbcaLoc.T("MpBtnScanLan"), FontSize = 11.5, FontWeight = FontWeight.SemiBold, Foreground = StormThemeBrushes.PrimaryText, VerticalAlignment = VerticalAlignment.Center };
        scanBtn.Click += async (_, _) =>
        {
            _mpFeedback = "Scanning local network (UDP 54321)...";
            LoadTab(_activeTabIndex);
            var found = await Task.Run(() => emu.DiscoverLanRooms(1200));
            await Dispatcher.UIThread.InvokeAsync(() =>
            {
                _discoveredRooms.Clear();
                _discoveredRooms.AddRange(found);
                _mpFeedback = found.Count > 0 ? $"Found {found.Count} LAN room(s)" : "No active LAN rooms found";
                LoadTab(_activeTabIndex);
            });
        };
        btnWrap.Children.Add(scanBtn);
        p.Children.Add(btnWrap);

        if (!string.IsNullOrWhiteSpace(_mpFeedback))
        {
            p.Children.Add(new TextBlock { Text = _mpFeedback, FontSize = 11.5, Foreground = StormThemeBrushes.AccentSecondary, TextWrapping = TextWrapping.Wrap });
        }

        foreach (var room in _discoveredRooms)
        {
            var rBorder = new Border
            {
                Padding = new Thickness(10, 7),
                CornerRadius = new CornerRadius(8),
                Background = StormThemeBrushes.RowSurface,
                BorderBrush = StormThemeBrushes.Accent,
                BorderThickness = new Thickness(1),
                Cursor = new Cursor(StandardCursorType.Hand)
            };
            var rg = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto") };
            rg.Children.Add(new TextBlock
            {
                Text = $"{room.RoomName} ({room.HostIp}:{room.Port}) - {room.GameTitle} [{room.PlayerCount}/{room.MaxPlayers}]",
                FontSize = 11.5,
                Foreground = StormThemeBrushes.PrimaryText,
                VerticalAlignment = VerticalAlignment.Center,
                TextWrapping = TextWrapping.Wrap
            });
            var joinLbl = new TextBlock { Text = "Join ->", FontSize = 11, FontWeight = FontWeight.Bold, Foreground = StormThemeBrushes.Accent, VerticalAlignment = VerticalAlignment.Center };
            rg.Children.Add(joinLbl);
            Grid.SetColumn(joinLbl, 1);
            rBorder.Child = rg;
            rBorder.PointerPressed += async (_, _) =>
            {
                s.MultiplayerLastHostIp = room.HostIp;
                s.MultiplayerPort = room.Port;
                _mpSelectedMode = room.Mode == StormLinkMode.WirelessHost ? StormLinkMode.WirelessClient : (room.Mode == StormLinkMode.IrHost ? StormLinkMode.IrClient : StormLinkMode.LanClient);
                CommitSave();
                await emu.StartLinkAsync(_mpSelectedMode, room.HostIp, room.Port, 0, room.RoomName);
                await Dispatcher.UIThread.InvokeAsync(() => LoadTab(_activeTabIndex));
            };
            p.Children.Add(rBorder);
        }
    }

    private void BuildAudioTab(StackPanel p)
    {
        var s = StormGbcaUiState.Instance.Settings;
        p.Children.Add(CreateSectionTitle("Аудиотракт 48000 Гц, фильтр высоких частот и 6-канальный микшер"));
        p.Children.Add(CreateCombo("Фильтр высоких частот (High-Pass):", ["Отключен", "Аппаратно точный (Accurate)", "Удаление постоянной составляющей (Remove DC)"], (int)s.AudioHighpassMode, i => s.AudioHighpassMode = (StormAudioHighpassMode)i));
        p.Children.Add(CreateCombo("Интерполяция звука:", ["Ближайшая (Nearest)", "Линейная (Linear)", "Кубическая Эрмита (Hermite High Quality)"], (int)s.AudioInterpMode, i => s.AudioInterpMode = (StormAudioInterpMode)i));
        p.Children.Add(CreateCheck("Динамическая синхронизация частоты (Dynamic Rate Control)", s.DynamicRateControl, v => s.DynamicRateControl = v));
        p.Children.Add(CreateCheck("Без звука (Mute)", s.AudioMuted, v => s.AudioMuted = v));
        p.Children.Add(CreateSlider("Общая громкость:", 0.0, 2.0, s.MasterVolume, "x", v => s.MasterVolume = (float)v));

        string[] chLabels = ["Канал 1: PSG Pulse 1", "Канал 2: PSG Pulse 2", "Канал 3: PSG Wave", "Канал 4: PSG Noise", "Канал 5: GBA Direct Sound A", "Канал 6: GBA Direct Sound B"];
        for (int i = 0; i < 6; i++)
        {
            int idx = i;
            p.Children.Add(CreateSlider(chLabels[idx], 0.0, 1.5, s.ChannelVolumes[idx], "x", v => s.ChannelVolumes[idx] = (float)v));
        }

        p.Children.Add(CreateCheck("Тактильные звуки интерфейса STORM", s.UiTactileAudioEnabled, v =>
        {
            s.UiTactileAudioEnabled = v;
            AuroraTactileFeedbackService.Instance.Enabled = v;
        }));
    }

    private void BuildSkinsSummaryTab(StackPanel p)
    {
        var s = StormGbcaUiState.Instance.Settings;
        p.Children.Add(CreateSectionTitle("Независимые 3D-корпусы и оверлеи по системам"));
        p.Children.Add(CreateCombo("3D-корпус для Game Boy (GB):", Enum.GetNames<GbConsoleSkin>(), (int)s.GbConsoleSkin, i => { s.GbConsoleSkin = (GbConsoleSkin)i; StormGbcaUiState.Instance.NotifySkinOrOverlayChanged(); }));
        p.Children.Add(CreateCombo("3D-корпус для Game Boy Color (GBC):", Enum.GetNames<GbcConsoleSkin>(), (int)s.GbcConsoleSkin, i => { s.GbcConsoleSkin = (GbcConsoleSkin)i; StormGbcaUiState.Instance.NotifySkinOrOverlayChanged(); }));
        p.Children.Add(CreateCombo("3D-корпус для Game Boy Advance (GBA):", Enum.GetNames<GbaConsoleSkin>(), (int)s.GbaConsoleSkin, i => { s.GbaConsoleSkin = (GbaConsoleSkin)i; StormGbcaUiState.Instance.NotifySkinOrOverlayChanged(); }));
        p.Children.Add(CreateCheck("Ночное затемнение 3D-корпуса", s.SkinNightDimming, v => { s.SkinNightDimming = v; StormGbcaUiState.Instance.NotifySkinOrOverlayChanged(); }));

        var openStudioBtn = new AuroraVolumetricButton { IsHero = true, Height = 38, Padding = new Thickness(18, 0), HorizontalAlignment = HorizontalAlignment.Left, Margin = new Thickness(0, 6, 0, 0) };
        openStudioBtn.Content = new TextBlock { Text = "Открыть студию 3D-корпусов с предпросмотром", FontSize = 12, FontWeight = FontWeight.Bold, Foreground = Brushes.White, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
        openStudioBtn.Click += (_, _) => StormGbcaUiState.Instance.SetNavSection(NavSectionKind.SkinsAndOverlays);
        p.Children.Add(openStudioBtn);
    }

    private void BuildInputTab(StackPanel p)
    {
        var s = StormGbcaUiState.Instance.Settings;
        p.Children.Add(CreateSectionTitle("Назначение клавиш клавиатуры (нажмите значок клавиши для переназначения)"));

        var grid = new UniformGrid { Columns = _isWideInputCols ? 2 : 1 };
        _inputBindingsGrid = grid;
        (string Key, string Label)[] bindings =
        [
            ("Up", "Крестовина: Вверх"),
            ("Down", "Крестовина: Вниз"),
            ("Left", "Крестовина: Влево"),
            ("Right", "Крестовина: Вправо"),
            ("A", "Кнопка A"),
            ("B", "Кнопка B"),
            ("L", "Триггер L (GBA)"),
            ("R", "Триггер R (GBA)"),
            ("Start", "Кнопка Start"),
            ("Select", "Кнопка Select"),
            ("FastForward", "Ускорение (Fast-Forward)"),
            ("Rewind", "Перемотка назад (Rewind)"),
            ("QuickSave", GbcaLoc.T("MenuQuickSave")),
            ("QuickLoad", GbcaLoc.T("MenuQuickLoad"))
        ];

        foreach (var b in bindings)
        {
            string currentKey = s.KeyBindings.TryGetValue(b.Key, out string? k) ? k : "-";
            bool listening = _listeningActionKey == b.Key;

            var row = new Border
            {
                Margin = new Thickness(0, 3, 8, 3),
                Padding = new Thickness(10, 6),
                CornerRadius = new CornerRadius(10),
                Background = StormThemeBrushes.RowSurface,
                BorderBrush = listening ? StormThemeBrushes.Accent : StormThemeBrushes.SubtleBorder,
                BorderThickness = new Thickness(1),
                Cursor = new Cursor(StandardCursorType.Hand)
            };
            var rg = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto") };
            rg.Children.Add(new TextBlock
            {
                Text = b.Label,
                FontSize = 11.5,
                Foreground = StormThemeBrushes.BodyText,
                VerticalAlignment = VerticalAlignment.Center,
                TextWrapping = TextWrapping.Wrap,
                Margin = new Thickness(0, 0, 8, 0)
            });

            var keyCap = Create3DPhysicalKeyBadge(listening ? "Нажмите клавишу..." : currentKey, listening);
            rg.Children.Add(keyCap);
            Grid.SetColumn(keyCap, 1);

            row.Child = rg;
            row.PointerPressed += (_, _) =>
            {
                AuroraTactileFeedbackService.Instance.PlayClick();
                _listeningActionKey = b.Key;
                Focus();
                LoadTab(_activeTabIndex);
            };
            grid.Children.Add(row);
        }
        p.Children.Add(grid);

        p.Children.Add(CreateSectionTitle("Геймпад XInput и сенсоры картриджа"));
        p.Children.Add(new TextBlock
        {
            Text = $"Статус XInput: {(StormGbcaUiState.Instance.Emulator.Gamepad.IsSupported ? "Драйвер XInput активен (подключите геймпад Xbox / совместимый)" : "XInput недоступен")}",
            FontSize = 11.5,
            Foreground = StormThemeBrushes.SecondaryText,
            TextWrapping = TextWrapping.Wrap
        });
        p.Children.Add(CreateCheck("Передавать вибрацию картриджа (Rumble) на геймпад XInput", s.RumbleToXInput, v => s.RumbleToXInput = v));
        p.Children.Add(CreateCheck("Использовать системное время ПК для часов реального времени (RTC)", s.UseHostRtc, v => s.UseHostRtc = v));
        p.Children.Add(CreateSlider("Солнечный датчик Boktai (Solar LUX):", 0, 10, s.SolarLuxLevel, "Lux", v => s.SolarLuxLevel = (int)v));
    }

    private static Border Create3DPhysicalKeyBadge(string keyName, bool highlighted)
    {
        var accent = StormThemeBrushes.AccentPrimaryColor;
        bool isDark = StormThemeBrushes.IsDark;

        Color topColor = highlighted
            ? accent
            : (isDark ? Color.Parse("#334155") : Color.Parse("#F8FAFC"));
        Color bottomColor = highlighted
            ? Color.FromArgb(200, accent.R, accent.G, accent.B)
            : (isDark ? Color.Parse("#1E293B") : Color.Parse("#E2E8F0"));
        IBrush borderBrush = highlighted
            ? new SolidColorBrush(Colors.White)
            : (isDark
                ? new SolidColorBrush(Color.FromArgb(140, 255, 255, 255))
                : new SolidColorBrush(Color.Parse("#94A3B8")));
        IBrush textBrush = (highlighted || isDark)
            ? Brushes.White
            : new SolidColorBrush(Color.Parse("#0F172A"));
        Color shadowColor = isDark
            ? Color.FromArgb(120, 0, 0, 0)
            : Color.FromArgb(45, 15, 23, 42);

        return new Border
        {
            MinWidth = 68,
            Height = 28,
            Padding = new Thickness(10, 2),
            CornerRadius = new CornerRadius(7),
            Background = new LinearGradientBrush
            {
                StartPoint = new RelativePoint(0, 0, RelativeUnit.Relative),
                EndPoint = new RelativePoint(0, 1, RelativeUnit.Relative),
                GradientStops =
                {
                    new GradientStop(topColor, 0.0),
                    new GradientStop(bottomColor, 1.0)
                }
            },
            BorderBrush = borderBrush,
            BorderThickness = new Thickness(1, 1, 1, 3),
            BoxShadow = new BoxShadows(new BoxShadow { OffsetX = 0, OffsetY = 2, Blur = 5, Color = shadowColor }),
            Child = new TextBlock
            {
                Text = keyName,
                FontSize = 11,
                FontWeight = FontWeight.Bold,
                FontFamily = new FontFamily("Consolas, Cascadia Mono, monospace"),
                Foreground = textBrush,
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            }
        };
    }

    private void OnKeyCaptureKeyDown(object? sender, KeyEventArgs e)
    {
        if (string.IsNullOrWhiteSpace(_listeningActionKey)) return;
        if (e.Key != Key.Escape)
        {
            StormGbcaUiState.Instance.Settings.KeyBindings[_listeningActionKey] = e.Key.ToString();
            CommitSave();
        }
        _listeningActionKey = null;
        e.Handled = true;
        LoadTab(_activeTabIndex);
    }

    private void BuildRaTab(StackPanel p)
    {
        var s = StormGbcaUiState.Instance.Settings;
        var ra = StormGbcaUiState.Instance.RetroAchievements;
        p.Children.Add(CreateSectionTitle("Аккаунт RetroAchievements (Статус и очки)"));

        var statusBanner = new Border
        {
            Padding = new Thickness(14, 10),
            CornerRadius = new CornerRadius(10),
            Background = ra.IsLoggedIn
                ? new SolidColorBrush(Color.FromArgb(40, 16, 185, 129))
                : StormThemeBrushes.RowSurface,
            BorderBrush = ra.IsLoggedIn ? new SolidColorBrush(Color.Parse("#10B981")) : StormThemeBrushes.SubtleBorder,
            BorderThickness = new Thickness(1)
        };
        var statusCol = new StackPanel { Spacing = 4 };
        var statusTitle = new TextBlock
        {
            Text = ra.IsLoggedIn ? $"Подключен: {ra.Username}" : "Аккаунт RetroAchievements не подключен",
            FontSize = 13.5,
            FontWeight = FontWeight.Bold,
            Foreground = ra.IsLoggedIn ? GetSuccessTextBrush() : StormThemeBrushes.PrimaryText,
            TextWrapping = TextWrapping.Wrap
        };
        var statusSub = new TextBlock
        {
            Text = ra.IsLoggedIn
                ? $"Hardcore: {ra.Points} pts  |  Casual: {ra.SoftcorePoints} pts{(ra.Rank > 0 ? $"  |  Рейтинг: #{ra.Rank}" : "")}"
                : "Введите логин и пароль RetroAchievements для синхронизации очков и трофеев",
            FontSize = 11.5,
            Foreground = StormThemeBrushes.SecondaryText,
            TextWrapping = TextWrapping.Wrap
        };
        statusCol.Children.Add(statusTitle);
        statusCol.Children.Add(statusSub);
        statusBanner.Child = statusCol;
        p.Children.Add(statusBanner);

        p.Children.Add(CreateTextRow("Логин (Username):", s.RetroAchievementsUsername, t => s.RetroAchievementsUsername = t));

        var passGrid = new Grid { ColumnDefinitions = new ColumnDefinitions("*,210"), Margin = new Thickness(0, 4) };
        passGrid.Children.Add(new TextBlock
        {
            Text = "Пароль (не сохраняется):",
            FontSize = 11.5,
            Foreground = StormThemeBrushes.BodyText,
            VerticalAlignment = VerticalAlignment.Center,
            TextWrapping = TextWrapping.Wrap,
            Margin = new Thickness(0, 0, 12, 0)
        });
        var passBox = new TextBox { PlaceholderText = "Введите пароль RetroAchievements", PasswordChar = '*', Height = 32, MinWidth = 0 };
        passGrid.Children.Add(passBox);
        Grid.SetColumn(passBox, 1);
        p.Children.Add(passGrid);

        var statusTxt = new TextBlock { Text = ra.LastStatusMessage, FontSize = 11.5, Foreground = StormThemeBrushes.AccentSecondary, TextWrapping = TextWrapping.Wrap };
        var btnRow = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 10, Margin = new Thickness(0, 4) };

        var loginBtn = new AuroraVolumetricButton { IsHero = true, Height = 34, Padding = new Thickness(16, 0) };
        loginBtn.Content = new TextBlock { Text = "Войти в RetroAchievements", FontSize = 12, FontWeight = FontWeight.Bold, Foreground = Brushes.White, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
        loginBtn.Click += async (_, _) =>
        {
            statusTxt.Text = "Авторизация на retroachievements.org...";
            var res = await ra.LoginAsync(s.RetroAchievementsUsername, passBox.Text ?? string.Empty);
            await Dispatcher.UIThread.InvokeAsync(() =>
            {
                if (res.Success)
                {
                    s.RetroAchievementsUsername = ra.Username;
                    s.RetroAchievementsToken = ra.Token;
                    s.RetroAchievementsPoints = ra.Points;
                    s.RetroAchievementsSoftcorePoints = ra.SoftcorePoints;
                    s.RetroAchievementsRank = ra.Rank;
                    StormGbcaUiState.Instance.Library.ConfigureCoverSources(s.ShowLibretroCovers, s.ShowRaCovers, s.RetroAchievementsUsername, s.RetroAchievementsToken);
                    CommitSave(true);
                    LoadTab(_activeTabIndex);
                }
                else
                {
                    statusTxt.Text = res.Message;
                }
            });
        };
        btnRow.Children.Add(loginBtn);

        if (ra.IsLoggedIn)
        {
            var logoutBtn = new AuroraVolumetricButton { Height = 34, Padding = new Thickness(14, 0) };
            logoutBtn.Content = new TextBlock { Text = "Выйти из аккаунта", FontSize = 11.5, FontWeight = FontWeight.SemiBold, Foreground = GetDangerTextBrush(), HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
            logoutBtn.Click += (_, _) =>
            {
                ra.Logout();
                s.RetroAchievementsUsername = string.Empty;
                s.RetroAchievementsToken = string.Empty;
                s.RetroAchievementsPoints = 0;
                s.RetroAchievementsSoftcorePoints = 0;
                s.RetroAchievementsRank = 0;
                StormGbcaUiState.Instance.Library.ConfigureCoverSources(s.ShowLibretroCovers, s.ShowRaCovers, string.Empty, string.Empty);
                CommitSave(true);
                LoadTab(_activeTabIndex);
            };
            btnRow.Children.Add(logoutBtn);
        }

        p.Children.Add(btnRow);
        p.Children.Add(statusTxt);

        var hcCheck = new AuroraNeonCheckBox
        {
            Label = "Режим Hardcore (запрещает загрузку состояний, перемотку, читы, RAM Finder, разгон и замедление)",
            IsChecked = s.RetroAchievementsHardcoreMode,
            Margin = new Thickness(0, 2)
        };
        hcCheck.IsCheckedChanged += (_, v) =>
        {
            if (StormGbcaUiState.Instance.IsEmulationActive && v && !StormGbcaUiState.Instance.Emulator.IsHardcoreMode)
            {
                _pendingHardcoreConfirm = true;
                LoadTab(_activeTabIndex);
                return;
            }
            _pendingHardcoreConfirm = false;
            StormGbcaUiState.Instance.SetHardcoreModeFromUi(v);
            LoadTab(_activeTabIndex);
        };
        p.Children.Add(hcCheck);

        if (_pendingHardcoreConfirm && StormGbcaUiState.Instance.IsEmulationActive)
        {
            var confirmCard = new Border
            {
                Padding = new Thickness(12, 9),
                CornerRadius = new CornerRadius(10),
                Background = new SolidColorBrush(Color.FromArgb(45, 245, 158, 11)),
                BorderBrush = new SolidColorBrush(Color.Parse("#F59E0B")),
                BorderThickness = new Thickness(1)
            };
            var cCol = new StackPanel { Spacing = 8 };
            cCol.Children.Add(new TextBlock
            {
                Text = "Переход из Casual в Hardcore во время игры выполнит полный перезапуск консоли и отключит читы, загрузку состояний, перемотку, RAM Finder и разгон.",
                FontSize = 11.5,
                FontWeight = FontWeight.SemiBold,
                Foreground = StormThemeBrushes.PrimaryText,
                TextWrapping = TextWrapping.Wrap
            });
            var cBtns = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8 };
            var okBtn = new AuroraVolumetricButton { IsHero = true, Height = 30, Padding = new Thickness(12, 0) };
            okBtn.Content = new TextBlock { Text = "Подтвердить и перезапустить", FontSize = 11, FontWeight = FontWeight.Bold, Foreground = Brushes.White, VerticalAlignment = VerticalAlignment.Center };
            okBtn.Click += (_, _) =>
            {
                _pendingHardcoreConfirm = false;
                StormGbcaUiState.Instance.SetHardcoreModeFromUi(true);
                LoadTab(_activeTabIndex);
            };
            var cancelBtn = new AuroraVolumetricButton { Height = 30, Padding = new Thickness(12, 0) };
            cancelBtn.Content = new TextBlock { Text = "Отмена", FontSize = 11, FontWeight = FontWeight.SemiBold, Foreground = StormThemeBrushes.PrimaryText, VerticalAlignment = VerticalAlignment.Center };
            cancelBtn.Click += (_, _) =>
            {
                _pendingHardcoreConfirm = false;
                LoadTab(_activeTabIndex);
            };
            cBtns.Children.Add(okBtn);
            cBtns.Children.Add(cancelBtn);
            cCol.Children.Add(cBtns);
            confirmCard.Child = cCol;
            p.Children.Add(confirmCard);
        }

        bool isHardcore = s.RetroAchievementsHardcoreMode;
        var rpCheck = CreateCheck("Rich Presence статус активности (в Hardcore активен всегда)", isHardcore || s.RetroAchievementsRichPresence, v => s.RetroAchievementsRichPresence = v);
        rpCheck.IsEnabled = !isHardcore;
        rpCheck.Opacity = isHardcore ? 0.55 : 1.0;
        p.Children.Add(rpCheck);
        p.Children.Add(CreateCheck("Enable RetroAchievements", s.RetroAchievementsEnabled, v => s.RetroAchievementsEnabled = v));
        p.Children.Add(CreateCheck("Encore Mode (re-trigger unlocked trophies)", s.RetroAchievementsEncoreMode, v => s.RetroAchievementsEncoreMode = v));
        var unoffCheck = CreateCheck("Unofficial Achievements", !isHardcore && s.RetroAchievementsUnofficial, v => s.RetroAchievementsUnofficial = v);
        unoffCheck.IsEnabled = !isHardcore;
        unoffCheck.Opacity = isHardcore ? 0.55 : 1.0;
        p.Children.Add(unoffCheck);
        p.Children.Add(CreateCheck("In-Game Unlock Notifications", s.RetroAchievementsNotifications, v => s.RetroAchievementsNotifications = v));
        var lbCheck = CreateCheck("Leaderboards Evaluation (в Hardcore активна всегда)", isHardcore || s.RetroAchievementsLeaderboards, v => s.RetroAchievementsLeaderboards = v);
        lbCheck.IsEnabled = !isHardcore;
        lbCheck.Opacity = isHardcore ? 0.55 : 1.0;
        p.Children.Add(lbCheck);
        p.Children.Add(CreateCheck("Show Leaderboard Popups (OSD-уведомления таблиц лидеров)", s.RetroAchievementsShowLeaderboardPopups, v => s.RetroAchievementsShowLeaderboardPopups = v));
        p.Children.Add(CreateCheck("Progress & Challenge Indicators", s.RetroAchievementsProgressIndicators, v => { s.RetroAchievementsProgressIndicators = v; s.RetroAchievementsChallengeIndicators = v; }));

        var testBtn = new AuroraVolumetricButton { Height = 34, Padding = new Thickness(16, 0), HorizontalAlignment = HorizontalAlignment.Left, Margin = new Thickness(0, 6, 0, 0) };
        testBtn.Content = new TextBlock { Text = GbcaLoc.T("RaTestConn"), FontSize = 11.5, FontWeight = FontWeight.Bold, Foreground = StormThemeBrushes.PrimaryText, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
        testBtn.Click += async (_, _) =>
        {
            statusTxt.Text = "Testing RetroAchievements server...";
            var (ok, _, details) = await ra.TestConnectionAsync();
            await Dispatcher.UIThread.InvokeAsync(() => statusTxt.Text = (ok ? "[OK] " : "[FAIL] ") + details);
        };
        p.Children.Add(testBtn);
    }

    private void BuildThemesTab(StackPanel p)
    {
        var s = StormGbcaUiState.Instance.Settings;
        p.Children.Add(CreateSectionTitle("9 Dynamic Themes (English Names)"));

        (string Id, string Title, string Sub)[] themes =
        [
            ("MIDNIGHT", "STORM MIDNIGHT", "Deep Violet"),
            ("NIGHT", "STORM NIGHT", "OLED Dark"),
            ("DARK", "STORM DARK", "Slate Cyber"),
            ("DAY", "STORM DAY", "Light Mode"),
            ("FANTASY", "STORM FANTASY", "Royal Gold"),
            ("CYBERPUNK", "STORM CYBERPUNK", "Night City Neon"),
            ("WARHAMMER 40K", "STORM WARHAMMER 40K", "Imperial Gothic"),
            ("ELEMENTAL", "STORM ELEMENTAL", "Emerald Matrix"),
            ("STORM", "STORM AURORA", "Plasma Gradient")
        ];

        var themesGrid = new UniformGrid { Columns = 3 };
        foreach (var t in themes)
        {
            bool sel = string.Equals(AuroraThemeManager.Instance.CurrentTheme.Id, t.Id, StringComparison.OrdinalIgnoreCase);
            var btn = new Button
            {
                Margin = new Thickness(4),
                Padding = new Thickness(8, 6),
                Background = sel ? StormThemeBrushes.HoverSurface : StormThemeBrushes.CardSurface,
                BorderBrush = sel ? StormThemeBrushes.Accent : StormThemeBrushes.SubtleBorder,
                BorderThickness = new Thickness(1),
                CornerRadius = new CornerRadius(8),
                Cursor = new Cursor(StandardCursorType.Hand),
                HorizontalContentAlignment = HorizontalAlignment.Center
            };
            var st = new StackPanel { Spacing = 2, HorizontalAlignment = HorizontalAlignment.Center };
            st.Children.Add(new TextBlock { Text = t.Title, FontSize = 11, FontWeight = FontWeight.Bold, Foreground = StormThemeBrushes.PrimaryText, HorizontalAlignment = HorizontalAlignment.Center, TextTrimming = TextTrimming.CharacterEllipsis });
            st.Children.Add(new TextBlock { Text = t.Sub, FontSize = 9.5, Foreground = StormThemeBrushes.SecondaryText, HorizontalAlignment = HorizontalAlignment.Center, TextTrimming = TextTrimming.CharacterEllipsis });
            btn.Content = st;
            btn.Click += (_, _) =>
            {
                AuroraTactileFeedbackService.Instance.PlayClick();
                AuroraThemeManager.Instance.ApplyTheme(t.Id);
                s.Theme = t.Title;
                CommitSave();
            };
            themesGrid.Children.Add(btn);
        }
        p.Children.Add(themesGrid);

        p.Children.Add(CreateSectionTitle("Акцентные цвета интерфейса (13 вариантов)"));
        var accentsWrap = new WrapPanel { Orientation = Orientation.Horizontal };
        foreach (var kind in Enum.GetValues<AuroraAccentKind>())
        {
            var (swatchColor, _) = AuroraThemeManager.ResolveAccentColors(AuroraThemeManager.Instance.CurrentTheme, kind);
            bool activeAccent = AuroraThemeManager.Instance.CurrentAccentOverride == kind;
            var swatchBtn = new Button
            {
                Margin = new Thickness(0, 0, 6, 6),
                Padding = new Thickness(8, 5),
                CornerRadius = new CornerRadius(8),
                Background = activeAccent ? StormThemeBrushes.HoverSurface : StormThemeBrushes.CardSurface,
                BorderBrush = activeAccent ? new SolidColorBrush(swatchColor) : StormThemeBrushes.SubtleBorder,
                BorderThickness = new Thickness(1),
                Cursor = new Cursor(StandardCursorType.Hand)
            };
            var row = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 6, VerticalAlignment = VerticalAlignment.Center };
            row.Children.Add(new Border { Width = 12, Height = 12, CornerRadius = new CornerRadius(6), Background = new SolidColorBrush(swatchColor) });
            row.Children.Add(new TextBlock { Text = kind == AuroraAccentKind.DefaultThemeAccent ? "По теме" : kind.ToString(), FontSize = 10.5, Foreground = StormThemeBrushes.PrimaryText });
            swatchBtn.Content = row;
            swatchBtn.Click += (_, _) =>
            {
                AuroraTactileFeedbackService.Instance.PlayClick();
                AuroraThemeManager.Instance.ApplyTheme(AuroraThemeManager.Instance.CurrentTheme.Id, kind);
                s.AccentName = kind.ToString();
                CommitSave();
                RefreshTabLabelsAndContent(force: true);
            };
            accentsWrap.Children.Add(swatchBtn);
        }
        p.Children.Add(accentsWrap);

        p.Children.Add(CreateSectionTitle("Шрифты интерфейса (10 пресетов)"));
        var fontsGrid = new UniformGrid { Columns = 2 };
        foreach (var preset in Enum.GetValues<AuroraTypographyPreset>())
        {
            bool activeFont = AuroraThemeManager.Instance.CurrentTypography == preset;
            var fBtn = new Button
            {
                Margin = new Thickness(3),
                Padding = new Thickness(8, 5),
                CornerRadius = new CornerRadius(6),
                Background = activeFont ? StormThemeBrushes.HoverSurface : StormThemeBrushes.CardSurface,
                BorderBrush = activeFont ? StormThemeBrushes.Accent : StormThemeBrushes.SubtleBorder,
                BorderThickness = new Thickness(1),
                Cursor = new Cursor(StandardCursorType.Hand),
                HorizontalContentAlignment = HorizontalAlignment.Center,
                Content = new TextBlock { Text = preset.ToString(), FontSize = 11, FontFamily = AuroraThemeManager.ResolveFontFamily(preset), Foreground = StormThemeBrushes.PrimaryText }
            };
            fBtn.Click += (_, _) =>
            {
                AuroraTactileFeedbackService.Instance.PlayClick();
                AuroraThemeManager.Instance.ApplyTheme(AuroraThemeManager.Instance.CurrentTheme.Id, null, preset);
                s.TypographyName = preset.ToString();
                CommitSave();
                RefreshTabLabelsAndContent(force: true);
            };
            fontsGrid.Children.Add(fBtn);
        }
        p.Children.Add(fontsGrid);

        p.Children.Add(CreateSectionTitle("Язык интерфейса и автообновление (GitHub + STORM GAMES WORLD)"));
        p.Children.Add(CreateCombo("Язык интерфейса:", GbcaLoc.SupportedLanguages.Select(l => l.NativeName).ToArray(),
            Math.Max(0, GbcaLoc.SupportedLanguages.ToList().FindIndex(l => string.Equals(l.Code, s.Language, StringComparison.OrdinalIgnoreCase))),
            idx =>
            {
                string code = GbcaLoc.SupportedLanguages[idx].Code;
                GbcaLoc.CurrentLanguage = code;
                s.Language = code;
                CommitSave();
            }));
        p.Children.Add(CreateCheck("Автоматически проверять обновления при запуске", s.AutoCheckUpdates, v => s.AutoCheckUpdates = v));
        p.Children.Add(CreateCombo(
            GbcaLoc.T("DefaultUpdateSourceSetting"),
            ["STORM GAMES WORLD", "GitHub"],
            string.Equals(s.DefaultUpdateSource, "github", StringComparison.OrdinalIgnoreCase) ? 1 : 0,
            idx => s.DefaultUpdateSource = idx == 1 ? "github" : "storm"));
        p.Children.Add(CreateCheck(GbcaLoc.T("AutoCheckGameUpdatesSetting"), s.AutoCheckGameUpdates, v =>
        {
            s.AutoCheckGameUpdates = v;
            if (v) _ = StormGbcaUiState.Instance.RefreshCatalogAndUpdatesAsync(forceRefresh: false);
        }));

        var updBtn = new AuroraVolumetricButton { Height = 34, Padding = new Thickness(16, 0), HorizontalAlignment = HorizontalAlignment.Left };
        updBtn.Content = new TextBlock { Text = "Проверить обновления сейчас", FontSize = 12, FontWeight = FontWeight.Bold, Foreground = StormThemeBrushes.PrimaryText, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
        updBtn.Click += (_, _) => StormGbcaUiState.Instance.RequestUpdateCheck(true);
        p.Children.Add(updBtn);

        p.Children.Add(CreateSectionTitle("О программе, конфиденциальность и лицензии (Open Source)"));
        var aboutCard = new Border
        {
            Padding = new Thickness(14, 10),
            CornerRadius = new CornerRadius(10),
            Background = StormThemeBrushes.RowSurface,
            BorderBrush = StormThemeBrushes.SubtleBorder,
            BorderThickness = new Thickness(1)
        };
        var aboutCol = new StackPanel { Spacing = 8 };
        aboutCol.Children.Add(new TextBlock
        {
            Text = $"STORM GBCA {GbcaLoc.AppVersion} - 100% бесплатное ПО с открытым исходным кодом (MPL-2.0): без рекламы, без платных подписок и без телеметрии.",
            FontSize = 11.5,
            Foreground = StormThemeBrushes.BodyText,
            TextWrapping = TextWrapping.Wrap
        });
        var linksWrap = new WrapPanel { Orientation = Orientation.Horizontal };
        linksWrap.Children.Add(CreateExternalLinkBtn("Политика конфиденциальности (PRIVACY_POLICY.md)", "https://github.com/ReiKatari/STORM_GBCA/blob/main/PRIVACY_POLICY.md"));
        linksWrap.Children.Add(CreateExternalLinkBtn("Сторонние лицензии (THIRD_PARTY_LICENSES.md)", "https://github.com/ReiKatari/STORM_GBCA/blob/main/THIRD_PARTY_LICENSES.md"));
        linksWrap.Children.Add(CreateExternalLinkBtn("Исходный код на GitHub", "https://github.com/ReiKatari/STORM_GBCA"));
        aboutCol.Children.Add(linksWrap);
        aboutCard.Child = aboutCol;
        p.Children.Add(aboutCard);
    }

    private static AuroraVolumetricButton CreateExternalLinkBtn(string label, string url)
    {
        var btn = new AuroraVolumetricButton { Height = 30, Padding = new Thickness(12, 0), Margin = new Thickness(0, 0, 8, 6) };
        btn.Content = new TextBlock
        {
            Text = label,
            FontSize = 11,
            FontWeight = FontWeight.SemiBold,
            Foreground = StormThemeBrushes.Accent,
            VerticalAlignment = VerticalAlignment.Center
        };
        btn.Click += (_, _) =>
        {
            try
            {
                Process.Start(new ProcessStartInfo(url) { UseShellExecute = true });
            }
            catch { }
        };
        return btn;
    }

    private static TextBlock CreateSectionTitle(string title) => new()
    {
        Text = title.ToUpperInvariant(),
        FontSize = 11,
        FontWeight = FontWeight.Bold,
        Foreground = StormThemeBrushes.Accent,
        Margin = new Thickness(0, 6, 0, 2),
        TextWrapping = TextWrapping.Wrap
    };

    private Control CreateCheck(string label, bool initial, Action<bool> onChange)
    {
        var cb = new AuroraNeonCheckBox { Label = label, IsChecked = initial, Margin = new Thickness(0, 2) };
        cb.IsCheckedChanged += (_, chk) =>
        {
            onChange(chk);
            CommitSave();
        };
        return cb;
    }

    private Grid CreateSlider(string label, double min, double max, double initial, string unit, Action<double> onChange)
    {
        var g = new Grid { Margin = new Thickness(0, 4), RowDefinitions = new RowDefinitions("Auto,Auto") };
        var lbl = new TextBlock { Text = $"{label} {initial:0.##} {unit}", FontSize = 11, Foreground = StormThemeBrushes.BodyText, TextWrapping = TextWrapping.Wrap };
        g.Children.Add(lbl);
        var slider = new AuroraVolumetricSlider { Minimum = min, Maximum = max, Value = Math.Clamp(initial, min, max), Height = 24 };
        slider.ValueChanged += (_, val) =>
        {
            lbl.Text = $"{label} {val:0.##} {unit}";
            onChange(val);
            ScheduleDebouncedSave();
        };
        g.Children.Add(slider);
        Grid.SetRow(slider, 1);
        return g;
    }

    private Grid CreateCombo(string label, string[] options, int selectedIndex, Action<int> onSelect, bool reloadAfter = false)
    {
        var g = new Grid { ColumnDefinitions = new ColumnDefinitions("*,210"), Margin = new Thickness(0, 4) };
        g.Children.Add(new TextBlock
        {
            Text = label,
            FontSize = 11.5,
            Foreground = StormThemeBrushes.BodyText,
            VerticalAlignment = VerticalAlignment.Center,
            TextWrapping = TextWrapping.Wrap,
            Margin = new Thickness(0, 0, 12, 0)
        });
        var combo = new ComboBox
        {
            HorizontalAlignment = HorizontalAlignment.Stretch,
            MinWidth = 0,
            ItemsSource = options,
            SelectedIndex = Math.Clamp(selectedIndex, 0, Math.Max(0, options.Length - 1))
        };
        combo.SelectionChanged += (_, _) =>
        {
            if (combo.SelectedIndex >= 0)
            {
                onSelect(combo.SelectedIndex);
                CommitSave();
                if (reloadAfter)
                {
                    LoadTab(_activeTabIndex);
                }
            }
        };
        g.Children.Add(combo);
        Grid.SetColumn(combo, 1);
        return g;
    }

    private Grid CreateTextRow(string label, string initial, Action<string> onChange)
    {
        var g = new Grid { ColumnDefinitions = new ColumnDefinitions("*,210"), Margin = new Thickness(0, 4) };
        g.Children.Add(new TextBlock
        {
            Text = label,
            FontSize = 11.5,
            Foreground = StormThemeBrushes.BodyText,
            VerticalAlignment = VerticalAlignment.Center,
            TextWrapping = TextWrapping.Wrap,
            Margin = new Thickness(0, 0, 12, 0)
        });
        var tb = new TextBox { Text = initial, Height = 30, MinWidth = 0 };
        tb.TextChanged += (_, _) =>
        {
            onChange(tb.Text ?? string.Empty);
            ScheduleDebouncedSave();
        };
        g.Children.Add(tb);
        Grid.SetColumn(tb, 1);
        return g;
    }

    private Grid CreatePathRow(string label, string initial, string[] patterns, Action<string> onTextChange, Action<string> onPicked)
    {
        var g = new Grid { ColumnDefinitions = new ColumnDefinitions("*,180,Auto"), Margin = new Thickness(0, 4) };
        g.Children.Add(new TextBlock
        {
            Text = label,
            FontSize = 11.5,
            Foreground = StormThemeBrushes.BodyText,
            VerticalAlignment = VerticalAlignment.Center,
            TextWrapping = TextWrapping.Wrap,
            Margin = new Thickness(0, 0, 12, 0)
        });
        var tb = new TextBox { Text = initial, Height = 30, MinWidth = 0 };
        tb.TextChanged += (_, _) => onTextChange(tb.Text ?? string.Empty);
        g.Children.Add(tb);
        Grid.SetColumn(tb, 1);
        var btn = new Button
        {
            Content = GbcaLoc.T("BtnBrowse"),
            Foreground = StormThemeBrushes.PrimaryText,
            Height = 30,
            Margin = new Thickness(6, 0, 0, 0),
            Padding = new Thickness(10, 0),
            Cursor = new Cursor(StandardCursorType.Hand)
        };
        btn.Click += async (_, _) =>
        {
            var top = TopLevel.GetTopLevel(this);
            if (top?.StorageProvider == null) return;
            var files = await top.StorageProvider.OpenFilePickerAsync(new FilePickerOpenOptions
            {
                Title = label,
                AllowMultiple = false,
                FileTypeFilter = [new FilePickerFileType(label) { Patterns = patterns }, FilePickerFileTypes.All]
            });
            string? path = files.Count > 0 ? files[0].TryGetLocalPath() : null;
            if (!string.IsNullOrWhiteSpace(path))
            {
                tb.Text = path;
                onPicked(path);
            }
        };
        g.Children.Add(btn);
        Grid.SetColumn(btn, 2);
        return g;
    }
}
