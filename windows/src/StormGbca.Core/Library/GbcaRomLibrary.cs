// Copyright (c) 2026 STORM TEAM. All rights reserved.
// Licensed under the Apache License, Version 2.0.

using System.Globalization;
using System.IO.Compression;
using System.Security.Cryptography;
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
/// tracks Favorites and Recently Played, and fetches/caches official boxart via bundled 9,132-title
/// Libretro index (raw.githubusercontent.com + cdn.jsdelivr.net mirror) or RetroAchievements.
/// </summary>
public sealed partial class GbcaRomLibrary
{
    private static readonly Regex s_parensTagRegex = new(@"\s*[\(\[][^\)\]]*[\)\]]", RegexOptions.Compiled);
    private static readonly Regex s_nonAlnumRegex = new(@"[^a-z0-9]+", RegexOptions.Compiled);
    private static readonly Regex s_yearRegex = new(@"\b(19\d\d|20\d\d)\b", RegexOptions.Compiled);

    private readonly List<GbcaRomRecord> _roms = [];
    private readonly object _syncLock = new();
    private readonly HttpClient _httpClient = new() { Timeout = TimeSpan.FromSeconds(14) };
    private readonly Dictionary<string, List<string>> _indexCache = new(StringComparer.OrdinalIgnoreCase);
    private readonly SemaphoreSlim _indexLock = new(1, 1);
    private volatile bool _showLibretroCovers;
    private volatile bool _showRaCovers;
    private string _raUsername = string.Empty;
    private string _raToken = string.Empty;

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
        _httpClient.DefaultRequestHeaders.UserAgent.ParseAdd("STORM-GBCA/1.6.0 (Windows)");
        try
        {
            var initial = GbcaSettingsStore.Load();
            ConfigureCoverSources(initial.ShowLibretroCovers, initial.ShowRaCovers, initial.RetroAchievementsUsername, initial.RetroAchievementsToken);
        }
        catch
        {
        }
        LoadCache();
    }

    public void ConfigureCoverSources(bool showLibretroCovers, bool showRaCovers, string? raUsername = null, string? raToken = null)
    {
        _showLibretroCovers = showLibretroCovers;
        _showRaCovers = showRaCovers;
        if (raUsername != null) _raUsername = raUsername;
        if (raToken != null) _raToken = raToken;
    }

    public Task RescanAsync(IEnumerable<string>? userFolders = null, CancellationToken ct = default)
        => ScanDirectoriesAsync(userFolders, ct);

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

    public static bool TryExtractRomFromZip(string zipPath, out byte[] romBytes, out string innerEntryName)
    {
        romBytes = [];
        innerEntryName = string.Empty;
        const long maxRomBytes = 64L * 1024 * 1024;

        if (string.IsNullOrWhiteSpace(zipPath) || !File.Exists(zipPath)) return false;

        try
        {
            using var archive = ZipFile.OpenRead(zipPath);
            ZipArchiveEntry? match = null;
            foreach (var entry in archive.Entries)
            {
                if (string.IsNullOrEmpty(entry.Name) || entry.Length <= 0 || entry.Length > maxRomBytes) continue;
                string ext = Path.GetExtension(entry.Name).ToLowerInvariant();
                if (ext is ".gba" or ".gbc" or ".gb" or ".sgb")
                {
                    match = entry;
                    break;
                }
            }

            if (match == null) return false;

            using var stream = match.Open();
            using var ms = new MemoryStream((int)Math.Min(match.Length, maxRomBytes));
            byte[] buf = new byte[81920];
            long total = 0;
            int read;
            while ((read = stream.Read(buf, 0, buf.Length)) > 0)
            {
                total += read;
                if (total > maxRomBytes) return false;
                ms.Write(buf, 0, read);
            }

            if (ms.Length == 0) return false;
            romBytes = ms.ToArray();
            innerEntryName = Path.GetFileName(match.Name);
            return true;
        }
        catch
        {
            romBytes = [];
            innerEntryName = string.Empty;
            return false;
        }
    }

    public unsafe GbcaRomRecord? InspectRomFile(string filePath)
    {
        if (string.IsNullOrWhiteSpace(filePath) || !File.Exists(filePath)) return null;

        bool isZip = string.Equals(Path.GetExtension(filePath), ".zip", StringComparison.OrdinalIgnoreCase);
        StormGbcaRomInfo info = default;
        string raHashFromZip = string.Empty;

        if (isZip)
        {
            if (!TryExtractRomFromZip(filePath, out byte[] romBytes, out string innerName)) return null;

            bool ok = false;
            if (NativeCoreBindings.IsAvailable)
            {
                fixed (byte* p = romBytes)
                {
                    int rc = NativeCoreBindings.storm_gbca_inspect_rom_buffer(p, (nuint)romBytes.Length, innerName, &info);
                    ok = rc == 0 && info.platform != (int)StormPlatform.None;
                    if (ok)
                    {
                        byte* hbuf = stackalloc byte[33];
                        if (NativeCoreBindings.storm_gbca_compute_ra_hash_buffer(p, (nuint)romBytes.Length, info.platform, hbuf) == 0)
                        {
                            raHashFromZip = StormGbcaRomInfo.ReadFixedUtf8(hbuf, 33);
                        }
                    }
                }
            }

            if (!ok && !TryInspectHeaderFallback(romBytes, innerName, out info))
            {
                return null;
            }
        }
        else
        {
            if (!NativeCoreBindings.IsAvailable) return null;
            int rc = NativeCoreBindings.storm_gbca_inspect_rom_file(filePath, &info);
            if (rc != 0 || info.platform == (int)StormPlatform.None) return null;
        }

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
            RaHash = raHashFromZip,
        };

        string upperName = rec.FileName.ToUpperInvariant();
        if (upperName.Contains("[RUS]") || upperName.Contains("[M. RUS]") || upperName.Contains("(RU)") || upperName.Contains("[RU]"))
        {
            rec.Region = "Russia";
        }
        else if (string.IsNullOrWhiteSpace(rec.Region) || rec.Region == "World")
        {
            if (upperName.Contains("[US]") || upperName.Contains("(USA")) rec.Region = "USA";
            else if (upperName.Contains("[EU]") || upperName.Contains("(EUROPE")) rec.Region = "Europe";
            else if (upperName.Contains("[JP]") || upperName.Contains("(JAPAN")) rec.Region = "Japan";
        }

        string cachedCover = GetExpectedCoverCachePath(rec);
        if (File.Exists(cachedCover) && new FileInfo(cachedCover).Length > 512)
        {
            rec.CoverLocalPath = cachedCover;
        }

        return rec;
    }

    private static unsafe bool TryInspectHeaderFallback(byte[] data, string innerName, out StormGbcaRomInfo info)
    {
        info = default;
        if (data.Length == 0) return false;

        string ext = Path.GetExtension(innerName).ToLowerInvariant();
        StormPlatform plat = ext switch
        {
            ".gba" => StormPlatform.Gba,
            ".gbc" => StormPlatform.Gbc,
            ".sgb" => StormPlatform.Sgb,
            ".gb" => StormPlatform.Gb,
            _ => StormPlatform.None,
        };

        if (plat == StormPlatform.None)
        {
            if (data.Length >= 0xC0 && data[0xB2] == 0x96 && data[3] == 0xEA)
            {
                plat = StormPlatform.Gba;
            }
            else if (data.Length >= 0x150)
            {
                if ((data[0x143] & 0x80) != 0) plat = StormPlatform.Gbc;
                else if (data[0x146] == 0x03 && data[0x14B] == 0x33) plat = StormPlatform.Sgb;
                else plat = StormPlatform.Gb;
            }
        }
        if (plat == StormPlatform.None) return false;

        info.platform = (int)plat;
        info.rom_size = (uint)data.Length;
        info.crc32 = ComputeCrc32(data);

        byte[] md5 = MD5.HashData(data);
        string md5Hex = Convert.ToHexStringLower(md5);
        byte[] md5Ascii = Encoding.ASCII.GetBytes(md5Hex);
        fixed (byte* md5Dst = info.md5)
        {
            int len = Math.Min(md5Ascii.Length, 32);
            md5Ascii.AsSpan(0, len).CopyTo(new Span<byte>(md5Dst, len));
        }

        if (plat == StormPlatform.Gba && data.Length >= 0xC0)
        {
            fixed (byte* tDst = info.title)
            {
                for (int i = 0; i < 12; i++)
                {
                    byte b = data[0xA0 + i];
                    tDst[i] = (b >= 0x20 && b < 0x7F) ? b : (byte)0;
                }
            }
            fixed (byte* cDst = info.game_code)
            {
                for (int i = 0; i < 4; i++)
                {
                    byte b = data[0xAC + i];
                    cDst[i] = (b >= 0x20 && b < 0x7F) ? b : (byte)0;
                }
            }
            string region = (char)data[0xAF] switch
            {
                'J' => "Japan",
                'E' => "USA",
                'P' or 'D' or 'F' or 'I' or 'S' => "Europe",
                _ => "World"
            };
            byte[] regBytes = Encoding.UTF8.GetBytes(region);
            fixed (byte* rDst = info.region)
            {
                regBytes.AsSpan(0, Math.Min(regBytes.Length, 15)).CopyTo(new Span<byte>(rDst, 15));
            }
        }
        else if (data.Length >= 0x150)
        {
            int titleLen = (data[0x143] & 0x80) != 0 ? 11 : 15;
            fixed (byte* tDst = info.title)
            {
                for (int i = 0; i < titleLen; i++)
                {
                    byte b = data[0x134 + i];
                    if (b == 0) break;
                    tDst[i] = (b >= 0x20 && b < 0x7F) ? b : (byte)0;
                }
            }
            byte[] regBytes = Encoding.UTF8.GetBytes(data[0x14A] == 0 ? "Japan" : "World");
            fixed (byte* rDst = info.region)
            {
                regBytes.AsSpan(0, Math.Min(regBytes.Length, 15)).CopyTo(new Span<byte>(rDst, 15));
            }
        }

        return true;
    }

    private static uint ComputeCrc32(ReadOnlySpan<byte> data)
    {
        uint crc = 0xFFFFFFFFu;
        foreach (byte b in data)
        {
            crc ^= b;
            for (int k = 0; k < 8; k++)
            {
                crc = (crc & 1) != 0 ? (crc >> 1) ^ 0xEDB88320u : (crc >> 1);
            }
        }
        return crc ^ 0xFFFFFFFFu;
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

    public int ClearCoverCache()
    {
        int removed = 0;
        try
        {
            if (Directory.Exists(StormGbcaPaths.CoversDirectory))
            {
                foreach (var f in Directory.EnumerateFiles(StormGbcaPaths.CoversDirectory, "*.png"))
                {
                    try { File.Delete(f); removed++; } catch { }
                }
            }
            lock (_syncLock)
            {
                foreach (var r in _roms) r.CoverLocalPath = string.Empty;
            }
            SaveCache();
            LibraryChanged?.Invoke();
        }
        catch { }
        return removed;
    }

    /// <summary>
    /// Downloads all missing covers in parallel (up to 4 concurrent workers) when enabled by user.
    /// </summary>
    public Task PreloadAllMissingCoversAsync(CancellationToken ct = default)
        => PreloadAllMissingCoversAsync(null, ct);

    public async Task PreloadAllMissingCoversAsync(GbcaAppSettings? settings, CancellationToken ct = default)
    {
        if (settings != null)
        {
            ConfigureCoverSources(settings.ShowLibretroCovers, settings.ShowRaCovers, settings.RetroAchievementsUsername, settings.RetroAchievementsToken);
        }
        if (!_showLibretroCovers && !_showRaCovers) return;

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
                await EnsureCoverArtAsync(rom, settings, ct).ConfigureAwait(false);
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
    /// Downloads and locally caches official boxart using bundled 9,132-title Libretro index
    /// (raw.githubusercontent.com primary + cdn.jsdelivr.net mirror fallback) and/or RetroAchievements.
    /// Strictly performs zero downloads when both ShowLibretroCovers and ShowRaCovers are false.
    /// </summary>
    public Task<string?> EnsureCoverArtAsync(GbcaRomRecord rom, CancellationToken ct = default)
        => EnsureCoverArtAsync(rom, null, ct);

    public async Task<string?> EnsureCoverArtAsync(GbcaRomRecord rom, GbcaAppSettings? settings, CancellationToken ct = default)
    {
        bool useLibretro = settings?.ShowLibretroCovers ?? _showLibretroCovers;
        bool useRa = settings?.ShowRaCovers ?? _showRaCovers;
        string raUser = settings?.RetroAchievementsUsername ?? _raUsername;
        string raToken = settings?.RetroAchievementsToken ?? _raToken;

        string localPath = GetExpectedCoverCachePath(rom);
        if (File.Exists(localPath) && new FileInfo(localPath).Length > 512)
        {
            rom.CoverLocalPath = localPath;
            return localPath;
        }

        if (!useLibretro && !useRa)
        {
            return null;
        }

        Directory.CreateDirectory(StormGbcaPaths.CoversDirectory);

        if (useLibretro)
        {
            var systemsToTry = rom.Platform switch
            {
                StormPlatform.Gb or StormPlatform.Sgb => new[] { "gb", "gbc" },
                StormPlatform.Gbc => new[] { "gbc", "gb" },
                _ => new[] { "gba" },
            };

            string rawName = Path.GetFileNameWithoutExtension(rom.FileName);
            string baseClean = s_parensTagRegex.Replace(rawName, string.Empty).Replace('_', ' ').Trim();

            foreach (string sysKey in systemsToTry)
            {
                var indexEntries = await GetOrLoadIndexAsync(sysKey, ct).ConfigureAwait(false);
                string? matchedPng = FindBestIndexMatch(indexEntries, rawName, baseClean, rom.Title, rom.Region);
                if (!string.IsNullOrWhiteSpace(matchedPng))
                {
                    foreach (string url in BuildLibretroImageUrls(sysKey, matchedPng, "Named_Boxarts"))
                    {
                        if (await TryDownloadCoverAsync(url, localPath, ct).ConfigureAwait(false))
                        {
                            rom.CoverLocalPath = localPath;
                            SaveCache();
                            CoverArtUpdated?.Invoke(rom);
                            return localPath;
                        }
                    }
                    foreach (string url in BuildLibretroImageUrls(sysKey, matchedPng, "Named_Titles"))
                    {
                        if (await TryDownloadCoverAsync(url, localPath, ct).ConfigureAwait(false))
                        {
                            rom.CoverLocalPath = localPath;
                            SaveCache();
                            CoverArtUpdated?.Invoke(rom);
                            return localPath;
                        }
                    }
                }
            }

            // Direct fallback probing
            string primarySys = systemsToTry[0];
            var candidates = new List<string>();
            if (!string.IsNullOrWhiteSpace(rawName)) candidates.Add(rawName);
            if (!string.IsNullOrWhiteSpace(baseClean))
            {
                candidates.Add($"{baseClean} (USA, Europe)");
                candidates.Add($"{baseClean} (USA)");
                candidates.Add($"{baseClean} (Europe)");
                candidates.Add($"{baseClean} (World)");
                candidates.Add($"{baseClean} (Japan)");
            }

            foreach (string nameCandidate in candidates.Distinct(StringComparer.OrdinalIgnoreCase))
            {
                if (ct.IsCancellationRequested) break;
                string sanitized = SanitizeLibretroFileName(nameCandidate) + ".png";
                foreach (string url in BuildLibretroImageUrls(primarySys, sanitized))
                {
                    if (await TryDownloadCoverAsync(url, localPath, ct).ConfigureAwait(false))
                    {
                        rom.CoverLocalPath = localPath;
                        SaveCache();
                        CoverArtUpdated?.Invoke(rom);
                        return localPath;
                    }
                }
            }
        }

        if (useRa && await TryFetchRaCoverAsync(rom, localPath, raUser, raToken, ct).ConfigureAwait(false))
        {
            rom.CoverLocalPath = localPath;
            SaveCache();
            CoverArtUpdated?.Invoke(rom);
            return localPath;
        }

        return null;
    }

    private async Task<bool> TryFetchRaCoverAsync(GbcaRomRecord rom, string localPath, string raUser, string raToken, CancellationToken ct)
    {
        try
        {
            if (string.IsNullOrWhiteSpace(rom.RaHash))
            {
                rom.RaHash = ComputeRaHashSafe(rom);
            }

            if (rom.RaGameId <= 0 && !string.IsNullOrWhiteSpace(rom.RaHash))
            {
                string idUrl = $"https://retroachievements.org/dorequest.php?r=gameid&m={Uri.EscapeDataString(rom.RaHash)}";
                using var idResp = await _httpClient.GetAsync(idUrl, ct).ConfigureAwait(false);
                if (idResp.IsSuccessStatusCode)
                {
                    string idJson = await idResp.Content.ReadAsStringAsync(ct).ConfigureAwait(false);
                    using var idDoc = JsonDocument.Parse(idJson);
                    if (idDoc.RootElement.TryGetProperty("GameID", out var gidEl) && gidEl.TryGetInt32(out int gid) && gid > 0)
                    {
                        rom.RaGameId = gid;
                    }
                }
            }

            if (rom.RaGameId <= 0 || string.IsNullOrWhiteSpace(raUser) || string.IsNullOrWhiteSpace(raToken))
            {
                return false;
            }

            string patchUrl = $"https://retroachievements.org/dorequest.php?r=patch&u={Uri.EscapeDataString(raUser)}&t={Uri.EscapeDataString(raToken)}&g={rom.RaGameId}";
            using var pResp = await _httpClient.GetAsync(patchUrl, ct).ConfigureAwait(false);
            if (!pResp.IsSuccessStatusCode) return false;
            string pJson = await pResp.Content.ReadAsStringAsync(ct).ConfigureAwait(false);
            using var pDoc = JsonDocument.Parse(pJson);
            if (!pDoc.RootElement.TryGetProperty("PatchData", out var patchData)) return false;

            string boxArt = patchData.TryGetProperty("ImageBoxArt", out var baEl) ? (baEl.GetString() ?? string.Empty) : string.Empty;
            string icon = patchData.TryGetProperty("ImageIcon", out var icEl) ? (icEl.GetString() ?? string.Empty) : string.Empty;

            foreach (string relPath in new[] { boxArt, icon })
            {
                if (string.IsNullOrWhiteSpace(relPath)) continue;
                string fullUrl = relPath.StartsWith("http", StringComparison.OrdinalIgnoreCase)
                    ? relPath
                    : $"https://media.retroachievements.org{relPath}";
                if (await TryDownloadCoverAsync(fullUrl, localPath, ct).ConfigureAwait(false))
                {
                    return true;
                }
            }
        }
        catch
        {
        }
        return false;
    }

    private static unsafe string ComputeRaHashSafe(GbcaRomRecord rom)
    {
        if (!NativeCoreBindings.IsAvailable || string.IsNullOrWhiteSpace(rom.FilePath) || !File.Exists(rom.FilePath))
        {
            return string.Empty;
        }

        byte* buf = stackalloc byte[33];
        if (string.Equals(Path.GetExtension(rom.FilePath), ".zip", StringComparison.OrdinalIgnoreCase))
        {
            if (TryExtractRomFromZip(rom.FilePath, out byte[] romBytes, out _))
            {
                fixed (byte* p = romBytes)
                {
                    if (NativeCoreBindings.storm_gbca_compute_ra_hash_buffer(p, (nuint)romBytes.Length, (int)rom.Platform, buf) == 0)
                    {
                        return StormGbcaRomInfo.ReadFixedUtf8(buf, 33);
                    }
                }
            }
            return string.Empty;
        }

        if (NativeCoreBindings.storm_gbca_compute_ra_hash(rom.FilePath, (int)rom.Platform, buf) == 0)
        {
            return StormGbcaRomInfo.ReadFixedUtf8(buf, 33);
        }
        return string.Empty;
    }

    private static string RepoNameForKey(string sysKey) => sysKey switch
    {
        "gb" => "Nintendo_-_Game_Boy",
        "gbc" => "Nintendo_-_Game_Boy_Color",
        _ => "Nintendo_-_Game_Boy_Advance",
    };

    private static string[] BuildLibretroImageUrls(string sysKey, string fileName, string folder = "Named_Boxarts")
    {
        string repo = RepoNameForKey(sysKey);
        string encoded = Uri.EscapeDataString(fileName);
        return
        [
            $"https://cdn.jsdelivr.net/gh/libretro-thumbnails/{repo}@master/{folder}/{encoded}",
            $"https://fastly.jsdelivr.net/gh/libretro-thumbnails/{repo}@master/{folder}/{encoded}",
            $"https://raw.githubusercontent.com/libretro-thumbnails/{repo}/master/{folder}/{encoded}",
        ];
    }

    private async Task<bool> TryDownloadCoverAsync(string url, string localPath, CancellationToken ct)
    {
        try
        {
            using var resp = await _httpClient.GetAsync(url, HttpCompletionOption.ResponseHeadersRead, ct).ConfigureAwait(false);
            if (!resp.IsSuccessStatusCode) return false;
            byte[] bytes = await resp.Content.ReadAsByteArrayAsync(ct).ConfigureAwait(false);
            if (!IsValidImageBytes(bytes)) return false;
            await File.WriteAllBytesAsync(localPath, bytes, ct).ConfigureAwait(false);
            return true;
        }
        catch
        {
            return false;
        }
    }

    private static bool IsValidImageBytes(byte[] bytes)
    {
        if (bytes.Length < 256) return false;
        bool isPng = bytes[0] == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47;
        bool isJpg = bytes[0] == 0xFF && bytes[1] == 0xD8 && bytes[2] == 0xFF;
        bool isWebp = bytes[0] == 0x52 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x46;
        return isPng || isJpg || isWebp;
    }

    private async Task<List<string>> GetOrLoadIndexAsync(string sysKey, CancellationToken ct)
    {
        await _indexLock.WaitAsync(ct).ConfigureAwait(false);
        try
        {
            if (_indexCache.TryGetValue(sysKey, out var cached)) return cached;

            // 1. Instant 0 ms bundled offline asset index (next to EXE or in workspace)
            string[] bundledCandidates =
            [
                Path.Combine(AppContext.BaseDirectory, "assets", "boxart_index", $"{sysKey}.txt"),
                Path.Combine(AppContext.BaseDirectory, "boxart_index", $"{sysKey}.txt"),
                Path.Combine(StormGbcaPaths.CacheDirectory, $"gh_idx_{sysKey}_v2.txt"),
            ];

            foreach (string path in bundledCandidates)
            {
                if (File.Exists(path) && new FileInfo(path).Length > 1024)
                {
                    var lines = (await File.ReadAllLinesAsync(path, ct).ConfigureAwait(false))
                        .Where(l => l.EndsWith(".png", StringComparison.OrdinalIgnoreCase))
                        .ToList();
                    if (lines.Count > 50)
                    {
                        _indexCache[sysKey] = lines;
                        return lines;
                    }
                }
            }

            // 2. Fallback: live GitHub Trees API
            string repo = RepoNameForKey(sysKey);
            string apiUrl = $"https://api.github.com/repos/libretro-thumbnails/{repo}/git/trees/master?recursive=1";
            try
            {
                string json = await _httpClient.GetStringAsync(apiUrl, ct).ConfigureAwait(false);
                using var doc = JsonDocument.Parse(json);
                var names = new List<string>(4096);
                if (doc.RootElement.TryGetProperty("tree", out var tree))
                {
                    foreach (var item in tree.EnumerateArray())
                    {
                        if (item.TryGetProperty("path", out var pEl))
                        {
                            string p = pEl.GetString() ?? "";
                            if (p.StartsWith("Named_Boxarts/", StringComparison.Ordinal) &&
                                p.EndsWith(".png", StringComparison.OrdinalIgnoreCase))
                            {
                                names.Add(p["Named_Boxarts/".Length..]);
                            }
                        }
                    }
                }
                if (names.Count > 50)
                {
                    Directory.CreateDirectory(StormGbcaPaths.CacheDirectory);
                    string cacheFile = Path.Combine(StormGbcaPaths.CacheDirectory, $"gh_idx_{sysKey}_v2.txt");
                    await File.WriteAllLinesAsync(cacheFile, names, ct).ConfigureAwait(false);
                    _indexCache[sysKey] = names;
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

    private static string? FindBestIndexMatch(List<string> index, string rawName, string baseClean, string internalTitle, string preferredRegion = "")
    {
        if (index.Count == 0) return null;

        var candidates = new List<string>();
        if (!string.IsNullOrWhiteSpace(baseClean)) candidates.Add(baseClean);
        if (!string.IsNullOrWhiteSpace(rawName)) candidates.Add(rawName);
        if (!string.IsNullOrWhiteSpace(internalTitle) && internalTitle.Length >= 3) candidates.Add(internalTitle);

        string? bestMatch = null;
        double bestScore = 0.0;

        foreach (string cand in candidates)
        {
            string targetNorm = NormalizeTitle(cand);
            if (string.IsNullOrEmpty(targetNorm)) continue;

            string[] targetTokens = targetNorm.Split(' ', StringSplitOptions.RemoveEmptyEntries);
            if (targetTokens.Length == 0) continue;

            foreach (string entry in index)
            {
                string stem = Path.GetFileNameWithoutExtension(entry);
                string stemBase = s_parensTagRegex.Replace(stem, string.Empty).Trim();
                string norm = NormalizeTitle(stemBase);
                if (norm == targetNorm)
                {
                    double regBonus = (!string.IsNullOrWhiteSpace(preferredRegion) && stem.Contains($"({preferredRegion}", StringComparison.OrdinalIgnoreCase)) ? 1.12
                        : stem.Contains("(USA, Europe", StringComparison.OrdinalIgnoreCase) ? 1.08
                        : stem.Contains("(USA", StringComparison.OrdinalIgnoreCase) ? 1.06
                        : stem.Contains("(World", StringComparison.OrdinalIgnoreCase) ? 1.04
                        : stem.Contains("(Europe", StringComparison.OrdinalIgnoreCase) ? 1.02 : 1.0;
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
                double score = jaccard * 0.72 + contain * 0.28;
                if (norm.StartsWith(targetNorm, StringComparison.Ordinal) || targetNorm.StartsWith(norm, StringComparison.Ordinal))
                    score += 0.08;
                if (stem.Contains("(USA", StringComparison.OrdinalIgnoreCase)) score += 0.02;
                if (score > bestScore && score >= 0.58)
                {
                    bestScore = score;
                    bestMatch = entry;
                }
            }
        }

        return bestMatch;
    }

    private static string NormalizeTitle(string input)
    {
        if (string.IsNullOrWhiteSpace(input)) return string.Empty;
        string decomposed = input.Normalize(NormalizationForm.FormD);
        var sb = new StringBuilder(decomposed.Length);
        foreach (char c in decomposed)
        {
            if (CharUnicodeInfo.GetUnicodeCategory(c) != UnicodeCategory.NonSpacingMark)
                sb.Append(c);
        }
        string s = s_parensTagRegex.Replace(sb.ToString(), " ").ToLowerInvariant();
        s = s_yearRegex.Replace(s, " ");
        s = s.Replace("&", " and ").Replace("'", "").Replace("_", " ");
        s = Regex.Replace(s, @"\biii\b", "3");
        s = Regex.Replace(s, @"\bii\b", "2");
        s = Regex.Replace(s, @"\biv\b", "4");
        s = Regex.Replace(s, @"\bvi\b", "6");
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
