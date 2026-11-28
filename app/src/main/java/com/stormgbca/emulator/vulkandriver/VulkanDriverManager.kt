package com.stormgbca.emulator.vulkandriver

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.stormgbca.emulator.data.GbcaPrefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

object VulkanDriverManager {
    private const val MAX_META_SIZE_BYTES = 500_000L
    private const val MAX_EXTRACTED_BYTES = 512L * 1024L * 1024L
    private const val MIN_ARCHIVE_BYTES = 1024L
    private const val STREAM_BUFFER_BYTES = 65_536

    private val KNOWN_NON_ZIP_EXTENSIONS = setOf(
        "txt", "json", "xml", "so", "apk", "7z", "rar", "tar", "gz", "bz2", "xz",
        "tgz", "tbz2", "bin", "gb", "gbc", "gba", "sav", "pdf", "png", "jpg", "jpeg",
        "webp", "html", "htm", "md", "sha256", "sig", "asc",
    )

    private val activeCallRef = AtomicReference<Call?>(null)

    private val downloadClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.MINUTES)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private data class DriverMetadata(
        val name: String? = null,
        val description: String? = null,
        val author: String? = null,
        val packageVersion: String? = null,
        val vendor: String? = null,
        val driverVersion: String? = null,
        val minApi: Int? = null,
        val libraryName: String? = null,
    )

    class DriverException(val reason: Reason, val detailInt: Int = 0) : Exception(reason.name) {
        enum class Reason {
            UnsupportedDevice,
            NotZip,
            InvalidArchive,
            InvalidZip,
            NoDriver,
            AmbiguousDriver,
            ApiTooLow,
            Cancelled,
        }
    }

    fun cancelActiveDownload() {
        activeCallRef.getAndSet(null)?.cancel()
    }

    private fun driversRoot(context: Context): File =
        File(context.filesDir, "vulkan_drivers").apply { mkdirs() }

    private fun isKnownNonZipFileName(displayName: String?): Boolean {
        if (displayName.isNullOrBlank()) return false
        val lower = displayName.trim().lowercase(Locale.ROOT)
        if (lower.endsWith(".zip") || lower.endsWith(".adpkg.zip") || lower.endsWith(".adpkg")) {
            return false
        }
        val ext = lower.substringAfterLast('.', "")
        return ext.isNotEmpty() && ext in KNOWN_NON_ZIP_EXTENSIONS
    }

    private fun validateArchiveFile(file: File) {
        if (!file.isFile || file.length() < MIN_ARCHIVE_BYTES) {
            throw DriverException(DriverException.Reason.InvalidArchive)
        }
        val magic = ByteArray(4)
        file.inputStream().use { input ->
            if (input.read(magic) < 4) {
                throw DriverException(DriverException.Reason.InvalidArchive)
            }
        }
        val isZipMagic = magic[0] == 0x50.toByte() &&
            magic[1] == 0x4B.toByte() &&
            magic[2] == 0x03.toByte() &&
            magic[3] == 0x04.toByte()
        if (!isZipMagic) {
            throw DriverException(DriverException.Reason.InvalidArchive)
        }
    }

    fun selectDriver(driver: InstalledVulkanDriver) {
        if (!File(driver.soPath).isFile) return
        GbcaPrefs.updateCoreConfig { it.copy(customVulkanDriverPath = driver.soPath) }
    }

    fun setSystemDriver() {
        GbcaPrefs.updateCoreConfig { it.copy(customVulkanDriverPath = "") }
    }

    fun deleteDriver(context: Context, driverId: String) {
        val currentList = GbcaPrefs.getInstalledVulkanDrivers()
        val target = currentList.firstOrNull { it.id == driverId } ?: return
        val rootCanonical = driversRoot(context).canonicalFile

        val pkgDir = runCatching { UUID.fromString(target.id) }.getOrNull()?.let { File(rootCanonical, "driver-$it") }
        if (pkgDir != null && pkgDir.canonicalFile.path.startsWith(rootCanonical.path + File.separator)) {
            pkgDir.deleteRecursively()
        } else {
            runCatching {
                val f = File(target.driverDir).canonicalFile
                if (f.path.startsWith(rootCanonical.path + File.separator)) {
                    f.deleteRecursively()
                }
            }
        }

        val updatedList = currentList.filterNot { it.id == driverId }
        GbcaPrefs.saveInstalledVulkanDrivers(updatedList)

        if (GbcaPrefs.coreConfigState.value.customVulkanDriverPath == target.soPath) {
            GbcaPrefs.updateCoreConfig { it.copy(customVulkanDriverPath = "") }
        }
    }

    fun computeInstalledDriverSize(driver: InstalledVulkanDriver): String? {
        return runCatching {
            val dir = File(driver.driverDir)
            if (!dir.exists()) return null
            val totalBytes = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            if (totalBytes <= 0L) return null
            if (totalBytes >= 1024L * 1024L) {
                String.format(Locale.US, "%.1f MB", totalBytes / (1024.0 * 1024.0))
            } else {
                String.format(Locale.US, "%d KB", (totalBytes / 1024L).coerceAtLeast(1L))
            }
        }.getOrNull()
    }

    suspend fun importFromUri(context: Context, uri: Uri): Result<InstalledVulkanDriver> = withContext(Dispatchers.IO) {
        runCatching {
            val profile = VulkanDriverRepository.detectGpuProfile(context)
            if (!profile.supportsCustomDrivers) {
                throw DriverException(DriverException.Reason.UnsupportedDevice)
            }
            val queriedName = queryDisplayName(context, uri)?.trim()?.takeIf { it.isNotBlank() }
            if (isKnownNonZipFileName(queriedName)) {
                throw DriverException(DriverException.Reason.NotZip)
            }
            val rawName = queriedName ?: "Custom Vulkan Driver"
            val tempZip = File(context.cacheDir, "vk_import_${System.currentTimeMillis()}.zip")
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempZip.outputStream().use { output ->
                        input.copyTo(output, bufferSize = STREAM_BUFFER_BYTES)
                    }
                } ?: throw DriverException(DriverException.Reason.InvalidZip)

                val fallbackName = rawName
                    .removeSuffix(".adpkg.zip")
                    .removeSuffix(".zip")
                    .removeSuffix(".adpkg")
                installFromArchiveFile(
                    context = context,
                    zipFile = tempZip,
                    fallbackDisplayName = fallbackName,
                    sourceRepo = "local",
                    assetFileName = queriedName ?: rawName,
                    profile = profile,
                )
            } finally {
                tempZip.delete()
            }
        }
    }

    suspend fun downloadAndInstall(
        context: Context,
        urls: List<String>,
        fallbackDisplayName: String,
        sourceRepo: String?,
        assetFileName: String?,
        onProgress: (bytesRead: Long, totalBytes: Long, percent: Int) -> Unit,
    ): Result<InstalledVulkanDriver> = withContext(Dispatchers.IO) {
        val profile = VulkanDriverRepository.detectGpuProfile(context)
        if (!profile.supportsCustomDrivers) {
            return@withContext Result.failure(DriverException(DriverException.Reason.UnsupportedDevice))
        }
        val cleanUrls = urls.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (cleanUrls.isEmpty()) {
            return@withContext Result.failure(DriverException(DriverException.Reason.InvalidArchive))
        }

        var lastError: Throwable? = null
        for (url in cleanUrls) {
            coroutineContext.ensureActive()
            val tempFile = File(context.cacheDir, "vk_download_${System.currentTimeMillis()}.zip")
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", VulkanDriverRepository.USER_AGENT)
                    .header("Accept", "application/octet-stream, application/zip, */*")
                    .build()

                val call = downloadClient.newCall(request)
                activeCallRef.set(call)

                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("HTTP ${response.code}")
                    }
                    val contentType = response.header("Content-Type").orEmpty().lowercase(Locale.ROOT)
                    if (contentType.contains("text/html") || contentType.contains("application/xhtml")) {
                        throw DriverException(DriverException.Reason.InvalidArchive)
                    }
                    val body = response.body ?: throw IllegalStateException("Empty HTTP body")
                    val totalBytes = body.contentLength()
                    var downloadedBytes = 0L

                    body.byteStream().use { input ->
                        tempFile.outputStream().use { output ->
                            val buffer = ByteArray(STREAM_BUFFER_BYTES)
                            while (true) {
                                coroutineContext.ensureActive()
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                downloadedBytes += read
                                val pct = if (totalBytes > 0L) {
                                    ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
                                } else {
                                    -1
                                }
                                onProgress(downloadedBytes, totalBytes, pct)
                            }
                        }
                    }
                }
                activeCallRef.compareAndSet(call, null)

                val installed = installFromArchiveFile(
                    context = context,
                    zipFile = tempFile,
                    fallbackDisplayName = fallbackDisplayName,
                    sourceRepo = sourceRepo,
                    assetFileName = assetFileName ?: url.substringAfterLast('/'),
                    profile = profile,
                )
                return@withContext Result.success(installed)
            } catch (ce: CancellationException) {
                activeCallRef.getAndSet(null)?.cancel()
                throw ce
            } catch (t: Throwable) {
                if (activeCallRef.get()?.isCanceled() == true) {
                    activeCallRef.set(null)
                    return@withContext Result.failure(DriverException(DriverException.Reason.Cancelled))
                }
                activeCallRef.set(null)
                lastError = t
            } finally {
                tempFile.delete()
            }
        }
        Result.failure(lastError ?: DriverException(DriverException.Reason.InvalidArchive))
    }

    private fun installFromArchiveFile(
        context: Context,
        zipFile: File,
        fallbackDisplayName: String,
        sourceRepo: String?,
        assetFileName: String?,
        profile: GpuDeviceProfile,
    ): InstalledVulkanDriver {
        validateArchiveFile(zipFile)

        val root = driversRoot(context)
        cleanupStalePendingDirectories(root)

        val tempRunId = UUID.randomUUID().toString()
        val pendingRoot = File(root, "pending-$tempRunId")

        try {
            pendingRoot.mkdirs()
            zipFile.inputStream().use { input ->
                extractZipStream(input, pendingRoot)
            }

            val metadata = readDriverMetadata(pendingRoot)
            if (metadata.minApi != null && metadata.minApi > Build.VERSION.SDK_INT) {
                throw DriverException(DriverException.Reason.ApiTooLow, metadata.minApi)
            }

            val isAdreno8xx = profile.adrenoSubFamily == AdrenoSubFamily.ADRENO_8XX_ELITE ||
                profile.adrenoSubFamily == AdrenoSubFamily.ADRENO_840_PLUS
            val driverFile = selectDriverFile(
                root = pendingRoot,
                libraryName = metadata.libraryName,
                preferAdreno = profile.isAdrenoTurnipCapable,
                isAdreno8xx = isAdreno8xx,
            )
            val driverDir = driverFile.parentFile ?: throw DriverException(DriverException.Reason.NoDriver)
            copyCompanionConfigsIfNeeded(pendingRoot, driverDir)

            val versionSuffix = metadata.driverVersion
                ?.takeIf { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) }
            val importedDisplayName = when {
                !metadata.name.isNullOrBlank() && versionSuffix != null && !metadata.name.contains(versionSuffix, ignoreCase = true) ->
                    "${metadata.name} ($versionSuffix)"
                !metadata.name.isNullOrBlank() -> metadata.name
                fallbackDisplayName.isNotBlank() -> fallbackDisplayName
                else -> "Custom Vulkan Driver"
            }

            val existingDrivers = GbcaPrefs.getInstalledVulkanDrivers()
            val duplicate = existingDrivers.firstOrNull { existing ->
                (sourceRepo != null && assetFileName != null &&
                    existing.sourceRepo == sourceRepo && existing.assetFileName == assetFileName) ||
                    (existing.displayName.equals(importedDisplayName, ignoreCase = true) &&
                        existing.soFileName.equals(driverFile.name, ignoreCase = true))
            }

            val finalId = duplicate?.id ?: UUID.randomUUID().toString()
            val installedRoot = File(root, "driver-$finalId")

            if (duplicate != null) {
                runCatching {
                    val rootCanon = root.canonicalFile
                    var oldDir: File? = File(duplicate.driverDir).canonicalFile
                    while (oldDir != null && oldDir.parentFile != rootCanon) {
                        oldDir = oldDir.parentFile
                    }
                    if (oldDir != null && oldDir != installedRoot.canonicalFile && oldDir.name.startsWith("driver-")) {
                        oldDir.deleteRecursively()
                    }
                }
            }

            if (installedRoot.exists()) installedRoot.deleteRecursively()
            if (!pendingRoot.renameTo(installedRoot)) {
                throw DriverException(DriverException.Reason.InvalidZip)
            }

            val relDir = driverDir.toRelativeString(pendingRoot)
            val finalDriverDir = if (relDir == "." || relDir.isEmpty()) installedRoot else File(installedRoot, relDir)
            val finalSoFile = File(finalDriverDir, driverFile.name)

            val resolvedDesc = metadata.description?.takeIf { it.isNotBlank() }
                ?: metadata.packageVersion?.takeIf { it.isNotBlank() }

            val installedDriver = InstalledVulkanDriver(
                id = finalId,
                driverDir = finalDriverDir.absolutePath,
                soPath = finalSoFile.absolutePath,
                soFileName = driverFile.name,
                displayName = importedDisplayName,
                author = metadata.author,
                vendor = metadata.vendor,
                driverVersion = metadata.driverVersion,
                description = resolvedDesc,
                minApi = metadata.minApi,
                sourceRepo = sourceRepo,
                assetFileName = assetFileName,
                installedAtEpochMs = System.currentTimeMillis(),
            )

            val nextList = existingDrivers.filterNot { it.id == finalId } + installedDriver
            GbcaPrefs.saveInstalledVulkanDrivers(nextList)
            GbcaPrefs.updateCoreConfig { it.copy(customVulkanDriverPath = installedDriver.soPath) }
            return installedDriver
        } catch (de: DriverException) {
            pendingRoot.deleteRecursively()
            throw de
        } catch (_: Exception) {
            pendingRoot.deleteRecursively()
            throw DriverException(DriverException.Reason.InvalidZip)
        }
    }

    private fun selectDriverFile(
        root: File,
        libraryName: String?,
        preferAdreno: Boolean,
        isAdreno8xx: Boolean,
    ): File {
        val allSoFiles = root.walkTopDown()
            .filter { it.isFile && it.extension.equals("so", ignoreCase = true) }
            .toList()
        if (allSoFiles.isEmpty()) throw DriverException(DriverException.Reason.NoDriver)

        val arm64Files = allSoFiles.filter { file ->
            val rel = file.relativeTo(root).path.replace('\\', '/').lowercase(Locale.ROOT)
            rel.contains("/arm64-v8a/") || rel.startsWith("arm64-v8a/") ||
                rel.contains("/arm64/") || rel.startsWith("arm64/")
        }
        val nonUnsupported = allSoFiles.filterNot { file ->
            val rel = file.relativeTo(root).path.replace('\\', '/').lowercase(Locale.ROOT)
            rel.contains("/armeabi-v7a/") || rel.startsWith("armeabi-v7a/") ||
                rel.contains("/armeabi/") || rel.startsWith("armeabi/") ||
                rel.contains("/x86_64/") || rel.startsWith("x86_64/") ||
                rel.contains("/x86/") || rel.startsWith("x86/")
        }
        val soFiles = when {
            arm64Files.isNotEmpty() -> arm64Files
            nonUnsupported.isNotEmpty() -> nonUnsupported
            else -> allSoFiles
        }

        if (preferAdreno) {
            val gen8File = soFiles.firstOrNull { it.name.equals("vulkan.adreno_gen8.so", ignoreCase = true) }
            val a7xxFile = soFiles.firstOrNull { it.name.equals("vulkan.adreno_a7xx.so", ignoreCase = true) }
            if (gen8File != null && a7xxFile != null) {
                return if (isAdreno8xx) gen8File else a7xxFile
            }
            if (gen8File != null && isAdreno8xx) return gen8File
        }

        libraryName?.let { reqName ->
            val rootCanon = root.canonicalFile
            val metaFile = File(root, reqName).canonicalFile
            if (!metaFile.path.startsWith(rootCanon.path + File.separator)) {
                throw DriverException(DriverException.Reason.InvalidZip)
            }
            if (metaFile.isFile && metaFile.extension.equals("so", ignoreCase = true)) {
                return metaFile
            }
            soFiles.firstOrNull { it.name.equals(reqName, ignoreCase = true) }?.let { return it.canonicalFile }
        }

        if (preferAdreno) {
            val preferredNames = buildList {
                if (isAdreno8xx) add("vulkan.adreno_gen8.so")
                add("vulkan.adreno_a7xx.so")
                add("vulkan.freedreno.so")
                add("libvulkan_freedreno.so")
                add("vulkan.adreno.so")
            }
            preferredNames.forEach { name ->
                val matches = soFiles.filter { it.name.equals(name, ignoreCase = true) }
                if (matches.size == 1) return matches.first()
                if (matches.size > 1) throw DriverException(DriverException.Reason.AmbiguousDriver)
            }
        }

        val genericMatches = soFiles.filter { file ->
            file.name.startsWith("libvulkan", ignoreCase = true) ||
                (file.name.startsWith("vulkan.", ignoreCase = true) && file.name.endsWith(".so", ignoreCase = true))
        }
        return when (genericMatches.size) {
            1 -> genericMatches.first()
            0 -> if (soFiles.size == 1) soFiles.first() else throw DriverException(DriverException.Reason.NoDriver)
            else -> throw DriverException(DriverException.Reason.AmbiguousDriver)
        }
    }

    private fun extractZipStream(input: InputStream, destination: File) {
        val destCanon = destination.canonicalFile
        var totalExtractedBytes = 0L
        val buffer = ByteArray(STREAM_BUFFER_BYTES)
        ZipInputStream(input.buffered(STREAM_BUFFER_BYTES)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val outputFile = File(destCanon, entry.name).canonicalFile
                val inside = outputFile.path.startsWith(destCanon.path + File.separator) ||
                    (entry.isDirectory && outputFile == destCanon)
                if (!inside) throw DriverException(DriverException.Reason.InvalidZip)

                if (entry.isDirectory) {
                    outputFile.mkdirs()
                } else {
                    outputFile.parentFile?.mkdirs()
                    outputFile.outputStream().use { output ->
                        while (true) {
                            val read = zip.read(buffer)
                            if (read == -1) break
                            totalExtractedBytes += read
                            if (totalExtractedBytes > MAX_EXTRACTED_BYTES) {
                                throw DriverException(DriverException.Reason.InvalidArchive)
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                }
                zip.closeEntry()
            }
        }
    }

    private fun readDriverMetadata(root: File): DriverMetadata {
        val direct = File(root, "meta.json")
        val metaFile = if (direct.isFile) {
            direct
        } else {
            root.walkTopDown().maxDepth(2)
                .firstOrNull { it.isFile && it.name.equals("meta.json", ignoreCase = true) }
                ?: return DriverMetadata()
        }
        if (metaFile.length() > MAX_META_SIZE_BYTES) return DriverMetadata()

        return runCatching {
            val json = JSONObject(metaFile.readText(Charsets.UTF_8))
            DriverMetadata(
                name = json.optString("name").takeIf { it.isNotBlank() },
                description = json.optString("description").takeIf { it.isNotBlank() },
                author = json.optString("author").takeIf { it.isNotBlank() },
                packageVersion = json.optString("packageVersion").takeIf { it.isNotBlank() },
                vendor = json.optString("vendor").takeIf { it.isNotBlank() },
                driverVersion = json.optString("driverVersion").takeIf { it.isNotBlank() },
                minApi = if (json.has("minApi") && !json.isNull("minApi")) {
                    json.optInt("minApi", -1).takeIf { it > 0 }
                } else null,
                libraryName = json.optString("libraryName").takeIf { it.isNotBlank() },
            )
        }.getOrDefault(DriverMetadata())
    }

    private fun copyCompanionConfigsIfNeeded(root: File, driverDir: File) {
        root.walkTopDown()
            .filter { file ->
                file.isFile && (
                    file.extension.equals("conf", ignoreCase = true) ||
                        file.extension.equals("xml", ignoreCase = true) ||
                        file.name.startsWith("drirc", ignoreCase = true) ||
                        file.name.equals("meta.json", ignoreCase = true)
                    )
            }
            .forEach { cfgFile ->
                val target = File(driverDir, cfgFile.name)
                if (!target.exists() && cfgFile.parentFile != driverDir) {
                    runCatching { cfgFile.copyTo(target, overwrite = false) }
                }
            }
    }

    private fun cleanupStalePendingDirectories(root: File) {
        runCatching {
            root.listFiles { f -> f.isDirectory && f.name.startsWith("pending-") }
                ?.forEach { it.deleteRecursively() }
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
    }
}
