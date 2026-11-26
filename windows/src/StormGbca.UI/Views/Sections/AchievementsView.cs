// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Apache License, Version 2.0.

using Avalonia;
using Avalonia.Controls;
using Avalonia.Input;
using Avalonia.Layout;
using Avalonia.Media;
using Avalonia.Threading;
using Storm.AuroraFluid.Kit.Controls;
using Storm.AuroraFluid.Kit.Icons;
using Storm.AuroraFluid.Kit.Services;
using StormGbca.Core.Configuration;
using StormGbca.Core.Subsystems;
using StormGbca.UI.Common;
using StormGbca.UI.Views.Bento;

namespace StormGbca.UI.Views.Sections;

public class AchievementsView : ScrollViewer
{
    private readonly TextBlock _accountStatusText;
    private readonly TextBlock _pointsSummaryText;
    private readonly TextBlock _hardcoreStatusText;
    private readonly TextBlock _actionResultText;
    private readonly StackPanel _selectedGameStack;
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
        var titleBlock = new TextBlock { Text = "ЦЕНТР ДОСТИЖЕНИЙ RETROACHIEVEMENTS", FontSize = 15, FontWeight = FontWeight.Bold };
        titleRow.Children.Add(titleBlock);
        hStack.Children.Add(titleRow);

        _accountStatusText = new TextBlock { FontSize = 13.5, FontWeight = FontWeight.Bold };
        _pointsSummaryText = new TextBlock { FontSize = 12, Foreground = new SolidColorBrush(Color.Parse("#FACC15")), FontWeight = FontWeight.SemiBold };
        _hardcoreStatusText = new TextBlock { FontSize = 12 };
        _actionResultText = new TextBlock { FontSize = 11.5 };
        hStack.Children.Add(_accountStatusText);
        hStack.Children.Add(_pointsSummaryText);
        hStack.Children.Add(_hardcoreStatusText);

        var actionsRow = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 10 };
        actionsRow.Children.Add(CreateBtn(AuroraIconKind.SyncDevices, "Обновить профиль и очки", async () =>
        {
            var ra = StormGbcaUiState.Instance.RetroAchievements;
            if (!ra.IsLoggedIn)
            {
                _actionResultText.Text = "Сначала выполните вход в аккаунт RetroAchievements в разделе Настройки.";
                return;
            }
            _actionResultText.Text = "Загрузка профиля с retroachievements.org...";
            bool ok = await ra.RefreshProfileAsync();
            await Dispatcher.UIThread.InvokeAsync(() =>
            {
                if (ok)
                {
                    var s = StormGbcaUiState.Instance.Settings;
                    s.RetroAchievementsPoints = ra.Points;
                    s.RetroAchievementsSoftcorePoints = ra.SoftcorePoints;
                    s.RetroAchievementsRank = ra.Rank;
                    StormGbcaUiState.Instance.SaveSettings();
                }
                _actionResultText.Text = ok ? ra.LastStatusMessage : "Не удалось обновить профиль.";
                RefreshData();
            });
        }));
        actionsRow.Children.Add(CreateBtn(AuroraIconKind.TrophyCrown, "Синхронизировать очередь", async () =>
        {
            _actionResultText.Text = "Выполняется отправка отложенных достижений...";
            int sent = await StormGbcaUiState.Instance.RetroAchievements.FlushOfflineQueueAsync();
            _actionResultText.Text = $"Отправлено достижений из очереди: {sent}";
        }));
        actionsRow.Children.Add(CreateBtn(AuroraIconKind.SettingsCore, "Аккаунт и вход", () =>
        {
            StormGbcaUiState.Instance.SetNavSection(NavSectionKind.Settings);
        }));
        hStack.Children.Add(actionsRow);
        hStack.Children.Add(_actionResultText);

        headerCard.Content = hStack;
        rootStack.Children.Add(headerCard);

        // Selected Game Achievements Inspector Card
        var detailCard = new AuroraGlassCard { Padding = new Thickness(16), SpotlightEnabled = true };
        _selectedGameStack = new StackPanel { Spacing = 8 };
        detailCard.Content = _selectedGameStack;
        rootStack.Children.Add(detailCard);

        var listCard = new AuroraGlassCard { Padding = new Thickness(16), SpotlightEnabled = true };
        var listRoot = new StackPanel { Spacing = 10 };
        var subHeader = new TextBlock { Text = "ИГРЫ БИБЛИОТЕКИ (НАЖМИТЕ ДЛЯ ЗАГРУЗКИ ДОСТИЖЕНИЙ)", FontSize = 11, FontWeight = FontWeight.Bold };
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
            ? $"Статус: Подключен ({ra.Username})"
            : "Статус: Профиль не подключен (перейдите в Настройки -> RetroAchievements)";
        _pointsSummaryText.Text = ra.IsLoggedIn
            ? $"Hardcore: {ra.Points} pts  |  Softcore: {ra.SoftcorePoints} pts{(ra.Rank > 0 ? $"  |  Рейтинг: #{ra.Rank}" : "")}"
            : "Очки: 0 pts";
        _hardcoreStatusText.Text = ra.IsHardcoreMode
            ? "Режим: Hardcore (загрузка состояний, перемотка и читы отключены)"
            : "Режим: Softcore (обычный)";

        RenderSessionDetails(ra.ActiveSession);

        _gamesListStack.Children.Clear();
        var roms = StormGbcaUiState.Instance.Library.AllRoms.Take(32).ToList();
        if (roms.Count == 0)
        {
            _gamesListStack.Children.Add(new TextBlock { Text = "В библиотеке пока нет игр для проверки достижений.", FontSize = 12, Foreground = StormThemeBrushes.SecondaryText });
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
            string hashStr = string.IsNullOrWhiteSpace(rom.RaHash) ? "Вычислится при выборе" : rom.RaHash;
            info.Children.Add(new TextBlock { Text = $"CRC32: {rom.Crc32Hex} | Хэш RetroAchievements: {hashStr}", FontSize = 10.5, Foreground = StormThemeBrushes.SecondaryText });
            g.Children.Add(info);
            Grid.SetColumn(info, 1);

            var openLbl = new TextBlock { Text = "Показать достижения", FontSize = 11.5, FontWeight = FontWeight.SemiBold, Foreground = StormThemeBrushes.Accent, VerticalAlignment = VerticalAlignment.Center };
            g.Children.Add(openLbl);
            Grid.SetColumn(openLbl, 2);

            row.Child = g;
            row.PointerPressed += (_, _) =>
            {
                AuroraTactileFeedbackService.Instance.PlayClick();
                _ = LoadRomAchievementsAsync(rom);
            };
            _gamesListStack.Children.Add(row);
        }
    }

    private async Task LoadRomAchievementsAsync(GbcaRomRecord rom)
    {
        var ra = StormGbcaUiState.Instance.RetroAchievements;
        if (!ra.IsLoggedIn)
        {
            _actionResultText.Text = "Войдите в аккаунт RetroAchievements, чтобы просматривать достижения игр.";
            return;
        }

        _actionResultText.Text = $"Загрузка достижений для {rom.GetDisplayTitle()}...";
        if (string.IsNullOrWhiteSpace(rom.RaHash))
        {
            rom.RaHash = await Task.Run(() => ra.ComputeRomHash(rom.FilePath, rom.Platform));
        }

        var session = await ra.FetchGameSessionAsync(rom.RaHash);
        await Dispatcher.UIThread.InvokeAsync(() =>
        {
            if (session != null)
            {
                _actionResultText.Text = $"{session.GameTitle}: открыто {session.UnlockedCount} из {session.TotalCount} ({session.EarnedPoints}/{session.TotalPoints} pts)";
                RenderSessionDetails(session);
            }
            else
            {
                _actionResultText.Text = $"Для {rom.GetDisplayTitle()} (хэш {rom.RaHash}) набор достижений на сервере не найден.";
            }
        });
    }

    private void RenderSessionDetails(RaGameSessionInfo? session)
    {
        _selectedGameStack.Children.Clear();
        if (session == null)
        {
            _selectedGameStack.Children.Add(new TextBlock
            {
                Text = "ДОСТИЖЕНИЯ ВЫБРАННОЙ ИГРЫ",
                FontSize = 11,
                FontWeight = FontWeight.Bold,
                Foreground = StormThemeBrushes.SecondaryText
            });
            _selectedGameStack.Children.Add(new TextBlock
            {
                Text = "Нажмите на любую игру ниже или запустите ее, чтобы увидеть прогресс, очки и список трофеев.",
                FontSize = 12,
                Foreground = StormThemeBrushes.SecondaryText
            });
            return;
        }

        _selectedGameStack.Children.Add(new TextBlock
        {
            Text = $"{session.GameTitle}  |  Открыто {session.UnlockedCount}/{session.TotalCount} ({session.CompletionPercent}%)  |  {session.EarnedPoints}/{session.TotalPoints} pts",
            FontSize = 13,
            FontWeight = FontWeight.Bold,
            Foreground = StormThemeBrushes.Accent
        });

        _selectedGameStack.Children.Add(new ProgressBar
        {
            Minimum = 0,
            Maximum = Math.Max(1, session.TotalCount),
            Value = session.UnlockedCount,
            Height = 8,
            CornerRadius = new CornerRadius(4)
        });

        foreach (var ach in session.Achievements.Take(40))
        {
            bool earned = ach.Unlocked || ach.UnlockedHardcore;
            Color accent = ach.UnlockedHardcore ? Color.Parse("#FACC15") : (ach.Unlocked ? Color.Parse("#10B981") : Color.Parse("#64748B"));
            var card = new Border
            {
                Padding = new Thickness(10, 7),
                CornerRadius = new CornerRadius(8),
                Background = StormThemeBrushes.RowSurface,
                BorderBrush = new SolidColorBrush(Color.FromArgb(earned ? (byte)180 : (byte)60, accent.R, accent.G, accent.B)),
                BorderThickness = new Thickness(1)
            };
            var g = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto") };
            var col = new StackPanel { Spacing = 2 };
            col.Children.Add(new TextBlock { Text = ach.Title, FontSize = 12, FontWeight = FontWeight.Bold, Foreground = StormThemeBrushes.PrimaryText });
            col.Children.Add(new TextBlock { Text = ach.Description, FontSize = 11, Foreground = StormThemeBrushes.SecondaryText, TextWrapping = TextWrapping.Wrap });
            g.Children.Add(col);

            var right = new StackPanel { Spacing = 2, HorizontalAlignment = HorizontalAlignment.Right, VerticalAlignment = VerticalAlignment.Center };
            right.Children.Add(new TextBlock { Text = $"{ach.Points} pts", FontSize = 11.5, FontWeight = FontWeight.Bold, Foreground = new SolidColorBrush(Color.Parse("#FACC15")), HorizontalAlignment = HorizontalAlignment.Right });
            right.Children.Add(new TextBlock { Text = ach.UnlockedHardcore ? "HARDCORE" : (ach.Unlocked ? "ОТКРЫТО" : "ЗАКРЫТО"), FontSize = 9.5, FontWeight = FontWeight.Bold, Foreground = new SolidColorBrush(accent), HorizontalAlignment = HorizontalAlignment.Right });
            g.Children.Add(right);
            Grid.SetColumn(right, 1);

            card.Child = g;
            _selectedGameStack.Children.Add(card);
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
