// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Apache License, Version 2.0.

using System.Text;
using System.Text.Json;
using StormGbca.Core.Configuration;
using StormGbca.Core.Native;
using StormGbca.Core.Paths;

namespace StormGbca.Core.Subsystems;

/// <summary>
/// RetroAchievements service using storm_gbca_compute_ra_hash, storm_gbca_read_memory,
/// RA Web API authentication/unlocks, hardcore rules, and offline submission queue.
/// </summary>
public sealed class RetroAchievementsService
{
    private readonly HttpClient _httpClient;
    private readonly List<RetroAchievementsQueueItem> _offlineQueue = [];
    private readonly object _lock = new();

    public string Username { get; set; } = string.Empty;
    public string Token { get; set; } = string.Empty;
    public bool IsHardcoreMode { get; set; }
    public bool IsRichPresenceEnabled { get; set; } = true;
    public bool IsLoggedIn => !string.IsNullOrWhiteSpace(Username) && !string.IsNullOrWhiteSpace(Token);
    public string ActiveGameHash { get; private set; } = string.Empty;
    public string LastStatusMessage { get; private set; } = string.Empty;

    public RetroAchievementsService(HttpClient? httpClient = null)
    {
        _httpClient = httpClient ?? new HttpClient { Timeout = TimeSpan.FromSeconds(12) };
        LoadOfflineQueue();
    }

    public bool IsFeatureAllowedInCurrentMode(string featureName)
    {
        if (!IsHardcoreMode) return true;
        return featureName.ToLowerInvariant() switch
        {
            "loadstate" or "savestate_load" => false,
            "rewind" => false,
            "cheat" or "cheats" => false,
            "slowdown" => false,
            _ => true,
        };
    }

    public unsafe string ComputeRomHash(string romPath, StormPlatform platform)
    {
        if (string.IsNullOrWhiteSpace(romPath) || !File.Exists(romPath) || !NativeCoreBindings.IsAvailable)
        {
            return string.Empty;
        }

        byte* buf = stackalloc byte[33];
        if (NativeCoreBindings.storm_gbca_compute_ra_hash(romPath, (int)platform, buf) == 0)
        {
            int len = 0;
            while (len < 32 && buf[len] != 0) len++;
            string hash = len > 0 ? Encoding.ASCII.GetString(buf, len) : string.Empty;
            ActiveGameHash = hash;
            return hash;
        }
        return string.Empty;
    }

    public unsafe byte[] ReadMemoryRange(nint ctx, StormMemoryRegion region, uint address, int length)
    {
        if (ctx == nint.Zero || length <= 0 || !NativeCoreBindings.IsAvailable) return [];
        byte[] result = new byte[length];
        fixed (byte* dst = result)
        {
            nuint read = NativeCoreBindings.storm_gbca_read_memory(ctx, (int)region, address, dst, (nuint)length);
            if ((int)read < length)
            {
                Array.Resize(ref result, (int)read);
            }
        }
        return result;
    }

    public async Task<(bool Success, string Message)> LoginAsync(string username, string password, CancellationToken ct = default)
    {
        if (string.IsNullOrWhiteSpace(username) || string.IsNullOrWhiteSpace(password))
        {
            return (false, "Введите имя пользователя и пароль или API-токен.");
        }

        try
        {
            string url = $"https://retroachievements.org/dorequest.php?r=login2&u={Uri.EscapeDataString(username.Trim())}&p={Uri.EscapeDataString(password)}";
            using var resp = await _httpClient.GetAsync(url, ct).ConfigureAwait(false);
            string body = await resp.Content.ReadAsStringAsync(ct).ConfigureAwait(false);
            using var doc = JsonDocument.Parse(body);
            var root = doc.RootElement;
            if (root.TryGetProperty("Success", out var okProp) && okProp.GetBoolean())
            {
                Username = root.TryGetProperty("User", out var uProp) ? (uProp.GetString() ?? username.Trim()) : username.Trim();
                Token = root.TryGetProperty("Token", out var tProp) ? (tProp.GetString() ?? string.Empty) : string.Empty;
                LastStatusMessage = $"Вход выполнен: {Username}";
                return (true, LastStatusMessage);
            }

            string err = root.TryGetProperty("Error", out var eProp) ? (eProp.GetString() ?? "Ошибка входа") : "Неверные учетные данные";
            LastStatusMessage = err;
            return (false, err);
        }
        catch (Exception ex)
        {
            LastStatusMessage = $"Ошибка сети RA: {ex.Message}";
            return (false, LastStatusMessage);
        }
    }

    public async Task<string> StartSessionAndFetchSummaryAsync(string raHash, CancellationToken ct = default)
    {
        ActiveGameHash = raHash ?? string.Empty;
        if (!IsLoggedIn || string.IsNullOrWhiteSpace(ActiveGameHash))
        {
            return "Недостаточно данных для сессии RetroAchievements.";
        }

        try
        {
            string idUrl = $"https://retroachievements.org/dorequest.php?r=gameid&m={Uri.EscapeDataString(ActiveGameHash)}";
            using var idResp = await _httpClient.GetAsync(idUrl, ct).ConfigureAwait(false);
            string idJson = await idResp.Content.ReadAsStringAsync(ct).ConfigureAwait(false);
            using var doc = JsonDocument.Parse(idJson);
            int gameId = doc.RootElement.TryGetProperty("GameID", out var gid) ? gid.GetInt32() : 0;
            if (gameId <= 0)
            {
                return $"Игра с хэшем {ActiveGameHash} не найдена в базе RetroAchievements.";
            }

            string sessUrl = $"https://retroachievements.org/dorequest.php?r=startsession&u={Uri.EscapeDataString(Username)}&t={Uri.EscapeDataString(Token)}&g={gameId}&h={(IsHardcoreMode ? 1 : 0)}";
            using var sessResp = await _httpClient.GetAsync(sessUrl, ct).ConfigureAwait(false);
            return sessResp.IsSuccessStatusCode
                ? $"Сессия RetroAchievements запущена (Game ID: {gameId})"
                : $"Игра распознана (Game ID: {gameId}), но сервер вернул {(int)sessResp.StatusCode}.";
        }
        catch (Exception ex)
        {
            return $"Ошибка проверки игры RA: {ex.Message}";
        }
    }

    public async Task<bool> UnlockAchievementAsync(int achievementId, string gameHash, CancellationToken ct = default)
    {
        if (!IsLoggedIn) return false;

        try
        {
            string url = $"https://retroachievements.org/dorequest.php?r=awardachievement&u={Uri.EscapeDataString(Username)}&t={Uri.EscapeDataString(Token)}&a={achievementId}&h={(IsHardcoreMode ? 1 : 0)}";
            using var response = await _httpClient.GetAsync(url, ct).ConfigureAwait(false);
            if (response.IsSuccessStatusCode) return true;
        }
        catch
        {
        }

        lock (_lock)
        {
            _offlineQueue.Add(new RetroAchievementsQueueItem
            {
                AchievementId = achievementId,
                GameHash = gameHash,
                Username = Username,
                TimestampUtc = DateTime.UtcNow
            });
            SaveOfflineQueue();
        }
        return false;
    }

    public async Task<int> FlushOfflineQueueAsync(CancellationToken ct = default)
    {
        if (!IsLoggedIn) return 0;

        List<RetroAchievementsQueueItem> toProcess;
        lock (_lock)
        {
            if (_offlineQueue.Count == 0) return 0;
            toProcess = _offlineQueue.ToList();
        }

        int submitted = 0;
        var remaining = new List<RetroAchievementsQueueItem>();
        foreach (var item in toProcess)
        {
            if (ct.IsCancellationRequested) break;
            try
            {
                string url = $"https://retroachievements.org/dorequest.php?r=awardachievement&u={Uri.EscapeDataString(item.Username)}&t={Uri.EscapeDataString(Token)}&a={item.AchievementId}&h={(IsHardcoreMode ? 1 : 0)}";
                using var response = await _httpClient.GetAsync(url, ct).ConfigureAwait(false);
                if (response.IsSuccessStatusCode)
                {
                    submitted++;
                    continue;
                }
            }
            catch
            {
            }
            remaining.Add(item);
        }

        lock (_lock)
        {
            _offlineQueue.Clear();
            _offlineQueue.AddRange(remaining);
            SaveOfflineQueue();
        }
        return submitted;
    }

    private void LoadOfflineQueue()
    {
        string path = StormGbcaPaths.RetroAchievementsQueuePath;
        if (!File.Exists(path)) return;
        try
        {
            string json = File.ReadAllText(path);
            var items = JsonSerializer.Deserialize(json, GbcaJsonContext.Default.ListRetroAchievementsQueueItem);
            if (items != null)
            {
                lock (_lock)
                {
                    _offlineQueue.Clear();
                    _offlineQueue.AddRange(items);
                }
            }
        }
        catch
        {
        }
    }

    private void SaveOfflineQueue()
    {
        try
        {
            string path = StormGbcaPaths.RetroAchievementsQueuePath;
            Directory.CreateDirectory(Path.GetDirectoryName(path)!);
            lock (_lock)
            {
                string json = JsonSerializer.Serialize(_offlineQueue, GbcaJsonContext.Default.ListRetroAchievementsQueueItem);
                File.WriteAllText(path, json);
            }
        }
        catch
        {
        }
    }
}
