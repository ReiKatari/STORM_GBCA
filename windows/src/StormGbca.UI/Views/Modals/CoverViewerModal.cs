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
        Width = 680;
        Height = 620;
        Padding = new Thickness(20);
        SpotlightEnabled = true;

        var grid = new Grid { RowDefinitions = new RowDefinitions("Auto,*,Auto") };

        var header = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto"), Margin = new Thickness(0, 0, 0, 12) };
        var titleStack = new StackPanel { Spacing = 4 };
        titleStack.Children.Add(new TextBlock
        {
            Text = rom.GetDisplayTitle(),
            FontSize = 16,
            FontWeight = FontWeight.Bold,
            Foreground = StormThemeBrushes.PrimaryText,
            TextTrimming = TextTrimming.CharacterEllipsis
        });
        var badges = new WrapPanel { Orientation = Orientation.Horizontal };
        badges.Children.Add(BentoRomCard.CreateTagBadge(rom.PlatformShortName, StormGbcaUiState.GetPlatformBadgeColor(rom.Platform), true));
        badges.Children.Add(BentoRomCard.CreateTagBadge(rom.RegionBadgeCode, Color.Parse("#38BDF8"), false));
        BentoRomCard.AddHardwareBadges(badges, rom.HardwareFlags);
        titleStack.Children.Add(badges);
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
            CornerRadius = new CornerRadius(14),
            Background = new SolidColorBrush(Color.FromArgb(140, 8, 10, 18)),
            BorderBrush = StormThemeBrushes.StrongBorder,
            BorderThickness = new Thickness(1),
            Padding = new Thickness(16)
        };
        var img = new Image
        {
            Stretch = Stretch.Uniform,
            Source = BitmapHelper.LoadBitmapCached(rom.CoverLocalPath)
        };
        var statusLabel = new TextBlock
        {
            Text = img.Source == null ? "Загрузка обложки из Libretro Thumbnails..." : string.Empty,
            FontSize = 12,
            Foreground = StormThemeBrushes.SecondaryText,
            HorizontalAlignment = HorizontalAlignment.Center,
            VerticalAlignment = VerticalAlignment.Center,
            IsVisible = img.Source == null
        };
        var imgContainer = new Grid();
        imgContainer.Children.Add(img);
        imgContainer.Children.Add(statusLabel);
        imgHost.Child = imgContainer;
        grid.Children.Add(imgHost);
        Grid.SetRow(imgHost, 1);

        var footer = new StackPanel
        {
            Orientation = Orientation.Horizontal,
            HorizontalAlignment = HorizontalAlignment.Right,
            Spacing = 10,
            Margin = new Thickness(0, 12, 0, 0)
        };

        var fetchBtn = new AuroraVolumetricButton { Height = 34, Padding = new Thickness(14, 0) };
        fetchBtn.Content = new TextBlock { Text = "Обновить обложку", FontSize = 12, FontWeight = FontWeight.SemiBold, Foreground = Brushes.White, VerticalAlignment = VerticalAlignment.Center };
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

        var playBtn = new AuroraVolumetricButton { IsHero = true, Height = 34, Padding = new Thickness(16, 0) };
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

        if (img.Source == null)
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
