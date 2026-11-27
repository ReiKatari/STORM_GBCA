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
using StormGbca.UI.Common;
using StormGbca.UI.Views.Bento;

namespace StormGbca.UI.Views.Modals;

/// <summary>
/// Fullscreen 3D Boxart Cover Viewer modal with download trigger if boxart is not cached yet.
/// </summary>
public class CoverViewerModal : AuroraGlassCard
{
    public event Action? CloseRequested;

    public CoverViewerModal(GbcaRomRecord rom)
    {
        MaxWidth = 680;
        MaxHeight = 620;
        Margin = new Thickness(24, 18);
        HorizontalAlignment = HorizontalAlignment.Stretch;
        VerticalAlignment = VerticalAlignment.Center;
        Padding = new Thickness(20);
        SpotlightEnabled = true;

        var grid = new Grid { RowDefinitions = new RowDefinitions("Auto,*,Auto") };

        var header = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto"), Margin = new Thickness(0, 0, 0, 12) };
        var titleStack = new StackPanel { Spacing = 4 };
        var titleText = new TextBlock
        {
            Text = rom.GetDisplayTitle(),
            FontSize = 16,
            FontWeight = FontWeight.Bold,
            Foreground = StormThemeBrushes.PrimaryText,
            TextTrimming = TextTrimming.CharacterEllipsis
        };
        titleStack.Children.Add(titleText);

        var settings = StormGbcaUiState.Instance.Settings;
        var badges = new WrapPanel { Orientation = Orientation.Horizontal };
        if (settings.ShowPlatformBadge)
        {
            badges.Children.Add(BentoRomCard.CreateTagBadge(rom.PlatformShortName, StormGbcaUiState.GetPlatformBadgeColor(rom.Platform), true));
        }
        if (settings.ShowRegionBadge)
        {
            badges.Children.Add(BentoRomCard.CreateTagBadge(rom.RegionBadgeCode, Color.Parse("#38BDF8"), false));
        }
        if (settings.ShowHardwareBadges)
        {
            BentoRomCard.AddHardwareBadges(badges, rom.HardwareFlags);
        }
        if (badges.Children.Count > 0) titleStack.Children.Add(badges);
        header.Children.Add(titleStack);

        var closeBtn = new Button
        {
            Width = 32,
            Height = 32,
            Background = Brushes.Transparent,
            BorderThickness = new Thickness(0),
            Cursor = new Cursor(StandardCursorType.Hand),
            Content = new AuroraVectorIcon { Kind = AuroraIconKind.WindowClose, Size = 14 }
        };
        closeBtn.Click += (_, _) => CloseRequested?.Invoke();
        header.Children.Add(closeBtn);
        Grid.SetColumn(closeBtn, 1);
        grid.Children.Add(header);

        var imgHost = new Border
        {
            MinHeight = 200,
            CornerRadius = new CornerRadius(14),
            Background = StormThemeBrushes.RowSurface,
            BorderBrush = StormThemeBrushes.StrongBorder,
            BorderThickness = new Thickness(1),
            Padding = new Thickness(16)
        };
        var img = new Image
        {
            Stretch = Stretch.Uniform,
            Source = BitmapHelper.LoadBitmapCached(rom.CoverLocalPath)
        };
        bool allowAutoCover = settings.ShowLibretroCovers || settings.ShowRaCovers;
        var statusLabel = new TextBlock
        {
            Text = img.Source == null
                ? (allowAutoCover ? "Загрузка обложки из Libretro Thumbnails..." : "Автозагрузка обложек отключена. Нажмите «Загрузить обложку».")
                : string.Empty,
            FontSize = 12,
            Foreground = StormThemeBrushes.SecondaryText,
            HorizontalAlignment = HorizontalAlignment.Center,
            VerticalAlignment = VerticalAlignment.Center,
            TextAlignment = TextAlignment.Center,
            TextWrapping = TextWrapping.Wrap,
            IsVisible = img.Source == null
        };
        var imgContainer = new Grid();
        imgContainer.Children.Add(img);
        imgContainer.Children.Add(statusLabel);
        imgHost.Child = imgContainer;
        grid.Children.Add(imgHost);
        Grid.SetRow(imgHost, 1);

        var footer = new WrapPanel
        {
            Orientation = Orientation.Horizontal,
            HorizontalAlignment = HorizontalAlignment.Right,
            Margin = new Thickness(0, 12, 0, 0)
        };

        var fetchBtn = new AuroraVolumetricButton { Height = 34, Padding = new Thickness(14, 0), Margin = new Thickness(0, 0, 10, 4) };
        var fetchLabel = new TextBlock
        {
            Text = img.Source == null ? "Загрузить обложку" : "Обновить обложку",
            FontSize = 12,
            FontWeight = FontWeight.SemiBold,
            Foreground = StormThemeBrushes.PrimaryText,
            VerticalAlignment = VerticalAlignment.Center
        };
        fetchBtn.Content = fetchLabel;
        fetchBtn.Click += async (_, _) =>
        {
            statusLabel.Text = "Поиск обложки...";
            statusLabel.IsVisible = true;
            string? path = await StormGbcaUiState.Instance.Library.EnsureCoverArtAsync(rom);
            await Avalonia.Threading.Dispatcher.UIThread.InvokeAsync(() =>
            {
                img.Source = BitmapHelper.LoadBitmapCached(path);
                statusLabel.IsVisible = img.Source == null;
                if (img.Source == null) statusLabel.Text = "Обложка не найдена в базе Libretro Thumbnails.";
            });
        };
        footer.Children.Add(fetchBtn);

        var playBtn = new AuroraVolumetricButton { IsHero = true, Height = 34, Padding = new Thickness(16, 0), Margin = new Thickness(0, 0, 0, 4) };
        playBtn.Content = new TextBlock { Text = "Запустить игру", FontSize = 12, FontWeight = FontWeight.Bold, Foreground = Brushes.White, VerticalAlignment = VerticalAlignment.Center };
        playBtn.Click += (_, _) =>
        {
            CloseRequested?.Invoke();
            StormGbcaUiState.Instance.StartEmulation(rom);
        };
        footer.Children.Add(playBtn);

        grid.Children.Add(footer);
        Grid.SetRow(footer, 2);

        Content = grid;

        StormThemeBrushes.BindTheme(this, () =>
        {
            titleText.Foreground = StormThemeBrushes.PrimaryText;
            imgHost.Background = StormThemeBrushes.RowSurface;
            imgHost.BorderBrush = StormThemeBrushes.StrongBorder;
            statusLabel.Foreground = StormThemeBrushes.SecondaryText;
            fetchLabel.Foreground = StormThemeBrushes.PrimaryText;
        });

        if (img.Source == null && allowAutoCover)
        {
            _ = Task.Run(async () =>
            {
                string? path = await StormGbcaUiState.Instance.Library.EnsureCoverArtAsync(rom);
                await Avalonia.Threading.Dispatcher.UIThread.InvokeAsync(() =>
                {
                    img.Source = BitmapHelper.LoadBitmapCached(path);
                    statusLabel.IsVisible = img.Source == null;
                    if (img.Source == null) statusLabel.Text = "Обложка не найдена в базе Libretro Thumbnails.";
                });
            });
        }
    }
}
