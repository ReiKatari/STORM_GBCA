// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Apache License, Version 2.0.

using Avalonia;
using Avalonia.Controls;
using Avalonia.Controls.Primitives;
using Avalonia.Layout;
using Avalonia.Media;
using Storm.AuroraFluid.Kit.Controls;
using Storm.AuroraFluid.Kit.Icons;
using StormGbca.Core.Native;
using StormGbca.UI.Common;

namespace StormGbca.UI.Views.Bento;

public class BentoHardwareCard : AuroraGlassCard
{
    private readonly TextBlock _gbSkinVal;
    private readonly TextBlock _gbcSkinVal;
    private readonly TextBlock _gbaSkinVal;
    private readonly TextBlock _coreVal;

    public BentoHardwareCard()
    {
        Height = 180;
        Margin = new Thickness(0, 0, 0, 8);
        SpotlightEnabled = true;

        var stack = new StackPanel { Margin = new Thickness(14), Spacing = 7 };

        var headerRow = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8, VerticalAlignment = VerticalAlignment.Center };
        headerRow.Children.Add(new AuroraVectorIcon { Kind = AuroraIconKind.ShaderPrism, Size = 18, VerticalAlignment = VerticalAlignment.Center });
        headerRow.Children.Add(new TextBlock
        {
            Text = "АКТИВНЫЕ ЯДРА И КОРПУСЫ 3D",
            FontSize = 10.5,
            FontWeight = FontWeight.Bold,
            Foreground = new SolidColorBrush(Color.Parse("#8BAC0F")),
            VerticalAlignment = VerticalAlignment.Center
        });
        stack.Children.Add(headerRow);

        var verLine = new TextBlock
        {
            Text = NativeCoreBindings.IsAvailable
                ? $"storm_gbca_core {NativeCoreBindings.GetVersionString()}"
                : "Ядро не загружено",
            FontSize = 12.5,
            FontWeight = FontWeight.SemiBold,
            Foreground = StormThemeBrushes.PrimaryText
        };
        stack.Children.Add(verLine);

        var grid = new UniformGrid { Columns = 2, Rows = 2 };
        grid.Children.Add(CreateStat("Ядра:", out _coreVal, "#10B981"));
        grid.Children.Add(CreateStat("Корпус GB:", out _gbSkinVal, "#8BAC0F"));
        grid.Children.Add(CreateStat("Корпус GBC:", out _gbcSkinVal, "#7B42F6"));
        grid.Children.Add(CreateStat("Корпус GBA:", out _gbaSkinVal, "#4F46E5"));
        stack.Children.Add(grid);

        var skinsBtn = new AuroraVolumetricButton { Height = 32, HorizontalAlignment = HorizontalAlignment.Stretch, Padding = new Thickness(8, 2) };
        var bStack = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 6, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
        bStack.Children.Add(new AuroraVectorIcon { Kind = AuroraIconKind.ThemePalette, Size = 14, VerticalAlignment = VerticalAlignment.Center });
        bStack.Children.Add(new TextBlock { Text = "Настроить 3D-корпусы и оверлеи", FontSize = 11.5, FontWeight = FontWeight.SemiBold, Foreground = Brushes.White, VerticalAlignment = VerticalAlignment.Center });
        skinsBtn.Content = bStack;
        skinsBtn.Click += (_, _) => StormGbcaUiState.Instance.SetNavSection(NavSectionKind.SkinsAndOverlays);
        stack.Children.Add(skinsBtn);

        Content = stack;

        StormGbcaUiState.Instance.SkinOrOverlayChanged += RefreshStats;
        StormGbcaUiState.Instance.SettingsSaved += RefreshStats;
        StormThemeBrushes.BindTheme(this, () =>
        {
            verLine.Foreground = StormThemeBrushes.PrimaryText;
            RefreshStats();
        });
        RefreshStats();
    }

    private void RefreshStats()
    {
        var s = StormGbcaUiState.Instance.Settings;
        _coreVal.Text = $"{s.PreferredCoreGb}/{s.PreferredCoreGbc}";
        _gbSkinVal.Text = s.GbConsoleSkin.ToString();
        _gbcSkinVal.Text = s.GbcConsoleSkin.ToString();
        _gbaSkinVal.Text = s.GbaConsoleSkin.ToString();
    }

    private static StackPanel CreateStat(string label, out TextBlock valBlock, string hexColor)
    {
        var row = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 4, Margin = new Thickness(0, 2) };
        row.Children.Add(new TextBlock { Text = label, FontSize = 10.5, Foreground = StormThemeBrushes.SecondaryText });
        valBlock = new TextBlock
        {
            Text = "-",
            FontSize = 10.5,
            FontWeight = FontWeight.SemiBold,
            Foreground = new SolidColorBrush(Color.Parse(hexColor)),
            TextTrimming = TextTrimming.CharacterEllipsis
        };
        row.Children.Add(valBlock);
        return row;
    }
}
