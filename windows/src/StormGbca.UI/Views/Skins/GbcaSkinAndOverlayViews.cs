// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Mozilla Public License, Version 2.0 (MPL-2.0).

using Avalonia;
using Avalonia.Controls;
using Avalonia.Controls.Primitives;
using Avalonia.Input;
using Avalonia.Layout;
using Avalonia.Media;
using Storm.AuroraFluid.Kit.Controls;
using Storm.AuroraFluid.Kit.Icons;
using Storm.AuroraFluid.Kit.Services;
using StormGbca.Core.Configuration;
using StormGbca.Core.Native;
using StormGbca.UI.Common;
using StormGbca.UI.Views.Emulation;

namespace StormGbca.UI.Views.Skins;

/// <summary>
/// Dedicated per-system [GB] / [GBC] / [GBA] 3D Skin and Overlay customization studio
/// with live SkiaSharp mini-renders for every chassis skin and button/bezel overlay profile.
/// </summary>
public class GbcaSkinAndOverlayViews : Grid
{
    private readonly StackPanel _tabsRow;
    private readonly StackPanel _bodyStack;
    private Image? _overlayLivePreview;
    private Grid? _ovGrid;
    private Border? _previewFrame;
    private int _selectedSystemTab; // 0 = GB, 1 = GBC, 2 = GBA

    public GbcaSkinAndOverlayViews()
    {
        Margin = new Thickness(16, 8, 16, 12);
        RowDefinitions = new RowDefinitions("Auto,*");

        var headerCard = new AuroraGlassCard { Padding = new Thickness(16, 12), Margin = new Thickness(0, 0, 0, 10), SpotlightEnabled = true };
        var hGrid = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto"), ColumnSpacing = 12, RowSpacing = 8 };
        var titleCol = new StackPanel { Spacing = 3 };
        titleCol.Children.Add(new TextBlock { Text = "СТУДИЯ 3D-КОРПУСОВ И ОВЕРЛЕЕВ ПО СИСТЕМАМ", FontSize = 14.5, FontWeight = FontWeight.Bold, Foreground = StormThemeBrushes.Accent, TextWrapping = TextWrapping.Wrap });
        titleCol.Children.Add(new TextBlock { Text = "Настройте отдельный 3D-корпус, рамку оверлея, стиль кнопок и LCD-фильтр отдельно для GB, GBC и GBA.", FontSize = 11.5, Foreground = StormThemeBrushes.SecondaryText, TextWrapping = TextWrapping.Wrap });
        hGrid.Children.Add(titleCol);

        _tabsRow = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8, VerticalAlignment = VerticalAlignment.Center };
        AddSystemTab(0, "GB", Color.Parse("#8BAC0F"));
        AddSystemTab(1, "GBC", Color.Parse("#7B42F6"));
        AddSystemTab(2, "GBA", Color.Parse("#4F46E5"));
        hGrid.Children.Add(_tabsRow);
        Grid.SetColumn(_tabsRow, 1);

        headerCard.Content = hGrid;
        Children.Add(headerCard);

        var scroll = new ScrollViewer
        {
            Padding = new Thickness(0, 0, 14, 0),
            HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled
        };
        _bodyStack = new StackPanel { Spacing = 14 };
        scroll.Content = _bodyStack;
        Children.Add(scroll);
        Grid.SetRow(scroll, 1);

        PropertyChanged += (_, ch) =>
        {
            if (ch.Property == BoundsProperty && Bounds.Width > 0)
            {
                bool narrowHeader = Bounds.Width < 700;
                if (narrowHeader)
                {
                    hGrid.ColumnDefinitions = new ColumnDefinitions("*");
                    hGrid.RowDefinitions = new RowDefinitions("Auto,Auto");
                    Grid.SetColumn(_tabsRow, 0);
                    Grid.SetRow(_tabsRow, 1);
                }
                else
                {
                    hGrid.ColumnDefinitions = new ColumnDefinitions("*,Auto");
                    hGrid.RowDefinitions = new RowDefinitions("Auto");
                    Grid.SetColumn(_tabsRow, 1);
                    Grid.SetRow(_tabsRow, 0);
                }
                ApplyOvGridLayout();
            }
        };

        StormThemeBrushes.BindTheme(this, RefreshBody);
        RefreshBody();
    }

    private void ApplyOvGridLayout()
    {
        if (_ovGrid == null || _previewFrame == null) return;
        bool stackVert = Bounds.Width > 0 && Bounds.Width < 860;
        if (stackVert)
        {
            _ovGrid.ColumnDefinitions = new ColumnDefinitions("*");
            _ovGrid.RowDefinitions = new RowDefinitions("Auto,Auto");
            Grid.SetColumn(_previewFrame, 0);
            Grid.SetRow(_previewFrame, 1);
        }
        else
        {
            _ovGrid.ColumnDefinitions = new ColumnDefinitions("*,300");
            _ovGrid.RowDefinitions = new RowDefinitions("Auto");
            Grid.SetColumn(_previewFrame, 1);
            Grid.SetRow(_previewFrame, 0);
        }
    }

    private void AddSystemTab(int idx, string code, Color color)
    {
        var b = new Border
        {
            Height = 36,
            Padding = new Thickness(18, 4),
            CornerRadius = new CornerRadius(10),
            BorderThickness = new Thickness(1.5),
            Cursor = new Cursor(StandardCursorType.Hand),
            Child = new TextBlock { Text = code, FontSize = 12.5, FontWeight = FontWeight.Bold, Foreground = Brushes.White, VerticalAlignment = VerticalAlignment.Center, HorizontalAlignment = HorizontalAlignment.Center }
        };
        b.PointerPressed += (_, _) =>
        {
            AuroraTactileFeedbackService.Instance.PlayClick();
            _selectedSystemTab = idx;
            RefreshBody();
        };
        _tabsRow.Children.Add(b);
    }

    public void RefreshBody()
    {
        Color[] tabColors = [Color.Parse("#8BAC0F"), Color.Parse("#7B42F6"), Color.Parse("#4F46E5")];
        for (int i = 0; i < _tabsRow.Children.Count; i++)
        {
            if (_tabsRow.Children[i] is Border b)
            {
                bool sel = i == _selectedSystemTab;
                Color c = tabColors[i];
                b.Background = sel ? new SolidColorBrush(Color.FromArgb(200, c.R, c.G, c.B)) : StormThemeBrushes.RowSurface;
                b.BorderBrush = new SolidColorBrush(sel ? c : Color.FromArgb(80, c.R, c.G, c.B));
                if (b.Child is TextBlock tb) tb.Foreground = sel ? Brushes.White : StormThemeBrushes.BodyText;
            }
        }

        _bodyStack.Children.Clear();
        var s = StormGbcaUiState.Instance.Settings;
        StormPlatform plat = _selectedSystemTab switch
        {
            0 => StormPlatform.Gb,
            1 => StormPlatform.Gbc,
            _ => StormPlatform.Gba,
        };

        // 1. 3D Console Skins Catalog with Live SkiaSharp Mini-Renders
        var skinCard = new AuroraGlassCard { Padding = new Thickness(16), SpotlightEnabled = true };
        var skinStack = new StackPanel { Spacing = 10 };
        skinStack.Children.Add(new TextBlock
        {
            Text = $"ВЫБОР 3D-КОРПУСА ДЛЯ {plat}",
            FontSize = 11.5,
            FontWeight = FontWeight.Bold,
            Foreground = StormThemeBrushes.Accent
        });

        var skinsWrap = new WrapPanel { Orientation = Orientation.Horizontal };
        if (_selectedSystemTab == 0)
        {
            foreach (var skin in Enum.GetValues<GbConsoleSkin>())
            {
                var pal = GbcaConsoleSkinRenderer.GetGbPalette(skin);
                bool active = s.GbConsoleSkin == skin;
                skinsWrap.Children.Add(CreateSkinTile(plat, pal, active, () =>
                {
                    s.GbConsoleSkin = skin;
                    StormGbcaUiState.Instance.NotifySkinOrOverlayChanged();
                    RefreshBody();
                }));
            }
        }
        else if (_selectedSystemTab == 1)
        {
            foreach (var skin in Enum.GetValues<GbcConsoleSkin>())
            {
                var pal = GbcaConsoleSkinRenderer.GetGbcPalette(skin);
                bool active = s.GbcConsoleSkin == skin;
                skinsWrap.Children.Add(CreateSkinTile(plat, pal, active, () =>
                {
                    s.GbcConsoleSkin = skin;
                    StormGbcaUiState.Instance.NotifySkinOrOverlayChanged();
                    RefreshBody();
                }));
            }
        }
        else
        {
            foreach (var skin in Enum.GetValues<GbaConsoleSkin>())
            {
                var pal = GbcaConsoleSkinRenderer.GetGbaPalette(skin);
                bool active = s.GbaConsoleSkin == skin;
                skinsWrap.Children.Add(CreateSkinTile(plat, pal, active, () =>
                {
                    s.GbaConsoleSkin = skin;
                    StormGbcaUiState.Instance.NotifySkinOrOverlayChanged();
                    RefreshBody();
                }));
            }
        }
        skinStack.Children.Add(skinsWrap);

        var nightCheck = new AuroraNeonCheckBox { Label = "Ночное затемнение 3D-корпуса (снижает яркость пластика и LED)", IsChecked = s.SkinNightDimming };
        nightCheck.IsCheckedChanged += (_, v) =>
        {
            s.SkinNightDimming = v;
            StormGbcaUiState.Instance.NotifySkinOrOverlayChanged();
        };
        skinStack.Children.Add(nightCheck);
        skinCard.Content = skinStack;
        _bodyStack.Children.Add(skinCard);

        // 2. Per-System Overlay Profile & Live SkiaSharp Preview
        var prof = s.GetOverlayProfileForPlatform(plat);
        var overlayCard = new AuroraGlassCard { Padding = new Thickness(16), SpotlightEnabled = true };
        _ovGrid = new Grid { ColumnDefinitions = new ColumnDefinitions("*,300"), ColumnSpacing = 16, RowSpacing = 12 };

        var controlsCol = new StackPanel { Spacing = 8 };
        controlsCol.Children.Add(new TextBlock
        {
            Text = $"НЕЗАВИСИМЫЙ ПРОФИЛЬ ОВЕРЛЕЯ И КНОПОК ДЛЯ {plat}",
            FontSize = 11.5,
            FontWeight = FontWeight.Bold,
            Foreground = StormThemeBrushes.Accent
        });

        controlsCol.Children.Add(CreateCombo("Макет рамки оверлея:", Enum.GetNames<OverlayBezelStyle>(), (int)prof.OverlayLayout, idx =>
        {
            prof.OverlayLayout = (OverlayBezelStyle)idx;
            StormGbcaUiState.Instance.NotifySkinOrOverlayChanged();
            UpdateOverlayPreview(plat, prof);
        }));

        controlsCol.Children.Add(CreateCombo("Стиль кнопок оверлея:", Enum.GetNames<OverlayButtonStyle>(), (int)prof.ButtonStyle, idx =>
        {
            prof.ButtonStyle = (OverlayButtonStyle)idx;
            StormGbcaUiState.Instance.NotifySkinOrOverlayChanged();
            UpdateOverlayPreview(plat, prof);
        }));

        controlsCol.Children.Add(CreateCombo("LCD-фильтр системы:", Enum.GetNames<StormVideoFilter>(), (int)prof.LcdFilter, idx =>
        {
            prof.LcdFilter = (StormVideoFilter)idx;
            s.VideoFilter = prof.LcdFilter;
            StormGbcaUiState.Instance.NotifySkinOrOverlayChanged();
        }));

        controlsCol.Children.Add(CreateSlider("Прозрачность оверлея:", 0.15, 1.0, prof.Opacity, "", v =>
        {
            prof.Opacity = (float)v;
            StormGbcaUiState.Instance.NotifySkinOrOverlayChanged();
            UpdateOverlayPreview(plat, prof);
        }));

        controlsCol.Children.Add(CreateSlider("Масштаб панели кнопок:", 0.6, 1.6, prof.Scale, "x", v =>
        {
            prof.Scale = (float)v;
            StormGbcaUiState.Instance.NotifySkinOrOverlayChanged();
            UpdateOverlayPreview(plat, prof);
        }));

        var deckCheck = new AuroraNeonCheckBox { Label = "Показывать панель интерактивных кнопок", IsChecked = prof.ShowControlsDeck };
        deckCheck.IsCheckedChanged += (_, v) =>
        {
            prof.ShowControlsDeck = v;
            StormGbcaUiState.Instance.NotifySkinOrOverlayChanged();
            UpdateOverlayPreview(plat, prof);
        };
        controlsCol.Children.Add(deckCheck);

        var lrCheck = new AuroraNeonCheckBox { Label = "Показывать боковые триггеры L и R", IsChecked = prof.ShowLR };
        lrCheck.IsCheckedChanged += (_, v) =>
        {
            prof.ShowLR = v;
            StormGbcaUiState.Instance.NotifySkinOrOverlayChanged();
            UpdateOverlayPreview(plat, prof);
        };
        controlsCol.Children.Add(lrCheck);

        var sepAbCheck = new AuroraNeonCheckBox { Label = "Раздельные кнопки A и B (без наложения)", IsChecked = prof.SeparateABButtons };
        sepAbCheck.IsCheckedChanged += (_, v) =>
        {
            prof.SeparateABButtons = v;
            StormGbcaUiState.Instance.NotifySkinOrOverlayChanged();
            UpdateOverlayPreview(plat, prof);
        };
        controlsCol.Children.Add(sepAbCheck);

        controlsCol.Children.Add(CreateSlider("Расстояние между кнопками A и B (%):", 95, 200, prof.AbSpacingPercent, "%", v =>
        {
            prof.AbSpacingPercent = (int)Math.Round(v);
            StormGbcaUiState.Instance.NotifySkinOrOverlayChanged();
            UpdateOverlayPreview(plat, prof);
        }));

        _ovGrid.Children.Add(controlsCol);

        _overlayLivePreview = new Image { Width = 280, Height = 165, Stretch = Stretch.Uniform };
        _previewFrame = new Border
        {
            CornerRadius = new CornerRadius(12),
            Background = StormThemeBrushes.RowSurface,
            BorderBrush = StormThemeBrushes.StrongBorder,
            BorderThickness = new Thickness(1),
            Padding = new Thickness(10),
            VerticalAlignment = VerticalAlignment.Center,
            Child = new StackPanel
            {
                Spacing = 8,
                HorizontalAlignment = HorizontalAlignment.Center,
                Children =
                {
                    new TextBlock { Text = "ПРЕДПРОСМОТР ОВЕРЛЕЯ", FontSize = 10, FontWeight = FontWeight.Bold, Foreground = StormThemeBrushes.SecondaryText, HorizontalAlignment = HorizontalAlignment.Center },
                    _overlayLivePreview
                }
            }
        };
        _ovGrid.Children.Add(_previewFrame);
        Grid.SetColumn(_previewFrame, 1);
        ApplyOvGridLayout();

        overlayCard.Content = _ovGrid;
        _bodyStack.Children.Add(overlayCard);
        UpdateOverlayPreview(plat, prof);

        // 3. Per-System Screen Size, Position, Rotation & RetroArch Shader Preset
        var scr = prof.ScreenRect ?? new ScreenRectSettings();
        prof.ScreenRect = scr;

        var screenCard = new AuroraGlassCard { Padding = new Thickness(16), SpotlightEnabled = true };
        var screenStack = new StackPanel { Spacing = 8 };
        screenStack.Children.Add(new TextBlock
        {
            Text = $"РАЗМЕР, РАСПОЛОЖЕНИЕ ЭКРАНА И ШЕЙДЕР RETROARCH ДЛЯ {plat}",
            FontSize = 11.5,
            FontWeight = FontWeight.Bold,
            Foreground = StormThemeBrushes.Accent
        });

        var presetRow = new WrapPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 2, 0, 4) };
        (int Id, string Label)[] screenPresets =
        [
            (0, "Внутри 3D-корпуса"),
            (1, "Максимум (пропорции)"),
            (2, "Во весь экран (100%)"),
            (3, "Пиксель-в-пиксель")
        ];
        foreach (var pr in screenPresets)
        {
            bool activePreset = !scr.UseCustom && scr.ScreenPreset == pr.Id;
            var pBtn = new AuroraVolumetricButton { IsHero = activePreset, Height = 30, Margin = new Thickness(0, 0, 8, 6), Padding = new Thickness(12, 0) };
            pBtn.Content = new TextBlock
            {
                Text = pr.Label,
                FontSize = 11,
                FontWeight = activePreset ? FontWeight.Bold : FontWeight.SemiBold,
                Foreground = activePreset ? Brushes.White : StormThemeBrushes.PrimaryText,
                VerticalAlignment = VerticalAlignment.Center
            };
            pBtn.Click += (_, _) =>
            {
                scr.UseCustom = false;
                scr.ScreenPreset = pr.Id;
                StormGbcaUiState.Instance.NotifySkinOrOverlayChanged();
                RefreshBody();
            };
            presetRow.Children.Add(pBtn);
        }
        screenStack.Children.Add(presetRow);

        var hideSkinFs = new AuroraNeonCheckBox { Label = "Автоматически скрывать 3D-корпус в полноэкранном режиме", IsChecked = scr.HideSkinInFullscreen };
        hideSkinFs.IsCheckedChanged += (_, v) =>
        {
            scr.HideSkinInFullscreen = v;
            StormGbcaUiState.Instance.NotifySkinOrOverlayChanged();
        };
        screenStack.Children.Add(hideSkinFs);

        var customCheck = new AuroraNeonCheckBox { Label = "Включить пользовательский размер и позицию экрана", IsChecked = scr.UseCustom };
        customCheck.IsCheckedChanged += (_, v) =>
        {
            scr.UseCustom = v;
            if (v) scr.ScreenPreset = 4;
            StormGbcaUiState.Instance.NotifySkinOrOverlayChanged();
        };
        screenStack.Children.Add(customCheck);

        screenStack.Children.Add(CreateSlider("Ширина экрана (%):", 25, 100, Math.Round(scr.WidthFraction * 100), "%", v =>
        {
            scr.UseCustom = true;
            scr.WidthFraction = (float)(v / 100.0);
            StormGbcaUiState.Instance.NotifySkinOrOverlayChanged();
        }));
        screenStack.Children.Add(CreateSlider("Высота экрана (%):", 20, 100, Math.Round(scr.HeightFraction * 100), "%", v =>
        {
            scr.UseCustom = true;
            scr.HeightFraction = (float)(v / 100.0);
            StormGbcaUiState.Instance.NotifySkinOrOverlayChanged();
        }));
        screenStack.Children.Add(CreateSlider("Позиция X (центр %):", 10, 90, Math.Round(scr.CenterX * 100), "%", v =>
        {
            scr.UseCustom = true;
            scr.CenterX = (float)(v / 100.0);
            StormGbcaUiState.Instance.NotifySkinOrOverlayChanged();
        }));
        screenStack.Children.Add(CreateSlider("Позиция Y (центр %):", 10, 90, Math.Round(scr.CenterY * 100), "%", v =>
        {
            scr.UseCustom = true;
            scr.CenterY = (float)(v / 100.0);
            StormGbcaUiState.Instance.NotifySkinOrOverlayChanged();
        }));

        int[] rotAngles = [0, 90, 180, 270];
        int rotIdx = Math.Max(0, Array.IndexOf(rotAngles, scr.RotationDeg));
        screenStack.Children.Add(CreateCombo("Поворот экрана:", ["0 градусов", "90 градусов", "180 градусов", "270 градусов"], rotIdx, idx =>
        {
            scr.RotationDeg = rotAngles[Math.Clamp(idx, 0, rotAngles.Length - 1)];
            StormGbcaUiState.Instance.NotifySkinOrOverlayChanged();
        }));

        // RetroArch .slangp shader presets installed in shaders/
        var shaderFiles = DiscoverRetroArchShaders();
        var shaderLabels = new List<string> { "Отключен (Встроенный)" };
        shaderLabels.AddRange(shaderFiles.Select(Path.GetFileNameWithoutExtension)!);
        int shaderIdx = 0;
        if (!string.IsNullOrWhiteSpace(prof.RetroArchShaderPath))
        {
            int found = shaderFiles.FindIndex(f => string.Equals(f, prof.RetroArchShaderPath, StringComparison.OrdinalIgnoreCase)
                || string.Equals(Path.GetFileName(f), Path.GetFileName(prof.RetroArchShaderPath), StringComparison.OrdinalIgnoreCase));
            if (found >= 0) shaderIdx = found + 1;
        }
        screenStack.Children.Add(CreateCombo("Шейдер RetroArch (.slangp):", shaderLabels.ToArray(), shaderIdx, idx =>
        {
            prof.RetroArchShaderPath = idx == 0 ? string.Empty : shaderFiles[idx - 1];
            StormGbcaUiState.Instance.NotifySkinOrOverlayChanged();
        }));

        var resetBtn = new AuroraVolumetricButton { Height = 32, Width = 240, HorizontalAlignment = HorizontalAlignment.Left };
        resetBtn.Content = new TextBlock { Text = "Сбросить геометрию экрана", FontSize = 11.5, FontWeight = FontWeight.SemiBold, Foreground = StormThemeBrushes.PrimaryText, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
        resetBtn.Click += (_, _) =>
        {
            prof.ScreenRect = new ScreenRectSettings();
            StormGbcaUiState.Instance.NotifySkinOrOverlayChanged();
            RefreshBody();
        };
        screenStack.Children.Add(resetBtn);

        screenCard.Content = screenStack;
        _bodyStack.Children.Add(screenCard);
    }

    private static List<string> DiscoverRetroArchShaders()
    {
        var dirs = new[]
        {
            Path.Combine(AppContext.BaseDirectory, "shaders"),
            Path.Combine(AppContext.BaseDirectory, "assets", "shaders"),
            @"F:\STORM GBCA\windows\assets\shaders"
        };
        foreach (string dir in dirs)
        {
            if (Directory.Exists(dir))
            {
                return Directory.EnumerateFiles(dir, "*.slangp", SearchOption.TopDirectoryOnly)
                    .OrderBy(f => f, StringComparer.OrdinalIgnoreCase)
                    .ToList();
            }
        }
        return [];
    }

    private void UpdateOverlayPreview(StormPlatform plat, SystemOverlayProfile prof)
    {
        if (_overlayLivePreview != null)
        {
            _overlayLivePreview.Source = GbcaOverlayRenderer.RenderPreviewBitmap(280, 165, plat, prof);
        }
    }

    private static Border CreateSkinTile(StormPlatform plat, ConsoleSkinPalette pal, bool isSelected, Action onSelect)
    {
        var accent = StormThemeBrushes.AccentPrimaryColor;
        var tile = new Border
        {
            Width = 176,
            Margin = new Thickness(0, 0, 10, 10),
            Padding = new Thickness(8),
            CornerRadius = new CornerRadius(12),
            Background = isSelected ? StormThemeBrushes.HoverSurface : StormThemeBrushes.RowSurface,
            BorderBrush = isSelected ? new SolidColorBrush(accent) : StormThemeBrushes.SubtleBorder,
            BorderThickness = new Thickness(isSelected ? 2 : 1),
            Cursor = new Cursor(StandardCursorType.Hand)
        };

        var stack = new StackPanel { Spacing = 6 };
        var imgBorder = new Border
        {
            Width = 158,
            Height = 108,
            CornerRadius = new CornerRadius(8),
            ClipToBounds = true,
            Child = new Image
            {
                Width = 158,
                Height = 108,
                Stretch = Stretch.UniformToFill,
                Source = GbcaConsoleSkinRenderer.RenderPreviewBitmap(158, 108, plat, pal)
            }
        };
        stack.Children.Add(imgBorder);
        stack.Children.Add(new TextBlock
        {
            Text = pal.DisplayName == "None" ? "Без корпуса" : pal.DisplayName,
            FontSize = 11.5,
            FontWeight = isSelected ? FontWeight.Bold : FontWeight.SemiBold,
            Foreground = StormThemeBrushes.PrimaryText,
            HorizontalAlignment = HorizontalAlignment.Center,
            TextTrimming = TextTrimming.CharacterEllipsis
        });

        tile.Child = stack;
        tile.PointerPressed += (_, _) =>
        {
            AuroraTactileFeedbackService.Instance.PlayClick();
            onSelect();
        };
        return tile;
    }

    private static Grid CreateCombo(string label, string[] options, int selectedIdx, Action<int> onChanged)
    {
        var g = new Grid { ColumnDefinitions = new ColumnDefinitions("*,220"), Margin = new Thickness(0, 2) };
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
        var lbl = new TextBlock { Text = $"{label} {val:0.00} {unit}".Trim(), FontSize = 11, Foreground = StormThemeBrushes.BodyText };
        g.Children.Add(lbl);
        var sld = new AuroraVolumetricSlider { Minimum = min, Maximum = max, Value = Math.Clamp(val, min, max), Height = 22 };
        sld.ValueChanged += (_, v) =>
        {
            lbl.Text = $"{label} {v:0.00} {unit}".Trim();
            onChanged(v);
        };
        g.Children.Add(sld);
        Grid.SetRow(sld, 1);
        return g;
    }
}
