// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Mozilla Public License, Version 2.0 (MPL-2.0).

using System.Globalization;
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
using StormGbca.Core.Configuration;
using StormGbca.Core.Library;
using StormGbca.Core.Localization;
using StormGbca.Core.Native;
using StormGbca.Core.Subsystems;
using StormGbca.UI.Common;
using StormGbca.UI.Views.Bento;

namespace StormGbca.UI.Views.Sections;

public sealed class CatalogView : Grid
{
    private enum CatalogFilterKind
    {
        All = 0,
        Gb = 1,
        Gbc = 2,
        Gba = 3,
        Translations = 4,
        Updates = 5,
    }

    private readonly TextBlock _titleText;
    private readonly TextBlock _subtitleText;
    private readonly AuroraSearchBox _searchBox;
    private readonly Border _refreshBtn;
    private readonly TextBlock _refreshLabel;
    private readonly Border _folderBtn;
    private readonly TextBlock _folderLabel;
    private readonly WrapPanel _chipsPanel;
    private readonly Border _downloadBanner;
    private readonly TextBlock _dlTitleText;
    private readonly ProgressBar _dlProgressBar;
    private readonly TextBlock _dlStatsText;
    private readonly TextBlock _dlSpeedText;
    private readonly TextBlock _dlPercentText;
    private readonly Border _dlCancelBtn;
    private readonly TextBlock _dlCancelLabel;
    private readonly WrapPanel _cardsWrap;
    private readonly TextBlock _emptyLabel;
    private readonly ScrollViewer _scrollViewer;

    private readonly List<(CatalogFilterKind Kind, Color Accent, Border Chip, TextBlock Label)> _chips = [];
    private CatalogFilterKind _activeFilter = CatalogFilterKind.All;
    private string _searchQuery = string.Empty;
    private bool _isLoading;
    private bool _isDownloading;
    private CancellationTokenSource? _downloadCts;

    public CatalogView()
    {
        Margin = new Thickness(16, 0, 16, 0);
        RowDefinitions = new RowDefinitions("Auto,Auto,Auto,*");

        // 1. Top Bar: Title, Search, Refresh, Download Folder Picker
        var topBar = new Grid
        {
            Margin = new Thickness(0, 8, 0, 8),
            ColumnDefinitions = new ColumnDefinitions("*,Auto,Auto,Auto"),
            ColumnSpacing = 8
        };

        var titleCol = new StackPanel { Spacing = 2, VerticalAlignment = VerticalAlignment.Center };
        _titleText = new TextBlock
        {
            Text = GbcaLoc.T("CatalogTitle"),
            FontSize = 18,
            FontWeight = FontWeight.Black,
            Foreground = StormThemeBrushes.PrimaryText
        };
        _subtitleText = new TextBlock
        {
            Text = GbcaLoc.T("CatalogSubtitle"),
            FontSize = 11.5,
            Foreground = StormThemeBrushes.SecondaryText,
            TextTrimming = TextTrimming.CharacterEllipsis
        };
        titleCol.Children.Add(_titleText);
        titleCol.Children.Add(_subtitleText);
        topBar.Children.Add(titleCol);

        _searchBox = new AuroraSearchBox
        {
            PlaceholderText = GbcaLoc.T("CatalogSearchPlaceholder"),
            Width = 250,
            VerticalAlignment = VerticalAlignment.Center
        };
        _searchBox.TextChanged += (_, _) =>
        {
            _searchQuery = _searchBox.Text ?? string.Empty;
            RebuildCards();
        };
        topBar.Children.Add(_searchBox);
        Grid.SetColumn(_searchBox, 1);

        (_folderBtn, _folderLabel) = CreateHeaderButton(AuroraIconKind.FolderVault, BuildFolderButtonText(), async () => await PickDownloadFolderAsync());
        topBar.Children.Add(_folderBtn);
        Grid.SetColumn(_folderBtn, 2);

        (_refreshBtn, _refreshLabel) = CreateHeaderButton(AuroraIconKind.ResetCycle, GbcaLoc.T("CatalogRefreshBtn"), () => _ = LoadCatalogAsync(forceRefresh: true));
        topBar.Children.Add(_refreshBtn);
        Grid.SetColumn(_refreshBtn, 3);

        Children.Add(topBar);

        // 2. Filter Chips Row
        _chipsPanel = new WrapPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 0, 0, 6) };
        AddFilterChip(CatalogFilterKind.All, AuroraIconKind.FilterFunnel, GbcaLoc.T("CatalogFilterAll"), Color.Parse("#38BDF8"));
        AddFilterChip(CatalogFilterKind.Gb, AuroraIconKind.RomCartridge, "GB", Color.Parse("#8BAC0F"));
        AddFilterChip(CatalogFilterKind.Gbc, AuroraIconKind.ThemePalette, "GBC", Color.Parse("#7B42F6"));
        AddFilterChip(CatalogFilterKind.Gba, AuroraIconKind.GamepadController, "GBA", Color.Parse("#4F46E5"));
        AddFilterChip(CatalogFilterKind.Translations, AuroraIconKind.AiTranslatorSpark, GbcaLoc.T("CatalogFilterTranslations"), Color.Parse("#EC4899"));
        AddFilterChip(CatalogFilterKind.Updates, AuroraIconKind.UpdateRocket, string.Format(CultureInfo.InvariantCulture, GbcaLoc.T("CatalogFilterUpdatesFmt"), 0), Color.Parse("#10B981"));
        Children.Add(_chipsPanel);
        Grid.SetRow(_chipsPanel, 1);

        // 3. Active Download Banner
        _downloadBanner = new Border
        {
            IsVisible = false,
            Margin = new Thickness(0, 0, 0, 8),
            Padding = new Thickness(14, 10),
            CornerRadius = new CornerRadius(12),
            BorderThickness = new Thickness(1)
        };
        var dlGrid = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto"), RowDefinitions = new RowDefinitions("Auto,Auto,Auto"), ColumnSpacing = 12, RowSpacing = 6 };
        _dlTitleText = new TextBlock { FontSize = 12.5, FontWeight = FontWeight.Bold, Foreground = StormThemeBrushes.PrimaryText, TextTrimming = TextTrimming.CharacterEllipsis };
        dlGrid.Children.Add(_dlTitleText);

        (_dlCancelBtn, _dlCancelLabel) = CreateHeaderButton(AuroraIconKind.WindowClose, GbcaLoc.T("CatalogCancelBtn"), () => _downloadCts?.Cancel());
        _dlCancelBtn.Height = 28;
        dlGrid.Children.Add(_dlCancelBtn);
        Grid.SetColumn(_dlCancelBtn, 1);
        Grid.SetRowSpan(_dlCancelBtn, 3);

        _dlProgressBar = new ProgressBar { Minimum = 0, Maximum = 100, Value = 0, Height = 7 };
        dlGrid.Children.Add(_dlProgressBar);
        Grid.SetRow(_dlProgressBar, 1);

        var statsRow = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto,Auto"), ColumnSpacing = 10 };
        _dlStatsText = new TextBlock { FontSize = 11, Foreground = StormThemeBrushes.SecondaryText };
        _dlSpeedText = new TextBlock { FontSize = 11, Foreground = StormThemeBrushes.SecondaryText };
        _dlPercentText = new TextBlock { FontSize = 11, FontWeight = FontWeight.Bold, Foreground = StormThemeBrushes.Accent };
        statsRow.Children.Add(_dlStatsText);
        statsRow.Children.Add(_dlSpeedText);
        Grid.SetColumn(_dlSpeedText, 1);
        statsRow.Children.Add(_dlPercentText);
        Grid.SetColumn(_dlPercentText, 2);
        dlGrid.Children.Add(statsRow);
        Grid.SetRow(statsRow, 2);

        _downloadBanner.Child = dlGrid;
        Children.Add(_downloadBanner);
        Grid.SetRow(_downloadBanner, 2);

        // 4. Responsive Cards Host
        _cardsWrap = new WrapPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Left };
        _emptyLabel = new TextBlock
        {
            Text = GbcaLoc.T("CatalogEmptyMsg"),
            FontSize = 13,
            Foreground = StormThemeBrushes.SecondaryText,
            Margin = new Thickness(4, 20, 4, 20),
            IsVisible = false
        };
        var bodyStack = new StackPanel { Spacing = 8 };
        bodyStack.Children.Add(_emptyLabel);
        bodyStack.Children.Add(_cardsWrap);

        _scrollViewer = new ScrollViewer
        {
            Padding = new Thickness(0, 0, 12, 16),
            HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled,
            VerticalScrollBarVisibility = ScrollBarVisibility.Auto,
            Content = bodyStack
        };
        Children.Add(_scrollViewer);
        Grid.SetRow(_scrollViewer, 3);

        StormGbcaUiState.Instance.RomUpdatesChanged += () => Dispatcher.UIThread.Post(() =>
        {
            UpdateChipLabelsAndStyles();
            RebuildCards();
        });
        StormGbcaUiState.Instance.Library.LibraryChanged += () => Dispatcher.UIThread.Post(RebuildCards);
        StormGbcaUiState.Instance.SettingsSaved += () => Dispatcher.UIThread.Post(() =>
        {
            _folderLabel.Text = BuildFolderButtonText();
        });
        GbcaLoc.LanguageChanged += _ => Dispatcher.UIThread.Post(ApplyLocalization);
        StormThemeBrushes.BindTheme(this, ApplyThemeStyles);

        ApplyLocalization();
        ApplyThemeStyles();
    }

    public async Task EnsureLoadedAsync(bool forceRefresh = false)
    {
        if (!forceRefresh && StormGbcaUiState.Instance.CatalogGames.Count > 0)
        {
            UpdateChipLabelsAndStyles();
            RebuildCards();
            return;
        }
        await LoadCatalogAsync(forceRefresh);
    }

    private async Task LoadCatalogAsync(bool forceRefresh)
    {
        if (_isLoading) return;
        _isLoading = true;
        Dispatcher.UIThread.Post(() =>
        {
            _emptyLabel.Text = GbcaLoc.T("CatalogLoadingMsg");
            _emptyLabel.IsVisible = StormGbcaUiState.Instance.CatalogGames.Count == 0;
        });

        try
        {
            await StormGbcaUiState.Instance.RefreshCatalogAndUpdatesAsync(forceRefresh);
        }
        catch (Exception ex)
        {
            Dispatcher.UIThread.Post(() => StormGbcaUiState.Instance.NotifyStatus(ex.Message));
        }
        finally
        {
            _isLoading = false;
            Dispatcher.UIThread.Post(() =>
            {
                UpdateChipLabelsAndStyles();
                RebuildCards();
            });
        }
    }

    private void ApplyLocalization()
    {
        _titleText.Text = GbcaLoc.T("CatalogTitle");
        _subtitleText.Text = GbcaLoc.T("CatalogSubtitle");
        _searchBox.PlaceholderText = GbcaLoc.T("CatalogSearchPlaceholder");
        _refreshLabel.Text = GbcaLoc.T("CatalogRefreshBtn");
        _folderLabel.Text = BuildFolderButtonText();
        _dlCancelLabel.Text = GbcaLoc.T("CatalogCancelBtn");
        UpdateChipLabelsAndStyles();
        RebuildCards();
    }

    private void ApplyThemeStyles()
    {
        _titleText.Foreground = StormThemeBrushes.PrimaryText;
        _subtitleText.Foreground = StormThemeBrushes.SecondaryText;
        _emptyLabel.Foreground = StormThemeBrushes.SecondaryText;
        _refreshBtn.Background = StormThemeBrushes.RowSurface;
        _refreshBtn.BorderBrush = StormThemeBrushes.SubtleBorder;
        _refreshLabel.Foreground = StormThemeBrushes.PrimaryText;
        _folderBtn.Background = StormThemeBrushes.RowSurface;
        _folderBtn.BorderBrush = StormThemeBrushes.SubtleBorder;
        _folderLabel.Foreground = StormThemeBrushes.PrimaryText;
        _downloadBanner.Background = StormThemeBrushes.CardSurface;
        _downloadBanner.BorderBrush = new SolidColorBrush(StormThemeBrushes.AccentPrimaryColor);
        _dlProgressBar.Foreground = StormThemeBrushes.Accent;
        _dlTitleText.Foreground = StormThemeBrushes.PrimaryText;
        _dlStatsText.Foreground = StormThemeBrushes.SecondaryText;
        _dlSpeedText.Foreground = StormThemeBrushes.SecondaryText;
        _dlPercentText.Foreground = StormThemeBrushes.Accent;
        _dlCancelBtn.Background = StormThemeBrushes.RowSurface;
        _dlCancelBtn.BorderBrush = StormThemeBrushes.SubtleBorder;
        _dlCancelLabel.Foreground = StormThemeBrushes.PrimaryText;
        UpdateChipLabelsAndStyles();
        RebuildCards();
    }

    private string BuildFolderButtonText()
    {
        string dir = SgwCatalogService.ResolveTargetDirectory(StormGbcaUiState.Instance.Settings);
        string leaf = Path.GetFileName(dir.TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar));
        if (string.IsNullOrWhiteSpace(leaf)) leaf = dir;
        return $"{GbcaLoc.T("CatalogFolderLabel")} {leaf}";
    }

    private async Task PickDownloadFolderAsync()
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
            var s = StormGbcaUiState.Instance.Settings;
            s.RomDirectories.RemoveAll(d => string.Equals(d, folder, StringComparison.OrdinalIgnoreCase));
            s.RomDirectories.Insert(0, folder);
            StormGbcaUiState.Instance.SaveSettings(notify: true);
            _folderLabel.Text = BuildFolderButtonText();
        }
    }

    private void AddFilterChip(CatalogFilterKind kind, AuroraIconKind icon, string text, Color accent)
    {
        var chip = new Border
        {
            Height = 32,
            Padding = new Thickness(12, 4),
            Margin = new Thickness(0, 0, 8, 6),
            CornerRadius = new CornerRadius(16),
            BorderThickness = new Thickness(1),
            Cursor = new Cursor(StandardCursorType.Hand)
        };
        var row = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 6, VerticalAlignment = VerticalAlignment.Center };
        row.Children.Add(new AuroraVectorIcon { Kind = icon, Size = 14, VerticalAlignment = VerticalAlignment.Center });
        var tb = new TextBlock { Text = text, FontSize = 11.5, FontWeight = FontWeight.SemiBold, VerticalAlignment = VerticalAlignment.Center };
        row.Children.Add(tb);
        chip.Child = row;
        chip.PointerPressed += (_, _) =>
        {
            AuroraTactileFeedbackService.Instance.PlayClick();
            _activeFilter = kind;
            UpdateChipLabelsAndStyles();
            RebuildCards();
        };
        _chipsPanel.Children.Add(chip);
        _chips.Add((kind, accent, chip, tb));
    }

    private void UpdateChipLabelsAndStyles()
    {
        int updateCount = StormGbcaUiState.Instance.AvailableRomUpdates.Count;
        foreach (var (kind, accent, chip, label) in _chips)
        {
            label.Text = kind switch
            {
                CatalogFilterKind.All => GbcaLoc.T("CatalogFilterAll"),
                CatalogFilterKind.Gb => "GB",
                CatalogFilterKind.Gbc => "GBC",
                CatalogFilterKind.Gba => "GBA",
                CatalogFilterKind.Translations => GbcaLoc.T("CatalogFilterTranslations"),
                CatalogFilterKind.Updates => string.Format(CultureInfo.InvariantCulture, GbcaLoc.T("CatalogFilterUpdatesFmt"), updateCount),
                _ => label.Text
            };

            bool sel = kind == _activeFilter;
            chip.Background = sel
                ? new SolidColorBrush(Color.FromArgb(80, accent.R, accent.G, accent.B))
                : StormThemeBrushes.RowSurface;
            chip.BorderBrush = sel ? new SolidColorBrush(accent) : StormThemeBrushes.SubtleBorder;
            label.Foreground = sel ? StormThemeBrushes.PrimaryText : StormThemeBrushes.BodyText;
        }
    }

    private void RebuildCards()
    {
        _cardsWrap.Children.Clear();

        var state = StormGbcaUiState.Instance;
        var allGames = state.CatalogGames;
        var updatesByGameId = state.AvailableRomUpdates.Values
            .GroupBy(u => u.RemoteGame.GameId)
            .ToDictionary(g => g.Key, g => g.First());

        IEnumerable<SgwCatalogGame> filtered = allGames;

        filtered = _activeFilter switch
        {
            CatalogFilterKind.Gb => filtered.Where(g => g.Platform is StormPlatform.Gb or StormPlatform.Sgb),
            CatalogFilterKind.Gbc => filtered.Where(g => g.Platform == StormPlatform.Gbc),
            CatalogFilterKind.Gba => filtered.Where(g => g.Platform == StormPlatform.Gba),
            CatalogFilterKind.Translations => filtered.Where(g => g.IsRussianTranslation || g.IsTranslationRelease),
            CatalogFilterKind.Updates => filtered.Where(g => updatesByGameId.ContainsKey(g.GameId)),
            _ => filtered
        };

        if (!string.IsNullOrWhiteSpace(_searchQuery))
        {
            string q = _searchQuery.Trim();
            filtered = filtered.Where(g =>
                g.Title.Contains(q, StringComparison.OrdinalIgnoreCase) ||
                g.OriginalTitle.Contains(q, StringComparison.OrdinalIgnoreCase) ||
                g.Description.Contains(q, StringComparison.OrdinalIgnoreCase) ||
                g.Changelog.Contains(q, StringComparison.OrdinalIgnoreCase));
        }

        var list = filtered.Take(240).ToList();
        if (list.Count == 0)
        {
            _emptyLabel.Text = _isLoading ? GbcaLoc.T("CatalogLoadingMsg") : GbcaLoc.T("CatalogEmptyMsg");
            _emptyLabel.IsVisible = true;
            return;
        }

        _emptyLabel.IsVisible = false;
        var library = state.Library.AllRoms;

        foreach (var game in list)
        {
            updatesByGameId.TryGetValue(game.GameId, out var updateInfo);
            bool installed = updateInfo == null && state.CatalogService.IsCatalogGameInstalled(game, library);
            _cardsWrap.Children.Add(BuildCatalogCard(game, updateInfo, installed));
        }
    }

    private Control BuildCatalogCard(SgwCatalogGame game, SgwGameUpdateInfo? updateInfo, bool installed)
    {
        Color platColor = StormGbcaUiState.GetPlatformBadgeColor(game.Platform);

        var card = new AuroraGlassCard
        {
            Width = 300,
            Height = 228,
            Margin = new Thickness(0, 0, 12, 12),
            Padding = new Thickness(12),
            SpotlightEnabled = true
        };

        var root = new Grid { RowDefinitions = new RowDefinitions("*,Auto"), RowSpacing = 10 };

        // Top section: Cover + Info
        var topRow = new Grid { ColumnDefinitions = new ColumnDefinitions("88,*"), ColumnSpacing = 12 };

        var coverBox = new Border
        {
            Width = 88,
            Height = 114,
            CornerRadius = new CornerRadius(10),
            ClipToBounds = true,
            BorderThickness = new Thickness(1),
            BorderBrush = new SolidColorBrush(Color.FromArgb(140, platColor.R, platColor.G, platColor.B)),
            Background = new LinearGradientBrush
            {
                StartPoint = new RelativePoint(0, 0, RelativeUnit.Relative),
                EndPoint = new RelativePoint(1, 1, RelativeUnit.Relative),
                GradientStops =
                {
                    new GradientStop(Color.FromArgb(80, platColor.R, platColor.G, platColor.B), 0.0),
                    new GradientStop(StormThemeBrushes.IsDark ? Color.FromArgb(180, 12, 16, 28) : Color.FromArgb(225, 241, 245, 249), 1.0)
                }
            }
        };

        var coverLayer = new Grid();
        var placeholderIcon = new AuroraVectorIcon
        {
            Kind = AuroraIconKind.RomCartridge,
            Size = 30,
            CustomColorOverride = new SolidColorBrush(platColor),
            HorizontalAlignment = HorizontalAlignment.Center,
            VerticalAlignment = VerticalAlignment.Center
        };
        coverLayer.Children.Add(placeholderIcon);
        var coverImg = new Image { Stretch = Stretch.UniformToFill };
        coverLayer.Children.Add(coverImg);
        coverBox.Child = coverLayer;
        topRow.Children.Add(coverBox);

        LoadCoverAsync(game, coverImg, placeholderIcon);

        var infoCol = new StackPanel { Spacing = 5 };
        infoCol.Children.Add(new TextBlock
        {
            Text = game.Title,
            FontSize = 13,
            FontWeight = FontWeight.Bold,
            Foreground = StormThemeBrushes.PrimaryText,
            MaxLines = 2,
            TextWrapping = TextWrapping.Wrap,
            TextTrimming = TextTrimming.CharacterEllipsis
        });

        var badges = new WrapPanel { Orientation = Orientation.Horizontal };
        badges.Children.Add(BentoRomCard.CreateTagBadge(game.PlatformName, platColor, true));
        if (game.IsRussianTranslation)
        {
            badges.Children.Add(BentoRomCard.CreateTagBadge("RUS", Color.Parse("#EC4899"), true));
        }
        else if (game.Regions.Count > 0)
        {
            badges.Children.Add(BentoRomCard.CreateTagBadge(game.Regions[0].ToUpperInvariant(), Color.Parse("#38BDF8"), false));
        }
        if (!string.IsNullOrWhiteSpace(game.CleanVersion))
        {
            badges.Children.Add(BentoRomCard.CreateTagBadge(game.CleanVersion, Color.Parse("#8B5CF6"), false));
        }
        if (game.HasRetroAchievements)
        {
            badges.Children.Add(BentoRomCard.CreateTagBadge("RA", Color.Parse("#F59E0B"), true));
        }
        infoCol.Children.Add(badges);

        string sizeMeta = game.DisplaySize;
        if (game.Downloads > 0) sizeMeta = $"{sizeMeta} | {game.Downloads} DL";
        infoCol.Children.Add(new TextBlock
        {
            Text = sizeMeta,
            FontSize = 10.5,
            Foreground = StormThemeBrushes.SecondaryText
        });

        string preview = !string.IsNullOrWhiteSpace(game.Changelog)
            ? game.Changelog.Replace('\n', ' ').Trim()
            : (!string.IsNullOrWhiteSpace(game.Description) ? game.Description.Replace('\n', ' ').Trim() : game.TranslationAuthor);
        if (!string.IsNullOrWhiteSpace(preview))
        {
            infoCol.Children.Add(new TextBlock
            {
                Text = preview,
                FontSize = 10.5,
                Foreground = StormThemeBrushes.SecondaryText,
                MaxLines = 2,
                TextWrapping = TextWrapping.Wrap,
                TextTrimming = TextTrimming.CharacterEllipsis
            });
        }

        topRow.Children.Add(infoCol);
        Grid.SetColumn(infoCol, 1);
        root.Children.Add(topRow);

        // Action Button Row
        var actionBtn = new AuroraVolumetricButton
        {
            Height = 36,
            HorizontalAlignment = HorizontalAlignment.Stretch,
            IsHero = updateInfo != null || !installed,
            IsEnabled = !_isDownloading && (game.FileExists || !string.IsNullOrWhiteSpace(game.DownloadUrl))
        };

        string btnLabel;
        if (updateInfo != null)
        {
            btnLabel = updateInfo.Kind == SgwUpdateKind.TranslationAvailable
                ? GbcaLoc.T("CatalogTranslateBtn")
                : string.Format(CultureInfo.InvariantCulture, GbcaLoc.T("CatalogUpdateFmt"), updateInfo.LocalVersion, updateInfo.RemoteVersion);
            ToolTip.SetTip(actionBtn, GbcaLoc.T("CatalogUpdateTooltip"));
        }
        else if (installed)
        {
            btnLabel = GbcaLoc.T("CatalogInstalledBadge");
        }
        else
        {
            btnLabel = GbcaLoc.T("CatalogDownloadBtn");
        }

        actionBtn.Content = new TextBlock
        {
            Text = btnLabel,
            FontSize = 12,
            FontWeight = FontWeight.Bold,
            Foreground = actionBtn.IsHero ? Brushes.White : StormThemeBrushes.PrimaryText,
            HorizontalAlignment = HorizontalAlignment.Center,
            VerticalAlignment = VerticalAlignment.Center
        };

        actionBtn.Click += async (_, _) =>
        {
            AuroraTactileFeedbackService.Instance.PlayClick();
            await StartCatalogDownloadAsync(game, updateInfo?.LocalRom);
        };

        root.Children.Add(actionBtn);
        Grid.SetRow(actionBtn, 1);

        card.Content = root;
        return card;
    }

    private void LoadCoverAsync(SgwCatalogGame game, Image targetImage, Control fallback)
    {
        if (string.IsNullOrWhiteSpace(game.CoverUrl)) return;
        _ = Task.Run(async () =>
        {
            string? path = await StormGbcaUiState.Instance.CatalogService.EnsureCatalogCoverCachedAsync(game).ConfigureAwait(false);
            if (string.IsNullOrWhiteSpace(path)) return;
            await Dispatcher.UIThread.InvokeAsync(() =>
            {
                var bmp = BitmapHelper.LoadBitmapCached(path);
                if (bmp != null)
                {
                    targetImage.Source = bmp;
                    fallback.IsVisible = false;
                }
            });
        });
    }

    private async Task StartCatalogDownloadAsync(SgwCatalogGame game, GbcaRomRecord? replacingRom)
    {
        if (_isDownloading) return;
        _isDownloading = true;
        _downloadCts = new CancellationTokenSource();

        _dlTitleText.Text = string.Format(CultureInfo.InvariantCulture, GbcaLoc.T("CatalogDownloadingFmt"), game.Title);
        _dlProgressBar.Value = 0;
        _dlProgressBar.IsIndeterminate = game.FileSizeBytes <= 0;
        _dlStatsText.Text = game.DisplaySize;
        _dlSpeedText.Text = string.Empty;
        _dlPercentText.Text = string.Empty;
        _downloadBanner.IsVisible = true;
        RebuildCards();

        var progress = new Progress<SgwDownloadProgress>(p =>
        {
            Dispatcher.UIThread.Post(() =>
            {
                long total = p.TotalBytes > 0 ? p.TotalBytes : game.FileSizeBytes;
                if (total > 1)
                {
                    _dlProgressBar.IsIndeterminate = false;
                    double pct = Math.Clamp((double)p.DownloadedBytes * 100.0 / total, 0.0, 100.0);
                    _dlProgressBar.Value = pct;
                    _dlStatsText.Text = string.Format(
                        CultureInfo.InvariantCulture,
                        "{0:F1} / {1:F1} MB",
                        p.DownloadedBytes / (1024.0 * 1024.0),
                        total / (1024.0 * 1024.0));
                    _dlPercentText.Text = $"{(int)pct}%";
                }
                else if (!string.IsNullOrWhiteSpace(p.StatusText))
                {
                    _dlStatsText.Text = p.StatusText;
                }
                _dlSpeedText.Text = p.SpeedMbPerSec > 0.05
                    ? string.Format(CultureInfo.InvariantCulture, GbcaLoc.T("UpdateSpeedMbFmt"), p.SpeedMbPerSec)
                    : string.Empty;
            });
        });

        try
        {
            var res = await StormGbcaUiState.Instance.DownloadCatalogGameAsync(game, replacingRom, progress, _downloadCts.Token);
            if (!res.Success && !string.IsNullOrWhiteSpace(res.ErrorMessage))
            {
                StormGbcaUiState.Instance.NotifyStatus(res.ErrorMessage);
            }
        }
        catch (OperationCanceledException)
        {
            StormGbcaUiState.Instance.NotifyStatus(GbcaLoc.T("CatalogCancelBtn"));
        }
        catch (Exception ex)
        {
            StormGbcaUiState.Instance.NotifyStatus(ex.Message);
        }
        finally
        {
            _isDownloading = false;
            _downloadBanner.IsVisible = false;
            _downloadCts?.Dispose();
            _downloadCts = null;
            UpdateChipLabelsAndStyles();
            RebuildCards();
        }
    }

    private static (Border Btn, TextBlock Label) CreateHeaderButton(AuroraIconKind icon, string label, Action onClick)
    {
        var btn = new Border
        {
            Height = 32,
            Padding = new Thickness(10, 4),
            CornerRadius = new CornerRadius(8),
            Background = StormThemeBrushes.RowSurface,
            BorderBrush = StormThemeBrushes.SubtleBorder,
            BorderThickness = new Thickness(1),
            Cursor = new Cursor(StandardCursorType.Hand)
        };
        var row = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 6, VerticalAlignment = VerticalAlignment.Center };
        row.Children.Add(new AuroraVectorIcon { Kind = icon, Size = 14, VerticalAlignment = VerticalAlignment.Center });
        var tb = new TextBlock
        {
            Text = label,
            FontSize = 11.5,
            FontWeight = FontWeight.Medium,
            Foreground = StormThemeBrushes.PrimaryText,
            VerticalAlignment = VerticalAlignment.Center
        };
        row.Children.Add(tb);
        btn.Child = row;
        btn.PointerPressed += (_, _) =>
        {
            AuroraTactileFeedbackService.Instance.PlayClick();
            onClick();
        };
        return (btn, tb);
    }
}
