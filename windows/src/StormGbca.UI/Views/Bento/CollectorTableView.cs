// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Apache License, Version 2.0.

using Avalonia;
using Avalonia.Controls;
using Avalonia.Input;
using Avalonia.Layout;
using Avalonia.Media;
using Storm.AuroraFluid.Kit.Services;
using StormGbca.Core.Library;
using StormGbca.UI.Common;

namespace StormGbca.UI.Views.Bento;

public class CollectorTableView : Border
{
    private readonly StackPanel _rowsStack;

    public CollectorTableView()
    {
        BorderThickness = new Thickness(1);
        CornerRadius = new CornerRadius(12);
        ClipToBounds = true;

        var mainStack = new StackPanel();
        var header = new Grid
        {
            Height = 36,
            ColumnDefinitions = new ColumnDefinitions("70,*,90,80,90,95,85,125")
        };

        header.Children.Add(CreateHeaderCell("ПЛАТФОРМА", 0));
        header.Children.Add(CreateHeaderCell("НАЗВАНИЕ ИГРЫ", 1));
        header.Children.Add(CreateHeaderCell("РЕГИОН", 2));
        header.Children.Add(CreateHeaderCell("КОД", 3));
        header.Children.Add(CreateHeaderCell("РАЗМЕР", 4));
        header.Children.Add(CreateHeaderCell("CRC32", 5));
        header.Children.Add(CreateHeaderCell("ЗАПУСКОВ", 6));
        header.Children.Add(CreateHeaderCell("ДАТА ЗАПУСКА", 7));
        mainStack.Children.Add(header);

        _rowsStack = new StackPanel();
        var scroll = new ScrollViewer
        {
            Content = _rowsStack,
            Padding = new Thickness(0, 0, 8, 0)
        };
        mainStack.Children.Add(scroll);
        Child = mainStack;

        StormGbcaUiState.Instance.FilterChanged += RefreshRows;
        StormGbcaUiState.Instance.StateChanged += RefreshRows;
        StormThemeBrushes.BindTheme(this, () =>
        {
            Background = StormThemeBrushes.GlassPanel;
            BorderBrush = StormThemeBrushes.SubtleBorder;
            header.Background = StormThemeBrushes.CardSurface;
            RefreshRows();
        });
        RefreshRows();
    }

    private static Border CreateHeaderCell(string title, int col)
    {
        var border = new Border
        {
            BorderBrush = new SolidColorBrush(Color.Parse("#25FFFFFF")),
            BorderThickness = new Thickness(col > 0 ? 1 : 0, 0, 0, 1),
            Padding = new Thickness(8, 0),
            VerticalAlignment = VerticalAlignment.Stretch
        };
        border.Child = new TextBlock
        {
            Text = title,
            FontSize = 10,
            FontWeight = FontWeight.Bold,
            Foreground = new SolidColorBrush(Color.Parse("#94A3B8")),
            VerticalAlignment = VerticalAlignment.Center
        };
        Grid.SetColumn(border, col);
        return border;
    }

    public void RefreshRows()
    {
        _rowsStack.Children.Clear();
        var roms = StormGbcaUiState.Instance.GetFilteredRoms();
        var selected = StormGbcaUiState.Instance.SelectedRom;

        foreach (var rom in roms)
        {
            bool isSel = string.Equals(selected?.FilePath, rom.FilePath, StringComparison.OrdinalIgnoreCase);
            Color platColor = StormGbcaUiState.GetPlatformBadgeColor(rom.Platform);

            var row = new Grid
            {
                Height = 42,
                Background = isSel ? StormThemeBrushes.HoverSurface : Brushes.Transparent,
                Cursor = new Cursor(StandardCursorType.Hand),
                ColumnDefinitions = new ColumnDefinitions("70,*,90,80,90,95,85,125")
            };

            var platCell = new Border
            {
                BorderBrush = new SolidColorBrush(Color.Parse("#25FFFFFF")),
                BorderThickness = new Thickness(0, 0, 0, 1),
                Padding = new Thickness(8, 0),
                Child = new Border
                {
                    HorizontalAlignment = HorizontalAlignment.Center,
                    VerticalAlignment = VerticalAlignment.Center,
                    Child = BentoRomCard.CreateTagBadge(rom.PlatformShortName, platColor, true)
                }
            };
            row.Children.Add(platCell);

            row.Children.Add(CreateDataCell(rom.GetDisplayTitle(), 1, true));
            row.Children.Add(CreateDataCell(rom.RegionBadgeCode, 2, false, "#38BDF8"));
            row.Children.Add(CreateDataCell(string.IsNullOrWhiteSpace(rom.GameCode) ? "-" : rom.GameCode, 3));
            row.Children.Add(CreateDataCell(BentoRomCard.FormatRomSize(rom.RomSize), 4));
            row.Children.Add(CreateDataCell(rom.Crc32Hex, 5, false, "#A855F7"));
            row.Children.Add(CreateDataCell(rom.PlayCount.ToString(), 6));
            row.Children.Add(CreateDataCell(rom.LastPlayedUtc.HasValue ? $"{rom.LastPlayedUtc.Value.ToLocalTime():dd.MM.yyyy HH:mm}" : "-", 7));

            row.PointerPressed += (_, _) =>
            {
                AuroraTactileFeedbackService.Instance.PlayClick();
                StormGbcaUiState.Instance.SelectRom(rom);
            };
            row.DoubleTapped += (_, _) => StormGbcaUiState.Instance.StartEmulation(rom);

            _rowsStack.Children.Add(row);
        }
    }

    private static Border CreateDataCell(string text, int col, bool bold = false, string? hexColor = null)
    {
        var border = new Border
        {
            BorderBrush = new SolidColorBrush(Color.Parse("#25FFFFFF")),
            BorderThickness = new Thickness(col > 0 ? 1 : 0, 0, 0, 1),
            Padding = new Thickness(8, 0),
            VerticalAlignment = VerticalAlignment.Stretch
        };
        border.Child = new TextBlock
        {
            Text = text,
            FontSize = 11.5,
            FontWeight = bold ? FontWeight.Bold : FontWeight.Normal,
            Foreground = hexColor != null ? new SolidColorBrush(Color.Parse(hexColor)) : StormThemeBrushes.BodyText,
            VerticalAlignment = VerticalAlignment.Center,
            TextTrimming = TextTrimming.CharacterEllipsis
        };
        Grid.SetColumn(border, col);
        return border;
    }
}
