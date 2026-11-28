package com.stormgbca.emulator.sgw

import com.stormgbca.emulator.model.ConsoleSystem
import com.stormgbca.emulator.model.RomEntry
import kotlinx.serialization.Serializable

enum class SgwPlatformFilter {
    ALL,
    GB,
    GBC,
    GBA,
    TRANSLATIONS,
    UPDATES,
}

enum class SgwUpdateKind {
    VERSION_UPDATE,
    TRANSLATION_AVAILABLE,
}

@Serializable
data class SgwCatalogGame(
    val gameId: Int,
    val releaseId: Int = 0,
    val platform: ConsoleSystem,
    val title: String,
    val finalTitle: String,
    val version: String,
    val internalBuild: Int = 0,
    val sizeLabel: String,
    val sizeBytes: Long = 0L,
    val coverUrl: String = "",
    val description: String = "",
    val changelog: String = "",
    val regions: List<String> = emptyList(),
    val textLangs: List<String> = emptyList(),
    val isRussian: Boolean = false,
    val isFromTranslations: Boolean = false,
    val hasRetroAchievements: Boolean = false,
    val author: String = "",
    val md5: String = "",
    val crc: String = "",
    val fileName: String = "",
    val downloadPathOrUrl: String = "",
    val downloads: Int = 0,
    val rating: Double = 0.0,
)

data class SgwGameUpdateInfo(
    val localRom: RomEntry,
    val remoteGame: SgwCatalogGame,
    val kind: SgwUpdateKind,
    val currentVersion: String,
    val remoteVersion: String,
)

@Serializable
data class SgwInstalledMeta(
    val uriString: String,
    val gameId: Int,
    val releaseId: Int,
    val version: String,
    val internalBuild: Int,
    val md5: String,
    val isRussian: Boolean,
)

data class SgwDownloadState(
    val active: Boolean = false,
    val key: String = "",
    val title: String = "",
    val bytesRead: Long = 0L,
    val totalBytes: Long = 0L,
    val percent: Int = 0,
    val statusText: String = "",
    val error: String? = null,
)
