// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Apache License, Version 2.0.

using Avalonia;
using Avalonia.Controls;
using Avalonia.Input;
using Avalonia.Layout;
using Avalonia.Media;
using Avalonia.Platform.Storage;
using Storm.AuroraFluid.Kit.Icons;
using Storm.AuroraFluid.Kit.Services;
using Storm.AuroraFluid.Kit.Themes;
using StormGbca.Core.Library;
using StormGbca.UI.Common;

namespace StormGbca.UI.Views.Bento;

public class BentoWorkspaceView : Grid
{
    private readonly WrapPanel _chipsPanel;
    private readonly WrapPanel _romsWrapPanel;
    private readonly ScrollViewer _cardsScrollViewer;
    private readonly CollectorTableView _collectorTable;
    private readonly List<(GbcaPlatformFilter Filter, Color Accent, Border Chip, TextBlock Label)> _chips = [];

    public BentoWorkspaceView()
    {
        Margin = new Thickness(16, 0, 16, 0);
        RowDefinitions = new RowDefinitions("Auto,Auto,*");

        var controlsBar = new Grid
        {
            Margin = new Thickness(0, 8, 0, 6),
            ColumnDefinitions = new ColumnDefinitions("*,Auto")
        };

        _chipsPanel = new WrapPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Center };
        AddChip(GbcaPlatformFilter.All, AuroraIconKind.FilterFunnel, "Все", Color.Parse("#38BDF8"));
        AddChip(GbcaPlatformFilter.Gb, AuroraIconKind.RomCartridge, "GB", Color.Parse("#8BAC0F"));
        AddChip(GbcaPlatformFilter.Gbc, AuroraIconKind.ThemePalette, "GBC", Color.Parse("#7B42F6"));
        AddChip(GbcaPlatformFilter.Gba, AuroraIconKind.GamepadController, "GBA", Color.Parse("#4F46E5"));
        AddChip(GbcaPlatformFilter.Favorites, AuroraIconKind.StarFavorite, "Избранное", Color.Parse("#EF4444"));
        controlsBar.Children.Add(_chipsPanel);

        var rightStack = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8, VerticalAlignment = VerticalAlignment.Center };
        var viewToggle = CreateActionButton(AuroraIconKind.LibraryGrid, "Вид таблицы", () =>
        {
            var next = StormGbcaUiState.Instance.ActiveViewMode == GbcaViewMode.BentoCards
                ? GbcaViewMode.CollectorTable
                : GbcaViewMode.BentoCards;
            StormGbcaUiState.Instance.SetViewMode(next);
        });
        var scanBtn = CreateActionButton(AuroraIconKind.FolderVault, "Добавить папку", async () => await PickAndScanFolderAsync());
        rightStack.Children.Add(viewToggle);
        rightStack.Children.Add(scanBtn);
        controlsBar.Children.Add(rightStack);
        Grid.SetColumn(rightStack, 1);

        Children.Add(controlsBar);

        var topBento = new Grid
        {
            Margin = new Thickness(0, 0, 0, 10),
            ColumnDefinitions = new ColumnDefinitions("*,330"),
            RowDefinitions = new RowDefinitions("Auto")
        };
        var heroCard = new BentoHeroCard();
        var hwCard = new BentoHardwareCard();
        topBento.Children.Add(heroCard);
        topBento.Children.Add(hwCard);
        Grid.SetColumn(hwCard, 1);

        PropertyChanged += (_, ch) =>
        {
            if (ch.Property == BoundsProperty && Bounds.Width > 0)
            {
                if (Bounds.Width < 800)
                {
                    topBento.ColumnDefinitions = new ColumnDefinitions("*");
                    topBento.RowDefinitions = new RowDefinitions("Auto,Auto");
                    Grid.SetColumn(hwCard, 0);
                    Grid.SetRow(hwCard, 1);
                    heroCard.Margin = new Thickness(0, 0, 0, 8);
                }
                else
                {
                    topBento.ColumnDefinitions = new ColumnDefinitions("*,330");
                    topBento.RowDefinitions = new RowDefinitions("Auto");
                    Grid.SetColumn(hwCard, 1);
                    Grid.SetRow(hwCard, 0);
                    heroCard.Margin = new Thickness(0, 0, 8, 8);
                }
            }
        };

        Children.Add(topBento);
        Grid.SetRow(topBento, 1);

        _cardsScrollViewer = new ScrollViewer
        {
            Padding = new Thickness(0, 0, 16, 12),
            HorizontalScrollBarVisibility = Avalonia.Controls.Primitives.ScrollBarVisibility.Disabled,
            VerticalScrollBarVisibility = Avalonia.Controls.Primitives.ScrollBarVisibility.Auto
        };
        _romsWrapPanel = new WrapPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Left };
        _cardsScrollViewer.Content = _romsWrapPanel;

        _collectorTable = new CollectorTableView { IsVisible = false };

        var container = new Grid();
        container.Children.Add(_cardsScrollViewer);
        container.Children.Add(_collectorTable);
        Children.Add(container);
        Grid.SetRow(container, 2);

        StormGbcaUiState.Instance.FilterChanged += RefreshGrid;
        StormGbcaUiState.Instance.StateChanged += RefreshGrid;
        StormThemeBrushes.BindTheme(this, () =>
        {
            UpdateChipStyles();
            RefreshGrid();
        });

        UpdateChipStyles();
        RefreshGrid();
    }

    private void AddChip(GbcaPlatformFilter filter, AuroraIconKind icon, string label, Color accent)
    {
        var chip = new Border
        {
            Height = 32,
            Padding = new Thickness(12, 4),
            Margin = new Thickness(0, 0, 8, 6),
            CornerRadius = new CornerRadius(16),
            BorderThickness = new Thickness(1),
            Cursor = new Cursor(StandardCursorType.Hand)
        };
        var stack = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 6, VerticalAlignment = VerticalAlignment.Center };
        stack.Children.Add(new AuroraVectorIcon { Kind = icon, Size = 14, VerticalAlignment = VerticalAlignment.Center });
        var tb = new TextBlock { Text = label, FontSize = 11.5, FontWeight = FontWeight.SemiBold, VerticalAlignment = VerticalAlignment.Center };
        stack.Children.Add(tb);
        chip.Child = stack;

        chip.PointerPressed += (_, _) =>
        {
            AuroraTactileFeedbackService.Instance.PlayClick();
            StormGbcaUiState.Instance.SetFilter(filter);
            UpdateChipStyles();
        };

        _chipsPanel.Children.Add(chip);
        _chips.Add((filter, accent, chip, tb));
    }

    private void UpdateChipStyles()
    {
        var active = StormGbcaUiState.Instance.ActiveFilter;
        foreach (var item in _chips)
        {
            bool sel = item.Filter == active;
            item.Chip.Background = sel
                ? new SolidColorBrush(Color.FromArgb(80, item.Accent.R, item.Accent.G, item.Accent.B))
                : StormThemeBrushes.RowSurface;
            item.Chip.BorderBrush = sel
                ? new SolidColorBrush(item.Accent)
                : StormThemeBrushes.SubtleBorder;
            item.Label.Foreground = sel ? StormThemeBrushes.PrimaryText : StormThemeBrushes.BodyText;
        }
    }

    private Border CreateActionButton(AuroraIconKind icon, string label, Action onClick)
    {
        var btn = new Border
        {
            Height = 32,
            Padding = new Thickness(10, 4),
            CornerRadius = new CornerRadius(8),
            Background = StormThemeBrushes.RowSurface,
            BorderBrush = StormThemeBrushes.SubtleBorder,
            BorderThickness = new Thickness(1),
            Cursor = new Cursor(StandardCursorType.Hand)
        };
        var stack = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 6, VerticalAlignment = VerticalAlignment.Center };
        stack.Children.Add(new AuroraVectorIcon { Kind = icon, Size = 14, VerticalAlignment = VerticalAlignment.Center });
        stack.Children.Add(new TextBlock { Text = label, FontSize = 11.5, FontWeight = FontWeight.Medium, Foreground = StormThemeBrushes.PrimaryText, VerticalAlignment = VerticalAlignment.Center });
        btn.Child = stack;
        btn.PointerPressed += (_, _) =>
        {
            AuroraTactileFeedbackService.Instance.PlayClick();
            onClick();
        };
        return btn;
    }

    private async Task PickAndScanFolderAsync()
    {
        var top = TopLevel.GetTopLevel(this);
        if (top?.StorageProvider == null)
        {
            await StormGbcaUiState.Instance.Library.ScanDirectoriesAsync(StormGbcaUiState.Instance.Settings.RomDirectories);
            return;
        }

        var folders = await top.StorageProvider.OpenFolderPickerAsync(new FolderPickerOpenOptions
        {
            Title = "Выберите папку с играми GB / GBC / GBA",
            AllowMultiple = false
        });
        string? folderPath = folders.Count > 0 ? folders[0].TryGetLocalPath() : null;
        if (!string.IsNullOrWhiteSpace(folderPath) && Directory.Exists(folderPath))
        {
            if (!StormGbcaUiState.Instance.Settings.RomDirectories.Contains(folderPath, StringComparer.OrdinalIgnoreCase))
            {
                StormGbcaUiState.Instance.Settings.RomDirectories.Add(folderPath);
                StormGbcaUiState.Instance.SaveSettings();
            }
            await StormGbcaUiState.Instance.Library.ScanDirectoriesAsync(StormGbcaUiState.Instance.Settings.RomDirectories);
            StormGbcaUiState.Instance.NotifyStatus($"Папка добавлена: {folderPath}");
        }
    }

    private void RefreshGrid()
    {
        UpdateChipStyles();
        bool isTable = StormGbcaUiState.Instance.ActiveViewMode == GbcaViewMode.CollectorTable;
        _cardsScrollViewer.IsVisible = !isTable;
        _collectorTable.IsVisible = isTable;

        if (!isTable)
        {
            _romsWrapPanel.Children.Clear();
            foreach (var rom in StormGbcaUiState.Instance.GetFilteredRoms())
            {
                _romsWrapPanel.Children.Add(new BentoRomCard(rom));
            }
        }
        else
        {
            _collectorTable.RefreshRows();
        }
    }
}
