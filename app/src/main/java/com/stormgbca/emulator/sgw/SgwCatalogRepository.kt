package com.stormgbca.emulator.sgw

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.stormgbca.emulator.data.BoxArtRepository
import com.stormgbca.emulator.data.GbcaPrefs
import com.stormgbca.emulator.data.RomRepository
import com.stormgbca.emulator.model.ConsoleSystem
import com.stormgbca.emulator.model.RomEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URLDecoder
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipInputStream

object SgwCatalogRepository {
    private const val TAG = "SgwCatalogRepo"
    private const val PRIMARY_HOST = "https://stormgamesworld.ru"
    private const val FALLBACK_HOST = "https://storm-games.world"
    private const val USER_AGENT = "STORM-GBCA/1.9.0 (Android)"
    private const val CACHE_FILE_NAME = "sgw_catalog_v1.json"
    private const val CACHE_TTL_MS = 15L * 60L * 1000L

    private const val DEFAULT_GB_ID = 190
    private const val DEFAULT_GBC_ID = 192
    private const val DEFAULT_GBA_ID = 191

    private val ROM_EXTENSIONS = setOf("gb", "gbc", "gba", "sgb")
    private val HOSTS = listOf(PRIMARY_HOST, FALLBACK_HOST)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val fetchMutex = Mutex()
    private val activeDownloadCall = AtomicReference<Call?>(null)
    private val counterScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.MINUTES)
            .followRedirects(true)
            .build()
    }

    @Serializable
    private data class CachedCatalogPayload(
        val timestampMs: Long = 0L,
        val baseHost: String = PRIMARY_HOST,
        val items: List<SgwCatalogGame> = emptyList(),
    )

    @Volatile
    private var memoryCache: CachedCatalogPayload? = null

    fun cancelActiveDownload() {
        activeDownloadCall.getAndSet(null)?.cancel()
    }

    fun getCachedCatalog(context: Context): List<SgwCatalogGame> {
        memoryCache?.let { if (it.items.isNotEmpty()) return it.items }
        val file = File(context.cacheDir, CACHE_FILE_NAME)
        if (!file.isFile) return emptyList()
        return runCatching {
            val payload = json.decodeFromString<CachedCatalogPayload>(file.readText())
            memoryCache = payload
            payload.items
        }.getOrDefault(emptyList())
    }

    suspend fun fetchCatalog(
        context: Context,
        forceRefresh: Boolean = false,
    ): Result<List<SgwCatalogGame>> = withContext(Dispatchers.IO) {
        fetchMutex.withLock {
            val now = System.currentTimeMillis()
            if (!forceRefresh) {
                memoryCache?.let { cached ->
                    if (cached.items.isNotEmpty() && (now - cached.timestampMs) < CACHE_TTL_MS) {
                        return@withLock Result.success(cached.items)
                    }
                }
                val cacheFile = File(context.cacheDir, CACHE_FILE_NAME)
                if (cacheFile.isFile) {
                    val disk = runCatching { json.decodeFromString<CachedCatalogPayload>(cacheFile.readText()) }.getOrNull()
                    if (disk != null && disk.items.isNotEmpty() && (now - disk.timestampMs) < CACHE_TTL_MS) {
                        memoryCache = disk
                        return@withLock Result.success(disk.items)
                    }
                }
            }

            var lastError: Throwable? = null
            for (host in HOSTS) {
                val res = runCatching { fetchFromHost(host) }
                val list = res.getOrNull()
                if (list != null && list.isNotEmpty()) {
                    val payload = CachedCatalogPayload(
                        timestampMs = System.currentTimeMillis(),
                        baseHost = host,
                        items = list,
                    )
                    memoryCache = payload
                    runCatching {
                        File(context.cacheDir, CACHE_FILE_NAME).writeText(json.encodeToString(payload))
                    }
                    return@withLock Result.success(list)
                }
                lastError = res.exceptionOrNull()
            }

            val fallbackCached = getCachedCatalog(context)
            if (fallbackCached.isNotEmpty()) {
                Result.success(fallbackCached)
            } else {
                Result.failure(lastError ?: IllegalStateException("Failed to fetch STORM GAMES WORLD catalog"))
            }
        }
    }

    private suspend fun fetchFromHost(host: String): List<SgwCatalogGame> = coroutineScope {
        val platformIds = resolvePlatformIds(host)
        val gbId = platformIds[ConsoleSystem.GB] ?: DEFAULT_GB_ID
        val gbcId = platformIds[ConsoleSystem.GBC] ?: DEFAULT_GBC_ID
        val gbaId = platformIds[ConsoleSystem.GBA] ?: DEFAULT_GBA_ID
        val idToSystem = mapOf(
            gbId to ConsoleSystem.GB,
            gbcId to ConsoleSystem.GBC,
            gbaId to ConsoleSystem.GBA,
        )

        val gbTask = async { fetchPlatformGames(host, gbId, ConsoleSystem.GB) }
        val gbcTask = async { fetchPlatformGames(host, gbcId, ConsoleSystem.GBC) }
        val gbaTask = async { fetchPlatformGames(host, gbaId, ConsoleSystem.GBA) }
        val trTask = async { fetchTranslations(host, idToSystem) }

        val gamesById = LinkedHashMap<Int, SgwCatalogGame>(512)
        for (item in gbTask.await() + gbcTask.await() + gbaTask.await()) {
            gamesById[item.gameId] = item
        }

        val translations = trTask.await()
        for (tr in translations) {
            val existing = gamesById[tr.gameId]
            if (existing != null) {
                gamesById[tr.gameId] = existing.copy(
                    releaseId = tr.releaseId,
                    title = tr.title.ifBlank { existing.title },
                    finalTitle = tr.finalTitle.ifBlank { existing.finalTitle },
                    version = tr.version.ifBlank { existing.version },
                    internalBuild = tr.internalBuild,
                    sizeLabel = tr.sizeLabel.ifBlank { existing.sizeLabel },
                    sizeBytes = if (tr.sizeBytes > 0L) tr.sizeBytes else existing.sizeBytes,
                    coverUrl = tr.coverUrl.ifBlank { existing.coverUrl },
                    changelog = tr.changelog.ifBlank { existing.changelog },
                    regions = tr.regions.ifEmpty { existing.regions },
                    textLangs = (existing.textLangs + tr.textLangs + "ru").distinct(),
                    isRussian = true,
                    isFromTranslations = true,
                    hasRetroAchievements = existing.hasRetroAchievements || tr.hasRetroAchievements,
                    author = tr.author.ifBlank { existing.author },
                    md5 = tr.md5.ifBlank { existing.md5 },
                    crc = tr.crc.ifBlank { existing.crc },
                    fileName = tr.fileName.ifBlank { existing.fileName },
                    downloadPathOrUrl = tr.downloadPathOrUrl.ifBlank { existing.downloadPathOrUrl },
                    downloads = maxOf(existing.downloads, tr.downloads),
                )
            } else {
                gamesById[tr.gameId] = tr
            }
        }

        gamesById.values.sortedWith(
            compareByDescending<SgwCatalogGame> { it.isFromTranslations }
                .thenByDescending { it.downloads }
                .thenBy { it.finalTitle.lowercase(Locale.ROOT) }
        )
    }

    private fun resolvePlatformIds(host: String): Map<ConsoleSystem, Int> {
        val result = mutableMapOf(
            ConsoleSystem.GB to DEFAULT_GB_ID,
            ConsoleSystem.GBC to DEFAULT_GBC_ID,
            ConsoleSystem.GBA to DEFAULT_GBA_ID,
        )
        val body = executeGetString("$host/api/platforms?all=1") ?: return result
        val arr = extractJsonArray(body, "platforms", "data") ?: return result
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optInt("id", 0)
            if (id <= 0) continue
            val name = obj.optString("name", "").trim().lowercase(Locale.ROOT)
            when {
                name == "nintendo game boy" || name == "game boy" -> result[ConsoleSystem.GB] = id
                name == "nintendo game boy color" || name == "game boy color" -> result[ConsoleSystem.GBC] = id
                name == "nintendo game boy advance" || name == "game boy advance" -> result[ConsoleSystem.GBA] = id
            }
        }
        return result
    }

    private fun fetchPlatformGames(
        host: String,
        platformId: Int,
        system: ConsoleSystem,
    ): List<SgwCatalogGame> {
        val body = executeGetString("$host/api/games?platformId=$platformId") ?: return emptyList()
        val arr = extractJsonArray(body, "data", "games") ?: return emptyList()
        val out = ArrayList<SgwCatalogGame>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optInt("id", 0)
            if (id <= 0) continue
            val fileExists = obj.optBoolean("fileExists", true)
            if (!fileExists) continue

            val title = cleanJsonStr(obj, "title")
            val finalTitle = cleanJsonStr(obj, "finalTitle")
                .ifBlank { cleanJsonStr(obj, "correctTitle") }
                .ifBlank { cleanJsonStr(obj, "correctTitle2") }
                .ifBlank { title }
            if (finalTitle.isBlank()) continue

            val version = cleanVersion(cleanJsonStr(obj, "version")).ifBlank { "1.0" }
            val sizeBytes = obj.optLong("fileSizeBytes", 0L).coerceAtLeast(0L)
            val sizeLabel = cleanJsonStr(obj, "size").ifBlank { formatByteSize(sizeBytes) }
            val coverUrl = resolveUrl(host, cleanJsonStr(obj, "cover"))
            val description = cleanJsonStr(obj, "description")
            val textLangs = parseStringArray(obj.optJSONArray("textLangs"))
            val regions = parseStringArray(obj.optJSONArray("regions"))
            val hasRa = obj.optBoolean("retroachievements", false)
            val downloads = obj.optInt("downloads", 0)
            val rating = obj.optDouble("averageRating", 0.0).let { if (it.isNaN()) 0.0 else it }
            val md5 = cleanJsonStr(obj, "md5")
            val crc = cleanJsonStr(obj, "crc")

            val modsArr = obj.optJSONArray("mods")
            val modHints = buildString {
                if (modsArr != null) {
                    for (m in 0 until modsArr.length()) {
                        val mo = modsArr.optJSONObject(m) ?: continue
                        append(' ').append(mo.optString("name", ""))
                    }
                }
            }
            val isRus = hasRussianMarker(textLangs, regions, finalTitle, title, modHints)

            out.add(
                SgwCatalogGame(
                    gameId = id,
                    releaseId = 0,
                    platform = system,
                    title = title.ifBlank { finalTitle },
                    finalTitle = finalTitle,
                    version = version,
                    internalBuild = 0,
                    sizeLabel = sizeLabel,
                    sizeBytes = sizeBytes,
                    coverUrl = coverUrl,
                    description = description,
                    changelog = "",
                    regions = regions,
                    textLangs = textLangs,
                    isRussian = isRus,
                    isFromTranslations = false,
                    hasRetroAchievements = hasRa,
                    author = "",
                    md5 = md5,
                    crc = crc,
                    fileName = "",
                    downloadPathOrUrl = "/api/games/$id/download",
                    downloads = downloads,
                    rating = rating,
                )
            )
        }
        return out
    }

    private fun fetchTranslations(
        host: String,
        idToSystem: Map<Int, ConsoleSystem>,
    ): List<SgwCatalogGame> {
        val body = executeGetString("$host/api/translations?mode=overview") ?: return emptyList()
        val arr = extractJsonArray(body, "translatedGames", "data") ?: return emptyList()
        val out = ArrayList<SgwCatalogGame>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val platformId = obj.optInt("platformId", 0)
            val system = idToSystem[platformId] ?: continue
            val gameId = obj.optInt("id", 0)
            if (gameId <= 0) continue

            val relArr = obj.optJSONArray("translationReleases") ?: continue
            var pickedRel: JSONObject? = null
            for (r in 0 until relArr.length()) {
                val ro = relArr.optJSONObject(r) ?: continue
                if (ro.optString("fileUrl", "").isBlank()) continue
                if (ro.optBoolean("isCurrent", false)) {
                    pickedRel = ro
                    break
                }
                if (pickedRel == null) pickedRel = ro
            }
            val rel = pickedRel ?: continue

            val fileUrl = cleanJsonStr(rel, "fileUrl")
            if (fileUrl.isBlank()) continue

            val title = cleanJsonStr(obj, "title")
            val finalTitle = cleanJsonStr(obj, "finalTitle")
                .ifBlank { cleanJsonStr(obj, "correctTitle") }
                .ifBlank { cleanJsonStr(rel, "title") }
                .ifBlank { title }
            if (finalTitle.isBlank()) continue

            val releaseId = rel.optInt("id", 0)
            val version = cleanVersion(cleanJsonStr(rel, "version")).ifBlank { "1.0" }
            val internalBuild = rel.optInt("internalBuild", 0)
            val fileName = cleanJsonStr(rel, "fileName")
            val fileSize = rel.optLong("fileSize", 0L).coerceAtLeast(0L)
            val sizeLabel = formatByteSize(fileSize)
            val author = cleanJsonStr(rel, "author").ifBlank { "STORM TEAM" }
            val changelogRaw = cleanJsonStr(rel, "changelog")
            val fixesRaw = cleanJsonStr(rel, "fixes")
            val combinedChangelog = listOf(changelogRaw, fixesRaw)
                .filter { it.isNotBlank() }
                .joinToString("\n\n")
            val coverUrl = resolveUrl(host, cleanJsonStr(obj, "cover"))
            val regions = parseStringArray(obj.optJSONArray("regions"))
            val textLangs = (parseStringArray(obj.optJSONArray("textLangs")) + "ru").distinct()
            val hasRa = obj.optBoolean("retroachievements", false)
            val md5 = cleanJsonStr(obj, "md5")
            val crc = cleanJsonStr(obj, "crc")
            val downloads = rel.optInt("downloads", 0)

            out.add(
                SgwCatalogGame(
                    gameId = gameId,
                    releaseId = releaseId,
                    platform = system,
                    title = title.ifBlank { finalTitle },
                    finalTitle = finalTitle,
                    version = version,
                    internalBuild = internalBuild,
                    sizeLabel = sizeLabel,
                    sizeBytes = fileSize,
                    coverUrl = coverUrl,
                    description = "",
                    changelog = combinedChangelog,
                    regions = regions,
                    textLangs = textLangs,
                    isRussian = true,
                    isFromTranslations = true,
                    hasRetroAchievements = hasRa,
                    author = author,
                    md5 = md5,
                    crc = crc,
                    fileName = fileName,
                    downloadPathOrUrl = fileUrl,
                    downloads = downloads,
                    rating = 0.0,
                )
            )
        }
        return out
    }

    fun findUpdatesForInstalled(
        installedRoms: List<RomEntry>,
        catalog: List<SgwCatalogGame>,
    ): List<SgwGameUpdateInfo> {
        if (installedRoms.isEmpty() || catalog.isEmpty()) return emptyList()
        val metaMap = GbcaPrefs.getSgwInstalledMeta()
        val catalogByGameId = catalog.associateBy { it.gameId }
        val catalogByPlatform = catalog.groupBy { it.platform }
        val updates = ArrayList<SgwGameUpdateInfo>()

        for (rom in installedRoms) {
            val system = rom.consoleSystem
            val localMeta = metaMap[rom.uriString]
            val localMd5 = rom.md5.trim().lowercase(Locale.ROOT)
            val localCrc = rom.crc32.toString(16).lowercase(Locale.ROOT).padStart(8, '0')
            val isLocalRussian = (localMeta?.isRussian == true) ||
                rom.region.equals("Russia", ignoreCase = true) ||
                hasRussianFilenameMarker(rom.fileName) ||
                rom.softPatchUri.isNotBlank()

            val localTitleRaw = rom.fileName.substringBeforeLast('.').ifBlank { rom.internalTitle }
            val localCanonical = canonicalKey(localTitleRaw)
                .ifBlank { canonicalKey(rom.internalTitle) }
            val localRegion = normalizeRegionTag(rom.region, rom.fileName)

            val matchedRemote: SgwCatalogGame? = when {
                localMeta != null && localMeta.gameId > 0 -> {
                    catalogByGameId[localMeta.gameId]?.takeIf { it.platform == system }
                }
                else -> null
            } ?: run {
                if (localCanonical.isBlank()) null
                else {
                    val samePlat = catalogByPlatform[system].orEmpty()
                    val exactMatches = samePlat.filter { g ->
                        canonicalKey(g.finalTitle) == localCanonical ||
                            canonicalKey(g.title) == localCanonical
                    }
                    pickBestRemoteCandidate(exactMatches, localRegion, isLocalRussian)
                }
            }

            if (matchedRemote == null) continue

            // Skip if exact ROM hash already matches
            if (localMd5.isNotBlank() && matchedRemote.md5.isNotBlank() &&
                localMd5 == matchedRemote.md5.trim().lowercase(Locale.ROOT)
            ) {
                continue
            }
            if (matchedRemote.crc.isNotBlank() &&
                localCrc == matchedRemote.crc.trim().lowercase(Locale.ROOT).padStart(8, '0')
            ) {
                continue
            }

            // Skip if SgwInstalledMeta already records this exact release/version/build
            val remoteVer = cleanVersion(matchedRemote.version).ifBlank { "1.0" }
            if (localMeta != null && localMeta.gameId == matchedRemote.gameId) {
                val verCmp = compareVersions(remoteVer, cleanVersion(localMeta.version).ifBlank { "1.0" })
                if (matchedRemote.releaseId > 0 &&
                    localMeta.releaseId == matchedRemote.releaseId &&
                    verCmp <= 0 &&
                    localMeta.internalBuild >= matchedRemote.internalBuild
                ) {
                    continue
                }
                if (matchedRemote.releaseId == 0 &&
                    verCmp <= 0 &&
                    localMeta.internalBuild >= matchedRemote.internalBuild
                ) {
                    continue
                }
            }

            val localVer = localMeta?.version?.let { cleanVersion(it) }?.takeIf { it.isNotBlank() }
                ?: extractVersionFromFileName(rom.fileName)
            val localBuild = localMeta?.internalBuild ?: 0

            if (!isLocalRussian && matchedRemote.isRussian) {
                updates.add(
                    SgwGameUpdateInfo(
                        localRom = rom,
                        remoteGame = matchedRemote,
                        kind = SgwUpdateKind.TRANSLATION_AVAILABLE,
                        currentVersion = localVer,
                        remoteVersion = remoteVer,
                    )
                )
            } else {
                val cmp = compareVersions(remoteVer, localVer)
                val newerBuild = cmp == 0 && matchedRemote.internalBuild > localBuild
                if (cmp > 0 || newerBuild) {
                    updates.add(
                        SgwGameUpdateInfo(
                            localRom = rom,
                            remoteGame = matchedRemote,
                            kind = SgwUpdateKind.VERSION_UPDATE,
                            currentVersion = localVer,
                            remoteVersion = remoteVer,
                        )
                    )
                }
            }
        }
        return updates
    }

    suspend fun downloadOrUpdateRom(
        ctx: Context,
        remoteGame: SgwCatalogGame,
        replacingRom: RomEntry?,
        targetTreeUri: Uri?,
        onProgress: (SgwDownloadState) -> Unit,
    ): Result<RomEntry> = withContext(Dispatchers.IO) {
        val dlKey = "${remoteGame.gameId}_${remoteGame.releaseId}"
        val dlTitle = remoteGame.finalTitle.ifBlank { remoteGame.title }
        val dlDir = File(ctx.cacheDir, "sgw_dl").apply { mkdirs() }
        val tempDownload = File(dlDir, "dl_${dlKey}_${System.currentTimeMillis()}.tmp")
        var extractedTempFile: File? = null

        try {
            if (remoteGame.isFromTranslations && remoteGame.releaseId > 0) {
                fireTranslationDownloadCounter(remoteGame.releaseId)
            }

            val urls = buildDownloadUrls(remoteGame.downloadPathOrUrl)
            if (urls.isEmpty()) error("Missing download URL")

            var headerFileName: String? = null
            var downloadedOk = false
            var lastFailure: Throwable? = null

            for (url in urls) {
                currentCoroutineContext().ensureActive()
                val attempt = runCatching {
                    val req = Request.Builder()
                        .url(url)
                        .header("User-Agent", USER_AGENT)
                        .build()
                    val call = client.newCall(req)
                    activeDownloadCall.set(call)
                    val reg = currentCoroutineContext()[Job]?.invokeOnCompletion {
                        call.cancel()
                    }
                    try {
                        call.execute().use { resp ->
                            if (!resp.isSuccessful) error("HTTP ${resp.code}")
                            val contentType = resp.header("Content-Type").orEmpty().lowercase(Locale.ROOT)
                            if (contentType.contains("text/html")) {
                                error("Server returned HTML page instead of ROM")
                            }
                            headerFileName = parseFileNameFromHeaders(
                                xFilename = resp.header("X-Filename"),
                                contentDisposition = resp.header("Content-Disposition"),
                            )
                            val body = resp.body ?: error("Empty response body")
                            val totalBytes = body.contentLength().takeIf { it > 0L } ?: remoteGame.sizeBytes
                            val buf = ByteArray(16384)
                            var bytesRead = 0L
                            var lastReportMs = 0L

                            onProgress(
                                SgwDownloadState(
                                    active = true,
                                    key = dlKey,
                                    title = dlTitle,
                                    bytesRead = 0L,
                                    totalBytes = totalBytes,
                                    percent = 0,
                                    statusText = "0%",
                                )
                            )

                            body.byteStream().use { input ->
                                FileOutputStream(tempDownload).use { output ->
                                    while (true) {
                                        currentCoroutineContext().ensureActive()
                                        val read = input.read(buf)
                                        if (read <= 0) break
                                        if (bytesRead == 0L && looksLikeHtmlHeader(buf, read)) {
                                            error("Server returned HTML error page")
                                        }
                                        output.write(buf, 0, read)
                                        bytesRead += read
                                        val now = System.currentTimeMillis()
                                        if (now - lastReportMs >= 200L) {
                                            lastReportMs = now
                                            val pct = if (totalBytes > 0L) {
                                                ((bytesRead * 100L) / totalBytes).toInt().coerceIn(0, 99)
                                            } else 0
                                            onProgress(
                                                SgwDownloadState(
                                                    active = true,
                                                    key = dlKey,
                                                    title = dlTitle,
                                                    bytesRead = bytesRead,
                                                    totalBytes = totalBytes,
                                                    percent = pct,
                                                    statusText = if (totalBytes > 0L) {
                                                        "${formatByteSize(bytesRead)} / ${formatByteSize(totalBytes)}"
                                                    } else {
                                                        formatByteSize(bytesRead)
                                                    },
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } finally {
                        reg?.dispose()
                        activeDownloadCall.set(null)
                    }
                }
                if (attempt.isSuccess) {
                    downloadedOk = true
                    break
                } else {
                    lastFailure = attempt.exceptionOrNull()
                    if (lastFailure is kotlinx.coroutines.CancellationException) throw lastFailure
                    tempDownload.delete()
                }
            }

            if (!downloadedOk || !tempDownload.isFile || tempDownload.length() <= 0L) {
                throw (lastFailure ?: IllegalStateException("Download failed"))
            }

            currentCoroutineContext().ensureActive()

            // Detect ZIP magic PK\x03\x04 and safely extract first ROM file with zip-slip guard
            val isZip = isZipMagic(tempDownload)
            val sourceRomFile: File
            var resolvedName: String
            if (isZip) {
                val extracted = extractFirstRomFromZip(tempDownload, dlDir)
                    ?: error("No GB/GBC/GBA/SGB ROM found inside ZIP archive")
                extractedTempFile = extracted.first
                sourceRomFile = extracted.first
                resolvedName = extracted.second
            } else {
                sourceRomFile = tempDownload
                resolvedName = headerFileName
                    ?.takeIf { it.isNotBlank() }
                    ?: remoteGame.fileName.takeIf { it.isNotBlank() }
                    ?: buildDefaultRomFileName(remoteGame)
            }

            val finalFileName = sanitizeRomFileName(resolvedName, remoteGame.platform)

            // Write final ROM into target folder
            val writtenUri = writeRomToTargetFolder(
                ctx = ctx,
                sourceFile = sourceRomFile,
                finalFileName = finalFileName,
                replacingRom = replacingRom,
                targetTreeUri = targetTreeUri,
            )

            // Verify written ROM via RomRepository.inspectUri
            val inspectedBase = RomRepository.inspectUri(ctx, writtenUri, finalFileName)
                ?: run {
                    deleteUriQuietly(ctx, writtenUri)
                    error("Downloaded file is not a valid GB/GBC/GBA ROM")
                }

            val newRom = if (replacingRom != null) {
                inspectedBase.copy(
                    isFavorite = replacingRom.isFavorite,
                    coreOverride = replacingRom.coreOverride,
                    softPatchUri = if (remoteGame.isRussian) "" else replacingRom.softPatchUri,
                    lastPlayedEpochMs = replacingRom.lastPlayedEpochMs,
                    totalPlaySeconds = replacingRom.totalPlaySeconds,
                )
            } else {
                inspectedBase
            }

            // 100% Save, State, Cheats & Custom Cover Migration when replacing an existing ROM
            if (replacingRom != null) {
                migrateRomData(ctx, replacingRom, newRom)
            }

            // Update cached_roms_json BEFORE any rescan
            val existingCached = GbcaPrefs.getCachedRoms().toMutableList()
            if (replacingRom != null) {
                val idx = existingCached.indexOfFirst { it.uriString == replacingRom.uriString }
                if (idx >= 0) {
                    existingCached[idx] = newRom
                } else {
                    existingCached.add(newRom)
                }
                GbcaPrefs.removeSgwInstalledMeta(replacingRom.uriString)
            } else {
                val idx = existingCached.indexOfFirst { it.uriString == newRom.uriString }
                if (idx >= 0) {
                    existingCached[idx] = newRom
                } else {
                    existingCached.add(newRom)
                }
            }
            GbcaPrefs.saveCachedRoms(existingCached.distinctBy { it.uriString })

            // Record SGW metadata for future update checks
            GbcaPrefs.saveSgwInstalledMeta(
                SgwInstalledMeta(
                    uriString = newRom.uriString,
                    gameId = remoteGame.gameId,
                    releaseId = remoteGame.releaseId,
                    version = cleanVersion(remoteGame.version).ifBlank { "1.0" },
                    internalBuild = remoteGame.internalBuild,
                    md5 = newRom.md5.ifBlank { remoteGame.md5 },
                    isRussian = remoteGame.isRussian,
                )
            )

            // Delete old ROM ONLY AFTER new ROM is written, verified, and migrated
            if (replacingRom != null && replacingRom.uriString != newRom.uriString) {
                deleteUriQuietly(ctx, Uri.parse(replacingRom.uriString))
            }

            onProgress(SgwDownloadState(active = false))
            Result.success(newRom)
        } catch (t: Throwable) {
            activeDownloadCall.set(null)
            if (t is kotlinx.coroutines.CancellationException) {
                onProgress(SgwDownloadState(active = false))
            } else {
                Log.w(TAG, "Download/update failed: ${t.message}", t)
                onProgress(SgwDownloadState(active = false, error = t.message ?: "Download failed"))
            }
            Result.failure(t)
        } finally {
            tempDownload.delete()
            extractedTempFile?.delete()
        }
    }

    private fun migrateRomData(ctx: Context, oldRom: RomEntry, newRom: RomEntry) {
        val oldKey = RomRepository.getRomStorageKey(oldRom)
        val newKey = RomRepository.getRomStorageKey(newRom)
        if (oldKey != newKey) {
            val saveDir = RomRepository.getSaveDir(ctx)
            val oldSave = File(saveDir, "$oldKey.sav")
            val newSave = File(saveDir, "$newKey.sav")
            if (oldSave.isFile && oldSave.length() > 0L) {
                runCatching { oldSave.copyTo(newSave, overwrite = true) }
            }

            val stateDir = RomRepository.getStateDir(ctx)
            for (slot in 0..10) {
                val oldSs = File(stateDir, "${oldKey}_slot$slot.ss")
                val newSs = File(stateDir, "${newKey}_slot$slot.ss")
                if (oldSs.isFile) runCatching { oldSs.copyTo(newSs, overwrite = true) }

                val oldPng = File(stateDir, "${oldKey}_slot$slot.png")
                val newPng = File(stateDir, "${newKey}_slot$slot.png")
                if (oldPng.isFile) runCatching { oldPng.copyTo(newPng, overwrite = true) }

                val oldRaProg = File(stateDir, "${oldKey}_slot$slot.ss.raprog")
                val newRaProg = File(stateDir, "${newKey}_slot$slot.ss.raprog")
                if (oldRaProg.isFile) runCatching { oldRaProg.copyTo(newRaProg, overwrite = true) }
            }

            val oldCheats = GbcaPrefs.getCheatsForRom(oldKey)
            if (oldCheats.isNotEmpty()) {
                GbcaPrefs.saveCheatsForRom(newKey, oldCheats)
            }

            val oldCover = BoxArtRepository.getCustomCoverFile(ctx, oldRom)
            val newCover = BoxArtRepository.getCustomCoverFile(ctx, newRom)
            if (oldCover.isFile && oldCover.length() > 128L && oldCover.absolutePath != newCover.absolutePath) {
                runCatching { oldCover.copyTo(newCover, overwrite = true) }
            }
        }
    }

    private fun writeRomToTargetFolder(
        ctx: Context,
        sourceFile: File,
        finalFileName: String,
        replacingRom: RomEntry?,
        targetTreeUri: Uri?,
    ): Uri {
        // 1. If replacing a file:// ROM, write into the same local directory
        if (replacingRom != null) {
            val oldUri = runCatching { Uri.parse(replacingRom.uriString) }.getOrNull()
            if (oldUri?.scheme == "file") {
                val parentDir = oldUri.path?.let { File(it).parentFile }
                if (parentDir != null && (parentDir.isDirectory || parentDir.mkdirs())) {
                    val outFile = resolveUniqueLocalFile(parentDir, finalFileName, oldUri.path)
                    sourceFile.copyTo(outFile, overwrite = true)
                    return Uri.fromFile(outFile)
                }
            } else if (oldUri?.scheme == "content") {
                val safParent = findSafParentDirectory(ctx, oldUri)
                if (safParent != null) {
                    return writeToSafDir(ctx, sourceFile, safParent, finalFileName, replacingUri = oldUri)
                }
            }
        }

        // 2. Explicit targetTreeUri
        if (targetTreeUri != null) {
            if (targetTreeUri.scheme == "file") {
                val dir = targetTreeUri.path?.let { File(it) }
                if (dir != null && (dir.isDirectory || dir.mkdirs())) {
                    val outFile = File(dir, finalFileName)
                    sourceFile.copyTo(outFile, overwrite = true)
                    return Uri.fromFile(outFile)
                }
            } else {
                val treeDoc = DocumentFile.fromTreeUri(ctx, targetTreeUri)
                if (treeDoc != null && treeDoc.exists() && treeDoc.isDirectory) {
                    return writeToSafDir(ctx, sourceFile, treeDoc, finalFileName, replacingUri = null)
                }
            }
        }

        // 3. First registered folder in GbcaPrefs
        for (folderStr in GbcaPrefs.getRomFoldersList()) {
            val uri = runCatching { Uri.parse(folderStr) }.getOrNull() ?: continue
            if (uri.scheme == "file") {
                val dir = uri.path?.let { File(it) }
                if (dir != null && (dir.isDirectory || dir.mkdirs())) {
                    val outFile = File(dir, finalFileName)
                    sourceFile.copyTo(outFile, overwrite = true)
                    return Uri.fromFile(outFile)
                }
            } else {
                val treeDoc = DocumentFile.fromTreeUri(ctx, uri)
                if (treeDoc != null && treeDoc.exists() && treeDoc.isDirectory) {
                    return writeToSafDir(ctx, sourceFile, treeDoc, finalFileName, replacingUri = null)
                }
            }
        }

        // 4. Fallback app-local roms directory
        val fallbackDir = RomRepository.getDefaultFallbackRomsDir(ctx)
        val outFile = File(fallbackDir, finalFileName)
        sourceFile.copyTo(outFile, overwrite = true)
        return Uri.fromFile(outFile)
    }

    private fun resolveUniqueLocalFile(dir: File, fileName: String, oldPath: String?): File {
        val candidate = File(dir, fileName)
        if (oldPath == null || candidate.absolutePath != oldPath) {
            return candidate
        }
        val base = fileName.substringBeforeLast('.')
        val ext = fileName.substringAfterLast('.', "gba")
        return File(dir, "$base.updated.$ext")
    }

    private fun writeToSafDir(
        ctx: Context,
        sourceFile: File,
        dir: DocumentFile,
        finalFileName: String,
        replacingUri: Uri?,
    ): Uri {
        val existing = dir.findFile(finalFileName)
        val targetDoc = if (existing != null && existing.uri != replacingUri) {
            existing
        } else {
            val createName = if (existing != null && existing.uri == replacingUri) {
                val base = finalFileName.substringBeforeLast('.')
                val ext = finalFileName.substringAfterLast('.', "gba")
                "$base (new).$ext"
            } else {
                finalFileName
            }
            dir.createFile("application/octet-stream", createName)
                ?: error("Cannot create file in selected ROM folder")
        }
        ctx.contentResolver.openOutputStream(targetDoc.uri, "wt")?.use { out ->
            FileInputStream(sourceFile).use { input -> input.copyTo(out) }
        } ?: error("Cannot open output stream for ${targetDoc.uri}")
        return targetDoc.uri
    }

    private fun findSafParentDirectory(ctx: Context, childUri: Uri): DocumentFile? {
        val childId = runCatching { DocumentsContract.getDocumentId(childUri) }.getOrNull()
        for (folderStr in GbcaPrefs.getRomFoldersList()) {
            val treeUri = runCatching { Uri.parse(folderStr) }.getOrNull() ?: continue
            val rootDoc = DocumentFile.fromTreeUri(ctx, treeUri) ?: continue
            if (!rootDoc.exists() || !rootDoc.isDirectory) continue
            val found = findParentInTree(rootDoc, childUri, childId, depth = 0)
            if (found != null) return found
        }
        return null
    }

    private fun findParentInTree(
        dir: DocumentFile,
        targetUri: Uri,
        targetDocId: String?,
        depth: Int,
    ): DocumentFile? {
        if (depth > 8) return null
        val children = dir.listFiles()
        for (child in children) {
            if (child.isDirectory) {
                findParentInTree(child, targetUri, targetDocId, depth + 1)?.let { return it }
            } else {
                if (child.uri == targetUri) return dir
                if (targetDocId != null) {
                    val cId = runCatching { DocumentsContract.getDocumentId(child.uri) }.getOrNull()
                    if (cId == targetDocId) return dir
                }
            }
        }
        return null
    }

    private fun deleteUriQuietly(ctx: Context, uri: Uri) {
        runCatching {
            if (uri.scheme == "file") {
                uri.path?.let { File(it).delete() }
            } else {
                val singleDeleted = DocumentFile.fromSingleUri(ctx, uri)?.delete() == true
                if (!singleDeleted) {
                    val parent = findSafParentDirectory(ctx, uri)
                    val targetId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
                    parent?.listFiles()?.firstOrNull { c ->
                        c.uri == uri || (targetId != null && runCatching { DocumentsContract.getDocumentId(c.uri) }.getOrNull() == targetId)
                    }?.delete()
                }
            }
        }
    }

    private fun fireTranslationDownloadCounter(releaseId: Int) {
        val host = memoryCache?.baseHost ?: PRIMARY_HOST
        counterScope.launch {
            runCatching {
                val req = Request.Builder()
                    .url("$host/api/translations/releases?action=download&id=$releaseId")
                    .header("User-Agent", USER_AGENT)
                    .build()
                client.newCall(req).execute().close()
            }
        }
    }

    private fun executeGetString(url: String): String? {
        return runCatching {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.string()
            }
        }.getOrNull()
    }

    private fun extractJsonArray(raw: String, vararg keys: String): JSONArray? {
        val trimmed = raw.trim()
        if (trimmed.startsWith("[")) {
            return runCatching { JSONArray(trimmed) }.getOrNull()
        }
        val obj = runCatching { JSONObject(trimmed) }.getOrNull() ?: return null
        for (k in keys) {
            obj.optJSONArray(k)?.let { return it }
        }
        return null
    }

    private fun parseStringArray(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val list = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            val s = arr.optString(i, "").trim()
            if (s.isNotEmpty() && !s.equals("null", ignoreCase = true)) {
                list.add(s)
            }
        }
        return list
    }

    private fun cleanJsonStr(obj: JSONObject, key: String): String {
        if (obj.isNull(key)) return ""
        val s = obj.optString(key, "").trim()
        return if (s.equals("null", ignoreCase = true)) "" else s
    }

    private fun resolveUrl(host: String, raw: String): String {
        if (raw.isBlank()) return ""
        return if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
            raw
        } else {
            val path = if (raw.startsWith("/")) raw else "/$raw"
            "$host$path"
        }
    }

    private fun buildDownloadUrls(pathOrUrl: String): List<String> {
        val clean = pathOrUrl.trim()
        if (clean.isBlank()) return emptyList()
        if (clean.startsWith("http://", ignoreCase = true) || clean.startsWith("https://", ignoreCase = true)) {
            val list = mutableListOf(clean)
            if (clean.startsWith(PRIMARY_HOST, ignoreCase = true)) {
                list.add(FALLBACK_HOST + clean.substring(PRIMARY_HOST.length))
            } else if (clean.startsWith(FALLBACK_HOST, ignoreCase = true)) {
                list.add(PRIMARY_HOST + clean.substring(FALLBACK_HOST.length))
            }
            return list
        }
        val path = if (clean.startsWith("/")) clean else "/$clean"
        val primary = memoryCache?.baseHost ?: PRIMARY_HOST
        val secondary = if (primary == PRIMARY_HOST) FALLBACK_HOST else PRIMARY_HOST
        return listOf("$primary$path", "$secondary$path")
    }

    private fun parseFileNameFromHeaders(xFilename: String?, contentDisposition: String?): String? {
        if (!xFilename.isNullOrBlank()) {
            val decoded = runCatching { URLDecoder.decode(xFilename.trim(), "UTF-8") }.getOrDefault(xFilename.trim())
            if (decoded.isNotBlank()) return decoded.substringAfterLast('/').substringAfterLast('\\')
        }
        if (!contentDisposition.isNullOrBlank()) {
            val utf8Match = Regex("""filename\*\s*=\s*(?:UTF-8''|utf-8'')([^;\r\n]+)""").find(contentDisposition)
            if (utf8Match != null) {
                val decoded = runCatching { URLDecoder.decode(utf8Match.groupValues[1].trim().trim('"'), "UTF-8") }.getOrNull()
                if (!decoded.isNullOrBlank()) return decoded.substringAfterLast('/').substringAfterLast('\\')
            }
            val plainMatch = Regex("""filename\s*=\s*"([^"]+)"""").find(contentDisposition)
                ?: Regex("""filename\s*=\s*([^;\r\n]+)""").find(contentDisposition)
            if (plainMatch != null) {
                val raw = plainMatch.groupValues[1].trim().trim('"')
                if (raw.isNotBlank()) return raw.substringAfterLast('/').substringAfterLast('\\')
            }
        }
        return null
    }

    private fun looksLikeHtmlHeader(buf: ByteArray, len: Int): Boolean {
        val head = String(buf, 0, len.coerceAtMost(64), Charsets.UTF_8).trimStart().lowercase(Locale.ROOT)
        return head.startsWith("<!doctype") || head.startsWith("<html")
    }

    private fun isZipMagic(file: File): Boolean {
        if (file.length() < 4L) return false
        return runCatching {
            FileInputStream(file).use { fis ->
                val magic = ByteArray(4)
                fis.read(magic) == 4 &&
                    magic[0] == 0x50.toByte() &&
                    magic[1] == 0x4B.toByte() &&
                    magic[2] == 0x03.toByte() &&
                    magic[3] == 0x04.toByte()
            }
        }.getOrDefault(false)
    }

    private fun extractFirstRomFromZip(zipFile: File, outDir: File): Pair<File, String>? {
        val outDirCanonical = outDir.canonicalFile
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                if (entry.isDirectory) continue
                val entryPath = entry.name.replace('\\', '/')
                if (entryPath.contains("..")) continue
                val baseName = entryPath.substringAfterLast('/')
                val ext = baseName.substringAfterLast('.', "").lowercase(Locale.ROOT)
                if (ext !in ROM_EXTENSIONS) continue

                val safeOutName = "zip_${System.currentTimeMillis()}_${baseName.replace(Regex("[^A-Za-z0-9._-]"), "_")}"
                val target = File(outDirCanonical, safeOutName).canonicalFile
                if (!target.path.startsWith(outDirCanonical.path + File.separator)) continue

                FileOutputStream(target).use { fos ->
                    val buf = ByteArray(16384)
                    while (true) {
                        val r = zis.read(buf)
                        if (r <= 0) break
                        fos.write(buf, 0, r)
                    }
                }
                return target to baseName
            }
        }
        return null
    }

    private fun buildDefaultRomFileName(game: SgwCatalogGame): String {
        val ext = when (game.platform) {
            ConsoleSystem.GB -> "gb"
            ConsoleSystem.GBC -> "gbc"
            ConsoleSystem.GBA -> "gba"
        }
        val ver = cleanVersion(game.version)
        val base = game.finalTitle.ifBlank { game.title }
        return if (ver.isNotBlank() && ver != "1.0") "$base ($ver).$ext" else "$base.$ext"
    }

    private fun sanitizeRomFileName(rawName: String, platform: ConsoleSystem): String {
        val defaultExt = when (platform) {
            ConsoleSystem.GB -> "gb"
            ConsoleSystem.GBC -> "gbc"
            ConsoleSystem.GBA -> "gba"
        }
        val cleaned = rawName.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[<>:\"/\\\\|?*\\x00-\\x1F]"), "_")
            .trim()
            .ifBlank { "game.$defaultExt" }
        val ext = cleaned.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return if (ext in ROM_EXTENSIONS) cleaned else "${cleaned.substringBeforeLast('.')}.$defaultExt"
    }

    private fun hasRussianMarker(
        textLangs: List<String>,
        regions: List<String>,
        finalTitle: String,
        title: String,
        modHints: String,
    ): Boolean {
        if (textLangs.any { it.lowercase(Locale.ROOT) in setOf("ru", "rus", "russian", "рус", "русский") }) return true
        if (regions.any { it.lowercase(Locale.ROOT) in setOf("ru", "rus", "russia", "россия") }) return true
        return hasRussianFilenameMarker(finalTitle) || hasRussianFilenameMarker(title) || hasRussianFilenameMarker(modHints)
    }

    private fun hasRussianFilenameMarker(name: String): Boolean {
        val u = name.uppercase(Locale.ROOT)
        return u.contains("[MOD - M. RUS]") ||
            u.contains("[MOD - RUS]") ||
            u.contains("[RUS]") ||
            u.contains("(RUS)") ||
            u.contains("[RU]") ||
            u.contains("(RU)") ||
            Regex("""\bRUS\b""").containsMatchIn(u)
    }

    private fun normalizeRegionTag(region: String, fileName: String): String {
        val u = "$region $fileName".uppercase(Locale.ROOT)
        return when {
            "(USA" in u || "USA" == region.uppercase(Locale.ROOT) || "[U]" in u || "[US]" in u -> "US"
            "(EUR" in u || "EUROPE" == region.uppercase(Locale.ROOT) || "[E]" in u || "[EU]" in u -> "EU"
            "(JPN" in u || "(JAPAN" in u || "JAPAN" == region.uppercase(Locale.ROOT) || "[J]" in u || "[JP]" in u -> "JP"
            else -> ""
        }
    }

    private fun pickBestRemoteCandidate(
        candidates: List<SgwCatalogGame>,
        localRegion: String,
        isLocalRussian: Boolean,
    ): SgwCatalogGame? {
        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates[0]
        return candidates.maxWithOrNull(
            compareBy<SgwCatalogGame>(
                { if (!isLocalRussian && it.isRussian) 2 else if (isLocalRussian == it.isRussian) 1 else 0 },
                {
                    if (localRegion.isNotBlank() && it.regions.any { r -> normalizeRegionTag(r, it.finalTitle) == localRegion }) 1 else 0
                },
                { it.internalBuild },
                { it.downloads },
            )
        )
    }

    fun canonicalKey(value: String): String {
        val noExt = value.replace(Regex("\\.(gb|gbc|gba|sgb|zip|7z)$", RegexOption.IGNORE_CASE), "")
        val decomposed = Normalizer.normalize(noExt, Normalizer.Form.NFD)
        return decomposed
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
            .replace("&", " and ")
            .replace("'", "")
            .replace(Regex("\\([^\\)]*\\)|\\[[^\\]]*\\]"), " ")
            .replace(Regex("\\b(19\\d\\d|20\\d\\d)\\b"), " ")
            .replace(Regex("\\b(rev\\s*\\d+|v\\d+(?:\\.\\d+)*)\\b", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("[^a-z0-9\\u0400-\\u04ff]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    fun cleanVersion(raw: String): String =
        raw.trim().removePrefix("v").removePrefix("V").trim()

    private fun extractVersionFromFileName(fileName: String): String {
        val base = fileName.substringBeforeLast('.')
        val parenVer = Regex("""[\(\[]\s*[vV]?(\d+(?:\.\d+)+)\s*[\)\]]""").find(base)
        if (parenVer != null) return parenVer.groupValues[1]
        val vTag = Regex("""\b[vV](\d+(?:\.\d+)+)\b""").find(base)
        if (vTag != null) return vTag.groupValues[1]
        val revNum = Regex("""\bRev\s*(\d+)\b""", RegexOption.IGNORE_CASE).find(base)
        if (revNum != null) return "1.${revNum.groupValues[1]}"
        return "1.0"
    }

    fun compareVersions(a: String, b: String): Int {
        val pa = cleanVersion(a).split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val pb = cleanVersion(b).split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val max = maxOf(pa.size, pb.size)
        for (i in 0 until max) {
            val va = pa.getOrElse(i) { 0 }
            val vb = pb.getOrElse(i) { 0 }
            if (va != vb) return va.compareTo(vb)
        }
        return 0
    }

    fun formatByteSize(bytes: Long): String {
        if (bytes <= 0L) return ""
        val kb = bytes / 1024.0
        return if (kb >= 1024.0) {
            String.format(Locale.US, "%.1f MB", kb / 1024.0)
        } else {
            String.format(Locale.US, "%.0f KB", kb.coerceAtLeast(1.0))
        }
    }
}
