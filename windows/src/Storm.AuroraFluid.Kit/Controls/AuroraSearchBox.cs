// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Mozilla Public License, Version 2.0 (MPL-2.0).

using System;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Input;
using Avalonia.Layout;
using Avalonia.Media;
using Storm.AuroraFluid.Kit.Icons;
using Storm.AuroraFluid.Kit.Themes;

namespace Storm.AuroraFluid.Kit.Controls;

/// <summary>
/// High-tech search box with theme-aware styling, integrated left search icon,
/// right hotkey hint pill (Ctrl+K), and instant Escape clear capability.
/// </summary>
public class AuroraSearchBox : TextBox
{
    protected override Type StyleKeyOverride => typeof(TextBox);

    public static readonly StyledProperty<string> HotkeyHintProperty =
        AvaloniaProperty.Register<AuroraSearchBox, string>(nameof(HotkeyHint), "Ctrl+K");

    public string HotkeyHint
    {
        get => GetValue(HotkeyHintProperty);
        set => SetValue(HotkeyHintProperty, value);
    }

    private readonly Action<AuroraThemeDefinition> _themeChangedHandler;
    private readonly AuroraVectorIcon _searchIcon;
    private readonly Border _hotkeyPill;
    private readonly TextBlock _hotkeyText;

    public AuroraSearchBox()
    {
        _themeChangedHandler = _ => ApplyThemeColors();

        PlaceholderText = "Поиск по библиотеке...";
        CornerRadius = new CornerRadius(8);
        Padding = new Thickness(6, 7, 8, 7);
        VerticalContentAlignment = VerticalAlignment.Center;

        _searchIcon = new AuroraVectorIcon
        {
            Kind = AuroraIconKind.SearchSpotlight,
            Size = 14,
            Margin = new Thickness(10, 0, 2, 0),
            VerticalAlignment = VerticalAlignment.Center,
            IsHitTestVisible = false
        };
        InnerLeftContent = _searchIcon;

        _hotkeyText = new TextBlock
        {
            Text = HotkeyHint,
            FontSize = 10,
            FontWeight = FontWeight.SemiBold,
            VerticalAlignment = VerticalAlignment.Center
        };
        _hotkeyPill = new Border
        {
            CornerRadius = new CornerRadius(5),
            Padding = new Thickness(6, 2),
            Margin = new Thickness(2, 0, 8, 0),
            BorderThickness = new Thickness(1),
            VerticalAlignment = VerticalAlignment.Center,
            IsHitTestVisible = false,
            Child = _hotkeyText
        };
        InnerRightContent = _hotkeyPill;

        PropertyChanged += (_, ch) =>
        {
            if (ch.Property == HotkeyHintProperty)
            {
                _hotkeyText.Text = HotkeyHint;
            }
            if (ch.Property == TextProperty || ch.Property == HotkeyHintProperty)
            {
                _hotkeyPill.IsVisible = string.IsNullOrEmpty(Text) && !string.IsNullOrEmpty(HotkeyHint);
            }
        };

        ApplyThemeColors();
    }

    protected override void OnAttachedToVisualTree(VisualTreeAttachmentEventArgs e)
    {
        base.OnAttachedToVisualTree(e);
        AuroraThemeManager.Instance.ThemeChanged -= _themeChangedHandler;
        AuroraThemeManager.Instance.ThemeChanged += _themeChangedHandler;
        ApplyThemeColors();
    }

    protected override void OnDetachedFromVisualTree(VisualTreeAttachmentEventArgs e)
    {
        AuroraThemeManager.Instance.ThemeChanged -= _themeChangedHandler;
        base.OnDetachedFromVisualTree(e);
    }

    private void ApplyThemeColors()
    {
        var theme = AuroraThemeManager.Instance.CurrentTheme;
        var (accentPrimary, _) = AuroraThemeManager.ResolveAccentColors(theme, AuroraThemeManager.Instance.CurrentAccentOverride);

        CaretBrush = new SolidColorBrush(theme.IsDark ? accentPrimary : Color.Parse("#0284C7"));
        Foreground = theme.TextPrimaryBrush;
        Background = new SolidColorBrush(theme.IsDark
            ? Color.FromArgb(200, theme.HeaderColor.R, theme.HeaderColor.G, theme.HeaderColor.B)
            : Color.Parse("#FFFFFF"));
        BorderBrush = new SolidColorBrush(theme.IsDark ? theme.BorderColor : Color.Parse("#CBD5E1"));

        var iconBrush = new SolidColorBrush(theme.IsDark ? theme.TextSecondaryColor : Color.Parse("#475569"));
        _searchIcon.CustomColorOverride = iconBrush;

        _hotkeyPill.Background = new SolidColorBrush(theme.IsDark ? Color.FromArgb(40, 255, 255, 255) : Color.Parse("#F1F5F9"));
        _hotkeyPill.BorderBrush = new SolidColorBrush(theme.IsDark ? theme.BorderColor : Color.Parse("#CBD5E1"));
        _hotkeyText.Foreground = new SolidColorBrush(theme.IsDark ? theme.TextSecondaryColor : Color.Parse("#334155"));
        _hotkeyPill.IsVisible = string.IsNullOrEmpty(Text) && !string.IsNullOrEmpty(HotkeyHint);
    }

    protected override void OnKeyDown(KeyEventArgs e)
    {
        base.OnKeyDown(e);
        if (e.Key == Key.Escape)
        {
            Text = string.Empty;
            e.Handled = true;
        }
    }
}
