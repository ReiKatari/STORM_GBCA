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
using Storm.AuroraFluid.Kit.Icons;
using Storm.AuroraFluid.Kit.Services;
using Storm.AuroraFluid.Kit.Themes;
using StormGbca.Core.Library;
using StormGbca.Core.Configuration;
using StormGbca.UI.Common;

namespace StormGbca.UI.Views.Bento;

public class BentoWorkspaceView : Grid
{
    private readonly WrapPanel _chipsPanel;
    private readonly WrapPanel _romsWrapPanel;
    private readonly ScrollViewer _workspaceScroll;
    private readonly CollectorTableView _collectorTable;
    private readonly Border _viewToggleBtn;
    private readonly TextBlock _viewToggleLabel;
    private readonly Border _scanBtn;
    private readonly TextBlock _scanLabel;
    private readonly List<(GbcaPlatformFilter Filter, Color Accent, Border Chip, TextBlock Label)> _chips = [];

    public BentoWorkspaceView()
    {
        Margin = new Thickness(16, 0, 16, 0);
        RowDefinitions = new RowDefinitions("Auto,*");

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
        (_viewToggleBtn, _viewToggleLabel) = CreateActionButton(AuroraIconKind.LibraryGrid, "Таблица", () =>
        {
            var next = StormGbcaUiState.Instance.ActiveViewMode == GbcaViewMode.BentoCards
                ? GbcaViewMode.CollectorTable
                : GbcaViewMode.BentoCards;
            StormGbcaUiState.Instance.SetViewMode(next);
        });
        (_scanBtn, _scanLabel) = CreateActionButton(AuroraIconKind.FolderVault, "Добавить папку", async () => await PickAndScanFolderAsync());
        rightStack.Children.Add(_viewToggleBtn);
        rightStack.Children.Add(_scanBtn);
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

        _romsWrapPanel = new WrapPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Left };
        _collectorTable = new CollectorTableView { IsVisible = false, MinHeight = 340 };

        var workspaceStack = new StackPanel { Spacing = 4 };
        workspaceStack.Children.Add(topBento);
        workspaceStack.Children.Add(_romsWrapPanel);
        workspaceStack.Children.Add(_collectorTable);

        _workspaceScroll = new ScrollViewer
        {
            Padding = new Thickness(0, 0, 12, 12),
            HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled,
            VerticalScrollBarVisibility = ScrollBarVisibility.Auto,
            Content = workspaceStack
        };
        Children.Add(_workspaceScroll);
        Grid.SetRow(_workspaceScroll, 1);

        PropertyChanged += (_, ch) =>
        {
            if (ch.Property == BoundsProperty && Bounds.Width > 0)
            {
                bool stacked = Bounds.Width < 880;
                if (stacked)
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
                double reserve = stacked ? 68 : 250;
                _collectorTable.Height = Math.Max(340, Bounds.Height - reserve);
            }
        };

        StormGbcaUiState.Instance.Library.LibraryChanged += () => Dispatcher.UIThread.Post(RefreshGrid);
        StormGbcaUiState.Instance.FilterChanged += RefreshGrid;
        StormGbcaUiState.Instance.SettingsSaved += RefreshGrid;
        StormGbcaUiState.Instance.EmulationStopped += RefreshGrid;
        StormGbcaUiState.Instance.StateChanged += RefreshGrid;
        StormGbcaUiState.Instance.RomSelected += UpdateSelectionVisual;
        StormThemeBrushes.BindTheme(this, () =>
        {
            UpdateChipStyles();
            UpdateActionButtonBrushes();
            RefreshGrid();
        });

        UpdateChipStyles();
        UpdateActionButtonBrushes();
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

    private static (Border Button, TextBlock Label) CreateActionButton(AuroraIconKind icon, string label, Action onClick)
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
        var tb = new TextBlock { Text = label, FontSize = 11.5, FontWeight = FontWeight.Medium, Foreground = StormThemeBrushes.PrimaryText, VerticalAlignment = VerticalAlignment.Center };
        stack.Children.Add(tb);
        btn.Child = stack;
        btn.PointerPressed += (_, _) =>
        {
            AuroraTactileFeedbackService.Instance.PlayClick();
            onClick();
        };
        return (btn, tb);
    }

    private void UpdateActionButtonBrushes()
    {
        _viewToggleBtn.Background = StormThemeBrushes.RowSurface;
        _viewToggleBtn.BorderBrush = StormThemeBrushes.SubtleBorder;
        _viewToggleLabel.Foreground = StormThemeBrushes.PrimaryText;
        _scanBtn.Background = StormThemeBrushes.RowSurface;
        _scanBtn.BorderBrush = StormThemeBrushes.SubtleBorder;
        _scanLabel.Foreground = StormThemeBrushes.PrimaryText;
    }

    private void UpdateSelectionVisual(GbcaRomRecord? selected)
    {
        if (!Dispatcher.UIThread.CheckAccess())
        {
            Dispatcher.UIThread.Post(() => UpdateSelectionVisual(selected));
            return;
        }
        foreach (var child in _romsWrapPanel.Children)
        {
            if (child is BentoRomCard card)
            {
                bool isSel = selected != null && string.Equals(selected.FilePath, card.Rom.FilePath, StringComparison.OrdinalIgnoreCase);
                card.UpdateSelectedState(isSel);
            }
        }
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
        if (!Dispatcher.UIThread.CheckAccess())
        {
            Dispatcher.UIThread.Post(RefreshGrid);
            return;
        }
        UpdateChipStyles();
        bool isTable = StormGbcaUiState.Instance.ActiveViewMode == GbcaViewMode.CollectorTable;
        _viewToggleLabel.Text = isTable ? "Сетка" : "Таблица";
        _romsWrapPanel.IsVisible = !isTable;
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
