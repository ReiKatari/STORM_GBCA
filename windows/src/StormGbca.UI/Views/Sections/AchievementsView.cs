// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Mozilla Public License, Version 2.0 (MPL-2.0).

using System.Diagnostics;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Controls.Primitives;
using Avalonia.Controls.Templates;
using Avalonia.Input;
using Avalonia.Layout;
using Avalonia.Media;
using Avalonia.Threading;
using Storm.AuroraFluid.Kit.Controls;
using Storm.AuroraFluid.Kit.Icons;
using Storm.AuroraFluid.Kit.Services;
using StormGbca.Core.Configuration;
using StormGbca.Core.Library;
using StormGbca.Core.Localization;
using StormGbca.Core.Subsystems;
using StormGbca.UI.Common;
using StormGbca.UI.Views.Bento;

namespace StormGbca.UI.Views.Sections;

public class AchievementsView : ScrollViewer
{
    private readonly TextBlock _titleBlock;
    private readonly TextBlock _accountStatusText;
    private readonly TextBlock _pointsSummaryText;
    private readonly TextBlock _hardcoreStatusText;
    private readonly TextBlock _actionResultText;
    private readonly StackPanel _selectedGameStack;
    private readonly ItemsControl _gamesItemsControl;
    private readonly TextBlock _emptyGamesText;
    private readonly List<TextBlock> _actionBtnLabels = [];
    private int _activeFilter; // 0=All, 1=Unlocked, 2=Locked, 3=Missable, 4=Leaderboards

    private static Color GetAmberColor() =>
        StormThemeBrushes.IsDark ? Color.Parse("#FACC15") : Color.Parse("#B45309");

    public AchievementsView()
    {
        Margin = new Thickness(16, 8, 16, 8);
        Padding = new Thickness(0, 0, 14, 0);

        var rootStack = new StackPanel { Spacing = 14 };

        var headerCard = new AuroraGlassCard { Padding = new Thickness(18), SpotlightEnabled = true };
        var hStack = new StackPanel { Spacing = 10 };

        var titleRow = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8, VerticalAlignment = VerticalAlignment.Center };
        titleRow.Children.Add(new AuroraVectorIcon { Kind = AuroraIconKind.TrophyCrown, Size = 22, VerticalAlignment = VerticalAlignment.Center });
        _titleBlock = new TextBlock { Text = GbcaLoc.T("RaCenterTitle"), FontSize = 15, FontWeight = FontWeight.Bold, TextWrapping = TextWrapping.Wrap };
        titleRow.Children.Add(_titleBlock);
        hStack.Children.Add(titleRow);

        _accountStatusText = new TextBlock { FontSize = 13.5, FontWeight = FontWeight.Bold, TextWrapping = TextWrapping.Wrap };
        _pointsSummaryText = new TextBlock { FontSize = 12, Foreground = new SolidColorBrush(GetAmberColor()), FontWeight = FontWeight.SemiBold, TextWrapping = TextWrapping.Wrap };
        _hardcoreStatusText = new TextBlock { FontSize = 12, TextWrapping = TextWrapping.Wrap };
        _actionResultText = new TextBlock { FontSize = 11.5, TextWrapping = TextWrapping.Wrap };
        hStack.Children.Add(_accountStatusText);
        hStack.Children.Add(_pointsSummaryText);
        hStack.Children.Add(_hardcoreStatusText);

        var actionsRow = new WrapPanel { Orientation = Orientation.Horizontal };
        actionsRow.Children.Add(CreateBtn(AuroraIconKind.SyncDevices, GbcaLoc.T("RaRefreshProfile"), async () =>
        {
            var ra = StormGbcaUiState.Instance.RetroAchievements;
            if (!ra.IsLoggedIn)
            {
                _actionResultText.Text = "RetroAchievements: sign in via Settings -> RetroAchievements";
                return;
            }
            _actionResultText.Text = "Connecting to retroachievements.org...";
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
                _actionResultText.Text = ok ? ra.LastStatusMessage : "Profile refresh failed";
                RefreshData();
            });
        }));
        actionsRow.Children.Add(CreateBtn(AuroraIconKind.CheckSuccess, GbcaLoc.T("RaTestConn"), async () =>
        {
            _actionResultText.Text = "Testing RetroAchievements server...";
            var (ok, _, details) = await StormGbcaUiState.Instance.RetroAchievements.TestConnectionAsync();
            await Dispatcher.UIThread.InvokeAsync(() =>
            {
                _actionResultText.Text = (ok ? "[OK] " : "[FAIL] ") + details;
                RefreshData();
            });
        }));
        actionsRow.Children.Add(CreateBtn(AuroraIconKind.TrophyCrown, GbcaLoc.T("RaSyncQueue"), async () =>
        {
            _actionResultText.Text = "Syncing offline queue...";
            int sent = await StormGbcaUiState.Instance.RetroAchievements.FlushOfflineQueueAsync();
            await Dispatcher.UIThread.InvokeAsync(() => _actionResultText.Text = $"Synced: {sent} | Pending: {StormGbcaUiState.Instance.RetroAchievements.PendingOfflineCount}");
        }));
        actionsRow.Children.Add(CreateBtn(AuroraIconKind.SettingsCore, GbcaLoc.T("RaAccountSettings"), () =>
        {
            StormGbcaUiState.Instance.SetNavSection(NavSectionKind.Settings);
        }));
        hStack.Children.Add(actionsRow);
        hStack.Children.Add(_actionResultText);

        headerCard.Content = hStack;
        rootStack.Children.Add(headerCard);

        var detailCard = new AuroraGlassCard { Padding = new Thickness(16), SpotlightEnabled = true };
        _selectedGameStack = new StackPanel { Spacing = 8 };
        detailCard.Content = _selectedGameStack;
        rootStack.Children.Add(detailCard);

        var listCard = new AuroraGlassCard { Padding = new Thickness(16), SpotlightEnabled = true };
        var listRoot = new StackPanel { Spacing = 10 };
        var subHeader = new TextBlock { Text = "LIBRARY GAMES - CLICK TO INSPECT ACHIEVEMENTS & LEADERBOARDS", FontSize = 11, FontWeight = FontWeight.Bold, TextWrapping = TextWrapping.Wrap };
        listRoot.Children.Add(subHeader);

        _emptyGamesText = new TextBlock { Text = "No games in library yet.", FontSize = 12, Foreground = StormThemeBrushes.SecondaryText, IsVisible = false };
        listRoot.Children.Add(_emptyGamesText);

        _gamesItemsControl = new ItemsControl
        {
            ItemsPanel = new FuncTemplate<Panel?>(() => new VirtualizingStackPanel()),
            ItemTemplate = new FuncDataTemplate<GbcaRomRecord>((rom, _) => BuildGameRow(rom), supportsRecycling: true)
        };
        var gamesScroll = new ScrollViewer
        {
            MaxHeight = 460,
            Padding = new Thickness(0, 0, 8, 0),
            HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled,
            VerticalScrollBarVisibility = ScrollBarVisibility.Auto,
            Content = _gamesItemsControl
        };
        listRoot.Children.Add(gamesScroll);
        listCard.Content = listRoot;
        rootStack.Children.Add(listCard);

        Content = rootStack;

        StormThemeBrushes.BindTheme(this, () =>
        {
            _titleBlock.Foreground = StormThemeBrushes.Accent;
            _accountStatusText.Foreground = StormThemeBrushes.PrimaryText;
            _pointsSummaryText.Foreground = new SolidColorBrush(GetAmberColor());
            _hardcoreStatusText.Foreground = StormThemeBrushes.SecondaryText;
            _actionResultText.Foreground = StormThemeBrushes.AccentSecondary;
            subHeader.Foreground = StormThemeBrushes.SecondaryText;
            _emptyGamesText.Foreground = StormThemeBrushes.SecondaryText;
            foreach (var lbl in _actionBtnLabels) lbl.Foreground = StormThemeBrushes.PrimaryText;
            RefreshData();
        });
        GbcaLoc.LanguageChanged += _ => Dispatcher.UIThread.Post(() =>
        {
            _titleBlock.Text = GbcaLoc.T("RaCenterTitle");
            RefreshData();
        });
        StormGbcaUiState.Instance.Library.LibraryChanged += () => Dispatcher.UIThread.Post(RefreshData);
        StormGbcaUiState.Instance.StateChanged += RefreshData;
        StormGbcaUiState.Instance.SettingsSaved += RefreshData;
        RefreshData();
    }

    private Border BuildGameRow(GbcaRomRecord rom)
    {
        var row = new Border
        {
            Margin = new Thickness(0, 0, 0, 6),
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
        info.Children.Add(new TextBlock { Text = rom.GetDisplayTitle(), FontSize = 12.5, FontWeight = FontWeight.SemiBold, Foreground = StormThemeBrushes.PrimaryText, TextWrapping = TextWrapping.Wrap });
        string hashStr = string.IsNullOrWhiteSpace(rom.RaHash) ? "Pending hash" : rom.RaHash;
        info.Children.Add(new TextBlock { Text = $"CRC32: {rom.Crc32Hex} | RA Hash: {hashStr}", FontSize = 10.5, Foreground = StormThemeBrushes.SecondaryText, TextWrapping = TextWrapping.Wrap });
        g.Children.Add(info);
        Grid.SetColumn(info, 1);

        var openLbl = new TextBlock { Text = "Inspect ->", FontSize = 11.5, FontWeight = FontWeight.SemiBold, Foreground = StormThemeBrushes.Accent, VerticalAlignment = VerticalAlignment.Center };
        g.Children.Add(openLbl);
        Grid.SetColumn(openLbl, 2);

        row.Child = g;
        row.PointerPressed += (_, _) =>
        {
            AuroraTactileFeedbackService.Instance.PlayClick();
            _ = LoadRomAchievementsAsync(rom);
        };
        return row;
    }

    public void RefreshData()
    {
        if (!Dispatcher.UIThread.CheckAccess())
        {
            Dispatcher.UIThread.Post(RefreshData);
            return;
        }
        var ra = StormGbcaUiState.Instance.RetroAchievements;
        _accountStatusText.Text = ra.IsLoggedIn
            ? $"Connected: {ra.Username}"
            : "Not connected (Settings -> RetroAchievements)";
        _pointsSummaryText.Text = ra.IsLoggedIn
            ? $"Hardcore: {ra.Points} pts  |  Casual: {ra.SoftcorePoints} pts{(ra.Rank > 0 ? $"  |  Rank: #{ra.Rank}" : "")}  |  Queue: {ra.PendingOfflineCount}"
            : "Points: 0 pts";
        _hardcoreStatusText.Text = ra.IsHardcoreMode
            ? "Mode: Hardcore (save state load, rewind, cheats, RAM finder, overclock & slowdown disabled)"
            : "Mode: Casual (standard)";

        RenderSessionDetails(ra.ActiveSession);

        var roms = StormGbcaUiState.Instance.Library.AllRoms.ToList();
        _emptyGamesText.IsVisible = roms.Count == 0;
        _gamesItemsControl.ItemsSource = roms;
    }

    private async Task LoadRomAchievementsAsync(GbcaRomRecord rom)
    {
        var ra = StormGbcaUiState.Instance.RetroAchievements;
        if (!ra.IsLoggedIn)
        {
            _actionResultText.Text = "Sign in to RetroAchievements first in Settings.";
            return;
        }

        _actionResultText.Text = $"Loading achievements for {rom.GetDisplayTitle()}...";
        if (string.IsNullOrWhiteSpace(rom.RaHash))
        {
            rom.RaHash = await Task.Run(() => ra.ComputeRomHash(rom.FilePath, rom.Platform));
        }

        var session = await ra.FetchGameSessionAsync(rom.RaHash);
        await Dispatcher.UIThread.InvokeAsync(() =>
        {
            if (session != null)
            {
                _actionResultText.Text = $"{session.GameTitle}: {session.UnlockedCount}/{session.TotalCount} ({session.EarnedPoints}/{session.TotalPoints} pts) | {session.Leaderboards.Count} leaderboards";
                RenderSessionDetails(session);
            }
            else
            {
                _actionResultText.Text = $"No official RetroAchievements set found for {rom.GetDisplayTitle()} (hash {rom.RaHash}).";
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
                Text = "SELECTED GAME ACHIEVEMENTS & LEADERBOARDS",
                FontSize = 11,
                FontWeight = FontWeight.Bold,
                Foreground = StormThemeBrushes.SecondaryText
            });
            _selectedGameStack.Children.Add(new TextBlock
            {
                Text = "Select any game below or launch it to inspect Hardcore/Casual trophies, missable flags, and leaderboards.",
                FontSize = 12,
                Foreground = StormThemeBrushes.SecondaryText
            });
            return;
        }

        var topRow = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto"), ColumnSpacing = 10 };
        topRow.Children.Add(new TextBlock
        {
            Text = $"{session.GameTitle}  |  {session.UnlockedCount}/{session.TotalCount} ({session.CompletionPercent}%)  |  {session.EarnedPoints}/{session.TotalPoints} pts",
            FontSize = 13,
            FontWeight = FontWeight.Bold,
            Foreground = StormThemeBrushes.Accent,
            TextWrapping = TextWrapping.Wrap,
            VerticalAlignment = VerticalAlignment.Center
        });
        var webBtn = CreateBtn(AuroraIconKind.TrophyCrown, GbcaLoc.T("RaOpenWeb"), () =>
        {
            try
            {
                Process.Start(new ProcessStartInfo($"https://retroachievements.org/game/{session.GameId}") { UseShellExecute = true });
            }
            catch { }
        }, registerThemeLabel: false);
        topRow.Children.Add(webBtn);
        Grid.SetColumn(webBtn, 1);
        _selectedGameStack.Children.Add(topRow);

        _selectedGameStack.Children.Add(new ProgressBar
        {
            Minimum = 0,
            Maximum = Math.Max(1, session.TotalCount),
            Value = session.UnlockedCount,
            Height = 8,
            CornerRadius = new CornerRadius(4)
        });

        // Filter Bar (All / Unlocked / Locked / Missable / Leaderboards)
        var filterWrap = new WrapPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 4, 0, 2) };
        string[] tabs =
        [
            $"{GbcaLoc.T("RaFilterAll")} ({session.TotalCount})",
            $"{GbcaLoc.T("RaFilterUnlocked")} ({session.UnlockedCount})",
            $"{GbcaLoc.T("RaFilterLocked")} ({session.TotalCount - session.UnlockedCount})",
            $"{GbcaLoc.T("RaFilterMissable")} ({session.Achievements.Count(a => a.Type.Equals("missable", StringComparison.OrdinalIgnoreCase))})",
            $"{GbcaLoc.T("RaFilterLeaderboards")} ({session.Leaderboards.Count})"
        ];
        for (int i = 0; i < tabs.Length; i++)
        {
            int idx = i;
            bool sel = _activeFilter == idx;
            var pill = new Border
            {
                Margin = new Thickness(0, 0, 6, 4),
                Padding = new Thickness(10, 4),
                CornerRadius = new CornerRadius(8),
                Background = sel ? StormThemeBrushes.HoverSurface : StormThemeBrushes.RowSurface,
                BorderBrush = sel ? StormThemeBrushes.Accent : StormThemeBrushes.SubtleBorder,
                BorderThickness = new Thickness(1),
                Cursor = new Cursor(StandardCursorType.Hand),
                Child = new TextBlock { Text = tabs[idx], FontSize = 11, FontWeight = FontWeight.SemiBold, Foreground = StormThemeBrushes.PrimaryText }
            };
            pill.PointerPressed += (_, _) =>
            {
                _activeFilter = idx;
                RenderSessionDetails(session);
            };
            filterWrap.Children.Add(pill);
        }
        _selectedGameStack.Children.Add(filterWrap);

        if (_activeFilter == 4)
        {
            if (session.Leaderboards.Count == 0)
            {
                _selectedGameStack.Children.Add(new TextBlock { Text = "No leaderboards available for this game.", FontSize = 12, Foreground = StormThemeBrushes.SecondaryText });
                return;
            }
            foreach (var lb in session.Leaderboards.Take(40))
            {
                var card = new Border
                {
                    Padding = new Thickness(10, 7),
                    CornerRadius = new CornerRadius(8),
                    Background = StormThemeBrushes.RowSurface,
                    BorderBrush = StormThemeBrushes.SubtleBorder,
                    BorderThickness = new Thickness(1)
                };
                var col = new StackPanel { Spacing = 2 };
                col.Children.Add(new TextBlock { Text = $"#{lb.Id}  {lb.Title} ({lb.Format})", FontSize = 12, FontWeight = FontWeight.Bold, Foreground = StormThemeBrushes.PrimaryText });
                col.Children.Add(new TextBlock { Text = lb.Description, FontSize = 11, Foreground = StormThemeBrushes.SecondaryText, TextWrapping = TextWrapping.Wrap });
                card.Child = col;
                _selectedGameStack.Children.Add(card);
            }
            return;
        }

        IEnumerable<RaAchievementInfo> filtered = _activeFilter switch
        {
            1 => session.Achievements.Where(a => a.Unlocked || a.UnlockedHardcore),
            2 => session.Achievements.Where(a => !a.Unlocked && !a.UnlockedHardcore),
            3 => session.Achievements.Where(a => a.Type.Equals("missable", StringComparison.OrdinalIgnoreCase)),
            _ => session.Achievements,
        };

        Color amber = GetAmberColor();
        foreach (var ach in filtered)
        {
            bool earned = ach.Unlocked || ach.UnlockedHardcore;
            Color accent = ach.UnlockedHardcore ? amber : (ach.Unlocked ? Color.Parse("#10B981") : Color.Parse("#38BDF8"));
            var card = new Border
            {
                Padding = new Thickness(10, 7),
                CornerRadius = new CornerRadius(8),
                Background = StormThemeBrushes.RowSurface,
                BorderBrush = new SolidColorBrush(Color.FromArgb(earned ? (byte)190 : (byte)70, accent.R, accent.G, accent.B)),
                BorderThickness = new Thickness(1)
            };
            var g = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto"), ColumnSpacing = 10 };
            var col = new StackPanel { Spacing = 2 };
            string missTag = ach.Type.Equals("missable", StringComparison.OrdinalIgnoreCase) ? " [MISSABLE]" : "";
            col.Children.Add(new TextBlock { Text = ach.Title + missTag, FontSize = 12, FontWeight = FontWeight.Bold, Foreground = StormThemeBrushes.PrimaryText, TextWrapping = TextWrapping.Wrap });
            col.Children.Add(new TextBlock { Text = ach.Description, FontSize = 11, Foreground = StormThemeBrushes.SecondaryText, TextWrapping = TextWrapping.Wrap });
            g.Children.Add(col);

            var right = new StackPanel { Spacing = 2, HorizontalAlignment = HorizontalAlignment.Right, VerticalAlignment = VerticalAlignment.Center };
            right.Children.Add(new TextBlock { Text = $"{ach.Points} pts", FontSize = 11.5, FontWeight = FontWeight.Bold, Foreground = new SolidColorBrush(amber), HorizontalAlignment = HorizontalAlignment.Right });
            right.Children.Add(new TextBlock { Text = ach.UnlockedHardcore ? "HARDCORE" : (ach.Unlocked ? "CASUAL" : "LOCKED"), FontSize = 9.5, FontWeight = FontWeight.Bold, Foreground = new SolidColorBrush(accent), HorizontalAlignment = HorizontalAlignment.Right });
            g.Children.Add(right);
            Grid.SetColumn(right, 1);

            card.Child = g;
            _selectedGameStack.Children.Add(card);
        }
    }

    private AuroraVolumetricButton CreateBtn(AuroraIconKind icon, string text, Action onClick, bool registerThemeLabel = true)
    {
        var btn = new AuroraVolumetricButton { Height = 32, Padding = new Thickness(12, 2), Margin = new Thickness(0, 0, 8, 6) };
        var sp = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 6, VerticalAlignment = VerticalAlignment.Center };
        sp.Children.Add(new AuroraVectorIcon { Kind = icon, Size = 14, VerticalAlignment = VerticalAlignment.Center });
        var tb = new TextBlock { Text = text, FontSize = 11.5, FontWeight = FontWeight.SemiBold, Foreground = StormThemeBrushes.PrimaryText, VerticalAlignment = VerticalAlignment.Center };
        if (registerThemeLabel) _actionBtnLabels.Add(tb);
        sp.Children.Add(tb);
        btn.Content = sp;
        btn.Click += (_, _) =>
        {
            AuroraTactileFeedbackService.Instance.PlayClick();
            onClick();
        };
        return btn;
    }
}
