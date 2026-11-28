package com.stormgbca.emulator.vulkandriver

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import ru.stormgames.gbca.NativeCore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object VulkanDriverRepository {
    private const val CACHE_TTL_MS = 30L * 60L * 1000L
    private const val RELEASES_PER_PAGE = 10
    private const val MAX_CONCURRENT_REQUESTS = 4
    const val USER_AGENT = "STORM-GBCA/1.8.0 (Android)"

    val STORM_DRIVER_MIRRORS: List<String> = listOf(
        "https://stormgamesworld.ru/uploads/programs/STORM_DRIVER/STORM_DRIVER_12.0.0.zip",
        "https://github.com/ReiKatari/STORM_DRIVER/releases/download/12.0.0/STORM_DRIVER_12.0.0.zip",
        "https://media.githubusercontent.com/media/ReiKatari/STORM_GAMES_WORLD/main/Files/STORM_DRIVER_12.0.0.zip",
        "https://github.com/ReiKatari/STORM_GAMES_WORLD/raw/main/Files/STORM_DRIVER_12.0.0.zip",
    )

    val ALL_REPOSITORIES: List<VulkanDriverRepoSpec> = listOf(
        VulkanDriverRepoSpec(
            id = "storm_driver",
            displayName = "STORM DRIVER",
            repoPath = "ReiKatari/STORM_DRIVER",
            category = DriverRepoCategory.STORM,
            useTagName = true,
            sortByPublishTime = true,
        ),
        VulkanDriverRepoSpec(
            id = "kimchi",
            displayName = "KIMCHI Turnip Drivers",
            repoPath = "K11MCH1/AdrenoToolsDrivers",
            category = DriverRepoCategory.TURNIP,
            useTagName = true,
            sortByPublishTime = true,
        ),
        VulkanDriverRepoSpec(
            id = "mrpurple",
            displayName = "Mr. Purple Turnip",
            repoPath = "MrPurple666/purple-turnip",
            category = DriverRepoCategory.TURNIP,
            useTagName = false,
            sortByPublishTime = false,
        ),
        VulkanDriverRepoSpec(
            id = "weabchan",
            displayName = "Weab-Chan Freedreno CI",
            repoPath = "Weab-chan/freedreno_turnip-CI",
            category = DriverRepoCategory.TURNIP,
            useTagName = false,
            sortByPublishTime = false,
        ),
        VulkanDriverRepoSpec(
            id = "whitebelyash",
            displayName = "Whitebelyash Turnip Hub",
            repoPath = "whitebelyash/freedreno_turnip-CI",
            category = DriverRepoCategory.TURNIP,
            useTagName = false,
            sortByPublishTime = true,
        ),
        VulkanDriverRepoSpec(
            id = "crueter_8elite",
            displayName = "GameHub Adreno 8xx",
            repoPath = "crueter/GameHub-8Elite-Drivers",
            category = DriverRepoCategory.ADRENO,
            useTagName = false,
            sortByPublishTime = false,
        ),
        VulkanDriverRepoSpec(
            id = "stevenmx_ci",
            displayName = "StevenMX Turnip CI",
            repoPath = "StevenMXZ/freedreno_turnip-CI",
            category = DriverRepoCategory.TURNIP,
            useTagName = true,
            sortByPublishTime = true,
        ),
        VulkanDriverRepoSpec(
            id = "the412banner",
            displayName = "The412Banner Nightly Turnip",
            repoPath = "The412Banner/Banners-Turnip",
            category = DriverRepoCategory.TURNIP,
            useTagName = true,
            sortByPublishTime = true,
        ),
        VulkanDriverRepoSpec(
            id = "stevenmx_tools",
            displayName = "StevenMXZ Adreno Tools",
            repoPath = "StevenMXZ/Adreno-Tools-Drivers",
            category = DriverRepoCategory.ADRENO,
            useTagName = true,
            sortByPublishTime = true,
        ),
        VulkanDriverRepoSpec(
            id = "malisx2",
            displayName = "malisx2 (Arm Mali PanVK)",
            repoPath = "bmdhacks/malisx2",
            category = DriverRepoCategory.MALI,
            useTagName = true,
            sortByPublishTime = true,
        ),
    )

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .callTimeout(40, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val MALI_MODEL_REGEX = Regex("(?:mali|immortalis)?[^a-z0-9]*(t\\d{2,4}|g\\d{2,3}(?:ae)?)")
    private val ADRENO_710_TOKEN_REGEX = Regex("(^|[^a-z0-9])(a?710|a?720|a?722|adreno[_\\s-]*(710|720|722))([^a-z0-9]|$)")
    private val ADRENO_8XX_TOKEN_REGEX = Regex("(^|[^a-z0-9])(825|829|830|840|845|850|a830|a840|a8xx|gen8|8elite|sm8750|sm8850)([^a-z0-9]|$)")

    fun readGraphicsTelemetry(context: Context): GraphicsTelemetry {
        val vkSupported = runCatching { NativeCore.nativeIsVulkanSupported() }.getOrDefault(false)
        val rawVk = runCatching { NativeCore.nativeGetVulkanInfo() }.getOrDefault("").trim()
        val parts = rawVk.split('|')
        val parsedName = parts.getOrNull(0)?.trim().orEmpty()
        val devApi = parts.getOrNull(1)?.trim().orEmpty()
        val instApi = parts.getOrNull(2)?.trim().orEmpty()
        val fallbackName = runCatching { NativeCore.nativeGetVulkanDeviceName() }.getOrDefault("").trim()
        val gpuName = parsedName.ifBlank { fallbackName }

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val amGles = am?.deviceConfigurationInfo?.glEsVersion?.trim().orEmpty()
        val nativeGles = runCatching { NativeCore.nativeGetGlesVersion() }.getOrDefault("").trim()
        val nativeVerMatch = Regex("""\b([0-9]+\.[0-9]+(?:\.[0-9]+)?)\b""").find(nativeGles)?.groupValues?.getOrNull(1).orEmpty()
        val glesVer = when {
            nativeVerMatch.isNotBlank() && nativeVerMatch.startsWith("3") -> nativeVerMatch
            amGles.isNotBlank() -> amGles
            nativeVerMatch.isNotBlank() -> nativeVerMatch
            else -> "3.0"
        }

        return GraphicsTelemetry(
            vulkanSupported = vkSupported,
            gpuName = gpuName,
            vulkanDeviceApi = devApi,
            vulkanInstanceApi = instApi,
            glesVersion = glesVer,
        )
    }

    private fun readSysfsGpuModel(): String {
        return listOf(
            "/sys/class/kgsl/kgsl-3d0/gpu_model",
            "/sys/class/kgsl/kgsl-3d0/gpu_model_name",
            "/sys/class/misc/mali0/device/gpuinfo",
            "/proc/gpuinfo",
        ).firstNotNullOfOrNull { path ->
            runCatching {
                File(path).takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() }
            }.getOrNull()
        }.orEmpty().trim()
    }

    private fun readSocModel(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL.orEmpty()
        } else {
            ""
        }
    }

    private fun hasMaliKbaseKernel(): Boolean =
        File("/dev/mali0").exists() || File("/sys/class/misc/mali0").exists()

    private fun is64BitArmProcess(): Boolean {
        val is64 = runCatching { Process.is64Bit() }.getOrDefault(false)
        return is64 && Build.SUPPORTED_64_BIT_ABIS.orEmpty().any { it.equals("arm64-v8a", ignoreCase = true) }
    }

    private fun isKnownNonAdrenoName(nameLower: String): Boolean {
        if (nameLower.isBlank()) return false
        return nameLower.contains("mali") ||
            nameLower.contains("immortalis") ||
            nameLower.contains("xclipse") ||
            nameLower.contains("powervr") ||
            nameLower.contains("rogue") ||
            nameLower.contains("b-series") ||
            nameLower.contains("maleoon") ||
            nameLower.contains("swiftshader") ||
            nameLower.contains("llvmpipe") ||
            nameLower.contains("virgl") ||
            nameLower.contains("tegra") ||
            nameLower.contains("nvidia") ||
            nameLower.contains("radeon") ||
            nameLower.contains("intel")
    }

    private fun isAdrenoDevice(vkName: String): Boolean {
        val vkLower = vkName.lowercase(Locale.ROOT)
        if (isKnownNonAdrenoName(vkLower)) return false
        if (vkLower.contains("adreno")) return true

        val sysfsModel = readSysfsGpuModel().lowercase(Locale.ROOT)
        if (isKnownNonAdrenoName(sysfsModel)) return false
        if (sysfsModel.contains("adreno")) return true

        val hardware = Build.HARDWARE.orEmpty().lowercase(Locale.ROOT)
        val soc = readSocModel().lowercase(Locale.ROOT)
        if (hardware.startsWith("mt") ||
            hardware.contains("exynos") ||
            hardware.contains("samsung") ||
            hardware.contains("kirin") ||
            hardware.contains("s5e") ||
            hardware.contains("tensor") ||
            hardware.contains("ranchu") ||
            hardware.contains("goldfish") ||
            soc.startsWith("mt") ||
            soc.contains("dimensity") ||
            soc.contains("exynos") ||
            soc.contains("tensor") ||
            soc.contains("kirin") ||
            hasMaliKbaseKernel()
        ) {
            return false
        }

        if (File("/sys/class/kgsl/kgsl-3d0").exists()) return true

        return hardware == "qcom" ||
            hardware.contains("qualcomm") ||
            soc.startsWith("sm") ||
            soc.startsWith("sdm") ||
            soc.startsWith("qcm") ||
            soc.startsWith("qcs")
    }

    private fun detectAdrenoSubFamily(vkName: String): AdrenoSubFamily {
        if (!isAdrenoDevice(vkName)) return AdrenoSubFamily.NON_ADRENO

        val vkDevice = vkName.lowercase(Locale.ROOT)
        val sysfs = readSysfsGpuModel().lowercase(Locale.ROOT)
        val combinedModel = "$vkDevice $sysfs".trim()
        val soc = readSocModel().lowercase(Locale.ROOT)
        val hardware = Build.HARDWARE.orEmpty().lowercase(Locale.ROOT)
        val board = Build.BOARD.orEmpty().lowercase(Locale.ROOT)

        if (soc.contains("7635") || hardware.contains("volcano") || board.contains("volcano") || combinedModel.contains("810")) {
            return AdrenoSubFamily.ADRENO_710_720
        }

        if (soc.contains("8850") ||
            soc.contains("8845") ||
            soc.contains("8835") ||
            hardware.contains("canoe") ||
            board.contains("canoe") ||
            combinedModel.contains("840") ||
            combinedModel.contains("845") ||
            combinedModel.contains("850") ||
            combinedModel.contains("adreno (tm) 84") ||
            combinedModel.contains("adreno 84") ||
            combinedModel.contains("adreno (tm) 9") ||
            combinedModel.contains("adreno 9")
        ) {
            return AdrenoSubFamily.ADRENO_840_PLUS
        }

        if (soc.contains("8750") ||
            soc.contains("8735") ||
            combinedModel.contains("830") ||
            combinedModel.contains("829") ||
            combinedModel.contains("825") ||
            combinedModel.contains("adreno 8") ||
            combinedModel.contains("adreno8") ||
            combinedModel.contains("adreno (tm) 8") ||
            combinedModel.contains("adreno x1")
        ) {
            return AdrenoSubFamily.ADRENO_8XX_ELITE
        }

        if (combinedModel.contains("710") || combinedModel.contains("720") || combinedModel.contains("722")) {
            return AdrenoSubFamily.ADRENO_710_720
        }

        if (combinedModel.contains("750") ||
            combinedModel.contains("740") ||
            combinedModel.contains("735") ||
            combinedModel.contains("732") ||
            combinedModel.contains("730") ||
            combinedModel.contains("725") ||
            combinedModel.contains("702") ||
            combinedModel.contains("adreno 7") ||
            combinedModel.contains("adreno7") ||
            combinedModel.contains("adreno (tm) 7")
        ) {
            return AdrenoSubFamily.ADRENO_7XX_MODERN
        }

        if (combinedModel.contains("adreno 6") ||
            combinedModel.contains("adreno6") ||
            combinedModel.contains("adreno (tm) 6") ||
            Regex("\\b6[0-9]{2}\\b").containsMatchIn(combinedModel)
        ) {
            return AdrenoSubFamily.ADRENO_6XX
        }

        val modelNumber = Regex("adreno[^0-9]*([0-9]{3})").find(combinedModel)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (modelNumber != null && modelNumber < 600) {
            return AdrenoSubFamily.ADRENO_LEGACY_OR_UNKNOWN
        }

        return AdrenoSubFamily.ADRENO_7XX_MODERN
    }

    private fun classifyMaliArchFamily(gpuModelText: String?): MaliArchFamily {
        val text = gpuModelText.orEmpty().trim().lowercase(Locale.ROOT)
        if (text.isBlank()) return MaliArchFamily.NON_MALI
        if (text.contains("adreno") || text.contains("xclipse") || text.contains("powervr") ||
            text.contains("rogue") || text.contains("maleoon") || text.contains("nvidia") ||
            text.contains("tegra") || text.contains("swiftshader")
        ) {
            return MaliArchFamily.NON_MALI
        }

        val tokens = MALI_MODEL_REGEX.findAll(text).mapNotNull { it.groupValues.getOrNull(1) }.toList()
        for (token in tokens) {
            when (token) {
                "g31", "g51", "g52", "g76" -> return MaliArchFamily.V7_BIFROST
                "g57", "g68", "g77", "g78", "g78ae" -> return MaliArchFamily.V9_VALHALL
                "g310", "g510", "g610", "g710" -> return MaliArchFamily.V10_VALHALL
                "g615", "g715" -> return MaliArchFamily.V11_VALHALL
                "g620", "g720" -> return MaliArchFamily.V12_FIFTH_GEN
                "g71", "g72" -> return MaliArchFamily.UNSUPPORTED_MIDGARD_OR_V6
                "g925" -> return MaliArchFamily.UNSUPPORTED_V13_PLUS
            }
            if (token.startsWith("t") && token.drop(1).all { it.isDigit() }) {
                return MaliArchFamily.UNSUPPORTED_MIDGARD_OR_V6
            }
        }
        if (text.contains("mali") || text.contains("immortalis")) {
            return MaliArchFamily.UNKNOWN_MALI
        }
        return MaliArchFamily.NON_MALI
    }

    fun detectGpuProfile(context: Context): GpuDeviceProfile {
        val telemetry = readGraphicsTelemetry(context)
        val adrenoSub = detectAdrenoSubFamily(telemetry.gpuName)
        val sysfs = readSysfsGpuModel()
        val soc = readSocModel()
        val hardware = Build.HARDWARE.orEmpty()
        val combined = "${telemetry.gpuName} $sysfs $soc $hardware".lowercase(Locale.ROOT)

        val vendorFamily = when {
            adrenoSub != AdrenoSubFamily.NON_ADRENO -> GpuVendorFamily.ADRENO
            combined.contains("xclipse") || combined.contains("s5e") || combined.contains("exynos") -> GpuVendorFamily.SAMSUNG_XCLIPSE
            combined.contains("powervr") || combined.contains("rogue") || combined.contains("b-series") -> GpuVendorFamily.POWERVR
            combined.contains("mali") || combined.contains("immortalis") || hardware.lowercase(Locale.ROOT).startsWith("mt") ||
                soc.lowercase(Locale.ROOT).startsWith("mt") || combined.contains("dimensity") || hasMaliKbaseKernel() -> GpuVendorFamily.ARM_MALI
            else -> GpuVendorFamily.OTHER
        }

        val maliArch = if (vendorFamily == GpuVendorFamily.ARM_MALI) {
            val classified = classifyMaliArchFamily("${telemetry.gpuName} $sysfs")
            if (classified != MaliArchFamily.NON_MALI) classified else MaliArchFamily.UNKNOWN_MALI
        } else {
            MaliArchFamily.NON_MALI
        }

        val malisx2Supported = maliArch.isSupportedByMalisx2() &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            is64BitArmProcess() &&
            hasMaliKbaseKernel()

        val turnipCapable = adrenoSub != AdrenoSubFamily.NON_ADRENO &&
            adrenoSub != AdrenoSubFamily.ADRENO_LEGACY_OR_UNKNOWN

        val supportsCustom = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && is64BitArmProcess()
        val blockReason = if (!supportsCustom) {
            "Requires Android 9+ (64-bit arm64-v8a)"
        } else {
            null
        }

        val bestName = telemetry.gpuName.ifBlank { sysfs }
        val desc = when {
            bestName.isNotBlank() && soc.isNotBlank() && !bestName.contains(soc, ignoreCase = true) -> "$bestName ($soc)"
            bestName.isNotBlank() -> bestName
            vendorFamily == GpuVendorFamily.ADRENO -> "Qualcomm Adreno ($soc)"
            else -> "${vendorFamily.displayName} (${soc.ifBlank { hardware }})"
        }

        return GpuDeviceProfile(
            telemetry = telemetry,
            gpuDescription = desc,
            vendorFamily = vendorFamily,
            adrenoSubFamily = adrenoSub,
            maliArchFamily = maliArch,
            isAdrenoTurnipCapable = turnipCapable,
            isMalisx2Supported = malisx2Supported,
            supportsCustomDrivers = supportsCustom,
            blockReason = blockReason,
        )
    }

    fun getRecommendedRepoIds(profile: GpuDeviceProfile): List<String> {
        if (profile.adrenoSubFamily == AdrenoSubFamily.NON_ADRENO && profile.isMalisx2Supported) {
            return listOf("malisx2")
        }
        return when (profile.adrenoSubFamily) {
            AdrenoSubFamily.ADRENO_840_PLUS -> listOf("storm_driver", "crueter_8elite")
            AdrenoSubFamily.ADRENO_8XX_ELITE -> listOf("storm_driver", "crueter_8elite", "kimchi")
            AdrenoSubFamily.ADRENO_710_720 -> listOf("storm_driver", "kimchi", "mrpurple")
            AdrenoSubFamily.ADRENO_7XX_MODERN -> listOf("storm_driver", "kimchi", "mrpurple", "weabchan", "whitebelyash")
            AdrenoSubFamily.ADRENO_6XX -> listOf("storm_driver", "kimchi", "mrpurple", "stevenmx_tools", "weabchan")
            AdrenoSubFamily.ADRENO_LEGACY_OR_UNKNOWN, AdrenoSubFamily.NON_ADRENO -> emptyList()
        }
    }

    fun isRepoSupportedOnDevice(repoId: String, profile: GpuDeviceProfile): Boolean {
        if (repoId == "malisx2") {
            return profile.isMalisx2Supported && profile.adrenoSubFamily == AdrenoSubFamily.NON_ADRENO
        }
        return when (profile.adrenoSubFamily) {
            AdrenoSubFamily.NON_ADRENO,
            AdrenoSubFamily.ADRENO_LEGACY_OR_UNKNOWN,
            -> false
            AdrenoSubFamily.ADRENO_840_PLUS,
            AdrenoSubFamily.ADRENO_8XX_ELITE,
            -> true
            AdrenoSubFamily.ADRENO_710_720,
            AdrenoSubFamily.ADRENO_7XX_MODERN,
            AdrenoSubFamily.ADRENO_6XX,
            -> repoId != "crueter_8elite"
        }
    }

    fun isAssetSupportedOnDevice(
        repoId: String,
        releaseTitle: String,
        tagName: String,
        assetName: String,
        profile: GpuDeviceProfile,
    ): Boolean {
        if (!isRepoSupportedOnDevice(repoId, profile)) return false
        if (repoId == "malisx2") {
            val lower = assetName.lowercase(Locale.ROOT)
            return lower.startsWith("malisx2-") && lower.endsWith(".zip") && !lower.endsWith(".sha256.zip")
        }
        if (repoId == "storm_driver") return true

        val text = "$releaseTitle $tagName $assetName".lowercase(Locale.ROOT)
        val hasUnifiedTag = text.contains("a6xx") || text.contains("a7xx") ||
            text.contains("6xx") || text.contains("7xx") ||
            text.contains("unified") || text.contains("all adreno")
        val has710OnlyTag = ADRENO_710_TOKEN_REGEX.containsMatchIn(text) &&
            !text.contains("a7xx") && !text.contains("6xx")

        return when (profile.adrenoSubFamily) {
            AdrenoSubFamily.NON_ADRENO,
            AdrenoSubFamily.ADRENO_LEGACY_OR_UNKNOWN,
            -> false
            AdrenoSubFamily.ADRENO_840_PLUS -> {
                if (has710OnlyTag) return false
                if (repoId == "crueter_8elite") return !text.contains("a7xx") && !text.contains("a6xx")
                text.contains("840") || text.contains("8850") || text.contains("gen 5") ||
                    text.contains("gen5") || text.contains("a840") || text.contains("gamehub") ||
                    text.contains("adreno 8") || text.contains("adreno8") || text.contains("8 elite") ||
                    text.contains("a8xx") || text.contains("849.") || text.contains("842.")
            }
            AdrenoSubFamily.ADRENO_8XX_ELITE -> {
                if (has710OnlyTag) return false
                if (repoId == "crueter_8elite") return !text.contains("a7xx") && !text.contains("a6xx")
                ADRENO_8XX_TOKEN_REGEX.containsMatchIn(text) ||
                    text.contains("adreno 8") || text.contains("adreno8") ||
                    text.contains("8 elite") || text.contains("gamehub") ||
                    text.contains("842.8") || text.contains("849.")
            }
            AdrenoSubFamily.ADRENO_710_720 -> {
                val is8xxOnly = (ADRENO_8XX_TOKEN_REGEX.containsMatchIn(text) ||
                    text.contains("adreno 8") || text.contains("adreno8") ||
                    text.contains("8 elite") || text.contains("849.") || text.contains("842.")) && !hasUnifiedTag
                !is8xxOnly
            }
            AdrenoSubFamily.ADRENO_7XX_MODERN,
            AdrenoSubFamily.ADRENO_6XX,
            -> {
                if (has710OnlyTag) return false
                val is8xxOnly = (ADRENO_8XX_TOKEN_REGEX.containsMatchIn(text) ||
                    text.contains("adreno 8") || text.contains("adreno8") ||
                    text.contains("8 elite") || text.contains("849.") || text.contains("842.")) && !hasUnifiedTag
                !is8xxOnly
            }
        }
    }

    fun isDriverCompatibleWithCurrentGpu(
        driverName: String,
        description: String? = null,
        version: String? = null,
        sourceRepo: String? = null,
        profile: GpuDeviceProfile,
    ): Boolean {
        val combined = listOfNotNull(driverName, description, version, sourceRepo).joinToString(" ").lowercase(Locale.ROOT)
        val isPanvk = combined.contains("malisx2") || combined.contains("panvk") ||
            combined.contains("panfrost") || combined.contains("panthor") || combined.contains("libvulkan_panfrost")
        val isAdrenoPkg = combined.contains("turnip") || combined.contains("freedreno") ||
            combined.contains("adreno") || combined.contains("kimchi") ||
            combined.contains("storm_driver") || combined.contains("storm driver") ||
            combined.contains("mrpurple") || combined.contains("weab") ||
            combined.contains("whitebelyash") || combined.contains("gamehub") ||
            combined.contains("crueter") || combined.contains("gen8") ||
            combined.contains("a7xx") || combined.contains("a6xx")

        if (profile.isAdrenoTurnipCapable) {
            if (isPanvk || combined.contains("mali") || combined.contains("xclipse") || combined.contains("powervr")) {
                return false
            }
            if (profile.adrenoSubFamily == AdrenoSubFamily.ADRENO_840_PLUS ||
                profile.adrenoSubFamily == AdrenoSubFamily.ADRENO_8XX_ELITE
            ) {
                if (sourceRepo in setOf("storm_driver", "crueter_8elite")) return true
                return !isDriverPotentiallyIncompatibleWithAdreno8xx(driverName, description, version, profile.adrenoSubFamily)
            }
            return true
        }
        if (isAdrenoPkg) return false
        if (profile.vendorFamily == GpuVendorFamily.ARM_MALI) {
            if (isPanvk) return profile.isMalisx2Supported
            return combined.contains("mali") || (!combined.contains("xclipse") && !combined.contains("powervr"))
        }
        return !isPanvk && !combined.contains("mali")
    }

    fun isDriverPotentiallyIncompatibleWithAdreno8xx(
        driverName: String,
        description: String? = null,
        version: String? = null,
        subFamily: AdrenoSubFamily,
    ): Boolean {
        if (subFamily != AdrenoSubFamily.ADRENO_8XX_ELITE && subFamily != AdrenoSubFamily.ADRENO_840_PLUS) {
            return false
        }
        val combined = listOfNotNull(driverName, description, version).joinToString(" ").lowercase(Locale.ROOT)
        if (subFamily == AdrenoSubFamily.ADRENO_840_PLUS) {
            val ok = combined.contains("840") || combined.contains("8850") ||
                combined.contains("gen 5") || combined.contains("gen5") ||
                combined.contains("a840") || combined.contains("storm") ||
                combined.contains("gamehub") || combined.contains("elite gen")
            return !ok
        }
        val ok = combined.contains("830") || combined.contains("829") || combined.contains("825") ||
            combined.contains("adreno 8") || combined.contains("adreno8") ||
            combined.contains("8 elite") || combined.contains("gen8") ||
            combined.contains("gamehub") || combined.contains("842.8") ||
            combined.contains("849.") || combined.contains("storm")
        return !ok
    }

    fun filterRecommendedGroups(groups: List<VulkanDriverRepoGroup>, profile: GpuDeviceProfile): List<VulkanDriverRepoGroup> {
        val recommendedIds = getRecommendedRepoIds(profile)
        return groups.mapNotNull { group ->
            if (!isRepoSupportedOnDevice(group.spec.id, profile)) return@mapNotNull null
            if (group.releases.isEmpty()) return@mapNotNull if (group.isLoading) group else null
            val filteredReleases = group.releases.mapNotNull { release ->
                val compatAssets = release.assets.filter { asset ->
                    isAssetSupportedOnDevice(group.spec.id, release.title, release.tagName, asset.name, profile)
                }
                if (compatAssets.isEmpty()) null else release.copy(assets = compatAssets)
            }
            if (filteredReleases.isEmpty() && !group.isLoading) null else group.copy(releases = filteredReleases)
        }.sortedWith(
            compareBy { group ->
                val idx = recommendedIds.indexOf(group.spec.id)
                if (idx >= 0) idx else Int.MAX_VALUE
            }
        )
    }

    private fun cacheRoot(context: Context): File =
        File(context.cacheDir, "vulkan_driver_repos").apply { mkdirs() }

    private fun cacheJsonFile(context: Context, repoId: String): File =
        File(cacheRoot(context), "$repoId.json")

    private fun cacheEtagFile(context: Context, repoId: String): File =
        File(cacheRoot(context), "$repoId.etag")

    fun getCachedAllRepositories(context: Context): List<VulkanDriverRepoGroup> {
        return ALL_REPOSITORIES.map { spec ->
            val file = cacheJsonFile(context, spec.id)
            if (file.isFile && file.length() >= 2L) {
                val parsed = runCatching {
                    parseReleasesJson(spec, file.readText(Charsets.UTF_8), isOfflineFallback = false)
                }.getOrNull()
                if (parsed != null) {
                    return@map VulkanDriverRepoGroup(
                        spec = spec,
                        releases = parsed.ifEmpty { getOfflineFallbackForRepo(spec) },
                        isLoading = false,
                        fromCache = true,
                    )
                }
            }
            VulkanDriverRepoGroup(
                spec = spec,
                releases = getOfflineFallbackForRepo(spec),
                isLoading = true,
                fromCache = false,
            )
        }
    }

    suspend fun fetchAllRepositories(context: Context, forceRefresh: Boolean = false): List<VulkanDriverRepoGroup> =
        withContext(Dispatchers.IO) {
            val semaphore = Semaphore(MAX_CONCURRENT_REQUESTS)
            coroutineScope {
                ALL_REPOSITORIES.map { spec ->
                    async {
                        semaphore.withPermit {
                            fetchSingleRepository(context, spec, forceRefresh)
                        }
                    }
                }.awaitAll()
            }
        }

    suspend fun fetchSingleRepository(
        context: Context,
        spec: VulkanDriverRepoSpec,
        forceRefresh: Boolean = false,
    ): VulkanDriverRepoGroup = withContext(Dispatchers.IO) {
        val jsonFile = cacheJsonFile(context, spec.id)
        val etagFile = cacheEtagFile(context, spec.id)
        val now = System.currentTimeMillis()

        if (!forceRefresh && jsonFile.isFile && (now - jsonFile.lastModified() < CACHE_TTL_MS)) {
            val cached = runCatching {
                parseReleasesJson(spec, jsonFile.readText(Charsets.UTF_8), isOfflineFallback = false)
            }.getOrNull()
            if (cached != null) {
                return@withContext VulkanDriverRepoGroup(
                    spec = spec,
                    releases = cached.ifEmpty { getOfflineFallbackForRepo(spec) },
                    isLoading = false,
                    fromCache = true,
                )
            }
        }

        val apiUrl = "https://api.github.com/repos/${spec.repoPath}/releases?per_page=$RELEASES_PER_PAGE"
        val requestBuilder = Request.Builder()
            .url(apiUrl)
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", USER_AGENT)

        if (!forceRefresh && etagFile.isFile && jsonFile.isFile) {
            val etag = etagFile.readText(Charsets.UTF_8).trim()
            if (etag.isNotEmpty()) {
                requestBuilder.header("If-None-Match", etag)
            }
        }

        val networkAttempt = runCatching {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (response.code == 304 && jsonFile.isFile) {
                    jsonFile.setLastModified(now)
                    val cached = parseReleasesJson(spec, jsonFile.readText(Charsets.UTF_8), isOfflineFallback = false)
                    return@use VulkanDriverRepoGroup(
                        spec = spec,
                        releases = if (cached.isNotEmpty()) cached else getOfflineFallbackForRepo(spec),
                        isLoading = false,
                        fromCache = true,
                    )
                }

                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP ${response.code}")
                }

                val bodyText = response.body?.string().orEmpty()
                if (bodyText.isBlank()) {
                    throw IllegalStateException("Empty JSON")
                }

                val parsed = parseReleasesJson(spec, bodyText, isOfflineFallback = false)
                jsonFile.writeText(bodyText, Charsets.UTF_8)
                response.header("ETag")?.takeIf { it.isNotBlank() }?.let { etag ->
                    etagFile.writeText(etag, Charsets.UTF_8)
                }
                VulkanDriverRepoGroup(
                    spec = spec,
                    releases = parsed.ifEmpty { getOfflineFallbackForRepo(spec) },
                    isLoading = false,
                    fromCache = false,
                )
            }
        }

        networkAttempt.getOrElse { err ->
            val staleCached = if (jsonFile.isFile) {
                runCatching {
                    parseReleasesJson(spec, jsonFile.readText(Charsets.UTF_8), isOfflineFallback = false)
                }.getOrNull()?.takeIf { it.isNotEmpty() }
            } else {
                null
            }
            val fallback = staleCached ?: getOfflineFallbackForRepo(spec)
            VulkanDriverRepoGroup(
                spec = spec,
                releases = fallback,
                isLoading = false,
                fromCache = staleCached != null,
                error = if (fallback.isEmpty()) err.message else null,
            )
        }
    }

    private fun parseReleasesJson(
        spec: VulkanDriverRepoSpec,
        jsonText: String,
        isOfflineFallback: Boolean,
    ): List<VulkanDriverRelease> {
        val array = JSONArray(jsonText)
        val rawList = mutableListOf<Pair<Long, VulkanDriverRelease>>()

        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            if (obj.optBoolean("draft", false)) continue

            val tagName = obj.optString("tag_name").trim()
            val releaseName = obj.optString("name").trim()
            val title = if (spec.useTagName) {
                tagName.ifEmpty { releaseName }
            } else {
                releaseName.ifEmpty { tagName }
            }.ifEmpty { spec.displayName }

            val publishedRaw = obj.optString("published_at").trim()
            val publishedEpoch = parseIsoDateEpoch(publishedRaw)
            val publishedDate = publishedRaw.take(10)
            val notes = obj.optString("body").trim()

            val assetsJson = obj.optJSONArray("assets") ?: continue
            val assets = mutableListOf<VulkanDriverAsset>()
            for (j in 0 until assetsJson.length()) {
                val assetObj = assetsJson.optJSONObject(j) ?: continue
                val assetName = assetObj.optString("name").trim()
                val downloadUrl = assetObj.optString("browser_download_url").trim()
                val sizeBytes = assetObj.optLong("size", 0L)
                if (assetName.isEmpty() || downloadUrl.isEmpty()) continue
                if (!isSupportedDriverArchiveName(assetName, spec.id)) continue

                val extraMirrors = if (spec.id == "storm_driver" && assetName.equals("STORM_DRIVER_12.0.0.zip", ignoreCase = true)) {
                    STORM_DRIVER_MIRRORS
                } else {
                    emptyList()
                }
                assets.add(
                    VulkanDriverAsset(
                        name = assetName,
                        downloadUrl = downloadUrl,
                        sizeBytes = sizeBytes,
                        mirrorUrls = extraMirrors,
                    )
                )
            }
            if (assets.isEmpty()) continue

            rawList.add(
                publishedEpoch to VulkanDriverRelease(
                    id = "${spec.id}:${obj.optLong("id", i.toLong())}",
                    repoId = spec.id,
                    repoDisplayName = spec.displayName,
                    title = title,
                    tagName = tagName,
                    publishedAt = publishedDate,
                    releaseNotes = notes,
                    assets = assets,
                    isFromOfflineFallback = isOfflineFallback,
                )
            )
        }

        return if (spec.sortByPublishTime) {
            rawList.sortedByDescending { it.first }.map { it.second }
        } else {
            rawList.map { it.second }
        }
    }

    private fun isSupportedDriverArchiveName(name: String, repoId: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        if (repoId == "malisx2") {
            return lower.startsWith("malisx2-") &&
                lower.endsWith(".zip") &&
                !lower.endsWith(".sha256.zip") &&
                !lower.contains("source")
        }
        if (!lower.endsWith(".zip") && !lower.endsWith(".adpkg.zip") && !lower.endsWith(".adpkg")) {
            return false
        }
        return !lower.endsWith(".sha256.zip") && !lower.contains("source")
    }

    private fun parseIsoDateEpoch(isoDate: String): Long {
        if (isoDate.isBlank()) return 0L
        return runCatching {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            fmt.parse(isoDate)?.time ?: 0L
        }.getOrDefault(0L)
    }

    private fun getOfflineFallbackForRepo(spec: VulkanDriverRepoSpec): List<VulkanDriverRelease> {
        return when (spec.id) {
            "storm_driver" -> listOf(
                VulkanDriverRelease(
                    id = "offline:storm_driver:12.0.0",
                    repoId = spec.id,
                    repoDisplayName = spec.displayName,
                    title = "STORM DRIVER 12.0.0",
                    tagName = "12.0.0",
                    publishedAt = "2026",
                    releaseNotes = "Qualcomm Adreno 6xx/7xx/8xx unified multi-SO package (STORM GAMES WORLD + GitHub mirrors).",
                    assets = listOf(
                        VulkanDriverAsset(
                            name = "STORM_DRIVER_12.0.0.zip",
                            downloadUrl = STORM_DRIVER_MIRRORS.first(),
                            sizeBytes = 0L,
                            mirrorUrls = STORM_DRIVER_MIRRORS.drop(1),
                        )
                    ),
                    isFromOfflineFallback = true,
                )
            )
            else -> emptyList()
        }
    }
}
