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
using StormGbca.Core.Library;
using StormGbca.Core.Localization;
using StormGbca.Core.Native;
using StormGbca.Core.Paths;
using StormGbca.UI.Common;
using StormGbca.UI.Views.Bento;

namespace StormGbca.UI.Views.Inspector;

public class RomInspectorPane : Border
{
    private readonly Image _coverImage;
    private readonly Border _coverFrame;
    private readonly TextBlock _headerTitle;
    private readonly TextBlock _slotsHeader;
    private readonly TextBlock _profileHeader;
    private readonly TextBlock _titleText;
    private readonly TextBlock _metaText;
    private readonly WrapPanel _badgesRow;
    private readonly TextBlock _skinVal;
    private readonly TextBlock _overlayVal;
    private readonly TextBlock _coreVal;
    private readonly UniformGrid _slotsGrid;
    private readonly AuroraVolumetricButton _sgwUpdateBtn;
    private readonly TextBlock _sgwUpdateBtnText;
    private readonly TextBlock _sgwUpdateHint;
    private readonly List<Border> _dividers = [];
    private readonly List<TextBlock> _metaLabels = [];
    private readonly List<(Button Btn, TextBlock Label)> _tabButtons = [];
    private readonly Action<GbcaRomRecord, int>? _onOpenModalTab;
    private GbcaRomRecord? _currentRom;

    public RomInspectorPane(Action<GbcaRomRecord, int>? onOpenModalTab = null)
    {
        _onOpenModalTab = onOpenModalTab;
        Width = 330;
        HorizontalAlignment = HorizontalAlignment.Right;
        VerticalAlignment = VerticalAlignment.Stretch;
        BorderThickness = new Thickness(1, 0, 0, 0);

        var scroll = new ScrollViewer
        {
            Padding = new Thickness(14, 12, 14, 12),
            HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled
        };

        var stack = new StackPanel { Spacing = 10 };

        _headerTitle = new TextBlock
        {
            Text = "ИНСПЕКТОР КАРТРИДЖА",
            FontSize = 10,
            FontWeight = FontWeight.Bold,
            Foreground = StormThemeBrushes.Accent,
            HorizontalAlignment = HorizontalAlignment.Center
        };
        stack.Children.Add(_headerTitle);

        _coverFrame = new Border
        {
            Width = 148,
            Height = 148,
            CornerRadius = new CornerRadius(12),
            Background = StormThemeBrushes.RowSurface,
            BorderThickness = new Thickness(1),
            HorizontalAlignment = HorizontalAlignment.Center,
            ClipToBounds = true,
            Cursor = new Cursor(StandardCursorType.Hand)
        };
        _coverImage = new Image { Stretch = Stretch.Uniform };
        _coverFrame.Child = _coverImage;
        _coverFrame.PointerPressed += (_, _) =>
        {
            if (_currentRom != null) StormGbcaUiState.Instance.RequestCoverViewer(_currentRom);
        };
        ToolTip.SetTip(_coverFrame, "Открыть обложку во весь экран");
        stack.Children.Add(_coverFrame);

        _titleText = new TextBlock
        {
            Text = "Выберите игру",
            FontSize = 14,
            FontWeight = FontWeight.Bold,
            TextAlignment = TextAlignment.Center,
            TextWrapping = TextWrapping.Wrap,
            MaxLines = 4
        };
        stack.Children.Add(_titleText);

        _badgesRow = new WrapPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Center };
        stack.Children.Add(_badgesRow);

        _metaText = new TextBlock
        {
            Text = "CRC32: -",
            FontSize = 11,
            HorizontalAlignment = HorizontalAlignment.Center,
            TextWrapping = TextWrapping.Wrap,
            TextAlignment = TextAlignment.Center
        };
        stack.Children.Add(_metaText);

        var playBtn = new AuroraVolumetricButton { IsHero = true, Height = 42, HorizontalAlignment = HorizontalAlignment.Stretch };
        var playStack = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
        playStack.Children.Add(new AuroraVectorIcon { Kind = AuroraIconKind.PlayPrism, Size = 16, VerticalAlignment = VerticalAlignment.Center });
        playStack.Children.Add(new TextBlock { Text = "Запустить игру", FontSize = 13, FontWeight = FontWeight.Bold, Foreground = Brushes.White, VerticalAlignment = VerticalAlignment.Center });
        playBtn.Content = playStack;
        playBtn.Click += (_, _) =>
        {
            if (_currentRom != null) StormGbcaUiState.Instance.StartEmulation(_currentRom);
        };
        stack.Children.Add(playBtn);

        _sgwUpdateBtn = new AuroraVolumetricButton
        {
            IsVisible = false,
            Height = 36,
            HorizontalAlignment = HorizontalAlignment.Stretch
        };
        var updStack = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 6, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
        updStack.Children.Add(new AuroraVectorIcon { Kind = AuroraIconKind.UpdateRocket, Size = 14, VerticalAlignment = VerticalAlignment.Center });
        _sgwUpdateBtnText = new TextBlock { Text = GbcaLoc.T("BtnUpdateFromSgw"), FontSize = 11.5, FontWeight = FontWeight.Bold, Foreground = StormThemeBrushes.PrimaryText, VerticalAlignment = VerticalAlignment.Center };
        updStack.Children.Add(_sgwUpdateBtnText);
        _sgwUpdateBtn.Content = updStack;
        _sgwUpdateBtn.Click += async (_, _) => await RunSgwUpdateAsync();
        stack.Children.Add(_sgwUpdateBtn);

        _sgwUpdateHint = new TextBlock
        {
            IsVisible = false,
            FontSize = 10,
            Foreground = new SolidColorBrush(Color.Parse("#10B981")),
            TextWrapping = TextWrapping.Wrap,
            TextAlignment = TextAlignment.Center
        };
        stack.Children.Add(_sgwUpdateHint);

        var div1 = new Border { Height = 1, Background = StormThemeBrushes.SubtleBorder, Margin = new Thickness(0, 2) };
        _dividers.Add(div1);
        stack.Children.Add(div1);

        _slotsHeader = new TextBlock
        {
            Text = "БЫСТРЫЕ СЛОТЫ СОХРАНЕНИЯ (1..4)",
            FontSize = 10,
            FontWeight = FontWeight.Bold,
            Foreground = StormThemeBrushes.SecondaryText
        };
        stack.Children.Add(_slotsHeader);
        _slotsGrid = new UniformGrid { Columns = 2, Rows = 2 };
        stack.Children.Add(_slotsGrid);

        var div2 = new Border { Height = 1, Background = StormThemeBrushes.SubtleBorder, Margin = new Thickness(0, 2) };
        _dividers.Add(div2);
        stack.Children.Add(div2);

        _profileHeader = new TextBlock
        {
            Text = "ПРОФИЛЬ СИСТЕМЫ",
            FontSize = 10,
            FontWeight = FontWeight.Bold,
            Foreground = StormThemeBrushes.SecondaryText
        };
        stack.Children.Add(_profileHeader);
        stack.Children.Add(CreateMetaRow("Активное ядро:", out _coreVal));
        stack.Children.Add(CreateMetaRow("3D-корпус:", out _skinVal));
        stack.Children.Add(CreateMetaRow("Оверлей:", out _overlayVal));

        var tabsRow = new UniformGrid { Columns = 3, Margin = new Thickness(0, 6, 0, 0) };
        tabsRow.Children.Add(CreateTabBtn(AuroraIconKind.InfoCrystal, "Свойства", 0));
        tabsRow.Children.Add(CreateTabBtn(AuroraIconKind.CheatMatrix, "Читы", 1));
        tabsRow.Children.Add(CreateTabBtn(AuroraIconKind.SaveSlotRibbon, "Сохранения", 2));
        stack.Children.Add(tabsRow);

        scroll.Content = stack;
        Child = scroll;

        StormGbcaUiState.Instance.RomSelected += UpdatePane;
        StormGbcaUiState.Instance.RomUpdatesChanged += () => Avalonia.Threading.Dispatcher.UIThread.Post(() => UpdatePane(_currentRom));
        StormGbcaUiState.Instance.SettingsSaved += () => Avalonia.Threading.Dispatcher.UIThread.Post(() => UpdatePane(_currentRom));
        StormGbcaUiState.Instance.SkinOrOverlayChanged += () => Avalonia.Threading.Dispatcher.UIThread.Post(() => UpdatePane(_currentRom));
        StormGbcaUiState.Instance.Library.CoverArtUpdated += updated =>
        {
            if (_currentRom != null && string.Equals(updated.FilePath, _currentRom.FilePath, StringComparison.OrdinalIgnoreCase))
            {
                Avalonia.Threading.Dispatcher.UIThread.Post(() => UpdatePane(_currentRom));
            }
        };

        StormThemeBrushes.BindTheme(this, () =>
        {
            Background = StormThemeBrushes.GlassPanel;
            BorderBrush = StormThemeBrushes.SubtleBorder;
            _headerTitle.Foreground = StormThemeBrushes.Accent;
            _slotsHeader.Foreground = StormThemeBrushes.SecondaryText;
            _profileHeader.Foreground = StormThemeBrushes.SecondaryText;
            _coverFrame.Background = StormThemeBrushes.RowSurface;
            _coverFrame.BorderBrush = StormThemeBrushes.StrongBorder;
            _titleText.Foreground = StormThemeBrushes.PrimaryText;
            _metaText.Foreground = StormThemeBrushes.SecondaryText;
            foreach (var d in _dividers) d.Background = StormThemeBrushes.SubtleBorder;
            foreach (var lbl in _metaLabels) lbl.Foreground = StormThemeBrushes.SecondaryText;
            _coreVal.Foreground = StormThemeBrushes.BodyText;
            _skinVal.Foreground = StormThemeBrushes.BodyText;
            _overlayVal.Foreground = StormThemeBrushes.BodyText;
            foreach (var (btn, tb) in _tabButtons)
            {
                btn.Background = StormThemeBrushes.RowSurface;
                btn.BorderBrush = StormThemeBrushes.SubtleBorder;
                tb.Foreground = StormThemeBrushes.BodyText;
            }
            UpdatePane(_currentRom);
        });
        UpdatePane(StormGbcaUiState.Instance.SelectedRom);
    }

    private Button CreateTabBtn(AuroraIconKind icon, string title, int tabIdx)
    {
        var btn = new Button
        {
            Margin = new Thickness(2),
            Padding = new Thickness(4, 6),
            Background = StormThemeBrushes.RowSurface,
            BorderBrush = StormThemeBrushes.SubtleBorder,
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(8),
            Cursor = new Cursor(StandardCursorType.Hand),
            HorizontalContentAlignment = HorizontalAlignment.Center
        };
        var s = new StackPanel { Spacing = 3, HorizontalAlignment = HorizontalAlignment.Center };
        s.Children.Add(new AuroraVectorIcon { Kind = icon, Size = 14, HorizontalAlignment = HorizontalAlignment.Center });
        var tb = new TextBlock { Text = title, FontSize = 10, FontWeight = FontWeight.Medium, Foreground = StormThemeBrushes.BodyText, HorizontalAlignment = HorizontalAlignment.Center };
        s.Children.Add(tb);
        btn.Content = s;
        btn.Click += (_, _) =>
        {
            AuroraTactileFeedbackService.Instance.PlayClick();
            if (_currentRom != null) _onOpenModalTab?.Invoke(_currentRom, tabIdx);
        };
        _tabButtons.Add((btn, tb));
        return btn;
    }

    private Grid CreateMetaRow(string label, out TextBlock valText)
    {
        var g = new Grid { ColumnDefinitions = new ColumnDefinitions("Auto,*"), ColumnSpacing = 8, Margin = new Thickness(0, 2) };
        var lbl = new TextBlock { Text = label, FontSize = 11, Foreground = StormThemeBrushes.SecondaryText };
        _metaLabels.Add(lbl);
        g.Children.Add(lbl);
        valText = new TextBlock { Text = "-", FontSize = 11, FontWeight = FontWeight.SemiBold, Foreground = StormThemeBrushes.BodyText, HorizontalAlignment = HorizontalAlignment.Right, TextTrimming = TextTrimming.CharacterEllipsis };
        g.Children.Add(valText);
        Grid.SetColumn(valText, 1);
        return g;
    }

    private void UpdatePane(GbcaRomRecord? rom)
    {
        if (!Avalonia.Threading.Dispatcher.UIThread.CheckAccess())
        {
            Avalonia.Threading.Dispatcher.UIThread.Post(() => UpdatePane(rom));
            return;
        }
        _currentRom = rom;
        _badgesRow.Children.Clear();
        _slotsGrid.Children.Clear();

        var s = StormGbcaUiState.Instance.Settings;
        bool showCover = s.ShowLibretroCovers || s.ShowRaCovers;
        var plat = rom?.Platform ?? StormPlatform.Gba;

        _coreVal.Text = plat switch
        {
            StormPlatform.Gb or StormPlatform.Sgb => s.PreferredCoreGb.ToString(),
            StormPlatform.Gbc => s.PreferredCoreGbc.ToString(),
            _ => s.PreferredCoreGba.ToString(),
        };
        _skinVal.Text = plat switch
        {
            StormPlatform.Gb or StormPlatform.Sgb => s.GbConsoleSkin.ToString(),
            StormPlatform.Gbc => s.GbcConsoleSkin.ToString(),
            _ => s.GbaConsoleSkin.ToString(),
        };
        _overlayVal.Text = s.GetOverlayProfileForPlatform(plat).ButtonStyle.ToString();

        for (int i = 0; i < 4; i++)
        {
            _slotsGrid.Children.Add(CreateSlotTile(i));
        }

        if (rom == null)
        {
            _titleText.Text = $"STORM GBCA {GbcaLoc.AppVersion}";
            _metaText.Text = "Выберите игру в списке";
            _coverImage.Source = null;
            _coverFrame.IsVisible = false;
            _sgwUpdateBtn.IsVisible = false;
            _sgwUpdateHint.IsVisible = false;
            return;
        }

        var upd = StormGbcaUiState.Instance.GetUpdateForRom(rom);
        if (upd != null)
        {
            _sgwUpdateBtn.IsVisible = true;
            _sgwUpdateBtn.IsEnabled = true;
            _sgwUpdateBtn.BorderBrush = new SolidColorBrush(Color.Parse("#10B981"));
            _sgwUpdateBtnText.Text = GbcaLoc.T("BtnUpdateFromSgw");
            _sgwUpdateBtnText.Foreground = StormThemeBrushes.PrimaryText;
            _sgwUpdateHint.IsVisible = true;
            _sgwUpdateHint.Text = $"{upd.LocalVersion} -> {upd.RemoteVersion} | {GbcaLoc.T("SgwSaveMigrationNote")}";
            ToolTip.SetTip(_sgwUpdateBtn, GbcaLoc.T("CatalogUpdateTooltip"));
        }
        else
        {
            _sgwUpdateBtn.IsVisible = false;
            _sgwUpdateHint.IsVisible = false;
        }

        if (s.ShowPlatformBadge)
        {
            _badgesRow.Children.Add(BentoRomCard.CreateTagBadge(rom.PlatformShortName, StormGbcaUiState.GetPlatformBadgeColor(rom.Platform), true));
        }
        if (s.ShowRegionBadge)
        {
            _badgesRow.Children.Add(BentoRomCard.CreateTagBadge(rom.RegionBadgeCode, Color.Parse("#38BDF8"), false));
        }
        if (s.ShowHardwareBadges)
        {
            BentoRomCard.AddHardwareBadges(_badgesRow, rom.HardwareFlags);
        }
        if (s.ShowRaBadge)
        {
            _badgesRow.Children.Add(BentoRomCard.CreateTagBadge("RA", Color.Parse("#F59E0B"), true));
        }
        _badgesRow.IsVisible = _badgesRow.Children.Count > 0;

        _titleText.Text = rom.GetDisplayTitle();
        string ext = Path.GetExtension(rom.FileName).ToUpperInvariant(); if (string.IsNullOrWhiteSpace(ext)) ext = "-";
        _metaText.Text = $"{ext} | {BentoRomCard.FormatRomSize(rom.RomSize)} | CRC32: {rom.Crc32Hex}";
        _coverImage.Source = showCover ? BitmapHelper.LoadBitmapCached(rom.CoverLocalPath) : null;
        _coverFrame.IsVisible = showCover;
    }

    private Control CreateSlotTile(int slotIdx)
    {
        string timeText = "Пусто";
        if (_currentRom != null)
        {
            string key = !string.IsNullOrWhiteSpace(_currentRom.FileName) ? _currentRom.FileName : _currentRom.GameCode;
            string path = StormGbcaPaths.GetSaveStateFilePath(key, slotIdx);
            if (File.Exists(path))
            {
                timeText = $"{File.GetLastWriteTime(path):HH:mm dd.MM}";
            }
        }

        var border = new Border
        {
            Margin = new Thickness(3),
            Padding = new Thickness(7, 5),
            CornerRadius = new CornerRadius(8),
            Background = StormThemeBrushes.RowSurface,
            BorderBrush = StormThemeBrushes.SubtleBorder,
            BorderThickness = new Thickness(1)
        };
        var sp = new StackPanel { Spacing = 2 };
        sp.Children.Add(new TextBlock { Text = $"Слот {slotIdx + 1}", FontSize = 10.5, FontWeight = FontWeight.Bold, Foreground = StormThemeBrushes.PrimaryText });
        sp.Children.Add(new TextBlock { Text = timeText, FontSize = 9.5, Foreground = StormThemeBrushes.SecondaryText });
        border.Child = sp;
        return border;
    }

    private async Task RunSgwUpdateAsync()
    {
        if (_currentRom == null) return;
        var upd = StormGbcaUiState.Instance.GetUpdateForRom(_currentRom);
        if (upd == null) return;

        _sgwUpdateBtn.IsEnabled = false;
        var progress = new Progress<Core.Subsystems.SgwDownloadProgress>(p =>
        {
            Avalonia.Threading.Dispatcher.UIThread.Post(() =>
            {
                if (p.TotalBytes > 1)
                {
                    int pct = (int)Math.Clamp((double)p.DownloadedBytes * 100.0 / p.TotalBytes, 0.0, 100.0);
                    _sgwUpdateBtnText.Text = $"{pct}%";
                }
                else if (!string.IsNullOrWhiteSpace(p.StatusText))
                {
                    _sgwUpdateBtnText.Text = p.StatusText;
                }
            });
        });

        try
        {
            var res = await StormGbcaUiState.Instance.DownloadCatalogGameAsync(upd.RemoteGame, _currentRom, progress);
            if (!res.Success && !string.IsNullOrWhiteSpace(res.ErrorMessage))
            {
                StormGbcaUiState.Instance.NotifyStatus(res.ErrorMessage);
            }
        }
        finally
        {
            Avalonia.Threading.Dispatcher.UIThread.Post(() => UpdatePane(_currentRom));
        }
    }
}
