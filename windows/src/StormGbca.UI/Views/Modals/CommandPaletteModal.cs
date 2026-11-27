// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Apache License, Version 2.0.

using Avalonia;
using Avalonia.Controls;
using Avalonia.Input;
using Avalonia.Layout;
using Avalonia.Media;
using Storm.AuroraFluid.Kit.Controls;
using Storm.AuroraFluid.Kit.Icons;
using Storm.AuroraFluid.Kit.Services;
using Storm.AuroraFluid.Kit.Themes;
using StormGbca.UI.Common;

namespace StormGbca.UI.Views.Modals;

public class CommandPaletteModal : AuroraGlassCard
{
    private readonly AuroraSearchBox _searchBox;
    private readonly StackPanel _resultsStack;
    private readonly List<(AuroraIconKind Icon, string Title, string Sub, Action Action)> _actions = [];

    public event Action? CloseRequested;

    public CommandPaletteModal()
    {
        MaxWidth = 560;
        MaxHeight = 460;
        Margin = new Thickness(24, 18);
        HorizontalAlignment = HorizontalAlignment.Stretch;
        VerticalAlignment = VerticalAlignment.Center;
        Padding = new Thickness(16);
        SpotlightEnabled = true;

        var mainGrid = new Grid { RowDefinitions = new RowDefinitions("Auto,*"), RowSpacing = 10 };
        _searchBox = new AuroraSearchBox
        {
            PlaceholderText = "Введите команду или название игры...",
            HotkeyHint = "ESC для закрытия",
            Height = 40
        };
        _searchBox.TextChanged += (_, _) => FilterActions(_searchBox.Text ?? string.Empty);
        mainGrid.Children.Add(_searchBox);

        var scroll = new ScrollViewer { Padding = new Thickness(0, 0, 6, 0) };
        _resultsStack = new StackPanel { Spacing = 4 };
        scroll.Content = _resultsStack;
        mainGrid.Children.Add(scroll);
        Grid.SetRow(scroll, 1);
        Content = mainGrid;

        RegisterActions();
        StormThemeBrushes.BindTheme(this, () => FilterActions(_searchBox.Text ?? string.Empty));
        FilterActions(string.Empty);
    }

    private void RegisterActions()
    {
        _actions.Add((AuroraIconKind.PlayPrism, "Запустить выбранную игру", "Немедленный запуск активного картриджа GB/GBC/GBA", () =>
        {
            CloseRequested?.Invoke();
            StormGbcaUiState.Instance.StartEmulation();
        }));
        _actions.Add((AuroraIconKind.ShaderPrism, "Корпусы 3D и оверлеи", "Независимый выбор 3D-скинов и кнопок для GB, GBC и GBA", () =>
        {
            CloseRequested?.Invoke();
            StormGbcaUiState.Instance.SetNavSection(NavSectionKind.SkinsAndOverlays);
        }));
        _actions.Add((AuroraIconKind.SettingsCore, "Открыть настройки", "Ядра mGBA/SameBoy, видеофильтры, аудиомикшер и ввод", () =>
        {
            CloseRequested?.Invoke();
            StormGbcaUiState.Instance.SetNavSection(NavSectionKind.Settings);
        }));
        _actions.Add((AuroraIconKind.FolderVault, "Пересканировать папки игр", "Поиск файлов .gb, .gbc, .gba, .sgb, .zip, .7z", async () =>
        {
            CloseRequested?.Invoke();
            await StormGbcaUiState.Instance.Library.ScanDirectoriesAsync(StormGbcaUiState.Instance.Settings.RomDirectories);
        }));
        _actions.Add((AuroraIconKind.UpdateRocket, "Проверить обновления", "Двухзеркальная проверка GitHub и STORM GAMES WORLD", () =>
        {
            CloseRequested?.Invoke();
            StormGbcaUiState.Instance.RequestUpdateCheck(true);
        }));
    }

    private void FilterActions(string q)
    {
        _resultsStack.Children.Clear();
        string clean = q.Trim().ToLowerInvariant();

        var matched = string.IsNullOrWhiteSpace(clean)
            ? _actions
            : _actions.Where(a => a.Title.ToLowerInvariant().Contains(clean) || a.Sub.ToLowerInvariant().Contains(clean)).ToList();

        foreach (var a in matched)
        {
            _resultsStack.Children.Add(CreateRow(a.Icon, a.Title, a.Sub, a.Action));
        }

        var roms = string.IsNullOrWhiteSpace(clean)
            ? StormGbcaUiState.Instance.Library.AllRoms.Take(6)
            : StormGbcaUiState.Instance.Library.AllRoms
                .Where(r => r.GetDisplayTitle().ToLowerInvariant().Contains(clean) || r.GameCode.ToLowerInvariant().Contains(clean))
                .Take(12);

        foreach (var rom in roms)
        {
            var captured = rom;
            _resultsStack.Children.Add(CreateRow(
                AuroraIconKind.RomCartridge,
                captured.GetDisplayTitle(),
                $"{captured.PlatformShortName} | {captured.RegionBadgeCode} | CRC32: {captured.Crc32Hex}",
                () =>
                {
                    CloseRequested?.Invoke();
                    StormGbcaUiState.Instance.StartEmulation(captured);
                }));
        }
    }

    private static Border CreateRow(AuroraIconKind icon, string title, string sub, Action onExecute)
    {
        var row = new Border
        {
            Height = 44,
            Padding = new Thickness(10, 4),
            CornerRadius = new CornerRadius(8),
            Background = StormThemeBrushes.RowSurface,
            Cursor = new Cursor(StandardCursorType.Hand)
        };
        var g = new Grid { ColumnDefinitions = new ColumnDefinitions("Auto,*") };
        g.Children.Add(new AuroraVectorIcon { Kind = icon, Size = 18, VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(0, 0, 10, 0) });

        var s = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
        s.Children.Add(new TextBlock { Text = title, FontSize = 12, FontWeight = FontWeight.Bold, Foreground = StormThemeBrushes.PrimaryText });
        s.Children.Add(new TextBlock { Text = sub, FontSize = 9.5, Foreground = StormThemeBrushes.SecondaryText });
        g.Children.Add(s);
        Grid.SetColumn(s, 1);

        row.Child = g;
        row.PointerEntered += (_, _) => row.Background = StormThemeBrushes.HoverSurface;
        row.PointerExited += (_, _) => row.Background = StormThemeBrushes.RowSurface;
        row.PointerPressed += (_, _) =>
        {
            AuroraTactileFeedbackService.Instance.PlayClick();
            onExecute();
        };
        return row;
    }
}
