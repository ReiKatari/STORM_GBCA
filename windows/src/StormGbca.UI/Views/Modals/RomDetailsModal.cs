// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Mozilla Public License, Version 2.0 (MPL-2.0).

using Avalonia;
using Avalonia.Controls;
using Avalonia.Input;
using Avalonia.Layout;
using Avalonia.Media;
using Storm.AuroraFluid.Kit.Controls;
using Storm.AuroraFluid.Kit.Icons;
using Storm.AuroraFluid.Kit.Services;
using StormGbca.Core.Configuration;
using StormGbca.Core.Library;
using StormGbca.Core.Localization;
using StormGbca.Core.Paths;
using StormGbca.Core.Subsystems;
using StormGbca.UI.Common;
using StormGbca.UI.Views.Bento;

namespace StormGbca.UI.Views.Modals;

public class RomDetailsModal : AuroraGlassCard
{
    private readonly GbcaRomRecord _rom;
    private readonly WrapPanel _tabBar;
    private readonly ContentControl _tabContent;
    private readonly TextBlock _headerTitle;
    private int _selectedTab;

    public event Action? CloseRequested;

    public RomDetailsModal(GbcaRomRecord rom, int initialTab = 0)
    {
        _rom = rom;
        _selectedTab = initialTab;
        MaxWidth = 660;
        MaxHeight = 560;
        Margin = new Thickness(24, 18);
        HorizontalAlignment = HorizontalAlignment.Stretch;
        VerticalAlignment = VerticalAlignment.Center;
        Padding = new Thickness(20);
        SpotlightEnabled = true;

        var mainGrid = new Grid { RowDefinitions = new RowDefinitions("Auto,Auto,*") };

        var header = new Grid { ColumnDefinitions = new ColumnDefinitions("Auto,*,Auto"), ColumnSpacing = 8, Margin = new Thickness(0, 0, 0, 10) };
        header.Children.Add(new AuroraVectorIcon { Kind = AuroraIconKind.RomCartridge, Size = 20, VerticalAlignment = VerticalAlignment.Center });
        _headerTitle = new TextBlock
        {
            Text = $"{_rom.GetCleanTitleBeforeYear()} ({_rom.PlatformShortName})",
            FontSize = 14,
            FontWeight = FontWeight.Bold,
            Foreground = StormThemeBrushes.Accent,
            VerticalAlignment = VerticalAlignment.Center,
            TextTrimming = TextTrimming.CharacterEllipsis
        };
        header.Children.Add(_headerTitle);
        Grid.SetColumn(_headerTitle, 1);

        var closeBtn = new Button
        {
            Width = 30,
            Height = 30,
            Background = Brushes.Transparent,
            BorderThickness = new Thickness(0),
            Cursor = new Cursor(StandardCursorType.Hand),
            Content = new AuroraVectorIcon { Kind = AuroraIconKind.WindowClose, Size = 13 }
        };
        closeBtn.Click += (_, _) => CloseRequested?.Invoke();
        header.Children.Add(closeBtn);
        Grid.SetColumn(closeBtn, 2);
        mainGrid.Children.Add(header);

        _tabBar = new WrapPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 0, 0, 12) };
        AddTabHeader(0, AuroraIconKind.InfoCrystal, "Сведения о картридже");
        AddTabHeader(1, AuroraIconKind.CheatMatrix, "Читы");
        AddTabHeader(2, AuroraIconKind.SaveSlotRibbon, "Сохранения (10 слотов)");
        mainGrid.Children.Add(_tabBar);
        Grid.SetRow(_tabBar, 1);

        _tabContent = new ContentControl();
        mainGrid.Children.Add(_tabContent);
        Grid.SetRow(_tabContent, 2);

        Content = mainGrid;

        StormThemeBrushes.BindTheme(this, () =>
        {
            _headerTitle.Foreground = StormThemeBrushes.Accent;
            LoadTab(_selectedTab);
        });
        LoadTab(_selectedTab);
    }

    private void AddTabHeader(int idx, AuroraIconKind icon, string title)
    {
        var btn = new Border
        {
            Height = 32,
            Margin = new Thickness(0, 0, 6, 4),
            Padding = new Thickness(10, 4),
            CornerRadius = new CornerRadius(8),
            BorderThickness = new Thickness(1),
            Cursor = new Cursor(StandardCursorType.Hand)
        };
        var s = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 6, VerticalAlignment = VerticalAlignment.Center };
        s.Children.Add(new AuroraVectorIcon { Kind = icon, Size = 14, VerticalAlignment = VerticalAlignment.Center });
        s.Children.Add(new TextBlock { Text = title, FontSize = 11.5, FontWeight = FontWeight.Medium, VerticalAlignment = VerticalAlignment.Center });
        btn.Child = s;
        btn.PointerPressed += (_, _) =>
        {
            AuroraTactileFeedbackService.Instance.PlayClick();
            LoadTab(idx);
        };
        _tabBar.Children.Add(btn);
    }

    private void LoadTab(int index)
    {
        _selectedTab = index;
        var accent = StormThemeBrushes.AccentPrimaryColor;

        for (int i = 0; i < _tabBar.Children.Count; i++)
        {
            if (_tabBar.Children[i] is Border b)
            {
                bool sel = i == index;
                b.Background = sel ? StormThemeBrushes.HoverSurface : Brushes.Transparent;
                b.BorderBrush = sel ? new SolidColorBrush(accent) : StormThemeBrushes.SubtleBorder;
                if (b.Child is StackPanel sp && sp.Children.Count > 1 && sp.Children[1] is TextBlock tb)
                {
                    tb.Foreground = sel ? StormThemeBrushes.PrimaryText : StormThemeBrushes.BodyText;
                }
            }
        }

        var scroll = new ScrollViewer { Padding = new Thickness(0, 0, 8, 0) };
        var stack = new StackPanel { Spacing = 8 };
        switch (index)
        {
            case 0: BuildInfoTab(stack); break;
            case 1: BuildCheatsTab(stack); break;
            case 2: BuildSavesTab(stack); break;
        }
        scroll.Content = stack;
        _tabContent.Content = scroll;
    }

    private void BuildInfoTab(StackPanel stack)
    {
        string sourceName = !string.IsNullOrWhiteSpace(_rom.FileName) ? _rom.FileName : _rom.FilePath;
        string ext = Path.GetExtension(sourceName).ToUpperInvariant();
        if (string.IsNullOrWhiteSpace(ext)) ext = "-";

        stack.Children.Add(CreateMetaRow("Название игры:", _rom.GetCleanTitleBeforeYear()));
        stack.Children.Add(CreateMetaRow("Файл:", _rom.FilePath));
        stack.Children.Add(CreateMetaRow("Расширение:", ext));
        stack.Children.Add(CreateMetaRow("Платформа:", _rom.PlatformShortName));
        stack.Children.Add(CreateMetaRow("Заголовок ROM:", string.IsNullOrWhiteSpace(_rom.Title) ? "-" : _rom.Title));
        stack.Children.Add(CreateMetaRow("Код игры:", string.IsNullOrWhiteSpace(_rom.GameCode) ? "-" : _rom.GameCode));
        stack.Children.Add(CreateMetaRow("Регион:", _rom.Region));
        stack.Children.Add(CreateMetaRow("Размер:", $"{BentoRomCard.FormatRomSize(_rom.RomSize)} ({_rom.RomSize} байт)"));
        stack.Children.Add(CreateMetaRow("CRC32:", _rom.Crc32Hex));
        stack.Children.Add(CreateMetaRow("MD5:", _rom.Md5));

        string raHash = !string.IsNullOrWhiteSpace(_rom.RaHash)
            ? _rom.RaHash
            : StormGbcaUiState.Instance.RetroAchievements.ComputeRomHash(_rom.FilePath, _rom.Platform);
        _rom.RaHash = raHash;
        stack.Children.Add(CreateMetaRow("RetroAchievements Hash:", string.IsNullOrWhiteSpace(raHash) ? "-" : raHash));

        var badges = new WrapPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 6, 0, 0) };
        BentoRomCard.AddHardwareBadges(badges, _rom.HardwareFlags);
        if (badges.Children.Count > 0) stack.Children.Add(badges);

        var sgwUpdate = StormGbcaUiState.Instance.GetUpdateForRom(_rom);
        if (sgwUpdate != null)
        {
            var updCard = new Border
            {
                Margin = new Thickness(0, 8, 0, 0),
                Padding = new Thickness(12, 10),
                CornerRadius = new CornerRadius(10),
                Background = new SolidColorBrush(Color.FromArgb(30, 16, 185, 129)),
                BorderBrush = new SolidColorBrush(Color.Parse("#10B981")),
                BorderThickness = new Thickness(1)
            };
            var updStack = new StackPanel { Spacing = 6 };
            updStack.Children.Add(new TextBlock
            {
                Text = $"{sgwUpdate.RemoteGame.Title} ({sgwUpdate.LocalVersion} -> {sgwUpdate.RemoteVersion})",
                FontSize = 12,
                FontWeight = FontWeight.Bold,
                Foreground = StormThemeBrushes.PrimaryText,
                TextWrapping = TextWrapping.Wrap
            });
            updStack.Children.Add(new TextBlock
            {
                Text = GbcaLoc.T("SgwSaveMigrationNote"),
                FontSize = 11,
                Foreground = StormThemeBrushes.SecondaryText,
                TextWrapping = TextWrapping.Wrap
            });
            var updBtnLabel = new TextBlock
            {
                Text = GbcaLoc.T("BtnUpdateFromSgw"),
                FontSize = 12,
                FontWeight = FontWeight.Bold,
                Foreground = Brushes.White,
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            };
            var updBtn = new AuroraVolumetricButton
            {
                IsHero = true,
                Height = 36,
                HorizontalAlignment = HorizontalAlignment.Stretch,
                Content = updBtnLabel
            };
            updBtn.Click += async (_, _) =>
            {
                updBtn.IsEnabled = false;
                var progress = new Progress<SgwDownloadProgress>(p =>
                {
                    Avalonia.Threading.Dispatcher.UIThread.Post(() =>
                    {
                        if (p.TotalBytes > 1)
                        {
                            int pct = (int)Math.Clamp((double)p.DownloadedBytes * 100.0 / p.TotalBytes, 0.0, 100.0);
                            updBtnLabel.Text = $"{pct}%";
                        }
                        else if (!string.IsNullOrWhiteSpace(p.StatusText))
                        {
                            updBtnLabel.Text = p.StatusText;
                        }
                    });
                });
                var res = await StormGbcaUiState.Instance.DownloadCatalogGameAsync(sgwUpdate.RemoteGame, _rom, progress);
                Avalonia.Threading.Dispatcher.UIThread.Post(() =>
                {
                    if (res.Success)
                    {
                        CloseRequested?.Invoke();
                    }
                    else
                    {
                        updBtn.IsEnabled = true;
                        updBtnLabel.Text = GbcaLoc.T("BtnUpdateFromSgw");
                        if (!string.IsNullOrWhiteSpace(res.ErrorMessage))
                        {
                            StormGbcaUiState.Instance.NotifyStatus(res.ErrorMessage);
                        }
                    }
                });
            };
            updStack.Children.Add(updBtn);
            updCard.Child = updStack;
            stack.Children.Add(updCard);
        }
    }

    private void BuildCheatsTab(StackPanel stack)
    {
        bool cheatsBlocked = StormGbcaUiState.Instance.IsEmulationActive && StormGbcaUiState.Instance.Emulator.IsHardcoreMode;
        if (cheatsBlocked)
        {
            stack.Children.Add(new Border
            {
                Padding = new Thickness(10, 7),
                CornerRadius = new CornerRadius(8),
                Background = new SolidColorBrush(Color.FromArgb(40, 250, 204, 21)),
                BorderBrush = new SolidColorBrush(Color.Parse("#FACC15")),
                BorderThickness = new Thickness(1),
                Child = new TextBlock
                {
                    Text = "Режим Hardcore RetroAchievements активен: читы заблокированы.",
                    FontSize = 11.5,
                    FontWeight = FontWeight.SemiBold,
                    Foreground = StormThemeBrushes.PrimaryText,
                    TextWrapping = TextWrapping.Wrap
                }
            });
        }

        string romKey = !string.IsNullOrWhiteSpace(_rom.FileName) ? _rom.FileName : _rom.GameCode;
        var cheats = StormGbcaUiState.Instance.Cheats;
        cheats.LoadCheatsForRom(romKey);

        var listPanel = new StackPanel { Spacing = 6, IsEnabled = !cheatsBlocked };
        void RebuildList()
        {
            listPanel.Children.Clear();
            var list = cheats.Cheats;
            if (list.Count == 0)
            {
                listPanel.Children.Add(new TextBlock { Text = "Читы еще не добавлены.", FontSize = 11.5, Foreground = StormThemeBrushes.SecondaryText });
                return;
            }

            for (int i = 0; i < list.Count; i++)
            {
                int idx = i;
                var item = list[idx];
                var row = new Border
                {
                    Padding = new Thickness(10, 6),
                    CornerRadius = new CornerRadius(8),
                    Background = StormThemeBrushes.RowSurface,
                    BorderBrush = StormThemeBrushes.SubtleBorder,
                    BorderThickness = new Thickness(1)
                };
                var rg = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto") };
                var cb = new AuroraNeonCheckBox { Label = $"{item.Name} ({item.Code})", IsChecked = item.Enabled };
                cb.IsCheckedChanged += (_, _) =>
                {
                    cheats.ToggleCheat(idx, romKey);
                    StormGbcaUiState.Instance.ApplyLiveCheats();
                };
                rg.Children.Add(cb);

                var del = new Button { Width = 28, Height = 28, Background = Brushes.Transparent, BorderThickness = new Thickness(0), Cursor = new Cursor(StandardCursorType.Hand), Content = new AuroraVectorIcon { Kind = AuroraIconKind.TrashClean, Size = 13 } };
                del.Click += (_, _) =>
                {
                    cheats.RemoveCheat(idx, romKey);
                    StormGbcaUiState.Instance.ApplyLiveCheats();
                    RebuildList();
                };
                rg.Children.Add(del);
                Grid.SetColumn(del, 1);

                row.Child = rg;
                listPanel.Children.Add(row);
            }
        }
        RebuildList();
        stack.Children.Add(listPanel);

        var nameBox = new TextBox { PlaceholderText = "Название чита", Height = 32, IsEnabled = !cheatsBlocked };
        var codeBox = new TextBox { PlaceholderText = "Код (GameShark, ActionReplay или GameGenie)", Height = 54, AcceptsReturn = true, TextWrapping = TextWrapping.Wrap, IsEnabled = !cheatsBlocked };
        var addBtn = new AuroraVolumetricButton { Height = 32, Width = 180, HorizontalAlignment = HorizontalAlignment.Left, IsEnabled = !cheatsBlocked };
        addBtn.Content = new TextBlock { Text = "Добавить чит-код", FontSize = 11.5, FontWeight = FontWeight.SemiBold, Foreground = StormThemeBrushes.PrimaryText, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
        addBtn.Click += (_, _) =>
        {
            string n = (nameBox.Text ?? string.Empty).Trim();
            string c = (codeBox.Text ?? string.Empty).Trim();
            if (string.IsNullOrWhiteSpace(n) || string.IsNullOrWhiteSpace(c)) return;
            cheats.AddCheat(new GbcaCheatEntry { Name = n, Code = c, Enabled = true }, romKey);
            StormGbcaUiState.Instance.ApplyLiveCheats();
            nameBox.Text = string.Empty;
            codeBox.Text = string.Empty;
            RebuildList();
        };
        stack.Children.Add(nameBox);
        stack.Children.Add(codeBox);
        stack.Children.Add(addBtn);
    }

    private void BuildSavesTab(StackPanel stack)
    {
        string key = !string.IsNullOrWhiteSpace(_rom.FileName) ? _rom.FileName : _rom.GameCode;
        string savPath = StormGbcaPaths.GetSaveFilePath(key);
        stack.Children.Add(CreateMetaRow("Картриджное сохранение (.sav):", savPath));
        stack.Children.Add(CreateMetaRow("Размер .sav:", File.Exists(savPath) ? $"{new FileInfo(savPath).Length / 1024.0:0.1} КБ" : "Еще не создано"));

        for (int i = 0; i < 10; i++)
        {
            int slot = i;
            string slotPath = StormGbcaPaths.GetSaveStateFilePath(key, slot);
            string thumbPath = StormGbcaPaths.GetSaveStateThumbnailPath(key, slot);
            bool exists = File.Exists(slotPath);
            var row = new Border
            {
                Padding = new Thickness(10, 6),
                CornerRadius = new CornerRadius(8),
                Background = StormThemeBrushes.RowSurface,
                BorderBrush = StormThemeBrushes.SubtleBorder,
                BorderThickness = new Thickness(1)
            };
            var g = new Grid { ColumnDefinitions = new ColumnDefinitions("Auto,78,*,Auto"), ColumnSpacing = 10 };

            var thumbBox = new Border
            {
                Width = 64,
                Height = 42,
                CornerRadius = new CornerRadius(6),
                Background = StormThemeBrushes.CardSurface,
                BorderBrush = StormThemeBrushes.SubtleBorder,
                BorderThickness = new Thickness(1),
                ClipToBounds = true,
                Child = new Image
                {
                    Stretch = Stretch.Uniform,
                    Source = File.Exists(thumbPath) ? BitmapHelper.LoadBitmapCached(thumbPath) : null
                }
            };
            g.Children.Add(thumbBox);

            var slotLbl = new TextBlock { Text = $"Слот {slot + 1}", FontSize = 11.5, FontWeight = FontWeight.Bold, Foreground = StormThemeBrushes.PrimaryText, VerticalAlignment = VerticalAlignment.Center };
            g.Children.Add(slotLbl);
            Grid.SetColumn(slotLbl, 1);

            var dt = new TextBlock
            {
                Text = exists ? $"{new FileInfo(slotPath).Length / 1024.0:0.1} КБ | {File.GetLastWriteTime(slotPath):dd.MM.yyyy HH:mm}" : "Пустой слот",
                FontSize = 11,
                Foreground = StormThemeBrushes.SecondaryText,
                VerticalAlignment = VerticalAlignment.Center,
                TextTrimming = TextTrimming.CharacterEllipsis
            };
            g.Children.Add(dt);
            Grid.SetColumn(dt, 2);

            if (exists)
            {
                var delBtn = new Button { Width = 28, Height = 28, Background = Brushes.Transparent, BorderThickness = new Thickness(0), Cursor = new Cursor(StandardCursorType.Hand), Content = new AuroraVectorIcon { Kind = AuroraIconKind.TrashClean, Size = 13 } };
                delBtn.Click += (_, _) =>
                {
                    try { File.Delete(slotPath); } catch { }
                    try { if (File.Exists(thumbPath)) File.Delete(thumbPath); } catch { }
                    LoadTab(2);
                };
                g.Children.Add(delBtn);
                Grid.SetColumn(delBtn, 3);
            }
            row.Child = g;
            stack.Children.Add(row);
        }
    }

    private static Grid CreateMetaRow(string label, string val)
    {
        var g = new Grid { ColumnDefinitions = new ColumnDefinitions("190,*"), Margin = new Thickness(0, 2) };
        g.Children.Add(new TextBlock { Text = label, FontSize = 11, Foreground = StormThemeBrushes.SecondaryText });
        var vt = new TextBlock { Text = val, FontSize = 11, FontWeight = FontWeight.SemiBold, Foreground = StormThemeBrushes.PrimaryText, TextTrimming = TextTrimming.CharacterEllipsis };
        g.Children.Add(vt);
        Grid.SetColumn(vt, 1);
        return g;
    }
}
