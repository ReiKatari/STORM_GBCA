// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Apache License, Version 2.0.

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
    private readonly Image _coverImage;
    private readonly Border _fallbackArt;
    private readonly TextBlock _titleText;
    private readonly TextBlock _sizeText;
    private readonly Border _starButton;
    private readonly Border _selectionBorder;

    public GbcaRomRecord Rom => _rom;

    public BentoRomCard(GbcaRomRecord rom)
    {
        _rom = rom;
        Width = 200;
        Height = 266;
        Margin = new Thickness(6);
        SpotlightEnabled = true;
        Cursor = new Cursor(StandardCursorType.Hand);

        var rootGrid = new Grid { RowDefinitions = new RowDefinitions("156,*,Auto") };

        // 1. 3D Boxart Frame with Spine Shading
        Color platColor = StormGbcaUiState.GetPlatformBadgeColor(rom.Platform);
        var box3D = new Border
        {
            Width = 148,
            Height = 140,
            CornerRadius = new CornerRadius(10),
            Margin = new Thickness(0, 10, 0, 0),
            HorizontalAlignment = HorizontalAlignment.Center,
            VerticalAlignment = VerticalAlignment.Center,
            ClipToBounds = true,
            Background = new LinearGradientBrush
            {
                StartPoint = new RelativePoint(0, 0, RelativeUnit.Relative),
                EndPoint = new RelativePoint(1, 1, RelativeUnit.Relative),
                GradientStops =
                {
                    new GradientStop(Color.FromArgb(95, platColor.R, platColor.G, platColor.B), 0.0),
                    new GradientStop(Color.FromArgb(180, 12, 16, 28), 1.0)
                }
            },
            BorderBrush = new SolidColorBrush(Color.FromArgb(140, platColor.R, platColor.G, platColor.B)),
            BorderThickness = new Thickness(1.2),
            BoxShadow = new BoxShadows(new BoxShadow
            {
                OffsetX = 4,
                OffsetY = 6,
                Blur = 12,
                Color = Color.FromArgb(140, 0, 0, 0)
            })
        };

        var artLayer = new Grid();

        // Fallback procedural 3D cartridge emblem when boxart is not cached yet
        _fallbackArt = new Border
        {
            HorizontalAlignment = HorizontalAlignment.Stretch,
            VerticalAlignment = VerticalAlignment.Stretch,
            Child = BuildProceduralBoxPlaceholder(rom, platColor)
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
                    new GradientStop(Color.FromArgb(145, 0, 0, 0), 0.0),
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

        // Top-left Platform Badge inside boxart
        var platPill = CreateTagBadge(rom.PlatformShortName, platColor, isSolid: true);
        platPill.HorizontalAlignment = HorizontalAlignment.Left;
        platPill.VerticalAlignment = VerticalAlignment.Top;
        platPill.Margin = new Thickness(8, 8, 0, 0);
        artLayer.Children.Add(platPill);

        // Top-right Favorite Star
        _starButton = new Border
        {
            Width = 26,
            Height = 26,
            CornerRadius = new CornerRadius(7),
            Background = new SolidColorBrush(Color.FromArgb(130, 0, 0, 0)),
            HorizontalAlignment = HorizontalAlignment.Right,
            VerticalAlignment = VerticalAlignment.Top,
            Margin = new Thickness(0, 7, 7, 0)
        };
        _starButton.Child = new AuroraVectorIcon
        {
            Kind = AuroraIconKind.StarFavorite,
            Size = 13,
            HorizontalAlignment = HorizontalAlignment.Center,
            VerticalAlignment = VerticalAlignment.Center
        };
        _starButton.PointerPressed += (_, e) =>
        {
            e.Handled = true;
            StormGbcaUiState.Instance.ToggleFavorite(_rom);
            UpdateFavoriteVisual();
        };
        artLayer.Children.Add(_starButton);

        box3D.Child = artLayer;
        rootGrid.Children.Add(box3D);

        // 2. Title & Hardware Badges
        var titleStack = new StackPanel
        {
            Margin = new Thickness(12, 6, 12, 0),
            Spacing = 5,
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

        var hwRow = new WrapPanel { Orientation = Orientation.Horizontal };
        hwRow.Children.Add(CreateTagBadge(rom.RegionBadgeCode, Color.Parse("#38BDF8"), isSolid: false));
        AddHardwareBadges(hwRow, rom.HardwareFlags);
        titleStack.Children.Add(hwRow);

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

        StormGbcaUiState.Instance.RomSelected += _ => UpdateSelectionVisual();
        StormGbcaUiState.Instance.Library.CoverArtUpdated += updated =>
        {
            if (string.Equals(updated.FilePath, _rom.FilePath, StringComparison.OrdinalIgnoreCase))
            {
                Avalonia.Threading.Dispatcher.UIThread.Post(LoadCoverIfPresent);
            }
        };

        StormThemeBrushes.BindTheme(this, () =>
        {
            _titleText.Foreground = StormThemeBrushes.PrimaryText;
            _sizeText.Foreground = StormThemeBrushes.SecondaryText;
            UpdateSelectionVisual();
        });

        UpdateFavoriteVisual();
        UpdateSelectionVisual();
    }

    private void LoadCoverIfPresent()
    {
        var bmp = BitmapHelper.LoadBitmapCached(_rom.CoverLocalPath);
        _coverImage.Source = bmp;
        _fallbackArt.IsVisible = bmp == null;
    }

    private static StackPanel BuildProceduralBoxPlaceholder(GbcaRomRecord rom, Color platColor)
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
        s.Children.Add(new TextBlock
        {
            Text = string.IsNullOrWhiteSpace(rom.Title) ? rom.PlatformShortName : rom.Title,
            FontSize = 10,
            FontWeight = FontWeight.Bold,
            Foreground = new SolidColorBrush(Color.Parse("#E2E8F0")),
            TextAlignment = TextAlignment.Center,
            TextTrimming = TextTrimming.CharacterEllipsis,
            MaxWidth = 116
        });
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
        return new Border
        {
            Background = new SolidColorBrush(isSolid
                ? Color.FromArgb(220, color.R, color.G, color.B)
                : Color.FromArgb(35, color.R, color.G, color.B)),
            BorderBrush = new SolidColorBrush(Color.FromArgb(isSolid ? (byte)255 : (byte)135, color.R, color.G, color.B)),
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(5),
            Padding = new Thickness(6, 1),
            Margin = new Thickness(0, 0, 4, 2),
            Child = new TextBlock
            {
                Text = text,
                FontSize = 9,
                FontWeight = FontWeight.Bold,
                Foreground = isSolid ? Brushes.White : new SolidColorBrush(color)
            }
        };
    }

    public static string FormatRomSize(uint bytes)
    {
        if (bytes >= 1024 * 1024) return $"{bytes / (1024.0 * 1024.0):0.1} МБ";
        return $"{Math.Max(1, bytes / 1024.0):0} КБ";
    }

    private void UpdateFavoriteVisual()
    {
        _starButton.Background = _rom.IsFavorite
            ? new SolidColorBrush(Color.Parse("#F59E0B"))
            : new SolidColorBrush(Color.FromArgb(130, 0, 0, 0));
    }

    private void UpdateSelectionVisual()
    {
        bool isSel = string.Equals(StormGbcaUiState.Instance.SelectedRom?.FilePath, _rom.FilePath, StringComparison.OrdinalIgnoreCase);
        _selectionBorder.BorderBrush = isSel
            ? new SolidColorBrush(StormThemeBrushes.AccentPrimaryColor)
            : Brushes.Transparent;
    }
}
