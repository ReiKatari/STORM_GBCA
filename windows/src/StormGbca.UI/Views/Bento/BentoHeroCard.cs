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

public class BentoHeroCard : AuroraGlassCard
{
    private readonly Image _coverImage;
    private readonly Border _coverFrame;
    private readonly TextBlock _titleText;
    private readonly TextBlock _metaText;
    private readonly TextBlock _descText;
    private readonly WrapPanel _badgesRow;
    private readonly Border _favButton;
    private GbcaRomRecord? _currentRom;

    public BentoHeroCard()
    {
        Height = 180;
        Margin = new Thickness(0, 0, 8, 8);
        SpotlightEnabled = true;

        var grid = new Grid { ColumnDefinitions = new ColumnDefinitions("154,*,Auto") };

        _coverFrame = new Border
        {
            Width = 128,
            Height = 144,
            CornerRadius = new CornerRadius(12),
            Background = new SolidColorBrush(Color.FromArgb(50, 0, 0, 0)),
            BorderBrush = StormThemeBrushes.StrongBorder,
            BorderThickness = new Thickness(1),
            Margin = new Thickness(14),
            VerticalAlignment = VerticalAlignment.Center,
            HorizontalAlignment = HorizontalAlignment.Center,
            ClipToBounds = true,
            Cursor = new Cursor(StandardCursorType.Hand)
        };
        _coverImage = new Image { Stretch = Stretch.UniformToFill };
        _coverFrame.Child = _coverImage;
        _coverFrame.PointerPressed += (_, _) =>
        {
            if (_currentRom != null)
            {
                AuroraTactileFeedbackService.Instance.PlayClick();
                StormGbcaUiState.Instance.RequestCoverViewer(_currentRom);
            }
        };
        ToolTip.SetTip(_coverFrame, "Нажмите для просмотра обложки во весь экран");
        grid.Children.Add(_coverFrame);

        var infoStack = new StackPanel
        {
            VerticalAlignment = VerticalAlignment.Center,
            Margin = new Thickness(6, 0, 14, 0),
            Spacing = 5
        };

        _badgesRow = new WrapPanel { Orientation = Orientation.Horizontal };
        infoStack.Children.Add(_badgesRow);

        _titleText = new TextBlock
        {
            Text = "Выберите игру из библиотеки или перетащите ROM",
            FontSize = 17,
            FontWeight = FontWeight.Bold,
            Foreground = StormThemeBrushes.PrimaryText,
            TextTrimming = TextTrimming.CharacterEllipsis
        };
        infoStack.Children.Add(_titleText);

        _metaText = new TextBlock
        {
            Text = "GB / GBC / GBA / SGB | mGBA + SameBoy",
            FontSize = 11.5,
            Foreground = StormThemeBrushes.SecondaryText
        };
        infoStack.Children.Add(_metaText);

        _descText = new TextBlock
        {
            Text = "Поддерживаются картриджи Game Boy, Game Boy Color и Game Boy Advance, 3D-корпусы и мягкие патчи IPS/UPS/BPS.",
            FontSize = 11.5,
            Foreground = StormThemeBrushes.SecondaryText,
            TextWrapping = TextWrapping.Wrap,
            MaxLines = 2
        };
        infoStack.Children.Add(_descText);

        grid.Children.Add(infoStack);
        Grid.SetColumn(infoStack, 1);

        var actionStack = new StackPanel
        {
            VerticalAlignment = VerticalAlignment.Center,
            Margin = new Thickness(0, 0, 16, 0),
            Spacing = 8
        };

        var launchBtn = new AuroraVolumetricButton
        {
            IsHero = true,
            Height = 44,
            Width = 190,
            Padding = new Thickness(12, 6)
        };
        var btnContent = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
        btnContent.Children.Add(new AuroraVectorIcon { Kind = AuroraIconKind.PlayPrism, Size = 18, VerticalAlignment = VerticalAlignment.Center });
        btnContent.Children.Add(new TextBlock { Text = "Запустить игру", FontSize = 13.5, FontWeight = FontWeight.Bold, Foreground = Brushes.White, VerticalAlignment = VerticalAlignment.Center });
        launchBtn.Content = btnContent;
        launchBtn.Click += (_, _) =>
        {
            if (_currentRom != null) StormGbcaUiState.Instance.StartEmulation(_currentRom);
        };
        actionStack.Children.Add(launchBtn);

        var subRow = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 6, HorizontalAlignment = HorizontalAlignment.Center };
        var detailsBtn = new AuroraVolumetricButton { Height = 32, Width = 146, Padding = new Thickness(8, 2) };
        detailsBtn.Content = new TextBlock { Text = "Свойства игры", FontSize = 11.5, FontWeight = FontWeight.SemiBold, Foreground = Brushes.White, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
        detailsBtn.Click += (_, _) =>
        {
            if (_currentRom != null) StormGbcaUiState.Instance.RequestRomModal(_currentRom, 0);
        };
        subRow.Children.Add(detailsBtn);

        _favButton = new Border
        {
            Width = 36,
            Height = 32,
            CornerRadius = new CornerRadius(8),
            Background = new SolidColorBrush(Color.FromArgb(40, 255, 255, 255)),
            BorderBrush = new SolidColorBrush(Color.FromArgb(80, 255, 255, 255)),
            BorderThickness = new Thickness(1),
            Cursor = new Cursor(StandardCursorType.Hand),
            Child = new AuroraVectorIcon { Kind = AuroraIconKind.StarFavorite, Size = 15, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center }
        };
        _favButton.PointerPressed += (_, _) =>
        {
            if (_currentRom != null)
            {
                StormGbcaUiState.Instance.ToggleFavorite(_currentRom);
                UpdateFavoriteVisual();
            }
        };
        subRow.Children.Add(_favButton);
        actionStack.Children.Add(subRow);

        grid.Children.Add(actionStack);
        Grid.SetColumn(actionStack, 2);

        Content = grid;

        StormGbcaUiState.Instance.RomSelected += UpdateWithRom;
        StormGbcaUiState.Instance.Library.CoverArtUpdated += updated =>
        {
            if (_currentRom != null && string.Equals(updated.FilePath, _currentRom.FilePath, StringComparison.OrdinalIgnoreCase))
            {
                Avalonia.Threading.Dispatcher.UIThread.Post(() => UpdateWithRom(_currentRom));
            }
        };
        StormThemeBrushes.BindTheme(this, () =>
        {
            _titleText.Foreground = StormThemeBrushes.PrimaryText;
            _metaText.Foreground = StormThemeBrushes.SecondaryText;
            _descText.Foreground = StormThemeBrushes.SecondaryText;
        });

        UpdateWithRom(StormGbcaUiState.Instance.SelectedRom);
    }

    private void UpdateWithRom(GbcaRomRecord? rom)
    {
        _currentRom = rom;
        _badgesRow.Children.Clear();

        if (rom == null)
        {
            _badgesRow.Children.Add(BentoRomCard.CreateTagBadge("GB", StormGbcaUiState.GetPlatformBadgeColor(StormPlatform.Gb), true));
            _badgesRow.Children.Add(BentoRomCard.CreateTagBadge("GBC", StormGbcaUiState.GetPlatformBadgeColor(StormPlatform.Gbc), true));
            _badgesRow.Children.Add(BentoRomCard.CreateTagBadge("GBA", StormGbcaUiState.GetPlatformBadgeColor(StormPlatform.Gba), true));
            _titleText.Text = "STORM GBCA 1.4.0 - Готов к запуску";
            _metaText.Text = "mGBA + SameBoy | 59.73 FPS | 48 кГц Hermite";
            _descText.Text = "Перетащите любой .gb, .gbc, .gba, .sgb, .zip или .7z файл в окно, или выберите папку с играми.";
            _coverImage.Source = null;
            UpdateFavoriteVisual();
            return;
        }

        _badgesRow.Children.Add(BentoRomCard.CreateTagBadge(rom.PlatformShortName, StormGbcaUiState.GetPlatformBadgeColor(rom.Platform), true));
        _badgesRow.Children.Add(BentoRomCard.CreateTagBadge(rom.RegionBadgeCode, Color.Parse("#38BDF8"), false));
        BentoRomCard.AddHardwareBadges(_badgesRow, rom.HardwareFlags);

        _titleText.Text = rom.GetDisplayTitle();
        string codePart = string.IsNullOrWhiteSpace(rom.GameCode) ? "-" : rom.GameCode;
        _metaText.Text = $"Код: {codePart} | {BentoRomCard.FormatRomSize(rom.RomSize)} | CRC32: {rom.Crc32Hex}";
        _descText.Text = $"Запусков: {rom.PlayCount} | Нажмите на обложку для увеличения во весь экран.";
        _coverImage.Source = BitmapHelper.LoadBitmapCached(rom.CoverLocalPath);
        UpdateFavoriteVisual();
    }

    private void UpdateFavoriteVisual()
    {
        bool fav = _currentRom?.IsFavorite == true;
        _favButton.Background = fav
            ? new SolidColorBrush(Color.FromArgb(110, 245, 158, 11))
            : new SolidColorBrush(Color.FromArgb(40, 255, 255, 255));
    }
}
