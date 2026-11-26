// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Apache License, Version 2.0.

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
    private int _selectedSystemTab; // 0 = GB, 1 = GBC, 2 = GBA

    public GbcaSkinAndOverlayViews()
    {
        Margin = new Thickness(16, 8, 16, 12);
        RowDefinitions = new RowDefinitions("Auto,*");

        var headerCard = new AuroraGlassCard { Padding = new Thickness(16, 12), Margin = new Thickness(0, 0, 0, 10), SpotlightEnabled = true };
        var hGrid = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto") };
        var titleCol = new StackPanel { Spacing = 3 };
        titleCol.Children.Add(new TextBlock { Text = "СТУДИЯ 3D-КОРПУСОВ И ОВЕРЛЕЕВ ПО СИСТЕМАМ", FontSize = 14.5, FontWeight = FontWeight.Bold, Foreground = StormThemeBrushes.Accent });
        titleCol.Children.Add(new TextBlock { Text = "Настройте отдельный 3D-корпус, рамку оверлея, стиль кнопок и LCD-фильтр отдельно для GB, GBC и GBA.", FontSize = 11.5, Foreground = StormThemeBrushes.SecondaryText });
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

        StormThemeBrushes.BindTheme(this, RefreshBody);
        RefreshBody();
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
        var ovGrid = new Grid { ColumnDefinitions = new ColumnDefinitions("*,300"), ColumnSpacing = 16 };

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

        ovGrid.Children.Add(controlsCol);

        _overlayLivePreview = new Image { Width = 280, Height = 165, Stretch = Stretch.Uniform };
        var previewFrame = new Border
        {
            CornerRadius = new CornerRadius(12),
            Background = new SolidColorBrush(Color.Parse("#0B0D16")),
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
        ovGrid.Children.Add(previewFrame);
        Grid.SetColumn(previewFrame, 1);

        overlayCard.Content = ovGrid;
        _bodyStack.Children.Add(overlayCard);
        UpdateOverlayPreview(plat, prof);
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
