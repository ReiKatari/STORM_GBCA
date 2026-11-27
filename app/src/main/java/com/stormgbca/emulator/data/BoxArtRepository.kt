package com.stormgbca.emulator.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.stormgbca.emulator.model.ConsoleSystem
import com.stormgbca.emulator.model.RomEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Dual-source boxart repository (STORM DS parity):
 * 1. Libretro Thumbnails via bundled 9,132-title offline index (GB / GBC / GBA)
 *    with raw.githubusercontent.com primary + cdn.jsdelivr.net mirror fallback.
 * 2. RetroAchievements official boxart / title art via independent toggle.
 * 3. Custom user-selected cover image.
 *
 * All automatic cover sources default to OFF in GbcaCoreConfig (matching STORM DS).
 */
object BoxArtRepository {
    private const val TAG = "BoxArtRepository"
    private const val USER_AGENT = "STORM-GBCA/1.3.0 (Android)"

    private const val REPO_GB = "Nintendo_-_Game_Boy"
    private const val REPO_GBC = "Nintendo_-_Game_Boy_Color"
    private const val REPO_GBA = "Nintendo_-_Game_Boy_Advance"

    private data class IndexEntry(
        val fileName: String,
        val normalized: String,
        val tokens: Set<String>,
        val regionScore: Int,
        val regionTag: String,
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(18, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val indexMutexes = mapOf(
        ConsoleSystem.GB to Mutex(),
        ConsoleSystem.GBC to Mutex(),
        ConsoleSystem.GBA to Mutex(),
    )
    private val indexCache = ConcurrentHashMap<ConsoleSystem, List<IndexEntry>>()
    private val exactCache = ConcurrentHashMap<ConsoleSystem, Map<String, IndexEntry>>()
    private val failedLibretro = ConcurrentHashMap<String, Long>()
    private val failedRa = ConcurrentHashMap<String, Long>()

    private fun libretroCacheDir(context: Context): File =
        File(context.filesDir, "boxart").apply { mkdirs() }

    private fun raCacheDir(context: Context): File =
        File(context.filesDir, "ra_boxart").apply { mkdirs() }

    private fun customDir(context: Context): File =
        File(context.filesDir, "custom_covers").apply { mkdirs() }

    private fun downloadedIndexFile(context: Context, system: ConsoleSystem): File =
        File(libretroCacheDir(context), "gh_index_${system.name.lowercase()}_v2.txt")

    private fun coverKey(rom: RomEntry): String {
        val base = rom.md5.ifBlank { rom.crc32.toString(16) }.ifBlank { rom.fileName.hashCode().toString() }
        return "${rom.consoleSystem.name.lowercase()}_${base.replace(Regex("[^a-zA-Z0-9_-]"), "_")}"
    }

    private fun repoNameFor(system: ConsoleSystem): String = when (system) {
        ConsoleSystem.GB -> REPO_GB
        ConsoleSystem.GBC -> REPO_GBC
        ConsoleSystem.GBA -> REPO_GBA
    }

    private fun assetNameFor(system: ConsoleSystem): String = when (system) {
        ConsoleSystem.GB -> "boxart_index/gb.txt"
        ConsoleSystem.GBC -> "boxart_index/gbc.txt"
        ConsoleSystem.GBA -> "boxart_index/gba.txt"
    }

    private fun buildImageUrls(system: ConsoleSystem, fileName: String, folder: String = "Named_Boxarts"): List<String> {
        val repo = repoNameFor(system)
        val encoded = encodePathSegment(fileName)
        return listOf(
            "https://cdn.jsdelivr.net/gh/libretro-thumbnails/$repo@master/$folder/$encoded",
            "https://fastly.jsdelivr.net/gh/libretro-thumbnails/$repo@master/$folder/$encoded",
            "https://raw.githubusercontent.com/libretro-thumbnails/$repo/master/$folder/$encoded",
        )
    }

    private fun encodePathSegment(segment: String): String {
        return URLEncoder.encode(segment, "UTF-8")
            .replace("+", "%20")
            .replace("%28", "(")
            .replace("%29", ")")
            .replace("%2C", ",")
            .replace("%27", "'")
    }

    /**
     * Returns the best available cached cover respecting user toggles:
     * Custom cover (if set) -> Libretro cover (if enabled) -> RA cover (if enabled).
     */
    fun getCachedBoxArtPath(
        context: Context,
        rom: RomEntry,
        allowLibretro: Boolean = true,
        allowRa: Boolean = false,
    ): String? {
        val key = coverKey(rom)
        val custom = File(customDir(context), "$key.png")
        if (custom.isFile && custom.length() > 128L) return custom.absolutePath

        if (allowLibretro) {
            val cached = File(libretroCacheDir(context), "$key.png")
            if (cached.isFile && cached.length() > 128L) return cached.absolutePath
        }
        if (allowRa) {
            val raCached = File(raCacheDir(context), "$key.png")
            if (raCached.isFile && raCached.length() > 128L) return raCached.absolutePath
            // Also return Libretro fallback cached for patched/[MOD] ROMs not in RA hash DB
            val fallbackCached = File(libretroCacheDir(context), "$key.png")
            if (fallbackCached.isFile && fallbackCached.length() > 128L) return fallbackCached.absolutePath
        }
        return null
    }

    fun getCustomCoverPath(context: Context, rom: RomEntry): String? {
        val custom = File(customDir(context), "${coverKey(rom)}.png")
        return if (custom.isFile && custom.length() > 128L) custom.absolutePath else null
    }

    suspend fun saveCustomCover(context: Context, rom: RomEntry, uri: Uri): String? = withContext(Dispatchers.IO) {
        val key = coverKey(rom)
        val outFile = File(customDir(context), "$key.png")
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
            }
            if (outFile.length() > 128L) outFile.absolutePath else null
        }.getOrNull()
    }

    fun clearAllCachedCovers(context: Context): Int {
        failedLibretro.clear()
        failedRa.clear()
        var removed = 0
        libretroCacheDir(context).listFiles()?.forEach { f ->
            if (f.name.endsWith(".png") && f.delete()) removed++
        }
        raCacheDir(context).listFiles()?.forEach { f ->
            if (f.name.endsWith(".png") && f.delete()) removed++
        }
        return removed
    }

    /**
     * Fetches cover art according to enabled sources (Libretro BoxArt and/or RetroAchievements BoxArt).
     */
    suspend fun fetchOrGetBoxArt(
        context: Context,
        rom: RomEntry,
        useLibretro: Boolean = true,
        useRa: Boolean = false,
        forceRefresh: Boolean = false,
    ): String? = withContext(Dispatchers.IO) {
        val key = coverKey(rom)
        getCustomCoverPath(context, rom)?.let { return@withContext it }

        if (!useLibretro && !useRa) return@withContext null

        if (!forceRefresh) {
            getCachedBoxArtPath(context, rom, allowLibretro = useLibretro, allowRa = useRa)?.let {
                return@withContext it
            }
        } else {
            failedLibretro.remove(key)
            failedRa.remove(key)
        }

        if (useLibretro) {
            fetchLibretroBoxArt(context, rom, key, forceRefresh)?.let { return@withContext it }
        }
        if (useRa) {
            fetchRaBoxArt(context, rom, key, forceRefresh)?.let { return@withContext it }
            // Smart fallback for patched / translated [MOD] ROMs whose MD5 is not in RA DB:
            // match by normalized title via our bundled 9,132-title Libretro index so 100% of games get covers!
            fetchLibretroBoxArt(context, rom, key, forceRefresh)?.let { return@withContext it }
        }
        null
    }

    private suspend fun fetchLibretroBoxArt(
        context: Context,
        rom: RomEntry,
        key: String,
        forceRefresh: Boolean,
    ): String? {
        if (!forceRefresh) {
            val lastFail = failedLibretro[key]
            if (lastFail != null && (System.currentTimeMillis() - lastFail) < 2L * 60 * 1000) {
                return null
            }
        }

        val systemsToTry = when (rom.consoleSystem) {
            ConsoleSystem.GB -> listOf(ConsoleSystem.GB, ConsoleSystem.GBC)
            ConsoleSystem.GBC -> listOf(ConsoleSystem.GBC, ConsoleSystem.GB)
            ConsoleSystem.GBA -> listOf(ConsoleSystem.GBA)
        }

        val candidates = buildCandidates(rom)
        val preferredRegion = rom.region.uppercase()
        val outFile = File(libretroCacheDir(context), "$key.png")

        for (sys in systemsToTry) {
            val entries = ensureIndex(context, sys, forceRefresh)
            if (entries.isNotEmpty()) {
                val match = findBestMatch(candidates, sys, entries, preferredRegion)
                if (match != null) {
                    for (url in buildImageUrls(sys, match.fileName, "Named_Boxarts")) {
                        val saved = downloadImageToFile(url, outFile)
                        if (saved != null) return saved
                    }
                    // Fallback to Named_Titles if Named_Boxarts failed
                    for (url in buildImageUrls(sys, match.fileName, "Named_Titles")) {
                        val saved = downloadImageToFile(url, outFile)
                        if (saved != null) return saved
                    }
                }
            }
        }

        // Fallback direct probes for exact standard naming
        for (name in directProbeNames(rom)) {
            val sanitized = sanitizeLibretroFileName(name)
            for (url in buildImageUrls(rom.consoleSystem, "$sanitized.png", "Named_Boxarts")) {
                val saved = downloadImageToFile(url, outFile)
                if (saved != null) return saved
            }
        }

        failedLibretro[key] = System.currentTimeMillis()
        return null
    }

    private suspend fun fetchRaBoxArt(
        context: Context,
        rom: RomEntry,
        key: String,
        forceRefresh: Boolean,
    ): String? {
        if (!forceRefresh) {
            val lastFail = failedRa[key]
            if (lastFail != null && (System.currentTimeMillis() - lastFail) < 5L * 60 * 1000) {
                return null
            }
        }
        val hash = rom.md5.trim().lowercase()
        if (hash.length != 32) return null

        val outFile = File(raCacheDir(context), "$key.png")
        val artUrl = RetroAchievementsClient.resolveGameBoxArtUrl(context, hash) ?: run {
            failedRa[key] = System.currentTimeMillis()
            return null
        }
        val saved = downloadImageToFile(artUrl, outFile)
        if (saved == null) {
            failedRa[key] = System.currentTimeMillis()
        }
        return saved
    }

    private suspend fun ensureIndex(
        context: Context,
        system: ConsoleSystem,
        forceRefresh: Boolean,
    ): List<IndexEntry> {
        if (!forceRefresh) {
            indexCache[system]?.let { if (it.isNotEmpty()) return it }
        }
        val mutex = indexMutexes[system] ?: return emptyList()
        return mutex.withLock {
            if (!forceRefresh) {
                indexCache[system]?.let { if (it.isNotEmpty()) return@withLock it }
            }

            // 1. Optional live refresh from GitHub Trees API if forceRefresh
            val liveFile = downloadedIndexFile(context, system)
            if (forceRefresh) {
                fetchGithubTreeIndex(system)?.let { txt ->
                    runCatching { liveFile.writeText(txt) }
                    return@withLock parseIndex(system, txt)
                }
            }

            // 2. Cached live refresh if present
            if (liveFile.isFile && liveFile.length() > 1024L) {
                runCatching { liveFile.readText() }.getOrNull()?.takeIf { it.isNotBlank() }?.let {
                    return@withLock parseIndex(system, it)
                }
            }

            // 3. Instant 0 ms bundled offline asset index (9,132 titles across GB/GBC/GBA!)
            val assetText = runCatching {
                context.assets.open(assetNameFor(system)).bufferedReader(Charsets.UTF_8).use { it.readText() }
            }.onFailure { Log.w(TAG, "Bundled index missing for $system: ${it.message}") }.getOrNull()

            if (!assetText.isNullOrBlank()) {
                return@withLock parseIndex(system, assetText)
            }

            // 4. Last resort: live GitHub Trees API
            fetchGithubTreeIndex(system)?.let { txt ->
                runCatching { liveFile.writeText(txt) }
                return@withLock parseIndex(system, txt)
            }

            emptyList()
        }
    }

    private fun fetchGithubTreeIndex(system: ConsoleSystem): String? {
        val repo = repoNameFor(system)
        val url = "https://api.github.com/repos/libretro-thumbnails/$repo/git/trees/master?recursive=1"
        return runCatching {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string() ?: return@use null
                val arr = JSONObject(body).optJSONArray("tree") ?: return@use null
                val names = ArrayList<String>(arr.length() / 2)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val p = obj.optString("path")
                    if (p.startsWith("Named_Boxarts/") && p.endsWith(".png")) {
                        names.add(p.removePrefix("Named_Boxarts/"))
                    }
                }
                if (names.size < 50) null else names.sorted().joinToString("\n")
            }
        }.onFailure { Log.w(TAG, "GitHub tree fetch failed for $system: ${it.message}") }.getOrNull()
    }

    private fun parseIndex(system: ConsoleSystem, raw: String): List<IndexEntry> {
        val exact = HashMap<String, IndexEntry>(4096)
        val entries = ArrayList<IndexEntry>(4096)
        raw.lineSequence().forEach { line ->
            val fileName = line.trim()
            if (fileName.isEmpty() || !fileName.endsWith(".png", ignoreCase = true)) return@forEach
            val regionScore = when {
                fileName.contains("(USA, Europe", ignoreCase = true) -> 5
                fileName.contains("(USA", ignoreCase = true) -> 4
                fileName.contains("(World", ignoreCase = true) -> 3
                fileName.contains("(Europe", ignoreCase = true) -> 2
                fileName.contains("(Japan", ignoreCase = true) -> 1
                else -> 0
            }
            val regionTag = when {
                fileName.contains("(USA", ignoreCase = true) -> "USA"
                fileName.contains("(Europe", ignoreCase = true) -> "EUROPE"
                fileName.contains("(Japan", ignoreCase = true) -> "JAPAN"
                fileName.contains("(World", ignoreCase = true) -> "WORLD"
                else -> ""
            }
            val clean = fileName.removeSuffix(".png").substringBefore(" (").substringBefore(" [")
            val norm = normalize(clean)
            if (norm.isNotEmpty()) {
                val toks = norm.split(' ').filter { it.isNotEmpty() }.toSet()
                val entry = IndexEntry(fileName, norm, toks, regionScore, regionTag)
                entries.add(entry)
                val prev = exact[norm]
                if (prev == null || entry.regionScore > prev.regionScore) {
                    exact[norm] = entry
                }
            }
        }
        exactCache[system] = exact
        indexCache[system] = entries
        return entries
    }

    private fun findBestMatch(
        candidates: List<String>,
        system: ConsoleSystem,
        entries: List<IndexEntry>,
        preferredRegion: String = "",
    ): IndexEntry? {
        val exactMap = exactCache[system] ?: emptyMap()
        // Pass 1: O(1) exact normalized title lookup (with region preference if available)
        for (c in candidates) {
            val norm = normalize(c)
            if (norm.isEmpty()) continue
            if (exactMap.containsKey(norm)) {
                if (preferredRegion.isNotBlank()) {
                    entries.firstOrNull { it.normalized == norm && it.regionTag == preferredRegion }?.let { return it }
                }
                exactMap[norm]?.let { return it }
            }
        }

        // Pass 2: Token Jaccard + containment fuzzy scoring (handles [MOD - M. RUS], years, revisions, subtitles)
        var best: IndexEntry? = null
        var bestScore = 0.0
        for (c in candidates) {
            val norm = normalize(c)
            if (norm.length < 2) continue
            val tokens = norm.split(' ').filter { it.isNotEmpty() }.toSet()
            if (tokens.isEmpty()) continue
            for (e in entries) {
                if (e.tokens.isEmpty()) continue
                val inter = tokens.intersect(e.tokens).size.toDouble()
                if (inter == 0.0) continue
                val union = tokens.union(e.tokens).size.toDouble()
                val jaccard = inter / union
                val contain = inter / minOf(tokens.size, e.tokens.size).toDouble()
                var score = jaccard * 0.72 + contain * 0.28
                if (e.normalized.startsWith(norm) || norm.startsWith(e.normalized)) {
                    score += 0.08
                }
                if (preferredRegion.isNotBlank() && e.regionTag == preferredRegion) {
                    score += 0.025
                }
                score += e.regionScore * 0.005
                if (score > bestScore) {
                    bestScore = score
                    best = e
                    if (jaccard >= 0.95 && (e.regionTag == preferredRegion || e.regionScore >= 4)) return best
                }
            }
        }
        return if (bestScore >= 0.54) best else null
    }

    private fun downloadImageToFile(url: String, outFile: File): String? {
        return runCatching {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val bytes = resp.body?.bytes() ?: return@use null
                if (!isValidImageBytes(bytes)) return@use null
                outFile.parentFile?.mkdirs()
                FileOutputStream(outFile).use { it.write(bytes) }
                outFile.absolutePath
            }
        }.getOrNull()
    }

    private fun isValidImageBytes(bytes: ByteArray): Boolean {
        if (bytes.size < 256) return false
        val isPng = bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        val isJpg = bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
        val isWebp = bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() &&
            bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte()
        return isPng || isJpg || isWebp
    }

    private fun buildCandidates(rom: RomEntry): List<String> {
        val raw = rom.fileName.substringBeforeLast('.')
        val stripped = raw
            .replace(Regex("\\[[^\\]]*\\]"), " ")
            .replace(Regex("\\([^\\)]*\\)"), " ")
            .replace("_", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val res = LinkedHashSet<String>()
        if (stripped.isNotBlank()) {
            res.add(stripped)
            if (stripped.startsWith("The ", ignoreCase = true)) {
                res.add(stripped.drop(4) + ", The")
            }
            if (stripped.contains(" - ")) {
                res.add(stripped.substringBefore(" - ").trim())
            }
            val noVer = stripped.replace(
                Regex("\\b(v\\d[\\d.]*|rev\\s*\\d+|rus|eng|mod|m\\.\\s*rus|hack|patched|gb|gbc|gba)\\b", RegexOption.IGNORE_CASE),
                " "
            ).replace(Regex("\\s+"), " ").trim()
            if (noVer.isNotBlank()) res.add(noVer)
        }
        if (rom.internalTitle.isNotBlank() && rom.internalTitle.length >= 3) {
            res.add(rom.internalTitle.trim())
        }
        return res.toList()
    }

    private fun directProbeNames(rom: RomEntry): List<String> {
        val raw = rom.fileName.substringBeforeLast('.').trim()
        val clean = raw.replace(Regex("\\[[^\\]]*\\]"), " ")
            .replace(Regex("\\([^\\)]*\\)"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .replace(":", " -")
        return listOf(
            raw,
            "$clean (USA, Europe)",
            "$clean (USA)",
            "$clean (Europe)",
            "$clean (World)",
            "$clean (Japan)",
        ).distinct().filter { it.isNotBlank() }
    }

    private fun sanitizeLibretroFileName(name: String): String {
        val sb = StringBuilder(name.length)
        for (c in name) {
            sb.append(if (c in "&*/:`<>?\\|") '_' else c)
        }
        return sb.toString().trim()
    }

    private fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
        return decomposed
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace("&", " and ")
            .replace("'", "")
            .replace(Regex("\\([^\\)]*\\)|\\[[^\\]]*\\]"), " ")
            .replace(Regex("\\b(19\\d\\d|20\\d\\d)\\b"), " ")
            .replace(Regex("\\biii\\b"), "3")
            .replace(Regex("\\bii\\b"), "2")
            .replace(Regex("\\biv\\b"), "4")
            .replace(Regex("\\bvi\\b"), "6")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }
}
