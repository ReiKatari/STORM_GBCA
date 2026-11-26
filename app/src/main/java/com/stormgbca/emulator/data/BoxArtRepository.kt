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
import java.io.File
import java.io.FileOutputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object BoxArtRepository {
    private const val TAG = "BoxArtRepository"
    private const val INDEX_MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    private const val URL_GB = "https://thumbnails.libretro.com/Nintendo%20-%20Game%20Boy/Named_Boxarts/"
    private const val URL_GBC = "https://thumbnails.libretro.com/Nintendo%20-%20Game%20Boy%20Color/Named_Boxarts/"
    private const val URL_GBA = "https://thumbnails.libretro.com/Nintendo%20-%20Game%20Boy%20Advance/Named_Boxarts/"

    private data class IndexEntry(
        val fullUrl: String,
        val normalized: String,
        val tokens: Set<String>,
        val regionScore: Int,
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val indexMutexes = mapOf(
        ConsoleSystem.GB to Mutex(),
        ConsoleSystem.GBC to Mutex(),
        ConsoleSystem.GBA to Mutex(),
    )
    private val indexCache = ConcurrentHashMap<ConsoleSystem, List<IndexEntry>>()
    private val exactCache = ConcurrentHashMap<ConsoleSystem, Map<String, IndexEntry>>()
    private val failedMatches = ConcurrentHashMap<String, Long>()

    private fun cacheDir(context: Context): File =
        File(context.filesDir, "boxart").apply { mkdirs() }

    private fun customDir(context: Context): File =
        File(context.filesDir, "custom_covers").apply { mkdirs() }

    private fun indexFile(context: Context, system: ConsoleSystem): File =
        File(cacheDir(context), "libretro_index_${system.name.lowercase()}_v1.txt")

    private fun coverKey(rom: RomEntry): String {
        val base = rom.md5.ifBlank { rom.crc32.toString(16) }.ifBlank { rom.fileName.hashCode().toString() }
        return "${rom.consoleSystem.name.lowercase()}_${base.replace(Regex("[^a-zA-Z0-9_-]"), "_")}"
    }

    private fun baseUrlFor(system: ConsoleSystem): String = when (system) {
        ConsoleSystem.GB -> URL_GB
        ConsoleSystem.GBC -> URL_GBC
        ConsoleSystem.GBA -> URL_GBA
    }

    fun getCachedBoxArtPath(context: Context, rom: RomEntry): String? {
        val key = coverKey(rom)
        val custom = File(customDir(context), "$key.png")
        if (custom.isFile && custom.length() > 128L) return custom.absolutePath
        val cached = File(cacheDir(context), "$key.png")
        if (cached.isFile && cached.length() > 128L) return cached.absolutePath
        return null
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

    suspend fun fetchOrGetBoxArt(context: Context, rom: RomEntry, forceRefresh: Boolean = false): String? =
        withContext(Dispatchers.IO) {
            val key = coverKey(rom)
            if (!forceRefresh) {
                getCachedBoxArtPath(context, rom)?.let { return@withContext it }
                val lastFail = failedMatches[key]
                if (lastFail != null && (System.currentTimeMillis() - lastFail) < 10L * 60 * 1000) {
                    return@withContext null
                }
            } else {
                failedMatches.remove(key)
            }

            // 1. Ensure Libretro full directory index is loaded for this system (and fallback GB <-> GBC)
            val systemsToTry = when (rom.consoleSystem) {
                ConsoleSystem.GB -> listOf(ConsoleSystem.GB, ConsoleSystem.GBC)
                ConsoleSystem.GBC -> listOf(ConsoleSystem.GBC, ConsoleSystem.GB)
                ConsoleSystem.GBA -> listOf(ConsoleSystem.GBA)
            }

            val candidates = buildCandidates(rom)
            for (sys in systemsToTry) {
                val entries = ensureIndex(context, sys, forceRefresh)
                if (entries.isNotEmpty()) {
                    val match = findBestMatch(candidates, sys, entries)
                    if (match != null) {
                        val downloaded = downloadToCache(context, key, match.fullUrl)
                        if (downloaded != null) return@withContext downloaded
                    }
                }
            }

            // 2. Direct fallback URL probing if index download was unavailable
            val baseUrl = baseUrlFor(rom.consoleSystem)
            for (c in directProbeNames(rom)) {
                val encoded = URLEncoder.encode("$c.png", "UTF-8").replace("+", "%20")
                val downloaded = downloadToCache(context, key, "$baseUrl$encoded")
                if (downloaded != null) return@withContext downloaded
            }

            failedMatches[key] = System.currentTimeMillis()
            null
        }

    private suspend fun ensureIndex(context: Context, system: ConsoleSystem, forceRefresh: Boolean): List<IndexEntry> {
        if (!forceRefresh) {
            indexCache[system]?.let { if (it.isNotEmpty()) return it }
        }
        val mutex = indexMutexes[system] ?: return emptyList()
        return mutex.withLock {
            if (!forceRefresh) {
                indexCache[system]?.let { if (it.isNotEmpty()) return@withLock it }
            }
            val file = indexFile(context, system)
            val stale = !file.isFile || file.length() < 1024L ||
                (System.currentTimeMillis() - file.lastModified() > INDEX_MAX_AGE_MS)
            val rawText = if (!forceRefresh && !stale) {
                runCatching { file.readText() }.getOrNull()
            } else {
                downloadDirectoryIndex(baseUrlFor(system))?.also { txt ->
                    runCatching { file.writeText(txt) }
                } ?: runCatching { if (file.isFile) file.readText() else null }.getOrNull()
            }
            if (rawText.isNullOrBlank()) return@withLock emptyList()
            parseIndex(system, rawText)
        }
    }

    private fun downloadDirectoryIndex(baseUrl: String): String? {
        return runCatching {
            val req = Request.Builder()
                .url(baseUrl)
                .header("User-Agent", USER_AGENT)
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val html = resp.body?.string() ?: return@use null
                val matches = Regex("""href="([^"]+\.png)"""").findAll(html)
                    .map { it.groupValues[1] }
                    .filter { !it.contains("..") && !it.startsWith("/") }
                    .toList()
                if (matches.size < 20) null else matches.joinToString("\n")
            }
        }.onFailure { Log.w(TAG, "Index fetch failed for $baseUrl: ${it.message}") }.getOrNull()
    }

    private fun parseIndex(system: ConsoleSystem, raw: String): List<IndexEntry> {
        val baseUrl = baseUrlFor(system)
        val exact = HashMap<String, IndexEntry>()
        val entries = ArrayList<IndexEntry>(2048)
        raw.lineSequence().forEach { encoded ->
            val trimmed = encoded.trim()
            if (trimmed.isEmpty()) return@forEach
            val decoded = runCatching { URLDecoder.decode(trimmed, "UTF-8") }.getOrDefault(trimmed)
            val regionScore = when {
                decoded.contains("(USA", ignoreCase = true) -> 4
                decoded.contains("(World", ignoreCase = true) -> 3
                decoded.contains("(Europe", ignoreCase = true) -> 2
                decoded.contains("(Japan", ignoreCase = true) -> 1
                else -> 0
            }
            val clean = decoded.removeSuffix(".png").substringBefore(" (").substringBefore(" [")
            val norm = normalize(clean)
            if (norm.isNotEmpty()) {
                val toks = norm.split(' ').filter { it.isNotEmpty() }.toSet()
                val entry = IndexEntry(baseUrl + trimmed, norm, toks, regionScore)
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

    private fun findBestMatch(candidates: List<String>, system: ConsoleSystem, entries: List<IndexEntry>): IndexEntry? {
        val exactMap = exactCache[system] ?: emptyMap()
        // Pass 1: O(1) exact normalized title lookup
        for (c in candidates) {
            val norm = normalize(c)
            if (norm.isEmpty()) continue
            exactMap[norm]?.let { return it }
        }

        // Pass 2: Token Jaccard + containment fuzzy scoring (handles [MOD - M. RUS], editions, sequels cleanly)
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
                var score = jaccard * 0.75 + contain * 0.25
                if (e.normalized.startsWith(norm) || norm.startsWith(e.normalized)) {
                    score += 0.08
                }
                score += e.regionScore * 0.005
                if (score > bestScore) {
                    bestScore = score
                    best = e
                    if (jaccard >= 0.95 && e.regionScore >= 4) return best
                }
            }
        }
        return if (bestScore >= 0.62) best else null
    }

    private fun downloadToCache(context: Context, key: String, url: String): String? {
        return runCatching {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val bytes = resp.body?.bytes() ?: return@use null
                if (bytes.size < 256) return@use null
                val outFile = File(cacheDir(context), "$key.png")
                FileOutputStream(outFile).use { it.write(bytes) }
                outFile.absolutePath
            }
        }.getOrNull()
    }

    private fun buildCandidates(rom: RomEntry): List<String> {
        val raw = rom.fileName.substringBeforeLast('.')
        val stripped = raw
            .replace(Regex("\\[[^\\]]*\\]"), " ")
            .replace(Regex("\\([^\\)]*\\)"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val res = LinkedHashSet<String>()
        if (stripped.isNotBlank()) {
            res.add(stripped)
            // Handle "Title, The" <-> "The Title"
            if (stripped.startsWith("The ", ignoreCase = true)) {
                res.add(stripped.drop(4) + ", The")
            }
            // Strip version suffixes like "v1.1" or "Rev 1"
            val noVer = stripped.replace(Regex("\\b(v\\d[\\d.]*|rev\\s*\\d+|rus|eng|mod)\\b", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("\\s+"), " ").trim()
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
            "$clean (USA)",
            "$clean (USA, Europe)",
            "$clean (Europe)",
            "$clean (World)",
            "$clean (Japan)",
        ).distinct().filter { it.isNotBlank() }
    }

    private fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
        return decomposed
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace("&", " and ")
            .replace(Regex("\\([^\\)]*\\)|\\[[^\\]]*\\]"), " ")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }
}
