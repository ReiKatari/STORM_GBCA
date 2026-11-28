// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Mozilla Public License, Version 2.0 (MPL-2.0).

using Avalonia;
using Avalonia.Controls;
using Avalonia.Controls.ApplicationLifetimes;
using Avalonia.Themes.Fluent;
using Avalonia.Threading;
using Storm.AuroraFluid.Kit.Icons;
using Storm.AuroraFluid.Kit.Platform;
using Storm.AuroraFluid.Kit.Services;
using Storm.AuroraFluid.Kit.Themes;
using StormGbca.Core.Localization;
using StormGbca.UI.Common;
using StormGbca.UI.Windows;

namespace StormGbca.UI;

public class StormGbcaApp : Application
{
    public override void Initialize()
    {
        Styles.Add(new FluentTheme());
    }

    public override void OnFrameworkInitializationCompleted()
    {
        AuroraIconCatalog.VerifyZeroDuplicates();

        var uiState = StormGbcaUiState.Instance;
        string savedTheme = uiState.Settings.Theme;
        if (string.IsNullOrWhiteSpace(savedTheme)) savedTheme = AuroraThemeManager.ThemeMidnight;
        string themeKey = savedTheme.Replace("STORM ", "").Trim();

        AuroraAccentKind? parsedAccent = Enum.TryParse<AuroraAccentKind>(uiState.Settings.AccentName, true, out var acc) ? acc : null;
        AuroraTypographyPreset? parsedTypo = Enum.TryParse<AuroraTypographyPreset>(uiState.Settings.TypographyName, true, out var typ) ? typ : null;

        AuroraThemeManager.Instance.ApplyTheme(themeKey, parsedAccent, parsedTypo);
        AuroraTactileFeedbackService.Instance.Enabled = uiState.Settings.UiTactileAudioEnabled;

        if (!string.IsNullOrWhiteSpace(uiState.Settings.Language))
        {
            GbcaLoc.CurrentLanguage = uiState.Settings.Language;
        }

        if (ApplicationLifetime is IClassicDesktopStyleApplicationLifetime desktop)
        {
            var mainWindow = new StormGbcaMainWindow();
            desktop.MainWindow = mainWindow;

            var trayIcon = new TrayIcon { ToolTipText = $"STORM GBCA {GbcaLoc.AppVersion}" };
            string iconPath = Path.Combine(AppContext.BaseDirectory, "assets", "AppIcon.ico");
            if (File.Exists(iconPath))
            {
                try { trayIcon.Icon = new WindowIcon(iconPath); } catch { }
            }

            var trayMenu = new NativeMenu();
            trayMenu.Items.Add(new NativeMenuItem($"STORM GBCA {GbcaLoc.AppVersion}") { IsEnabled = false });
            var statusItem = new NativeMenuItem(GbcaLoc.T("TrayReady")) { IsEnabled = false };
            trayMenu.Items.Add(statusItem);
            trayMenu.Items.Add(new NativeMenuItemSeparator());

            var openItem = new NativeMenuItem(GbcaLoc.T("TrayOpenWindow"));
            openItem.Click += (_, _) => AuroraTrayManager.Instance.ShowAndActivate();
            trayMenu.Items.Add(openItem);

            var resumeItem = new NativeMenuItem(GbcaLoc.T("TrayContinue"));
            resumeItem.Click += (_, _) =>
            {
                AuroraTrayManager.Instance.ShowAndActivate();
                if (!StormGbcaUiState.Instance.IsEmulationActive) StormGbcaUiState.Instance.StartEmulation();
                else if (StormGbcaUiState.Instance.IsPaused) StormGbcaUiState.Instance.TogglePause();
            };
            trayMenu.Items.Add(resumeItem);

            var pauseItem = new NativeMenuItem(GbcaLoc.T("TrayPause"));
            pauseItem.Click += (_, _) =>
            {
                if (StormGbcaUiState.Instance.IsEmulationActive && !StormGbcaUiState.Instance.IsPaused)
                {
                    StormGbcaUiState.Instance.TogglePause();
                }
            };
            trayMenu.Items.Add(pauseItem);

            var settingsItem = new NativeMenuItem(GbcaLoc.T("TraySettings"));
            settingsItem.Click += (_, _) =>
            {
                AuroraTrayManager.Instance.ShowAndActivate();
                StormGbcaUiState.Instance.SetNavSection(NavSectionKind.Settings);
            };
            trayMenu.Items.Add(settingsItem);

            trayMenu.Items.Add(new NativeMenuItemSeparator());
            var exitItem = new NativeMenuItem(GbcaLoc.T("TrayExit"));
            exitItem.Click += (_, _) =>
            {
                StormGbcaUiState.Instance.StopEmulation();
                AuroraTrayManager.Instance.RequestExit();
            };
            trayMenu.Items.Add(exitItem);
            trayIcon.Menu = trayMenu;

            void RefreshTrayMenu()
            {
                Dispatcher.UIThread.Post(() =>
                {
                    var state = StormGbcaUiState.Instance;
                    bool active = state.IsEmulationActive;
                    bool paused = state.IsPaused;

                    if (active && state.SelectedRom != null)
                    {
                        statusItem.Header = paused
                            ? $"{state.SelectedRom.Title} ({GbcaLoc.T("MenuPause")})"
                            : state.SelectedRom.Title;
                    }
                    else
                    {
                        statusItem.Header = GbcaLoc.T("TrayReady");
                    }

                    openItem.Header = GbcaLoc.T("TrayOpenWindow");
                    resumeItem.Header = GbcaLoc.T("TrayContinue");
                    resumeItem.IsEnabled = !active || paused || state.SelectedRom != null;
                    pauseItem.Header = GbcaLoc.T("TrayPause");
                    pauseItem.IsEnabled = active && !paused;
                    settingsItem.Header = GbcaLoc.T("TraySettings");
                    exitItem.Header = GbcaLoc.T("TrayExit");
                });
            }

            GbcaLoc.LanguageChanged += _ => RefreshTrayMenu();
            uiState.EmulationStarted += _ => RefreshTrayMenu();
            uiState.EmulationStopped += RefreshTrayMenu;
            uiState.StateChanged += RefreshTrayMenu;
            RefreshTrayMenu();

            TrayIcon.SetIcons(this, new TrayIcons { trayIcon });
            AuroraTrayManager.Instance.AttachToWindow(
                mainWindow,
                "STORM GBCA",
                GbcaLoc.AppVersion,
                trayIcon,
                onShowRequested: () =>
                {
                    StormGbcaUiState.Instance.SetHiddenToTray(false);
                    mainWindow.Activate();
                });

            mainWindow.Closing += (_, _) =>
            {
                if (!AuroraTrayManager.Instance.IsExitRequestedFromTray)
                {
                    StormGbcaUiState.Instance.SetHiddenToTray(true);
                }
                else
                {
                    StormGbcaUiState.Instance.StopEmulation();
                    StormGbcaUiState.Instance.Emulator.Dispose();
                }
            };
        }

        base.OnFrameworkInitializationCompleted();
    }
}
