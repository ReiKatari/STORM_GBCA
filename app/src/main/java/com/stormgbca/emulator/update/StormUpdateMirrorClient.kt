package com.stormgbca.emulator.update

import android.content.Context
import android.content.Intent
import com.stormgbca.emulator.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class UpdateAsset(
    val name: String,
    val url: String,
    val sizeBytes: Long,
)

data class UpdateReleaseInfo(
    val version: String,
    val title: String,
    val changelogMd: String,
    val publishedAt: String,
    val releasePageUrl: String,
    val sourceLabel: String,
    val apkAsset: UpdateAsset?,
)

sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState
    data class Downloading(val bytesRead: Long, val totalBytes: Long, val speedBytesPerSec: Long) : UpdateDownloadState
    data class Ready(val apkFile: File) : UpdateDownloadState
    data class Error(val message: String) : UpdateDownloadState
}

object StormUpdateMirrorClient {
    private const val GITHUB_API = "https://api.github.com/repos/ReiKatari/STORM_GBCA/releases"
    private const val GITHUB_RELEASES_HTML = "https://github.com/ReiKatari/STORM_GBCA/releases"
    private const val SGW_API = "https://stormgamesworld.ru/api/programs/releases?repo=STORM_GBCA"
    private const val SGW_ALT_API = "https://storm-games.world/api/programs/releases?repo=STORM_GBCA"
    private val USER_AGENT: String
        get() = "STORM-GBCA-Updater/${BuildConfig.VERSION_NAME.trim().removePrefix("v").removePrefix("V")}"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    suspend fun checkLatestUpdate(): UpdateReleaseInfo? = withContext(Dispatchers.IO) {
        coroutineScope {
            val ghTask = async { fetchGitHubReleases() }
            val sgwTask = async { fetchSgwReleases() }

            val all = (ghTask.await() + sgwTask.await())
                .filter { it.apkAsset != null }
                .sortedWith { a, b -> compareVersions(b.version, a.version) }

            val best = all.firstOrNull() ?: return@coroutineScope null
            if (compareVersions(best.version, BuildConfig.VERSION_NAME) > 0) best else null
        }
    }

    private fun fetchGitHubReleases(): List<UpdateReleaseInfo> {
        return runCatching {
            val req = Request.Builder().url(GITHUB_API).header("User-Agent", USER_AGENT).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val arr = JSONArray(resp.body?.string() ?: "[]")
                val out = mutableListOf<UpdateReleaseInfo>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    if (obj.optBoolean("draft", false)) continue
                    val tag = obj.optString("tag_name", "").trim().removePrefix("v").removePrefix("V")
                    if (tag.isBlank()) continue
                    val assetsArr = obj.optJSONArray("assets")
                    var apk: UpdateAsset? = null
                    if (assetsArr != null) {
                        for (j in 0 until assetsArr.length()) {
                            val a = assetsArr.optJSONObject(j) ?: continue
                            val name = a.optString("name", "")
                            if (name.endsWith(".apk", ignoreCase = true)) {
                                apk = UpdateAsset(
                                    name = name,
                                    url = a.optString("browser_download_url", ""),
                                    sizeBytes = a.optLong("size", 0L),
                                )
                                break
                            }
                        }
                    }
                    out.add(
                        UpdateReleaseInfo(
                            version = tag,
                            title = obj.optString("name", "STORM GBCA $tag").ifBlank { "STORM GBCA $tag" },
                            changelogMd = obj.optString("body", ""),
                            publishedAt = obj.optString("published_at", ""),
                            releasePageUrl = obj.optString("html_url", GITHUB_RELEASES_HTML),
                            sourceLabel = "GitHub",
                            apkAsset = apk,
                        )
                    )
                }
                out
            }
        }.getOrDefault(emptyList())
    }

    private fun fetchSgwReleases(): List<UpdateReleaseInfo> {
        for (url in listOf(SGW_API, SGW_ALT_API)) {
            val res = runCatching {
                val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use emptyList()
                    val root = JSONObject(resp.body?.string() ?: "{}")
                    val repo = root.optJSONObject("repository") ?: root
                    val arr = repo.optJSONArray("releases") ?: return@use emptyList()
                    val out = mutableListOf<UpdateReleaseInfo>()
                    for (i in 0 until arr.length()) {
                        val rel = arr.optJSONObject(i) ?: continue
                        val ver = rel.optString("version", "").trim().removePrefix("v").removePrefix("V")
                        if (ver.isBlank()) continue
                        val assetsArr = rel.optJSONArray("assets")
                        var apk: UpdateAsset? = null
                        if (assetsArr != null) {
                            for (j in 0 until assetsArr.length()) {
                                val a = assetsArr.optJSONObject(j) ?: continue
                                val name = a.optString("name", "")
                                if (name.endsWith(".apk", ignoreCase = true)) {
                                    var dlUrl = a.optString("downloadUrl", "")
                                    if (dlUrl.startsWith("/")) dlUrl = "https://stormgamesworld.ru$dlUrl"
                                    apk = UpdateAsset(name, dlUrl, a.optLong("size", 0L))
                                    break
                                }
                            }
                        }
                        out.add(
                            UpdateReleaseInfo(
                                version = ver,
                                title = rel.optString("title", "STORM GBCA $ver").ifBlank { "STORM GBCA $ver" },
                                changelogMd = rel.optString("notes", ""),
                                publishedAt = rel.optString("publishedAt", rel.optString("createdAt", "")),
                                releasePageUrl = "https://stormgamesworld.ru/programs?repo=STORM_GBCA",
                                sourceLabel = "STORM GAMES WORLD",
                                apkAsset = apk,
                            )
                        )
                    }
                    out
                }
            }.getOrDefault(emptyList())
            if (res.isNotEmpty()) return res
        }
        return emptyList()
    }

    suspend fun downloadApk(
        context: Context,
        release: UpdateReleaseInfo,
        onProgress: (UpdateDownloadState) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val asset = release.apkAsset ?: run {
            onProgress(UpdateDownloadState.Error("APK asset not found"))
            return@withContext
        }
        runCatching {
            val req = Request.Builder().url(asset.url).header("User-Agent", USER_AGENT).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                val body = resp.body ?: error("Empty body")
                val total = body.contentLength().takeIf { it > 0 } ?: asset.sizeBytes
                val outFile = File(context.cacheDir, asset.name.ifBlank { "STORM_GBCA_${release.version}.apk" })
                val buf = ByteArray(16384)
                var readTotal = 0L
                var lastTickMs = System.currentTimeMillis()
                var bytesSinceTick = 0L
                var speed = 0L

                body.byteStream().use { input ->
                    FileOutputStream(outFile).use { output ->
                        while (true) {
                            val r = input.read(buf)
                            if (r <= 0) break
                            output.write(buf, 0, r)
                            readTotal += r
                            bytesSinceTick += r
                            val now = System.currentTimeMillis()
                            val elapsed = now - lastTickMs
                            if (elapsed >= 300) {
                                speed = (bytesSinceTick * 1000L) / elapsed.coerceAtLeast(1L)
                                lastTickMs = now
                                bytesSinceTick = 0L
                                onProgress(UpdateDownloadState.Downloading(readTotal, total, speed))
                            }
                        }
                    }
                }
                onProgress(UpdateDownloadState.Ready(outFile))
            }
        }.onFailure { err ->
            onProgress(UpdateDownloadState.Error(err.message ?: "Download failed"))
        }
    }

    fun installApk(context: Context, apkFile: File) {
        val uri = UpdateContentProvider.getUpdateFileUri(context, apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun compareVersions(a: String, b: String): Int {
        val pa = a.trim().removePrefix("v").removePrefix("V").split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val pb = b.trim().removePrefix("v").removePrefix("V").split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val max = maxOf(pa.size, pb.size)
        for (i in 0 until max) {
            val va = pa.getOrElse(i) { 0 }
            val vb = pb.getOrElse(i) { 0 }
            if (va != vb) return va.compareTo(vb)
        }
        return 0
    }
}
