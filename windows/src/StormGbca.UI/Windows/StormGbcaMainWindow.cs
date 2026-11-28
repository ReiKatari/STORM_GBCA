// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Mozilla Public License, Version 2.0 (MPL-2.0).

using Avalonia;
using Avalonia.Controls;
using Avalonia.Input;
using Avalonia.Interactivity;
using Avalonia.Platform;
using Storm.AuroraFluid.Kit.Controls;
using Storm.AuroraFluid.Kit.Platform;
using Storm.AuroraFluid.Kit.Themes;
using StormGbca.Core.Localization;
using StormGbca.UI.Common;
using StormGbca.UI.Views;
using StormGbca.UI.Views.TitleBar;

namespace StormGbca.UI.Windows;

public class StormGbcaMainWindow : Window
{
    private readonly AuroraScaledLayoutControl _scaledHost;
    private readonly StormGbcaMainView _mainView;
    private readonly CustomTitleBar _titleBar;
    private double _lastWorkWidth = 1920;
    private double _lastWorkHeight = 1040;
    private WindowState _preFullscreenState = WindowState.Normal;

    public StormGbcaMainWindow()
    {
        var cfg = StormGbcaUiState.Instance.Settings;
        Title = $"STORM GBCA {GbcaLoc.AppVersion}";
        MinWidth = 1020;
        MinHeight = 640;
        Width = Math.Clamp(cfg.WindowWidth > 0 ? cfg.WindowWidth : 1360, MinWidth, 3840);
        Height = Math.Clamp(cfg.WindowHeight > 0 ? cfg.WindowHeight : 840, MinHeight, 2160);
        WindowStartupLocation = WindowStartupLocation.CenterScreen;

        WindowDecorations = WindowDecorations.BorderOnly;
        ExtendClientAreaToDecorationsHint = true;
        ExtendClientAreaTitleBarHeightHint = 42;

        string iconPath = Path.Combine(AppContext.BaseDirectory, "assets", "AppIcon.ico");
        if (File.Exists(iconPath))
        {
            try { Icon = new WindowIcon(iconPath); } catch { }
        }

        UpdateThemeBackground();
        AuroraThemeManager.Instance.ThemeChanged += _ => UpdateThemeBackground();

        var rootShell = new Grid { RowDefinitions = new RowDefinitions("Auto,*") };
        _titleBar = new CustomTitleBar(this);
        rootShell.Children.Add(_titleBar);

        _mainView = new StormGbcaMainView(this, includeTitleBar: false);
        _scaledHost = new AuroraScaledLayoutControl { Child = _mainView };
        rootShell.Children.Add(_scaledHost);
        Grid.SetRow(_scaledHost, 1);
        Content = rootShell;

        StormGbcaUiState.Instance.UiScaleChanged += ApplyUiScale;
        StormGbcaUiState.Instance.FullscreenToggled += ToggleFullscreen;
        StormGbcaUiState.Instance.FullscreenChanged += fs => _titleBar.IsVisible = !fs;

        PropertyChanged += (_, change) =>
        {
            if (change.Property == WindowStateProperty)
            {
                UpdateWindowStateChrome();
            }
        };

        AddHandler(KeyDownEvent, OnWindowTunnelKeyDown, RoutingStrategies.Tunnel);

        Opened += OnWindowOpened;
        Closing += OnWindowClosing;
        KeyDown += OnWindowKeyDown;
    }

    private void UpdateWindowStateChrome()
    {
        bool isMaximized = WindowState == WindowState.Maximized;
        Padding = isMaximized ? OffScreenMargin : new Thickness(0);
        _titleBar.UpdateMaximizeIcon(isMaximized);
    }

    private void OnWindowOpened(object? sender, EventArgs e)
    {
        nint hwnd = TryGetPlatformHandle()?.Handle ?? IntPtr.Zero;
        if (hwnd != IntPtr.Zero)
        {
            AuroraWin32Interop.ApplyDwmDarkMode(hwnd, AuroraThemeManager.Instance.CurrentTheme.IsDark, enableMica: true);
            AuroraWin32Interop.EnableElevatedDragDropBypass(hwnd);
        }

        var screen = Screens.ScreenFromWindow(this) ?? Screens.Primary;
        if (screen != null)
        {
            double dpiScale = Math.Max(1.0, screen.Scaling);
            _lastWorkWidth = screen.WorkingArea.Width / dpiScale;
            _lastWorkHeight = screen.WorkingArea.Height / dpiScale;

            if (StormGbcaUiState.Instance.Settings.WindowMaximized)
            {
                WindowState = WindowState.Maximized;
            }
            else
            {
                double maxW = Math.Max(MinWidth, _lastWorkWidth);
                double maxH = Math.Max(MinHeight, _lastWorkHeight);
                if (Width > maxW) Width = maxW;
                if (Height > maxH) Height = maxH;
            }
        }
        UpdateWindowStateChrome();
        ApplyUiScale();
    }

    private void OnWindowClosing(object? sender, WindowClosingEventArgs e)
    {
        var cfg = StormGbcaUiState.Instance.Settings;
        if (WindowState != WindowState.FullScreen)
        {
            cfg.WindowMaximized = WindowState == WindowState.Maximized;
        }
        if (WindowState == WindowState.Normal && Bounds.Width >= MinWidth && Bounds.Height >= MinHeight)
        {
            cfg.WindowWidth = Math.Round(Bounds.Width);
            cfg.WindowHeight = Math.Round(Bounds.Height);
        }
        StormGbcaUiState.Instance.SaveSettings();

        if (AuroraTrayManager.Instance.IsExitRequestedFromTray)
        {
            StormGbcaUiState.Instance.StopEmulation();
            StormGbcaUiState.Instance.Emulator.Dispose();
        }
    }

    private void OnWindowTunnelKeyDown(object? sender, KeyEventArgs e)
    {
        if (e.Key == Key.Escape && _mainView.TryDismissOpenModal())
        {
            e.Handled = true;
        }
    }

    private void ApplyUiScale()
    {
        double configured = StormGbcaUiState.Instance.Settings.UiScaleFactor;
        double effective = configured > 0.05
            ? Math.Clamp(configured, 0.75, 1.50)
            : (_lastWorkWidth < 1100 || _lastWorkHeight < 680 ? 0.90 : (_lastWorkWidth >= 2200 && _lastWorkHeight >= 1200 ? 1.12 : 1.0));
        _scaledHost.Scale = effective;
    }

    private void ToggleFullscreen()
    {
        if (WindowState == WindowState.FullScreen)
        {
            WindowState = _preFullscreenState;
            StormGbcaUiState.Instance.SetFullscreen(false);
        }
        else
        {
            _preFullscreenState = WindowState == WindowState.Minimized ? WindowState.Normal : WindowState;
            WindowState = WindowState.FullScreen;
            StormGbcaUiState.Instance.SetFullscreen(true);
        }
    }

    private void OnWindowKeyDown(object? sender, KeyEventArgs e)
    {
        if (e.Key == Key.F11 || (e.Key == Key.Enter && e.KeyModifiers.HasFlag(KeyModifiers.Alt)))
        {
            ToggleFullscreen();
            e.Handled = true;
            return;
        }

        if ((e.KeyModifiers & KeyModifiers.Control) != 0)
        {
            if (e.Key is Key.OemPlus or Key.Add)
            {
                double next = Math.Clamp(Math.Round((_scaledHost.Scale + 0.10) * 20.0) / 20.0, 0.75, 1.50);
                StormGbcaUiState.Instance.Settings.UiScaleFactor = next;
                StormGbcaUiState.Instance.SaveSettings();
                ApplyUiScale();
                e.Handled = true;
            }
            else if (e.Key is Key.OemMinus or Key.Subtract)
            {
                double next = Math.Clamp(Math.Round((_scaledHost.Scale - 0.10) * 20.0) / 20.0, 0.75, 1.50);
                StormGbcaUiState.Instance.Settings.UiScaleFactor = next;
                StormGbcaUiState.Instance.SaveSettings();
                ApplyUiScale();
                e.Handled = true;
            }
            else if (e.Key is Key.D0 or Key.NumPad0)
            {
                StormGbcaUiState.Instance.Settings.UiScaleFactor = 0.0;
                StormGbcaUiState.Instance.SaveSettings();
                ApplyUiScale();
                e.Handled = true;
            }
        }
    }

    private void UpdateThemeBackground()
    {
        var theme = AuroraThemeManager.Instance.CurrentTheme;
        Background = theme.BackgroundBrush;
        nint hwnd = TryGetPlatformHandle()?.Handle ?? IntPtr.Zero;
        if (hwnd != IntPtr.Zero)
        {
            AuroraWin32Interop.ApplyDwmDarkMode(hwnd, theme.IsDark, enableMica: true);
        }
    }
}
