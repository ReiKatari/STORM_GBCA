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
using StormGbca.UI.Common;
using StormGbca.UI.Views.Bento;

namespace StormGbca.UI.Views.Sections;

public class AchievementsView : ScrollViewer
{
    private readonly TextBlock _accountStatusText;
    private readonly TextBlock _hardcoreStatusText;
    private readonly TextBlock _actionResultText;
    private readonly StackPanel _gamesListStack;

    public AchievementsView()
    {
        Margin = new Thickness(16, 8, 16, 8);
        Padding = new Thickness(0, 0, 14, 0);

        var rootStack = new StackPanel { Spacing = 14 };

        var headerCard = new AuroraGlassCard { Padding = new Thickness(18), SpotlightEnabled = true };
        var hStack = new StackPanel { Spacing = 10 };

        var titleRow = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8, VerticalAlignment = VerticalAlignment.Center };
        titleRow.Children.Add(new AuroraVectorIcon { Kind = AuroraIconKind.TrophyCrown, Size = 22, VerticalAlignment = VerticalAlignment.Center });
        var titleBlock = new TextBlock { Text = "ДОСТИЖЕНИЯ RETROACHIEVEMENTS", FontSize = 15, FontWeight = FontWeight.Bold };
        titleRow.Children.Add(titleBlock);
        hStack.Children.Add(titleRow);

        _accountStatusText = new TextBlock { FontSize = 12.5 };
        _hardcoreStatusText = new TextBlock { FontSize = 12 };
        _actionResultText = new TextBlock { FontSize = 11.5 };
        hStack.Children.Add(_accountStatusText);
        hStack.Children.Add(_hardcoreStatusText);

        var actionsRow = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 10 };
        actionsRow.Children.Add(CreateBtn(AuroraIconKind.SyncDevices, "Синхронизировать очередь", async () =>
        {
            _actionResultText.Text = "Выполняется отправка отложенных достижений...";
            int sent = await StormGbcaUiState.Instance.RetroAchievements.FlushOfflineQueueAsync();
            _actionResultText.Text = $"Отправлено достижений из очереди: {sent}";
        }));
        actionsRow.Children.Add(CreateBtn(AuroraIconKind.SettingsCore, "Настройки входа RetroAchievements", () =>
        {
            StormGbcaUiState.Instance.SetNavSection(NavSectionKind.Settings);
        }));
        hStack.Children.Add(actionsRow);
        hStack.Children.Add(_actionResultText);

        headerCard.Content = hStack;
        rootStack.Children.Add(headerCard);

        var listCard = new AuroraGlassCard { Padding = new Thickness(16), SpotlightEnabled = true };
        var listRoot = new StackPanel { Spacing = 10 };
        var subHeader = new TextBlock { Text = "ИГРЫ БИБЛИОТЕКИ И ХЭШИ RETROACHIEVEMENTS", FontSize = 11, FontWeight = FontWeight.Bold };
        listRoot.Children.Add(subHeader);
        _gamesListStack = new StackPanel { Spacing = 6 };
        listRoot.Children.Add(_gamesListStack);
        listCard.Content = listRoot;
        rootStack.Children.Add(listCard);

        Content = rootStack;

        StormThemeBrushes.BindTheme(this, () =>
        {
            titleBlock.Foreground = StormThemeBrushes.Accent;
            _accountStatusText.Foreground = StormThemeBrushes.PrimaryText;
            _hardcoreStatusText.Foreground = StormThemeBrushes.SecondaryText;
            _actionResultText.Foreground = StormThemeBrushes.AccentSecondary;
            subHeader.Foreground = StormThemeBrushes.SecondaryText;
            RefreshData();
        });
        StormGbcaUiState.Instance.StateChanged += RefreshData;
        StormGbcaUiState.Instance.SettingsSaved += RefreshData;
        RefreshData();
    }

    public void RefreshData()
    {
        var ra = StormGbcaUiState.Instance.RetroAchievements;
        _accountStatusText.Text = ra.IsLoggedIn
            ? $"Активный профиль: {ra.Username}"
            : "Профиль не подключен. Укажите логин и пароль или токен в разделе Настройки.";
        _hardcoreStatusText.Text = ra.IsHardcoreMode
            ? "Режим: Хардкорный (загрузка состояний, перемотка и читы отключены)"
            : "Режим: Обычный";

        _gamesListStack.Children.Clear();
        var roms = StormGbcaUiState.Instance.Library.AllRoms.Take(24).ToList();
        if (roms.Count == 0)
        {
            _gamesListStack.Children.Add(new TextBlock { Text = "В библиотеке пока нет игр для проверки хэшей.", FontSize = 12, Foreground = StormThemeBrushes.SecondaryText });
            return;
        }

        foreach (var rom in roms)
        {
            var row = new Border
            {
                Padding = new Thickness(12, 8),
                CornerRadius = new CornerRadius(8),
                Background = StormThemeBrushes.RowSurface,
                BorderBrush = StormThemeBrushes.SubtleBorder,
                BorderThickness = new Thickness(1),
                Cursor = new Cursor(StandardCursorType.Hand)
            };
            var g = new Grid { ColumnDefinitions = new ColumnDefinitions("Auto,*,Auto") };
            g.Children.Add(BentoRomCard.CreateTagBadge(rom.PlatformShortName, StormGbcaUiState.GetPlatformBadgeColor(rom.Platform), true));

            var info = new StackPanel { Spacing = 2, Margin = new Thickness(10, 0), VerticalAlignment = VerticalAlignment.Center };
            info.Children.Add(new TextBlock { Text = rom.GetDisplayTitle(), FontSize = 12.5, FontWeight = FontWeight.SemiBold, Foreground = StormThemeBrushes.PrimaryText });
            string hashStr = string.IsNullOrWhiteSpace(rom.RaHash) ? "Будет вычислен при открытии" : rom.RaHash;
            info.Children.Add(new TextBlock { Text = $"CRC32: {rom.Crc32Hex} | Хэш RetroAchievements: {hashStr}", FontSize = 10.5, Foreground = StormThemeBrushes.SecondaryText });
            g.Children.Add(info);
            Grid.SetColumn(info, 1);

            var openLbl = new TextBlock { Text = "Подробнее", FontSize = 11.5, FontWeight = FontWeight.SemiBold, Foreground = StormThemeBrushes.Accent, VerticalAlignment = VerticalAlignment.Center };
            g.Children.Add(openLbl);
            Grid.SetColumn(openLbl, 2);

            row.Child = g;
            row.PointerPressed += (_, _) =>
            {
                AuroraTactileFeedbackService.Instance.PlayClick();
                StormGbcaUiState.Instance.SelectRom(rom);
                StormGbcaUiState.Instance.RequestRomModal(rom, 0);
            };
            _gamesListStack.Children.Add(row);
        }
    }

    private static AuroraVolumetricButton CreateBtn(AuroraIconKind icon, string text, Action onClick)
    {
        var btn = new AuroraVolumetricButton { Height = 32, Padding = new Thickness(12, 2) };
        var sp = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 6, VerticalAlignment = VerticalAlignment.Center };
        sp.Children.Add(new AuroraVectorIcon { Kind = icon, Size = 14, VerticalAlignment = VerticalAlignment.Center });
        sp.Children.Add(new TextBlock { Text = text, FontSize = 11.5, FontWeight = FontWeight.SemiBold, Foreground = Brushes.White, VerticalAlignment = VerticalAlignment.Center });
        btn.Content = sp;
        btn.Click += (_, _) =>
        {
            AuroraTactileFeedbackService.Instance.PlayClick();
            onClick();
        };
        return btn;
    }
}
