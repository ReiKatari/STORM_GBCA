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
    private readonly StackPanel _tabBar;
    private int _activeTab;

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

        // Header
        var header = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto"), Margin = new Thickness(0, 0, 0, 10) };
        var titleStack = new StackPanel { Spacing = 2 };
        titleStack.Children.Add(new TextBlock { Text = "ПАУЗА И БЫСТРАЯ НАСТРОЙКА", FontSize = 16, FontWeight = FontWeight.Bold, Foreground = StormThemeBrushes.Accent });
        titleStack.Children.Add(new TextBlock { Text = "Нажмите Escape для возврата в игру", FontSize = 11.5, Foreground = StormThemeBrushes.SecondaryText });
        header.Children.Add(titleStack);

        var resumeBtn = new AuroraVolumetricButton { IsHero = true, Height = 36, Width = 155 };
        resumeBtn.Content = new TextBlock { Text = "Продолжить", FontSize = 12.5, FontWeight = FontWeight.Bold, Foreground = Brushes.White, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
        resumeBtn.Click += (_, _) => ResumeRequested?.Invoke();
        header.Children.Add(resumeBtn);
        Grid.SetColumn(resumeBtn, 1);
        root.Children.Add(header);

        // Tabs
        _tabBar = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 6, Margin = new Thickness(0, 0, 0, 12) };
        AddTab(0, "Сохранения (10)");
        AddTab(1, "Корпус и оверлей");
        AddTab(2, "Видео и палитры");
        AddTab(3, "Аудиомикшер (6 кан.)");
        AddTab(4, "Сенсоры, читы и ядро");
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

    public void RefreshOnOpen() => RefreshTab();

    private void AddTab(int idx, string title)
    {
        var b = new Border
        {
            Height = 30,
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
            case 4: BuildSensorsCoreTab(); break;
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
