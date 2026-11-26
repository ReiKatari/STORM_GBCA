// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Apache License, Version 2.0.

using System.Diagnostics;
using System.Globalization;
using System.Text.RegularExpressions;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Input;
using Avalonia.Layout;
using Avalonia.Media;
using Avalonia.Threading;
using Storm.AuroraFluid.Kit.Controls;
using Storm.AuroraFluid.Kit.Icons;
using Storm.AuroraFluid.Kit.Services;
using Storm.AuroraFluid.Kit.Themes;
using StormGbca.Core.Localization;
using StormGbca.UI.Common;

namespace StormGbca.UI.Views.Modals;

public sealed class StormUpdateModal : AuroraGlassCard
{
    public enum UpdateModalMode
    {
        Checking,
        UpdateAvailable,
        UpToDate,
        Error,
    }

    private static readonly Regex s_htmlTagRegex = new("</?(?:details|summary|b|i|br\\s*/?)>", RegexOptions.IgnoreCase | RegexOptions.Compiled);

    private readonly StackPanel _rootStack;
    private readonly TextBlock _titleText;
    private readonly Border _iconBox;
    private readonly AuroraVectorIcon _headerIcon;
    private readonly StackPanel _bodyHost;
    private readonly Action? _onRetryCallback;

    private CancellationTokenSource? _downloadCts;
    private bool _isDownloading;
    private ProgressBar? _progressBar;
    private TextBlock? _progressLeftText;
    private TextBlock? _progressSpeedText;
    private TextBlock? _progressPercentText;
    private Border? _progressCard;
    private StackPanel? _actionsPanel;

    public event Action? CloseRequested;

    public StormUpdateModal(UpdateModalMode mode, AuroraUpdateCheckResult? result = null, Action? onRetry = null)
    {
        _onRetryCallback = onRetry;
        Width = 560;
        MaxHeight = 640;
        Padding = new Thickness(0);
        CornerRadius = new CornerRadius(18);
        SpotlightEnabled = true;

        var theme = AuroraThemeManager.Instance.CurrentTheme;
        Background = new SolidColorBrush(theme.CardColor);
        BorderBrush = new SolidColorBrush(theme.BorderColor);
        BorderThickness = new Thickness(1.2);

        _rootStack = new StackPanel { Spacing = 0 };

        var headerGrid = new Grid { Margin = new Thickness(18, 14, 18, 14), ColumnDefinitions = new ColumnDefinitions("Auto,*,Auto") };
        _iconBox = new Border
        {
            Width = 40,
            Height = 40,
            CornerRadius = new CornerRadius(12),
            Background = new SolidColorBrush(Color.FromArgb(36, theme.DefaultAccentColor.R, theme.DefaultAccentColor.G, theme.DefaultAccentColor.B)),
            BorderBrush = new SolidColorBrush(Color.FromArgb(115, theme.DefaultAccentColor.R, theme.DefaultAccentColor.G, theme.DefaultAccentColor.B)),
            BorderThickness = new Thickness(1)
        };
        _headerIcon = new AuroraVectorIcon
        {
            Kind = AuroraIconKind.UpdateRocket,
            Size = 20,
            CustomColorOverride = new SolidColorBrush(theme.DefaultAccentColor),
            HorizontalAlignment = HorizontalAlignment.Center,
            VerticalAlignment = VerticalAlignment.Center
        };
        _iconBox.Child = _headerIcon;
        headerGrid.Children.Add(_iconBox);

        var titleCol = new StackPanel { Spacing = 2, Margin = new Thickness(12, 0, 12, 0), VerticalAlignment = VerticalAlignment.Center };
        _titleText = new TextBlock { Text = "STORM GBCA", FontSize = 16.5, FontWeight = FontWeight.Bold, Foreground = theme.TextPrimaryBrush };
        titleCol.Children.Add(_titleText);
        titleCol.Children.Add(new TextBlock { Text = "STORM GBCA", FontSize = 11, FontFamily = new FontFamily("Consolas, Cascadia Mono, monospace"), Foreground = theme.TextSecondaryBrush });
        headerGrid.Children.Add(titleCol);
        Grid.SetColumn(titleCol, 1);

        var closeBtn = new Border
        {
            Width = 32,
            Height = 32,
            CornerRadius = new CornerRadius(9),
            Background = new SolidColorBrush(theme.HeaderColor),
            BorderBrush = theme.BorderBrush,
            BorderThickness = new Thickness(1),
            Cursor = new Cursor(StandardCursorType.Hand),
            Child = new AuroraVectorIcon { Kind = AuroraIconKind.WindowClose, Size = 14, CustomColorOverride = theme.TextSecondaryBrush, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center }
        };
        closeBtn.PointerPressed += (_, _) => RequestSafeClose();
        headerGrid.Children.Add(closeBtn);
        Grid.SetColumn(closeBtn, 2);

        _rootStack.Children.Add(headerGrid);
        _rootStack.Children.Add(new Border { Height = 1, HorizontalAlignment = HorizontalAlignment.Stretch, Background = theme.BorderBrush });

        _bodyHost = new StackPanel { Margin = new Thickness(18, 14, 18, 16), Spacing = 12 };
        _rootStack.Children.Add(_bodyHost);
        Content = _rootStack;

        RenderState(mode, result);
    }

    public void UpdateState(UpdateModalMode mode, AuroraUpdateCheckResult? result = null) => RenderState(mode, result);

    public void RequestSafeClose()
    {
        if (_isDownloading)
        {
            _downloadCts?.Cancel();
            return;
        }
        CloseRequested?.Invoke();
    }

    private void RenderState(UpdateModalMode mode, AuroraUpdateCheckResult? result)
    {
        _bodyHost.Children.Clear();
        var theme = AuroraThemeManager.Instance.CurrentTheme;
        var accent = theme.DefaultAccentColor;

        if (mode == UpdateModalMode.Checking)
        {
            _titleText.Text = "Проверка обновлений...";
            _headerIcon.Kind = AuroraIconKind.UpdateRocket;
            SetHeaderTint(accent);
            _bodyHost.Children.Add(new TextBlock { Text = "Опрашиваем GitHub Releases API (ReiKatari/STORM_GBCA) и зеркало STORM GAMES WORLD...", FontSize = 13, Foreground = theme.TextSecondaryBrush, TextWrapping = TextWrapping.Wrap });
            _bodyHost.Children.Add(new ProgressBar { IsIndeterminate = true, Height = 6, Foreground = new SolidColorBrush(accent), Background = new SolidColorBrush(theme.HeaderColor) });
            return;
        }

        if (mode == UpdateModalMode.UpdateAvailable && result != null)
        {
            _titleText.Text = "Доступно обновление";
            _headerIcon.Kind = AuroraIconKind.UpdateRocket;
            SetHeaderTint(accent);

            string cleanCur = GbcaLoc.AppVersion;
            string cleanNew = result.LatestVersion.ToString().TrimStart('v', 'V');

            var transitionGrid = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto,*") };
            transitionGrid.Children.Add(BuildVersionPill("Текущая", cleanCur, false, theme));
            var arrowBox = new TextBlock { Text = "->", FontWeight = FontWeight.Bold, FontSize = 14, Foreground = new SolidColorBrush(accent), HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center, Margin = new Thickness(10, 0) };
            transitionGrid.Children.Add(arrowBox);
            Grid.SetColumn(arrowBox, 1);
            var newPill = BuildVersionPill("Новая", cleanNew, true, theme);
            transitionGrid.Children.Add(newPill);
            Grid.SetColumn(newPill, 2);
            _bodyHost.Children.Add(transitionGrid);

            var notesScroll = new ScrollViewer { MinHeight = 100, MaxHeight = 200 };
            var notesStack = new StackPanel { Spacing = 4 };
            string rawNotes = string.IsNullOrWhiteSpace(result.Changelog) ? $"STORM GBCA {cleanNew}" : s_htmlTagRegex.Replace(result.Changelog, string.Empty);
            foreach (string line in rawNotes.Replace("\r\n", "\n").Split('\n'))
            {
                if (string.IsNullOrWhiteSpace(line)) continue;
                notesStack.Children.Add(new TextBlock { Text = line.Trim(), FontSize = 12, Foreground = theme.TextSecondaryBrush, TextWrapping = TextWrapping.Wrap });
            }
            notesScroll.Content = notesStack;
            _bodyHost.Children.Add(new Border { CornerRadius = new CornerRadius(12), Background = new SolidColorBrush(theme.BackgroundColor), BorderBrush = theme.BorderBrush, BorderThickness = new Thickness(1), Padding = new Thickness(12, 10), Child = notesScroll });

            _progressCard = new Border { IsVisible = false, CornerRadius = new CornerRadius(12), Background = new SolidColorBrush(theme.HeaderColor), BorderBrush = theme.BorderBrush, BorderThickness = new Thickness(1), Padding = new Thickness(12, 10) };
            var progStack = new StackPanel { Spacing = 8 };
            _progressBar = new ProgressBar { Minimum = 0, Maximum = 100, Value = 0, Height = 8, Foreground = new SolidColorBrush(accent) };
            progStack.Children.Add(_progressBar);
            var progStats = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto,Auto") };
            _progressLeftText = new TextBlock { Text = "Подготовка загрузки...", FontSize = 11.5, Foreground = theme.TextSecondaryBrush };
            _progressSpeedText = new TextBlock { Text = string.Empty, FontSize = 11.5, Margin = new Thickness(0, 0, 10, 0), Foreground = theme.TextSecondaryBrush };
            _progressPercentText = new TextBlock { Text = string.Empty, FontSize = 11.5, FontWeight = FontWeight.Bold, Foreground = new SolidColorBrush(accent) };
            progStats.Children.Add(_progressLeftText);
            progStats.Children.Add(_progressSpeedText);
            Grid.SetColumn(_progressSpeedText, 1);
            progStats.Children.Add(_progressPercentText);
            Grid.SetColumn(_progressPercentText, 2);
            progStack.Children.Add(progStats);
            _progressCard.Child = progStack;
            _bodyHost.Children.Add(_progressCard);

            _actionsPanel = new StackPanel { Spacing = 8 };
            var updBtn = new AuroraVolumetricButton { IsHero = true, Height = 40, HorizontalAlignment = HorizontalAlignment.Stretch };
            updBtn.Content = new TextBlock { Text = "Обновить сейчас", FontSize = 13.5, FontWeight = FontWeight.Bold, Foreground = Brushes.White, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
            updBtn.Click += async (_, _) => await StartDownloadAsync(result);
            _actionsPanel.Children.Add(updBtn);

            var secRow = new Grid { ColumnDefinitions = new ColumnDefinitions("*,*"), ColumnSpacing = 8 };
            var laterBtn = CreateSecondaryButton("Напомнить позже", theme, () => CloseRequested?.Invoke());
            var skipBtn = CreateSecondaryButton("Пропустить версию", theme, () =>
            {
                StormGbcaUiState.Instance.Settings.SkippedUpdateVersion = cleanNew;
                StormGbcaUiState.Instance.SaveSettings();
                CloseRequested?.Invoke();
            });
            secRow.Children.Add(laterBtn);
            secRow.Children.Add(skipBtn);
            Grid.SetColumn(skipBtn, 1);
            _actionsPanel.Children.Add(secRow);
            _bodyHost.Children.Add(_actionsPanel);
            return;
        }

        bool isError = mode == UpdateModalMode.Error;
        _titleText.Text = isError ? "Ошибка проверки обновлений" : "Установлена актуальная версия";
        _headerIcon.Kind = isError ? AuroraIconKind.WarningAmber : AuroraIconKind.CheckSuccess;
        SetHeaderTint(isError ? Color.Parse("#EF4444") : accent);

        string msg = isError
            ? (result?.ErrorMessage ?? "Не удалось получить информацию об обновлениях.")
            : $"Вы используете самую свежую версию STORM GBCA {GbcaLoc.AppVersion} (источник: {result?.SourceMirror ?? "GitHub"}).";
        _bodyHost.Children.Add(new TextBlock { Text = msg, FontSize = 13, Foreground = theme.TextSecondaryBrush, TextWrapping = TextWrapping.Wrap });

        var row = new Grid { ColumnDefinitions = isError && _onRetryCallback != null ? new ColumnDefinitions("*,*") : new ColumnDefinitions("*"), ColumnSpacing = 8 };
        int okCol = 0;
        if (isError && _onRetryCallback != null)
        {
            row.Children.Add(CreateSecondaryButton("Повторить", theme, () => _onRetryCallback()));
            okCol = 1;
        }
        var okBtn = new AuroraVolumetricButton { IsHero = true, Height = 38, HorizontalAlignment = HorizontalAlignment.Stretch };
        okBtn.Content = new TextBlock { Text = "Закрыть", FontSize = 13, FontWeight = FontWeight.Bold, Foreground = Brushes.White, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
        okBtn.Click += (_, _) => CloseRequested?.Invoke();
        row.Children.Add(okBtn);
        Grid.SetColumn(okBtn, okCol);
        _bodyHost.Children.Add(row);
    }

    private async Task StartDownloadAsync(AuroraUpdateCheckResult result)
    {
        if (_isDownloading || string.IsNullOrWhiteSpace(result.DownloadUrl)) return;
        _isDownloading = true;
        _downloadCts = new CancellationTokenSource();
        if (_actionsPanel != null) _actionsPanel.IsVisible = false;
        if (_progressCard != null) _progressCard.IsVisible = true;

        string assetName = !string.IsNullOrWhiteSpace(result.AssetName) ? result.AssetName : $"STORM_GBCA_{result.LatestVersion}_Setup.exe";
        string destPath = Path.Combine(Path.GetTempPath(), "STORM_GBCA_Updates", assetName);

        var progress = new Progress<AuroraDownloadProgress>(p =>
        {
            Dispatcher.UIThread.Post(() =>
            {
                if (_progressBar == null || _progressLeftText == null || _progressSpeedText == null || _progressPercentText == null) return;
                double dlMb = p.DownloadedBytes / (1024.0 * 1024.0);
                long total = p.TotalBytes > 0 ? p.TotalBytes : result.AssetSizeBytes;
                if (total > 0)
                {
                    double totMb = total / (1024.0 * 1024.0);
                    double pct = Math.Clamp((double)p.DownloadedBytes * 100.0 / total, 0.0, 100.0);
                    _progressBar.Value = pct;
                    _progressLeftText.Text = $"{dlMb.ToString("F1", CultureInfo.InvariantCulture)} / {totMb.ToString("F1", CultureInfo.InvariantCulture)} МБ";
                    _progressPercentText.Text = $"{(int)pct}%";
                }
                _progressSpeedText.Text = p.SpeedMbPerSec > 0.05 ? $"{p.SpeedMbPerSec.ToString("F1", CultureInfo.InvariantCulture)} МБ/с" : string.Empty;
            });
        });

        try
        {
            var updater = new AuroraDualMirrorUpdater();
            bool ok = await updater.DownloadAndVerifyAsync(result.DownloadUrl, destPath, result.ExpectedSha256, progress, _downloadCts.Token, AuroraDualMirrorUpdater.DefaultGitHubRepo);
            _isDownloading = false;
            if (!ok)
            {
                UpdateState(UpdateModalMode.Error, result with { ErrorMessage = "Не удалось скачать или проверить целостность файла обновления." });
                return;
            }

            if (destPath.EndsWith(".exe", StringComparison.OrdinalIgnoreCase))
            {
                Process.Start(new ProcessStartInfo(destPath) { UseShellExecute = true });
            }
            else
            {
                Process.Start(new ProcessStartInfo("explorer.exe", $"/select,\"{destPath}\"") { UseShellExecute = true });
            }
            CloseRequested?.Invoke();
        }
        catch (OperationCanceledException)
        {
            _isDownloading = false;
            RenderState(UpdateModalMode.UpdateAvailable, result);
        }
        catch (Exception ex)
        {
            _isDownloading = false;
            UpdateState(UpdateModalMode.Error, result with { ErrorMessage = ex.Message });
        }
    }

    private void SetHeaderTint(Color c)
    {
        _headerIcon.CustomColorOverride = new SolidColorBrush(c);
        _iconBox.Background = new SolidColorBrush(Color.FromArgb(36, c.R, c.G, c.B));
        _iconBox.BorderBrush = new SolidColorBrush(Color.FromArgb(115, c.R, c.G, c.B));
    }

    private static Border BuildVersionPill(string label, string version, bool accent, AuroraThemeDefinition theme)
    {
        var accentColor = theme.DefaultAccentColor;
        var box = new Border
        {
            CornerRadius = new CornerRadius(10),
            Padding = new Thickness(10, 7),
            Background = accent ? new SolidColorBrush(Color.FromArgb(36, accentColor.R, accentColor.G, accentColor.B)) : new SolidColorBrush(theme.BackgroundColor),
            BorderBrush = accent ? new SolidColorBrush(Color.FromArgb(120, accentColor.R, accentColor.G, accentColor.B)) : theme.BorderBrush,
            BorderThickness = new Thickness(1)
        };
        var col = new StackPanel { Spacing = 2, HorizontalAlignment = HorizontalAlignment.Center };
        col.Children.Add(new TextBlock { Text = label, FontSize = 10.5, Foreground = theme.TextSecondaryBrush, HorizontalAlignment = HorizontalAlignment.Center });
        col.Children.Add(new TextBlock { Text = version, FontSize = 14, FontWeight = FontWeight.Bold, Foreground = accent ? new SolidColorBrush(accentColor) : theme.TextPrimaryBrush, HorizontalAlignment = HorizontalAlignment.Center });
        box.Child = col;
        return box;
    }

    private static Border CreateSecondaryButton(string text, AuroraThemeDefinition theme, Action onClick)
    {
        var btn = new Border
        {
            Height = 36,
            CornerRadius = new CornerRadius(10),
            Background = new SolidColorBrush(theme.HeaderColor),
            BorderBrush = theme.BorderBrush,
            BorderThickness = new Thickness(1),
            Cursor = new Cursor(StandardCursorType.Hand),
            Child = new TextBlock { Text = text, FontSize = 12.5, FontWeight = FontWeight.SemiBold, Foreground = theme.TextPrimaryBrush, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center }
        };
        btn.PointerPressed += (_, _) =>
        {
            AuroraTactileFeedbackService.Instance.PlayClick();
            onClick();
        };
        return btn;
    }
}
