// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Mozilla Public License, Version 2.0 (MPL-2.0).

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
using StormGbca.Core.Subsystems;
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
    private readonly Button _rwBtn;
    private readonly Button _ldBtn;
    private readonly List<TextBlock> _collapsibleHudLabels = [];
    private readonly Grid _stageHost;
    private readonly Image _chassisImage;
    private readonly Image _lcdImage;
    private readonly Image _overlayImage;
    private readonly Gbca3DBootOverlay _bootSplash;
    private readonly EmulationPauseOverlay _pauseOverlay;
    private readonly DispatcherTimer _uiTimer;

    // Live RetroAchievements HUD overlays
    private readonly Border _raBannerCard;
    private readonly TextBlock _raBannerTitle;
    private readonly TextBlock _raBannerSub;
    private readonly DispatcherTimer _raBannerTimer;

    private readonly Border _raToastCard;
    private readonly TextBlock _raToastBadge;
    private readonly TextBlock _raToastTitle;
    private readonly TextBlock _raToastDesc;
    private readonly DispatcherTimer _raToastTimer;

    private readonly Border _raChallengePill;
    private readonly TextBlock _raChallengeText;
    private readonly Border _raMeasuredPill;
    private readonly TextBlock _raMeasuredText;
    private readonly DispatcherTimer _raMeasuredTimer;
    private readonly Border _raLeaderboardPill;
    private readonly TextBlock _raLeaderboardText;
    private readonly DispatcherTimer _raLeaderboardTimer;

    private WriteableBitmap? _lcdBitmap;
    private int _lcdW;
    private int _lcdH;
    private byte[] _frameStaging = [];
    private Rect _lastViewportRect;

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

        // Top HUD Bar (responsive WrapPanel + collapsible labels below 1060px)
        _hudBar = new Border
        {
            MinHeight = 38,
            Background = new SolidColorBrush(Color.FromArgb(230, 12, 16, 28)),
            BorderBrush = new SolidColorBrush(Color.FromArgb(55, 255, 255, 255)),
            BorderThickness = new Thickness(0, 0, 0, 1),
            Padding = new Thickness(10, 4)
        };
        var hudGrid = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto"), ColumnSpacing = 10 };

        var actionsWrap = new WrapPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Center };
        var skinBtn = CreateHudPill(AuroraIconKind.ShaderPrism, "Корпус 3D", Color.Parse("#8BAC0F"), CyclePlatformSkin);
        _skinLabel = skinBtn.Label;
        actionsWrap.Children.Add(skinBtn.Btn);

        var ovBtn = CreateHudPill(AuroraIconKind.DualScreenLayout, "Оверлей кнопок", Color.Parse("#7B42F6"), ToggleControlsDeck);
        _collapsibleHudLabels.Add(ovBtn.Label);
        actionsWrap.Children.Add(ovBtn.Btn);

        var rwBtn = CreateHudPill(AuroraIconKind.RewindVortex, "Перемотка", Color.Parse("#38BDF8"), PerformRewind);
        _rwBtn = rwBtn.Btn;
        _collapsibleHudLabels.Add(rwBtn.Label);
        actionsWrap.Children.Add(rwBtn.Btn);

        var ffPair = CreateHudPill(AuroraIconKind.FastForwardBolt, "Ускорение (1x)", Color.Parse("#F59E0B"), ToggleFastForward);
        _ffText = ffPair.Label;
        actionsWrap.Children.Add(ffPair.Btn);

        var fsBtn = CreateHudPill(AuroraIconKind.FullScreenExpand, "Полный экран (F11)", Color.Parse("#06B6D4"), () => StormGbcaUiState.Instance.RequestToggleFullscreen());
        _collapsibleHudLabels.Add(fsBtn.Label);
        actionsWrap.Children.Add(fsBtn.Btn);

        var svBtn = CreateHudPill(AuroraIconKind.SaveSlotRibbon, "Сохранить (F5)", Color.Parse("#10B981"), () => PerformQuickSave(_activeSlot));
        _collapsibleHudLabels.Add(svBtn.Label);
        actionsWrap.Children.Add(svBtn.Btn);

        var ldBtn = CreateHudPill(AuroraIconKind.LoadSlotArrow, "Загрузить (F8)", Color.Parse("#38BDF8"), () => PerformQuickLoad(_activeSlot));
        _ldBtn = ldBtn.Btn;
        _collapsibleHudLabels.Add(ldBtn.Label);
        actionsWrap.Children.Add(ldBtn.Btn);

        var ssBtn = CreateHudPill(AuroraIconKind.ScreenshotCamera, "Снимок экрана (F12)", Color.Parse("#EC4899"), TakeScreenshot);
        _collapsibleHudLabels.Add(ssBtn.Label);
        actionsWrap.Children.Add(ssBtn.Btn);

        var psBtn = CreateHudPill(AuroraIconKind.PauseTwin, "Пауза (Esc)", Color.Parse("#F59E0B"), TogglePauseMenu);
        _collapsibleHudLabels.Add(psBtn.Label);
        actionsWrap.Children.Add(psBtn.Btn);

        var exBtn = CreateHudPill(AuroraIconKind.StopOctagon, "Выход", Color.Parse("#EF4444"), () => StormGbcaUiState.Instance.StopEmulation());
        _collapsibleHudLabels.Add(exBtn.Label);
        actionsWrap.Children.Add(exBtn.Btn);

        hudGrid.Children.Add(actionsWrap);

        _statsText = new TextBlock
        {
            Text = "59.7 FPS",
            FontSize = 11,
            FontWeight = FontWeight.SemiBold,
            Foreground = new SolidColorBrush(Color.Parse("#CBD5E1")),
            HorizontalAlignment = HorizontalAlignment.Right,
            VerticalAlignment = VerticalAlignment.Center
        };
        hudGrid.Children.Add(_statsText);
        Grid.SetColumn(_statsText, 1);

        _hudBar.Child = hudGrid;
        Children.Add(_hudBar);

        PropertyChanged += (_, ch) =>
        {
            if (ch.Property == BoundsProperty && Bounds.Width > 0)
            {
                bool compact = Bounds.Width < 1060;
                foreach (var lbl in _collapsibleHudLabels) lbl.IsVisible = !compact;
            }
        };

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

        // Live RetroAchievements HUD Layer
        var raHudLayer = new Grid { IsHitTestVisible = false, Margin = new Thickness(14) };

        _raBannerTitle = new TextBlock { FontSize = 13, FontWeight = FontWeight.Black, Foreground = Brushes.White, HorizontalAlignment = HorizontalAlignment.Center };
        _raBannerSub = new TextBlock { FontSize = 11.5, FontWeight = FontWeight.SemiBold, Foreground = new SolidColorBrush(Color.Parse("#E2E8F0")), HorizontalAlignment = HorizontalAlignment.Center };
        _raBannerCard = new Border
        {
            IsVisible = false,
            HorizontalAlignment = HorizontalAlignment.Center,
            VerticalAlignment = VerticalAlignment.Top,
            Padding = new Thickness(18, 9),
            CornerRadius = new CornerRadius(12),
            Background = new SolidColorBrush(Color.FromArgb(230, 11, 15, 25)),
            BorderThickness = new Thickness(1.5),
            Child = new StackPanel
            {
                Spacing = 2,
                Children = { _raBannerTitle, _raBannerSub }
            }
        };
        raHudLayer.Children.Add(_raBannerCard);

        _raToastBadge = new TextBlock { FontSize = 10, FontWeight = FontWeight.Black };
        _raToastTitle = new TextBlock { FontSize = 12.5, FontWeight = FontWeight.Bold, Foreground = Brushes.White, TextWrapping = TextWrapping.Wrap };
        _raToastDesc = new TextBlock { FontSize = 11, Foreground = new SolidColorBrush(Color.Parse("#CBD5E1")), TextWrapping = TextWrapping.Wrap };
        _raToastCard = new Border
        {
            IsVisible = false,
            MaxWidth = 340,
            HorizontalAlignment = HorizontalAlignment.Right,
            VerticalAlignment = VerticalAlignment.Top,
            Padding = new Thickness(14, 10),
            CornerRadius = new CornerRadius(12),
            Background = new SolidColorBrush(Color.FromArgb(236, 15, 23, 42)),
            BorderBrush = new SolidColorBrush(Color.Parse("#FACC15")),
            BorderThickness = new Thickness(1.5),
            Child = new StackPanel
            {
                Spacing = 3,
                Children =
                {
                    new StackPanel
                    {
                        Orientation = Orientation.Horizontal,
                        Spacing = 6,
                        Children =
                        {
                            new AuroraVectorIcon { Kind = AuroraIconKind.TrophyCrown, Size = 15, VerticalAlignment = VerticalAlignment.Center },
                            _raToastBadge
                        }
                    },
                    _raToastTitle,
                    _raToastDesc
                }
            }
        };
        raHudLayer.Children.Add(_raToastCard);

        var statusBottomStack = new StackPanel
        {
            Spacing = 6,
            HorizontalAlignment = HorizontalAlignment.Left,
            VerticalAlignment = VerticalAlignment.Bottom
        };

        _raChallengeText = new TextBlock { FontSize = 11, FontWeight = FontWeight.Bold, Foreground = new SolidColorBrush(Color.Parse("#FDE047")) };
        _raChallengePill = new Border
        {
            IsVisible = false,
            Padding = new Thickness(10, 5),
            CornerRadius = new CornerRadius(8),
            Background = new SolidColorBrush(Color.FromArgb(220, 30, 27, 15)),
            BorderBrush = new SolidColorBrush(Color.Parse("#FACC15")),
            BorderThickness = new Thickness(1),
            Child = _raChallengeText
        };
        statusBottomStack.Children.Add(_raChallengePill);

        _raMeasuredText = new TextBlock { FontSize = 11, FontWeight = FontWeight.SemiBold, Foreground = new SolidColorBrush(Color.Parse("#BAE6FD")) };
        _raMeasuredPill = new Border
        {
            IsVisible = false,
            Padding = new Thickness(10, 5),
            CornerRadius = new CornerRadius(8),
            Background = new SolidColorBrush(Color.FromArgb(220, 12, 24, 38)),
            BorderBrush = new SolidColorBrush(Color.Parse("#38BDF8")),
            BorderThickness = new Thickness(1),
            Child = _raMeasuredText
        };
        statusBottomStack.Children.Add(_raMeasuredPill);

        _raLeaderboardText = new TextBlock { FontSize = 11, FontWeight = FontWeight.SemiBold, Foreground = new SolidColorBrush(Color.Parse("#DDD6FE")) };
        _raLeaderboardPill = new Border
        {
            IsVisible = false,
            Padding = new Thickness(10, 5),
            CornerRadius = new CornerRadius(8),
            Background = new SolidColorBrush(Color.FromArgb(220, 24, 16, 42)),
            BorderBrush = new SolidColorBrush(Color.Parse("#A855F7")),
            BorderThickness = new Thickness(1),
            Child = _raLeaderboardText
        };
        statusBottomStack.Children.Add(_raLeaderboardPill);

        raHudLayer.Children.Add(statusBottomStack);
        _stageHost.Children.Add(raHudLayer);

        _raBannerTimer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(4) };
        _raBannerTimer.Tick += (_, _) => { _raBannerTimer.Stop(); _raBannerCard.IsVisible = false; };

        _raToastTimer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(4.5) };
        _raToastTimer.Tick += (_, _) => { _raToastTimer.Stop(); _raToastCard.IsVisible = false; };

        _raMeasuredTimer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(4) };
        _raMeasuredTimer.Tick += (_, _) => { _raMeasuredTimer.Stop(); _raMeasuredPill.IsVisible = false; };

        _raLeaderboardTimer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(4) };
        _raLeaderboardTimer.Tick += (_, _) => { _raLeaderboardTimer.Stop(); _raLeaderboardPill.IsVisible = false; };

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
        StormGbcaUiState.Instance.SettingsSaved += () => Dispatcher.UIThread.Post(UpdateHardcoreHudButtons);
        StormGbcaUiState.Instance.SkinOrOverlayChanged += () => InvalidateStageVisuals(force: true);
        StormGbcaUiState.Instance.FullscreenChanged += fs =>
        {
            _hudBar.IsVisible = !fs;
            InvalidateStageVisuals(force: true);
        };

        var emu = StormGbcaUiState.Instance.Emulator;
        emu.RaSessionActivated += (session, hardcore) => Dispatcher.UIThread.Post(() =>
        {
            UpdateHardcoreHudButtons();
            ShowStartupBanner(session, hardcore);
        });
        emu.RaAchievementUnlocked += (ach, hardcore) => Dispatcher.UIThread.Post(() => ShowUnlockToast(ach, hardcore));
        emu.RaChallengePrimedChanged += (_, title, isPrimed) => Dispatcher.UIThread.Post(() => UpdateChallengePill(title, isPrimed));
        emu.RaMeasuredProgressUpdated += (_, title, val, target, fmt) => Dispatcher.UIThread.Post(() => ShowMeasuredPill(title, val, target, fmt));
        emu.RaLeaderboardUpdated += (_, title, score, fmt, type) => Dispatcher.UIThread.Post(() => UpdateLeaderboardPill(title, score, fmt, type));

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
        _raBannerCard.IsVisible = false;
        _raToastCard.IsVisible = false;
        _raChallengePill.IsVisible = false;
        _raMeasuredPill.IsVisible = false;
        _raLeaderboardPill.IsVisible = false;
        UpdateHardcoreHudButtons();
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
        _raBannerTimer.Stop();
        _raToastTimer.Stop();
        _raMeasuredTimer.Stop();
        _raLeaderboardTimer.Stop();
        _raBannerCard.IsVisible = false;
        _raToastCard.IsVisible = false;
        _raChallengePill.IsVisible = false;
        _raMeasuredPill.IsVisible = false;
        _raLeaderboardPill.IsVisible = false;
        _bootSplash.Dismiss();
        _pauseOverlay.IsVisible = false;
    }

    private void UpdateHardcoreHudButtons()
    {
        bool hc = StormGbcaUiState.Instance.Emulator.IsHardcoreMode;
        _rwBtn.IsEnabled = !hc;
        _rwBtn.Opacity = hc ? 0.42 : 1.0;
        ToolTip.SetTip(_rwBtn, hc ? "Перемотка отключена в режиме Hardcore RetroAchievements" : "Перемотка");

        _ldBtn.IsEnabled = !hc;
        _ldBtn.Opacity = hc ? 0.42 : 1.0;
        ToolTip.SetTip(_ldBtn, hc ? "Загрузка состояний отключена в режиме Hardcore RetroAchievements" : "Загрузить (F8)");
    }

    private void ShowStartupBanner(RaGameSessionInfo session, bool hardcore)
    {
        Color accent = hardcore ? Color.Parse("#FACC15") : Color.Parse("#38BDF8");
        _raBannerCard.BorderBrush = new SolidColorBrush(accent);
        _raBannerTitle.Foreground = new SolidColorBrush(accent);
        _raBannerTitle.Text = hardcore ? "RetroAchievements - Hardcore" : "RetroAchievements - Casual";
        _raBannerSub.Text = $"{session.GameTitle}  |  {session.UnlockedCount} / {session.TotalCount}";
        _raBannerCard.IsVisible = true;
        _raBannerTimer.Stop();
        _raBannerTimer.Start();
    }

    private void ShowUnlockToast(RaAchievementInfo ach, bool hardcore)
    {
        if (!StormGbcaUiState.Instance.Settings.RetroAchievementsNotifications) return;
        Color accent = hardcore ? Color.Parse("#FACC15") : Color.Parse("#38BDF8");
        _raToastCard.BorderBrush = new SolidColorBrush(accent);
        _raToastBadge.Foreground = new SolidColorBrush(accent);
        _raToastBadge.Text = hardcore
            ? $"TROPHY UNLOCKED [HARDCORE] (+{ach.Points} pts)"
            : $"TROPHY UNLOCKED [CASUAL] (+{ach.Points} pts)";
        _raToastTitle.Text = ach.Title;
        _raToastDesc.Text = ach.Description;
        _raToastCard.IsVisible = true;
        _raToastTimer.Stop();
        _raToastTimer.Start();
        AuroraTactileFeedbackService.Instance.PlayAchievementChime();
    }

    private void UpdateChallengePill(string title, bool isPrimed)
    {
        if (!StormGbcaUiState.Instance.Settings.RetroAchievementsChallengeIndicators)
        {
            _raChallengePill.IsVisible = false;
            return;
        }
        var primed = StormGbcaUiState.Instance.Emulator.GetPrimedAchievementsSnapshot();
        if (primed.Count == 0 && !isPrimed)
        {
            _raChallengePill.IsVisible = false;
            return;
        }
        _raChallengeText.Text = primed.Count > 1
            ? $"Challenge Active ({primed.Count}): {title}"
            : $"Challenge Active: {title}";
        _raChallengePill.IsVisible = true;
    }

    private void ShowMeasuredPill(string title, int val, int target, string fmt)
    {
        if (!StormGbcaUiState.Instance.Settings.RetroAchievementsProgressIndicators) return;
        string progress = !string.IsNullOrWhiteSpace(fmt) ? fmt : $"{val}/{target}";
        _raMeasuredText.Text = $"{title}: {progress}";
        _raMeasuredPill.IsVisible = true;
        _raMeasuredTimer.Stop();
        _raMeasuredTimer.Start();
    }

    private void UpdateLeaderboardPill(string title, int score, string fmt, StormRaEventType type)
    {
        if (!StormGbcaUiState.Instance.Settings.RetroAchievementsShowLeaderboardPopups) return;
        string valText = !string.IsNullOrWhiteSpace(fmt) ? fmt : score.ToString();
        _raLeaderboardText.Text = type switch
        {
            StormRaEventType.LboardStarted => $"Leaderboard: {title}",
            StormRaEventType.LboardUpdated => $"{title}: {valText}",
            StormRaEventType.LboardTriggered => $"Leaderboard Submitted: {title} ({valText})",
            StormRaEventType.LboardCanceled => $"Leaderboard Canceled: {title}",
            _ => $"{title}: {valText}",
        };
        _raLeaderboardPill.IsVisible = true;
        _raLeaderboardTimer.Stop();
        if (type is StormRaEventType.LboardTriggered or StormRaEventType.LboardCanceled)
        {
            _raLeaderboardTimer.Start();
        }
    }

    private void OnUiTick(object? sender, EventArgs e)
    {
        if (!StormGbcaUiState.Instance.IsEmulationActive || StormGbcaUiState.Instance.IsPaused) return;

        var emu = StormGbcaUiState.Instance.Emulator;
        emu.PollRaEvents();
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
        _lastViewportRect = vp;
        var activeProf = settings.GetOverlayProfileForPlatform(plat);
        int rot = activeProf.ScreenRect?.RotationDeg ?? 0;
        bool rotSwap = rot == 90 || rot == 270;
        double imgW = rotSwap ? vp.Height : vp.Width;
        double imgH = rotSwap ? vp.Width : vp.Height;
        double imgX = vp.X + (vp.Width - imgW) * 0.5;
        double imgY = vp.Y + (vp.Height - imgH) * 0.5;

        _lcdImage.Width = imgW;
        _lcdImage.Height = imgH;
        _lcdImage.Margin = new Thickness(imgX, imgY, 0, 0);
        _lcdImage.RenderTransformOrigin = new RelativePoint(0.5, 0.5, RelativeUnit.Relative);
        _lcdImage.RenderTransform = rot != 0 ? new RotateTransform(rot) : null;

        var effectiveFilter = activeProf.LcdFilter != StormVideoFilter.LcdGrid
            ? activeProf.LcdFilter
            : settings.VideoFilter;
        var interp = effectiveFilter is StormVideoFilter.Nearest or StormVideoFilter.LcdGrid or StormVideoFilter.RgbSubpixel
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
        var vp = _lastViewportRect.Width > 0
            ? _lastViewportRect
            : new Rect(_lcdImage.Margin.Left, _lcdImage.Margin.Top, _lcdImage.Width, _lcdImage.Height);
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
        var settings = StormGbcaUiState.Instance.Settings;
        if (MatchesBindingOrDefault(e.Key, settings.KeyBindings, "QuickSave", Key.F5))
        {
            PerformQuickSave(_activeSlot);
            e.Handled = true;
            return;
        }
        if (MatchesBindingOrDefault(e.Key, settings.KeyBindings, "QuickLoad", Key.F8))
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

        if (MatchesBinding(e.Key, settings.KeyBindings, "FastForward"))
        {
            var emu = StormGbcaUiState.Instance.Emulator;
            if (!emu.IsFastForward)
            {
                emu.SetFastForward(true, settings.FastForwardMultiplier);
                _ffText.Text = emu.IsFastForward ? $"Ускорение ({emu.FastForwardMultiplier:0.#}x)" : "Ускорение (1x)";
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

    private static bool MatchesBindingOrDefault(Key key, Dictionary<string, string> map, string action, Key fallback)
    {
        if (map.TryGetValue(action, out string? name) && Enum.TryParse<Key>(name, true, out var parsed))
        {
            return parsed == key;
        }
        return key == fallback;
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
        _ffText.Text = emu.IsFastForward ? $"Ускорение ({emu.FastForwardMultiplier:0.#}x)" : "Ускорение (1x)";
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
        if (StormGbcaUiState.Instance.Emulator.IsHardcoreMode ||
            !StormGbcaUiState.Instance.RetroAchievements.IsFeatureAllowedInCurrentMode("loadstate"))
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
        if (StormGbcaUiState.Instance.Emulator.IsHardcoreMode ||
            !StormGbcaUiState.Instance.RetroAchievements.IsFeatureAllowedInCurrentMode("rewind"))
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
            Margin = new Thickness(0, 2, 6, 2),
            Padding = new Thickness(8, 2),
            Background = new SolidColorBrush(Color.FromArgb(40, accent.R, accent.G, accent.B)),
            BorderBrush = new SolidColorBrush(Color.FromArgb(100, accent.R, accent.G, accent.B)),
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(7),
            Cursor = new Cursor(StandardCursorType.Hand),
            VerticalAlignment = VerticalAlignment.Center
        };
        ToolTip.SetTip(btn, text);
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
