// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Apache License, Version 2.0.

using Avalonia;
using Avalonia.Controls;
using Avalonia.Layout;
using Avalonia.Media;
using Storm.AuroraFluid.Kit.Icons;

namespace StormGbca.UI.Views.Modals;

public class DropImportOverlay : Border
{
    public DropImportOverlay()
    {
        Background = new SolidColorBrush(Color.FromArgb(200, 10, 15, 25));
        HorizontalAlignment = HorizontalAlignment.Stretch;
        VerticalAlignment = VerticalAlignment.Stretch;
        IsHitTestVisible = false;

        var centerBorder = new Border
        {
            Width = 440,
            Height = 220,
            CornerRadius = new CornerRadius(16),
            Background = new SolidColorBrush(Color.FromArgb(80, 79, 70, 229)),
            BorderBrush = new SolidColorBrush(Color.Parse("#818CF8")),
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
        stack.Children.Add(new TextBlock
        {
            Text = "ОТПУСТИТЕ ФАЙЛЫ ДЛЯ ЗАПУСКА И ИМПОРТА",
            FontSize = 13.5,
            FontWeight = FontWeight.Bold,
            Foreground = Brushes.White,
            HorizontalAlignment = HorizontalAlignment.Center
        });
        stack.Children.Add(new TextBlock
        {
            Text = "Поддерживаются .gb, .gbc, .gba, .sgb, .zip, .7z и папки с играми",
            FontSize = 11.5,
            Foreground = new SolidColorBrush(Color.Parse("#CBD5E1")),
            HorizontalAlignment = HorizontalAlignment.Center
        });

        centerBorder.Child = stack;
        Child = centerBorder;
    }
}
