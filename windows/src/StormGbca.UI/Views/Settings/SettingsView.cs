// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Apache License, Version 2.0.

using Avalonia;
using Avalonia.Controls;
using Avalonia.Controls.Primitives;
using Avalonia.Input;
using Avalonia.Layout;
using Avalonia.Media;
using Avalonia.Platform.Storage;
using Avalonia.Threading;
using Storm.AuroraFluid.Kit.Controls;
using Storm.AuroraFluid.Kit.Icons;
using Storm.AuroraFluid.Kit.Services;
using Storm.AuroraFluid.Kit.Themes;
using StormGbca.Core.Configuration;
using StormGbca.Core.Engine;
using StormGbca.Core.Localization;
using StormGbca.Core.Native;
using StormGbca.UI.Common;

namespace StormGbca.UI.Views.Settings;

/// <summary>
/// Full Settings View covering Core (mGBA/SameBoy + BIOS + Run-Ahead + Rewind),
/// Video (7-mode Color Correction + 28 DMG Palettes + SGB Borders + LCD Ghosting + Filters),
/// Audio (48kHz + High-Pass + Hermite + DRC + 6-Channel Mixer),
/// Per-System 3D Skins & Overlays, Input (Keyboard with glossy 3D physical key badges + XInput),
/// RetroAchievements, and 9 STORM Themes / 13 Accents / 10 Fonts / 11 Languages / Dual-Mirror Updater.
/// </summary>
public class SettingsView : Grid
{
    private readonly AuroraSearchBox _searchBox;
    private readonly StackPanel _tabsRail;
    private readonly ContentControl _contentPresenter;
    private readonly TextBlock _savedFeedbackText;
    private readonly List<(int Index, AuroraIconKind Icon, string Title, Border Button)> _tabButtons = [];
    private int _activeTabIndex;
    private string _filterQuery = string.Empty;
    private string? _listeningActionKey;

    public SettingsView()
    {
        Margin = new Thickness(16, 8, 16, 12);
        RowDefinitions = new RowDefinitions("Auto,*");
        ColumnDefinitions = new ColumnDefinitions("240,*");

        _searchBox = new AuroraSearchBox
        {
            PlaceholderText = "Поиск настроек...",
            HotkeyHint = "Ctrl+F",
            Height = 36,
            Margin = new Thickness(0, 0, 14, 10)
        };
        _searchBox.TextChanged += (_, _) =>
        {
            _filterQuery = _searchBox.Text ?? string.Empty;
            LoadTab(_activeTabIndex);
        };
        Children.Add(_searchBox);

        var topRight = new StackPanel
        {
            Orientation = Orientation.Horizontal,
            Spacing = 12,
            HorizontalAlignment = HorizontalAlignment.Right,
            VerticalAlignment = VerticalAlignment.Center,
            Margin = new Thickness(0, 0, 0, 10)
        };
        _savedFeedbackText = new TextBlock
        {
            Text = string.Empty,
            FontSize = 11.5,
            FontWeight = FontWeight.SemiBold,
            Foreground = new SolidColorBrush(Color.Parse("#10B981")),
            VerticalAlignment = VerticalAlignment.Center,
            IsVisible = false
        };
        topRight.Children.Add(_savedFeedbackText);

        var savePill = new Border
        {
            Height = 36,
            CornerRadius = new CornerRadius(8),
            Background = new SolidColorBrush(Color.FromArgb(40, 16, 185, 129)),
            BorderBrush = new SolidColorBrush(Color.Parse("#10B981")),
            BorderThickness = new Thickness(1),
            Padding = new Thickness(14, 0),
            Cursor = new Cursor(StandardCursorType.Hand)
        };
        var spStack = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 6, VerticalAlignment = VerticalAlignment.Center };
        spStack.Children.Add(new AuroraVectorIcon { Kind = AuroraIconKind.CheckSuccess, Size = 14, VerticalAlignment = VerticalAlignment.Center });
        spStack.Children.Add(new TextBlock { Text = "Сохранить настройки", FontSize = 11.5, FontWeight = FontWeight.Bold, Foreground = new SolidColorBrush(Color.Parse("#10B981")), VerticalAlignment = VerticalAlignment.Center });
        savePill.Child = spStack;
        savePill.PointerPressed += (_, _) =>
        {
            AuroraTactileFeedbackService.Instance.PlayAchievementChime();
            CommitSave(showBanner: true);
        };
        topRight.Children.Add(savePill);
        Children.Add(topRight);
        Grid.SetColumn(topRight, 1);

        var tabsScroll = new ScrollViewer { Padding = new Thickness(0, 0, 10, 0), HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled };
        _tabsRail = new StackPanel { Spacing = 4 };
        AddTabHeader(0, AuroraIconKind.SettingsCore, "Ядра, BIOS и Run-Ahead");
        AddTabHeader(1, AuroraIconKind.ShaderPrism, "Видео, палитры и LCD");
        AddTabHeader(2, AuroraIconKind.AudioWaveDsp, "Звук и 6-канальный микшер");
        AddTabHeader(3, AuroraIconKind.DualScreenLayout, "3D-корпусы и оверлеи");
        AddTabHeader(4, AuroraIconKind.GamepadController, "Клавиатура, XInput и сенсоры");
        AddTabHeader(5, AuroraIconKind.TrophyCrown, "RetroAchievements");
        AddTabHeader(6, AuroraIconKind.ThemePalette, "Темы, шрифты и обновления");
        tabsScroll.Content = _tabsRail;
        Children.Add(tabsScroll);
        Grid.SetRow(tabsScroll, 1);

        var glassCard = new AuroraGlassCard { SpotlightEnabled = true, Margin = new Thickness(6, 0, 0, 0) };
        _contentPresenter = new ContentControl();
        glassCard.Content = _contentPresenter;
        Children.Add(glassCard);
        Grid.SetRow(glassCard, 1);
        Grid.SetColumn(glassCard, 1);

        KeyDown += OnKeyCaptureKeyDown;
        StormThemeBrushes.BindTheme(this, () => LoadTab(_activeTabIndex));
        LoadTab(0);
    }

    private void CommitSave(bool showBanner = false)
    {
        StormGbcaUiState.Instance.SaveSettings(showBanner);
        _savedFeedbackText.Text = GbcaLoc.T("SettingsSavedMsg");
        _savedFeedbackText.IsVisible = true;
        DispatcherTimer.RunOnce(() => _savedFeedbackText.IsVisible = false, TimeSpan.FromSeconds(2));
    }

    private void AddTabHeader(int idx, AuroraIconKind icon, string title)
    {
        var btn = new Border
        {
            Height = 44,
            CornerRadius = new CornerRadius(10),
            Padding = new Thickness(10, 0),
            Background = Brushes.Transparent,
            Cursor = new Cursor(StandardCursorType.Hand)
        };
        var row = new Grid { ColumnDefinitions = new ColumnDefinitions("Auto,*") };
        row.Children.Add(new AuroraVectorIcon { Kind = icon, Size = 18, VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(0, 0, 10, 0) });
        var tb = new TextBlock { Text = title, FontSize = 12, FontWeight = FontWeight.Medium, VerticalAlignment = VerticalAlignment.Center, TextTrimming = TextTrimming.CharacterEllipsis };
        row.Children.Add(tb);
        Grid.SetColumn(tb, 1);
        btn.Child = row;
        btn.PointerPressed += (_, _) =>
        {
            AuroraTactileFeedbackService.Instance.PlayClick();
            LoadTab(idx);
        };
        _tabsRail.Children.Add(btn);
        _tabButtons.Add((idx, icon, title, btn));
    }

    private void LoadTab(int index)
    {
        _activeTabIndex = index;
        var primary = StormThemeBrushes.AccentPrimaryColor;

        foreach (var item in _tabButtons)
        {
            bool sel = item.Index == index;
            item.Button.Background = sel ? new SolidColorBrush(Color.FromArgb(50, primary.R, primary.G, primary.B)) : Brushes.Transparent;
            item.Button.BorderBrush = sel ? new SolidColorBrush(primary) : Brushes.Transparent;
            item.Button.BorderThickness = new Thickness(sel ? 1 : 0);
            if (item.Button.Child is Grid g && g.Children.Count > 1 && g.Children[1] is TextBlock tb)
            {
                tb.Foreground = sel ? StormThemeBrushes.PrimaryText : StormThemeBrushes.SecondaryText;
            }
        }

        var scroll = new ScrollViewer { Padding = new Thickness(18, 16, 26, 16), HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled };
        var container = new StackPanel { Spacing = 12 };
        string q = _filterQuery.Trim();

        if (q.Length >= 2)
        {
            var all = new StackPanel { Spacing = 12 };
            BuildCoreTab(all);
            BuildVideoTab(all);
            BuildAudioTab(all);
            BuildSkinsSummaryTab(all);
            BuildInputTab(all);
            BuildRaTab(all);
            BuildThemesTab(all);
            foreach (var c in all.Children.ToList())
            {
                if (ControlContainsText(c, q))
                {
                    all.Children.Remove(c);
                    container.Children.Add(c);
                }
            }
            if (container.Children.Count == 0)
            {
                container.Children.Add(new TextBlock { Text = $"По запросу '{q}' ничего не найдено.", FontSize = 12, Foreground = StormThemeBrushes.SecondaryText });
            }
        }
        else
        {
            switch (index)
            {
                case 0: BuildCoreTab(container); break;
                case 1: BuildVideoTab(container); break;
                case 2: BuildAudioTab(container); break;
                case 3: BuildSkinsSummaryTab(container); break;
                case 4: BuildInputTab(container); break;
                case 5: BuildRaTab(container); break;
                case 6: BuildThemesTab(container); break;
            }
        }

        scroll.Content = container;
        _contentPresenter.Content = scroll;
    }

    private static bool ControlContainsText(Control control, string query)
    {
        if (control is TextBlock tb && tb.Text?.Contains(query, StringComparison.OrdinalIgnoreCase) == true) return true;
        if (control is AuroraNeonCheckBox cb && cb.Label?.Contains(query, StringComparison.OrdinalIgnoreCase) == true) return true;
        if (control is Panel p) return p.Children.Any(c => ControlContainsText(c, query));
        if (control is Border b && b.Child is Control bc) return ControlContainsText(bc, query);
        if (control is ContentControl cc && cc.Content is Control c2) return ControlContainsText(c2, query);
        return false;
    }

    private void BuildCoreTab(StackPanel p)
    {
        var s = StormGbcaUiState.Instance.Settings;
        p.Children.Add(CreateSectionTitle("Выбор эмулирующих ядер, BIOS и перемотки"));
        p.Children.Add(CreateCombo("Ядро для Game Boy (GB):", ["mGBA (Высокая скорость)", "SameBoy (Максимальная точность)"], (int)s.PreferredCoreGb, i => s.PreferredCoreGb = (StormCoreBackend)i));
        p.Children.Add(CreateCombo("Ядро для Game Boy Color (GBC):", ["mGBA (Высокая скорость)", "SameBoy (Максимальная точность)"], (int)s.PreferredCoreGbc, i => s.PreferredCoreGbc = (StormCoreBackend)i));
        p.Children.Add(CreateCombo("Ядро для Game Boy Advance (GBA):", ["mGBA (Высокая точность)"], 0, _ => s.PreferredCoreGba = StormCoreBackend.Mgba));
        p.Children.Add(CreateCombo("Модель оборудования Game Boy:", ["Автоопределение", "DMG (Классический)", "MGB (Pocket)", "SGB (Super Game Boy)", "SGB2", "CGB (Color)", "AGB (GBA)"], (int)s.GbModel, i => s.GbModel = (StormGbModel)i));
        p.Children.Add(CreateCheck("Пропускать заставку BIOS при запуске", s.SkipBiosIntro, v => s.SkipBiosIntro = v));
        p.Children.Add(CreateCheck("Включить перемотку времени назад (Rewind)", s.RewindEnabled, v => s.RewindEnabled = v));
        p.Children.Add(CreateSlider("Упреждающие кадры (Run-Ahead):", 0, 2, s.RunAheadFrames, "кадра", v => s.RunAheadFrames = (int)v));
        p.Children.Add(CreateSlider("Буфер перемотки назад:", 8, 64, s.RewindBufferMb, "МБ", v => s.RewindBufferMb = (int)v));
        p.Children.Add(CreateSlider("Скорость ускорения (Fast-Forward):", 1, 8, s.FastForwardMultiplier, "x", v => s.FastForwardMultiplier = (float)v));

        p.Children.Add(CreateSectionTitle("Файлы BIOS (необязательно)"));
        p.Children.Add(CreatePathRow("BIOS Game Boy:", s.BiosGbPath, ["*.bin"], t => { s.BiosGbPath = t; CommitSave(); }));
        p.Children.Add(CreatePathRow("BIOS Game Boy Color:", s.BiosGbcPath, ["*.bin"], t => { s.BiosGbcPath = t; CommitSave(); }));
        p.Children.Add(CreatePathRow("BIOS Game Boy Advance:", s.BiosGbaPath, ["*.bin"], t => { s.BiosGbaPath = t; CommitSave(); }));
        p.Children.Add(CreatePathRow("BIOS Super Game Boy:", s.BiosSgbPath, ["*.bin"], t => { s.BiosSgbPath = t; CommitSave(); }));
    }

    private void BuildVideoTab(StackPanel p)
    {
        var s = StormGbcaUiState.Instance.Settings;
        p.Children.Add(CreateSectionTitle("Цветокоррекция LCD, палитры DMG и шейдерные фильтры"));
        p.Children.Add(CreateCombo("Режим цветокоррекции (7 режимов + GBA):",
        [
            "Отключена (Сырой RGB)",
            "Точные кривые (Correct Curves)",
            "Современный баланс (Modern Balanced)",
            "Современный яркий (Modern Boost)",
            "Пониженный контраст (Reduce Contrast)",
            "Низкий контраст (Low Contrast)",
            "Современный точный (Modern Accurate)",
            "Оригинальный экран GBA AGB/AGS"
        ], (int)s.ColorCorrectionMode, i => s.ColorCorrectionMode = (StormColorCorrectionMode)i));

        var palNames = GbcaEmulatorSession.GetDmgPaletteNames().ToArray();
        p.Children.Add(CreateCombo($"Палитра монохромного Game Boy ({palNames.Length} вариантов):", palNames, Math.Clamp(s.DmgPaletteIndex, 0, palNames.Length - 1), i => s.DmgPaletteIndex = i));

        p.Children.Add(CreateCombo("Видеофильтр экрана:",
        [
            "Четкий пиксель (Nearest)",
            "Билинейное сглаживание (Bilinear)",
            "Сетка пикселей LCD (LCD Grid)",
            "Субпиксельная матрица (RGB Subpixel)",
            "Улучшение деталей (xBRZ Lite)",
            "Строки развертки (CRT Scanlines)",
            "Резкое сглаживание (Sharp Bilinear)"
        ], (int)s.VideoFilter, i => s.VideoFilter = (StormVideoFilter)i));

        p.Children.Add(CreateCombo("Масштабирование экрана:", ["Вписать с сохранением пропорций", "Целочисленное (Pixel-Perfect)", "Растянуть на область экрана"], (int)s.ScaleMode, i =>
        {
            s.ScaleMode = (GbcaScaleMode)i;
            StormGbcaUiState.Instance.NotifySkinOrOverlayChanged();
        }));

        p.Children.Add(CreateSlider("Инерционность матрицы LCD (Ghosting):", 0, 100, s.LcdGhosting, "%", v => s.LcdGhosting = (int)v));
        p.Children.Add(CreateCheck("Отображать декоративные рамки Super Game Boy (SGB)", s.SgbBorders, v => s.SgbBorders = v));
        p.Children.Add(CreateCheck("Показывать 3D-заставку STORM при запуске игры", s.Show3DBootSplash, v => s.Show3DBootSplash = v));
    }

    private void BuildAudioTab(StackPanel p)
    {
        var s = StormGbcaUiState.Instance.Settings;
        p.Children.Add(CreateSectionTitle("Аудиотракт 48000 Гц, фильтр высоких частот и 6-канальный микшер"));
        p.Children.Add(CreateCombo("Фильтр высоких частот (High-Pass):", ["Отключен", "Аппаратно точный (Accurate)", "Удаление постоянной составляющей (Remove DC)"], (int)s.AudioHighpassMode, i => s.AudioHighpassMode = (StormAudioHighpassMode)i));
        p.Children.Add(CreateCombo("Интерполяция звука:", ["Ближайшая (Nearest)", "Линейная (Linear)", "Кубическая Эрмита (Hermite High Quality)"], (int)s.AudioInterpMode, i => s.AudioInterpMode = (StormAudioInterpMode)i));
        p.Children.Add(CreateCheck("Динамическая синхронизация частоты (Dynamic Rate Control)", s.DynamicRateControl, v => s.DynamicRateControl = v));
        p.Children.Add(CreateCheck("Без звука (Mute)", s.AudioMuted, v => s.AudioMuted = v));
        p.Children.Add(CreateSlider("Общая громкость:", 0.0, 2.0, s.MasterVolume, "x", v => s.MasterVolume = (float)v));

        string[] chLabels = ["Канал 1: PSG Pulse 1", "Канал 2: PSG Pulse 2", "Канал 3: PSG Wave", "Канал 4: PSG Noise", "Канал 5: GBA Direct Sound A", "Канал 6: GBA Direct Sound B"];
        for (int i = 0; i < 6; i++)
        {
            int idx = i;
            p.Children.Add(CreateSlider(chLabels[idx], 0.0, 1.5, s.ChannelVolumes[idx], "x", v => s.ChannelVolumes[idx] = (float)v));
        }

        p.Children.Add(CreateCheck("Тактильные звуки интерфейса STORM", s.UiTactileAudioEnabled, v =>
        {
            s.UiTactileAudioEnabled = v;
            AuroraTactileFeedbackService.Instance.Enabled = v;
        }));
    }

    private void BuildSkinsSummaryTab(StackPanel p)
    {
        var s = StormGbcaUiState.Instance.Settings;
        p.Children.Add(CreateSectionTitle("Независимые 3D-корпусы и оверлеи по системам"));
        p.Children.Add(CreateCombo("3D-корпус для Game Boy (GB):", Enum.GetNames<GbConsoleSkin>(), (int)s.GbConsoleSkin, i => { s.GbConsoleSkin = (GbConsoleSkin)i; StormGbcaUiState.Instance.NotifySkinOrOverlayChanged(); }));
        p.Children.Add(CreateCombo("3D-корпус для Game Boy Color (GBC):", Enum.GetNames<GbcConsoleSkin>(), (int)s.GbcConsoleSkin, i => { s.GbcConsoleSkin = (GbcConsoleSkin)i; StormGbcaUiState.Instance.NotifySkinOrOverlayChanged(); }));
        p.Children.Add(CreateCombo("3D-корпус для Game Boy Advance (GBA):", Enum.GetNames<GbaConsoleSkin>(), (int)s.GbaConsoleSkin, i => { s.GbaConsoleSkin = (GbaConsoleSkin)i; StormGbcaUiState.Instance.NotifySkinOrOverlayChanged(); }));
        p.Children.Add(CreateCheck("Ночное затемнение 3D-корпуса", s.SkinNightDimming, v => { s.SkinNightDimming = v; StormGbcaUiState.Instance.NotifySkinOrOverlayChanged(); }));

        var openStudioBtn = new AuroraVolumetricButton { IsHero = true, Height = 38, Width = 310, HorizontalAlignment = HorizontalAlignment.Left, Margin = new Thickness(0, 6, 0, 0) };
        openStudioBtn.Content = new TextBlock { Text = "Открыть студию 3D-корпусов с предпросмотром", FontSize = 12, FontWeight = FontWeight.Bold, Foreground = Brushes.White, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
        openStudioBtn.Click += (_, _) => StormGbcaUiState.Instance.SetNavSection(NavSectionKind.SkinsAndOverlays);
        p.Children.Add(openStudioBtn);
    }

    private void BuildInputTab(StackPanel p)
    {
        var s = StormGbcaUiState.Instance.Settings;
        p.Children.Add(CreateSectionTitle("Назначение клавиш клавиатуры (нажмите значок клавиши для переназначения)"));

        var grid = new UniformGrid { Columns = 2 };
        (string Key, string Label)[] bindings =
        [
            ("Up", "Крестовина: Вверх"),
            ("Down", "Крестовина: Вниз"),
            ("Left", "Крестовина: Влево"),
            ("Right", "Крестовина: Вправо"),
            ("A", "Кнопка A"),
            ("B", "Кнопка B"),
            ("L", "Триггер L (GBA)"),
            ("R", "Триггер R (GBA)"),
            ("Start", "Кнопка Start"),
            ("Select", "Кнопка Select"),
            ("FastForward", "Ускорение (Fast-Forward)"),
            ("Rewind", "Перемотка назад (Rewind)")
        ];

        foreach (var b in bindings)
        {
            string currentKey = s.KeyBindings.TryGetValue(b.Key, out string? k) ? k : "-";
            bool listening = _listeningActionKey == b.Key;

            var row = new Border
            {
                Margin = new Thickness(0, 3, 8, 3),
                Padding = new Thickness(10, 6),
                CornerRadius = new CornerRadius(10),
                Background = StormThemeBrushes.RowSurface,
                BorderBrush = listening ? StormThemeBrushes.Accent : StormThemeBrushes.SubtleBorder,
                BorderThickness = new Thickness(1),
                Cursor = new Cursor(StandardCursorType.Hand)
            };
            var rg = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto") };
            rg.Children.Add(new TextBlock { Text = b.Label, FontSize = 11.5, Foreground = StormThemeBrushes.BodyText, VerticalAlignment = VerticalAlignment.Center });

            // Glossy 3D Physical Key Badge
            var keyCap = Create3DPhysicalKeyBadge(listening ? "Нажмите клавишу..." : currentKey, listening);
            rg.Children.Add(keyCap);
            Grid.SetColumn(keyCap, 1);

            row.Child = rg;
            row.PointerPressed += (_, _) =>
            {
                AuroraTactileFeedbackService.Instance.PlayClick();
                _listeningActionKey = b.Key;
                Focus();
                LoadTab(_activeTabIndex);
            };
            grid.Children.Add(row);
        }
        p.Children.Add(grid);

        p.Children.Add(CreateSectionTitle("Геймпад XInput и сенсоры картриджа"));
        p.Children.Add(new TextBlock
        {
            Text = $"Статус XInput: {(StormGbcaUiState.Instance.Emulator.Gamepad.IsSupported ? "Драйвер XInput активен (подключите геймпад Xbox / совместимый)" : "XInput недоступен")}",
            FontSize = 11.5,
            Foreground = StormThemeBrushes.SecondaryText
        });
        p.Children.Add(CreateCheck("Передавать вибрацию картриджа (Rumble) на геймпад XInput", s.RumbleToXInput, v => s.RumbleToXInput = v));
        p.Children.Add(CreateCheck("Использовать системное время ПК для часов реального времени (RTC)", s.UseHostRtc, v => s.UseHostRtc = v));
        p.Children.Add(CreateSlider("Солнечный датчик Boktai (Solar LUX):", 0, 10, s.SolarLuxLevel, "Lux", v => s.SolarLuxLevel = (int)v));
    }

    private static Border Create3DPhysicalKeyBadge(string keyName, bool highlighted)
    {
        var accent = StormThemeBrushes.AccentPrimaryColor;
        return new Border
        {
            MinWidth = 68,
            Height = 28,
            Padding = new Thickness(10, 2),
            CornerRadius = new CornerRadius(7),
            Background = new LinearGradientBrush
            {
                StartPoint = new RelativePoint(0, 0, RelativeUnit.Relative),
                EndPoint = new RelativePoint(0, 1, RelativeUnit.Relative),
                GradientStops =
                {
                    new GradientStop(highlighted ? accent : Color.Parse("#334155"), 0.0),
                    new GradientStop(highlighted ? Color.FromArgb(200, accent.R, accent.G, accent.B) : Color.Parse("#1E293B"), 1.0)
                }
            },
            BorderBrush = new SolidColorBrush(highlighted ? Colors.White : Color.FromArgb(140, 255, 255, 255)),
            BorderThickness = new Thickness(1, 1, 1, 3),
            BoxShadow = new BoxShadows(new BoxShadow { OffsetX = 0, OffsetY = 2, Blur = 5, Color = Color.FromArgb(120, 0, 0, 0) }),
            Child = new TextBlock
            {
                Text = keyName,
                FontSize = 11,
                FontWeight = FontWeight.Bold,
                FontFamily = new FontFamily("Consolas, Cascadia Mono, monospace"),
                Foreground = Brushes.White,
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            }
        };
    }

    private void OnKeyCaptureKeyDown(object? sender, KeyEventArgs e)
    {
        if (string.IsNullOrWhiteSpace(_listeningActionKey)) return;
        if (e.Key != Key.Escape)
        {
            StormGbcaUiState.Instance.Settings.KeyBindings[_listeningActionKey] = e.Key.ToString();
            CommitSave();
        }
        _listeningActionKey = null;
        e.Handled = true;
        LoadTab(_activeTabIndex);
    }

    private void BuildRaTab(StackPanel p)
    {
        var s = StormGbcaUiState.Instance.Settings;
        var ra = StormGbcaUiState.Instance.RetroAchievements;
        p.Children.Add(CreateSectionTitle("Учетная запись RetroAchievements"));
        p.Children.Add(CreateTextRow("Имя пользователя:", s.RetroAchievementsUsername, t => s.RetroAchievementsUsername = t));
        p.Children.Add(CreateTextRow("Web API Token:", s.RetroAchievementsToken, t => s.RetroAchievementsToken = t));

        var passBox = new TextBox { PlaceholderText = "Пароль для получения токена по API (не сохраняется)", PasswordChar = '*', Height = 32 };
        var statusTxt = new TextBlock { Text = ra.IsLoggedIn ? $"Выполнен вход: {ra.Username}" : "Токен не получен", FontSize = 11.5, Foreground = StormThemeBrushes.AccentSecondary };
        var loginBtn = new AuroraVolumetricButton { Height = 32, Width = 220, HorizontalAlignment = HorizontalAlignment.Left };
        loginBtn.Content = new TextBlock { Text = "Войти через RetroAchievements", FontSize = 11.5, FontWeight = FontWeight.Bold, Foreground = Brushes.White, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
        loginBtn.Click += async (_, _) =>
        {
            statusTxt.Text = "Авторизация на retroachievements.org...";
            var res = await ra.LoginAsync(s.RetroAchievementsUsername, passBox.Text ?? string.Empty);
            await Dispatcher.UIThread.InvokeAsync(() =>
            {
                if (res.Success)
                {
                    s.RetroAchievementsUsername = ra.Username;
                    s.RetroAchievementsToken = ra.Token;
                    CommitSave(true);
                }
                statusTxt.Text = res.Message;
            });
        };
        p.Children.Add(passBox);
        p.Children.Add(loginBtn);
        p.Children.Add(statusTxt);

        p.Children.Add(CreateCheck("Хардкорный режим (запрещает загрузку состояний, перемотку и читы)", s.RetroAchievementsHardcoreMode, v => s.RetroAchievementsHardcoreMode = v));
        p.Children.Add(CreateCheck("Rich Presence статус активности", s.RetroAchievementsRichPresence, v => s.RetroAchievementsRichPresence = v));
    }

    private void BuildThemesTab(StackPanel p)
    {
        var s = StormGbcaUiState.Instance.Settings;
        p.Children.Add(CreateSectionTitle("9 Динамических тем оформления STORM"));

        (string Id, string Sub)[] themes =
        [
            ("MIDNIGHT", "Аметистовая ночь"),
            ("NIGHT", "Глубокий OLED"),
            ("DARK", "Темный кибер"),
            ("DAY", "Светлая тема"),
            ("FANTASY", "Королевское золото"),
            ("CYBERPUNK", "Неон Найт-Сити"),
            ("WARHAMMER 40K", "Имперская готика"),
            ("ELEMENTAL", "Изумрудный шторм"),
            ("STORM", "Фирменный градиент")
        ];

        var themesGrid = new UniformGrid { Columns = 3 };
        foreach (var t in themes)
        {
            bool sel = string.Equals(AuroraThemeManager.Instance.CurrentTheme.Id, t.Id, StringComparison.OrdinalIgnoreCase);
            var btn = new Button
            {
                Margin = new Thickness(4),
                Padding = new Thickness(8, 6),
                Background = sel ? StormThemeBrushes.HoverSurface : StormThemeBrushes.CardSurface,
                BorderBrush = sel ? StormThemeBrushes.Accent : StormThemeBrushes.SubtleBorder,
                BorderThickness = new Thickness(1),
                CornerRadius = new CornerRadius(8),
                Cursor = new Cursor(StandardCursorType.Hand),
                HorizontalContentAlignment = HorizontalAlignment.Center
            };
            var st = new StackPanel { Spacing = 2, HorizontalAlignment = HorizontalAlignment.Center };
            st.Children.Add(new TextBlock { Text = t.Id, FontSize = 11, FontWeight = FontWeight.Bold, Foreground = StormThemeBrushes.PrimaryText, HorizontalAlignment = HorizontalAlignment.Center });
            st.Children.Add(new TextBlock { Text = t.Sub, FontSize = 9.5, Foreground = StormThemeBrushes.SecondaryText, HorizontalAlignment = HorizontalAlignment.Center });
            btn.Content = st;
            btn.Click += (_, _) =>
            {
                AuroraTactileFeedbackService.Instance.PlayClick();
                AuroraThemeManager.Instance.ApplyTheme(t.Id);
                s.Theme = "STORM " + t.Id;
                CommitSave();
            };
            themesGrid.Children.Add(btn);
        }
        p.Children.Add(themesGrid);

        p.Children.Add(CreateSectionTitle("Акцентные цвета интерфейса (13 вариантов)"));
        var accentsWrap = new WrapPanel { Orientation = Orientation.Horizontal };
        foreach (var kind in Enum.GetValues<AuroraAccentKind>())
        {
            var (swatchColor, _) = AuroraThemeManager.ResolveAccentColors(AuroraThemeManager.Instance.CurrentTheme, kind);
            bool activeAccent = AuroraThemeManager.Instance.CurrentAccentOverride == kind;
            var swatchBtn = new Button
            {
                Margin = new Thickness(0, 0, 6, 6),
                Padding = new Thickness(8, 5),
                CornerRadius = new CornerRadius(8),
                Background = activeAccent ? StormThemeBrushes.HoverSurface : StormThemeBrushes.CardSurface,
                BorderBrush = activeAccent ? new SolidColorBrush(swatchColor) : StormThemeBrushes.SubtleBorder,
                BorderThickness = new Thickness(1),
                Cursor = new Cursor(StandardCursorType.Hand)
            };
            var row = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 6, VerticalAlignment = VerticalAlignment.Center };
            row.Children.Add(new Border { Width = 12, Height = 12, CornerRadius = new CornerRadius(6), Background = new SolidColorBrush(swatchColor) });
            row.Children.Add(new TextBlock { Text = kind == AuroraAccentKind.DefaultThemeAccent ? "По теме" : kind.ToString(), FontSize = 10.5, Foreground = StormThemeBrushes.PrimaryText });
            swatchBtn.Content = row;
            swatchBtn.Click += (_, _) =>
            {
                AuroraTactileFeedbackService.Instance.PlayClick();
                AuroraThemeManager.Instance.ApplyTheme(AuroraThemeManager.Instance.CurrentTheme.Id, kind);
                s.AccentName = kind.ToString();
                CommitSave();
                LoadTab(_activeTabIndex);
            };
            accentsWrap.Children.Add(swatchBtn);
        }
        p.Children.Add(accentsWrap);

        p.Children.Add(CreateSectionTitle("Шрифты интерфейса (10 пресетов)"));
        var fontsGrid = new UniformGrid { Columns = 2 };
        foreach (var preset in Enum.GetValues<AuroraTypographyPreset>())
        {
            bool activeFont = AuroraThemeManager.Instance.CurrentTypography == preset;
            var fBtn = new Button
            {
                Margin = new Thickness(3),
                Padding = new Thickness(8, 5),
                CornerRadius = new CornerRadius(6),
                Background = activeFont ? StormThemeBrushes.HoverSurface : StormThemeBrushes.CardSurface,
                BorderBrush = activeFont ? StormThemeBrushes.Accent : StormThemeBrushes.SubtleBorder,
                BorderThickness = new Thickness(1),
                Cursor = new Cursor(StandardCursorType.Hand),
                HorizontalContentAlignment = HorizontalAlignment.Center,
                Content = new TextBlock { Text = preset.ToString(), FontSize = 11, FontFamily = AuroraThemeManager.ResolveFontFamily(preset), Foreground = StormThemeBrushes.PrimaryText }
            };
            fBtn.Click += (_, _) =>
            {
                AuroraTactileFeedbackService.Instance.PlayClick();
                AuroraThemeManager.Instance.ApplyTheme(AuroraThemeManager.Instance.CurrentTheme.Id, null, preset);
                s.TypographyName = preset.ToString();
                CommitSave();
                LoadTab(_activeTabIndex);
            };
            fontsGrid.Children.Add(fBtn);
        }
        p.Children.Add(fontsGrid);

        p.Children.Add(CreateSectionTitle("Язык интерфейса и автообновление (GitHub + STORM GAMES WORLD)"));
        p.Children.Add(CreateCombo("Язык интерфейса:", GbcaLoc.SupportedLanguages.Select(l => l.NativeName).ToArray(),
            Math.Max(0, GbcaLoc.SupportedLanguages.ToList().FindIndex(l => string.Equals(l.Code, s.Language, StringComparison.OrdinalIgnoreCase))),
            idx =>
            {
                string code = GbcaLoc.SupportedLanguages[idx].Code;
                GbcaLoc.CurrentLanguage = code;
                s.Language = code;
                CommitSave();
            }));
        p.Children.Add(CreateCheck("Автоматически проверять обновления при запуске", s.AutoCheckUpdates, v => s.AutoCheckUpdates = v));

        var updBtn = new AuroraVolumetricButton { Height = 34, Width = 240, HorizontalAlignment = HorizontalAlignment.Left };
        updBtn.Content = new TextBlock { Text = "Проверить обновления сейчас", FontSize = 12, FontWeight = FontWeight.Bold, Foreground = Brushes.White, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
        updBtn.Click += (_, _) => StormGbcaUiState.Instance.RequestUpdateCheck(true);
        p.Children.Add(updBtn);
    }

    private static TextBlock CreateSectionTitle(string title) => new()
    {
        Text = title.ToUpperInvariant(),
        FontSize = 11,
        FontWeight = FontWeight.Bold,
        Foreground = StormThemeBrushes.Accent,
        Margin = new Thickness(0, 6, 0, 2)
    };

    private Control CreateCheck(string label, bool initial, Action<bool> onChange)
    {
        var cb = new AuroraNeonCheckBox { Label = label, IsChecked = initial, Margin = new Thickness(0, 2) };
        cb.IsCheckedChanged += (_, chk) =>
        {
            onChange(chk);
            CommitSave();
        };
        return cb;
    }

    private Grid CreateSlider(string label, double min, double max, double initial, string unit, Action<double> onChange)
    {
        var g = new Grid { Margin = new Thickness(0, 4), RowDefinitions = new RowDefinitions("Auto,Auto") };
        var lbl = new TextBlock { Text = $"{label} {initial:0.##} {unit}", FontSize = 11, Foreground = StormThemeBrushes.BodyText };
        g.Children.Add(lbl);
        var slider = new AuroraVolumetricSlider { Minimum = min, Maximum = max, Value = Math.Clamp(initial, min, max), Height = 24 };
        slider.ValueChanged += (_, val) =>
        {
            lbl.Text = $"{label} {val:0.##} {unit}";
            onChange(val);
            CommitSave();
        };
        g.Children.Add(slider);
        Grid.SetRow(slider, 1);
        return g;
    }

    private Grid CreateCombo(string label, string[] options, int selectedIndex, Action<int> onSelect)
    {
        var g = new Grid { ColumnDefinitions = new ColumnDefinitions("*,260"), Margin = new Thickness(0, 4) };
        g.Children.Add(new TextBlock { Text = label, FontSize = 11.5, Foreground = StormThemeBrushes.BodyText, VerticalAlignment = VerticalAlignment.Center, TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0, 0, 12, 0) });
        var combo = new ComboBox { HorizontalAlignment = HorizontalAlignment.Stretch, ItemsSource = options, SelectedIndex = Math.Clamp(selectedIndex, 0, Math.Max(0, options.Length - 1)) };
        combo.SelectionChanged += (_, _) =>
        {
            if (combo.SelectedIndex >= 0)
            {
                onSelect(combo.SelectedIndex);
                CommitSave();
            }
        };
        g.Children.Add(combo);
        Grid.SetColumn(combo, 1);
        return g;
    }

    private Grid CreateTextRow(string label, string initial, Action<string> onChange)
    {
        var g = new Grid { ColumnDefinitions = new ColumnDefinitions("*,260"), Margin = new Thickness(0, 4) };
        g.Children.Add(new TextBlock { Text = label, FontSize = 11.5, Foreground = StormThemeBrushes.BodyText, VerticalAlignment = VerticalAlignment.Center });
        var tb = new TextBox { Text = initial, Height = 30 };
        tb.TextChanged += (_, _) =>
        {
            onChange(tb.Text ?? string.Empty);
            CommitSave();
        };
        g.Children.Add(tb);
        Grid.SetColumn(tb, 1);
        return g;
    }

    private Grid CreatePathRow(string label, string initial, string[] patterns, Action<string> onChange)
    {
        var g = new Grid { ColumnDefinitions = new ColumnDefinitions("*,220,Auto"), Margin = new Thickness(0, 4) };
        g.Children.Add(new TextBlock { Text = label, FontSize = 11.5, Foreground = StormThemeBrushes.BodyText, VerticalAlignment = VerticalAlignment.Center });
        var tb = new TextBox { Text = initial, Height = 30 };
        tb.TextChanged += (_, _) => onChange(tb.Text ?? string.Empty);
        g.Children.Add(tb);
        Grid.SetColumn(tb, 1);
        var btn = new Button { Content = "Обзор", Height = 30, Margin = new Thickness(6, 0, 0, 0), Padding = new Thickness(10, 0), Cursor = new Cursor(StandardCursorType.Hand) };
        btn.Click += async (_, _) =>
        {
            var top = TopLevel.GetTopLevel(this);
            if (top?.StorageProvider == null) return;
            var files = await top.StorageProvider.OpenFilePickerAsync(new FilePickerOpenOptions
            {
                Title = label,
                AllowMultiple = false,
                FileTypeFilter = [new FilePickerFileType(label) { Patterns = patterns }, FilePickerFileTypes.All]
            });
            string? path = files.Count > 0 ? files[0].TryGetLocalPath() : null;
            if (!string.IsNullOrWhiteSpace(path))
            {
                tb.Text = path;
                onChange(path);
            }
        };
        g.Children.Add(btn);
        Grid.SetColumn(btn, 2);
        return g;
    }
}
