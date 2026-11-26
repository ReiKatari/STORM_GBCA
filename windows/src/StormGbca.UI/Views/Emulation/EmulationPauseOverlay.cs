// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Apache License, Version 2.0.

using Avalonia;
using Avalonia.Controls;
using Avalonia.Controls.Primitives;
using Avalonia.Input;
using Avalonia.Layout;
using Avalonia.Media;
using Avalonia.Platform.Storage;
using Storm.AuroraFluid.Kit.Controls;
using Storm.AuroraFluid.Kit.Icons;
using Storm.AuroraFluid.Kit.Services;
using StormGbca.Core.Configuration;
using StormGbca.Core.Engine;
using StormGbca.Core.Localization;
using StormGbca.Core.Native;
using StormGbca.Core.Paths;
using StormGbca.UI.Common;

namespace StormGbca.UI.Views.Emulation;

/// <summary>
/// Full-featured In-Game Quick Pause & Tuning Overlay:
/// 10 Savestate Slots with Screenshot Thumbnails, Rewind, Fast-Forward 1x..8x,
/// Instant Skin/Overlay switcher, Video/Color Correction/DMG Palettes, 6-Channel Audio Mixer,
/// Cheats, Solar LUX 0..10, Gyro/Tilt/Rumble, Core Switch (mGBA/SameBoy), IPS/UPS/BPS Patcher, Reset, Exit.
/// </summary>
public class EmulationPauseOverlay : Border
{
    private readonly StackPanel _bodyPanel;
    private readonly WrapPanel _tabBar;
    private readonly TextBlock _bannerGameTitle;
    private readonly TextBlock _bannerTelemetry;
    private int _activeTab;
    private DateTime _sessionOpenedUtc = DateTime.UtcNow;

    public event Action? ResumeRequested;
    public event Action? StopRequested;
    public event Action<int>? SaveSlotRequested;
    public event Action<int>? LoadSlotRequested;
    public event Action? RewindRequested;
    public event Action? ResetRequested;

    public EmulationPauseOverlay()
    {
        Background = new SolidColorBrush(Color.FromArgb(215, 8, 10, 18));
        HorizontalAlignment = HorizontalAlignment.Stretch;
        VerticalAlignment = VerticalAlignment.Stretch;

        var card = new AuroraGlassCard
        {
            Width = 760,
            MaxHeight = 620,
            Padding = new Thickness(22, 18),
            HorizontalAlignment = HorizontalAlignment.Center,
            VerticalAlignment = VerticalAlignment.Center,
            SpotlightEnabled = true
        };

        var root = new Grid { RowDefinitions = new RowDefinitions("Auto,Auto,*,Auto") };

        // STORM DS-Style Top Game Banner with PAUSED Badge + Live Telemetry
        var header = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto"), Margin = new Thickness(0, 0, 0, 10) };
        var titleStack = new StackPanel { Spacing = 4 };
        var titleRow = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8, VerticalAlignment = VerticalAlignment.Center };
        _bannerGameTitle = new TextBlock { Text = "STORM GBCA", FontSize = 16, FontWeight = FontWeight.Bold, Foreground = StormThemeBrushes.PrimaryText, VerticalAlignment = VerticalAlignment.Center };
        var pausedBadge = new Border
        {
            CornerRadius = new CornerRadius(6),
            Background = new SolidColorBrush(Color.Parse("#EF4444")),
            Padding = new Thickness(8, 2),
            VerticalAlignment = VerticalAlignment.Center,
            Child = new TextBlock { Text = GbcaLoc.T("PauseBadge"), FontSize = 10, FontWeight = FontWeight.Black, Foreground = Brushes.White }
        };
        titleRow.Children.Add(_bannerGameTitle);
        titleRow.Children.Add(pausedBadge);
        titleStack.Children.Add(titleRow);
        _bannerTelemetry = new TextBlock { Text = "ESC - Resume", FontSize = 11.5, Foreground = StormThemeBrushes.SecondaryText };
        titleStack.Children.Add(_bannerTelemetry);
        header.Children.Add(titleStack);

        var resumeBtn = new AuroraVolumetricButton { IsHero = true, Height = 36, Width = 155 };
        resumeBtn.Content = new TextBlock { Text = "Продолжить", FontSize = 12.5, FontWeight = FontWeight.Bold, Foreground = Brushes.White, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
        resumeBtn.Click += (_, _) => ResumeRequested?.Invoke();
        header.Children.Add(resumeBtn);
        Grid.SetColumn(resumeBtn, 1);
        root.Children.Add(header);

        // Tabs (WrapPanel: ZERO horizontal scrolling!)
        _tabBar = new WrapPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 0, 0, 10) };
        AddTab(0, GbcaLoc.T("PauseTabSlots"));
        AddTab(1, GbcaLoc.T("PauseTabSkins"));
        AddTab(2, GbcaLoc.T("PauseTabVideo"));
        AddTab(3, GbcaLoc.T("PauseTabAudio"));
        AddTab(4, GbcaLoc.T("PauseTabLink"));
        AddTab(5, GbcaLoc.T("PauseTabSensors"));
        AddTab(6, GbcaLoc.T("PauseTabRam"));
        root.Children.Add(_tabBar);
        Grid.SetRow(_tabBar, 1);

        var scroll = new ScrollViewer { MaxHeight = 390, Padding = new Thickness(0, 0, 10, 0) };
        _bodyPanel = new StackPanel { Spacing = 10 };
        scroll.Content = _bodyPanel;
        root.Children.Add(scroll);
        Grid.SetRow(scroll, 2);

        // Bottom bar
        var bottom = new Grid { ColumnDefinitions = new ColumnDefinitions("Auto,Auto,*,Auto"), Margin = new Thickness(0, 12, 0, 0), ColumnSpacing = 8 };
        var rwBtn = CreateActionPill("Перемотка назад", Color.Parse("#38BDF8"), () => RewindRequested?.Invoke());
        var rstBtn = CreateActionPill("Перезапуск", Color.Parse("#F59E0B"), () => ResetRequested?.Invoke());
        var exitBtn = CreateActionPill("Выйти в библиотеку", Color.Parse("#EF4444"), () => StopRequested?.Invoke());
        bottom.Children.Add(rwBtn);
        bottom.Children.Add(rstBtn);
        Grid.SetColumn(rstBtn, 1);
        bottom.Children.Add(exitBtn);
        Grid.SetColumn(exitBtn, 3);

        root.Children.Add(bottom);
        Grid.SetRow(bottom, 3);

        card.Content = root;
        Child = card;
        RefreshTab();
    }

    public void RefreshOnOpen()
    {
        var rom = StormGbcaUiState.Instance.SelectedRom;
        _bannerGameTitle.Text = rom?.GetDisplayTitle() ?? "STORM GBCA";
        var link = StormGbcaUiState.Instance.Emulator.GetLinkStatus();
        string linkTag = link.Mode == StormLinkMode.None ? "" : $"  |  LINK: {link.Mode} ({link.PlayerCount}P)";
        _bannerTelemetry.Text = $"{DateTime.Now:HH:mm}  |  {StormGbcaUiState.Instance.Emulator.ActivePlatform}  |  ESC: {GbcaLoc.T("MenuResume")}{linkTag}";
        RefreshTab();
    }

    private void AddTab(int idx, string title)
    {
        var b = new Border
        {
            Height = 30,
            Margin = new Thickness(0, 0, 6, 6),
            Padding = new Thickness(10, 4),
            CornerRadius = new CornerRadius(8),
            BorderThickness = new Thickness(1),
            Cursor = new Cursor(StandardCursorType.Hand),
            Child = new TextBlock { Text = title, FontSize = 11, FontWeight = FontWeight.SemiBold, VerticalAlignment = VerticalAlignment.Center }
        };
        b.PointerPressed += (_, _) =>
        {
            AuroraTactileFeedbackService.Instance.PlayClick();
            _activeTab = idx;
            RefreshTab();
        };
        _tabBar.Children.Add(b);
    }

    private void RefreshTab()
    {
        var accent = StormThemeBrushes.AccentPrimaryColor;
        for (int i = 0; i < _tabBar.Children.Count; i++)
        {
            if (_tabBar.Children[i] is Border b)
            {
                bool sel = i == _activeTab;
                b.Background = sel ? StormThemeBrushes.HoverSurface : StormThemeBrushes.RowSurface;
                b.BorderBrush = sel ? new SolidColorBrush(accent) : StormThemeBrushes.SubtleBorder;
                if (b.Child is TextBlock tb) tb.Foreground = sel ? StormThemeBrushes.PrimaryText : StormThemeBrushes.BodyText;
            }
        }

        _bodyPanel.Children.Clear();
        switch (_activeTab)
        {
            case 0: BuildSlotsTab(); break;
            case 1: BuildSkinOverlayTab(); break;
            case 2: BuildVideoTab(); break;
            case 3: BuildAudioMixerTab(); break;
            case 4: BuildLinkTab(); break;
            case 5: BuildSensorsCoreTab(); break;
            case 6: BuildRamFinderTab(); break;
        }
    }

    private void BuildSlotsTab()
    {
        string romKey = StormGbcaUiState.Instance.Emulator.ActiveRomKey;
        var grid = new UniformGrid { Columns = 2 };

        for (int i = 0; i < 10; i++)
        {
            int slot = i;
            string stPath = StormGbcaPaths.GetSaveStateFilePath(romKey, slot);
            string thumbPath = StormGbcaPaths.GetSaveStateThumbnailPath(romKey, slot);
            bool exists = File.Exists(stPath);

            var card = new Border
            {
                Margin = new Thickness(4),
                Padding = new Thickness(8),
                CornerRadius = new CornerRadius(10),
                Background = StormThemeBrushes.RowSurface,
                BorderBrush = StormThemeBrushes.SubtleBorder,
                BorderThickness = new Thickness(1)
            };

            var row = new Grid { ColumnDefinitions = new ColumnDefinitions("76,*,Auto") };
            var thumb = new Border
            {
                Width = 68,
                Height = 46,
                CornerRadius = new CornerRadius(6),
                Background = new SolidColorBrush(Color.FromArgb(140, 0, 0, 0)),
                ClipToBounds = true,
                Child = new Image
                {
                    Stretch = Stretch.Uniform,
                    Source = BitmapHelper.LoadBitmapCached(thumbPath)
                }
            };
            row.Children.Add(thumb);

            var info = new StackPanel { VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(8, 0), Spacing = 2 };
            info.Children.Add(new TextBlock { Text = $"Слот {slot + 1}", FontSize = 12, FontWeight = FontWeight.Bold, Foreground = StormThemeBrushes.PrimaryText });
            info.Children.Add(new TextBlock
            {
                Text = exists ? $"{File.GetLastWriteTime(stPath):dd.MM.yyyy HH:mm}" : "Пустой слот",
                FontSize = 10,
                Foreground = StormThemeBrushes.SecondaryText
            });
            row.Children.Add(info);
            Grid.SetColumn(info, 1);

            var btns = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 4, VerticalAlignment = VerticalAlignment.Center };
            btns.Children.Add(CreateMiniBtn("Сохранить", Color.Parse("#10B981"), () =>
            {
                SaveSlotRequested?.Invoke(slot);
                RefreshTab();
            }));
            btns.Children.Add(CreateMiniBtn("Загрузить", Color.Parse("#38BDF8"), () => LoadSlotRequested?.Invoke(slot)));
            row.Children.Add(btns);
            Grid.SetColumn(btns, 2);

            card.Child = row;
            grid.Children.Add(card);
        }
        _bodyPanel.Children.Add(grid);
    }

    private void BuildSkinOverlayTab()
    {
        var s = StormGbcaUiState.Instance.Settings;
        var plat = StormGbcaUiState.Instance.Emulator.ActivePlatform;
        var prof = s.GetOverlayProfileForPlatform(plat);

        _bodyPanel.Children.Add(new TextBlock { Text = $"Активная платформа: {plat}", FontSize = 12, FontWeight = FontWeight.Bold, Foreground = StormThemeBrushes.Accent });

        if (plat is StormPlatform.Gb or StormPlatform.Sgb)
        {
            _bodyPanel.Children.Add(CreateCombo("3D-корпус Game Boy:", Enum.GetNames<GbConsoleSkin>(), (int)s.GbConsoleSkin, idx =>
            {
                s.GbConsoleSkin = (GbConsoleSkin)idx;
                StormGbcaUiState.Instance.NotifySkinOrOverlayChanged();
            }));
        }
        else if (plat == StormPlatform.Gbc)
        {
            _bodyPanel.Children.Add(CreateCombo("3D-корпус Game Boy Color:", Enum.GetNames<GbcConsoleSkin>(), (int)s.GbcConsoleSkin, idx =>
            {
                s.GbcConsoleSkin = (GbcConsoleSkin)idx;
                StormGbcaUiState.Instance.NotifySkinOrOverlayChanged();
            }));
        }
        else
        {
            _bodyPanel.Children.Add(CreateCombo("3D-корпус Game Boy Advance:", Enum.GetNames<GbaConsoleSkin>(), (int)s.GbaConsoleSkin, idx =>
            {
                s.GbaConsoleSkin = (GbaConsoleSkin)idx;
                StormGbcaUiState.Instance.NotifySkinOrOverlayChanged();
            }));
        }

        _bodyPanel.Children.Add(CreateCombo("Рамка оверлея:", Enum.GetNames<OverlayBezelStyle>(), (int)prof.OverlayLayout, idx =>
        {
            prof.OverlayLayout = (OverlayBezelStyle)idx;
            StormGbcaUiState.Instance.NotifySkinOrOverlayChanged();
        }));

        _bodyPanel.Children.Add(CreateCombo("Стиль кнопок оверлея:", Enum.GetNames<OverlayButtonStyle>(), (int)prof.ButtonStyle, idx =>
        {
            prof.ButtonStyle = (OverlayButtonStyle)idx;
            StormGbcaUiState.Instance.NotifySkinOrOverlayChanged();
        }));

        var deckCheck = new AuroraNeonCheckBox { Label = "Показывать панель экранных кнопок", IsChecked = prof.ShowControlsDeck };
        deckCheck.IsCheckedChanged += (_, v) =>
        {
            prof.ShowControlsDeck = v;
            StormGbcaUiState.Instance.NotifySkinOrOverlayChanged();
        };
        _bodyPanel.Children.Add(deckCheck);

        var lrCheck = new AuroraNeonCheckBox { Label = "Показывать боковые триггеры L и R", IsChecked = prof.ShowLR };
        lrCheck.IsCheckedChanged += (_, v) =>
        {
            prof.ShowLR = v;
            StormGbcaUiState.Instance.NotifySkinOrOverlayChanged();
        };
        _bodyPanel.Children.Add(lrCheck);

        var dimCheck = new AuroraNeonCheckBox { Label = "Ночное затемнение 3D-корпуса", IsChecked = s.SkinNightDimming };
        dimCheck.IsCheckedChanged += (_, v) =>
        {
            s.SkinNightDimming = v;
            StormGbcaUiState.Instance.NotifySkinOrOverlayChanged();
        };
        _bodyPanel.Children.Add(dimCheck);
    }

    private void BuildVideoTab()
    {
        var s = StormGbcaUiState.Instance.Settings;
        _bodyPanel.Children.Add(CreateCombo("Увеличение разрешения (Internal Scale):", ["1x (Нативное)", "2x HD", "3x Full HD", "4x Ultra HD"], Math.Clamp(s.ResolutionScale - 1, 0, 3), idx =>
        {
            s.ResolutionScale = idx + 1;
            if (s.ResolutionScale > 1 && s.UpscaleMode == StormUpscaleMode.None) s.UpscaleMode = StormUpscaleMode.Xbrz;
            StormGbcaUiState.Instance.SaveSettings();
        }));
        _bodyPanel.Children.Add(CreateCombo("Алгоритм апскейлинга:", ["Nearest", "xBRZ HD", "Scale2x / EPX", "MMPX Sharp", "LCD Subpixel HD", "CRT Scanlines HD"], (int)s.UpscaleMode, idx =>
        {
            s.UpscaleMode = (StormUpscaleMode)idx;
            if (s.UpscaleMode != StormUpscaleMode.None && s.ResolutionScale < 2) s.ResolutionScale = 2;
            StormGbcaUiState.Instance.SaveSettings();
        }));
        _bodyPanel.Children.Add(CreateSlider("Разгон процессора (CPU Overclock):", 50, 400, s.CpuOverclockPct, "%", v =>
        {
            s.CpuOverclockPct = Math.Clamp(((int)v / 25) * 25, 50, 400);
            StormGbcaUiState.Instance.SaveSettings();
        }));
        _bodyPanel.Children.Add(CreateCombo("Пропуск кадров (Frame Skip):", ["Выкл (0)", "1", "2", "3", "4"], Math.Clamp(s.FrameSkip, 0, 4), idx =>
        {
            s.FrameSkip = idx;
            StormGbcaUiState.Instance.SaveSettings();
        }));
        _bodyPanel.Children.Add(CreateCombo("Цветокоррекция LCD:", Enum.GetNames<StormColorCorrectionMode>(), (int)s.ColorCorrectionMode, idx =>
        {
            s.ColorCorrectionMode = (StormColorCorrectionMode)idx;
            StormGbcaUiState.Instance.SaveSettings();
        }));
        _bodyPanel.Children.Add(CreateCombo("Видеофильтр:", Enum.GetNames<StormVideoFilter>(), (int)s.VideoFilter, idx =>
        {
            s.VideoFilter = (StormVideoFilter)idx;
            StormGbcaUiState.Instance.SaveSettings();
        }));
        var palNames = GbcaEmulatorSession.GetDmgPaletteNames().ToArray();
        _bodyPanel.Children.Add(CreateCombo("Палитра DMG (28 пресетов):", palNames, Math.Clamp(s.DmgPaletteIndex, 0, palNames.Length - 1), idx =>
        {
            s.DmgPaletteIndex = idx;
            StormGbcaUiState.Instance.SaveSettings();
        }));
        _bodyPanel.Children.Add(CreateSlider("Инерционность LCD (Ghosting):", 0, 100, s.LcdGhosting, "%", v =>
        {
            s.LcdGhosting = (int)v;
            StormGbcaUiState.Instance.SaveSettings();
        }));
        _bodyPanel.Children.Add(CreateSlider("Скорость ускорения (Fast-Forward):", 1, 8, s.FastForwardMultiplier, "x", v =>
        {
            s.FastForwardMultiplier = (float)v;
            StormGbcaUiState.Instance.SaveSettings();
        }));
    }

    private void BuildAudioMixerTab()
    {
        var s = StormGbcaUiState.Instance.Settings;
        _bodyPanel.Children.Add(CreateSlider("Общая громкость:", 0.0, 2.0, s.MasterVolume, "x", v =>
        {
            s.MasterVolume = (float)v;
            StormGbcaUiState.Instance.SaveSettings();
        }));
        string[] chNames =
        [
            "Канал 1: PSG Pulse 1 (Меандр 1)",
            "Канал 2: PSG Pulse 2 (Меандр 2)",
            "Канал 3: PSG Wave (Волновой)",
            "Канал 4: PSG Noise (Шумовой)",
            "Канал 5: GBA Direct Sound A",
            "Канал 6: GBA Direct Sound B"
        ];
        for (int i = 0; i < 6; i++)
        {
            int ch = i;
            _bodyPanel.Children.Add(CreateSlider(chNames[ch], 0.0, 1.5, s.ChannelVolumes[ch], "x", v =>
            {
                s.ChannelVolumes[ch] = (float)v;
                StormGbcaUiState.Instance.SaveSettings();
            }));
        }
    }

    private void BuildLinkTab()
    {
        var s = StormGbcaUiState.Instance.Settings;
        var emu = StormGbcaUiState.Instance.Emulator;
        var st = emu.GetLinkStatus();
        _bodyPanel.Children.Add(new TextBlock
        {
            Text = st.Mode == StormLinkMode.None
                ? $"Link Status: {GbcaLoc.T("MpDisconnected")}"
                : $"Link Active: {st.Mode} | Players: {st.PlayerCount} | Port: {st.Port} | {st.StatusText}",
            FontSize = 12,
            FontWeight = FontWeight.Bold,
            Foreground = st.Connected ? new SolidColorBrush(Color.Parse("#34D399")) : StormThemeBrushes.Accent
        });

        var quickWrap = new WrapPanel { Orientation = Orientation.Horizontal };
        void AddQuick(string label, Color col, Action act)
        {
            var p = CreateActionPill(label, col, act);
            p.Margin = new Thickness(0, 0, 8, 8);
            quickWrap.Children.Add(p);
        }
        AddQuick("Host LAN Link (2P-4P)", Color.Parse("#10B981"), () => { emu.StartLink(StormLinkMode.LanHost, "", s.MultiplayerPort, 0, s.MultiplayerRoomName); RefreshTab(); });
        AddQuick("Join LAN (" + s.MultiplayerLastHostIp + ")", Color.Parse("#38BDF8"), () => { emu.StartLink(StormLinkMode.LanClient, s.MultiplayerLastHostIp, s.MultiplayerPort, 0, s.MultiplayerRoomName); RefreshTab(); });
        AddQuick("GBA Wireless Host (RFU)", Color.Parse("#8B5CF6"), () => { emu.StartLink(StormLinkMode.WirelessHost, "", s.MultiplayerPort, 0, s.MultiplayerRoomName); RefreshTab(); });
        AddQuick("GBC IR Host", Color.Parse("#F59E0B"), () => { emu.StartLink(StormLinkMode.IrHost, "", s.MultiplayerPort, 0, s.MultiplayerRoomName); RefreshTab(); });
        AddQuick("Dolphin JoyBus (GC)", Color.Parse("#06B6D4"), () => { emu.StartLink(StormLinkMode.DolphinJoyBus, s.DolphinIp, s.DolphinDataPort, s.DolphinClockPort, "Dolphin"); RefreshTab(); });
        AddQuick("Game Boy Printer (PNG)", Color.Parse("#EC4899"), () => { emu.StartLink(StormLinkMode.GbPrinter, "", 0, 0, "GBPrinter"); RefreshTab(); });
        AddQuick("Local Loopback", Color.Parse("#22C55E"), () => { emu.StartLink(StormLinkMode.LocalLoopback, "", 0, 0, "Loopback"); RefreshTab(); });
        if (st.Mode != StormLinkMode.None)
        {
            AddQuick(GbcaLoc.T("MpBtnDisconnect"), Color.Parse("#EF4444"), () => { emu.StopLink(); RefreshTab(); });
        }
        _bodyPanel.Children.Add(quickWrap);
    }

    private void BuildSensorsCoreTab()
    {
        var s = StormGbcaUiState.Instance.Settings;
        var emu = StormGbcaUiState.Instance.Emulator;

        _bodyPanel.Children.Add(CreateSlider("Солнечный датчик Boktai (Solar LUX):", 0, 10, s.SolarLuxLevel, "Lux", v =>
        {
            s.SolarLuxLevel = (int)v;
            emu.SetSolarLux(s.SolarLuxLevel);
            StormGbcaUiState.Instance.SaveSettings();
        }));

        _bodyPanel.Children.Add(CreateSlider("Наклон датчика Tilt X:", -1.0, 1.0, 0.0, "G", v => emu.SetTilt((float)v, 0f)));
        _bodyPanel.Children.Add(CreateSlider("Гироскоп Gyro Z (WarioWare Twisted):", -1.0, 1.0, 0.0, "rad", v => emu.SetGyro((float)v)));

        _bodyPanel.Children.Add(CreateCombo("Ядро Game Boy:", new[] { "mGBA", "SameBoy" }, (int)s.PreferredCoreGb, idx =>
        {
            s.PreferredCoreGb = (StormCoreBackend)idx;
            StormGbcaUiState.Instance.SaveSettings();
        }));
        _bodyPanel.Children.Add(CreateCombo("Ядро Game Boy Color:", new[] { "mGBA", "SameBoy" }, (int)s.PreferredCoreGbc, idx =>
        {
            s.PreferredCoreGbc = (StormCoreBackend)idx;
            StormGbcaUiState.Instance.SaveSettings();
        }));

        var row = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8, Margin = new Thickness(0, 6, 0, 0) };
        row.Children.Add(CreateActionPill("Применить патч IPS/UPS/BPS...", Color.Parse("#8B5CF6"), async () =>
        {
            var top = TopLevel.GetTopLevel(this);
            if (top?.StorageProvider == null) return;
            var files = await top.StorageProvider.OpenFilePickerAsync(new FilePickerOpenOptions
            {
                Title = "Выберите файл патча (.ips, .ups, .bps)",
                AllowMultiple = false,
                FileTypeFilter = [new FilePickerFileType("ROM Patches") { Patterns = ["*.ips", "*.ups", "*.bps"] }]
            });
            string? path = files.Count > 0 ? files[0].TryGetLocalPath() : null;
            if (!string.IsNullOrWhiteSpace(path)) emu.ApplyPatchFile(path);
        }));
        row.Children.Add(CreateActionPill("Менеджер читов", Color.Parse("#10B981"), () =>
        {
            var rom = StormGbcaUiState.Instance.SelectedRom;
            if (rom != null) StormGbcaUiState.Instance.RequestRomModal(rom, 1);
        }));
        _bodyPanel.Children.Add(row);
    }

    private StormMemoryRegion _ramRegion = StormMemoryRegion.Iwram;
    private int _ramValSize = 1;
    private string _ramSearchInput = "0";
    private string _ramWriteInput = "99";
    private string _ramStatus = "Нажмите 'Новый поиск' для создания снимка оперативной памяти";
    private List<(uint Offset, uint Value)> _ramHits = [];

    private void BuildRamFinderTab()
    {
        var emu = StormGbcaUiState.Instance.Emulator;
        bool isGba = emu.ActivePlatform == StormPlatform.Gba;
        StormMemoryRegion[] regions = isGba
            ? [StormMemoryRegion.Iwram, StormMemoryRegion.Ewram, StormMemoryRegion.CartRam]
            : [StormMemoryRegion.Wram, StormMemoryRegion.Hram, StormMemoryRegion.CartRam];
        string[] regionNames = isGba
            ? ["IWRAM (32 КБ)", "EWRAM (256 КБ)", "SRAM"]
            : ["WRAM (8 КБ)", "HRAM", "SRAM"];
        int regIdx = Array.IndexOf(regions, _ramRegion);
        if (regIdx < 0) { regIdx = 0; _ramRegion = regions[0]; }

        _bodyPanel.Children.Add(new TextBlock { Text = _ramStatus, FontSize = 11.5, FontWeight = FontWeight.SemiBold, Foreground = StormThemeBrushes.Accent });

        _bodyPanel.Children.Add(CreateCombo("Область памяти:", regionNames, regIdx, idx => _ramRegion = regions[idx]));
        int szIdx = _ramValSize == 4 ? 2 : (_ramValSize == 2 ? 1 : 0);
        _bodyPanel.Children.Add(CreateCombo("Размер значения:", ["8 бит (1 байт)", "16 бит (2 байта)", "32 бита (4 байта)"], szIdx, idx => _ramValSize = idx == 2 ? 4 : (idx == 1 ? 2 : 1)));

        var inputs = new Grid { ColumnDefinitions = new ColumnDefinitions("*,*"), ColumnSpacing = 10 };
        var searchBox = new TextBox { Text = _ramSearchInput, PlaceholderText = "Значение поиска", Height = 30 };
        searchBox.TextChanged += (_, _) => _ramSearchInput = searchBox.Text ?? "0";
        var writeBox = new TextBox { Text = _ramWriteInput, PlaceholderText = "Значение записи", Height = 30 };
        writeBox.TextChanged += (_, _) => _ramWriteInput = writeBox.Text ?? "99";
        inputs.Children.Add(searchBox);
        inputs.Children.Add(writeBox);
        Grid.SetColumn(writeBox, 1);
        _bodyPanel.Children.Add(inputs);

        var row1 = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 6 };
        row1.Children.Add(CreateActionPill("Новый поиск", Color.Parse("#10B981"), () =>
        {
            int n = emu.MemSearchReset(_ramRegion, _ramValSize);
            _ramHits = n <= 64 ? emu.MemSearchGetResults(48) : [];
            _ramStatus = $"Снимок создан: {n} адресов";
            RefreshTab();
        }));
        row1.Children.Add(CreateActionPill("Равно", Color.Parse("#38BDF8"), () => RunRamFilter(StormSearchCmp.Eq)));
        row1.Children.Add(CreateActionPill("Больше", Color.Parse("#38BDF8"), () => RunRamFilter(StormSearchCmp.Gt)));
        row1.Children.Add(CreateActionPill("Меньше", Color.Parse("#38BDF8"), () => RunRamFilter(StormSearchCmp.Lt)));
        row1.Children.Add(CreateActionPill("Увеличилось", Color.Parse("#A855F7"), () => RunRamFilter(StormSearchCmp.Increased)));
        row1.Children.Add(CreateActionPill("Уменьшилось", Color.Parse("#F59E0B"), () => RunRamFilter(StormSearchCmp.Decreased)));
        _bodyPanel.Children.Add(row1);

        foreach (var hit in _ramHits.Take(24))
        {
            uint baseAddr = isGba
                ? (_ramRegion == StormMemoryRegion.Ewram ? 0x02000000u : 0x03000000u)
                : (_ramRegion == StormMemoryRegion.Hram ? 0xFF80u : (_ramRegion == StormMemoryRegion.CartRam ? 0xA000u : 0xC000u));
            uint fullAddr = baseAddr + hit.Offset;
            string hexAddr = isGba ? $"0x{fullAddr:X8}" : $"0x{fullAddr:X4}";

            var rCard = new Border
            {
                Padding = new Thickness(10, 6),
                CornerRadius = new CornerRadius(8),
                Background = StormThemeBrushes.RowSurface,
                BorderBrush = StormThemeBrushes.SubtleBorder,
                BorderThickness = new Thickness(1)
            };
            var g = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto") };
            g.Children.Add(new TextBlock
            {
                Text = $"{hexAddr}  |  Текущее: {hit.Value} (0x{hit.Value:X})",
                FontSize = 11.5,
                FontFamily = new FontFamily("Consolas, monospace"),
                Foreground = StormThemeBrushes.PrimaryText,
                VerticalAlignment = VerticalAlignment.Center
            });
            var wBtn = CreateMiniBtn("Записать", Color.Parse("#10B981"), () =>
            {
                uint wv = uint.TryParse(_ramWriteInput.Trim(), out uint parsed) ? parsed : 0;
                if (emu.WriteMemoryValue(_ramRegion, hit.Offset, wv, _ramValSize))
                {
                    _ramStatus = $"Записано {wv} по адресу {hexAddr}";
                    _ramHits = emu.MemSearchGetResults(48);
                    RefreshTab();
                }
            });
            g.Children.Add(wBtn);
            Grid.SetColumn(wBtn, 1);
            rCard.Child = g;
            _bodyPanel.Children.Add(rCard);
        }
    }

    private void RunRamFilter(StormSearchCmp op)
    {
        uint val = uint.TryParse(_ramSearchInput.Trim(), out uint parsed) ? parsed : 0;
        int n = StormGbcaUiState.Instance.Emulator.MemSearchFilter(op, val);
        _ramHits = StormGbcaUiState.Instance.Emulator.MemSearchGetResults(48);
        _ramStatus = $"Найдено совпадений: {n}";
        RefreshTab();
    }

    private static Border CreateMiniBtn(string text, Color accent, Action onClick)
    {
        var b = new Border
        {
            Height = 26,
            Padding = new Thickness(8, 2),
            CornerRadius = new CornerRadius(6),
            Background = new SolidColorBrush(Color.FromArgb(45, accent.R, accent.G, accent.B)),
            BorderBrush = new SolidColorBrush(accent),
            BorderThickness = new Thickness(1),
            Cursor = new Cursor(StandardCursorType.Hand),
            Child = new TextBlock { Text = text, FontSize = 10, FontWeight = FontWeight.Bold, Foreground = Brushes.White, VerticalAlignment = VerticalAlignment.Center }
        };
        b.PointerPressed += (_, _) =>
        {
            AuroraTactileFeedbackService.Instance.PlayClick();
            onClick();
        };
        return b;
    }

    private static Border CreateActionPill(string text, Color accent, Action onClick)
    {
        var b = new Border
        {
            Height = 32,
            Padding = new Thickness(12, 4),
            CornerRadius = new CornerRadius(8),
            Background = new SolidColorBrush(Color.FromArgb(45, accent.R, accent.G, accent.B)),
            BorderBrush = new SolidColorBrush(accent),
            BorderThickness = new Thickness(1),
            Cursor = new Cursor(StandardCursorType.Hand),
            Child = new TextBlock { Text = text, FontSize = 11.5, FontWeight = FontWeight.SemiBold, Foreground = Brushes.White, VerticalAlignment = VerticalAlignment.Center }
        };
        b.PointerPressed += (_, _) =>
        {
            AuroraTactileFeedbackService.Instance.PlayClick();
            onClick();
        };
        return b;
    }

    private static Grid CreateCombo(string label, string[] options, int selectedIdx, Action<int> onChanged)
    {
        var g = new Grid { ColumnDefinitions = new ColumnDefinitions("*,230"), Margin = new Thickness(0, 2) };
        g.Children.Add(new TextBlock { Text = label, FontSize = 11.5, Foreground = StormThemeBrushes.BodyText, VerticalAlignment = VerticalAlignment.Center });
        var combo = new ComboBox { ItemsSource = options, SelectedIndex = Math.Clamp(selectedIdx, 0, Math.Max(0, options.Length - 1)), HorizontalAlignment = HorizontalAlignment.Stretch };
        combo.SelectionChanged += (_, _) =>
        {
            if (combo.SelectedIndex >= 0) onChanged(combo.SelectedIndex);
        };
        g.Children.Add(combo);
        Grid.SetColumn(combo, 1);
        return g;
    }

    private static Grid CreateSlider(string label, double min, double max, double val, string unit, Action<double> onChanged)
    {
        var g = new Grid { RowDefinitions = new RowDefinitions("Auto,Auto"), Margin = new Thickness(0, 2) };
        var lbl = new TextBlock { Text = $"{label} {val:0.##} {unit}", FontSize = 11, Foreground = StormThemeBrushes.BodyText };
        g.Children.Add(lbl);
        var sld = new AuroraVolumetricSlider { Minimum = min, Maximum = max, Value = Math.Clamp(val, min, max), Height = 22 };
        sld.ValueChanged += (_, v) =>
        {
            lbl.Text = $"{label} {v:0.##} {unit}";
            onChanged(v);
        };
        g.Children.Add(sld);
        Grid.SetRow(sld, 1);
        return g;
    }
}
