// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Apache License, Version 2.0.

using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;
using StormGbca.Core.Configuration;
using StormGbca.Core.Native;
using StormGbca.Core.Paths;

namespace StormGbca.Core.Library;

public enum GbcaPlatformFilter
{
    All = 0,
    Gb = 1,
    Gbc = 2,
    Gba = 3,
    Favorites = 4,
}

public enum GbcaSortMode
{
    Title = 0,
    RecentlyPlayed = 1,
    MostPlayed = 2,
    FileSize = 3,
    Platform = 4,
}

/// <summary>
/// Scans user ROM folders for .gb, .gbc, .gba, .sgb, .zip, .7z using storm_gbca_inspect_rom_file,
/// tracks Favorites and Recently Played, and fetches/caches boxart from Libretro Thumbnails.
/// </summary>
public sealed partial class GbcaRomLibrary
{
    private static readonly Regex s_parensTagRegex = new(@"\s*[\(\[][^\)\]]*[\)\]]", RegexOptions.Compiled);
    private static readonly Regex s_hrefPngRegex = new(@"href=""([^""]+\.png)""", RegexOptions.Compiled | RegexOptions.IgnoreCase);
    private static readonly Regex s_nonAlnumRegex = new(@"[^a-z0-9]+", RegexOptions.Compiled);

    private readonly List<GbcaRomRecord> _roms = [];
    private readonly object _syncLock = new();
    private readonly HttpClient _httpClient = new() { Timeout = TimeSpan.FromSeconds(18) };
    private readonly Dictionary<string, List<string>> _indexCache = new(StringComparer.OrdinalIgnoreCase);
    private readonly SemaphoreSlim _indexLock = new(1, 1);

    public event Action? LibraryChanged;
    public event Action<GbcaRomRecord>? CoverArtUpdated;

    public IReadOnlyList<GbcaRomRecord> AllRoms
    {
        get
        {
            lock (_syncLock) return _roms.ToList();
        }
    }

    public GbcaRomLibrary()
    {
        _httpClient.DefaultRequestHeaders.UserAgent.ParseAdd("Mozilla/5.0 (Windows NT 10.0; Win64; x64) STORM_GBCA/1.1.0");
        LoadCache();
    }

    private void LoadCache()
    {
        try
        {
            string path = StormGbcaPaths.LibraryCachePath;
            if (!File.Exists(path)) return;
            string json = File.ReadAllText(path);
            var list = JsonSerializer.Deserialize(json, GbcaJsonContext.Default.ListGbcaRomRecord);
            if (list != null)
            {
                lock (_syncLock)
                {
                    _roms.Clear();
                    _roms.AddRange(list.Where(r => !string.IsNullOrWhiteSpace(r.FilePath) && File.Exists(r.FilePath)));
                }
            }
        }
        catch
        {
        }
    }

    private void SaveCache()
    {
        try
        {
            Directory.CreateDirectory(StormGbcaPaths.CacheDirectory);
            lock (_syncLock)
            {
                string json = JsonSerializer.Serialize(_roms, GbcaJsonContext.Default.ListGbcaRomRecord);
                File.WriteAllText(StormGbcaPaths.LibraryCachePath, json);
            }
        }
        catch
        {
        }
    }

    public static bool IsSupportedRomExtension(string filePath)
    {
        string ext = Path.GetExtension(filePath).ToLowerInvariant();
        return ext is ".gb" or ".gbc" or ".gba" or ".sgb" or ".zip" or ".7z";
    }

    public async Task ScanDirectoriesAsync(IEnumerable<string>? userFolders, CancellationToken ct = default)
    {
        var dirs = new List<string>();
        foreach (var d in StormGbcaPaths.DiscoverRomDirectories())
        {
            if (!dirs.Contains(d, StringComparer.OrdinalIgnoreCase)) dirs.Add(d);
        }
        if (userFolders != null)
        {
            foreach (var d in userFolders)
            {
                if (!string.IsNullOrWhiteSpace(d) && !dirs.Contains(d, StringComparer.OrdinalIgnoreCase))
                {
                    dirs.Add(d);
                }
            }
        }

        var discovered = new List<GbcaRomRecord>();
        await Task.Run(() =>
        {
            foreach (string dir in dirs)
            {
                if (ct.IsCancellationRequested) break;
                if (!Directory.Exists(dir)) continue;

                try
                {
                    var files = Directory.EnumerateFiles(dir, "*.*", SearchOption.AllDirectories)
                        .Where(IsSupportedRomExtension);

                    foreach (string file in files)
                    {
                        if (ct.IsCancellationRequested) break;
                        var rec = InspectRomFile(file);
                        if (rec != null)
                        {
                            discovered.Add(rec);
                        }
                    }
                }
                catch
                {
                }
            }
        }, ct).ConfigureAwait(false);

        lock (_syncLock)
        {
            var existingMap = _roms.ToDictionary(r => r.FilePath, StringComparer.OrdinalIgnoreCase);
            foreach (var rec in discovered)
            {
                if (existingMap.TryGetValue(rec.FilePath, out var prev))
                {
                    rec.IsFavorite = prev.IsFavorite;
                    rec.PlayCount = prev.PlayCount;
                    rec.LastPlayedUtc = prev.LastPlayedUtc;
                    if (string.IsNullOrWhiteSpace(rec.CoverLocalPath)) rec.CoverLocalPath = prev.CoverLocalPath;
                    if (string.IsNullOrWhiteSpace(rec.RaHash)) rec.RaHash = prev.RaHash;
                }
            }
            _roms.Clear();
            _roms.AddRange(discovered);
        }

        SaveCache();
        LibraryChanged?.Invoke();
    }

    public unsafe GbcaRomRecord? InspectRomFile(string filePath)
    {
        if (string.IsNullOrWhiteSpace(filePath) || !File.Exists(filePath)) return null;
        if (!NativeCoreBindings.IsAvailable) return null;

        StormGbcaRomInfo info = default;
        int rc = NativeCoreBindings.storm_gbca_inspect_rom_file(filePath, &info);
        if (rc != 0 || info.platform == (int)StormPlatform.None) return null;

        var rec = new GbcaRomRecord
        {
            FilePath = filePath,
            FileName = Path.GetFileName(filePath),
            Platform = (StormPlatform)info.platform,
            Title = info.GetTitle(),
            GameCode = info.GetGameCode(),
            Maker = info.GetMaker(),
            Region = info.GetRegion(),
            Crc32 = info.crc32,
            Md5 = info.GetMd5(),
            RomSize = info.rom_size,
            HardwareFlags = (StormHwFlags)info.hw_flags,
            ActiveCoreHint = info.GetActiveCore(),
        };

        // Check Russian localization markers in filename
        string upperName = rec.FileName.ToUpperInvariant();
        if (upperName.Contains("[RUS]") || upperName.Contains("[M. RUS]") || upperName.Contains("(RU)"))
        {
            rec.Region = "Russia";
        }

        // Check cached cover art immediately
        string cachedCover = GetExpectedCoverCachePath(rec);
        if (File.Exists(cachedCover) && new FileInfo(cachedCover).Length > 512)
        {
            rec.CoverLocalPath = cachedCover;
        }

        return rec;
    }

    public GbcaRomRecord? ImportSingleFile(string filePath)
    {
        var rec = InspectRomFile(filePath);
        if (rec == null) return null;

        bool added = false;
        lock (_syncLock)
        {
            var existing = _roms.FirstOrDefault(r => string.Equals(r.FilePath, filePath, StringComparison.OrdinalIgnoreCase));
            if (existing != null) return existing;
            _roms.Add(rec);
            added = true;
        }
        if (added)
        {
            SaveCache();
            LibraryChanged?.Invoke();
        }
        return rec;
    }

    public IReadOnlyList<GbcaRomRecord> Query(
        string searchQuery,
        GbcaPlatformFilter filter,
        GbcaSortMode sortMode)
    {
        IEnumerable<GbcaRomRecord> q;
        lock (_syncLock) q = _roms.ToList();

        q = filter switch
        {
            GbcaPlatformFilter.Gb => q.Where(r => r.Platform is StormPlatform.Gb or StormPlatform.Sgb),
            GbcaPlatformFilter.Gbc => q.Where(r => r.Platform == StormPlatform.Gbc),
            GbcaPlatformFilter.Gba => q.Where(r => r.Platform == StormPlatform.Gba),
            GbcaPlatformFilter.Favorites => q.Where(r => r.IsFavorite),
            _ => q,
        };

        if (!string.IsNullOrWhiteSpace(searchQuery))
        {
            string s = searchQuery.Trim();
            q = q.Where(r =>
                r.GetDisplayTitle().Contains(s, StringComparison.OrdinalIgnoreCase) ||
                r.Title.Contains(s, StringComparison.OrdinalIgnoreCase) ||
                r.GameCode.Contains(s, StringComparison.OrdinalIgnoreCase) ||
                r.Crc32Hex.Contains(s, StringComparison.OrdinalIgnoreCase));
        }

        q = sortMode switch
        {
            GbcaSortMode.RecentlyPlayed => q.OrderByDescending(r => r.LastPlayedUtc ?? DateTime.MinValue),
            GbcaSortMode.MostPlayed => q.OrderByDescending(r => r.PlayCount),
            GbcaSortMode.FileSize => q.OrderByDescending(r => r.RomSize),
            GbcaSortMode.Platform => q.OrderBy(r => r.Platform).ThenBy(r => r.GetDisplayTitle(), StringComparer.OrdinalIgnoreCase),
            _ => q.OrderBy(r => r.GetDisplayTitle(), StringComparer.OrdinalIgnoreCase),
        };

        return q.ToList();
    }

    public void SyncFavoritesAndRecents(GbcaAppSettings settings)
    {
        lock (_syncLock)
        {
            foreach (var r in _roms)
            {
                r.IsFavorite = settings.FavoriteRoms.Contains(r.FilePath, StringComparer.OrdinalIgnoreCase);
            }
        }
    }

    public void RecordPlay(GbcaRomRecord rom, GbcaAppSettings settings)
    {
        lock (_syncLock)
        {
            rom.PlayCount++;
            rom.LastPlayedUtc = DateTime.UtcNow;
        }
        settings.RecentRoms.RemoveAll(p => string.Equals(p, rom.FilePath, StringComparison.OrdinalIgnoreCase));
        settings.RecentRoms.Insert(0, rom.FilePath);
        if (settings.RecentRoms.Count > 40) settings.RecentRoms.RemoveAt(settings.RecentRoms.Count - 1);
        SaveCache();
    }

    public void ToggleFavorite(GbcaRomRecord rom, GbcaAppSettings settings)
    {
        lock (_syncLock)
        {
            rom.IsFavorite = !rom.IsFavorite;
            if (rom.IsFavorite)
            {
                if (!settings.FavoriteRoms.Contains(rom.FilePath, StringComparer.OrdinalIgnoreCase))
                {
                    settings.FavoriteRoms.Add(rom.FilePath);
                }
            }
            else
            {
                settings.FavoriteRoms.RemoveAll(p => string.Equals(p, rom.FilePath, StringComparison.OrdinalIgnoreCase));
            }
        }
        SaveCache();
        LibraryChanged?.Invoke();
    }

    public string GetExpectedCoverCachePath(GbcaRomRecord rom)
    {
        string key = !string.IsNullOrWhiteSpace(rom.Crc32Hex) && rom.Crc32 != 0
            ? $"{rom.PlatformShortName}_{rom.Crc32Hex}"
            : StormGbcaPaths.SanitizeFileKey($"{rom.PlatformShortName}_{rom.GetDisplayTitle()}");
        return Path.Combine(StormGbcaPaths.CoversDirectory, $"{key}.png");
    }

    /// <summary>
    /// Downloads all missing covers in parallel (up to 4 concurrent workers) using the cached Libretro directory index.
    /// </summary>
    public async Task PreloadAllMissingCoversAsync(CancellationToken ct = default)
    {
        List<GbcaRomRecord> pending;
        lock (_syncLock)
        {
            pending = _roms.Where(r => string.IsNullOrWhiteSpace(r.CoverLocalPath) || !File.Exists(r.CoverLocalPath)).ToList();
        }
        if (pending.Count == 0) return;

        using var sem = new SemaphoreSlim(4, 4);
        var tasks = pending.Select(async rom =>
        {
            await sem.WaitAsync(ct).ConfigureAwait(false);
            try
            {
                await EnsureCoverArtAsync(rom, ct).ConfigureAwait(false);
            }
            catch
            {
            }
            finally
            {
                sem.Release();
            }
        });
        await Task.WhenAll(tasks).ConfigureAwait(false);
    }

    /// <summary>
    /// Downloads and locally caches official boxart from Libretro Thumbnails repositories
    /// using full HTML directory index (30-day local cache) + O(1) normalized & fuzzy token matching.
    /// </summary>
    public async Task<string?> EnsureCoverArtAsync(GbcaRomRecord rom, CancellationToken ct = default)
    {
        string localPath = GetExpectedCoverCachePath(rom);
        if (File.Exists(localPath) && new FileInfo(localPath).Length > 512)
        {
            rom.CoverLocalPath = localPath;
            return localPath;
        }

        string repoSystem = rom.Platform switch
        {
            StormPlatform.Gb or StormPlatform.Sgb => "Nintendo - Game Boy",
            StormPlatform.Gbc => "Nintendo - Game Boy Color",
            _ => "Nintendo - Game Boy Advance",
        };

        Directory.CreateDirectory(StormGbcaPaths.CoversDirectory);

        string rawName = Path.GetFileNameWithoutExtension(rom.FileName);
        string baseClean = s_parensTagRegex.Replace(rawName, string.Empty).Trim();

        // 1. Try Libretro directory index fuzzy/exact match first (handles USA/Europe/Rev/Special chars reliably!)
        var indexEntries = await GetOrFetchDirectoryIndexAsync(repoSystem, ct).ConfigureAwait(false);
        string? matchedPng = FindBestIndexMatch(indexEntries, rawName, baseClean, rom.Title);
        if (!string.IsNullOrWhiteSpace(matchedPng))
        {
            string url = $"https://thumbnails.libretro.com/{Uri.EscapeDataString(repoSystem)}/Named_Boxarts/{Uri.EscapeDataString(matchedPng)}";
            if (await TryDownloadCoverAsync(url, localPath, ct).ConfigureAwait(false))
            {
                rom.CoverLocalPath = localPath;
                SaveCache();
                CoverArtUpdated?.Invoke(rom);
                return localPath;
            }
        }

        // 2. Direct candidate URL fallback
        var candidates = new List<string>();
        if (!string.IsNullOrWhiteSpace(rawName)) candidates.Add(rawName);
        if (!string.IsNullOrWhiteSpace(baseClean))
        {
            candidates.Add($"{baseClean} (USA)");
            candidates.Add($"{baseClean} (Europe)");
            candidates.Add($"{baseClean} (USA, Europe)");
            candidates.Add($"{baseClean} (Japan)");
            candidates.Add($"{baseClean} (World)");
            candidates.Add(baseClean);
        }

        foreach (string nameCandidate in candidates.Distinct(StringComparer.OrdinalIgnoreCase))
        {
            if (ct.IsCancellationRequested) break;
            string sanitized = SanitizeLibretroFileName(nameCandidate);
            string url = $"https://thumbnails.libretro.com/{Uri.EscapeDataString(repoSystem)}/Named_Boxarts/{Uri.EscapeDataString(sanitized)}.png";
            if (await TryDownloadCoverAsync(url, localPath, ct).ConfigureAwait(false))
            {
                rom.CoverLocalPath = localPath;
                SaveCache();
                CoverArtUpdated?.Invoke(rom);
                return localPath;
            }
        }

        return null;
    }

    private async Task<bool> TryDownloadCoverAsync(string url, string localPath, CancellationToken ct)
    {
        try
        {
            using var resp = await _httpClient.GetAsync(url, HttpCompletionOption.ResponseHeadersRead, ct).ConfigureAwait(false);
            if (!resp.IsSuccessStatusCode) return false;
            byte[] bytes = await resp.Content.ReadAsByteArrayAsync(ct).ConfigureAwait(false);
            if (bytes.Length <= 512) return false;
            await File.WriteAllBytesAsync(localPath, bytes, ct).ConfigureAwait(false);
            return true;
        }
        catch
        {
            return false;
        }
    }

    private async Task<List<string>> GetOrFetchDirectoryIndexAsync(string repoSystem, CancellationToken ct)
    {
        await _indexLock.WaitAsync(ct).ConfigureAwait(false);
        try
        {
            if (_indexCache.TryGetValue(repoSystem, out var cached)) return cached;

            string safeName = StormGbcaPaths.SanitizeFileKey(repoSystem);
            string cacheFile = Path.Combine(StormGbcaPaths.CacheDirectory, $"libretro_idx_{safeName}.txt");
            if (File.Exists(cacheFile) && (DateTime.UtcNow - File.GetLastWriteTimeUtc(cacheFile)).TotalDays < 30)
            {
                var lines = (await File.ReadAllLinesAsync(cacheFile, ct).ConfigureAwait(false))
                    .Where(l => l.EndsWith(".png", StringComparison.OrdinalIgnoreCase))
                    .ToList();
                if (lines.Count > 50)
                {
                    _indexCache[repoSystem] = lines;
                    return lines;
                }
            }

            string idxUrl = $"https://thumbnails.libretro.com/{Uri.EscapeDataString(repoSystem)}/Named_Boxarts/";
            try
            {
                string html = await _httpClient.GetStringAsync(idxUrl, ct).ConfigureAwait(false);
                var names = new List<string>(2048);
                foreach (Match m in s_hrefPngRegex.Matches(html))
                {
                    string decoded = Uri.UnescapeDataString(m.Groups[1].Value);
                    if (!decoded.Contains('/')) names.Add(decoded);
                }
                if (names.Count > 0)
                {
                    Directory.CreateDirectory(StormGbcaPaths.CacheDirectory);
                    await File.WriteAllLinesAsync(cacheFile, names, ct).ConfigureAwait(false);
                    _indexCache[repoSystem] = names;
                    return names;
                }
            }
            catch
            {
            }

            return [];
        }
        finally
        {
            _indexLock.Release();
        }
    }

    private static string? FindBestIndexMatch(List<string> index, string rawName, string baseClean, string internalTitle)
    {
        if (index.Count == 0) return null;

        string targetNorm = NormalizeTitle(baseClean);
        if (string.IsNullOrEmpty(targetNorm)) targetNorm = NormalizeTitle(rawName);
        if (string.IsNullOrEmpty(targetNorm)) targetNorm = NormalizeTitle(internalTitle);
        if (string.IsNullOrEmpty(targetNorm)) return null;

        string[] targetTokens = targetNorm.Split(' ', StringSplitOptions.RemoveEmptyEntries);
        if (targetTokens.Length == 0) return null;

        string? bestMatch = null;
        double bestScore = 0.0;

        foreach (string entry in index)
        {
            string stem = Path.GetFileNameWithoutExtension(entry);
            string stemBase = s_parensTagRegex.Replace(stem, string.Empty).Trim();
            string norm = NormalizeTitle(stemBase);
            if (norm == targetNorm)
            {
                double regBonus = stem.Contains("(USA", StringComparison.OrdinalIgnoreCase) ? 1.05
                    : (stem.Contains("(Europe", StringComparison.OrdinalIgnoreCase) ? 1.03 : 1.0);
                if (regBonus > bestScore)
                {
                    bestScore = regBonus;
                    bestMatch = entry;
                }
                continue;
            }

            string[] entryTokens = norm.Split(' ', StringSplitOptions.RemoveEmptyEntries);
            if (entryTokens.Length == 0) continue;

            int intersect = 0;
            foreach (string tt in targetTokens)
            {
                if (entryTokens.Contains(tt)) intersect++;
            }
            if (intersect == 0) continue;

            double union = targetTokens.Length + entryTokens.Length - intersect;
            double jaccard = intersect / union;
            double contain = (double)intersect / Math.Min(targetTokens.Length, entryTokens.Length);
            double score = jaccard * 0.65 + contain * 0.35;
            if (stem.Contains("(USA", StringComparison.OrdinalIgnoreCase)) score += 0.02;
            if (score > bestScore && score >= 0.62)
            {
                bestScore = score;
                bestMatch = entry;
            }
        }

        return bestMatch;
    }

    private static string NormalizeTitle(string input)
    {
        if (string.IsNullOrWhiteSpace(input)) return string.Empty;
        string s = s_parensTagRegex.Replace(input, " ").ToLowerInvariant();
        s = s.Replace("&", " and ").Replace("'", "").Replace("_", " ");
        s = s_nonAlnumRegex.Replace(s, " ").Trim();
        return s;
    }

    private static string SanitizeLibretroFileName(string name)
    {
        var sb = new StringBuilder(name.Length);
        foreach (char c in name)
        {
            sb.Append(c is '&' or '*' or '/' or ':' or '`' or '<' or '>' or '?' or '\\' or '|' ? '_' : c);
        }
        return sb.ToString().Trim();
    }
}
