package com.stormgbca.emulator.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.stormgbca.emulator.model.RomEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import ru.stormgames.gbca.NativeCore
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

object RomRepository {
    private val ROM_EXTENSIONS = setOf("gb", "gbc", "gba", "sgb")
    private val ARCHIVE_EXTENSIONS = setOf("zip", "7z")

    suspend fun scanFolders(context: Context): List<RomEntry> = withContext(Dispatchers.IO) {
        val existingByUri = GbcaPrefs.getCachedRoms().associateBy { it.uriString }
        val result = mutableListOf<RomEntry>()
        val folders = GbcaPrefs.getRomFolders()

        for (folderStr in folders) {
            val treeUri = runCatching { Uri.parse(folderStr) }.getOrNull() ?: continue
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: continue
            if (!rootDoc.exists() || !rootDoc.isDirectory) continue
            scanDocumentDir(context, rootDoc, existingByUri, result, depth = 0)
        }

        val deduped = result.distinctBy { it.uriString }
        GbcaPrefs.saveCachedRoms(deduped)
        deduped
    }

    private fun scanDocumentDir(
        context: Context,
        dir: DocumentFile,
        existingByUri: Map<String, RomEntry>,
        out: MutableList<RomEntry>,
        depth: Int,
    ) {
        if (depth > 8) return
        val children = dir.listFiles()
        for (child in children) {
            if (child.isDirectory) {
                scanDocumentDir(context, child, existingByUri, out, depth + 1)
                continue
            }
            val name = child.name ?: continue
            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext !in ROM_EXTENSIONS && ext !in ARCHIVE_EXTENSIONS) continue

            val uriStr = child.uri.toString()
            val prev = existingByUri[uriStr]
            if (prev != null) {
                out.add(prev)
                continue
            }

            val inspected = inspectUri(context, child.uri, name)
            if (inspected != null) {
                out.add(inspected)
            }
        }
    }

    fun inspectUri(context: Context, uri: Uri, fileNameHint: String? = null): RomEntry? {
        val name = fileNameHint ?: resolveFileName(context, uri)
        val ext = name.substringAfterLast('.', "").lowercase()
        val romBytesPair = runCatching { extractRomBytes(context, uri, name, ext) }.getOrNull() ?: return null
        val (bytes, innerName) = romBytesPair
        val info = NativeCore.inspectRomBuffer(bytes, innerName) ?: return null
        if (info.platform == NativeCore.PLATFORM_NONE) return null

        return RomEntry(
            uriString = uri.toString(),
            fileName = name,
            internalTitle = info.title.ifBlank { name.substringBeforeLast('.') },
            platform = info.platform,
            gameCode = info.gameCode,
            region = info.region.ifBlank { detectRegionFallback(name) },
            crc32 = info.crc32,
            md5 = info.md5,
            romSize = info.romSize,
            hwFlags = info.hwFlags,
        )
    }

    fun resolveFileName(context: Context, uri: Uri): String {
        DocumentFile.fromSingleUri(context, uri)?.name?.let { return it }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "game.gba"
    }

    suspend fun materializeRomToCache(context: Context, rom: RomEntry): File? = withContext(Dispatchers.IO) {
        val uri = Uri.parse(rom.uriString)
        val ext = rom.fileName.substringAfterLast('.', "").lowercase()
        val pair = runCatching { extractRomBytes(context, uri, rom.fileName, ext) }.getOrNull() ?: return@withContext null
        val (bytes, innerName) = pair
        val cacheDir = File(context.cacheDir, "active_roms").apply { mkdirs() }
        val safeName = innerName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val outFile = File(cacheDir, safeName)
        FileOutputStream(outFile).use { it.write(bytes) }
        outFile
    }

    suspend fun materializeUriToCache(context: Context, uri: Uri, prefix: String): File? = withContext(Dispatchers.IO) {
        val name = resolveFileName(context, uri).replace(Regex("[^A-Za-z0-9._-]"), "_")
        val dir = File(context.cacheDir, prefix).apply { mkdirs() }
        val outFile = File(dir, name)
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
            } ?: return@withContext null
            outFile
        }.getOrNull()
    }

    private fun extractRomBytes(context: Context, uri: Uri, name: String, ext: String): Pair<ByteArray, String>? {
        if (ext == "zip") {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ZipInputStream(stream).use { zis ->
                    while (true) {
                        val entry = zis.nextEntry ?: break
                        if (entry.isDirectory) continue
                        val eExt = entry.name.substringAfterLast('.', "").lowercase()
                        if (eExt in ROM_EXTENSIONS) {
                            return zis.readBytes() to entry.name.substringAfterLast('/')
                        }
                    }
                }
            }
            return null
        }

        if (ext == "7z") {
            val tmpArchive = File.createTempFile("storm_gbca_", ".7z", context.cacheDir)
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tmpArchive).use { output -> input.copyTo(output) }
                } ?: return null
                SevenZFile.builder().setFile(tmpArchive).get().use { sz ->
                    while (true) {
                        val entry = sz.nextEntry ?: break
                        if (entry.isDirectory) continue
                        val eExt = entry.name.substringAfterLast('.', "").lowercase()
                        if (eExt in ROM_EXTENSIONS) {
                            val bytes = ByteArray(entry.size.toInt())
                            var offset = 0
                            while (offset < bytes.size) {
                                val rd = sz.read(bytes, offset, bytes.size - offset)
                                if (rd <= 0) break
                                offset += rd
                            }
                            return bytes to entry.name.substringAfterLast('/')
                        }
                    }
                }
            } finally {
                tmpArchive.delete()
            }
            return null
        }

        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        return bytes to name
    }

    fun getRomStorageKey(rom: RomEntry): String {
        val seed = if (rom.md5.isNotBlank()) rom.md5 else rom.uriString
        val md = MessageDigest.getInstance("SHA-1").digest(seed.toByteArray())
        return md.take(12).joinToString("") { "%02x".format(it) }
    }

    fun getSaveDir(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "saves").apply { mkdirs() }

    fun getStateDir(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "states").apply { mkdirs() }

    fun getSramFile(context: Context, rom: RomEntry): File =
        File(getSaveDir(context), "${getRomStorageKey(rom)}.sav")

    fun getStateFile(context: Context, rom: RomEntry, slot: Int): File =
        File(getStateDir(context), "${getRomStorageKey(rom)}_slot$slot.ss")

    fun getStateThumbFile(context: Context, rom: RomEntry, slot: Int): File =
        File(getStateDir(context), "${getRomStorageKey(rom)}_slot$slot.png")

    private fun detectRegionFallback(fileName: String): String {
        val u = fileName.uppercase()
        return when {
            "(USA" in u || "(U)" in u || "[U]" in u || "[US]" in u -> "USA"
            "(EUR" in u || "(E)" in u || "[E]" in u || "[EU]" in u -> "Europe"
            "(JPN" in u || "(JAPAN" in u || "(J)" in u || "[J]" in u || "[JP]" in u -> "Japan"
            "(RUS" in u || "(RUSSIA" in u || "[RU]" in u || "[RUS]" in u -> "Russia"
            else -> "World"
        }
    }
}
