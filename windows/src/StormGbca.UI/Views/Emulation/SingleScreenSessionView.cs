// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Apache License, Version 2.0.

using Avalonia;
using Avalonia.Controls;
using Avalonia.Input;
using Avalonia.Layout;
using Avalonia.Media;
using Avalonia.Media.Imaging;
using Avalonia.Platform;
using Avalonia.Threading;
using Storm.AuroraFluid.Kit.Icons;
using Storm.AuroraFluid.Kit.Services;
using StormGbca.Core.Configuration;
using StormGbca.Core.Engine;
using StormGbca.Core.Native;
using StormGbca.Core.Paths;
using StormGbca.UI.Common;

namespace StormGbca.UI.Views.Emulation;

/// <summary>
/// Single-screen GB / GBC / GBA emulation viewport with zero-alloc WriteableBitmap rendering,
/// SkiaSharp 3D console skin frame, per-system interactive button overlay, 3D boot splash,
/// keyboard + XInput + touch/pointer merging, and quick-action HUD bar.
/// </summary>
public class SingleScreenSessionView : Grid
{
    private readonly Border _hudBar;
    private readonly TextBlock _statsText;
    private readonly TextBlock _ffText;
    private readonly TextBlock _skinLabel;
    private readonly Grid _stageHost;
    private readonly Image _chassisImage;
    private readonly Image _lcdImage;
    private readonly Image _overlayImage;
    private readonly Gbca3DBootOverlay _bootSplash;
    private readonly EmulationPauseOverlay _pauseOverlay;
    private readonly DispatcherTimer _uiTimer;

    private WriteableBitmap? _lcdBitmap;
    private int _lcdW;
    private int _lcdH;
    private byte[] _frameStaging = [];

    private StormPlatform _lastSkinPlatform = StormPlatform.None;
    private string _lastSkinKey = string.Empty;
    private uint _lastOverlayMask = uint.MaxValue;
    private string _lastOverlayKey = string.Empty;
    private List<OverlayHitbox> _hitboxes = [];
    private uint _keyboardMask;
    private uint _pointerOverlayMask;
    private int _activeSlot;
    private bool _isPointerDownOnOverlay;

    public SingleScreenSessionView()
    {
        Background = Brushes.Black;
        Focusable = true;
        RowDefinitions = new RowDefinitions("Auto,*");

        // Top HUD Bar
        _hudBar = new Border
        {
            Height = 38,
            Background = new SolidColorBrush(Color.FromArgb(230, 12, 16, 28)),
            BorderBrush = new SolidColorBrush(Color.FromArgb(55, 255, 255, 255)),
            BorderThickness = new Thickness(0, 0, 0, 1),
            Padding = new Thickness(10, 4)
        };
        var hudGrid = new Grid { ColumnDefinitions = new ColumnDefinitions("Auto,*,Auto") };

        var leftBar = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 6, VerticalAlignment = VerticalAlignment.Center };
        var skinBtn = CreateHudPill(AuroraIconKind.ShaderPrism, "Корпус 3D", Color.Parse("#8BAC0F"), CyclePlatformSkin);
        _skinLabel = skinBtn.Label;
        leftBar.Children.Add(skinBtn.Btn);
        leftBar.Children.Add(CreateHudPill(AuroraIconKind.DualScreenLayout, "Оверлей кнопок", Color.Parse("#7B42F6"), ToggleControlsDeck).Btn);
        leftBar.Children.Add(CreateHudPill(AuroraIconKind.RewindVortex, "Перемотка", Color.Parse("#38BDF8"), PerformRewind).Btn);
        hudGrid.Children.Add(leftBar);

        _statsText = new TextBlock
        {
            Text = "59.7 FPS",
            FontSize = 11,
            FontWeight = FontWeight.SemiBold,
            Foreground = new SolidColorBrush(Color.Parse("#CBD5E1")),
            HorizontalAlignment = HorizontalAlignment.Center,
            VerticalAlignment = VerticalAlignment.Center
        };
        hudGrid.Children.Add(_statsText);
        Grid.SetColumn(_statsText, 1);

        var rightBar = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 6, VerticalAlignment = VerticalAlignment.Center };
        var ffPair = CreateHudPill(AuroraIconKind.FastForwardBolt, "Ускорение (1x)", Color.Parse("#F59E0B"), ToggleFastForward);
        _ffText = ffPair.Label;
        rightBar.Children.Add(ffPair.Btn);
        rightBar.Children.Add(CreateHudPill(AuroraIconKind.FullScreenExpand, "Полный экран (F11)", Color.Parse("#06B6D4"), () => StormGbcaUiState.Instance.RequestToggleFullscreen()).Btn);
        rightBar.Children.Add(CreateHudPill(AuroraIconKind.SaveSlotRibbon, "Сохранить (F5)", Color.Parse("#10B981"), () => PerformQuickSave(_activeSlot)).Btn);
        rightBar.Children.Add(CreateHudPill(AuroraIconKind.LoadSlotArrow, "Загрузить (F8)", Color.Parse("#38BDF8"), () => PerformQuickLoad(_activeSlot)).Btn);
        rightBar.Children.Add(CreateHudPill(AuroraIconKind.ScreenshotCamera, "Снимок экрана (F12)", Color.Parse("#EC4899"), TakeScreenshot).Btn);
        rightBar.Children.Add(CreateHudPill(AuroraIconKind.PauseTwin, "Пауза (Esc)", Color.Parse("#F59E0B"), TogglePauseMenu).Btn);
        rightBar.Children.Add(CreateHudPill(AuroraIconKind.StopOctagon, "Выход", Color.Parse("#EF4444"), () => StormGbcaUiState.Instance.StopEmulation()).Btn);
        hudGrid.Children.Add(rightBar);
        Grid.SetColumn(rightBar, 2);

        _hudBar.Child = hudGrid;
        Children.Add(_hudBar);

        // Stage Viewport
        _stageHost = new Grid { ClipToBounds = true, Background = new SolidColorBrush(Color.Parse("#070911")) };
        _chassisImage = new Image { Stretch = Stretch.Fill, IsHitTestVisible = false };
        _lcdImage = new Image { HorizontalAlignment = HorizontalAlignment.Left, VerticalAlignment = VerticalAlignment.Top, Stretch = Stretch.Fill };
        RenderOptions.SetBitmapInterpolationMode(_lcdImage, BitmapInterpolationMode.None);
        _overlayImage = new Image { Stretch = Stretch.Fill, Cursor = new Cursor(StandardCursorType.Hand) };

        _overlayImage.PointerPressed += OnOverlayPointerPressed;
        _overlayImage.PointerMoved += OnOverlayPointerMoved;
        _overlayImage.PointerReleased += (_, _) => ReleaseOverlayButtons();

        _stageHost.Children.Add(_chassisImage);
        _stageHost.Children.Add(_lcdImage);
        _stageHost.Children.Add(_overlayImage);
        _stageHost.PropertyChanged += (_, ch) =>
        {
            if (ch.Property == BoundsProperty) InvalidateStageVisuals(force: true);
        };
        Children.Add(_stageHost);
        Grid.SetRow(_stageHost, 1);

        // 3D Boot Splash
        _bootSplash = new Gbca3DBootOverlay();
        Children.Add(_bootSplash);
        Grid.SetRowSpan(_bootSplash, 2);

        // Pause Overlay
        _pauseOverlay = new EmulationPauseOverlay { IsVisible = false };
        _pauseOverlay.ResumeRequested += () => SetPauseOverlay(false);
        _pauseOverlay.StopRequested += () => StormGbcaUiState.Instance.StopEmulation();
        _pauseOverlay.SaveSlotRequested += PerformQuickSave;
        _pauseOverlay.LoadSlotRequested += PerformQuickLoad;
        _pauseOverlay.RewindRequested += PerformRewind;
        _pauseOverlay.ResetRequested += () =>
        {
            StormGbcaUiState.Instance.Emulator.Reset();
            SetPauseOverlay(false);
        };
        Children.Add(_pauseOverlay);
        Grid.SetRowSpan(_pauseOverlay, 2);

        _uiTimer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(16) };
        _uiTimer.Tick += OnUiTick;

        StormGbcaUiState.Instance.EmulationStarted += OnEmulationStarted;
        StormGbcaUiState.Instance.EmulationStopped += OnEmulationStopped;
        StormGbcaUiState.Instance.SkinOrOverlayChanged += () => InvalidateStageVisuals(force: true);
        StormGbcaUiState.Instance.FullscreenChanged += fs => _hudBar.IsVisible = !fs;

        KeyDown += OnViewKeyDown;
        KeyUp += OnViewKeyUp;
        LostFocus += (_, _) =>
        {
            _keyboardMask = 0;
            _pointerOverlayMask = 0;
            PushMasks();
        };
    }

    private void OnEmulationStarted(GbcaRomRecord rom)
    {
        _pauseOverlay.IsVisible = false;
        _keyboardMask = 0;
        _pointerOverlayMask = 0;
        _ffText.Text = "Ускорение (1x)";
        InvalidateStageVisuals(force: true);
        if (StormGbcaUiState.Instance.Settings.Show3DBootSplash)
        {
            string coreName = rom.Platform == StormPlatform.Gba
                ? StormGbcaUiState.Instance.Settings.PreferredCoreGba.ToString()
                : (rom.Platform == StormPlatform.Gbc
                    ? StormGbcaUiState.Instance.Settings.PreferredCoreGbc.ToString()
                    : StormGbcaUiState.Instance.Settings.PreferredCoreGb.ToString());
            _bootSplash.PlayForPlatform(rom.Platform, coreName);
        }
        if (!_uiTimer.IsEnabled) _uiTimer.Start();
        Focus();
    }

    private void OnEmulationStopped()
    {
        _uiTimer.Stop();
        _bootSplash.Dismiss();
        _pauseOverlay.IsVisible = false;
    }

    private void OnUiTick(object? sender, EventArgs e)
    {
        if (!StormGbcaUiState.Instance.IsEmulationActive || StormGbcaUiState.Instance.IsPaused) return;

        var emu = StormGbcaUiState.Instance.Emulator;
        int copied = emu.CopyCurrentFrameBgra(ref _frameStaging, out int w, out int h);
        if (copied > 0 && w > 0 && h > 0)
        {
            if (_lcdBitmap == null || _lcdW != w || _lcdH != h)
            {
                _lcdW = w;
                _lcdH = h;
                _lcdBitmap = new WriteableBitmap(new PixelSize(w, h), new Vector(96, 96), PixelFormat.Bgra8888, AlphaFormat.Premul);
                _lcdImage.Source = _lcdBitmap;
                InvalidateStageVisuals(force: true);
            }

            BitmapHelper.UpdateBgraBitmap(_lcdBitmap, _frameStaging, w, h);
            _lcdImage.InvalidateVisual();
        }

        uint gpMask = emu.Gamepad.PollButtonMask(out _, out _);
        uint visualMask = _keyboardMask | _pointerOverlayMask | gpMask;
        if (visualMask != _lastOverlayMask)
        {
            _lastOverlayMask = visualMask;
            RefreshOverlayBitmapOnly(visualMask);
        }

        var metrics = StormGbcaUiState.Instance.LastMetrics;
        if (metrics.Width > 0)
        {
            string rumbleTxt = metrics.RumbleIntensity > 0.05f ? " | Вибрация" : string.Empty;
            _statsText.Text = $"{metrics.MeasuredFps:0.0} FPS | {metrics.ActiveCore} ({metrics.Width}x{metrics.Height}){rumbleTxt}";
        }
    }

    private void InvalidateStageVisuals(bool force)
    {
        int w = (int)Math.Round(_stageHost.Bounds.Width);
        int h = (int)Math.Round(_stageHost.Bounds.Height);
        if (w < 64 || h < 64) return;

        var settings = StormGbcaUiState.Instance.Settings;
        var plat = StormGbcaUiState.Instance.Emulator.ActivePlatform;
        if (plat == StormPlatform.None) plat = StormPlatform.Gba;

        int natW = _lcdW > 0 ? _lcdW : (plat == StormPlatform.Gba ? 240 : 160);
        int natH = _lcdH > 0 ? _lcdH : (plat == StormPlatform.Gba ? 160 : 144);

        var palette = GbcaConsoleSkinRenderer.ResolvePalette(plat, settings);
        _skinLabel.Text = palette.DisplayName == "None" ? "Без корпуса" : palette.DisplayName;

        var vp = GbcaConsoleSkinRenderer.ComputeScreenViewport(w, h, natW, natH, plat, settings);
        _lcdImage.Width = vp.Width;
        _lcdImage.Height = vp.Height;
        _lcdImage.Margin = new Thickness(vp.X, vp.Y, 0, 0);
        var activeProf = settings.GetOverlayProfileForPlatform(plat);
        int rot = activeProf.ScreenRect?.RotationDeg ?? 0;
        _lcdImage.RenderTransformOrigin = RelativePoint.Center;
        _lcdImage.RenderTransform = rot != 0 ? new RotateTransform(rot) : null;

        var interp = settings.VideoFilter is StormVideoFilter.Nearest or StormVideoFilter.LcdGrid or StormVideoFilter.RgbSubpixel
            ? BitmapInterpolationMode.None
            : BitmapInterpolationMode.MediumQuality;
        RenderOptions.SetBitmapInterpolationMode(_lcdImage, interp);

        bool hideSkin = GbcaConsoleSkinRenderer.ShouldHideSkinForScreen(plat, settings);
        _chassisImage.IsVisible = !hideSkin;
        string skinKey = $"{w}x{h}|{plat}|{palette.DisplayName}|{settings.SkinNightDimming}|{hideSkin}|{vp.X},{vp.Y},{vp.Width},{vp.Height}";
        if (!hideSkin && (force || skinKey != _lastSkinKey || _lastSkinPlatform != plat))
        {
            _lastSkinKey = skinKey;
            _lastSkinPlatform = plat;
            _chassisImage.Source = GbcaConsoleSkinRenderer.RenderChassisBitmap(w, h, vp, plat, palette, settings.SkinNightDimming);
        }

        var prof = settings.GetOverlayProfileForPlatform(plat);
        string ovKey = $"{w}x{h}|{plat}|{prof.OverlayLayout}|{prof.ButtonStyle}|{prof.Opacity:0.00}|{prof.Scale:0.00}|{prof.ShowControlsDeck}|{prof.ShowLR}|{prof.SeparateABButtons}|{prof.AbSpacingPercent}|{vp.X},{vp.Y}";
        if (force || ovKey != _lastOverlayKey)
        {
            _lastOverlayKey = ovKey;
            _hitboxes = GbcaOverlayRenderer.ComputeHitboxes(w, h, prof);
            RefreshOverlayBitmapOnly(_lastOverlayMask == uint.MaxValue ? 0 : _lastOverlayMask);
        }
    }

    private void RefreshOverlayBitmapOnly(uint pressedMask)
    {
        int w = (int)Math.Round(_stageHost.Bounds.Width);
        int h = (int)Math.Round(_stageHost.Bounds.Height);
        if (w < 64 || h < 64) return;

        var settings = StormGbcaUiState.Instance.Settings;
        var plat = StormGbcaUiState.Instance.Emulator.ActivePlatform;
        if (plat == StormPlatform.None) plat = StormPlatform.Gba;
        var prof = settings.GetOverlayProfileForPlatform(plat);
        var vp = new Rect(_lcdImage.Margin.Left, _lcdImage.Margin.Top, _lcdImage.Width, _lcdImage.Height);
        _overlayImage.Source = GbcaOverlayRenderer.RenderOverlayBitmap(w, h, vp, plat, prof, pressedMask);
    }

    private void OnOverlayPointerPressed(object? sender, PointerPressedEventArgs e)
    {
        Focus();
        _isPointerDownOnOverlay = true;
        UpdatePointerHit(e.GetPosition(_overlayImage));
    }

    private void OnOverlayPointerMoved(object? sender, PointerEventArgs e)
    {
        if (_isPointerDownOnOverlay) UpdatePointerHit(e.GetPosition(_overlayImage));
    }

    private void ReleaseOverlayButtons()
    {
        _isPointerDownOnOverlay = false;
        _pointerOverlayMask = 0;
        PushMasks();
    }

    private void UpdatePointerHit(Point p)
    {
        uint mask = 0;
        foreach (var hb in _hitboxes)
        {
            if (hb.Bounds.Contains(p)) mask |= (uint)hb.Key;
        }
        if (mask != _pointerOverlayMask)
        {
            _pointerOverlayMask = mask;
            PushMasks();
        }
    }

    private void PushMasks()
    {
        StormGbcaUiState.Instance.Emulator.SetKeyboardMask(_keyboardMask);
        StormGbcaUiState.Instance.Emulator.SetOverlayMask(_pointerOverlayMask);
    }

    private void OnViewKeyDown(object? sender, KeyEventArgs e)
    {
        if (e.Key == Key.Escape)
        {
            TogglePauseMenu();
            e.Handled = true;
            return;
        }
        if (e.Key == Key.F5)
        {
            PerformQuickSave(_activeSlot);
            e.Handled = true;
            return;
        }
        if (e.Key == Key.F8)
        {
            PerformQuickLoad(_activeSlot);
            e.Handled = true;
            return;
        }
        if (e.Key == Key.F12)
        {
            TakeScreenshot();
            e.Handled = true;
            return;
        }

        var settings = StormGbcaUiState.Instance.Settings;
        if (MatchesBinding(e.Key, settings.KeyBindings, "FastForward"))
        {
            if (!StormGbcaUiState.Instance.Emulator.IsFastForward)
            {
                StormGbcaUiState.Instance.Emulator.SetFastForward(true, settings.FastForwardMultiplier);
                _ffText.Text = $"Ускорение ({settings.FastForwardMultiplier:0.#}x)";
            }
            e.Handled = true;
            return;
        }
        if (MatchesBinding(e.Key, settings.KeyBindings, "Rewind"))
        {
            PerformRewind();
            e.Handled = true;
            return;
        }

        uint bit = MapKeyToMask(e.Key, settings.KeyBindings);
        if (bit != 0)
        {
            _keyboardMask |= bit;
            PushMasks();
            e.Handled = true;
        }
    }

    private void OnViewKeyUp(object? sender, KeyEventArgs e)
    {
        var settings = StormGbcaUiState.Instance.Settings;
        if (MatchesBinding(e.Key, settings.KeyBindings, "FastForward"))
        {
            StormGbcaUiState.Instance.Emulator.SetFastForward(false, 1.0f);
            _ffText.Text = "Ускорение (1x)";
            e.Handled = true;
            return;
        }

        uint bit = MapKeyToMask(e.Key, settings.KeyBindings);
        if (bit != 0)
        {
            _keyboardMask &= ~bit;
            PushMasks();
            e.Handled = true;
        }
    }

    private static bool MatchesBinding(Key key, Dictionary<string, string> map, string action)
    {
        return map.TryGetValue(action, out string? name) &&
               Enum.TryParse<Key>(name, true, out var parsed) &&
               parsed == key;
    }

    private static uint MapKeyToMask(Key key, Dictionary<string, string> map)
    {
        if (MatchesBinding(key, map, "Up")) return (uint)StormKeyMask.Up;
        if (MatchesBinding(key, map, "Down")) return (uint)StormKeyMask.Down;
        if (MatchesBinding(key, map, "Left")) return (uint)StormKeyMask.Left;
        if (MatchesBinding(key, map, "Right")) return (uint)StormKeyMask.Right;
        if (MatchesBinding(key, map, "A")) return (uint)StormKeyMask.A;
        if (MatchesBinding(key, map, "B")) return (uint)StormKeyMask.B;
        if (MatchesBinding(key, map, "L")) return (uint)StormKeyMask.L;
        if (MatchesBinding(key, map, "R")) return (uint)StormKeyMask.R;
        if (MatchesBinding(key, map, "Start")) return (uint)StormKeyMask.Start;
        if (MatchesBinding(key, map, "Select")) return (uint)StormKeyMask.Select;
        return 0;
    }

    private void CyclePlatformSkin()
    {
        var s = StormGbcaUiState.Instance.Settings;
        var plat = StormGbcaUiState.Instance.Emulator.ActivePlatform;
        if (plat is StormPlatform.Gb or StormPlatform.Sgb)
        {
            int n = Enum.GetValues<GbConsoleSkin>().Length;
            s.GbConsoleSkin = (GbConsoleSkin)(((int)s.GbConsoleSkin + 1) % n);
        }
        else if (plat == StormPlatform.Gbc)
        {
            int n = Enum.GetValues<GbcConsoleSkin>().Length;
            s.GbcConsoleSkin = (GbcConsoleSkin)(((int)s.GbcConsoleSkin + 1) % n);
        }
        else
        {
            int n = Enum.GetValues<GbaConsoleSkin>().Length;
            s.GbaConsoleSkin = (GbaConsoleSkin)(((int)s.GbaConsoleSkin + 1) % n);
        }
        StormGbcaUiState.Instance.NotifySkinOrOverlayChanged();
        Focus();
    }

    private void ToggleControlsDeck()
    {
        var plat = StormGbcaUiState.Instance.Emulator.ActivePlatform;
        if (plat == StormPlatform.None) plat = StormPlatform.Gba;
        var prof = StormGbcaUiState.Instance.Settings.GetOverlayProfileForPlatform(plat);
        prof.ShowControlsDeck = !prof.ShowControlsDeck;
        StormGbcaUiState.Instance.NotifySkinOrOverlayChanged();
        Focus();
    }

    private void ToggleFastForward()
    {
        var emu = StormGbcaUiState.Instance.Emulator;
        bool next = !emu.IsFastForward;
        float mult = StormGbcaUiState.Instance.Settings.FastForwardMultiplier;
        emu.SetFastForward(next, mult);
        _ffText.Text = next ? $"Ускорение ({mult:0.#}x)" : "Ускорение (1x)";
        Focus();
    }

    private void TogglePauseMenu() => SetPauseOverlay(!_pauseOverlay.IsVisible);

    private void SetPauseOverlay(bool open)
    {
        _pauseOverlay.IsVisible = open;
        StormGbcaUiState.Instance.SetPaused(open);
        if (open) _pauseOverlay.RefreshOnOpen();
        else Focus();
    }

    private void PerformQuickSave(int slot)
    {
        _activeSlot = slot;
        var emu = StormGbcaUiState.Instance.Emulator;
        if (emu.SaveState(slot))
        {
            byte[] snap = [];
            emu.CopyCurrentFrameBgra(ref snap, out int w, out int h);
            if (snap.Length > 0)
            {
                string thumbPath = StormGbcaPaths.GetSaveStateThumbnailPath(emu.ActiveRomKey, slot);
                BitmapHelper.SaveBgraPng(snap, w, h, thumbPath);
            }
            AuroraTactileFeedbackService.Instance.PlayAchievementChime();
        }
    }

    private void PerformQuickLoad(int slot)
    {
        if (!StormGbcaUiState.Instance.RetroAchievements.IsFeatureAllowedInCurrentMode("loadstate"))
        {
            StormGbcaUiState.Instance.NotifyStatus("Загрузка состояний отключена в хардкорном режиме RetroAchievements");
            return;
        }
        _activeSlot = slot;
        StormGbcaUiState.Instance.Emulator.LoadState(slot);
        AuroraTactileFeedbackService.Instance.PlayClick();
    }

    private void PerformRewind()
    {
        if (!StormGbcaUiState.Instance.RetroAchievements.IsFeatureAllowedInCurrentMode("rewind"))
        {
            StormGbcaUiState.Instance.NotifyStatus("Перемотка отключена в хардкорном режиме RetroAchievements");
            return;
        }
        if (StormGbcaUiState.Instance.Emulator.RewindStep())
        {
            StormGbcaUiState.Instance.NotifyStatus("Перемотка назад выполнена");
        }
    }

    private void TakeScreenshot()
    {
        try
        {
            byte[] snap = [];
            int bytes = StormGbcaUiState.Instance.Emulator.CopyCurrentFrameBgra(ref snap, out int w, out int h);
            if (bytes <= 0) return;
            string key = StormGbcaPaths.SanitizeFileKey(StormGbcaUiState.Instance.Emulator.ActiveRomKey);
            string file = Path.Combine(StormGbcaPaths.ScreenshotsDirectory, $"{key}_{DateTime.Now:yyyyMMdd_HHmmss}.png");
            BitmapHelper.SaveBgraPng(snap, w, h, file);
            AuroraTactileFeedbackService.Instance.PlayAchievementChime();
            StormGbcaUiState.Instance.NotifyStatus($"Снимок экрана сохранен: {Path.GetFileName(file)}");
        }
        catch (Exception ex)
        {
            StormGbcaUiState.Instance.NotifyStatus($"Ошибка снимка экрана: {ex.Message}");
        }
    }

    private static (TextBlock Label, Button Btn) CreateHudPill(AuroraIconKind icon, string text, Color accent, Action onClick)
    {
        var btn = new Button
        {
            Height = 28,
            Padding = new Thickness(8, 2),
            Background = new SolidColorBrush(Color.FromArgb(40, accent.R, accent.G, accent.B)),
            BorderBrush = new SolidColorBrush(Color.FromArgb(100, accent.R, accent.G, accent.B)),
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(7),
            Cursor = new Cursor(StandardCursorType.Hand),
            VerticalAlignment = VerticalAlignment.Center
        };
        var s = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 5, VerticalAlignment = VerticalAlignment.Center };
        s.Children.Add(new AuroraVectorIcon { Kind = icon, Size = 13, VerticalAlignment = VerticalAlignment.Center });
        var tb = new TextBlock { Text = text, FontSize = 11, FontWeight = FontWeight.Medium, Foreground = Brushes.White, VerticalAlignment = VerticalAlignment.Center };
        s.Children.Add(tb);
        btn.Content = s;
        btn.Click += (_, _) =>
        {
            AuroraTactileFeedbackService.Instance.PlayClick();
            onClick();
        };
        return (tb, btn);
    }
}
