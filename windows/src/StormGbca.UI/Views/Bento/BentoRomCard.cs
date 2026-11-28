// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Mozilla Public License, Version 2.0 (MPL-2.0).

using Avalonia;
using Avalonia.Controls;
using Avalonia.Input;
using Avalonia.Layout;
using Avalonia.Media;
using Storm.AuroraFluid.Kit.Controls;
using Storm.AuroraFluid.Kit.Icons;
using Storm.AuroraFluid.Kit.Services;
using StormGbca.Core.Configuration;
using StormGbca.Core.Library;
using StormGbca.Core.Native;
using StormGbca.UI.Common;

namespace StormGbca.UI.Views.Bento;

/// <summary>
/// 3D Boxart Cartridge / Game Card with spine shading, color-coded Platform badge,
/// Region badge, Hardware capability badges, and Libretro cover art display.
/// </summary>
public class BentoRomCard : AuroraGlassCard
{
    private readonly GbcaRomRecord _rom;
    private readonly Border _box3D;
    private readonly Image _coverImage;
    private readonly Border _fallbackArt;
    private readonly TextBlock _placeholderTitle;
    private readonly TextBlock _titleText;
    private readonly TextBlock _sizeText;
    private readonly Border _starButton;
    private readonly AuroraVectorIcon _starIcon;
    private readonly Border _selectionBorder;
    private readonly Border _platPill;
    private readonly WrapPanel _hwRow;
    private readonly Border _raPill;
    private readonly Border _updatePill;
    private readonly TextBlock _updatePillText;

    public GbcaRomRecord Rom => _rom;

    public BentoRomCard(GbcaRomRecord rom)
    {
        _rom = rom;
        Width = 200;
        Height = 272;
        Margin = new Thickness(6);
        SpotlightEnabled = true;
        Cursor = new Cursor(StandardCursorType.Hand);

        var rootGrid = new Grid { RowDefinitions = new RowDefinitions("152,*,Auto") };

        // 1. 3D Boxart Frame with Spine Shading
        Color platColor = StormGbcaUiState.GetPlatformBadgeColor(rom.Platform);
        _box3D = new Border
        {
            Width = 148,
            Height = 140,
            CornerRadius = new CornerRadius(10),
            Margin = new Thickness(0, 10, 0, 0),
            HorizontalAlignment = HorizontalAlignment.Center,
            VerticalAlignment = VerticalAlignment.Center,
            ClipToBounds = true,
            BorderBrush = new SolidColorBrush(Color.FromArgb(140, platColor.R, platColor.G, platColor.B)),
            BorderThickness = new Thickness(1.2)
        };

        var artLayer = new Grid();

        // Fallback procedural 3D cartridge emblem when boxart is not cached yet
        var placeholder = BuildProceduralBoxPlaceholder(rom, platColor, out _placeholderTitle);
        _fallbackArt = new Border
        {
            HorizontalAlignment = HorizontalAlignment.Stretch,
            VerticalAlignment = VerticalAlignment.Stretch,
            Child = placeholder
        };
        artLayer.Children.Add(_fallbackArt);

        _coverImage = new Image
        {
            Stretch = Stretch.UniformToFill,
            HorizontalAlignment = HorizontalAlignment.Stretch,
            VerticalAlignment = VerticalAlignment.Stretch
        };
        LoadCoverIfPresent();
        artLayer.Children.Add(_coverImage);

        // 3D Spine Highlight (left vertical 10px bevel shading)
        var spineShade = new Border
        {
            Width = 12,
            HorizontalAlignment = HorizontalAlignment.Left,
            VerticalAlignment = VerticalAlignment.Stretch,
            IsHitTestVisible = false,
            Background = new LinearGradientBrush
            {
                StartPoint = new RelativePoint(0, 0, RelativeUnit.Relative),
                EndPoint = new RelativePoint(1, 0, RelativeUnit.Relative),
                GradientStops =
                {
                    new GradientStop(Color.FromArgb(115, 0, 0, 0), 0.0),
                    new GradientStop(Color.FromArgb(65, 255, 255, 255), 0.35),
                    new GradientStop(Color.FromArgb(0, 0, 0, 0), 1.0)
                }
            }
        };
        artLayer.Children.Add(spineShade);

        // Top gloss bevel
        var topGloss = new Border
        {
            Height = 24,
            VerticalAlignment = VerticalAlignment.Top,
            IsHitTestVisible = false,
            Background = new LinearGradientBrush
            {
                StartPoint = new RelativePoint(0, 0, RelativeUnit.Relative),
                EndPoint = new RelativePoint(0, 1, RelativeUnit.Relative),
                GradientStops =
                {
                    new GradientStop(Color.FromArgb(55, 255, 255, 255), 0.0),
                    new GradientStop(Color.FromArgb(0, 255, 255, 255), 1.0)
                }
            }
        };
        artLayer.Children.Add(topGloss);

        // Top-left Platform Badge inside boxart (independent toggle, OFF by default)
        _platPill = CreateTagBadge(rom.PlatformShortName, platColor, isSolid: true);
        _platPill.HorizontalAlignment = HorizontalAlignment.Left;
        _platPill.VerticalAlignment = VerticalAlignment.Top;
        _platPill.Margin = new Thickness(8, 8, 0, 0);
        artLayer.Children.Add(_platPill);

        // Bottom-right RetroAchievements Trophy Badge (independent toggle, OFF by default)
        _raPill = CreateTagBadge("RA", Color.Parse("#F59E0B"), isSolid: true);
        _raPill.HorizontalAlignment = HorizontalAlignment.Right;
        _raPill.VerticalAlignment = VerticalAlignment.Bottom;
        _raPill.Margin = new Thickness(0, 0, 7, 7);
        artLayer.Children.Add(_raPill);

        // Bottom-left SGW Update / Translation Badge (shown automatically when update is available)
        _updatePillText = new TextBlock
        {
            Text = "UPD",
            FontSize = 9,
            FontWeight = FontWeight.Bold,
            Foreground = Brushes.White
        };
        _updatePill = new Border
        {
            IsVisible = false,
            Background = new SolidColorBrush(Color.FromArgb(235, 16, 185, 129)),
            BorderBrush = new SolidColorBrush(Color.Parse("#10B981")),
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(5),
            Padding = new Thickness(6, 1),
            HorizontalAlignment = HorizontalAlignment.Left,
            VerticalAlignment = VerticalAlignment.Bottom,
            Margin = new Thickness(8, 0, 0, 7),
            Child = _updatePillText
        };
        artLayer.Children.Add(_updatePill);

        // Top-right Favorite Star
        _starIcon = new AuroraVectorIcon
        {
            Kind = AuroraIconKind.StarFavorite,
            Size = 13,
            HorizontalAlignment = HorizontalAlignment.Center,
            VerticalAlignment = VerticalAlignment.Center
        };
        _starButton = new Border
        {
            Width = 26,
            Height = 26,
            CornerRadius = new CornerRadius(7),
            BorderThickness = new Thickness(1),
            HorizontalAlignment = HorizontalAlignment.Right,
            VerticalAlignment = VerticalAlignment.Top,
            Margin = new Thickness(0, 7, 7, 0),
            Child = _starIcon
        };
        _starButton.PointerPressed += (_, e) =>
        {
            e.Handled = true;
            StormGbcaUiState.Instance.ToggleFavorite(_rom);
            UpdateFavoriteVisual();
        };
        artLayer.Children.Add(_starButton);

        _box3D.Child = artLayer;
        rootGrid.Children.Add(_box3D);

        // 2. Title & Hardware Badges
        var titleStack = new StackPanel
        {
            Margin = new Thickness(12, 6, 12, 4),
            Spacing = 4,
            VerticalAlignment = VerticalAlignment.Top
        };

        _titleText = new TextBlock
        {
            Text = rom.GetDisplayTitle(),
            FontSize = 12,
            FontWeight = FontWeight.Bold,
            Foreground = StormThemeBrushes.PrimaryText,
            TextTrimming = TextTrimming.CharacterEllipsis,
            MaxLines = 2,
            TextWrapping = TextWrapping.Wrap
        };
        titleStack.Children.Add(_titleText);

        _hwRow = new WrapPanel { Orientation = Orientation.Horizontal };
        titleStack.Children.Add(_hwRow);

        rootGrid.Children.Add(titleStack);
        Grid.SetRow(titleStack, 1);

        // 3. Bottom Metadata Row
        var bottomRow = new Grid
        {
            ColumnDefinitions = new ColumnDefinitions("*,Auto"),
            Margin = new Thickness(12, 0, 12, 8),
            VerticalAlignment = VerticalAlignment.Bottom
        };

        var codeText = new TextBlock
        {
            Text = string.IsNullOrWhiteSpace(rom.GameCode) ? rom.Crc32Hex : rom.GameCode,
            FontSize = 10,
            FontWeight = FontWeight.SemiBold,
            Foreground = new SolidColorBrush(platColor),
            TextTrimming = TextTrimming.CharacterEllipsis,
            VerticalAlignment = VerticalAlignment.Center
        };
        bottomRow.Children.Add(codeText);

        _sizeText = new TextBlock
        {
            Text = FormatRomSize(rom.RomSize),
            FontSize = 10,
            Foreground = StormThemeBrushes.SecondaryText,
            VerticalAlignment = VerticalAlignment.Center
        };
        bottomRow.Children.Add(_sizeText);
        Grid.SetColumn(_sizeText, 1);

        rootGrid.Children.Add(bottomRow);
        Grid.SetRow(bottomRow, 2);

        _selectionBorder = new Border
        {
            CornerRadius = CornerRadius,
            BorderThickness = new Thickness(2),
            BorderBrush = Brushes.Transparent,
            IsHitTestVisible = false
        };
        rootGrid.Children.Add(_selectionBorder);
        Grid.SetRowSpan(_selectionBorder, 3);

        Content = rootGrid;

        PointerPressed += (_, _) =>
        {
            AuroraTactileFeedbackService.Instance.PlayClick();
            StormGbcaUiState.Instance.SelectRom(_rom);
        };
        DoubleTapped += (_, _) => StormGbcaUiState.Instance.StartEmulation(_rom);

        void OnCoverArtUpdated(GbcaRomRecord updated)
        {
            if (string.Equals(updated.FilePath, _rom.FilePath, StringComparison.OrdinalIgnoreCase))
            {
                Avalonia.Threading.Dispatcher.UIThread.Post(LoadCoverIfPresent);
            }
        }
        void OnSettingsSaved() => Avalonia.Threading.Dispatcher.UIThread.Post(RefreshBadgesAndCover);
        void OnUpdatesChanged() => Avalonia.Threading.Dispatcher.UIThread.Post(RefreshUpdateBadge);

        AttachedToVisualTree += (_, _) =>
        {
            StormGbcaUiState.Instance.Library.CoverArtUpdated -= OnCoverArtUpdated;
            StormGbcaUiState.Instance.Library.CoverArtUpdated += OnCoverArtUpdated;
            StormGbcaUiState.Instance.SettingsSaved -= OnSettingsSaved;
            StormGbcaUiState.Instance.SettingsSaved += OnSettingsSaved;
            StormGbcaUiState.Instance.RomUpdatesChanged -= OnUpdatesChanged;
            StormGbcaUiState.Instance.RomUpdatesChanged += OnUpdatesChanged;
            RefreshUpdateBadge();
        };
        DetachedFromVisualTree += (_, _) =>
        {
            StormGbcaUiState.Instance.Library.CoverArtUpdated -= OnCoverArtUpdated;
            StormGbcaUiState.Instance.SettingsSaved -= OnSettingsSaved;
            StormGbcaUiState.Instance.RomUpdatesChanged -= OnUpdatesChanged;
        };

        StormThemeBrushes.BindTheme(this, () =>
        {
            bool dark = StormThemeBrushes.IsDark;
            _box3D.Background = new LinearGradientBrush
            {
                StartPoint = new RelativePoint(0, 0, RelativeUnit.Relative),
                EndPoint = new RelativePoint(1, 1, RelativeUnit.Relative),
                GradientStops =
                {
                    new GradientStop(Color.FromArgb(dark ? (byte)95 : (byte)55, platColor.R, platColor.G, platColor.B), 0.0),
                    new GradientStop(dark ? Color.FromArgb(180, 12, 16, 28) : Color.FromArgb(225, 241, 245, 249), 1.0)
                }
            };
            _box3D.BoxShadow = new BoxShadows(new BoxShadow
            {
                OffsetX = 3,
                OffsetY = 5,
                Blur = 10,
                Color = Color.FromArgb(dark ? (byte)140 : (byte)55, 0, 0, 0)
            });
            _placeholderTitle.Foreground = StormThemeBrushes.PrimaryText;
            _titleText.Foreground = StormThemeBrushes.PrimaryText;
            _sizeText.Foreground = StormThemeBrushes.SecondaryText;
            UpdateFavoriteVisual();
            UpdateSelectionVisual();
        });

        RefreshBadgesAndCover();
        RefreshUpdateBadge();
        UpdateFavoriteVisual();
        UpdateSelectionVisual();
    }

    private void RefreshUpdateBadge()
    {
        var upd = StormGbcaUiState.Instance.GetUpdateForRom(_rom);
        if (upd == null)
        {
            _updatePill.IsVisible = false;
            return;
        }
        _updatePillText.Text = upd.Kind == Core.Subsystems.SgwUpdateKind.TranslationAvailable
            ? "RUS"
            : $"{Core.Localization.GbcaLoc.T("BadgeUpdatePrefix")} {upd.RemoteVersion}";
        ToolTip.SetTip(_updatePill, Core.Localization.GbcaLoc.T("CatalogUpdateTooltip"));
        _updatePill.IsVisible = true;
    }

    private void RefreshBadgesAndCover()
    {
        var s = StormGbcaUiState.Instance.Settings;
        _platPill.IsVisible = s.ShowPlatformBadge;
        _raPill.IsVisible = s.ShowRaBadge;
        _hwRow.Children.Clear();
        if (s.ShowRegionBadge)
        {
            _hwRow.Children.Add(CreateTagBadge(_rom.RegionBadgeCode, Color.Parse("#38BDF8"), isSolid: false));
        }
        if (s.ShowHardwareBadges)
        {
            AddHardwareBadges(_hwRow, _rom.HardwareFlags);
        }
        _hwRow.IsVisible = _hwRow.Children.Count > 0;
        LoadCoverIfPresent();
    }

    private void LoadCoverIfPresent()
    {
        var s = StormGbcaUiState.Instance.Settings;
        if (!s.ShowLibretroCovers && !s.ShowRaCovers)
        {
            _coverImage.Source = null;
            _fallbackArt.IsVisible = true;
            return;
        }
        var bmp = BitmapHelper.LoadBitmapCached(_rom.CoverLocalPath);
        _coverImage.Source = bmp;
        _fallbackArt.IsVisible = bmp == null;
    }

    private static StackPanel BuildProceduralBoxPlaceholder(GbcaRomRecord rom, Color platColor, out TextBlock titleBlock)
    {
        var s = new StackPanel
        {
            HorizontalAlignment = HorizontalAlignment.Center,
            VerticalAlignment = VerticalAlignment.Center,
            Spacing = 6,
            Margin = new Thickness(14, 18, 10, 8)
        };
        s.Children.Add(new AuroraVectorIcon
        {
            Kind = AuroraIconKind.RomCartridge,
            Size = 36,
            CustomColorOverride = new SolidColorBrush(platColor),
            HorizontalAlignment = HorizontalAlignment.Center
        });
        titleBlock = new TextBlock
        {
            Text = string.IsNullOrWhiteSpace(rom.Title) ? rom.PlatformShortName : rom.Title,
            FontSize = 10,
            FontWeight = FontWeight.Bold,
            Foreground = StormThemeBrushes.PrimaryText,
            TextAlignment = TextAlignment.Center,
            TextTrimming = TextTrimming.CharacterEllipsis,
            MaxWidth = 116
        };
        s.Children.Add(titleBlock);
        return s;
    }

    public static void AddHardwareBadges(WrapPanel host, StormHwFlags flags)
    {
        if (flags.HasFlag(StormHwFlags.Rtc)) host.Children.Add(CreateTagBadge("RTC", Color.Parse("#10B981"), false));
        if (flags.HasFlag(StormHwFlags.Solar)) host.Children.Add(CreateTagBadge("SOLAR", Color.Parse("#F59E0B"), false));
        if (flags.HasFlag(StormHwFlags.Rumble)) host.Children.Add(CreateTagBadge("RUMBLE", Color.Parse("#EC4899"), false));
        if (flags.HasFlag(StormHwFlags.Gyro)) host.Children.Add(CreateTagBadge("GYRO", Color.Parse("#06B6D4"), false));
        if (flags.HasFlag(StormHwFlags.Tilt)) host.Children.Add(CreateTagBadge("TILT", Color.Parse("#8B5CF6"), false));
        if (flags.HasFlag(StormHwFlags.Sgb)) host.Children.Add(CreateTagBadge("SGB", Color.Parse("#F59E0B"), false));
    }

    public static Border CreateTagBadge(string text, Color color, bool isSolid)
    {
        Color textColor = isSolid || StormThemeBrushes.IsDark
            ? color
            : Color.FromRgb((byte)(color.R * 0.65), (byte)(color.G * 0.65), (byte)(color.B * 0.65));
        return new Border
        {
            Background = new SolidColorBrush(isSolid
                ? Color.FromArgb(225, color.R, color.G, color.B)
                : Color.FromArgb(StormThemeBrushes.IsDark ? (byte)35 : (byte)28, color.R, color.G, color.B)),
            BorderBrush = new SolidColorBrush(Color.FromArgb(isSolid ? (byte)255 : (byte)155, color.R, color.G, color.B)),
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(5),
            Padding = new Thickness(6, 1),
            Margin = new Thickness(0, 0, 4, 2),
            Child = new TextBlock
            {
                Text = text,
                FontSize = 9,
                FontWeight = FontWeight.Bold,
                Foreground = isSolid ? Brushes.White : new SolidColorBrush(textColor)
            }
        };
    }

    public static string FormatRomSize(uint bytes)
    {
        if (bytes >= 1024 * 1024) return $"{bytes / (1024.0 * 1024.0):0.1} MB";
        return $"{Math.Max(1, bytes / 1024.0):0} KB";
    }

    private void UpdateFavoriteVisual()
    {
        bool fav = _rom.IsFavorite;
        _starButton.Background = fav
            ? new SolidColorBrush(Color.Parse("#F59E0B"))
            : new SolidColorBrush(StormThemeBrushes.IsDark ? Color.FromArgb(140, 15, 23, 42) : Color.FromArgb(220, 255, 255, 255));
        _starButton.BorderBrush = fav
            ? new SolidColorBrush(Color.Parse("#F59E0B"))
            : StormThemeBrushes.SubtleBorder;
        _starIcon.CustomColorOverride = fav ? Brushes.White : StormThemeBrushes.PrimaryText;
    }

    public void UpdateSelectedState(bool selected)
    {
        _selectionBorder.BorderBrush = selected
            ? new SolidColorBrush(StormThemeBrushes.AccentPrimaryColor)
            : Brushes.Transparent;
    }

    private void UpdateSelectionVisual()
    {
        bool isSel = string.Equals(StormGbcaUiState.Instance.SelectedRom?.FilePath, _rom.FilePath, StringComparison.OrdinalIgnoreCase);
        UpdateSelectedState(isSel);
    }
}
