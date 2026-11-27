// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Apache License, Version 2.0.

using System.Diagnostics;
using System.Globalization;
using System.Reflection;
using System.Text.RegularExpressions;
using Avalonia;
using Avalonia.Controls;
using Avalonia.Controls.Documents;
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
    private static readonly Regex s_commentLangStartRegex = new(@"<!--\s*lang\s*:\s*([a-zA-Z_-]+)\s*-->", RegexOptions.IgnoreCase | RegexOptions.Compiled);
    private static readonly Regex s_commentLangEndRegex = new(@"<!--\s*/lang\s*-->", RegexOptions.IgnoreCase | RegexOptions.Compiled);
    private static readonly Regex s_htmlCommentLineRegex = new(@"^\s*<!--.*-->\s*$", RegexOptions.Compiled);

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
        Width = 580;
        MaxHeight = 660;
        Padding = new Thickness(0);
        CornerRadius = new CornerRadius(20);
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
        _titleText = new TextBlock { Text = "STORM GBCA", FontSize = 17, FontWeight = FontWeight.Bold, Foreground = theme.TextPrimaryBrush };
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

    private static string GetCleanCurrentVersion()
    {
        var ver = Assembly.GetEntryAssembly()?.GetName().Version
                  ?? typeof(StormUpdateModal).Assembly.GetName().Version;
        if (ver != null && (ver.Major > 0 || ver.Minor > 0 || ver.Build > 0))
        {
            return $"{Math.Max(0, ver.Major)}.{Math.Max(0, ver.Minor)}.{Math.Max(0, ver.Build)}";
        }
        return GbcaLoc.AppVersion.Trim().TrimStart('v', 'V');
    }

    private static string NormalizeLocaleCode(string raw)
    {
        string clean = raw.Trim().ToLowerInvariant().Replace('_', '-');
        if (clean == "in" || clean.StartsWith("in-", StringComparison.Ordinal)) return "id" + clean[2..];
        if (clean == "iw" || clean.StartsWith("iw-", StringComparison.Ordinal)) return "he" + clean[2..];
        return clean;
    }

    private static string? MatchLanguageHeading(string rawHeading)
    {
        string trimmed = rawHeading.Trim();
        if (trimmed.StartsWith("###", StringComparison.Ordinal)) trimmed = trimmed[3..];
        else if (trimmed.StartsWith("##", StringComparison.Ordinal)) trimmed = trimmed[2..];
        else if (trimmed.StartsWith('#')) trimmed = trimmed[1..];
        trimmed = trimmed.Trim().Trim('[', ']', '(', ')').ToLowerInvariant().Replace('_', '-');

        if (trimmed == "ru" || trimmed.Contains("русский") || trimmed.Contains("russian")) return "ru";
        if (trimmed == "en" || trimmed.Contains("english") || trimmed.Contains("английск")) return "en";
        if (trimmed == "de" || trimmed.Contains("deutsch") || trimmed.Contains("german")) return "de";
        if (trimmed == "es" || trimmed.Contains("español") || trimmed.Contains("espanol") || trimmed.Contains("spanish")) return "es";
        if (trimmed == "fr" || trimmed.Contains("français") || trimmed.Contains("francais") || trimmed.Contains("french")) return "fr";
        if (trimmed == "id" || trimmed.Contains("bahasa indonesia") || trimmed.Contains("indonesian")) return "id";
        if (trimmed == "it" || trimmed.Contains("italiano") || trimmed.Contains("italian")) return "it";
        if (trimmed == "ja" || trimmed.Contains("日本語") || trimmed.Contains("japanese")) return "ja";
        if (trimmed == "ko" || trimmed.Contains("한국어") || trimmed.Contains("korean")) return "ko";
        if (trimmed == "hi" || trimmed.Contains("हिन्दी") || trimmed.Contains("हिंदी") || trimmed.Contains("hindi")) return "hi";
        if (trimmed == "pt-br" || trimmed == "pt" || trimmed.Contains("português") || trimmed.Contains("portugues") || trimmed.Contains("portuguese"))
            return trimmed.Contains("br") ? "pt-br" : "pt";
        if (trimmed == "zh-hans" || trimmed == "zh-cn" || trimmed == "zh" || trimmed.Contains("简体中文") || trimmed.Contains("中文") || trimmed.Contains("chinese"))
            return (trimmed.Contains("hans") || trimmed.Contains("cn") || trimmed.Contains("简体")) ? "zh-hans" : "zh";
        return null;
    }

    /// <summary>
    /// Extracts the active language section from multilingual release notes (59c958ef standard).
    /// Supports both &lt;!-- lang:XX --&gt; ... &lt;!-- /lang --&gt; blocks and Markdown ## Language headings.
    /// </summary>
    public static string SelectLocalizedReleaseNotes(string raw, string? activeLangOverride = null)
    {
        if (string.IsNullOrWhiteSpace(raw)) return string.Empty;
        string normalized = raw.Replace("\r\n", "\n");
        string[] lines = normalized.Split('\n');

        // Pass 1: explicit <!-- lang:XX --> ... <!-- /lang --> blocks
        var markerSections = new Dictionary<string, List<string>>(StringComparer.OrdinalIgnoreCase);
        string? activeMarker = null;
        foreach (string line in lines)
        {
            var startMatch = s_commentLangStartRegex.Match(line);
            if (startMatch.Success)
            {
                activeMarker = NormalizeLocaleCode(startMatch.Groups[1].Value);
                if (!markerSections.TryGetValue(activeMarker, out var list))
                {
                    list = [];
                    markerSections[activeMarker] = list;
                }
                string stripped = s_commentLangEndRegex.Replace(s_commentLangStartRegex.Replace(line, string.Empty), string.Empty).TrimEnd();
                if (!string.IsNullOrWhiteSpace(stripped)) list.Add(stripped);
                if (s_commentLangEndRegex.IsMatch(line)) activeMarker = null;
                continue;
            }
            if (s_commentLangEndRegex.IsMatch(line))
            {
                activeMarker = null;
                continue;
            }
            if (activeMarker != null && !s_htmlCommentLineRegex.IsMatch(line))
            {
                markerSections[activeMarker].Add(line);
            }
        }

        // Pass 2: Markdown heading sections (## Русский / ## English / ...)
        var headingSections = new Dictionary<string, List<string>>(StringComparer.OrdinalIgnoreCase);
        if (markerSections.Count < 2)
        {
            string? activeHeading = null;
            foreach (string line in lines)
            {
                string trimmed = line.Trim();
                if (trimmed.StartsWith("## ", StringComparison.Ordinal) || trimmed.StartsWith("### ", StringComparison.Ordinal))
                {
                    string? detected = MatchLanguageHeading(trimmed);
                    if (detected != null)
                    {
                        activeHeading = detected;
                        if (!headingSections.ContainsKey(detected))
                        {
                            headingSections[detected] = [];
                        }
                        continue;
                    }
                }
                if (activeHeading != null && !s_htmlCommentLineRegex.IsMatch(line))
                {
                    headingSections[activeHeading].Add(line);
                }
            }
        }

        Dictionary<string, List<string>>? chosenMap = markerSections.Count >= 2
            ? markerSections
            : headingSections.Count >= 2 ? headingSections : null;

        if (chosenMap == null)
        {
            return string.Join("\n", lines.Where(l => !s_htmlCommentLineRegex.IsMatch(l))).Trim();
        }

        string effectiveLang = !string.IsNullOrWhiteSpace(activeLangOverride)
            ? NormalizeLocaleCode(activeLangOverride)
            : NormalizeLocaleCode(GbcaLoc.CurrentLanguage);
        string cultureName = NormalizeLocaleCode(CultureInfo.CurrentUICulture.Name);

        var candidates = new List<string>();
        if (!string.IsNullOrWhiteSpace(cultureName) && cultureName.StartsWith(effectiveLang, StringComparison.OrdinalIgnoreCase))
        {
            candidates.Add(cultureName);
        }
        if (effectiveLang == "zh")
        {
            candidates.Add("zh-hans");
            candidates.Add("zh-cn");
        }
        if (!string.IsNullOrWhiteSpace(effectiveLang)) candidates.Add(effectiveLang);
        if (effectiveLang == "pt") candidates.Add("pt-br");
        candidates.Add("en");
        candidates.Add("ru");

        List<string>? picked = null;
        foreach (string key in candidates)
        {
            if (chosenMap.TryGetValue(key, out var list) && list.Any(l => !string.IsNullOrWhiteSpace(l)))
            {
                picked = list;
                break;
            }
        }
        picked ??= chosenMap.Values.FirstOrDefault(list => list.Any(l => !string.IsNullOrWhiteSpace(l)));
        if (picked == null) return normalized.Trim();

        return string.Join("\n", picked.Where(l => !s_htmlCommentLineRegex.IsMatch(l))).Trim();
    }

    private void RenderState(UpdateModalMode mode, AuroraUpdateCheckResult? result)
    {
        _bodyHost.Children.Clear();
        var theme = AuroraThemeManager.Instance.CurrentTheme;
        var accent = theme.DefaultAccentColor;

        if (mode == UpdateModalMode.Checking)
        {
            _titleText.Text = GbcaLoc.T("UpdateCheckingTitle");
            _headerIcon.Kind = AuroraIconKind.UpdateRocket;
            SetHeaderTint(accent);
            _bodyHost.Children.Add(new TextBlock { Text = GbcaLoc.T("UpdateCheckingSubtitle"), FontSize = 13, Foreground = theme.TextSecondaryBrush, TextWrapping = TextWrapping.Wrap });
            _bodyHost.Children.Add(new ProgressBar { IsIndeterminate = true, Height = 6, Foreground = new SolidColorBrush(accent), Background = new SolidColorBrush(theme.HeaderColor) });
            return;
        }

        if (mode == UpdateModalMode.UpdateAvailable && result != null)
        {
            _titleText.Text = GbcaLoc.T("UpdateAvailableTitle");
            _headerIcon.Kind = AuroraIconKind.UpdateRocket;
            SetHeaderTint(accent);

            string cleanCur = GetCleanCurrentVersion();
            string cleanNew = result.LatestVersion.ToString().Trim().TrimStart('v', 'V');

            // Version Transition Card: Current -> New (zero 'v' prefix)
            var transitionBorder = new Border
            {
                CornerRadius = new CornerRadius(14),
                Background = new SolidColorBrush(theme.HeaderColor),
                BorderBrush = theme.BorderBrush,
                BorderThickness = new Thickness(1),
                Padding = new Thickness(12, 10)
            };
            var transitionGrid = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto,*") };
            transitionGrid.Children.Add(BuildVersionPill(GbcaLoc.T("UpdateCurrentLabel"), cleanCur, false, theme));
            var arrowBox = new Border
            {
                Width = 30,
                Height = 30,
                CornerRadius = new CornerRadius(8),
                Margin = new Thickness(8, 0),
                Background = new SolidColorBrush(Color.FromArgb(36, accent.R, accent.G, accent.B)),
                Child = new TextBlock { Text = "->", FontWeight = FontWeight.Bold, FontSize = 13, Foreground = new SolidColorBrush(accent), HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center }
            };
            transitionGrid.Children.Add(arrowBox);
            Grid.SetColumn(arrowBox, 1);
            var newPill = BuildVersionPill(GbcaLoc.T("UpdateNewLabel"), cleanNew, true, theme);
            transitionGrid.Children.Add(newPill);
            Grid.SetColumn(newPill, 2);
            transitionBorder.Child = transitionGrid;
            _bodyHost.Children.Add(transitionBorder);

            // Metadata Chips: Mirror Source, Size, Release Date
            var chipsPanel = new WrapPanel { Orientation = Orientation.Horizontal };
            string mirrorLabel = string.IsNullOrWhiteSpace(result.SourceMirror) ? "GitHub" : result.SourceMirror;
            chipsPanel.Children.Add(BuildMetadataChip(mirrorLabel, theme, true));
            if (result.AssetSizeBytes > 0)
            {
                double mb = result.AssetSizeBytes / (1024.0 * 1024.0);
                chipsPanel.Children.Add(BuildMetadataChip(string.Format(CultureInfo.InvariantCulture, GbcaLoc.T("UpdateSizeMbFmt"), mb), theme, false));
            }
            if (result.PublishedAtUtc.HasValue)
            {
                chipsPanel.Children.Add(BuildMetadataChip(result.PublishedAtUtc.Value.ToLocalTime().ToString("dd.MM.yyyy", CultureInfo.InvariantCulture), theme, false));
            }
            _bodyHost.Children.Add(chipsPanel);

            // Changelog Section Header
            _bodyHost.Children.Add(new TextBlock
            {
                Text = GbcaLoc.T("UpdateWhatsNewLabel").ToUpperInvariant(),
                FontSize = 11,
                FontWeight = FontWeight.Bold,
                FontFamily = new FontFamily("Consolas, Cascadia Mono, monospace"),
                Foreground = theme.TextSecondaryBrush
            });

            // Scrollable Structured Localized Changelog Card
            var notesScroll = new ScrollViewer { MinHeight = 110, MaxHeight = 220 };
            var notesStack = new StackPanel { Spacing = 6 };
            string rawCleaned = string.IsNullOrWhiteSpace(result.Changelog)
                ? $"STORM GBCA {cleanNew}"
                : s_htmlTagRegex.Replace(result.Changelog, string.Empty);
            string localizedNotes = SelectLocalizedReleaseNotes(rawCleaned, GbcaLoc.CurrentLanguage);
            if (string.IsNullOrWhiteSpace(localizedNotes)) localizedNotes = $"STORM GBCA {cleanNew}";

            RenderStructuredMarkdown(notesStack, localizedNotes, theme, accent);
            notesScroll.Content = notesStack;
            _bodyHost.Children.Add(new Border
            {
                CornerRadius = new CornerRadius(14),
                Background = new SolidColorBrush(theme.BackgroundColor),
                BorderBrush = theme.BorderBrush,
                BorderThickness = new Thickness(1),
                Padding = new Thickness(14, 12),
                Child = notesScroll
            });

            // Download Progress Card
            _progressCard = new Border { IsVisible = false, CornerRadius = new CornerRadius(12), Background = new SolidColorBrush(theme.HeaderColor), BorderBrush = theme.BorderBrush, BorderThickness = new Thickness(1), Padding = new Thickness(12, 10) };
            var progStack = new StackPanel { Spacing = 8 };
            _progressBar = new ProgressBar { Minimum = 0, Maximum = 100, Value = 0, Height = 8, Foreground = new SolidColorBrush(accent) };
            progStack.Children.Add(_progressBar);
            var progStats = new Grid { ColumnDefinitions = new ColumnDefinitions("*,Auto,Auto") };
            _progressLeftText = new TextBlock { Text = GbcaLoc.T("UpdatePreparing"), FontSize = 11.5, Foreground = theme.TextSecondaryBrush };
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

            // Action Buttons
            _actionsPanel = new StackPanel { Spacing = 8 };
            var updBtn = new AuroraVolumetricButton { IsHero = true, Height = 42, HorizontalAlignment = HorizontalAlignment.Stretch };
            updBtn.Content = new TextBlock { Text = GbcaLoc.T("UpdateNowBtn"), FontSize = 14, FontWeight = FontWeight.Bold, Foreground = Brushes.White, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
            updBtn.Click += async (_, _) => await StartDownloadAsync(result);
            _actionsPanel.Children.Add(updBtn);

            var secRow = new Grid { ColumnDefinitions = new ColumnDefinitions("*,*"), ColumnSpacing = 8 };
            var laterBtn = CreateSecondaryButton(GbcaLoc.T("UpdateRemindLaterBtn"), theme, () => CloseRequested?.Invoke());
            var skipBtn = CreateSecondaryButton(GbcaLoc.T("UpdateSkipVersionBtn"), theme, () =>
            {
                StormGbcaUiState.Instance.Settings.SkippedUpdateVersion = cleanNew;
                StormGbcaUiState.Instance.SaveSettings();
                CloseRequested?.Invoke();
            });
            secRow.Children.Add(laterBtn);
            secRow.Children.Add(skipBtn);
            Grid.SetColumn(skipBtn, 1);
            _actionsPanel.Children.Add(secRow);

            // Footer Link to Open Release Page
            string pageUrl = !string.IsNullOrWhiteSpace(result.ReleasePageUrl)
                ? result.ReleasePageUrl
                : "https://github.com/ReiKatari/STORM_GBCA/releases";
            var linkBtn = new Border
            {
                HorizontalAlignment = HorizontalAlignment.Center,
                Padding = new Thickness(10, 4),
                CornerRadius = new CornerRadius(8),
                Cursor = new Cursor(StandardCursorType.Hand),
                Child = new TextBlock
                {
                    Text = GbcaLoc.T("UpdateOpenPageBtn"),
                    FontSize = 12,
                    FontWeight = FontWeight.SemiBold,
                    Foreground = new SolidColorBrush(accent)
                }
            };
            linkBtn.PointerPressed += (_, _) =>
            {
                try { Process.Start(new ProcessStartInfo(pageUrl) { UseShellExecute = true }); } catch { }
            };
            _actionsPanel.Children.Add(linkBtn);
            _bodyHost.Children.Add(_actionsPanel);
            return;
        }

        bool isError = mode == UpdateModalMode.Error;
        _titleText.Text = isError ? GbcaLoc.T("UpdateErrorTitle") : GbcaLoc.T("UpdateUpToDateTitle");
        _headerIcon.Kind = isError ? AuroraIconKind.WarningAmber : AuroraIconKind.CheckSuccess;
        SetHeaderTint(isError ? Color.Parse("#EF4444") : accent);

        string curClean = GetCleanCurrentVersion();
        string msg = isError
            ? (result?.ErrorMessage ?? GbcaLoc.T("UpdateErrorDefault"))
            : string.Format(CultureInfo.InvariantCulture, GbcaLoc.T("UpdateUpToDateMsg"), curClean, result?.SourceMirror ?? "GitHub");
        _bodyHost.Children.Add(new TextBlock { Text = msg, FontSize = 13, Foreground = theme.TextSecondaryBrush, TextWrapping = TextWrapping.Wrap });

        var row = new Grid { ColumnDefinitions = isError && _onRetryCallback != null ? new ColumnDefinitions("*,*") : new ColumnDefinitions("*"), ColumnSpacing = 8 };
        int okCol = 0;
        if (isError && _onRetryCallback != null)
        {
            row.Children.Add(CreateSecondaryButton(GbcaLoc.T("UpdateRetryBtn"), theme, () => _onRetryCallback()));
            okCol = 1;
        }
        var okBtn = new AuroraVolumetricButton { IsHero = true, Height = 38, HorizontalAlignment = HorizontalAlignment.Stretch };
        okBtn.Content = new TextBlock { Text = GbcaLoc.T("ActionClose"), FontSize = 13, FontWeight = FontWeight.Bold, Foreground = Brushes.White, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
        okBtn.Click += (_, _) => CloseRequested?.Invoke();
        row.Children.Add(okBtn);
        Grid.SetColumn(okBtn, okCol);
        _bodyHost.Children.Add(row);
    }

    private static void RenderStructuredMarkdown(StackPanel host, string markdown, AuroraThemeDefinition theme, Color accent)
    {
        var accentBrush = new SolidColorBrush(accent);
        var monoFont = new FontFamily("Consolas, Cascadia Mono, monospace");
        foreach (string rawLine in markdown.Replace("\r\n", "\n").Split('\n'))
        {
            string trimmed = rawLine.Trim();
            if (string.IsNullOrEmpty(trimmed)) continue;
            if (trimmed.StartsWith("<!--", StringComparison.Ordinal) && trimmed.EndsWith("-->", StringComparison.Ordinal)) continue;

            if (trimmed == "---" || trimmed == "***" || trimmed == "___")
            {
                host.Children.Add(new Border { Height = 1, Margin = new Thickness(0, 2), Background = theme.BorderBrush });
                continue;
            }

            if (trimmed.StartsWith("### ", StringComparison.Ordinal) || trimmed.StartsWith("## ", StringComparison.Ordinal) || trimmed.StartsWith("# ", StringComparison.Ordinal))
            {
                int level = trimmed.StartsWith("### ", StringComparison.Ordinal) ? 3 : trimmed.StartsWith("## ", StringComparison.Ordinal) ? 2 : 1;
                string headingText = trimmed.TrimStart('#').Trim();
                var hRow = new Grid { ColumnDefinitions = new ColumnDefinitions("Auto,*"), Margin = new Thickness(0, level <= 2 ? 4 : 2, 0, 0) };
                hRow.Children.Add(new Border
                {
                    Width = 3,
                    Height = 14,
                    CornerRadius = new CornerRadius(1.5),
                    Background = accentBrush,
                    VerticalAlignment = VerticalAlignment.Center,
                    Margin = new Thickness(0, 0, 8, 0)
                });
                var hTb = BuildInlineTextBlock(headingText, level <= 2 ? 13.5 : 12.5, theme.TextPrimaryBrush, theme.TextPrimaryBrush, accentBrush, monoFont, FontWeight.Bold);
                hRow.Children.Add(hTb);
                Grid.SetColumn(hTb, 1);
                host.Children.Add(hRow);
                continue;
            }

            if (trimmed.StartsWith("- ", StringComparison.Ordinal) || trimmed.StartsWith("* ", StringComparison.Ordinal))
            {
                string bulletText = trimmed[2..].Trim();
                var bRow = new Grid { ColumnDefinitions = new ColumnDefinitions("Auto,*"), Margin = new Thickness(4, 0, 0, 0) };
                bRow.Children.Add(new TextBlock
                {
                    Text = "-",
                    FontFamily = monoFont,
                    FontSize = 12.5,
                    FontWeight = FontWeight.Bold,
                    Foreground = accentBrush,
                    Margin = new Thickness(0, 0, 7, 0),
                    VerticalAlignment = VerticalAlignment.Top
                });
                var bTb = BuildInlineTextBlock(bulletText, 12.5, theme.TextSecondaryBrush, theme.TextPrimaryBrush, accentBrush, monoFont, FontWeight.Normal);
                bRow.Children.Add(bTb);
                Grid.SetColumn(bTb, 1);
                host.Children.Add(bRow);
                continue;
            }

            host.Children.Add(BuildInlineTextBlock(trimmed, 12.5, theme.TextSecondaryBrush, theme.TextPrimaryBrush, accentBrush, monoFont, FontWeight.Normal));
        }
    }

    private static TextBlock BuildInlineTextBlock(
        string text,
        double fontSize,
        IBrush baseBrush,
        IBrush boldBrush,
        IBrush codeBrush,
        FontFamily monoFont,
        FontWeight defaultWeight)
    {
        var tb = new TextBlock
        {
            FontSize = fontSize,
            Foreground = baseBrush,
            FontWeight = defaultWeight,
            TextWrapping = TextWrapping.Wrap,
            LineHeight = fontSize + 5.0
        };
        var inlines = tb.Inlines;
        if (inlines == null)
        {
            tb.Text = text;
            return tb;
        }

        int i = 0;
        while (i < text.Length)
        {
            if (i + 1 < text.Length && text[i] == '*' && text[i + 1] == '*')
            {
                int end = text.IndexOf("**", i + 2, StringComparison.Ordinal);
                if (end > i + 2)
                {
                    inlines.Add(new Run(text.Substring(i + 2, end - (i + 2))) { Foreground = boldBrush, FontWeight = FontWeight.Bold });
                    i = end + 2;
                    continue;
                }
                inlines.Add(new Run("**") { Foreground = baseBrush });
                i += 2;
            }
            else if (text[i] == '`')
            {
                int end = text.IndexOf('`', i + 1);
                if (end > i + 1)
                {
                    inlines.Add(new Run(text.Substring(i + 1, end - (i + 1))) { Foreground = codeBrush, FontFamily = monoFont, FontWeight = FontWeight.Medium });
                    i = end + 1;
                    continue;
                }
                inlines.Add(new Run("`") { Foreground = baseBrush });
                i += 1;
            }
            else
            {
                int nextBold = text.IndexOf("**", i, StringComparison.Ordinal);
                int nextCode = text.IndexOf('`', i);
                int end = text.Length;
                if (nextBold >= 0 && nextBold < end) end = nextBold;
                if (nextCode >= 0 && nextCode < end) end = nextCode;
                if (end == i) end = i + 1;
                inlines.Add(new Run(text.Substring(i, end - i)) { Foreground = baseBrush });
                i = end;
            }
        }
        return tb;
    }

    private async Task StartDownloadAsync(AuroraUpdateCheckResult result)
    {
        if (_isDownloading || string.IsNullOrWhiteSpace(result.DownloadUrl)) return;
        _isDownloading = true;
        _downloadCts = new CancellationTokenSource();
        if (_actionsPanel != null) _actionsPanel.IsVisible = false;
        if (_progressCard != null) _progressCard.IsVisible = true;

        string cleanVer = result.LatestVersion.ToString().Trim().TrimStart('v', 'V');
        string assetName = !string.IsNullOrWhiteSpace(result.AssetName) ? result.AssetName : $"STORM_GBCA_{cleanVer}_Setup.exe";
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
                    _progressLeftText.Text = string.Format(CultureInfo.InvariantCulture, "{0:F1} / {1:F1} MB", dlMb, totMb);
                    _progressPercentText.Text = $"{(int)pct}%";
                }
                _progressSpeedText.Text = p.SpeedMbPerSec > 0.05
                    ? string.Format(CultureInfo.InvariantCulture, GbcaLoc.T("UpdateSpeedMbFmt"), p.SpeedMbPerSec)
                    : string.Empty;
            });
        });

        try
        {
            var updater = new AuroraDualMirrorUpdater();
            bool ok = await updater.DownloadAndVerifyAsync(result.DownloadUrl, destPath, result.ExpectedSha256, progress, _downloadCts.Token, AuroraDualMirrorUpdater.DefaultGitHubRepo);
            _isDownloading = false;
            if (!ok)
            {
                UpdateState(UpdateModalMode.Error, result with { ErrorMessage = GbcaLoc.T("UpdateErrorDownload") });
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

    private static Border BuildMetadataChip(string label, AuroraThemeDefinition theme, bool highlighted)
    {
        var accent = theme.DefaultAccentColor;
        return new Border
        {
            Margin = new Thickness(0, 0, 8, 4),
            CornerRadius = new CornerRadius(9),
            Padding = new Thickness(9, 4),
            Background = highlighted ? new SolidColorBrush(Color.FromArgb(32, accent.R, accent.G, accent.B)) : new SolidColorBrush(theme.HeaderColor),
            BorderBrush = highlighted ? new SolidColorBrush(Color.FromArgb(105, accent.R, accent.G, accent.B)) : theme.BorderBrush,
            BorderThickness = new Thickness(1),
            Child = new TextBlock
            {
                Text = label,
                FontSize = 11,
                FontFamily = new FontFamily("Consolas, Cascadia Mono, monospace"),
                Foreground = highlighted ? theme.TextPrimaryBrush : theme.TextSecondaryBrush
            }
        };
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
        col.Children.Add(new TextBlock { Text = version, FontSize = 14, FontWeight = FontWeight.Bold, FontFamily = new FontFamily("Consolas, Cascadia Mono, monospace"), Foreground = accent ? new SolidColorBrush(accentColor) : theme.TextPrimaryBrush, HorizontalAlignment = HorizontalAlignment.Center });
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
