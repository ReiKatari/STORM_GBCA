package com.stormgbca.emulator.vulkandriver

import kotlinx.serialization.Serializable

enum class DriverRepoCategory {
    ALL,
    STORM,
    TURNIP,
    ADRENO,
    MALI,
}

enum class GpuVendorFamily(val displayName: String) {
    ADRENO("Qualcomm Adreno"),
    ARM_MALI("ARM Mali / Immortalis"),
    SAMSUNG_XCLIPSE("Samsung Xclipse (RDNA)"),
    POWERVR("Imagination PowerVR"),
    OTHER("Other / Unknown GPU"),
}

enum class AdrenoSubFamily {
    ADRENO_840_PLUS,
    ADRENO_8XX_ELITE,
    ADRENO_7XX_MODERN,
    ADRENO_710_720,
    ADRENO_6XX,
    ADRENO_LEGACY_OR_UNKNOWN,
    NON_ADRENO,
}

enum class MaliArchFamily(val displayName: String) {
    V7_BIFROST("Arm Mali Bifrost v7 (G31/G51/G52/G76)"),
    V9_VALHALL("Arm Mali Valhall v9 (G57/G68/G77/G78)"),
    V10_VALHALL("Arm Mali Valhall v10 (G310/G510/G610/G710)"),
    V11_VALHALL("Arm Mali Valhall v11 (G615/G715)"),
    V12_FIFTH_GEN("Arm Mali 5th Gen v12 (G620/G720)"),
    UNSUPPORTED_MIDGARD_OR_V6("Unsupported Mali Midgard / Bifrost v6"),
    UNSUPPORTED_V13_PLUS("Unsupported Mali 5th Gen v13+"),
    UNKNOWN_MALI("Unknown Arm Mali GPU"),
    NON_MALI("Non-Mali GPU");

    fun isSupportedByMalisx2(): Boolean = when (this) {
        V7_BIFROST,
        V9_VALHALL,
        V10_VALHALL,
        V11_VALHALL,
        V12_FIFTH_GEN,
        -> true
        else -> false
    }
}

data class VulkanDriverRepoSpec(
    val id: String,
    val displayName: String,
    val repoPath: String,
    val category: DriverRepoCategory,
    val useTagName: Boolean,
    val sortByPublishTime: Boolean,
)

data class VulkanDriverAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val mirrorUrls: List<String> = emptyList(),
) {
    fun allUrls(): List<String> = (listOf(downloadUrl) + mirrorUrls).filter { it.isNotBlank() }.distinct()
}

data class VulkanDriverRelease(
    val id: String,
    val repoId: String,
    val repoDisplayName: String,
    val title: String,
    val tagName: String,
    val publishedAt: String,
    val releaseNotes: String,
    val assets: List<VulkanDriverAsset>,
    val isFromOfflineFallback: Boolean = false,
)

data class VulkanDriverRepoGroup(
    val spec: VulkanDriverRepoSpec,
    val releases: List<VulkanDriverRelease> = emptyList(),
    val isLoading: Boolean = false,
    val fromCache: Boolean = false,
    val error: String? = null,
)

@Serializable
data class InstalledVulkanDriver(
    val id: String,
    val driverDir: String,
    val soPath: String,
    val soFileName: String,
    val displayName: String,
    val author: String? = null,
    val vendor: String? = null,
    val driverVersion: String? = null,
    val description: String? = null,
    val minApi: Int? = null,
    val sourceRepo: String? = null,
    val assetFileName: String? = null,
    val installedAtEpochMs: Long = 0L,
)

data class GraphicsTelemetry(
    val vulkanSupported: Boolean,
    val gpuName: String,
    val vulkanDeviceApi: String,
    val vulkanInstanceApi: String,
    val glesVersion: String,
)

data class GpuDeviceProfile(
    val telemetry: GraphicsTelemetry,
    val gpuDescription: String,
    val vendorFamily: GpuVendorFamily,
    val adrenoSubFamily: AdrenoSubFamily,
    val maliArchFamily: MaliArchFamily,
    val isAdrenoTurnipCapable: Boolean,
    val isMalisx2Supported: Boolean,
    val supportsCustomDrivers: Boolean,
    val blockReason: String? = null,
)

sealed interface DriverDownloadState {
    data object Idle : DriverDownloadState
    data class Downloading(
        val key: String,
        val displayName: String,
        val bytesRead: Long,
        val totalBytes: Long,
        val percent: Int,
    ) : DriverDownloadState
    data class Error(val message: String) : DriverDownloadState
}
