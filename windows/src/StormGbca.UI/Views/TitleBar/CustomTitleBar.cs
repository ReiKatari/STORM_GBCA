// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Apache License, Version 2.0.

using Avalonia;
using Avalonia.Controls;
using Avalonia.Input;
using Avalonia.Layout;
using Avalonia.Media;
using Avalonia.Threading;
using Avalonia.VisualTree;
using Storm.AuroraFluid.Kit.Icons;
using Storm.AuroraFluid.Kit.Services;
using Storm.AuroraFluid.Kit.Themes;
using StormGbca.Core.Engine;
using StormGbca.Core.Library;
using StormGbca.Core.Localization;
using StormGbca.Core.Configuration;
using StormGbca.UI.Common;

namespace StormGbca.UI.Views.TitleBar;

public class CustomTitleBar : Grid
{
    private readonly Window? _hostWindow;
    private readonly Border _corePill;
    private readonly Border _fpsPill;
    private readonly Border _themePill;
    private readonly TextBlock _titleBrand;
    private readonly TextBlock _versionLabel;
    private readonly TextBlock _coreText;
    private readonly TextBlock _fpsText;
    private readonly TextBlock _themeLabelText;
    private readonly Button _minBtn;
    private readonly Button _maxBtn;
    private readonly Button _closeBtn;
    private readonly AuroraVectorIcon _minIcon;
    private readonly AuroraVectorIcon _maxIcon;
    private readonly AuroraVectorIcon _closeIcon;
    private bool _isCloseHovered;
    private long _lastFpsPostTickMs;

    public CustomTitleBar(Window? hostWindow = null)
    {
        _hostWindow = hostWindow;
        Height = 42;
        Background = Brushes.Transparent;
        VerticalAlignment = VerticalAlignment.Top;

        ColumnDefinitions = new ColumnDefinitions("Auto,Auto,*,Auto");

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

        _titleBrand = new TextBlock
        {
            Text = "STORM GBCA",
            FontSize = 13,
            FontWeight = FontWeight.Bold,
            Foreground = StormThemeBrushes.Accent,
            VerticalAlignment = VerticalAlignment.Center
        };

        _versionLabel = new TextBlock
        {
            Text = GbcaLoc.AppVersion,
            FontSize = 11,
            FontWeight = FontWeight.SemiBold,
            Foreground = StormThemeBrushes.SecondaryText,
            VerticalAlignment = VerticalAlignment.Center
        };

        titleStack.Children.Add(_titleBrand);
        titleStack.Children.Add(_versionLabel);
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
            VerticalAlignment = VerticalAlignment.Center
        };
        _corePill = CreateCustomPill(AuroraIconKind.ShaderPrism, _coreText, Color.Parse("#10B981"));
        badgesStack.Children.Add(_corePill);

        _fpsText = new TextBlock
        {
            Text = "Готов",
            FontSize = 11,
            FontWeight = FontWeight.SemiBold,
            VerticalAlignment = VerticalAlignment.Center
        };
        _fpsPill = CreateCustomPill(AuroraIconKind.SpeedGauge, _fpsText, Color.Parse("#06B6D4"));
        badgesStack.Children.Add(_fpsPill);

        _themeLabelText = new TextBlock
        {
            Text = AuroraThemeManager.Instance.CurrentTheme.DisplayName,
            FontSize = 10.5,
            FontWeight = FontWeight.Medium,
            VerticalAlignment = VerticalAlignment.Center
        };
        _themePill = CreateCustomPill(AuroraIconKind.ThemePalette, _themeLabelText, Color.Parse("#A855F7"));
        _themePill.Cursor = new Cursor(StandardCursorType.Hand);
        _themePill.PointerPressed += (_, e) =>
        {
            e.Handled = true;
            AuroraTactileFeedbackService.Instance.PlayClick();
            CycleTheme();
        };
        badgesStack.Children.Add(_themePill);

        SetColumn(badgesStack, 1);
        Children.Add(badgesStack);

        var winControls = new StackPanel
        {
            Orientation = Orientation.Horizontal,
            VerticalAlignment = VerticalAlignment.Stretch
        };

        (_minBtn, _minIcon) = CreateWindowControlButton(AuroraIconKind.WindowMinimize, () =>
        {
            var w = _hostWindow ?? VisualRoot as Window;
            if (w != null) w.WindowState = WindowState.Minimized;
        }, false);
        (_maxBtn, _maxIcon) = CreateWindowControlButton(AuroraIconKind.WindowMaximize, ToggleMaximize, false);
        (_closeBtn, _closeIcon) = CreateWindowControlButton(AuroraIconKind.WindowClose, () =>
        {
            (_hostWindow ?? VisualRoot as Window)?.Close();
        }, true);

        _closeBtn.PointerEntered += (_, _) =>
        {
            _isCloseHovered = true;
            _closeIcon.CustomColorOverride = Brushes.White;
        };
        _closeBtn.PointerExited += (_, _) =>
        {
            _isCloseHovered = false;
            _closeIcon.CustomColorOverride = null;
        };

        winControls.Children.Add(_minBtn);
        winControls.Children.Add(_maxBtn);
        winControls.Children.Add(_closeBtn);
        SetColumn(winControls, 3);
        Children.Add(winControls);

        PointerPressed += OnTitleBarPointerPressed;
        DoubleTapped += OnTitleBarDoubleTapped;

        AttachedToVisualTree += (_, _) =>
        {
            AuroraThemeManager.Instance.ThemeChanged -= OnThemeChanged;
            AuroraThemeManager.Instance.ThemeChanged += OnThemeChanged;
            StormGbcaUiState.Instance.Emulator.FrameCompleted -= OnFrameCompleted;
            StormGbcaUiState.Instance.Emulator.FrameCompleted += OnFrameCompleted;
            StormGbcaUiState.Instance.EmulationStarted -= OnEmulationStarted;
            StormGbcaUiState.Instance.EmulationStarted += OnEmulationStarted;
            StormGbcaUiState.Instance.EmulationStopped -= OnEmulationStopped;
            StormGbcaUiState.Instance.EmulationStopped += OnEmulationStopped;
            ApplyTheme(AuroraThemeManager.Instance.CurrentTheme);
            RefreshEmulationStatus();
        };

        DetachedFromVisualTree += (_, _) =>
        {
            AuroraThemeManager.Instance.ThemeChanged -= OnThemeChanged;
            StormGbcaUiState.Instance.Emulator.FrameCompleted -= OnFrameCompleted;
            StormGbcaUiState.Instance.EmulationStarted -= OnEmulationStarted;
            StormGbcaUiState.Instance.EmulationStopped -= OnEmulationStopped;
        };

        ApplyTheme(AuroraThemeManager.Instance.CurrentTheme);
        RefreshEmulationStatus();
    }

    public void UpdateMaximizeIcon(bool isMaximized)
    {
        _maxIcon.Kind = isMaximized ? AuroraIconKind.WindowRestore : AuroraIconKind.WindowMaximize;
    }

    private void OnTitleBarPointerPressed(object? sender, PointerPressedEventArgs e)
    {
        if (!e.GetCurrentPoint(this).Properties.IsLeftButtonPressed) return;
        if (IsInteractiveSource(e.Source as Visual)) return;

        if (e.ClickCount >= 2)
        {
            ToggleMaximize();
            e.Handled = true;
        }
        else
        {
            (_hostWindow ?? VisualRoot as Window)?.BeginMoveDrag(e);
        }
    }

    private void OnTitleBarDoubleTapped(object? sender, TappedEventArgs e)
    {
        if (IsInteractiveSource(e.Source as Visual)) return;
        ToggleMaximize();
        e.Handled = true;
    }

    private bool IsInteractiveSource(Visual? source)
    {
        return IsDescendantOrSelf(source, _themePill)
            || IsDescendantOrSelf(source, _minBtn)
            || IsDescendantOrSelf(source, _maxBtn)
            || IsDescendantOrSelf(source, _closeBtn);
    }

    private static bool IsDescendantOrSelf(Visual? node, Visual target)
    {
        while (node != null)
        {
            if (ReferenceEquals(node, target)) return true;
            node = node.GetVisualParent();
        }
        return false;
    }

    private void OnThemeChanged(AuroraThemeDefinition theme)
    {
        Dispatcher.UIThread.Post(() => ApplyTheme(theme));
    }

    private void ApplyTheme(AuroraThemeDefinition theme)
    {
        _themeLabelText.Text = theme.DisplayName;
        _themeLabelText.Foreground = StormThemeBrushes.PrimaryText;
        _coreText.Foreground = StormThemeBrushes.PrimaryText;
        _fpsText.Foreground = theme.IsDark
            ? new SolidColorBrush(Color.Parse("#22D3EE"))
            : new SolidColorBrush(Color.Parse("#0369A1"));
        _titleBrand.Foreground = StormThemeBrushes.Accent;
        _versionLabel.Foreground = StormThemeBrushes.SecondaryText;

        UpdatePillChrome(_corePill, Color.Parse("#10B981"), theme.IsDark);
        UpdatePillChrome(_fpsPill, Color.Parse("#06B6D4"), theme.IsDark);
        UpdatePillChrome(_themePill, Color.Parse("#A855F7"), theme.IsDark);

        var captionHover = new SolidColorBrush(theme.IsDark ? Color.Parse("#22FFFFFF") : Color.Parse("#16000000"));
        var captionPressed = new SolidColorBrush(theme.IsDark ? Color.Parse("#33FFFFFF") : Color.Parse("#24000000"));
        ApplyCaptionButtonBrushes(_minBtn, captionHover, captionPressed);
        ApplyCaptionButtonBrushes(_maxBtn, captionHover, captionPressed);
        ApplyCaptionButtonBrushes(_closeBtn, new SolidColorBrush(Color.Parse("#E81123")), new SolidColorBrush(Color.Parse("#C40E1D")));

        _minIcon.CustomColorOverride = null;
        _maxIcon.CustomColorOverride = null;
        _closeIcon.CustomColorOverride = _isCloseHovered ? Brushes.White : null;
    }

    private static void UpdatePillChrome(Border pill, Color accent, bool isDark)
    {
        pill.Background = new SolidColorBrush(Color.FromArgb(isDark ? (byte)35 : (byte)24, accent.R, accent.G, accent.B));
        pill.BorderBrush = new SolidColorBrush(Color.FromArgb(isDark ? (byte)90 : (byte)115, accent.R, accent.G, accent.B));
    }

    private static void ApplyCaptionButtonBrushes(Button btn, IBrush hover, IBrush pressed)
    {
        btn.Background = Brushes.Transparent;
        btn.Resources["ButtonBackground"] = Brushes.Transparent;
        btn.Resources["ButtonBackgroundPointerOver"] = hover;
        btn.Resources["ButtonBackgroundPressed"] = pressed;
    }

    private void OnEmulationStarted(GbcaRomRecord _)
    {
        Dispatcher.UIThread.Post(RefreshEmulationStatus);
    }

    private void OnEmulationStopped()
    {
        Dispatcher.UIThread.Post(RefreshEmulationStatus);
    }

    private void OnFrameCompleted(GbcaFrameMetrics metrics)
    {
        long now = Environment.TickCount64;
        long prev = Interlocked.Read(ref _lastFpsPostTickMs);
        if (now - prev < 240) return;
        if (Interlocked.CompareExchange(ref _lastFpsPostTickMs, now, prev) != prev) return;

        Dispatcher.UIThread.Post(() =>
        {
            if (!StormGbcaUiState.Instance.IsEmulationActive) return;
            _fpsText.Text = metrics.MeasuredFps > 0.1 ? $"{metrics.MeasuredFps:0.0} FPS" : "59.7 FPS";
            _coreText.Text = $"{metrics.ActiveCore} | {metrics.Width}x{metrics.Height}";
        });
    }

    private void RefreshEmulationStatus()
    {
        if (StormGbcaUiState.Instance.IsEmulationActive)
        {
            var m = StormGbcaUiState.Instance.LastMetrics;
            _fpsText.Text = m.MeasuredFps > 0.1 ? $"{m.MeasuredFps:0.0} FPS" : "59.7 FPS";
            _coreText.Text = string.IsNullOrWhiteSpace(m.ActiveCore) ? "mGBA + SameBoy" : $"{m.ActiveCore} | {m.Width}x{m.Height}";
        }
        else
        {
            _fpsText.Text = "Готов";
            _coreText.Text = "mGBA + SameBoy";
        }
    }

    protected override void OnPropertyChanged(AvaloniaPropertyChangedEventArgs change)
    {
        base.OnPropertyChanged(change);
        if (change.Property == BoundsProperty && Bounds.Width > 0)
        {
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

    private static (Button Button, AuroraVectorIcon Icon) CreateWindowControlButton(AuroraIconKind iconKind, Action action, bool isClose)
    {
        var icon = new AuroraVectorIcon { Kind = iconKind, Size = 12 };
        var btn = new Button
        {
            Width = 46,
            Height = 42,
            VerticalAlignment = VerticalAlignment.Stretch,
            Padding = new Thickness(0),
            Background = Brushes.Transparent,
            BorderThickness = new Thickness(0),
            CornerRadius = new CornerRadius(0),
            Cursor = new Cursor(StandardCursorType.Hand),
            HorizontalContentAlignment = HorizontalAlignment.Center,
            VerticalContentAlignment = VerticalAlignment.Center,
            Content = icon
        };
        btn.PointerEntered += (_, _) =>
        {
            var isDark = AuroraThemeManager.Instance.CurrentTheme.IsDark;
            btn.Background = isClose
                ? new SolidColorBrush(Color.Parse("#E81123"))
                : new SolidColorBrush(isDark ? Color.Parse("#22FFFFFF") : Color.Parse("#16000000"));
        };
        btn.PointerExited += (_, _) => btn.Background = Brushes.Transparent;
        btn.Click += (_, _) =>
        {
            AuroraTactileFeedbackService.Instance.PlayClick();
            action();
        };
        return (btn, icon);
    }
}
