// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Mozilla Public License, Version 2.0 (MPL-2.0).

using System.Globalization;
using System.IO.Compression;
using System.Net.Http.Headers;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Text.RegularExpressions;
using StormGbca.Core.Configuration;
using StormGbca.Core.Library;
using StormGbca.Core.Native;
using StormGbca.Core.Paths;

namespace StormGbca.Core.Subsystems;

public enum SgwUpdateKind
{
    VersionUpdate = 0,
    TranslationAvailable = 1,
}

public sealed class SgwCatalogGame
{
    public int GameId { get; set; }
    public int PlatformId { get; set; }
    public StormPlatform Platform { get; set; } = StormPlatform.Gba;
    public string PlatformName { get; set; } = "GBA";
    public string Title { get; set; } = string.Empty;
    public string OriginalTitle { get; set; } = string.Empty;
    public string Version { get; set; } = string.Empty;
    public string InternalBuild { get; set; } = string.Empty;
    public long FileSizeBytes { get; set; }
    public string SizeText { get; set; } = string.Empty;
    public bool FileExists { get; set; }
    public List<string> TextLangs { get; set; } = [];
    public List<string> Regions { get; set; } = [];
    public string CoverUrl { get; set; } = string.Empty;
    public string Description { get; set; } = string.Empty;
    public string Changelog { get; set; } = string.Empty;
    public string Fixes { get; set; } = string.Empty;
    public bool HasRetroAchievements { get; set; }
    public int Downloads { get; set; }
    public double AverageRating { get; set; }
    public string Md5 { get; set; } = string.Empty;
    public string Crc { get; set; } = string.Empty;
    public bool IsTranslationRelease { get; set; }
    public int TranslationReleaseId { get; set; }
    public string TranslationAuthor { get; set; } = string.Empty;
    public string TranslationFileName { get; set; } = string.Empty;
    public string TranslationFileUrl { get; set; } = string.Empty;
    public string DownloadUrl { get; set; } = string.Empty;
    public string BaseHost { get; set; } = SgwCatalogService.PrimaryHost;
    public bool IsRussianTranslation { get; set; }
    public string CreatedAt { get; set; } = string.Empty;

    [JsonIgnore]
    public string CleanVersion => SgwCatalogService.CleanVersionString(Version);

    [JsonIgnore]
    public string DisplaySize => SgwCatalogService.FormatSize(FileSizeBytes, SizeText);
}

public sealed record SgwGameUpdateInfo(
    GbcaRomRecord LocalRom,
    SgwCatalogGame RemoteGame,
    SgwUpdateKind Kind,
    string LocalVersion,
    string RemoteVersion,
    string Summary
);

public sealed class SgwInstalledMeta
{
    public string FilePath { get; set; } = string.Empty;
    public int GameId { get; set; }
    public int ReleaseId { get; set; }
    public string Version { get; set; } = string.Empty;
    public string InternalBuild { get; set; } = string.Empty;
    public string Md5 { get; set; } = string.Empty;
    public string Crc { get; set; } = string.Empty;
    public bool IsTranslation { get; set; }
    public DateTime InstalledAtUtc { get; set; } = DateTime.UtcNow;
}

public sealed record SgwDownloadProgress(
    long DownloadedBytes,
    long TotalBytes,
    double SpeedMbPerSec,
    string StatusText
);

public sealed record SgwDownloadResult(
    bool Success,
    GbcaRomRecord? NewRecord,
    string FinalFilePath,
    string? ErrorMessage = null
);

public sealed class SgwCatalogCacheFile
{
    public DateTime FetchedAtUtc { get; set; }
    public string BaseHost { get; set; } = SgwCatalogService.PrimaryHost;
    public List<SgwCatalogGame> Games { get; set; } = [];
}

[JsonSourceGenerationOptions(WriteIndented = true)]
[JsonSerializable(typeof(SgwCatalogCacheFile))]
[JsonSerializable(typeof(SgwCatalogGame))]
[JsonSerializable(typeof(List<SgwCatalogGame>))]
[JsonSerializable(typeof(SgwInstalledMeta))]
[JsonSerializable(typeof(Dictionary<string, SgwInstalledMeta>))]
internal partial class SgwCatalogJsonContext : JsonSerializerContext
{
}

/// <summary>
/// Fetches and caches the STORM GAMES WORLD catalog and /translations feed for GB, GBC, and GBA,
/// matches local ROMs for version/translation updates, and performs verified downloads with 100% save migration.
/// </summary>
public sealed class SgwCatalogService
{
    public const string PrimaryHost = "https://stormgamesworld.ru";
    public const string FallbackHost = "https://storm-games.world";
    public const int FallbackGbPlatformId = 190;
    public const int FallbackGbcPlatformId = 192;
    public const int FallbackGbaPlatformId = 191;

    private static readonly TimeSpan s_cacheTtl = TimeSpan.FromMinutes(15);
    private static readonly Regex s_bracketTagRegex = new(@"\s*[\(\[][^\)\]]*[\)\]]", RegexOptions.Compiled);
    private static readonly Regex s_versionTagRegex = new(@"(?:^|[\s._\-\(\[])v?(\d+(?:\.\d+){1,3})(?:$|[\s._\-\)\]])", RegexOptions.IgnoreCase | RegexOptions.Compiled);
    private static readonly Regex s_nonAlnumRegex = new(@"[^a-z0-9\u0400-\u04ff]+", RegexOptions.Compiled);

    private readonly HttpClient _http;
    private readonly SemaphoreSlim _fetchLock = new(1, 1);
    private readonly object _metaLock = new();
    private Dictionary<string, SgwInstalledMeta>? _installedCache;

    public static string CatalogCachePath => Path.Combine(StormGbcaPaths.CacheDirectory, "sgw_catalog.json");
    public static string InstalledMetaPath => Path.Combine(StormGbcaPaths.CacheDirectory, "sgw_installed.json");
    public static string CatalogCoversDirectory => Path.Combine(StormGbcaPaths.CacheDirectory, "sgw_covers");

    public SgwCatalogService()
    {
        _http = new HttpClient { Timeout = TimeSpan.FromSeconds(45) };
        _http.DefaultRequestHeaders.UserAgent.ParseAdd("STORM-GBCA/1.9.1 (Windows; Desktop)");
    }

    public async Task<IReadOnlyList<SgwCatalogGame>> GetCatalogAsync(bool forceRefresh = false, CancellationToken ct = default)
    {
        await _fetchLock.WaitAsync(ct).ConfigureAwait(false);
        try
        {
            if (!forceRefresh)
            {
                var cached = TryReadCatalogCache(allowExpired: false);
                if (cached != null && cached.Count > 0)
                {
                    return cached;
                }
            }

            foreach (string host in new[] { PrimaryHost, FallbackHost })
            {
                try
                {
                    var live = await FetchFromHostAsync(host, ct).ConfigureAwait(false);
                    if (live.Count > 0)
                    {
                        SaveCatalogCache(host, live);
                        return live;
                    }
                }
                catch (OperationCanceledException)
                {
                    throw;
                }
                catch
                {
                    // Try fallback mirror
                }
            }

            return TryReadCatalogCache(allowExpired: true) ?? [];
        }
        finally
        {
            _fetchLock.Release();
        }
    }

    private async Task<List<SgwCatalogGame>> FetchFromHostAsync(string host, CancellationToken ct)
    {
        var (gbId, gbcId, gbaId) = await ResolvePlatformIdsAsync(host, ct).ConfigureAwait(false);

        var gbTask = FetchGamesForPlatformAsync(host, gbId, StormPlatform.Gb, "GB", ct);
        var gbcTask = FetchGamesForPlatformAsync(host, gbcId, StormPlatform.Gbc, "GBC", ct);
        var gbaTask = FetchGamesForPlatformAsync(host, gbaId, StormPlatform.Gba, "GBA", ct);
        var transTask = FetchTranslationsOverviewAsync(host, gbId, gbcId, gbaId, ct);

        await Task.WhenAll(gbTask, gbcTask, gbaTask, transTask).ConfigureAwait(false);

        var merged = new Dictionary<int, SgwCatalogGame>();
        foreach (var g in (await gbTask.ConfigureAwait(false))
                     .Concat(await gbcTask.ConfigureAwait(false))
                     .Concat(await gbaTask.ConfigureAwait(false)))
        {
            if (g.GameId > 0)
            {
                merged[g.GameId] = g;
            }
        }

        // Prioritize active /translations releases so official STORM TEAM Russian builds always surface cleanly
        foreach (var tr in await transTask.ConfigureAwait(false))
        {
            if (tr.GameId <= 0) continue;

            if (merged.TryGetValue(tr.GameId, out var existing))
            {
                if (string.IsNullOrWhiteSpace(tr.Description)) tr.Description = existing.Description;
                if (string.IsNullOrWhiteSpace(tr.CoverUrl)) tr.CoverUrl = existing.CoverUrl;
                if (tr.AverageRating <= 0) tr.AverageRating = existing.AverageRating;
                if (tr.Downloads <= 0) tr.Downloads = existing.Downloads;
                if (string.IsNullOrWhiteSpace(tr.Md5)) tr.Md5 = existing.Md5;
                if (string.IsNullOrWhiteSpace(tr.Crc)) tr.Crc = existing.Crc;
                tr.HasRetroAchievements = tr.HasRetroAchievements || existing.HasRetroAchievements;
                if (tr.TextLangs.Count == 0 && existing.TextLangs.Count > 0) tr.TextLangs = existing.TextLangs;
                if (tr.Regions.Count == 0 && existing.Regions.Count > 0) tr.Regions = existing.Regions;
            }

            merged[tr.GameId] = tr;
        }

        return merged.Values
            .OrderByDescending(g => g.IsTranslationRelease)
            .ThenByDescending(g => g.Downloads)
            .ThenBy(g => g.Title, StringComparer.OrdinalIgnoreCase)
            .ToList();
    }

    private async Task<(int GbId, int GbcId, int GbaId)> ResolvePlatformIdsAsync(string host, CancellationToken ct)
    {
        int gbId = FallbackGbPlatformId;
        int gbcId = FallbackGbcPlatformId;
        int gbaId = FallbackGbaPlatformId;

        try
        {
            using var resp = await _http.GetAsync($"{host}/api/platforms?all=1", ct).ConfigureAwait(false);
            if (!resp.IsSuccessStatusCode) return (gbId, gbcId, gbaId);

            await using var stream = await resp.Content.ReadAsStreamAsync(ct).ConfigureAwait(false);
            using var doc = await JsonDocument.ParseAsync(stream, cancellationToken: ct).ConfigureAwait(false);

            JsonElement arr = UnwrapArray(doc.RootElement, "platforms", "data");
            if (arr.ValueKind != JsonValueKind.Array) return (gbId, gbcId, gbaId);

            foreach (var item in arr.EnumerateArray())
            {
                int id = GetInt(item, "id");
                string name = GetString(item, "name");
                if (id <= 0 || string.IsNullOrWhiteSpace(name)) continue;

                if (string.Equals(name, "Nintendo Game Boy", StringComparison.OrdinalIgnoreCase)) gbId = id;
                else if (string.Equals(name, "Nintendo Game Boy Color", StringComparison.OrdinalIgnoreCase)) gbcId = id;
                else if (string.Equals(name, "Nintendo Game Boy Advance", StringComparison.OrdinalIgnoreCase)) gbaId = id;
            }
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch
        {
            // Keep verified fallback IDs (190, 192, 191)
        }

        return (gbId, gbcId, gbaId);
    }

    private async Task<List<SgwCatalogGame>> FetchGamesForPlatformAsync(
        string host,
        int platformId,
        StormPlatform platform,
        string platformName,
        CancellationToken ct)
    {
        var list = new List<SgwCatalogGame>();
        try
        {
            using var resp = await _http.GetAsync($"{host}/api/games?platformId={platformId}", ct).ConfigureAwait(false);
            if (!resp.IsSuccessStatusCode) return list;

            await using var stream = await resp.Content.ReadAsStreamAsync(ct).ConfigureAwait(false);
            using var doc = await JsonDocument.ParseAsync(stream, cancellationToken: ct).ConfigureAwait(false);

            JsonElement arr = UnwrapArray(doc.RootElement, "data", "games");
            if (arr.ValueKind != JsonValueKind.Array) return list;

            foreach (var item in arr.EnumerateArray())
            {
                int id = GetInt(item, "id");
                if (id <= 0) continue;

                string rawTitle = GetString(item, "title");
                string displayTitle = FirstNonEmpty(
                    GetString(item, "correctTitle"),
                    GetString(item, "finalTitle"),
                    rawTitle,
                    GetString(item, "correctTitle2"));
                if (string.IsNullOrWhiteSpace(displayTitle)) continue;

                var langs = GetStringList(item, "textLangs");
                var regions = GetStringList(item, "regions");
                string coverRaw = GetString(item, "cover");
                string version = CleanVersionString(GetString(item, "version"));
                bool fileExists = GetBool(item, "fileExists", defaultValue: true);

                var game = new SgwCatalogGame
                {
                    GameId = id,
                    PlatformId = platformId,
                    Platform = platform,
                    PlatformName = platformName,
                    Title = displayTitle,
                    OriginalTitle = rawTitle,
                    Version = string.IsNullOrWhiteSpace(version) ? "1.0" : version,
                    FileSizeBytes = GetLong(item, "fileSizeBytes"),
                    SizeText = GetString(item, "size"),
                    FileExists = fileExists,
                    TextLangs = langs,
                    Regions = regions,
                    CoverUrl = ResolveHostUrl(host, coverRaw),
                    Description = GetString(item, "description"),
                    HasRetroAchievements = HasRaFlag(item),
                    Downloads = GetInt(item, "downloads"),
                    AverageRating = GetDouble(item, "averageRating"),
                    Md5 = GetString(item, "md5"),
                    Crc = GetString(item, "crc"),
                    IsTranslationRelease = false,
                    DownloadUrl = $"{host}/api/games/{id}/download",
                    BaseHost = host,
                    IsRussianTranslation = IsRussianCatalogMetadata(displayTitle, rawTitle, langs, regions)
                };
                list.Add(game);
            }
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch
        {
        }
        return list;
    }

    private async Task<List<SgwCatalogGame>> FetchTranslationsOverviewAsync(
        string host,
        int gbId,
        int gbcId,
        int gbaId,
        CancellationToken ct)
    {
        var list = new List<SgwCatalogGame>();
        try
        {
            using var resp = await _http.GetAsync($"{host}/api/translations?mode=overview", ct).ConfigureAwait(false);
            if (!resp.IsSuccessStatusCode) return list;

            await using var stream = await resp.Content.ReadAsStreamAsync(ct).ConfigureAwait(false);
            using var doc = await JsonDocument.ParseAsync(stream, cancellationToken: ct).ConfigureAwait(false);

            JsonElement arr = UnwrapArray(doc.RootElement, "translatedGames", "data");
            if (arr.ValueKind != JsonValueKind.Array) return list;

            foreach (var item in arr.EnumerateArray())
            {
                int platId = GetInt(item, "platformId");
                StormPlatform platform;
                string platformName;
                if (platId == gbId) { platform = StormPlatform.Gb; platformName = "GB"; }
                else if (platId == gbcId) { platform = StormPlatform.Gbc; platformName = "GBC"; }
                else if (platId == gbaId) { platform = StormPlatform.Gba; platformName = "GBA"; }
                else continue;

                int gameId = GetInt(item, "id");
                if (gameId <= 0) continue;

                if (!item.TryGetProperty("translationReleases", out var relArr) || relArr.ValueKind != JsonValueKind.Array)
                {
                    continue;
                }

                JsonElement chosenRel = default;
                bool hasRel = false;
                foreach (var rel in relArr.EnumerateArray())
                {
                    string fUrl = GetString(rel, "fileUrl");
                    if (string.IsNullOrWhiteSpace(fUrl)) continue;
                    if (!hasRel || GetBool(rel, "isCurrent", false))
                    {
                        chosenRel = rel;
                        hasRel = true;
                        if (GetBool(rel, "isCurrent", false)) break;
                    }
                }
                if (!hasRel) continue;

                string fileUrl = ResolveHostUrl(host, GetString(chosenRel, "fileUrl"));
                if (string.IsNullOrWhiteSpace(fileUrl)) continue;

                string rawTitle = GetString(item, "title");
                string displayTitle = FirstNonEmpty(
                    GetString(item, "correctTitle"),
                    GetString(item, "finalTitle"),
                    rawTitle,
                    GetString(chosenRel, "title"));

                string relVer = CleanVersionString(GetString(chosenRel, "version"));
                string internalBuild = GetString(chosenRel, "internalBuild");
                string changelog = GetString(chosenRel, "changelog");
                string fixes = GetString(chosenRel, "fixes");
                string fileName = GetString(chosenRel, "fileName");
                long fileSize = GetLong(chosenRel, "fileSize");

                var game = new SgwCatalogGame
                {
                    GameId = gameId,
                    PlatformId = platId,
                    Platform = platform,
                    PlatformName = platformName,
                    Title = displayTitle,
                    OriginalTitle = rawTitle,
                    Version = string.IsNullOrWhiteSpace(relVer) ? "1.0" : relVer,
                    InternalBuild = internalBuild,
                    FileSizeBytes = fileSize,
                    SizeText = fileSize > 0 ? FormatSize(fileSize, string.Empty) : string.Empty,
                    FileExists = true,
                    TextLangs = GetStringList(item, "textLangs"),
                    Regions = GetStringList(item, "regions"),
                    CoverUrl = ResolveHostUrl(host, GetString(item, "cover")),
                    Description = FirstNonEmpty(changelog, fixes),
                    Changelog = changelog,
                    Fixes = fixes,
                    HasRetroAchievements = HasRaFlag(item),
                    Downloads = GetInt(chosenRel, "downloads"),
                    Md5 = GetString(item, "md5"),
                    Crc = GetString(item, "crc"),
                    IsTranslationRelease = true,
                    TranslationReleaseId = GetInt(chosenRel, "id"),
                    TranslationAuthor = GetString(chosenRel, "author"),
                    TranslationFileName = fileName,
                    TranslationFileUrl = fileUrl,
                    DownloadUrl = fileUrl,
                    BaseHost = host,
                    IsRussianTranslation = true,
                    CreatedAt = GetString(chosenRel, "createdAt")
                };
                if (!game.TextLangs.Any(l => l.Contains("ru", StringComparison.OrdinalIgnoreCase)))
                {
                    game.TextLangs.Add("ru");
                }
                list.Add(game);
            }
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch
        {
        }
        return list;
    }

    public IReadOnlyDictionary<string, SgwGameUpdateInfo> FindUpdatesForLibrary(
        IReadOnlyList<GbcaRomRecord> library,
        IReadOnlyList<SgwCatalogGame> catalog)
    {
        var result = new Dictionary<string, SgwGameUpdateInfo>(StringComparer.OrdinalIgnoreCase);
        if (library.Count == 0 || catalog.Count == 0) return result;

        var metaMap = LoadInstalledMetaMap();

        foreach (var rom in library)
        {
            if (string.IsNullOrWhiteSpace(rom.FilePath)) continue;

            metaMap.TryGetValue(rom.FilePath, out var meta);
            if (meta == null && !string.IsNullOrWhiteSpace(rom.Md5))
            {
                meta = metaMap.Values.FirstOrDefault(m => !string.IsNullOrWhiteSpace(m.Md5) && string.Equals(m.Md5, rom.Md5, StringComparison.OrdinalIgnoreCase));
            }

            SgwCatalogGame? match = FindBestCatalogMatch(rom, meta, catalog);
            if (match == null || !match.FileExists || string.IsNullOrWhiteSpace(match.DownloadUrl)) continue;

            // Skip if exact cryptographic match with remote ROM
            if (IsExactRomHashMatch(rom, match)) continue;

            // Skip if tracked SGW metadata already matches the remote release
            if (meta != null)
            {
                bool sameReleaseId = !match.IsTranslationRelease || match.TranslationReleaseId <= 0 || meta.ReleaseId == match.TranslationReleaseId;
                bool sameVer = string.Equals(CleanVersionString(meta.Version), match.CleanVersion, StringComparison.OrdinalIgnoreCase);
                bool sameBuild = string.IsNullOrWhiteSpace(match.InternalBuild) || string.Equals(meta.InternalBuild, match.InternalBuild, StringComparison.OrdinalIgnoreCase);
                if (sameReleaseId && sameVer && sameBuild) continue;
            }

            bool localIsRus = IsLocalRussianRom(rom);

            if (!localIsRus && match.IsRussianTranslation)
            {
                string localVer = ExtractLocalVersion(rom, meta);
                string remoteVer = match.CleanVersion;
                result[rom.FilePath] = new SgwGameUpdateInfo(
                    LocalRom: rom,
                    RemoteGame: match,
                    Kind: SgwUpdateKind.TranslationAvailable,
                    LocalVersion: string.IsNullOrWhiteSpace(localVer) ? "ENG" : localVer,
                    RemoteVersion: string.IsNullOrWhiteSpace(remoteVer) ? "RUS" : remoteVer,
                    Summary: !string.IsNullOrWhiteSpace(match.TranslationAuthor)
                        ? $"{match.TranslationAuthor} ({remoteVer})"
                        : $"STORM TEAM ({remoteVer})"
                );
                continue;
            }

            string curVerStr = ExtractLocalVersion(rom, meta);
            string remVerStr = match.CleanVersion;

            bool hasNewerVersion = false;
            if (TryCompareVersions(remVerStr, curVerStr, out int cmp))
            {
                if (cmp > 0) hasNewerVersion = true;
                else if (cmp == 0 && meta != null &&
                         ((match.TranslationReleaseId > 0 && meta.ReleaseId > 0 && match.TranslationReleaseId > meta.ReleaseId) ||
                          (!string.IsNullOrWhiteSpace(match.InternalBuild) && !string.Equals(meta.InternalBuild, match.InternalBuild, StringComparison.OrdinalIgnoreCase))))
                {
                    hasNewerVersion = true;
                }
            }
            else if (meta != null && match.TranslationReleaseId > 0 && meta.ReleaseId > 0 && match.TranslationReleaseId > meta.ReleaseId)
            {
                hasNewerVersion = true;
            }

            if (hasNewerVersion)
            {
                result[rom.FilePath] = new SgwGameUpdateInfo(
                    LocalRom: rom,
                    RemoteGame: match,
                    Kind: SgwUpdateKind.VersionUpdate,
                    LocalVersion: string.IsNullOrWhiteSpace(curVerStr) ? "1.0" : curVerStr,
                    RemoteVersion: remVerStr,
                    Summary: !string.IsNullOrWhiteSpace(match.Changelog) ? match.Changelog : $"{curVerStr} -> {remVerStr}"
                );
            }
        }

        return result;
    }

    public bool IsCatalogGameInstalled(SgwCatalogGame game, IReadOnlyList<GbcaRomRecord> library)
    {
        var metaMap = LoadInstalledMetaMap();
        foreach (var rom in library)
        {
            if (IsExactRomHashMatch(rom, game)) return true;
            if (metaMap.TryGetValue(rom.FilePath, out var meta) && meta.GameId == game.GameId && game.GameId > 0)
            {
                return true;
            }
        }
        return library.Any(r => IsPlatformCompatible(r.Platform, game.Platform) && TitlesMatch(r, game));
    }

    public async Task<SgwDownloadResult> DownloadOrUpdateRomAsync(
        SgwCatalogGame game,
        GbcaAppSettings settings,
        GbcaRomLibrary library,
        GbcaRomRecord? replacingRom = null,
        IProgress<SgwDownloadProgress>? progress = null,
        CancellationToken ct = default)
    {
        if (string.IsNullOrWhiteSpace(game.DownloadUrl))
        {
            return new SgwDownloadResult(false, null, string.Empty, "Missing download URL");
        }

        if (game.IsTranslationRelease && game.TranslationReleaseId > 0)
        {
            _ = PingTranslationDownloadCountAsync(game.BaseHost, game.TranslationReleaseId);
        }

        string downloadsCacheDir = Path.Combine(StormGbcaPaths.CacheDirectory, "downloads");
        Directory.CreateDirectory(downloadsCacheDir);
        string tempRawPath = Path.Combine(downloadsCacheDir, $"{Guid.NewGuid():N}.tmp");
        string? stagedRomPath = null;

        try
        {
            string? headerFileName = null;
            bool downloaded = false;
            string? lastError = null;

            foreach (string url in BuildMirrorDownloadCandidates(game))
            {
                ct.ThrowIfCancellationRequested();
                try
                {
                    if (File.Exists(tempRawPath)) File.Delete(tempRawPath);
                    headerFileName = await StreamDownloadToFileAsync(url, tempRawPath, game.FileSizeBytes, progress, ct).ConfigureAwait(false);
                    downloaded = true;
                    break;
                }
                catch (OperationCanceledException)
                {
                    throw;
                }
                catch (Exception ex)
                {
                    lastError = ex.Message;
                }
            }

            if (!downloaded || !File.Exists(tempRawPath) || new FileInfo(tempRawPath).Length == 0)
            {
                return new SgwDownloadResult(false, null, string.Empty, lastError ?? "Download failed");
            }

            if (LooksLikeHtmlResponse(tempRawPath))
            {
                return new SgwDownloadResult(false, null, string.Empty, "Server returned HTML instead of ROM binary");
            }

            string targetDir = ResolveTargetDirectory(settings, replacingRom);
            Directory.CreateDirectory(targetDir);

            string preferredExt = DefaultExtensionForPlatform(game.Platform);
            string baseFileName = FirstNonEmpty(
                headerFileName,
                game.TranslationFileName,
                SanitizeFileName(game.Title) + preferredExt);

            string finalRomFileName;
            stagedRomPath = Path.Combine(targetDir, $".sgw_stage_{Guid.NewGuid():N}.tmp");

            if (IsZipArchiveMagic(tempRawPath))
            {
                progress?.Report(new SgwDownloadProgress(1, 1, 0, "Распаковка архива..."));
                if (!ExtractFirstRomEntrySafe(tempRawPath, stagedRomPath, targetDir, out string extractedEntryName))
                {
                    return new SgwDownloadResult(false, null, string.Empty, "Archive does not contain a valid GB/GBC/GBA ROM");
                }
                finalRomFileName = SanitizeFileName(extractedEntryName);
            }
            else
            {
                finalRomFileName = SanitizeFileName(baseFileName);
                string ext = Path.GetExtension(finalRomFileName).ToLowerInvariant();
                if (ext is not (".gb" or ".gbc" or ".gba" or ".sgb"))
                {
                    finalRomFileName = Path.GetFileNameWithoutExtension(finalRomFileName) + preferredExt;
                }
                File.Move(tempRawPath, stagedRomPath, overwrite: true);
            }

            string targetPath = Path.GetFullPath(Path.Combine(targetDir, finalRomFileName));
            string fullTargetDir = Path.GetFullPath(targetDir).TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar) + Path.DirectorySeparatorChar;
            if (!targetPath.StartsWith(fullTargetDir, StringComparison.OrdinalIgnoreCase))
            {
                return new SgwDownloadResult(false, null, string.Empty, "Unsafe destination filename");
            }

            if (replacingRom != null && string.Equals(targetPath, replacingRom.FilePath, StringComparison.OrdinalIgnoreCase))
            {
                File.Move(stagedRomPath, targetPath, overwrite: true);
                stagedRomPath = null;
            }
            else
            {
                File.Move(stagedRomPath, targetPath, overwrite: true);
                stagedRomPath = null;
            }

            if (!File.Exists(targetPath) || new FileInfo(targetPath).Length == 0)
            {
                return new SgwDownloadResult(false, null, string.Empty, "Final ROM verification failed");
            }

            GbcaRomRecord? newRecord;
            if (replacingRom != null)
            {
                progress?.Report(new SgwDownloadProgress(1, 1, 0, "Перенос сохранений..."));
                MigrateAllRomUserData(replacingRom, targetPath, settings, library);

                if (!string.Equals(replacingRom.FilePath, targetPath, StringComparison.OrdinalIgnoreCase) &&
                    File.Exists(replacingRom.FilePath))
                {
                    File.Delete(replacingRom.FilePath);
                }

                newRecord = library.ReplaceRomRecord(replacingRom.FilePath, targetPath);
            }
            else
            {
                newRecord = library.ImportSingleFile(targetPath);
            }

            if (newRecord == null)
            {
                return new SgwDownloadResult(false, null, targetPath, "Failed to inspect downloaded ROM");
            }

            RecordInstalledMeta(targetPath, newRecord, game, replacingRom?.FilePath);
            GbcaSettingsStore.Save(settings);

            return new SgwDownloadResult(true, newRecord, targetPath);
        }
        finally
        {
            try { if (File.Exists(tempRawPath)) File.Delete(tempRawPath); } catch { }
            try { if (!string.IsNullOrEmpty(stagedRomPath) && File.Exists(stagedRomPath)) File.Delete(stagedRomPath); } catch { }
        }
    }

    public async Task<string?> EnsureCatalogCoverCachedAsync(SgwCatalogGame game, CancellationToken ct = default)
    {
        if (game.GameId <= 0 || string.IsNullOrWhiteSpace(game.CoverUrl)) return null;

        Directory.CreateDirectory(CatalogCoversDirectory);
        string localPath = Path.Combine(CatalogCoversDirectory, $"sgw_{game.GameId}.img");
        if (File.Exists(localPath) && new FileInfo(localPath).Length > 256)
        {
            return localPath;
        }

        string tmpPath = localPath + ".tmp";
        try
        {
            using var resp = await _http.GetAsync(game.CoverUrl, HttpCompletionOption.ResponseHeadersRead, ct).ConfigureAwait(false);
            if (!resp.IsSuccessStatusCode) return null;

            byte[] bytes = await resp.Content.ReadAsByteArrayAsync(ct).ConfigureAwait(false);
            if (bytes.Length < 128) return null;
            if (bytes[0] == '<') return null;

            await File.WriteAllBytesAsync(tmpPath, bytes, ct).ConfigureAwait(false);
            File.Move(tmpPath, localPath, overwrite: true);
            return localPath;
        }
        catch
        {
            try { if (File.Exists(tmpPath)) File.Delete(tmpPath); } catch { }
            return null;
        }
    }

    public static string ResolveTargetDirectory(GbcaAppSettings settings, GbcaRomRecord? replacingRom = null)
    {
        if (replacingRom != null && !string.IsNullOrWhiteSpace(replacingRom.FilePath))
        {
            string? parent = Path.GetDirectoryName(replacingRom.FilePath);
            if (!string.IsNullOrWhiteSpace(parent)) return parent;
        }

        string? configured = settings.RomDirectories.FirstOrDefault(d => !string.IsNullOrWhiteSpace(d) && Directory.Exists(d));
        return !string.IsNullOrWhiteSpace(configured) ? configured : StormGbcaPaths.RomsDirectory;
    }

    private static void MigrateAllRomUserData(
        GbcaRomRecord oldRom,
        string newRomPath,
        GbcaAppSettings settings,
        GbcaRomLibrary library)
    {
        StormGbcaPaths.EnsureDirectories();

        string oldKey = !string.IsNullOrWhiteSpace(oldRom.FileName) ? oldRom.FileName : oldRom.GameCode;
        string newKey = Path.GetFileName(newRomPath);

        if (!string.IsNullOrWhiteSpace(oldKey) && !string.IsNullOrWhiteSpace(newKey) &&
            !string.Equals(oldKey, newKey, StringComparison.OrdinalIgnoreCase))
        {
            MoveOrCopyIfExists(
                StormGbcaPaths.GetSaveFilePath(oldKey),
                StormGbcaPaths.GetSaveFilePath(newKey));

            for (int slot = 0; slot < 10; slot++)
            {
                string oldState = StormGbcaPaths.GetSaveStateFilePath(oldKey, slot);
                string newState = StormGbcaPaths.GetSaveStateFilePath(newKey, slot);
                MoveOrCopyIfExists(oldState, newState);

                MoveOrCopyIfExists(
                    StormGbcaPaths.GetSaveStateThumbnailPath(oldKey, slot),
                    StormGbcaPaths.GetSaveStateThumbnailPath(newKey, slot));

                MoveOrCopyIfExists(
                    oldState + ".raprog",
                    newState + ".raprog");
            }

            MoveOrCopyIfExists(
                StormGbcaPaths.GetCheatsFilePath(oldKey),
                StormGbcaPaths.GetCheatsFilePath(newKey));
        }

        ReplacePathInList(settings.FavoriteRoms, oldRom.FilePath, newRomPath);
        ReplacePathInList(settings.RecentRoms, oldRom.FilePath, newRomPath);

        if (!string.IsNullOrWhiteSpace(oldRom.CoverLocalPath) && File.Exists(oldRom.CoverLocalPath))
        {
            var tempNewRec = library.InspectRomFile(newRomPath);
            if (tempNewRec != null)
            {
                string newCoverPath = library.GetExpectedCoverCachePath(tempNewRec);
                if (!string.Equals(oldRom.CoverLocalPath, newCoverPath, StringComparison.OrdinalIgnoreCase) &&
                    !File.Exists(newCoverPath))
                {
                    try
                    {
                        Directory.CreateDirectory(StormGbcaPaths.CoversDirectory);
                        File.Copy(oldRom.CoverLocalPath, newCoverPath, overwrite: false);
                    }
                    catch { }
                }
            }
        }
    }

    private static void MoveOrCopyIfExists(string src, string dst)
    {
        if (string.IsNullOrWhiteSpace(src) || string.IsNullOrWhiteSpace(dst)) return;
        if (string.Equals(src, dst, StringComparison.OrdinalIgnoreCase)) return;
        if (!File.Exists(src)) return;

        string? dir = Path.GetDirectoryName(dst);
        if (!string.IsNullOrWhiteSpace(dir)) Directory.CreateDirectory(dir);
        File.Copy(src, dst, overwrite: true);
        try { File.Delete(src); } catch { }
    }

    private static void ReplacePathInList(List<string> list, string oldPath, string newPath)
    {
        bool found = false;
        for (int i = 0; i < list.Count; i++)
        {
            if (string.Equals(list[i], oldPath, StringComparison.OrdinalIgnoreCase))
            {
                list[i] = newPath;
                found = true;
            }
        }
        if (found)
        {
            var dedup = list.Distinct(StringComparer.OrdinalIgnoreCase).ToList();
            list.Clear();
            list.AddRange(dedup);
        }
    }

    private async Task<string?> StreamDownloadToFileAsync(
        string url,
        string destPath,
        long fallbackTotalBytes,
        IProgress<SgwDownloadProgress>? progress,
        CancellationToken ct)
    {
        using var resp = await _http.GetAsync(url, HttpCompletionOption.ResponseHeadersRead, ct).ConfigureAwait(false);
        resp.EnsureSuccessStatusCode();

        if (resp.Content.Headers.ContentType?.MediaType?.Contains("html", StringComparison.OrdinalIgnoreCase) == true)
        {
            throw new InvalidOperationException("Unexpected HTML response");
        }

        string? fileName = ExtractResponseFileName(resp);
        long totalBytes = resp.Content.Headers.ContentLength ?? fallbackTotalBytes;

        await using var inStream = await resp.Content.ReadAsStreamAsync(ct).ConfigureAwait(false);
        await using var outStream = new FileStream(destPath, FileMode.Create, FileAccess.Write, FileShare.None, 81920, true);

        byte[] buffer = new byte[81920];
        long totalRead = 0;
        long lastSampleBytes = 0;
        long lastTicks = Environment.TickCount64;
        double emaSpeed = 0.0;

        progress?.Report(new SgwDownloadProgress(0, totalBytes, 0.0, "Загрузка..."));

        int read;
        while ((read = await inStream.ReadAsync(buffer, ct).ConfigureAwait(false)) > 0)
        {
            await outStream.WriteAsync(buffer.AsMemory(0, read), ct).ConfigureAwait(false);
            totalRead += read;

            long nowTicks = Environment.TickCount64;
            long dtMs = nowTicks - lastTicks;
            if (dtMs >= 220 || (totalBytes > 0 && totalRead >= totalBytes))
            {
                if (dtMs > 0)
                {
                    double instant = ((totalRead - lastSampleBytes) / (1024.0 * 1024.0)) / (dtMs / 1000.0);
                    emaSpeed = emaSpeed <= 0.01 ? instant : (emaSpeed * 0.65 + instant * 0.35);
                    lastTicks = nowTicks;
                    lastSampleBytes = totalRead;
                }
                progress?.Report(new SgwDownloadProgress(totalRead, totalBytes, emaSpeed, "Загрузка..."));
            }
        }

        await outStream.FlushAsync(ct).ConfigureAwait(false);
        return fileName;
    }

    private async Task PingTranslationDownloadCountAsync(string baseHost, int releaseId)
    {
        try
        {
            string host = string.IsNullOrWhiteSpace(baseHost) ? PrimaryHost : baseHost;
            using var req = new HttpRequestMessage(HttpMethod.Get, $"{host}/api/translations/releases?action=download&id={releaseId}");
            using var resp = await _http.SendAsync(req, HttpCompletionOption.ResponseHeadersRead).ConfigureAwait(false);
        }
        catch
        {
        }
    }

    private static IEnumerable<string> BuildMirrorDownloadCandidates(SgwCatalogGame game)
    {
        yield return game.DownloadUrl;
        if (game.DownloadUrl.StartsWith(PrimaryHost, StringComparison.OrdinalIgnoreCase))
        {
            yield return FallbackHost + game.DownloadUrl[PrimaryHost.Length..];
        }
        else if (game.DownloadUrl.StartsWith(FallbackHost, StringComparison.OrdinalIgnoreCase))
        {
            yield return PrimaryHost + game.DownloadUrl[FallbackHost.Length..];
        }
    }

    private static string? ExtractResponseFileName(HttpResponseMessage resp)
    {
        if (resp.Headers.TryGetValues("X-Filename", out var xNames))
        {
            string? raw = xNames.FirstOrDefault();
            if (!string.IsNullOrWhiteSpace(raw))
            {
                return Path.GetFileName(Uri.UnescapeDataString(raw.Trim()));
            }
        }

        ContentDispositionHeaderValue? cd = resp.Content.Headers.ContentDisposition;
        string? cdName = cd?.FileNameStar ?? cd?.FileName;
        if (!string.IsNullOrWhiteSpace(cdName))
        {
            return Path.GetFileName(Uri.UnescapeDataString(cdName.Trim().Trim('"')));
        }
        return null;
    }

    private static bool LooksLikeHtmlResponse(string filePath)
    {
        try
        {
            using var fs = File.OpenRead(filePath);
            byte[] buf = new byte[Math.Min(512, fs.Length)];
            int read = fs.Read(buf, 0, buf.Length);
            if (read == 0) return true;
            string head = Encoding.UTF8.GetString(buf, 0, read).TrimStart();
            return head.StartsWith("<!doctype", StringComparison.OrdinalIgnoreCase)
                || head.StartsWith("<html", StringComparison.OrdinalIgnoreCase)
                || head.StartsWith("<head", StringComparison.OrdinalIgnoreCase);
        }
        catch
        {
            return true;
        }
    }

    private static bool IsZipArchiveMagic(string filePath)
    {
        try
        {
            using var fs = File.OpenRead(filePath);
            byte[] h = new byte[4];
            return fs.Read(h, 0, 4) == 4 && h[0] == 0x50 && h[1] == 0x4B && h[2] == 0x03 && h[3] == 0x04;
        }
        catch
        {
            return false;
        }
    }

    private static bool ExtractFirstRomEntrySafe(string zipPath, string stagedOutputPath, string targetDir, out string entryFileName)
    {
        entryFileName = string.Empty;
        const long maxRomBytes = 128L * 1024 * 1024;
        string fullTargetDir = Path.GetFullPath(targetDir).TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar) + Path.DirectorySeparatorChar;

        using var archive = ZipFile.OpenRead(zipPath);
        ZipArchiveEntry? match = null;
        foreach (var entry in archive.Entries)
        {
            if (string.IsNullOrWhiteSpace(entry.Name) || entry.Length <= 0 || entry.Length > maxRomBytes) continue;
            string ext = Path.GetExtension(entry.Name).ToLowerInvariant();
            if (ext is ".gba" or ".gbc" or ".gb" or ".sgb")
            {
                match = entry;
                break;
            }
        }

        if (match == null) return false;

        string leaf = Path.GetFileName(match.FullName.Replace('\\', '/'));
        if (string.IsNullOrWhiteSpace(leaf) || leaf.Contains("..", StringComparison.Ordinal))
        {
            return false;
        }

        string candidateDest = Path.GetFullPath(Path.Combine(targetDir, leaf));
        if (!candidateDest.StartsWith(fullTargetDir, StringComparison.OrdinalIgnoreCase))
        {
            return false;
        }

        using var inStream = match.Open();
        using var outStream = new FileStream(stagedOutputPath, FileMode.Create, FileAccess.Write, FileShare.None, 81920);
        byte[] buf = new byte[81920];
        long total = 0;
        int read;
        while ((read = inStream.Read(buf, 0, buf.Length)) > 0)
        {
            total += read;
            if (total > maxRomBytes) return false;
            outStream.Write(buf, 0, read);
        }

        entryFileName = leaf;
        return total > 0;
    }

    private SgwCatalogGame? FindBestCatalogMatch(
        GbcaRomRecord rom,
        SgwInstalledMeta? meta,
        IReadOnlyList<SgwCatalogGame> catalog)
    {
        if (meta != null && meta.GameId > 0)
        {
            var byId = catalog.FirstOrDefault(g => g.GameId == meta.GameId && IsPlatformCompatible(rom.Platform, g.Platform));
            if (byId != null) return byId;
        }

        foreach (var g in catalog)
        {
            if (IsPlatformCompatible(rom.Platform, g.Platform) && IsExactRomHashMatch(rom, g))
            {
                return g;
            }
        }

        string localNorm = NormalizeTitle(rom.GetCleanTitleBeforeYear());
        string localInternalNorm = NormalizeTitle(rom.Title);
        string localRegion = NormalizeRegionTag(rom.Region);

        SgwCatalogGame? best = null;
        int bestScore = 0;

        foreach (var g in catalog)
        {
            if (!IsPlatformCompatible(rom.Platform, g.Platform)) continue;

            string[] candTitles = [g.Title, g.OriginalTitle];
            bool titleMatched = false;
            int exactBonus = 0;
            foreach (string cand in candTitles)
            {
                string gNorm = NormalizeTitle(cand);
                if (string.IsNullOrEmpty(gNorm)) continue;

                if (gNorm == localNorm || (!string.IsNullOrEmpty(localInternalNorm) && gNorm == localInternalNorm))
                {
                    titleMatched = true;
                    exactBonus = 100;
                    break;
                }
            }

            if (!titleMatched) continue;

            int score = exactBonus;
            if (g.IsTranslationRelease || g.IsRussianTranslation) score += 25;
            if (!string.IsNullOrEmpty(localRegion) && g.Regions.Any(r => NormalizeRegionTag(r) == localRegion)) score += 15;
            if (g.FileExists) score += 5;

            if (score > bestScore)
            {
                bestScore = score;
                best = g;
            }
        }

        return best;
    }

    private static bool TitlesMatch(GbcaRomRecord rom, SgwCatalogGame game)
    {
        string a = NormalizeTitle(rom.GetCleanTitleBeforeYear());
        if (string.IsNullOrEmpty(a)) return false;
        return NormalizeTitle(game.Title) == a || NormalizeTitle(game.OriginalTitle) == a;
    }

    private static bool IsPlatformCompatible(StormPlatform local, StormPlatform remote)
    {
        if (local == remote) return true;
        return local == StormPlatform.Sgb && remote == StormPlatform.Gb;
    }

    private static bool IsExactRomHashMatch(GbcaRomRecord rom, SgwCatalogGame game)
    {
        if (!string.IsNullOrWhiteSpace(rom.Md5) && !string.IsNullOrWhiteSpace(game.Md5) &&
            string.Equals(rom.Md5.Trim(), game.Md5.Trim(), StringComparison.OrdinalIgnoreCase))
        {
            return true;
        }
        if (rom.Crc32 != 0 && !string.IsNullOrWhiteSpace(game.Crc))
        {
            string normRemoteCrc = game.Crc.Trim().TrimStart('0', 'x', 'X').PadLeft(8, '0');
            if (normRemoteCrc.Length == 8 && string.Equals(rom.Crc32Hex, normRemoteCrc, StringComparison.OrdinalIgnoreCase))
            {
                return true;
            }
        }
        return false;
    }

    private static bool IsLocalRussianRom(GbcaRomRecord rom)
    {
        if (string.Equals(rom.Region, "Russia", StringComparison.OrdinalIgnoreCase)) return true;
        string upper = (rom.FileName ?? string.Empty).ToUpperInvariant();
        return upper.Contains("[RUS]") || upper.Contains("[M. RUS]") || upper.Contains("(RU)") || upper.Contains("[RU]") || upper.Contains("(RUS)");
    }

    private static bool IsRussianCatalogMetadata(string title, string origTitle, List<string> langs, List<string> regions)
    {
        if (langs.Any(l => l.Equals("ru", StringComparison.OrdinalIgnoreCase) || l.Equals("rus", StringComparison.OrdinalIgnoreCase) || l.Contains("рус", StringComparison.OrdinalIgnoreCase)))
            return true;
        if (regions.Any(r => r.Equals("RU", StringComparison.OrdinalIgnoreCase) || r.Equals("RUS", StringComparison.OrdinalIgnoreCase) || r.Equals("Russia", StringComparison.OrdinalIgnoreCase)))
            return true;
        string combined = $"{title} {origTitle}".ToUpperInvariant();
        return combined.Contains("[RUS]") || combined.Contains("[M. RUS]") || combined.Contains("(RU)");
    }

    private static string ExtractLocalVersion(GbcaRomRecord rom, SgwInstalledMeta? meta)
    {
        if (meta != null && !string.IsNullOrWhiteSpace(meta.Version))
        {
            return CleanVersionString(meta.Version);
        }
        var m = s_versionTagRegex.Match(rom.FileName ?? string.Empty);
        return m.Success ? CleanVersionString(m.Groups[1].Value) : string.Empty;
    }

    private static bool TryCompareVersions(string? remoteRaw, string? localRaw, out int comparison)
    {
        comparison = 0;
        string rClean = CleanVersionString(remoteRaw);
        string lClean = CleanVersionString(localRaw);
        if (string.IsNullOrWhiteSpace(rClean) || string.IsNullOrWhiteSpace(lClean)) return false;

        if (Version.TryParse(PadTwoPartVersion(rClean), out var rVer) &&
            Version.TryParse(PadTwoPartVersion(lClean), out var lVer))
        {
            comparison = rVer.CompareTo(lVer);
            return true;
        }
        return false;
    }

    private static string PadTwoPartVersion(string v)
    {
        int dots = v.Count(c => c == '.');
        return dots == 0 ? v + ".0" : v;
    }

    public static string CleanVersionString(string? raw)
    {
        if (string.IsNullOrWhiteSpace(raw)) return string.Empty;
        string s = raw.Trim().TrimStart('v', 'V').Trim();
        int dash = s.IndexOf('-');
        if (dash > 0) s = s[..dash];
        return s;
    }

    private static string NormalizeTitle(string? raw)
    {
        if (string.IsNullOrWhiteSpace(raw)) return string.Empty;
        string stripped = s_bracketTagRegex.Replace(raw, " ");
        stripped = s_versionTagRegex.Replace(stripped, " ");
        string norm = stripped.Normalize(NormalizationForm.FormD);
        var sb = new StringBuilder(norm.Length);
        foreach (char c in norm)
        {
            if (CharUnicodeInfo.GetUnicodeCategory(c) != UnicodeCategory.NonSpacingMark)
                sb.Append(c);
        }
        string s = sb.ToString().ToLowerInvariant().Replace("&", " and ");
        s = s_nonAlnumRegex.Replace(s, " ").Trim();
        return s;
    }

    private static string NormalizeRegionTag(string? raw)
    {
        if (string.IsNullOrWhiteSpace(raw)) return string.Empty;
        string u = raw.Trim().ToUpperInvariant();
        if (u is "USA" or "US" or "U") return "US";
        if (u is "EUROPE" or "EU" or "EUR" or "E") return "EU";
        if (u is "JAPAN" or "JP" or "JPN" or "J") return "JP";
        if (u is "RUSSIA" or "RU" or "RUS" or "R") return "RU";
        return u;
    }

    private static string DefaultExtensionForPlatform(StormPlatform platform) => platform switch
    {
        StormPlatform.Gb => ".gb",
        StormPlatform.Gbc => ".gbc",
        StormPlatform.Sgb => ".sgb",
        _ => ".gba",
    };

    private static string SanitizeFileName(string raw)
    {
        string leaf = Path.GetFileName((raw ?? "game").Trim());
        if (string.IsNullOrWhiteSpace(leaf)) leaf = "game";
        char[] invalid = Path.GetInvalidFileNameChars();
        var chars = leaf.ToCharArray();
        for (int i = 0; i < chars.Length; i++)
        {
            if (Array.IndexOf(invalid, chars[i]) >= 0) chars[i] = '_';
        }
        return new string(chars);
    }

    public static string FormatSize(long bytes, string fallback)
    {
        if (bytes > 0)
        {
            if (bytes >= 1024 * 1024) return string.Format(CultureInfo.InvariantCulture, "{0:F1} MB", bytes / (1024.0 * 1024.0));
            return string.Format(CultureInfo.InvariantCulture, "{0:F0} KB", Math.Max(1.0, bytes / 1024.0));
        }
        return string.IsNullOrWhiteSpace(fallback) ? "-" : fallback;
    }

    private List<SgwCatalogGame>? TryReadCatalogCache(bool allowExpired)
    {
        try
        {
            if (!File.Exists(CatalogCachePath)) return null;
            string json = File.ReadAllText(CatalogCachePath);
            var payload = JsonSerializer.Deserialize(json, SgwCatalogJsonContext.Default.SgwCatalogCacheFile);
            if (payload?.Games == null || payload.Games.Count == 0) return null;
            if (!allowExpired && (DateTime.UtcNow - payload.FetchedAtUtc) > s_cacheTtl) return null;
            return payload.Games;
        }
        catch
        {
            return null;
        }
    }

    private static void SaveCatalogCache(string host, List<SgwCatalogGame> games)
    {
        try
        {
            Directory.CreateDirectory(StormGbcaPaths.CacheDirectory);
            var payload = new SgwCatalogCacheFile
            {
                FetchedAtUtc = DateTime.UtcNow,
                BaseHost = host,
                Games = games
            };
            string json = JsonSerializer.Serialize(payload, SgwCatalogJsonContext.Default.SgwCatalogCacheFile);
            File.WriteAllText(CatalogCachePath, json);
        }
        catch
        {
        }
    }

    private Dictionary<string, SgwInstalledMeta> LoadInstalledMetaMap()
    {
        lock (_metaLock)
        {
            if (_installedCache != null) return new Dictionary<string, SgwInstalledMeta>(_installedCache, StringComparer.OrdinalIgnoreCase);

            var map = new Dictionary<string, SgwInstalledMeta>(StringComparer.OrdinalIgnoreCase);
            try
            {
                if (File.Exists(InstalledMetaPath))
                {
                    string json = File.ReadAllText(InstalledMetaPath);
                    var loaded = JsonSerializer.Deserialize(json, SgwCatalogJsonContext.Default.DictionaryStringSgwInstalledMeta);
                    if (loaded != null)
                    {
                        foreach (var kv in loaded)
                        {
                            map[kv.Key] = kv.Value;
                        }
                    }
                }
            }
            catch
            {
            }

            _installedCache = map;
            return new Dictionary<string, SgwInstalledMeta>(map, StringComparer.OrdinalIgnoreCase);
        }
    }

    private void RecordInstalledMeta(string newFilePath, GbcaRomRecord newRecord, SgwCatalogGame game, string? oldFilePath)
    {
        lock (_metaLock)
        {
            var map = LoadInstalledMetaMap();
            if (!string.IsNullOrWhiteSpace(oldFilePath) && !string.Equals(oldFilePath, newFilePath, StringComparison.OrdinalIgnoreCase))
            {
                map.Remove(oldFilePath);
            }

            map[newFilePath] = new SgwInstalledMeta
            {
                FilePath = newFilePath,
                GameId = game.GameId,
                ReleaseId = game.TranslationReleaseId,
                Version = game.CleanVersion,
                InternalBuild = game.InternalBuild,
                Md5 = newRecord.Md5,
                Crc = newRecord.Crc32Hex,
                IsTranslation = game.IsTranslationRelease,
                InstalledAtUtc = DateTime.UtcNow
            };
            _installedCache = map;

            try
            {
                Directory.CreateDirectory(StormGbcaPaths.CacheDirectory);
                string json = JsonSerializer.Serialize(map, SgwCatalogJsonContext.Default.DictionaryStringSgwInstalledMeta);
                File.WriteAllText(InstalledMetaPath, json);
            }
            catch
            {
            }
        }
    }

    private static JsonElement UnwrapArray(JsonElement root, params string[] wrapperKeys)
    {
        if (root.ValueKind == JsonValueKind.Array) return root;
        if (root.ValueKind == JsonValueKind.Object)
        {
            foreach (string k in wrapperKeys)
            {
                if (root.TryGetProperty(k, out var sub) && sub.ValueKind == JsonValueKind.Array) return sub;
            }
        }
        return default;
    }

    private static string ResolveHostUrl(string host, string? raw)
    {
        if (string.IsNullOrWhiteSpace(raw)) return string.Empty;
        string s = raw.Trim();
        if (s.StartsWith("http://", StringComparison.OrdinalIgnoreCase) ||
            s.StartsWith("https://", StringComparison.OrdinalIgnoreCase))
        {
            return s;
        }
        return s.StartsWith('/') ? host + s : $"{host}/{s}";
    }

    private static string FirstNonEmpty(params string?[] vals)
    {
        foreach (var v in vals)
        {
            if (!string.IsNullOrWhiteSpace(v)) return v.Trim();
        }
        return string.Empty;
    }

    private static string GetString(JsonElement obj, string prop)
    {
        if (obj.ValueKind != JsonValueKind.Object || !obj.TryGetProperty(prop, out var el)) return string.Empty;
        return el.ValueKind switch
        {
            JsonValueKind.String => el.GetString() ?? string.Empty,
            JsonValueKind.Number => el.GetRawText(),
            _ => string.Empty
        };
    }

    private static int GetInt(JsonElement obj, string prop)
    {
        if (obj.ValueKind != JsonValueKind.Object || !obj.TryGetProperty(prop, out var el)) return 0;
        if (el.ValueKind == JsonValueKind.Number && el.TryGetInt32(out int v)) return v;
        if (el.ValueKind == JsonValueKind.String && int.TryParse(el.GetString(), NumberStyles.Integer, CultureInfo.InvariantCulture, out int sv)) return sv;
        return 0;
    }

    private static long GetLong(JsonElement obj, string prop)
    {
        if (obj.ValueKind != JsonValueKind.Object || !obj.TryGetProperty(prop, out var el)) return 0;
        if (el.ValueKind == JsonValueKind.Number && el.TryGetInt64(out long v)) return v;
        if (el.ValueKind == JsonValueKind.String && long.TryParse(el.GetString(), NumberStyles.Integer, CultureInfo.InvariantCulture, out long sv)) return sv;
        return 0;
    }

    private static double GetDouble(JsonElement obj, string prop)
    {
        if (obj.ValueKind != JsonValueKind.Object || !obj.TryGetProperty(prop, out var el)) return 0;
        if (el.ValueKind == JsonValueKind.Number && el.TryGetDouble(out double v)) return v;
        if (el.ValueKind == JsonValueKind.String && double.TryParse(el.GetString(), NumberStyles.Float, CultureInfo.InvariantCulture, out double sv)) return sv;
        return 0;
    }

    private static bool GetBool(JsonElement obj, string prop, bool defaultValue)
    {
        if (obj.ValueKind != JsonValueKind.Object || !obj.TryGetProperty(prop, out var el)) return defaultValue;
        return el.ValueKind switch
        {
            JsonValueKind.True => true,
            JsonValueKind.False => false,
            JsonValueKind.Number => el.TryGetInt32(out int iv) && iv != 0,
            JsonValueKind.String => string.Equals(el.GetString(), "true", StringComparison.OrdinalIgnoreCase) || el.GetString() == "1",
            _ => defaultValue
        };
    }

    private static bool HasRaFlag(JsonElement obj)
    {
        if (obj.ValueKind != JsonValueKind.Object || !obj.TryGetProperty("retroachievements", out var el)) return false;
        return el.ValueKind switch
        {
            JsonValueKind.True => true,
            JsonValueKind.Number => el.TryGetInt32(out int iv) && iv > 0,
            JsonValueKind.String => !string.IsNullOrWhiteSpace(el.GetString()) && el.GetString() != "0" && !string.Equals(el.GetString(), "false", StringComparison.OrdinalIgnoreCase),
            JsonValueKind.Object => true,
            JsonValueKind.Array => el.GetArrayLength() > 0,
            _ => false
        };
    }

    private static List<string> GetStringList(JsonElement obj, string prop)
    {
        var list = new List<string>();
        if (obj.ValueKind != JsonValueKind.Object || !obj.TryGetProperty(prop, out var el)) return list;
        if (el.ValueKind == JsonValueKind.Array)
        {
            foreach (var item in el.EnumerateArray())
            {
                string? s = item.ValueKind == JsonValueKind.String ? item.GetString() : item.GetRawText();
                if (!string.IsNullOrWhiteSpace(s)) list.Add(s.Trim());
            }
        }
        else if (el.ValueKind == JsonValueKind.String)
        {
            string? raw = el.GetString();
            if (!string.IsNullOrWhiteSpace(raw))
            {
                foreach (var part in raw.Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries))
                {
                    if (!string.IsNullOrWhiteSpace(part)) list.Add(part);
                }
            }
        }
        return list;
    }
}
