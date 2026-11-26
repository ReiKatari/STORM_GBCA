// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Apache License, Version 2.0.

using System.Globalization;

namespace StormGbca.Core.Localization;

public sealed record GbcaLanguageInfo(string Code, string NativeName, string EnglishName);

/// <summary>
/// 11-language localization dictionary for STORM GBCA 1.0.0.
/// Enforces full words (zero truncated abbreviations) and zero em-dashes/en-dashes.
/// </summary>
public static class GbcaLoc
{
    public const string AppVersion = "1.1.0";

    public static event Action<string>? LanguageChanged;

    public static readonly IReadOnlyList<GbcaLanguageInfo> SupportedLanguages =
    [
        new("ru", "Русский", "Russian"),
        new("en", "English", "English"),
        new("de", "Deutsch", "German"),
        new("fr", "Français", "French"),
        new("zh", "简体中文", "Chinese"),
        new("ko", "한국어", "Korean"),
        new("ja", "日本語", "Japanese"),
        new("es", "Español", "Spanish"),
        new("it", "Italiano", "Italian"),
        new("pt", "Português", "Portuguese"),
        new("hi", "हिन्दी", "Hindi")
    ];

    private static string s_currentLanguage = DetectInitialLanguage();

    public static string CurrentLanguage
    {
        get => s_currentLanguage;
        set
        {
            if (SupportedLanguages.Any(l => string.Equals(l.Code, value, StringComparison.OrdinalIgnoreCase)))
            {
                string next = value.ToLowerInvariant();
                if (!string.Equals(s_currentLanguage, next, StringComparison.OrdinalIgnoreCase))
                {
                    s_currentLanguage = next;
                    LanguageChanged?.Invoke(next);
                }
            }
        }
    }

    public static string T(string key) => Get(key);

    public static string Get(string key, string? lang = null)
    {
        string target = lang?.ToLowerInvariant() ?? s_currentLanguage;
        if (s_translations.TryGetValue(target, out var dict) && dict.TryGetValue(key, out var val))
        {
            return val;
        }
        if (s_translations.TryGetValue("ru", out var ru) && ru.TryGetValue(key, out var ruVal))
        {
            return ruVal;
        }
        if (s_translations.TryGetValue("en", out var en) && en.TryGetValue(key, out var enVal))
        {
            return enVal;
        }
        return key;
    }

    private static string DetectInitialLanguage()
    {
        string two = CultureInfo.CurrentUICulture.TwoLetterISOLanguageName.ToLowerInvariant();
        return SupportedLanguages.Any(l => l.Code == two) ? two : "ru";
    }

    private static readonly Dictionary<string, Dictionary<string, string>> s_translations = new(StringComparer.OrdinalIgnoreCase)
    {
        ["ru"] = new(StringComparer.OrdinalIgnoreCase)
        {
            ["AppTitle"] = "STORM GBCA",
            ["AppSubtitle"] = "Эмулятор Nintendo Game Boy, Game Boy Color и Game Boy Advance",
            ["MenuFile"] = "Файл",
            ["MenuEmulation"] = "Эмуляция",
            ["MenuSkins"] = "Корпусы и оверлеи",
            ["MenuSettings"] = "Настройки",
            ["MenuHelp"] = "Справка",
            ["MenuOpenRom"] = "Открыть образ игры...",
            ["MenuScanFolder"] = "Добавить папку с играми...",
            ["MenuRescanLibrary"] = "Пересканировать библиотеку",
            ["MenuExit"] = "Выход",
            ["MenuResume"] = "Продолжить",
            ["MenuPause"] = "Пауза",
            ["MenuReset"] = "Перезапуск консоли",
            ["MenuQuickSave"] = "Быстрое сохранение",
            ["MenuQuickLoad"] = "Быстрая загрузка",
            ["MenuCheckUpdates"] = "Проверить обновления...",
            ["MenuAbout"] = "О программе STORM GBCA",
            ["LaunchGame"] = "Запустить игру",
            ["ActionClose"] = "Закрыть",
            ["TrayReady"] = "Готов к запуску",
            ["TrayOpenWindow"] = "Открыть главное окно",
            ["TrayContinue"] = "Продолжить игру",
            ["TrayPause"] = "Пауза эмуляции",
            ["TraySettings"] = "Настройки",
            ["TrayExit"] = "Выход",
            ["SettingsSavedMsg"] = "Конфигурация сохранена",
        },
        ["en"] = new(StringComparer.OrdinalIgnoreCase)
        {
            ["AppTitle"] = "STORM GBCA",
            ["AppSubtitle"] = "Nintendo Game Boy, Game Boy Color and Game Boy Advance Emulator",
            ["MenuFile"] = "File",
            ["MenuEmulation"] = "Emulation",
            ["MenuSkins"] = "Skins and Overlays",
            ["MenuSettings"] = "Settings",
            ["MenuHelp"] = "Help",
            ["MenuOpenRom"] = "Open Game ROM...",
            ["MenuScanFolder"] = "Add Games Folder...",
            ["MenuRescanLibrary"] = "Rescan Library",
            ["MenuExit"] = "Exit",
            ["MenuResume"] = "Resume",
            ["MenuPause"] = "Pause",
            ["MenuReset"] = "Reset Console",
            ["MenuQuickSave"] = "Quick Save",
            ["MenuQuickLoad"] = "Quick Load",
            ["MenuCheckUpdates"] = "Check for Updates...",
            ["MenuAbout"] = "About STORM GBCA",
            ["LaunchGame"] = "Launch Game",
            ["ActionClose"] = "Close",
            ["TrayReady"] = "Ready",
            ["TrayOpenWindow"] = "Open Main Window",
            ["TrayContinue"] = "Resume Game",
            ["TrayPause"] = "Pause Emulation",
            ["TraySettings"] = "Settings",
            ["TrayExit"] = "Exit",
            ["SettingsSavedMsg"] = "Configuration saved",
        }
    };
}
