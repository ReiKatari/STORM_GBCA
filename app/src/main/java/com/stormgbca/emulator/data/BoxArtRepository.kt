package com.stormgbca.emulator.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.stormgbca.emulator.model.ConsoleSystem
import com.stormgbca.emulator.model.RomEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object BoxArtRepository {
    private val memoryPaths = ConcurrentHashMap<String, String>()
    private val failedKeys = ConcurrentHashMap.newKeySet<String>()

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    private fun baseUrlFor(system: ConsoleSystem): String = when (system) {
        ConsoleSystem.GB -> "https://thumbnails.libretro.com/Nintendo%20-%20Game%20Boy/Named_Boxarts/"
        ConsoleSystem.GBC -> "https://thumbnails.libretro.com/Nintendo%20-%20Game%20Boy%20Color/Named_Boxarts/"
        ConsoleSystem.GBA -> "https://thumbnails.libretro.com/Nintendo%20-%20Game%20Boy%20Advance/Named_Boxarts/"
    }

    private fun cacheDir(context: Context): File =
        File(context.cacheDir, "boxart").apply { mkdirs() }

    private fun customDir(context: Context): File =
        File(context.filesDir, "custom_covers").apply { mkdirs() }

    fun getCachedBoxArtPath(context: Context, rom: RomEntry): String? {
        val key = RomRepository.getRomStorageKey(rom)
        memoryPaths[key]?.let { if (File(it).exists()) return it }

        val customFile = File(customDir(context), "$key.png")
        if (customFile.exists() && customFile.length() > 0L) {
            memoryPaths[key] = customFile.absolutePath
            return customFile.absolutePath
        }

        val cachedFile = File(cacheDir(context), "$key.png")
        if (cachedFile.exists() && cachedFile.length() > 0L) {
            memoryPaths[key] = cachedFile.absolutePath
            return cachedFile.absolutePath
        }
        return null
    }

    suspend fun fetchOrGetBoxArt(context: Context, rom: RomEntry, forceRefresh: Boolean = false): String? = withContext(Dispatchers.IO) {
        val key = RomRepository.getRomStorageKey(rom)
        if (!forceRefresh) {
            getCachedBoxArtPath(context, rom)?.let { return@withContext it }
            if (failedKeys.contains(key)) return@withContext null
        }

        val baseTitle = rom.fileName.substringBeforeLast('.')
        val candidates = buildCandidateNames(baseTitle, rom.internalTitle)
        val baseUrl = baseUrlFor(rom.consoleSystem)

        for (candidate in candidates) {
            val encoded = URLEncoder.encode("$candidate.png", "UTF-8").replace("+", "%20")
            val url = "$baseUrl$encoded"
            val req = Request.Builder().url(url).header("User-Agent", "STORM-GBCA/1.0.0").build()
            val downloaded = runCatching {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val bytes = resp.body?.bytes() ?: return@use null
                    if (bytes.size < 256) return@use null
                    val outFile = File(cacheDir(context), "$key.png")
                    FileOutputStream(outFile).use { it.write(bytes) }
                    outFile.absolutePath
                }
            }.getOrNull()

            if (downloaded != null) {
                memoryPaths[key] = downloaded
                failedKeys.remove(key)
                return@withContext downloaded
            }
        }

        failedKeys.add(key)
        null
    }

    suspend fun saveCustomCover(context: Context, rom: RomEntry, uri: Uri): String? = withContext(Dispatchers.IO) {
        val key = RomRepository.getRomStorageKey(rom)
        val outFile = File(customDir(context), "$key.png")
        runCatching {
            val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return@withContext null
            FileOutputStream(outFile).use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 95, out)
            }
            bmp.recycle()
            memoryPaths[key] = outFile.absolutePath
            failedKeys.remove(key)
            outFile.absolutePath
        }.getOrNull()
    }

    fun clearCustomCover(context: Context, rom: RomEntry) {
        val key = RomRepository.getRomStorageKey(rom)
        File(customDir(context), "$key.png").delete()
        memoryPaths.remove(key)
    }

    private fun buildCandidateNames(fileBase: String, internalTitle: String): List<String> {
        val list = LinkedHashSet<String>()
        val sanitizedFile = fileBase.replace('&', '_').trim()
        list.add(sanitizedFile)
        val strippedRev = sanitizedFile.replace(Regex("\\s*\\(Rev\\s*\\d+\\)", RegexOption.IGNORE_CASE), "").trim()
        list.add(strippedRev)
        val noTags = sanitizedFile.replace(Regex("\\s*\\[[^\\]]*\\]"), "").trim()
        list.add(noTags)

        val cleanName = noTags.replace(Regex("\\s*\\([^)]*\\)"), "").trim()
        if (cleanName.isNotEmpty()) {
            list.add("$cleanName (USA)")
            list.add("$cleanName (USA, Europe)")
            list.add("$cleanName (Europe)")
            list.add("$cleanName (Japan)")
            list.add("$cleanName (World)")
        }
        if (internalTitle.isNotBlank()) {
            val t = internalTitle.trim()
            list.add("$t (USA)")
            list.add("$t (USA, Europe)")
        }
        return list.filter { it.isNotBlank() }
    }
}
