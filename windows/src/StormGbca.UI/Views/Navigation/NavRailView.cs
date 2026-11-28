// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Mozilla Public License, Version 2.0 (MPL-2.0).

using Avalonia;
using Avalonia.Controls;
using Avalonia.Input;
using Avalonia.Layout;
using Avalonia.Media;
using Storm.AuroraFluid.Kit.Icons;
using Storm.AuroraFluid.Kit.Services;
using Storm.AuroraFluid.Kit.Themes;
using StormGbca.UI.Common;

namespace StormGbca.UI.Views.Navigation;

public class NavRailView : Border
{
    private readonly List<(NavSectionKind Kind, Border Container, TextBlock Label)> _items = [];

    public NavRailView()
    {
        Width = 82;
        HorizontalAlignment = HorizontalAlignment.Left;
        VerticalAlignment = VerticalAlignment.Stretch;
        BorderThickness = new Thickness(0, 0, 1, 0);

        var rootGrid = new Grid { RowDefinitions = new RowDefinitions("*,Auto") };
        var topStack = new StackPanel { Spacing = 6, Margin = new Thickness(0, 12, 0, 0) };

        AddItem(topStack, NavSectionKind.Library, AuroraIconKind.LibraryGrid, "Библиотека");
        AddItem(topStack, NavSectionKind.Favorites, AuroraIconKind.StarFavorite, "Избранное");
        AddItem(topStack, NavSectionKind.Recent, AuroraIconKind.RecentClock, "Недавние");
        AddItem(topStack, NavSectionKind.SkinsAndOverlays, AuroraIconKind.ShaderPrism, "Корпусы 3D");
        AddItem(topStack, NavSectionKind.Achievements, AuroraIconKind.TrophyCrown, "Достижения");

        rootGrid.Children.Add(topStack);

        var bottomStack = new StackPanel { Margin = new Thickness(0, 0, 0, 14) };
        AddItem(bottomStack, NavSectionKind.Settings, AuroraIconKind.SettingsCore, "Настройки");
        rootGrid.Children.Add(bottomStack);
        Grid.SetRow(bottomStack, 1);

        Child = rootGrid;

        StormGbcaUiState.Instance.NavSectionChanged += _ => UpdateActiveStyles();
        StormThemeBrushes.BindTheme(this, () =>
        {
            Background = StormThemeBrushes.GlassPanel;
            BorderBrush = StormThemeBrushes.SubtleBorder;
            UpdateActiveStyles();
        });
        UpdateActiveStyles();
    }

    private void AddItem(StackPanel panel, NavSectionKind kind, AuroraIconKind icon, string label)
    {
        var container = new Border
        {
            Width = 68,
            Height = 58,
            CornerRadius = new CornerRadius(12),
            Background = Brushes.Transparent,
            Cursor = new Cursor(StandardCursorType.Hand),
            HorizontalAlignment = HorizontalAlignment.Center
        };

        var stack = new StackPanel
        {
            HorizontalAlignment = HorizontalAlignment.Center,
            VerticalAlignment = VerticalAlignment.Center,
            Spacing = 3
        };
        stack.Children.Add(new AuroraVectorIcon { Kind = icon, Size = 22, HorizontalAlignment = HorizontalAlignment.Center });
        var tb = new TextBlock
        {
            Text = label,
            FontSize = 9.5,
            FontWeight = FontWeight.Medium,
            Foreground = StormThemeBrushes.SecondaryText,
            HorizontalAlignment = HorizontalAlignment.Center
        };
        stack.Children.Add(tb);
        container.Child = stack;

        container.PointerEntered += (_, _) =>
        {
            if (StormGbcaUiState.Instance.ActiveNavSection != kind)
            {
                container.Background = StormThemeBrushes.RowSurface;
                AuroraTactileFeedbackService.Instance.PlayHoverTick();
            }
        };
        container.PointerExited += (_, _) =>
        {
            if (StormGbcaUiState.Instance.ActiveNavSection != kind)
            {
                container.Background = Brushes.Transparent;
            }
        };
        container.PointerPressed += (_, _) =>
        {
            AuroraTactileFeedbackService.Instance.PlayClick();
            StormGbcaUiState.Instance.SetNavSection(kind);
        };

        panel.Children.Add(container);
        _items.Add((kind, container, tb));
    }

    private void UpdateActiveStyles()
    {
        var active = StormGbcaUiState.Instance.ActiveNavSection;
        var theme = AuroraThemeManager.Instance.CurrentTheme;
        var (primary, sec) = AuroraThemeManager.ResolveAccentColors(theme, AuroraThemeManager.Instance.CurrentAccentOverride);

        foreach (var item in _items)
        {
            bool isSel = item.Kind == active;
            if (isSel)
            {
                item.Container.Background = new LinearGradientBrush
                {
                    StartPoint = new RelativePoint(0, 0, RelativeUnit.Relative),
                    EndPoint = new RelativePoint(1, 1, RelativeUnit.Relative),
                    GradientStops =
                    {
                        new GradientStop(Color.FromArgb(70, primary.R, primary.G, primary.B), 0.0),
                        new GradientStop(Color.FromArgb(30, sec.R, sec.G, sec.B), 1.0)
                    }
                };
                item.Container.BorderBrush = new SolidColorBrush(Color.FromArgb(140, primary.R, primary.G, primary.B));
                item.Container.BorderThickness = new Thickness(1);
                item.Label.Foreground = StormThemeBrushes.PrimaryText;
            }
            else
            {
                item.Container.Background = Brushes.Transparent;
                item.Container.BorderBrush = Brushes.Transparent;
                item.Container.BorderThickness = new Thickness(0);
                item.Label.Foreground = StormThemeBrushes.SecondaryText;
            }
        }
    }
}

public class NavDockView : Border
{
    private readonly List<(NavSectionKind Kind, Border Container)> _items = [];

    public NavDockView()
    {
        HorizontalAlignment = HorizontalAlignment.Center;
        VerticalAlignment = VerticalAlignment.Bottom;
        Margin = new Thickness(0, 0, 0, 14);
        Padding = new Thickness(10, 6);
        CornerRadius = new CornerRadius(18);
        BorderThickness = new Thickness(1);

        var row = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8 };
        AddDockBtn(row, NavSectionKind.Library, AuroraIconKind.LibraryGrid, "Библиотека");
        AddDockBtn(row, NavSectionKind.Favorites, AuroraIconKind.StarFavorite, "Избранное");
        AddDockBtn(row, NavSectionKind.SkinsAndOverlays, AuroraIconKind.ShaderPrism, "Корпусы 3D");
        AddDockBtn(row, NavSectionKind.Achievements, AuroraIconKind.TrophyCrown, "Достижения");
        AddDockBtn(row, NavSectionKind.Settings, AuroraIconKind.SettingsCore, "Настройки");
        Child = row;

        StormGbcaUiState.Instance.NavSectionChanged += _ => UpdateActiveStyles();
        StormThemeBrushes.BindTheme(this, () =>
        {
            Background = StormThemeBrushes.GlassPanel;
            BorderBrush = StormThemeBrushes.StrongBorder;
            UpdateActiveStyles();
        });
        UpdateActiveStyles();
    }

    private void AddDockBtn(StackPanel row, NavSectionKind kind, AuroraIconKind icon, string tooltip)
    {
        var btn = new Border
        {
            Width = 42,
            Height = 42,
            CornerRadius = new CornerRadius(12),
            Background = Brushes.Transparent,
            Cursor = new Cursor(StandardCursorType.Hand),
            Child = new AuroraVectorIcon { Kind = icon, Size = 20, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center }
        };
        ToolTip.SetTip(btn, tooltip);
        btn.PointerEntered += (_, _) =>
        {
            if (StormGbcaUiState.Instance.ActiveNavSection != kind)
            {
                btn.Background = StormThemeBrushes.RowSurface;
                AuroraTactileFeedbackService.Instance.PlayHoverTick();
            }
        };
        btn.PointerExited += (_, _) =>
        {
            if (StormGbcaUiState.Instance.ActiveNavSection != kind)
            {
                btn.Background = Brushes.Transparent;
            }
        };
        btn.PointerPressed += (_, _) =>
        {
            AuroraTactileFeedbackService.Instance.PlayClick();
            StormGbcaUiState.Instance.SetNavSection(kind);
        };
        row.Children.Add(btn);
        _items.Add((kind, btn));
    }

    private void UpdateActiveStyles()
    {
        var active = StormGbcaUiState.Instance.ActiveNavSection;
        var theme = AuroraThemeManager.Instance.CurrentTheme;
        var (primary, sec) = AuroraThemeManager.ResolveAccentColors(theme, AuroraThemeManager.Instance.CurrentAccentOverride);

        foreach (var (kind, container) in _items)
        {
            if (kind == active)
            {
                container.Background = new LinearGradientBrush
                {
                    StartPoint = new RelativePoint(0, 0, RelativeUnit.Relative),
                    EndPoint = new RelativePoint(1, 1, RelativeUnit.Relative),
                    GradientStops =
                    {
                        new GradientStop(Color.FromArgb(70, primary.R, primary.G, primary.B), 0.0),
                        new GradientStop(Color.FromArgb(30, sec.R, sec.G, sec.B), 1.0)
                    }
                };
                container.BorderBrush = new SolidColorBrush(Color.FromArgb(140, primary.R, primary.G, primary.B));
                container.BorderThickness = new Thickness(1);
            }
            else
            {
                container.Background = Brushes.Transparent;
                container.BorderBrush = Brushes.Transparent;
                container.BorderThickness = new Thickness(0);
            }
        }
    }
}
