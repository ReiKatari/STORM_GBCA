// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Mozilla Public License, Version 2.0 (MPL-2.0).

using Avalonia;
using Avalonia.Controls;
using Avalonia.Layout;
using Avalonia.Media;
using Avalonia.Threading;
using Storm.AuroraFluid.Kit.Icons;
using Storm.AuroraFluid.Kit.Services;
using StormGbca.Core.Engine;
using StormGbca.Core.Library;
using StormGbca.Core.Configuration;
using StormGbca.UI.Common;

namespace StormGbca.UI.Views.StatusBar;

public class StormStatusBar : Border
{
    private readonly TextBlock _countText;
    private readonly TextBlock _audioText;
    private readonly TextBlock _statusText;
    private readonly TextBlock _clockText;
    private readonly DispatcherTimer _timer;
    private long _lastMetricsPostTickMs;
    private long _notificationUntilTickMs;

    public StormStatusBar()
    {
        Height = 28;
        BorderThickness = new Thickness(0, 1, 0, 0);
        Padding = new Thickness(14, 0);

        var grid = new Grid { ColumnDefinitions = new ColumnDefinitions("Auto,*,Auto") };

        var leftStack = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 6, VerticalAlignment = VerticalAlignment.Center };
        leftStack.Children.Add(new AuroraVectorIcon { Kind = AuroraIconKind.LibraryGrid, Size = 13, VerticalAlignment = VerticalAlignment.Center });
        _countText = new TextBlock { Text = "0 игр", FontSize = 11, VerticalAlignment = VerticalAlignment.Center };
        leftStack.Children.Add(_countText);
        grid.Children.Add(leftStack);

        var midStack = new StackPanel
        {
            Orientation = Orientation.Horizontal,
            Spacing = 16,
            HorizontalAlignment = HorizontalAlignment.Center,
            VerticalAlignment = VerticalAlignment.Center
        };

        var audioRow = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 4 };
        audioRow.Children.Add(new AuroraVectorIcon { Kind = AuroraIconKind.AudioWaveDsp, Size = 13, VerticalAlignment = VerticalAlignment.Center });
        _audioText = new TextBlock { Text = "48000 Гц Hermite DSP", FontSize = 10.5, VerticalAlignment = VerticalAlignment.Center };
        audioRow.Children.Add(_audioText);
        midStack.Children.Add(audioRow);

        var msgRow = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 4 };
        msgRow.Children.Add(new AuroraVectorIcon { Kind = AuroraIconKind.ShieldVerified, Size = 13, VerticalAlignment = VerticalAlignment.Center });
        _statusText = new TextBlock { Text = "mGBA + SameBoy готовы", FontSize = 10.5, VerticalAlignment = VerticalAlignment.Center };
        msgRow.Children.Add(_statusText);
        midStack.Children.Add(msgRow);

        grid.Children.Add(midStack);
        Grid.SetColumn(midStack, 1);

        _clockText = new TextBlock
        {
            Text = $"{DateTime.Now:dd.MM.yyyy HH:mm}",
            FontSize = 11,
            VerticalAlignment = VerticalAlignment.Center
        };
        grid.Children.Add(_clockText);
        Grid.SetColumn(_clockText, 2);

        Child = grid;

        _timer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(1) };
        _timer.Tick += (_, _) => _clockText.Text = $"{DateTime.Now:dd.MM.yyyy HH:mm}";
        _timer.Start();

        StormGbcaUiState.Instance.Library.LibraryChanged += UpdateCount;
        StormGbcaUiState.Instance.FilterChanged += UpdateCount;
        StormGbcaUiState.Instance.StatusNotification += msg =>
        {
            if (!string.IsNullOrWhiteSpace(msg))
            {
                Dispatcher.UIThread.Post(() =>
                {
                    _notificationUntilTickMs = Environment.TickCount64 + 4000;
                    _statusText.Text = msg;
                    DispatcherTimer.RunOnce(() =>
                    {
                        if (Environment.TickCount64 >= _notificationUntilTickMs)
                        {
                            ResetIdleStatusText();
                        }
                    }, TimeSpan.FromSeconds(4));
                });
            }
        };

        AttachedToVisualTree += (_, _) =>
        {
            StormGbcaUiState.Instance.Emulator.FrameCompleted -= OnFrameCompleted;
            StormGbcaUiState.Instance.Emulator.FrameCompleted += OnFrameCompleted;
            StormGbcaUiState.Instance.EmulationStarted -= OnEmulationStarted;
            StormGbcaUiState.Instance.EmulationStarted += OnEmulationStarted;
            StormGbcaUiState.Instance.EmulationStopped -= OnEmulationStopped;
            StormGbcaUiState.Instance.EmulationStopped += OnEmulationStopped;
        };

        DetachedFromVisualTree += (_, _) =>
        {
            StormGbcaUiState.Instance.Emulator.FrameCompleted -= OnFrameCompleted;
            StormGbcaUiState.Instance.EmulationStarted -= OnEmulationStarted;
            StormGbcaUiState.Instance.EmulationStopped -= OnEmulationStopped;
        };

        StormThemeBrushes.BindTheme(this, () =>
        {
            Background = StormThemeBrushes.ChromeSurface;
            BorderBrush = StormThemeBrushes.SubtleBorder;
            _countText.Foreground = StormThemeBrushes.BodyText;
            _audioText.Foreground = StormThemeBrushes.IsDark
                ? StormThemeBrushes.Accent
                : new SolidColorBrush(Color.Parse("#0369A1"));
            _statusText.Foreground = StormThemeBrushes.IsDark
                ? StormThemeBrushes.AccentSecondary
                : StormThemeBrushes.PrimaryText;
            _clockText.Foreground = StormThemeBrushes.SecondaryText;
        });

        UpdateCount();
    }

    private void OnEmulationStarted(GbcaRomRecord _)
    {
        Dispatcher.UIThread.Post(ResetIdleStatusText);
    }

    private void OnEmulationStopped()
    {
        Dispatcher.UIThread.Post(ResetIdleStatusText);
    }

    private void OnFrameCompleted(GbcaFrameMetrics metrics)
    {
        if (Environment.TickCount64 < _notificationUntilTickMs) return;
        long now = Environment.TickCount64;
        long prev = Interlocked.Read(ref _lastMetricsPostTickMs);
        if (now - prev < 240) return;
        if (Interlocked.CompareExchange(ref _lastMetricsPostTickMs, now, prev) != prev) return;

        Dispatcher.UIThread.Post(() =>
        {
            if (!StormGbcaUiState.Instance.IsEmulationActive || Environment.TickCount64 < _notificationUntilTickMs) return;
            _statusText.Text = $"{metrics.ActiveCore} | {metrics.MeasuredFps:0.0} FPS | {metrics.FrameTimeMs:0.1} мс";
        });
    }

    private void ResetIdleStatusText()
    {
        if (StormGbcaUiState.Instance.IsEmulationActive)
        {
            var m = StormGbcaUiState.Instance.LastMetrics;
            if (!string.IsNullOrWhiteSpace(m.ActiveCore))
            {
                _statusText.Text = $"{m.ActiveCore} | {m.MeasuredFps:0.0} FPS | {m.FrameTimeMs:0.1} мс";
                return;
            }
        }
        _statusText.Text = "mGBA + SameBoy готовы";
    }

    private void UpdateCount()
    {
        if (!Dispatcher.UIThread.CheckAccess())
        {
            Dispatcher.UIThread.Post(UpdateCount);
            return;
        }
        int count = StormGbcaUiState.Instance.GetFilteredRoms().Count;
        string plural = AuroraLocalizationService.GetRussianPlural(count, "игра", "игры", "игр");
        _countText.Text = $"{count} {plural}";
    }
}
