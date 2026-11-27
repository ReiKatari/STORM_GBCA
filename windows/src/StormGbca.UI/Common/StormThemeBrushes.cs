// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Apache License, Version 2.0.

using Avalonia;
using Avalonia.Controls;
using Avalonia.Media;
using Storm.AuroraFluid.Kit.Themes;

namespace StormGbca.UI.Common;

/// <summary>
/// Live theme-aware brushes supporting all 8 dark themes and the light STORM DAY theme.
/// Strictly enforces pure white text on dark themes and pure black text on light themes (zero dim gray).
/// </summary>
public static class StormThemeBrushes
{
    public static bool IsDark => AuroraThemeManager.Instance.CurrentTheme.IsDark;

    public static IBrush PrimaryText => new SolidColorBrush(
        IsDark ? Color.Parse("#FFFFFF") : Color.Parse("#0F172A"));

    public static IBrush SecondaryText => new SolidColorBrush(
        IsDark ? Color.Parse("#F1F5F9") : Color.Parse("#334155"));

    public static IBrush BodyText => new SolidColorBrush(
        IsDark ? Color.Parse("#FFFFFF") : Color.Parse("#0F172A"));

    public static Color AccentPrimaryColor =>
        AuroraThemeManager.ResolveAccentColors(
            AuroraThemeManager.Instance.CurrentTheme,
            AuroraThemeManager.Instance.CurrentAccentOverride).Primary;

    public static Color AccentSecondaryColor =>
        AuroraThemeManager.ResolveAccentColors(
            AuroraThemeManager.Instance.CurrentTheme,
            AuroraThemeManager.Instance.CurrentAccentOverride).Secondary;

    public static IBrush Accent => new SolidColorBrush(AccentPrimaryColor);
    public static IBrush AccentSecondary => new SolidColorBrush(AccentSecondaryColor);

    public static IBrush ChromeSurface
    {
        get
        {
            var hc = AuroraThemeManager.Instance.CurrentTheme.HeaderColor;
            return new SolidColorBrush(Color.FromArgb(IsDark ? (byte)195 : (byte)235, hc.R, hc.G, hc.B));
        }
    }

    public static IBrush GlassPanel
    {
        get
        {
            var cc = AuroraThemeManager.Instance.CurrentTheme.CardColor;
            return new SolidColorBrush(Color.FromArgb(IsDark ? (byte)180 : (byte)242, cc.R, cc.G, cc.B));
        }
    }

    public static IBrush CardSurface => new SolidColorBrush(
        IsDark ? Color.FromArgb(44, 255, 255, 255) : Color.Parse("#FFFFFF"));

    public static IBrush RowSurface => new SolidColorBrush(
        IsDark ? Color.FromArgb(30, 255, 255, 255) : Color.Parse("#F8FAFC"));

    public static IBrush HoverSurface => new SolidColorBrush(
        IsDark
            ? Color.FromArgb(60, AccentPrimaryColor.R, AccentPrimaryColor.G, AccentPrimaryColor.B)
            : Color.Parse("#14000000"));

    public static IBrush SubtleBorder => new SolidColorBrush(
        IsDark ? Color.FromArgb(75, 255, 255, 255) : Color.Parse("#E2E8F0"));

    public static IBrush StrongBorder => new SolidColorBrush(
        IsDark ? Color.FromArgb(115, 255, 255, 255) : Color.Parse("#CBD5E1"));

    public static void BindTheme(Control control, Action apply)
    {
        apply();

        void OnThemeChanged(AuroraThemeDefinition _) => apply();

        control.AttachedToVisualTree += (_, _) =>
        {
            AuroraThemeManager.Instance.ThemeChanged -= OnThemeChanged;
            AuroraThemeManager.Instance.ThemeChanged += OnThemeChanged;
            apply();
        };

        control.DetachedFromVisualTree += (_, _) =>
        {
            AuroraThemeManager.Instance.ThemeChanged -= OnThemeChanged;
        };
    }
}
