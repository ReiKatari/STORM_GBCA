// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Apache License, Version 2.0.

using Avalonia;
using Avalonia.Controls;
using Avalonia.Layout;
using Avalonia.Media;
using Avalonia.Threading;
using Storm.AuroraFluid.Kit.Icons;
using StormGbca.Core.Native;
using StormGbca.UI.Common;

namespace StormGbca.UI.Views.Emulation;

/// <summary>
/// Animated 3D GB/GBC/GBA neon STORM boot splash overlay matching the STORM ecosystem style.
/// </summary>
public class Gbca3DBootOverlay : Border
{
    private readonly TextBlock _platformText;
    private readonly TextBlock _subText;
    private readonly Border _glowCard;
    private readonly ProgressBar _bar;
    private DispatcherTimer? _timer;
    private double _progress;

    public Gbca3DBootOverlay()
    {
        Background = new SolidColorBrush(Color.FromArgb(245, 7, 9, 18));
        HorizontalAlignment = HorizontalAlignment.Stretch;
        VerticalAlignment = VerticalAlignment.Stretch;
        IsVisible = false;

        _glowCard = new Border
        {
            Width = 420,
            Padding = new Thickness(28, 24),
            CornerRadius = new CornerRadius(20),
            Background = new SolidColorBrush(Color.FromArgb(220, 14, 18, 34)),
            BorderThickness = new Thickness(1.8),
            HorizontalAlignment = HorizontalAlignment.Center,
            VerticalAlignment = VerticalAlignment.Center
        };

        var stack = new StackPanel { Spacing = 12, HorizontalAlignment = HorizontalAlignment.Center };
        stack.Children.Add(new AuroraVectorIcon
        {
            Kind = AuroraIconKind.RomCartridge,
            Size = 52,
            HorizontalAlignment = HorizontalAlignment.Center
        });
        stack.Children.Add(new TextBlock
        {
            Text = "STORM GBCA",
            FontSize = 26,
            FontWeight = FontWeight.Black,
            Foreground = Brushes.White,
            HorizontalAlignment = HorizontalAlignment.Center
        });
        _platformText = new TextBlock
        {
            Text = "GAME BOY ADVANCE",
            FontSize = 14,
            FontWeight = FontWeight.Bold,
            HorizontalAlignment = HorizontalAlignment.Center
        };
        stack.Children.Add(_platformText);

        _bar = new ProgressBar
        {
            Width = 280,
            Height = 6,
            Minimum = 0,
            Maximum = 100,
            Value = 0
        };
        stack.Children.Add(_bar);

        _subText = new TextBlock
        {
            Text = "Инициализация ядра и 3D-корпуса...",
            FontSize = 11.5,
            Foreground = new SolidColorBrush(Color.Parse("#94A3B8")),
            HorizontalAlignment = HorizontalAlignment.Center
        };
        stack.Children.Add(_subText);

        _glowCard.Child = stack;
        Child = _glowCard;

        PointerPressed += (_, _) => Dismiss();
    }

    public void PlayForPlatform(StormPlatform platform, string coreName)
    {
        Color c = StormGbcaUiState.GetPlatformBadgeColor(platform);
        _glowCard.BorderBrush = new SolidColorBrush(c);
        _glowCard.BoxShadow = new BoxShadows(new BoxShadow { OffsetX = 0, OffsetY = 0, Blur = 36, Color = Color.FromArgb(140, c.R, c.G, c.B) });
        _platformText.Foreground = new SolidColorBrush(c);
        _platformText.Text = platform switch
        {
            StormPlatform.Gb => "NINTENDO GAME BOY",
            StormPlatform.Gbc => "NINTENDO GAME BOY COLOR",
            StormPlatform.Sgb => "SUPER GAME BOY",
            _ => "NINTENDO GAME BOY ADVANCE",
        };
        _subText.Text = $"Ядро: {coreName} | 59.73 Гц | Нажмите для пропуска";
        _bar.Foreground = new SolidColorBrush(c);

        _progress = 0;
        _bar.Value = 0;
        IsVisible = true;

        _timer?.Stop();
        _timer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(20) };
        _timer.Tick += (_, _) =>
        {
            _progress += 3.5;
            _bar.Value = Math.Min(100, _progress);
            if (_progress >= 100)
            {
                Dismiss();
            }
        };
        _timer.Start();
    }

    public void Dismiss()
    {
        _timer?.Stop();
        IsVisible = false;
    }
}
