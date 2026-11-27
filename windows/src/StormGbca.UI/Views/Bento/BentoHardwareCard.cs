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
    private readonly TextBlock _headerTitle;
    private readonly TextBlock _skinsBtnText;
    private readonly List<TextBlock> _statLabels = [];

    public BentoHardwareCard()
    {
        MinHeight = 168;
        Margin = new Thickness(0, 0, 0, 8);
        SpotlightEnabled = true;

        var stack = new StackPanel { Margin = new Thickness(14), Spacing = 7 };

        var headerRow = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8, VerticalAlignment = VerticalAlignment.Center };
        headerRow.Children.Add(new AuroraVectorIcon { Kind = AuroraIconKind.ShaderPrism, Size = 18, VerticalAlignment = VerticalAlignment.Center });
        _headerTitle = new TextBlock
        {
            Text = "АКТИВНЫЕ ЯДРА И КОРПУСЫ 3D",
            FontSize = 10.5,
            FontWeight = FontWeight.Bold,
            Foreground = StormThemeBrushes.Accent,
            VerticalAlignment = VerticalAlignment.Center
        };
        headerRow.Children.Add(_headerTitle);
        stack.Children.Add(headerRow);

        var verLine = new TextBlock
        {
            Text = NativeCoreBindings.IsAvailable
                ? $"storm_gbca_core {NativeCoreBindings.GetVersionString()}"
                : "Ядро не загружено",
            FontSize = 12.5,
            FontWeight = FontWeight.SemiBold,
            Foreground = StormThemeBrushes.PrimaryText,
            TextTrimming = TextTrimming.CharacterEllipsis
        };
        stack.Children.Add(verLine);

        var grid = new UniformGrid { Columns = 2, Rows = 2 };
        grid.Children.Add(CreateStat("Ядра:", out _coreVal, "#10B981"));
        grid.Children.Add(CreateStat("GB:", out _gbSkinVal, "#65A30D"));
        grid.Children.Add(CreateStat("GBC:", out _gbcSkinVal, "#7C3AED"));
        grid.Children.Add(CreateStat("GBA:", out _gbaSkinVal, "#4F46E5"));
        stack.Children.Add(grid);

        var skinsBtn = new AuroraVolumetricButton { Height = 32, HorizontalAlignment = HorizontalAlignment.Stretch, Padding = new Thickness(8, 2) };
        var bStack = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 6, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
        bStack.Children.Add(new AuroraVectorIcon { Kind = AuroraIconKind.ThemePalette, Size = 14, VerticalAlignment = VerticalAlignment.Center });
        _skinsBtnText = new TextBlock { Text = "Настроить 3D-корпусы и оверлеи", FontSize = 11.5, FontWeight = FontWeight.SemiBold, Foreground = StormThemeBrushes.PrimaryText, VerticalAlignment = VerticalAlignment.Center };
        bStack.Children.Add(_skinsBtnText);
        skinsBtn.Content = bStack;
        skinsBtn.Click += (_, _) => StormGbcaUiState.Instance.SetNavSection(NavSectionKind.SkinsAndOverlays);
        stack.Children.Add(skinsBtn);

        Content = stack;

        StormGbcaUiState.Instance.SkinOrOverlayChanged += RefreshStats;
        StormGbcaUiState.Instance.SettingsSaved += RefreshStats;
        StormThemeBrushes.BindTheme(this, () =>
        {
            _headerTitle.Foreground = StormThemeBrushes.Accent;
            verLine.Foreground = StormThemeBrushes.PrimaryText;
            _skinsBtnText.Foreground = StormThemeBrushes.PrimaryText;
            foreach (var lbl in _statLabels) lbl.Foreground = StormThemeBrushes.SecondaryText;
            RefreshStats();
        });
        RefreshStats();
    }

    private void RefreshStats()
    {
        var s = StormGbcaUiState.Instance.Settings;
        _coreVal.Text = $"{s.PreferredCoreGb}/{s.PreferredCoreGbc}";
        _gbSkinVal.Text = FormatSkinShortName(s.GbConsoleSkin.ToString());
        _gbcSkinVal.Text = FormatSkinShortName(s.GbcConsoleSkin.ToString());
        _gbaSkinVal.Text = FormatSkinShortName(s.GbaConsoleSkin.ToString());
    }

    private static string FormatSkinShortName(string raw) => raw switch
    {
        "DmgClassicGray" => "DMG-01 Classic",
        "PocketSilver" => "Pocket Silver",
        "PocketRed" => "Pocket Red",
        "LightGold" => "Light Gold",
        "AtomicPurple" => "Atomic Purple",
        "Teal" => "GBC Teal",
        "Berry" => "GBC Berry",
        "Kiwi" => "GBC Kiwi",
        "Dandelion" => "GBC Dandelion",
        "GbaIndigo" => "AGB-001 Indigo",
        "GbaGlacier" => "AGB Glacier",
        "GbaSpOnyx" => "SP Onyx",
        "GbaSpCobalt" => "SP Cobalt",
        "GbaSpFamicom" => "SP Famicom",
        "GameBoyMicro" => "Micro Famicom",
        _ => raw
    };

    private Grid CreateStat(string label, out TextBlock valBlock, string hexColor)
    {
        var row = new Grid
        {
            ColumnDefinitions = new ColumnDefinitions("Auto,*"),
            ColumnSpacing = 4,
            Margin = new Thickness(0, 2, 6, 2)
        };
        var lbl = new TextBlock { Text = label, FontSize = 10.5, Foreground = StormThemeBrushes.SecondaryText, VerticalAlignment = VerticalAlignment.Center };
        _statLabels.Add(lbl);
        row.Children.Add(lbl);

        valBlock = new TextBlock
        {
            Text = "-",
            FontSize = 10.5,
            FontWeight = FontWeight.SemiBold,
            Foreground = new SolidColorBrush(Color.Parse(hexColor)),
            TextTrimming = TextTrimming.CharacterEllipsis,
            VerticalAlignment = VerticalAlignment.Center
        };
        row.Children.Add(valBlock);
        Grid.SetColumn(valBlock, 1);
        return row;
    }
}
