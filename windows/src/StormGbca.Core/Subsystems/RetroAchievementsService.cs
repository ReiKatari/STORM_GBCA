// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Apache License, Version 2.0.

using System.Text;
using System.Text.Json;
using StormGbca.Core.Configuration;
using StormGbca.Core.Localization;
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
    bool UnlockedHardcore,
    string MemAddr = "",
    int Flags = 3
);

public sealed record RaLeaderboardInfo(
    int Id,
    string Title,
    string Description,
    string Format,
    string Mem = "",
    bool LowerIsBetter = false,
    bool Hidden = false
);

public sealed record RaGameSessionInfo(
    int GameId,
    string GameTitle,
    string GameIconUrl,
    string BoxArtUrl,
    IReadOnlyList<RaAchievementInfo> Achievements,
    IReadOnlyList<RaLeaderboardInfo> Leaderboards,
    string RichPresencePatch = "",
    string GameHash = ""
)
{
    public int TotalCount => Achievements.Count;
    public int UnlockedCount => Achievements.Count(a => a.Unlocked || a.UnlockedHardcore);
    public int EarnedPoints => Achievements.Where(a => a.Unlocked || a.UnlockedHardcore).Sum(a => a.Points);
    public int TotalPoints => Achievements.Sum(a => a.Points);
    public int CompletionPercent => TotalCount > 0 ? (UnlockedCount * 100) / TotalCount : 0;
}

/// <summary>
/// Full RetroAchievements service using storm_gbca_compute_ra_hash, rcheevos request builders,
/// compliant 3-segment User-Agent, HTTP POST login/session/award/lboard/ping requests,
/// Hardcore compliance enforcement, and persistent offline submission queue.
/// </summary>
public sealed class RetroAchievementsService
{
    private const string RaDoRequestUrl = "https://retroachievements.org/dorequest.php";

    private readonly HttpClient _httpClient;
    private readonly List<RetroAchievementsQueueItem> _offlineQueue = [];
    private readonly object _lock = new();
    private string _activeCoreSegment = "mgba/0.10.5";

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
    public bool ShowLeaderboardPopups { get; set; } = true;
    public int PendingOfflineCount { get { lock (_lock) return _offlineQueue.Count; } }

    public RetroAchievementsService(HttpClient? httpClient = null)
    {
        _httpClient = httpClient ?? new HttpClient { Timeout = TimeSpan.FromSeconds(15) };
        RefreshDefaultUserAgent();
        LoadOfflineQueue();
    }

    public void SetActiveCore(string? coreName)
    {
        string next = !string.IsNullOrWhiteSpace(coreName) &&
                      coreName.Contains("SameBoy", StringComparison.OrdinalIgnoreCase)
            ? "sameboy/1.0.5"
            : "mgba/0.10.5";
        if (!string.Equals(_activeCoreSegment, next, StringComparison.Ordinal))
        {
            _activeCoreSegment = next;
            RefreshDefaultUserAgent();
        }
    }

    public string BuildUserAgent()
    {
        string appVer = GbcaLoc.AppVersion.Trim().TrimStart('v', 'V');
        var osVer = Environment.OSVersion.Version;
        return $"STORM-GBCA/{appVer} (Windows {osVer.Major}.{osVer.Minor}) {_activeCoreSegment}";
    }

    private void RefreshDefaultUserAgent()
    {
        try
        {
            _httpClient.DefaultRequestHeaders.UserAgent.Clear();
            _httpClient.DefaultRequestHeaders.UserAgent.ParseAdd(BuildUserAgent());
        }
        catch
        {
        }
    }

    private async Task<HttpResponseMessage> SendRaRequestAsync(string url, string postBody, CancellationToken ct)
    {
        bool usePost = !string.IsNullOrEmpty(postBody);
        using var req = new HttpRequestMessage(usePost ? HttpMethod.Post : HttpMethod.Get, url);
        req.Headers.UserAgent.Clear();
        req.Headers.UserAgent.ParseAdd(BuildUserAgent());
        if (usePost)
        {
            req.Content = new StringContent(postBody, Encoding.UTF8, "application/x-www-form-urlencoded");
        }
        return await _httpClient.SendAsync(req, ct).ConfigureAwait(false);
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
            "loadstate" or "savestate_load" or "quickload" or "quickresume" => false,
            "rewind" => false,
            "cheat" or "cheats" => false,
            "ramfinder" or "memwrite" or "write_memory" => false,
            "overclock" => false,
            "slowdown" or "frameadvance" or "stepframe" => false,
            "softpatch" or "patch" => false,
            "savestate" or "quicksave" => true,
            "fastforward" => true,
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

        string user = username.Trim();
        try
        {
            if (!NativeCoreBindings.BuildLoginRequest(user, password, string.Empty, out string url, out string postBody))
            {
                url = RaDoRequestUrl;
                postBody = $"r=login2&u={Uri.EscapeDataString(user)}&p={Uri.EscapeDataString(password)}";
            }

            using var resp = await SendRaRequestAsync(url, postBody, ct).ConfigureAwait(false);
            string body = await resp.Content.ReadAsStringAsync(ct).ConfigureAwait(false);
            using var doc = JsonDocument.Parse(body);
            var root = doc.RootElement;
            if (root.TryGetProperty("Success", out var okProp) && okProp.GetBoolean())
            {
                Username = root.TryGetProperty("User", out var uProp) ? (uProp.GetString() ?? user) : user;
                Token = root.TryGetProperty("Token", out var tProp) ? (tProp.GetString() ?? string.Empty) : string.Empty;
                Points = ReadInt(root, "Score");
                SoftcorePoints = ReadInt(root, "SoftcoreScore");
                Rank = ReadInt(root, "Rank");
                Motto = root.TryGetProperty("Motto", out var mProp) ? (mProp.GetString() ?? string.Empty) : string.Empty;
                AvatarUrl = $"https://media.retroachievements.org/UserPic/{Uri.EscapeDataString(Username)}.png";
                LastStatusMessage = $"Подключен: {Username} | Hardcore: {Points} pts | Casual: {SoftcorePoints} pts";
                _ = FlushOfflineQueueAsync(ct);
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
                if (!NativeCoreBindings.BuildLoginRequest(Username, string.Empty, Token, out string url, out string postBody))
                {
                    url = RaDoRequestUrl;
                    postBody = $"r=login2&u={Uri.EscapeDataString(Username)}&t={Uri.EscapeDataString(Token)}";
                }
                using var resp = await SendRaRequestAsync(url, postBody, ct).ConfigureAwait(false);
                sw.Stop();
                string body = await resp.Content.ReadAsStringAsync(ct).ConfigureAwait(false);
                using var doc = JsonDocument.Parse(body);
                bool ok = doc.RootElement.TryGetProperty("Success", out var p) && p.GetBoolean();
                if (ok)
                {
                    Points = ReadInt(doc.RootElement, "Score");
                    SoftcorePoints = ReadInt(doc.RootElement, "SoftcoreScore");
                    Rank = ReadInt(doc.RootElement, "Rank");
                    return (true, (int)sw.ElapsedMilliseconds, $"OK ({sw.ElapsedMilliseconds} ms) - {Username} | Hardcore: {Points} | Casual: {SoftcorePoints}");
                }
                return (false, (int)sw.ElapsedMilliseconds, $"Token rejected ({sw.ElapsedMilliseconds} ms)");
            }
            else
            {
                using var resp = await SendRaRequestAsync($"{RaDoRequestUrl}?r=gameid&m=00000000000000000000000000000000", string.Empty, ct).ConfigureAwait(false);
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
            if (!NativeCoreBindings.BuildLoginRequest(Username, string.Empty, Token, out string url, out string postBody))
            {
                url = RaDoRequestUrl;
                postBody = $"r=login2&u={Uri.EscapeDataString(Username)}&t={Uri.EscapeDataString(Token)}";
            }
            using var resp = await SendRaRequestAsync(url, postBody, ct).ConfigureAwait(false);
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
            string idUrl = $"{RaDoRequestUrl}?r=gameid&m={Uri.EscapeDataString(ActiveGameHash)}";
            using var idResp = await SendRaRequestAsync(idUrl, string.Empty, ct).ConfigureAwait(false);
            string idJson = await idResp.Content.ReadAsStringAsync(ct).ConfigureAwait(false);
            using var idDoc = JsonDocument.Parse(idJson);
            int gameId = ReadInt(idDoc.RootElement, "GameID");
            if (gameId <= 0) return null;

            string patchPost = $"r=patch&u={Uri.EscapeDataString(Username)}&t={Uri.EscapeDataString(Token)}&g={gameId}";
            using var pResp = await SendRaRequestAsync(RaDoRequestUrl, patchPost, ct).ConfigureAwait(false);
            string pJson = await pResp.Content.ReadAsStringAsync(ct).ConfigureAwait(false);
            using var pDoc = JsonDocument.Parse(pJson);
            if (!pDoc.RootElement.TryGetProperty("PatchData", out var patchData)) return null;

            string title = patchData.TryGetProperty("Title", out var tEl) ? (tEl.GetString() ?? $"Game #{gameId}") : $"Game #{gameId}";
            string iconName = patchData.TryGetProperty("ImageIcon", out var iEl) ? (iEl.GetString() ?? string.Empty) : string.Empty;
            string gameIconUrl = string.IsNullOrWhiteSpace(iconName) ? string.Empty : $"https://media.retroachievements.org{iconName}";
            string boxArtName = patchData.TryGetProperty("ImageBoxArt", out var baEl) ? (baEl.GetString() ?? string.Empty) : string.Empty;
            string boxArtUrl = string.IsNullOrWhiteSpace(boxArtName) ? gameIconUrl : $"https://media.retroachievements.org{boxArtName}";
            string richPresencePatch = patchData.TryGetProperty("RichPresencePatch", out var rpEl) ? (rpEl.GetString() ?? string.Empty) : string.Empty;

            var casualUnlocks = await FetchUnlocksSetAsync(gameId, hardcore: false, ct).ConfigureAwait(false);
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
                    string memAddr = a.TryGetProperty("MemAddr", out var amem) ? (amem.GetString() ?? string.Empty) : string.Empty;
                    int flags = a.TryGetProperty("Flags", out _) ? ReadInt(a, "Flags") : 3;
                    bool isCasual = casualUnlocks.Contains(id);
                    bool isHard = hardUnlocks.Contains(id);
                    string badgeUrl = string.IsNullOrWhiteSpace(badge)
                        ? string.Empty
                        : $"https://media.retroachievements.org/Badge/{badge}{(isCasual || isHard ? "" : "_lock")}.png";
                    list.Add(new RaAchievementInfo(id, aTitle, desc, pts, badgeUrl, type, isCasual, isHard, memAddr, flags));
                }
            }

            var lbs = new List<RaLeaderboardInfo>();
            if (patchData.TryGetProperty("Leaderboards", out var lbArr) && lbArr.ValueKind == JsonValueKind.Array)
            {
                foreach (var lb in lbArr.EnumerateArray())
                {
                    bool lowerIsBetter = lb.TryGetProperty("LowerIsBetter", out var libEl) &&
                                         (libEl.ValueKind == JsonValueKind.True ||
                                          (libEl.ValueKind == JsonValueKind.Number && libEl.GetInt32() != 0));
                    bool hidden = lb.TryGetProperty("Hidden", out var hidEl) &&
                                  (hidEl.ValueKind == JsonValueKind.True ||
                                   (hidEl.ValueKind == JsonValueKind.Number && hidEl.GetInt32() != 0));
                    lbs.Add(new RaLeaderboardInfo(
                        ReadInt(lb, "ID"),
                        lb.TryGetProperty("Title", out var lbt) ? (lbt.GetString() ?? string.Empty) : string.Empty,
                        lb.TryGetProperty("Description", out var lbd) ? (lbd.GetString() ?? string.Empty) : string.Empty,
                        lb.TryGetProperty("Format", out var lbf) ? (lbf.GetString() ?? "VALUE") : "VALUE",
                        lb.TryGetProperty("Mem", out var lbm) ? (lbm.GetString() ?? string.Empty) : string.Empty,
                        lowerIsBetter,
                        hidden));
                }
            }
            var session = new RaGameSessionInfo(gameId, title, gameIconUrl, boxArtUrl, list, lbs, richPresencePatch, ActiveGameHash);
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
            string post = $"r=unlocks&u={Uri.EscapeDataString(Username)}&t={Uri.EscapeDataString(Token)}&g={gameId}&h={(hardcore ? 1 : 0)}";
            using var resp = await SendRaRequestAsync(RaDoRequestUrl, post, ct).ConfigureAwait(false);
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

    public async Task<bool> StartSessionAsync(int gameId, string gameHash, bool hardcore, CancellationToken ct = default)
    {
        if (!IsLoggedIn || gameId <= 0) return false;
        try
        {
            if (!NativeCoreBindings.BuildStartSessionRequest(Username, Token, (uint)gameId, gameHash ?? string.Empty, hardcore, out string url, out string postBody))
            {
                url = RaDoRequestUrl;
                postBody = $"r=startsession&u={Uri.EscapeDataString(Username)}&t={Uri.EscapeDataString(Token)}&g={gameId}&h={(hardcore ? 1 : 0)}&m={Uri.EscapeDataString(gameHash ?? string.Empty)}";
            }
            using var resp = await SendRaRequestAsync(url, postBody, ct).ConfigureAwait(false);
            if (resp.IsSuccessStatusCode)
            {
                _ = FlushOfflineQueueAsync(ct);
                return true;
            }
        }
        catch
        {
        }
        return false;
    }

    public async Task<bool> SendPingAsync(int gameId, string gameHash, string richPresence, bool hardcore, CancellationToken ct = default)
    {
        if (!IsLoggedIn || gameId <= 0) return false;
        try
        {
            if (!NativeCoreBindings.BuildPingRequest(Username, Token, (uint)gameId, richPresence ?? string.Empty, gameHash ?? string.Empty, hardcore, out string url, out string postBody))
            {
                url = RaDoRequestUrl;
                postBody = $"r=ping&u={Uri.EscapeDataString(Username)}&t={Uri.EscapeDataString(Token)}&g={gameId}&m={Uri.EscapeDataString(richPresence ?? string.Empty)}";
            }
            using var resp = await SendRaRequestAsync(url, postBody, ct).ConfigureAwait(false);
            if (resp.IsSuccessStatusCode)
            {
                if (PendingOfflineCount > 0) _ = FlushOfflineQueueAsync(ct);
                return true;
            }
        }
        catch
        {
        }
        return false;
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

            bool started = await StartSessionAsync(session.GameId, ActiveGameHash, IsHardcoreMode, ct).ConfigureAwait(false);
            return started
                ? $"RetroAchievements: {session.GameTitle} ({session.UnlockedCount}/{session.TotalCount} | {session.EarnedPoints} pts)"
                : $"Игра распознана ({session.GameTitle}), ожидание ответа сервера RA.";
        }
        catch (Exception ex)
        {
            return $"Ошибка проверки игры RA: {ex.Message}";
        }
    }

    public async Task<bool> AwardAchievementAsync(int achievementId, bool hardcore, string gameHash, CancellationToken ct = default)
    {
        if (!IsLoggedIn) return false;

        MarkLocalAchievementUnlocked(achievementId, hardcore);

        try
        {
            if (NativeCoreBindings.BuildAwardRequest(Username, Token, (uint)achievementId, hardcore, gameHash ?? ActiveGameHash, out string url, out string postBody))
            {
                using var response = await SendRaRequestAsync(url, postBody, ct).ConfigureAwait(false);
                if (response.IsSuccessStatusCode)
                {
                    string respBody = await response.Content.ReadAsStringAsync(ct).ConfigureAwait(false);
                    using var doc = JsonDocument.Parse(respBody);
                    if (doc.RootElement.TryGetProperty("Score", out _)) Points = ReadInt(doc.RootElement, "Score");
                    if (doc.RootElement.TryGetProperty("SoftcoreScore", out _)) SoftcorePoints = ReadInt(doc.RootElement, "SoftcoreScore");
                    return true;
                }
            }
        }
        catch
        {
        }

        EnqueueOfflineItem(new RetroAchievementsQueueItem
        {
            ItemType = "achievement",
            AchievementId = achievementId,
            Hardcore = hardcore,
            GameHash = string.IsNullOrWhiteSpace(gameHash) ? ActiveGameHash : gameHash,
            Username = Username,
            TimestampUtc = DateTime.UtcNow
        });
        return false;
    }

    public Task<bool> UnlockAchievementAsync(int achievementId, string gameHash, CancellationToken ct = default)
        => AwardAchievementAsync(achievementId, IsHardcoreMode, gameHash, ct);

    public async Task<bool> SubmitLeaderboardAsync(int lboardId, int score, string gameHash, bool hardcore, CancellationToken ct = default)
    {
        if (!IsLoggedIn || !hardcore || lboardId <= 0) return false;

        try
        {
            if (NativeCoreBindings.BuildLboardRequest(Username, Token, (uint)lboardId, score, gameHash ?? ActiveGameHash, out string url, out string postBody))
            {
                using var response = await SendRaRequestAsync(url, postBody, ct).ConfigureAwait(false);
                if (response.IsSuccessStatusCode) return true;
            }
        }
        catch
        {
        }

        EnqueueOfflineItem(new RetroAchievementsQueueItem
        {
            ItemType = "leaderboard",
            LeaderboardId = lboardId,
            Score = score,
            Hardcore = true,
            GameHash = string.IsNullOrWhiteSpace(gameHash) ? ActiveGameHash : gameHash,
            Username = Username,
            TimestampUtc = DateTime.UtcNow
        });
        return false;
    }

    private void MarkLocalAchievementUnlocked(int achievementId, bool hardcore)
    {
        var session = ActiveSession;
        if (session == null) return;
        var updated = session.Achievements.Select(a =>
        {
            if (a.Id != achievementId) return a;
            bool nextHard = a.UnlockedHardcore || hardcore;
            bool nextCasual = a.Unlocked || !hardcore;
            return a with { Unlocked = nextCasual, UnlockedHardcore = nextHard };
        }).ToList();
        ActiveSession = session with { Achievements = updated };
    }

    private void EnqueueOfflineItem(RetroAchievementsQueueItem item)
    {
        lock (_lock)
        {
            _offlineQueue.Add(item);
        }
        SaveOfflineQueue();
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
            if (ct.IsCancellationRequested)
            {
                remaining.Add(item);
                continue;
            }
            try
            {
                string user = string.IsNullOrWhiteSpace(item.Username) ? Username : item.Username;
                bool built = string.Equals(item.ItemType, "leaderboard", StringComparison.OrdinalIgnoreCase)
                    ? NativeCoreBindings.BuildLboardRequest(user, Token, (uint)item.LeaderboardId, item.Score, item.GameHash, out string url, out string postBody)
                    : NativeCoreBindings.BuildAwardRequest(user, Token, (uint)item.AchievementId, item.Hardcore, item.GameHash, out url, out postBody);

                if (built)
                {
                    using var response = await SendRaRequestAsync(url, postBody, ct).ConfigureAwait(false);
                    if (response.IsSuccessStatusCode)
                    {
                        submitted++;
                        continue;
                    }
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
        }
        SaveOfflineQueue();
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
            List<RetroAchievementsQueueItem> snapshot;
            lock (_lock)
            {
                snapshot = _offlineQueue.ToList();
            }
            string json = JsonSerializer.Serialize(snapshot, GbcaJsonContext.Default.ListRetroAchievementsQueueItem);
            File.WriteAllText(path, json);
        }
        catch
        {
        }
    }
}
