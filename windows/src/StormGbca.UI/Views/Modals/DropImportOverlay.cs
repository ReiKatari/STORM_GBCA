// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Apache License, Version 2.0.

using Avalonia;
using Avalonia.Controls;
using Avalonia.Layout;
using Avalonia.Media;
using Storm.AuroraFluid.Kit.Icons;
using StormGbca.UI.Common;

namespace StormGbca.UI.Views.Modals;

public class DropImportOverlay : Border
{
    public DropImportOverlay()
    {
        HorizontalAlignment = HorizontalAlignment.Stretch;
        VerticalAlignment = VerticalAlignment.Stretch;
        Padding = new Thickness(24);
        IsHitTestVisible = false;

        var centerBorder = new Border
        {
            MaxWidth = 460,
            MinHeight = 200,
            Padding = new Thickness(24),
            CornerRadius = new CornerRadius(16),
            BorderThickness = new Thickness(2),
            HorizontalAlignment = HorizontalAlignment.Center,
            VerticalAlignment = VerticalAlignment.Center
        };

        var stack = new StackPanel
        {
            HorizontalAlignment = HorizontalAlignment.Center,
            VerticalAlignment = VerticalAlignment.Center,
            Spacing = 10
        };

        stack.Children.Add(new AuroraVectorIcon { Kind = AuroraIconKind.ImportBox, Size = 48, HorizontalAlignment = HorizontalAlignment.Center });
        var titleText = new TextBlock
        {
            Text = "ОТПУСТИТЕ ФАЙЛЫ ДЛЯ ЗАПУСКА И ИМПОРТА",
            FontSize = 13.5,
            FontWeight = FontWeight.Bold,
            TextWrapping = TextWrapping.Wrap,
            TextAlignment = TextAlignment.Center,
            HorizontalAlignment = HorizontalAlignment.Center
        };
        var subText = new TextBlock
        {
            Text = "Поддерживаются .gb, .gbc, .gba, .sgb, .zip, .7z и папки с играми",
            FontSize = 11.5,
            TextWrapping = TextWrapping.Wrap,
            TextAlignment = TextAlignment.Center,
            HorizontalAlignment = HorizontalAlignment.Center
        };
        stack.Children.Add(titleText);
        stack.Children.Add(subText);

        centerBorder.Child = stack;
        Child = centerBorder;

        StormThemeBrushes.BindTheme(this, () =>
        {
            Background = new SolidColorBrush(StormThemeBrushes.IsDark
                ? Color.FromArgb(200, 10, 15, 25)
                : Color.FromArgb(190, 226, 232, 240));
            centerBorder.Background = StormThemeBrushes.GlassPanel;
            centerBorder.BorderBrush = StormThemeBrushes.Accent;
            titleText.Foreground = StormThemeBrushes.PrimaryText;
            subText.Foreground = StormThemeBrushes.SecondaryText;
        });
    }
}
