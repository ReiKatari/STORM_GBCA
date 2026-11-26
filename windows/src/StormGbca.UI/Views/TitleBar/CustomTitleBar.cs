// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Apache License, Version 2.0.

using Avalonia;
using Avalonia.Controls;
using Avalonia.Input;
using Avalonia.Layout;
using Avalonia.Media;
using Avalonia.Threading;
using Storm.AuroraFluid.Kit.Icons;
using Storm.AuroraFluid.Kit.Services;
using Storm.AuroraFluid.Kit.Themes;
using StormGbca.Core.Localization;
using StormGbca.UI.Common;

namespace StormGbca.UI.Views.TitleBar;

public class CustomTitleBar : Grid
{
    private readonly Window? _hostWindow;
    private readonly Border _corePill;
    private readonly TextBlock _coreText;
    private readonly TextBlock _fpsText;
    private readonly TextBlock _themeLabelText;
    private readonly TextBlock _clockText;
    private readonly DispatcherTimer _clockTimer;

    public CustomTitleBar(Window? hostWindow = null)
    {
        _hostWindow = hostWindow;
        Height = 42;
        Background = Brushes.Transparent;
        VerticalAlignment = VerticalAlignment.Top;

        ColumnDefinitions = new ColumnDefinitions("Auto,Auto,*,Auto,Auto");

        var titleStack = new StackPanel
        {
            Orientation = Orientation.Horizontal,
            VerticalAlignment = VerticalAlignment.Center,
            Margin = new Thickness(14, 0, 16, 0),
            Spacing = 6
        };

        titleStack.Children.Add(new AuroraVectorIcon
        {
            Kind = AuroraIconKind.RomCartridge,
            Size = 18,
            VerticalAlignment = VerticalAlignment.Center
        });

        var titleBrand = new TextBlock
        {
            Text = "STORM GBCA",
            FontSize = 13,
            FontWeight = FontWeight.Bold,
            Foreground = StormThemeBrushes.Accent,
            VerticalAlignment = VerticalAlignment.Center
        };

        var versionLabel = new TextBlock
        {
            Text = GbcaLoc.AppVersion,
            FontSize = 11,
            FontWeight = FontWeight.SemiBold,
            Foreground = StormThemeBrushes.SecondaryText,
            VerticalAlignment = VerticalAlignment.Center
        };

        titleStack.Children.Add(titleBrand);
        titleStack.Children.Add(versionLabel);
        SetColumn(titleStack, 0);
        Children.Add(titleStack);

        var badgesStack = new StackPanel
        {
            Orientation = Orientation.Horizontal,
            VerticalAlignment = VerticalAlignment.Center,
            Spacing = 8
        };

        _coreText = new TextBlock
        {
            Text = "mGBA + SameBoy",
            FontSize = 10.5,
            FontWeight = FontWeight.Medium,
            Foreground = new SolidColorBrush(Color.Parse("#E2E8F0")),
            VerticalAlignment = VerticalAlignment.Center
        };
        _corePill = CreateCustomPill(AuroraIconKind.ShaderPrism, _coreText, Color.Parse("#10B981"));
        badgesStack.Children.Add(_corePill);

        _fpsText = new TextBlock
        {
            Text = "Готов",
            FontSize = 11,
            FontWeight = FontWeight.SemiBold,
            Foreground = new SolidColorBrush(Color.Parse("#22D3EE")),
            VerticalAlignment = VerticalAlignment.Center
        };
        badgesStack.Children.Add(CreateCustomPill(AuroraIconKind.SpeedGauge, _fpsText, Color.Parse("#06B6D4")));

        _themeLabelText = new TextBlock
        {
            Text = AuroraThemeManager.Instance.CurrentTheme.DisplayName,
            FontSize = 10.5,
            FontWeight = FontWeight.Medium,
            Foreground = new SolidColorBrush(Color.Parse("#E2E8F0")),
            VerticalAlignment = VerticalAlignment.Center
        };
        var themePill = CreateCustomPill(AuroraIconKind.ThemePalette, _themeLabelText, Color.Parse("#A855F7"));
        themePill.Cursor = new Cursor(StandardCursorType.Hand);
        themePill.PointerPressed += (_, _) =>
        {
            AuroraTactileFeedbackService.Instance.PlayClick();
            CycleTheme();
        };
        badgesStack.Children.Add(themePill);

        SetColumn(badgesStack, 1);
        Children.Add(badgesStack);

        var dragArea = new Border
        {
            Background = Brushes.Transparent,
            HorizontalAlignment = HorizontalAlignment.Stretch,
            VerticalAlignment = VerticalAlignment.Stretch
        };
        dragArea.PointerPressed += (_, e) =>
        {
            if (e.GetCurrentPoint(this).Properties.IsLeftButtonPressed)
            {
                (_hostWindow ?? VisualRoot as Window)?.BeginMoveDrag(e);
            }
        };
        dragArea.DoubleTapped += (_, _) => ToggleMaximize();
        SetColumn(dragArea, 2);
        Children.Add(dragArea);

        _clockText = new TextBlock
        {
            Text = $"{DateTime.Now:dd.MM.yyyy HH:mm}",
            FontSize = 11,
            Foreground = StormThemeBrushes.SecondaryText,
            VerticalAlignment = VerticalAlignment.Center,
            Margin = new Thickness(0, 0, 16, 0)
        };
        SetColumn(_clockText, 3);
        Children.Add(_clockText);

        var winControls = new StackPanel
        {
            Orientation = Orientation.Horizontal,
            VerticalAlignment = VerticalAlignment.Top
        };
        winControls.Children.Add(CreateWindowControlButton(AuroraIconKind.WindowMinimize, () =>
        {
            var w = _hostWindow ?? VisualRoot as Window;
            if (w != null) w.WindowState = WindowState.Minimized;
        }, false));
        winControls.Children.Add(CreateWindowControlButton(AuroraIconKind.WindowMaximize, ToggleMaximize, false));
        winControls.Children.Add(CreateWindowControlButton(AuroraIconKind.WindowClose, () =>
        {
            (_hostWindow ?? VisualRoot as Window)?.Close();
        }, true));
        SetColumn(winControls, 4);
        Children.Add(winControls);

        _clockTimer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(500) };
        _clockTimer.Tick += (_, _) =>
        {
            _clockText.Text = $"{DateTime.Now:dd.MM.yyyy HH:mm}";
            if (StormGbcaUiState.Instance.IsEmulationActive)
            {
                var m = StormGbcaUiState.Instance.LastMetrics;
                _fpsText.Text = m.MeasuredFps > 0.1 ? $"{m.MeasuredFps:0.0} FPS" : "59.7 FPS";
                _coreText.Text = $"{m.ActiveCore} | {m.Width}x{m.Height}";
            }
            else
            {
                _fpsText.Text = "Готов";
                _coreText.Text = "mGBA + SameBoy";
            }
        };
        _clockTimer.Start();

        AuroraThemeManager.Instance.ThemeChanged += t =>
        {
            _themeLabelText.Text = t.DisplayName;
            _themeLabelText.Foreground = StormThemeBrushes.PrimaryText;
            titleBrand.Foreground = StormThemeBrushes.Accent;
            versionLabel.Foreground = StormThemeBrushes.SecondaryText;
            _clockText.Foreground = StormThemeBrushes.SecondaryText;
        };
    }

    protected override void OnPropertyChanged(AvaloniaPropertyChangedEventArgs change)
    {
        base.OnPropertyChanged(change);
        if (change.Property == BoundsProperty && Bounds.Width > 0)
        {
            _clockText.IsVisible = Bounds.Width >= 860;
            _corePill.IsVisible = Bounds.Width >= 780;
        }
    }

    private void ToggleMaximize()
    {
        var w = _hostWindow ?? VisualRoot as Window;
        if (w == null) return;
        w.WindowState = w.WindowState == WindowState.Maximized ? WindowState.Normal : WindowState.Maximized;
    }

    private static void CycleTheme()
    {
        string[] themes =
        [
            AuroraThemeManager.ThemeMidnight,
            AuroraThemeManager.ThemeNight,
            AuroraThemeManager.ThemeDark,
            AuroraThemeManager.ThemeDay,
            AuroraThemeManager.ThemeFantasy,
            AuroraThemeManager.ThemeCyberpunk,
            AuroraThemeManager.ThemeWarhammer40K,
            AuroraThemeManager.ThemeElemental,
            AuroraThemeManager.ThemeStorm
        ];
        int idx = Array.IndexOf(themes, AuroraThemeManager.Instance.CurrentTheme.Id);
        string next = themes[(idx + 1) % themes.Length];
        AuroraThemeManager.Instance.ApplyTheme(next);
        StormGbcaUiState.Instance.Settings.Theme = "STORM " + next;
        StormGbcaUiState.Instance.SaveSettings();
    }

    private static Border CreateCustomPill(AuroraIconKind icon, Control textControl, Color accent)
    {
        var border = new Border
        {
            Background = new SolidColorBrush(Color.FromArgb(35, accent.R, accent.G, accent.B)),
            BorderBrush = new SolidColorBrush(Color.FromArgb(90, accent.R, accent.G, accent.B)),
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(12),
            Padding = new Thickness(8, 3),
            VerticalAlignment = VerticalAlignment.Center
        };
        var stack = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 5, VerticalAlignment = VerticalAlignment.Center };
        stack.Children.Add(new AuroraVectorIcon { Kind = icon, Size = 13, VerticalAlignment = VerticalAlignment.Center });
        stack.Children.Add(textControl);
        border.Child = stack;
        return border;
    }

    private static Button CreateWindowControlButton(AuroraIconKind icon, Action action, bool isClose)
    {
        var btn = new Button
        {
            Width = 40,
            Height = 30,
            Padding = new Thickness(0),
            Background = Brushes.Transparent,
            BorderThickness = new Thickness(0),
            CornerRadius = new CornerRadius(0),
            Cursor = new Cursor(StandardCursorType.Hand),
            HorizontalContentAlignment = HorizontalAlignment.Center,
            VerticalContentAlignment = VerticalAlignment.Center,
            Content = new AuroraVectorIcon { Kind = icon, Size = 12 }
        };
        btn.PointerEntered += (_, _) => btn.Background = isClose
            ? new SolidColorBrush(Color.Parse("#EF4444"))
            : new SolidColorBrush(Color.FromArgb(40, 255, 255, 255));
        btn.PointerExited += (_, _) => btn.Background = Brushes.Transparent;
        btn.Click += (_, _) =>
        {
            AuroraTactileFeedbackService.Instance.PlayClick();
            action();
        };
        return btn;
    }
}
