// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Apache License, Version 2.0.

using System.Text;
using System.Text.Json;
using StormGbca.Core.Configuration;
using StormGbca.Core.Native;
using StormGbca.Core.Paths;

namespace StormGbca.Core.Subsystems;

public sealed record RaAchievementInfo(
    int Id,
    string Title,
    string Description,
    int Points,
    string BadgeUrl,
    string Type,
    bool Unlocked,
    bool UnlockedHardcore
);

public sealed record RaLeaderboardInfo(
    int Id,
    string Title,
    string Description,
    string Format
);

public sealed record RaGameSessionInfo(
    int GameId,
    string GameTitle,
    string GameIconUrl,
    string BoxArtUrl,
    IReadOnlyList<RaAchievementInfo> Achievements,
    IReadOnlyList<RaLeaderboardInfo> Leaderboards
)
{
    public int TotalCount => Achievements.Count;
    public int UnlockedCount => Achievements.Count(a => a.Unlocked || a.UnlockedHardcore);
    public int EarnedPoints => Achievements.Where(a => a.Unlocked || a.UnlockedHardcore).Sum(a => a.Points);
    public int TotalPoints => Achievements.Sum(a => a.Points);
    public int CompletionPercent => TotalCount > 0 ? (UnlockedCount * 100) / TotalCount : 0;
}

/// <summary>
/// Full RetroAchievements service using storm_gbca_compute_ra_hash, storm_gbca_read_memory,
/// RA Web API authentication/profile stats/patch/unlocks, hardcore rules, and offline submission queue.
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
    public int Points { get; set; }
    public int SoftcorePoints { get; set; }
    public int Rank { get; set; }
    public string AvatarUrl { get; set; } = string.Empty;
    public string Motto { get; set; } = string.Empty;

    public bool IsLoggedIn => !string.IsNullOrWhiteSpace(Username) && !string.IsNullOrWhiteSpace(Token);
    public string ActiveGameHash { get; private set; } = string.Empty;
    public RaGameSessionInfo? ActiveSession { get; private set; }
    public string LastStatusMessage { get; private set; } = string.Empty;
    public bool IsEnabled { get; set; } = true;
    public bool IsEncoreMode { get; set; }
    public bool IsUnofficialEnabled { get; set; }
    public bool NotificationsEnabled { get; set; } = true;
    public bool LeaderboardsEnabled { get; set; } = true;
    public int PendingOfflineCount { get { lock (_lock) return _offlineQueue.Count; } }

    public RetroAchievementsService(HttpClient? httpClient = null)
    {
        _httpClient = httpClient ?? new HttpClient { Timeout = TimeSpan.FromSeconds(15) };
        _httpClient.DefaultRequestHeaders.UserAgent.ParseAdd("STORM-GBCA/1.5.0");
        LoadOfflineQueue();
    }

    public void Logout()
    {
        Username = string.Empty;
        Token = string.Empty;
        Points = 0;
        SoftcorePoints = 0;
        Rank = 0;
        AvatarUrl = string.Empty;
        Motto = string.Empty;
        ActiveSession = null;
        LastStatusMessage = "Вы вышли из аккаунта RetroAchievements.";
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
            return (false, "Введите имя пользователя и пароль RetroAchievements.");
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
                Points = ReadInt(root, "Score");
                SoftcorePoints = ReadInt(root, "SoftcoreScore");
                Rank = ReadInt(root, "Rank");
                Motto = root.TryGetProperty("Motto", out var mProp) ? (mProp.GetString() ?? string.Empty) : string.Empty;
                AvatarUrl = $"https://media.retroachievements.org/UserPic/{Uri.EscapeDataString(Username)}.png";
                LastStatusMessage = $"Подключен: {Username} | Hardcore: {Points} pts | Softcore: {SoftcorePoints} pts";
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

    public async Task<(bool Ok, int LatencyMs, string Details)> TestConnectionAsync(CancellationToken ct = default)
    {
        var sw = System.Diagnostics.Stopwatch.StartNew();
        try
        {
            if (IsLoggedIn)
            {
                string url = $"https://retroachievements.org/dorequest.php?r=login2&u={Uri.EscapeDataString(Username)}&t={Uri.EscapeDataString(Token)}";
                using var resp = await _httpClient.GetAsync(url, ct).ConfigureAwait(false);
                sw.Stop();
                string body = await resp.Content.ReadAsStringAsync(ct).ConfigureAwait(false);
                using var doc = JsonDocument.Parse(body);
                bool ok = doc.RootElement.TryGetProperty("Success", out var p) && p.GetBoolean();
                if (ok)
                {
                    Points = ReadInt(doc.RootElement, "Score");
                    SoftcorePoints = ReadInt(doc.RootElement, "SoftcoreScore");
                    Rank = ReadInt(doc.RootElement, "Rank");
                    return (true, (int)sw.ElapsedMilliseconds, $"OK ({sw.ElapsedMilliseconds} ms) - {Username} | HC: {Points} | SC: {SoftcorePoints}");
                }
                return (false, (int)sw.ElapsedMilliseconds, $"Token rejected ({sw.ElapsedMilliseconds} ms)");
            }
            else
            {
                using var resp = await _httpClient.GetAsync("https://retroachievements.org/dorequest.php?r=gameid&m=00000000000000000000000000000000", ct).ConfigureAwait(false);
                sw.Stop();
                return (resp.IsSuccessStatusCode, (int)sw.ElapsedMilliseconds,
                    resp.IsSuccessStatusCode
                        ? $"Server reachable ({sw.ElapsedMilliseconds} ms) - Sign in to track trophies"
                        : $"HTTP {(int)resp.StatusCode} ({sw.ElapsedMilliseconds} ms)");
            }
        }
        catch (Exception ex)
        {
            sw.Stop();
            return (false, (int)sw.ElapsedMilliseconds, $"Error: {ex.Message}");
        }
    }

    public async Task<bool> RefreshProfileAsync(CancellationToken ct = default)
    {
        if (!IsLoggedIn) return false;
        try
        {
            string url = $"https://retroachievements.org/dorequest.php?r=login2&u={Uri.EscapeDataString(Username)}&t={Uri.EscapeDataString(Token)}";
            using var resp = await _httpClient.GetAsync(url, ct).ConfigureAwait(false);
            string body = await resp.Content.ReadAsStringAsync(ct).ConfigureAwait(false);
            using var doc = JsonDocument.Parse(body);
            var root = doc.RootElement;
            if (root.TryGetProperty("Success", out var okProp) && okProp.GetBoolean())
            {
                Points = ReadInt(root, "Score");
                SoftcorePoints = ReadInt(root, "SoftcoreScore");
                Rank = ReadInt(root, "Rank");
                Motto = root.TryGetProperty("Motto", out var mProp) ? (mProp.GetString() ?? string.Empty) : string.Empty;
                AvatarUrl = $"https://media.retroachievements.org/UserPic/{Uri.EscapeDataString(Username)}.png";
                LastStatusMessage = $"Профиль обновлен: {Username} ({Points} pts)";
                return true;
            }
        }
        catch
        {
        }
        return false;
    }

    public async Task<RaGameSessionInfo?> FetchGameSessionAsync(string raHash, CancellationToken ct = default)
    {
        ActiveGameHash = raHash ?? string.Empty;
        if (!IsLoggedIn || string.IsNullOrWhiteSpace(ActiveGameHash)) return null;

        try
        {
            string idUrl = $"https://retroachievements.org/dorequest.php?r=gameid&m={Uri.EscapeDataString(ActiveGameHash)}";
            using var idResp = await _httpClient.GetAsync(idUrl, ct).ConfigureAwait(false);
            string idJson = await idResp.Content.ReadAsStringAsync(ct).ConfigureAwait(false);
            using var idDoc = JsonDocument.Parse(idJson);
            int gameId = ReadInt(idDoc.RootElement, "GameID");
            if (gameId <= 0) return null;

            string patchUrl = $"https://retroachievements.org/dorequest.php?r=patch&u={Uri.EscapeDataString(Username)}&t={Uri.EscapeDataString(Token)}&g={gameId}";
            using var pResp = await _httpClient.GetAsync(patchUrl, ct).ConfigureAwait(false);
            string pJson = await pResp.Content.ReadAsStringAsync(ct).ConfigureAwait(false);
            using var pDoc = JsonDocument.Parse(pJson);
            if (!pDoc.RootElement.TryGetProperty("PatchData", out var patchData)) return null;

            string title = patchData.TryGetProperty("Title", out var tEl) ? (tEl.GetString() ?? $"Game #{gameId}") : $"Game #{gameId}";
            string iconName = patchData.TryGetProperty("ImageIcon", out var iEl) ? (iEl.GetString() ?? string.Empty) : string.Empty;
            string gameIconUrl = string.IsNullOrWhiteSpace(iconName) ? string.Empty : $"https://media.retroachievements.org{iconName}";
            string boxArtName = patchData.TryGetProperty("ImageBoxArt", out var baEl) ? (baEl.GetString() ?? string.Empty) : string.Empty;
            string boxArtUrl = string.IsNullOrWhiteSpace(boxArtName) ? gameIconUrl : $"https://media.retroachievements.org{boxArtName}";

            var softUnlocks = await FetchUnlocksSetAsync(gameId, hardcore: false, ct).ConfigureAwait(false);
            var hardUnlocks = await FetchUnlocksSetAsync(gameId, hardcore: true, ct).ConfigureAwait(false);

            var list = new List<RaAchievementInfo>();
            if (patchData.TryGetProperty("Achievements", out var arr) && arr.ValueKind == JsonValueKind.Array)
            {
                foreach (var a in arr.EnumerateArray())
                {
                    int id = ReadInt(a, "ID");
                    string aTitle = a.TryGetProperty("Title", out var at) ? (at.GetString() ?? string.Empty) : string.Empty;
                    string desc = a.TryGetProperty("Description", out var ad) ? (ad.GetString() ?? string.Empty) : string.Empty;
                    int pts = ReadInt(a, "Points");
                    string badge = a.TryGetProperty("BadgeName", out var ab) ? (ab.GetString() ?? string.Empty) : string.Empty;
                    string type = a.TryGetProperty("Type", out var atyp) ? (atyp.GetString() ?? string.Empty) : string.Empty;
                    bool isSoft = softUnlocks.Contains(id);
                    bool isHard = hardUnlocks.Contains(id);
                    string badgeUrl = string.IsNullOrWhiteSpace(badge)
                        ? string.Empty
                        : $"https://media.retroachievements.org/Badge/{badge}{(isSoft || isHard ? "" : "_lock")}.png";
                    list.Add(new RaAchievementInfo(id, aTitle, desc, pts, badgeUrl, type, isSoft, isHard));
                }
            }

            var lbs = new List<RaLeaderboardInfo>();
            if (patchData.TryGetProperty("Leaderboards", out var lbArr) && lbArr.ValueKind == JsonValueKind.Array)
            {
                foreach (var lb in lbArr.EnumerateArray())
                {
                    lbs.Add(new RaLeaderboardInfo(
                        ReadInt(lb, "ID"),
                        lb.TryGetProperty("Title", out var lbt) ? (lbt.GetString() ?? "") : "",
                        lb.TryGetProperty("Description", out var lbd) ? (lbd.GetString() ?? "") : "",
                        lb.TryGetProperty("Format", out var lbf) ? (lbf.GetString() ?? "VALUE") : "VALUE"));
                }
            }
            var session = new RaGameSessionInfo(gameId, title, gameIconUrl, boxArtUrl, list, lbs);
            ActiveSession = session;
            return session;
        }
        catch
        {
            return null;
        }
    }

    private async Task<HashSet<int>> FetchUnlocksSetAsync(int gameId, bool hardcore, CancellationToken ct)
    {
        var set = new HashSet<int>();
        try
        {
            string url = $"https://retroachievements.org/dorequest.php?r=unlocks&u={Uri.EscapeDataString(Username)}&t={Uri.EscapeDataString(Token)}&g={gameId}&h={(hardcore ? 1 : 0)}";
            using var resp = await _httpClient.GetAsync(url, ct).ConfigureAwait(false);
            string body = await resp.Content.ReadAsStringAsync(ct).ConfigureAwait(false);
            using var doc = JsonDocument.Parse(body);
            if (doc.RootElement.TryGetProperty("UserUnlocks", out var arr) && arr.ValueKind == JsonValueKind.Array)
            {
                foreach (var el in arr.EnumerateArray())
                {
                    if (el.TryGetInt32(out int id)) set.Add(id);
                }
            }
        }
        catch
        {
        }
        return set;
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
            var session = await FetchGameSessionAsync(ActiveGameHash, ct).ConfigureAwait(false);
            if (session == null)
            {
                return $"Игра с хэшем {ActiveGameHash} не найдена в базе RetroAchievements.";
            }

            string sessUrl = $"https://retroachievements.org/dorequest.php?r=startsession&u={Uri.EscapeDataString(Username)}&t={Uri.EscapeDataString(Token)}&g={session.GameId}&h={(IsHardcoreMode ? 1 : 0)}";
            using var sessResp = await _httpClient.GetAsync(sessUrl, ct).ConfigureAwait(false);
            return sessResp.IsSuccessStatusCode
                ? $"RetroAchievements: {session.GameTitle} ({session.UnlockedCount}/{session.TotalCount} | {session.EarnedPoints} pts)"
                : $"Игра распознана ({session.GameTitle}), но сервер вернул {(int)sessResp.StatusCode}.";
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

    private static int ReadInt(JsonElement root, string name)
    {
        if (!root.TryGetProperty(name, out var el)) return 0;
        if (el.ValueKind == JsonValueKind.Number && el.TryGetInt32(out int v)) return v;
        if (el.ValueKind == JsonValueKind.String && int.TryParse(el.GetString(), out int sv)) return sv;
        return 0;
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
