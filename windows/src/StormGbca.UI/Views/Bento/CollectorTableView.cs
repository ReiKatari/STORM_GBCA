// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Apache License, Version 2.0.

using Avalonia;
using Avalonia.Controls;
using Avalonia.Controls.Primitives;
using Avalonia.Input;
using Avalonia.Layout;
using Avalonia.Media;
using Avalonia.Threading;
using Storm.AuroraFluid.Kit.Services;
using StormGbca.Core.Library;
using StormGbca.Core.Configuration;
using StormGbca.UI.Common;

namespace StormGbca.UI.Views.Bento;

public class CollectorTableView : Border
{
    private const string WideColumns = "70,*,90,80,90,95,85,125";
    private const string NarrowColumns = "66,*,76,0,76,0,0,108";

    private readonly Grid _header;
    private readonly StackPanel _rowsStack;
    private readonly List<Border> _headerCells = [];
    private readonly List<TextBlock> _headerTexts = [];
    private readonly List<(GbcaRomRecord Rom, Grid Row, List<Border> AllCells, List<TextBlock> BodyTexts, List<Control> SecondaryCols)> _rows = [];
    private bool _isNarrow;

    public CollectorTableView()
    {
        BorderThickness = new Thickness(1);
        CornerRadius = new CornerRadius(12);
        ClipToBounds = true;

        var mainGrid = new Grid { RowDefinitions = new RowDefinitions("Auto,*") };
        _header = new Grid
        {
            Height = 36,
            Margin = new Thickness(0, 0, 8, 0),
            ColumnDefinitions = new ColumnDefinitions(WideColumns)
        };

        _header.Children.Add(CreateHeaderCell("ПЛАТФОРМА", 0));
        _header.Children.Add(CreateHeaderCell("НАЗВАНИЕ ИГРЫ", 1));
        _header.Children.Add(CreateHeaderCell("РЕГИОН", 2));
        _header.Children.Add(CreateHeaderCell("КОД", 3));
        _header.Children.Add(CreateHeaderCell("РАЗМЕР", 4));
        _header.Children.Add(CreateHeaderCell("CRC32", 5));
        _header.Children.Add(CreateHeaderCell("ЗАПУСКОВ", 6));
        _header.Children.Add(CreateHeaderCell("ДАТА ЗАПУСКА", 7));
        mainGrid.Children.Add(_header);

        _rowsStack = new StackPanel();
        var scroll = new ScrollViewer
        {
            Content = _rowsStack,
            Padding = new Thickness(0, 0, 8, 0),
            VerticalScrollBarVisibility = ScrollBarVisibility.Auto
        };
        mainGrid.Children.Add(scroll);
        Grid.SetRow(scroll, 1);
        Child = mainGrid;

        PropertyChanged += (_, ch) =>
        {
            if (ch.Property == BoundsProperty && Bounds.Width > 0)
            {
                bool narrow = Bounds.Width < 820;
                if (narrow != _isNarrow)
                {
                    _isNarrow = narrow;
                    ApplyColumnResponsiveness();
                }
            }
        };

        StormGbcaUiState.Instance.Library.LibraryChanged += () => Dispatcher.UIThread.Post(RefreshRows);
        StormGbcaUiState.Instance.FilterChanged += RefreshRows;
        StormGbcaUiState.Instance.SettingsSaved += RefreshRows;
        StormGbcaUiState.Instance.EmulationStopped += RefreshRows;
        StormGbcaUiState.Instance.StateChanged += RefreshRows;
        StormGbcaUiState.Instance.RomSelected += UpdateSelectionVisual;
        StormThemeBrushes.BindTheme(this, () =>
        {
            Background = StormThemeBrushes.GlassPanel;
            BorderBrush = StormThemeBrushes.SubtleBorder;
            _header.Background = StormThemeBrushes.CardSurface;
            var subtle = StormThemeBrushes.SubtleBorder;
            var sec = StormThemeBrushes.SecondaryText;
            foreach (var cell in _headerCells) cell.BorderBrush = subtle;
            foreach (var tb in _headerTexts) tb.Foreground = sec;
            RefreshRows();
        });
        RefreshRows();
    }

    private Border CreateHeaderCell(string title, int col)
    {
        var border = new Border
        {
            BorderBrush = StormThemeBrushes.SubtleBorder,
            BorderThickness = new Thickness(col > 0 ? 1 : 0, 0, 0, 1),
            Padding = new Thickness(8, 0),
            VerticalAlignment = VerticalAlignment.Stretch
        };
        var tb = new TextBlock
        {
            Text = title,
            FontSize = 10,
            FontWeight = FontWeight.Bold,
            Foreground = StormThemeBrushes.SecondaryText,
            VerticalAlignment = VerticalAlignment.Center,
            TextTrimming = TextTrimming.CharacterEllipsis
        };
        border.Child = tb;
        Grid.SetColumn(border, col);
        _headerCells.Add(border);
        _headerTexts.Add(tb);
        return border;
    }

    private void ApplyColumnResponsiveness()
    {
        string cols = _isNarrow ? NarrowColumns : WideColumns;
        _header.ColumnDefinitions = new ColumnDefinitions(cols);
        for (int i = 0; i < _headerCells.Count; i++)
        {
            if (i is 3 or 5 or 6) _headerCells[i].IsVisible = !_isNarrow;
        }
        foreach (var item in _rows)
        {
            item.Row.ColumnDefinitions = new ColumnDefinitions(cols);
            foreach (var c in item.SecondaryCols) c.IsVisible = !_isNarrow;
        }
    }

    public void UpdateSelectionVisual(GbcaRomRecord? selected)
    {
        if (!Dispatcher.UIThread.CheckAccess())
        {
            Dispatcher.UIThread.Post(() => UpdateSelectionVisual(selected));
            return;
        }
        foreach (var item in _rows)
        {
            bool isSel = selected != null && string.Equals(selected.FilePath, item.Rom.FilePath, StringComparison.OrdinalIgnoreCase);
            item.Row.Background = isSel ? StormThemeBrushes.HoverSurface : Brushes.Transparent;
        }
    }

    public void RefreshRows()
    {
        if (!Dispatcher.UIThread.CheckAccess())
        {
            Dispatcher.UIThread.Post(RefreshRows);
            return;
        }
        _rowsStack.Children.Clear();
        _rows.Clear();
        var roms = StormGbcaUiState.Instance.GetFilteredRoms();
        var selected = StormGbcaUiState.Instance.SelectedRom;
        string cols = _isNarrow ? NarrowColumns : WideColumns;
        var subtle = StormThemeBrushes.SubtleBorder;
        var bodyText = StormThemeBrushes.BodyText;

        foreach (var rom in roms)
        {
            bool isSel = string.Equals(selected?.FilePath, rom.FilePath, StringComparison.OrdinalIgnoreCase);
            Color platColor = StormGbcaUiState.GetPlatformBadgeColor(rom.Platform);

            var row = new Grid
            {
                Height = 42,
                Background = isSel ? StormThemeBrushes.HoverSurface : Brushes.Transparent,
                Cursor = new Cursor(StandardCursorType.Hand),
                ColumnDefinitions = new ColumnDefinitions(cols)
            };

            var allCells = new List<Border>();
            var bodyTexts = new List<TextBlock>();
            var secCols = new List<Control>();

            var platCell = new Border
            {
                BorderBrush = subtle,
                BorderThickness = new Thickness(0, 0, 0, 1),
                Padding = new Thickness(8, 0),
                Child = new Border
                {
                    HorizontalAlignment = HorizontalAlignment.Center,
                    VerticalAlignment = VerticalAlignment.Center,
                    Child = BentoRomCard.CreateTagBadge(rom.PlatformShortName, platColor, true)
                }
            };
            allCells.Add(platCell);
            row.Children.Add(platCell);

            row.Children.Add(CreateDataCell(rom.GetDisplayTitle(), 1, subtle, bodyText, allCells, bodyTexts, bold: true));
            row.Children.Add(CreateDataCell(rom.RegionBadgeCode, 2, subtle, bodyText, allCells, bodyTexts, hexColor: "#38BDF8"));

            var codeCell = CreateDataCell(string.IsNullOrWhiteSpace(rom.GameCode) ? "-" : rom.GameCode, 3, subtle, bodyText, allCells, bodyTexts);
            codeCell.IsVisible = !_isNarrow;
            secCols.Add(codeCell);
            row.Children.Add(codeCell);

            row.Children.Add(CreateDataCell(BentoRomCard.FormatRomSize(rom.RomSize), 4, subtle, bodyText, allCells, bodyTexts));

            var crcCell = CreateDataCell(rom.Crc32Hex, 5, subtle, bodyText, allCells, bodyTexts, hexColor: "#A855F7");
            crcCell.IsVisible = !_isNarrow;
            secCols.Add(crcCell);
            row.Children.Add(crcCell);

            var playCell = CreateDataCell(rom.PlayCount.ToString(), 6, subtle, bodyText, allCells, bodyTexts);
            playCell.IsVisible = !_isNarrow;
            secCols.Add(playCell);
            row.Children.Add(playCell);

            row.Children.Add(CreateDataCell(rom.LastPlayedUtc.HasValue ? $"{rom.LastPlayedUtc.Value.ToLocalTime():dd.MM.yyyy HH:mm}" : "-", 7, subtle, bodyText, allCells, bodyTexts));

            row.PointerPressed += (_, _) =>
            {
                AuroraTactileFeedbackService.Instance.PlayClick();
                StormGbcaUiState.Instance.SelectRom(rom);
            };
            row.DoubleTapped += (_, _) => StormGbcaUiState.Instance.StartEmulation(rom);

            _rows.Add((rom, row, allCells, bodyTexts, secCols));
            _rowsStack.Children.Add(row);
        }
    }

    private static Border CreateDataCell(
        string text,
        int col,
        IBrush borderBrush,
        IBrush defaultTextBrush,
        List<Border> allCells,
        List<TextBlock> bodyTexts,
        bool bold = false,
        string? hexColor = null)
    {
        var border = new Border
        {
            BorderBrush = borderBrush,
            BorderThickness = new Thickness(col > 0 ? 1 : 0, 0, 0, 1),
            Padding = new Thickness(8, 0),
            VerticalAlignment = VerticalAlignment.Stretch
        };
        var tb = new TextBlock
        {
            Text = text,
            FontSize = 11.5,
            FontWeight = bold ? FontWeight.Bold : FontWeight.Normal,
            Foreground = hexColor != null ? new SolidColorBrush(Color.Parse(hexColor)) : defaultTextBrush,
            VerticalAlignment = VerticalAlignment.Center,
            TextTrimming = TextTrimming.CharacterEllipsis
        };
        border.Child = tb;
        Grid.SetColumn(border, col);
        allCells.Add(border);
        if (hexColor == null) bodyTexts.Add(tb);
        return border;
    }
}
