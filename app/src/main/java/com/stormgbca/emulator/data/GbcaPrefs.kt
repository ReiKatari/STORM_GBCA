package com.stormgbca.emulator.data

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.os.LocaleListCompat
import androidx.preference.PreferenceManager
import com.stormgbca.emulator.model.CheatItem
import com.stormgbca.emulator.model.ConsoleSystem
import com.stormgbca.emulator.model.GbcaCoreConfig
import com.stormgbca.emulator.model.OrientationControlsLayout
import com.stormgbca.emulator.model.OverlayLayoutPreset
import com.stormgbca.emulator.model.PhysicalAction
import com.stormgbca.emulator.model.RomEntry
import com.stormgbca.emulator.model.RomFilter
import com.stormgbca.emulator.model.RomSortMode
import com.stormgbca.emulator.model.RomViewMode
import com.stormgbca.emulator.model.SystemOverlayProfile
import com.stormgbca.emulator.vulkandriver.InstalledVulkanDriver
import kotlinx.serialization.json.Json
import ru.stormgames.gbca.NativeCore

object GbcaPrefs {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var prefs: SharedPreferences

    val coreConfigState = mutableStateOf(GbcaCoreConfig())
    val gbSkinState = mutableStateOf("dmg_classic")
    val gbcSkinState = mutableStateOf("gbc_atomic_purple")
    val gbaSkinState = mutableStateOf("gba_indigo")
    val gbSkinDimmingState = mutableIntStateOf(100)
    val gbcSkinDimmingState = mutableIntStateOf(100)
    val gbaSkinDimmingState = mutableIntStateOf(100)

    val gbOverlayState = mutableStateOf(defaultOverlayFor(ConsoleSystem.GB))
    val gbcOverlayState = mutableStateOf(defaultOverlayFor(ConsoleSystem.GBC))
    val gbaOverlayState = mutableStateOf(defaultOverlayFor(ConsoleSystem.GBA))
    val romFoldersState = mutableStateOf<List<String>>(emptyList())
    val raHardcoreState = mutableStateOf(false)
    val installedVulkanDriversState = mutableStateOf<List<InstalledVulkanDriver>>(emptyList())
    private var romFoldersDirty: Boolean = false

    fun consumeRomFoldersDirty(): Boolean {
        val d = romFoldersDirty
        romFoldersDirty = false
        return d
    }

    var detectedTotalRamMb: Long = 4096L
        private set
    var detectedPrimaryAbi: String = "arm64-v8a"
        private set
    var isLowEndDevice: Boolean = false
        private set

    fun defaultOverlayFor(system: ConsoleSystem): SystemOverlayProfile = when (system) {
        ConsoleSystem.GB -> SystemOverlayProfile(
            layoutVersion = 4,
            buttonStyle = "dmg_magenta",
            shaderOverlay = NativeCore.FILTER_LCD_GRID,
            showShoulders = false,
            portraitControls = OrientationControlsLayout.defaultPortrait(hasShoulders = false),
            landscapeControls = OrientationControlsLayout.defaultLandscape(),
        )
        ConsoleSystem.GBC -> SystemOverlayProfile(
            layoutVersion = 4,
            buttonStyle = "gbc_atomic_purple",
            shaderOverlay = NativeCore.FILTER_RGB_SUBPIXEL,
            showShoulders = false,
            portraitControls = OrientationControlsLayout.defaultPortrait(hasShoulders = false),
            landscapeControls = OrientationControlsLayout.defaultLandscape(),
        )
        ConsoleSystem.GBA -> SystemOverlayProfile(
            layoutVersion = 4,
            buttonStyle = "gba_indigo",
            shaderOverlay = NativeCore.FILTER_RGB_SUBPIXEL,
            showShoulders = true,
            portraitControls = OrientationControlsLayout.defaultPortrait(hasShoulders = true),
            landscapeControls = OrientationControlsLayout.defaultLandscape(),
        )
    }

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        val appCtx = context.applicationContext
        prefs = PreferenceManager.getDefaultSharedPreferences(appCtx)

        detectDeviceCapabilities(appCtx)

        var loaded = loadCoreConfig()
        // Auto-select optimal profile on first run for old vs modern devices
        if (loaded.optimizationProfile < 0) {
            val autoProfile = if (isLowEndDevice) NativeCore.OPT_LITE else NativeCore.OPT_BALANCED
            loaded = if (autoProfile == NativeCore.OPT_LITE) {
                loaded.copy(
                    optimizationProfile = NativeCore.OPT_LITE,
                    rewindBufferMb = 4,
                    rewindInterval = 2,
                    audioInterpMode = NativeCore.INTERP_NEAREST,
                    lcdGhosting = 0,
                    runAheadFrames = 0,
                    resolutionScale = 1,
                )
            } else {
                loaded.copy(optimizationProfile = NativeCore.OPT_BALANCED)
            }
            prefs.edit().putString("core_config_json", json.encodeToString(loaded)).apply()
        }
        coreConfigState.value = loaded

        // Apply saved app language immediately
        applyAppLanguage(loaded.appLanguage)

        gbSkinState.value = prefs.getString("gb_console_skin", "dmg_classic") ?: "dmg_classic"
        gbcSkinState.value = prefs.getString("gbc_console_skin", "gbc_atomic_purple") ?: "gbc_atomic_purple"
        gbaSkinState.value = prefs.getString("gba_console_skin", "gba_indigo") ?: "gba_indigo"

        gbSkinDimmingState.intValue = prefs.getInt("gb_skin_dimming", 100).coerceIn(30, 100)
        gbcSkinDimmingState.intValue = prefs.getInt("gbc_skin_dimming", 100).coerceIn(30, 100)
        gbaSkinDimmingState.intValue = prefs.getInt("gba_skin_dimming", 100).coerceIn(30, 100)

        gbOverlayState.value = loadOverlayProfile(ConsoleSystem.GB)
        gbcOverlayState.value = loadOverlayProfile(ConsoleSystem.GBC)
        gbaOverlayState.value = loadOverlayProfile(ConsoleSystem.GBA)
        romFoldersState.value = loadRomFolders()
        raHardcoreState.value = prefs.getBoolean("ra_hardcore", false)
        installedVulkanDriversState.value = loadInstalledVulkanDrivers()
    }

    private fun detectDeviceCapabilities(context: Context) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val mi = ActivityManager.MemoryInfo()
        if (am != null) {
            am.getMemoryInfo(mi)
            detectedTotalRamMb = (mi.totalMem / (1024L * 1024L)).coerceAtLeast(512L)
        }
        detectedPrimaryAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        val is32BitOnly = Build.SUPPORTED_64_BIT_ABIS.isEmpty()
        isLowEndDevice = is32BitOnly || detectedTotalRamMb < 3200L || (am?.isLowRamDevice == true)
    }

    fun applyAppLanguage(tag: String) {
        val locales = if (tag.isBlank() || tag == "system") {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        if (AppCompatDelegate.getApplicationLocales() != locales) {
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }

    fun getEffectiveOptimizationProfile(): Int {
        val p = coreConfigState.value.optimizationProfile
        return if (p in 0..3) p else if (isLowEndDevice) NativeCore.OPT_LITE else NativeCore.OPT_BALANCED
    }

    private fun loadCoreConfig(): GbcaCoreConfig {
        val raw = prefs.getString("core_config_json", null)
        if (!raw.isNullOrBlank()) {
            runCatching { return json.decodeFromString<GbcaCoreConfig>(raw) }
        }
        return GbcaCoreConfig()
    }

    fun updateCoreConfig(transform: (GbcaCoreConfig) -> GbcaCoreConfig) {
        val next = transform(coreConfigState.value)
        coreConfigState.value = next
        prefs.edit().putString("core_config_json", json.encodeToString(next)).apply()
    }

    fun getSkinKey(system: ConsoleSystem): String = when (system) {
        ConsoleSystem.GB -> gbSkinState.value
        ConsoleSystem.GBC -> gbcSkinState.value
        ConsoleSystem.GBA -> gbaSkinState.value
    }

    fun setSkinKey(system: ConsoleSystem, key: String) {
        when (system) {
            ConsoleSystem.GB -> {
                gbSkinState.value = key
                prefs.edit().putString("gb_console_skin", key).apply()
            }
            ConsoleSystem.GBC -> {
                gbcSkinState.value = key
                prefs.edit().putString("gbc_console_skin", key).apply()
            }
            ConsoleSystem.GBA -> {
                gbaSkinState.value = key
                prefs.edit().putString("gba_console_skin", key).apply()
            }
        }
    }

    fun getSkinDimming(system: ConsoleSystem): Int = when (system) {
        ConsoleSystem.GB -> gbSkinDimmingState.intValue
        ConsoleSystem.GBC -> gbcSkinDimmingState.intValue
        ConsoleSystem.GBA -> gbaSkinDimmingState.intValue
    }

    fun setSkinDimming(system: ConsoleSystem, percent: Int) {
        val clamped = percent.coerceIn(30, 100)
        when (system) {
            ConsoleSystem.GB -> {
                gbSkinDimmingState.intValue = clamped
                prefs.edit().putInt("gb_skin_dimming", clamped).apply()
            }
            ConsoleSystem.GBC -> {
                gbcSkinDimmingState.intValue = clamped
                prefs.edit().putInt("gbc_skin_dimming", clamped).apply()
            }
            ConsoleSystem.GBA -> {
                gbaSkinDimmingState.intValue = clamped
                prefs.edit().putInt("gba_skin_dimming", clamped).apply()
            }
        }
    }

    private fun overlayPrefKey(system: ConsoleSystem): String = when (system) {
        ConsoleSystem.GB -> "gb_overlay_profile_v1"
        ConsoleSystem.GBC -> "gbc_overlay_profile_v1"
        ConsoleSystem.GBA -> "gba_overlay_profile_v1"
    }

    private fun loadOverlayProfile(system: ConsoleSystem): SystemOverlayProfile {
        val raw = prefs.getString(overlayPrefKey(system), null)
        if (!raw.isNullOrBlank()) {
            val decoded = runCatching { json.decodeFromString<SystemOverlayProfile>(raw) }.getOrNull()
            if (decoded != null) {
                if (decoded.layoutVersion < 4) {
                    val hasLR = decoded.showShoulders || system == ConsoleSystem.GBA
                    val isCustom = decoded.layoutPreset.equals(OverlayLayoutPreset.CUSTOM.key, ignoreCase = true)
                    val migrated = if (decoded.layoutVersion < 2 || !isCustom) {
                        decoded.copy(
                            layoutVersion = 4,
                            abSpacingPercent = 150,
                            portraitControls = OrientationControlsLayout.defaultPortrait(hasShoulders = hasLR),
                            landscapeControls = OrientationControlsLayout.defaultLandscape(),
                        )
                    } else {
                        decoded.copy(layoutVersion = 4)
                    }
                    prefs.edit().putString(overlayPrefKey(system), json.encodeToString(migrated)).apply()
                    return migrated
                }
                return decoded
            }
        }
        return defaultOverlayFor(system)
    }

    fun getOverlayProfile(system: ConsoleSystem): SystemOverlayProfile = when (system) {
        ConsoleSystem.GB -> gbOverlayState.value
        ConsoleSystem.GBC -> gbcOverlayState.value
        ConsoleSystem.GBA -> gbaOverlayState.value
    }

    fun updateOverlayProfile(system: ConsoleSystem, transform: (SystemOverlayProfile) -> SystemOverlayProfile) {
        val next = transform(getOverlayProfile(system))
        when (system) {
            ConsoleSystem.GB -> gbOverlayState.value = next
            ConsoleSystem.GBC -> gbcOverlayState.value = next
            ConsoleSystem.GBA -> gbaOverlayState.value = next
        }
        prefs.edit().putString(overlayPrefKey(system), json.encodeToString(next)).apply()
    }

    private fun loadRomFolders(): List<String> {
        val raw = prefs.getString("rom_folder_uris_json", null)
        if (!raw.isNullOrBlank()) {
            val parsed = runCatching { json.decodeFromString<List<String>>(raw) }.getOrNull()
            if (parsed != null) {
                return parsed.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            }
        }
        val legacy = prefs.getStringSet("rom_folder_uris", null)
        if (!legacy.isNullOrEmpty()) {
            val migrated = legacy.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            saveRomFolders(migrated)
            return migrated
        }
        return emptyList()
    }

    private fun saveRomFolders(list: List<String>) {
        val clean = list.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        prefs.edit()
            .putString("rom_folder_uris_json", json.encodeToString(clean))
            .putStringSet("rom_folder_uris", HashSet(clean))
            .apply()
    }

    fun getRomFolders(): Set<String> = romFoldersState.value.toSet()

    fun getRomFoldersList(): List<String> = romFoldersState.value

    fun addRomFolder(uriString: String) {
        val trimmed = uriString.trim()
        if (trimmed.isBlank()) return
        val current = romFoldersState.value
        if (trimmed in current) return
        val next = current + trimmed
        saveRomFolders(next)
        romFoldersState.value = next
        romFoldersDirty = true
    }

    fun removeRomFolder(uriString: String) {
        val current = romFoldersState.value
        if (uriString !in current) return
        val next = current.filterNot { it == uriString }
        saveRomFolders(next)
        romFoldersState.value = next
        romFoldersDirty = true
    }

    fun getCachedRoms(): List<RomEntry> {
        val raw = prefs.getString("cached_roms_json", null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<RomEntry>>(raw) }.getOrDefault(emptyList())
    }

    fun saveCachedRoms(roms: List<RomEntry>) {
        prefs.edit().putString("cached_roms_json", json.encodeToString(roms)).apply()
    }

    // Persistent cache mapping MD5/RA hash -> RA GameID (0 = verified no RA set, >0 = verified RA set)
    fun getCachedRaGameIds(): MutableMap<String, Int> {
        val raw = prefs.getString("ra_hash_gameid_cache_v1", null) ?: return mutableMapOf()
        return runCatching { json.decodeFromString<Map<String, Int>>(raw).toMutableMap() }.getOrDefault(mutableMapOf())
    }

    fun saveCachedRaGameIds(map: Map<String, Int>) {
        prefs.edit().putString("ra_hash_gameid_cache_v1", json.encodeToString(map)).apply()
    }

    fun getCheatsForRom(romKey: String): List<CheatItem> {
        val raw = prefs.getString("cheats_$romKey", null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<CheatItem>>(raw) }.getOrDefault(emptyList())
    }

    fun saveCheatsForRom(romKey: String, cheats: List<CheatItem>) {
        prefs.edit().putString("cheats_$romKey", json.encodeToString(cheats)).apply()
    }

    fun getRomFilter(): RomFilter {
        val name = prefs.getString("rom_filter", RomFilter.ALL.name) ?: RomFilter.ALL.name
        return runCatching { RomFilter.valueOf(name) }.getOrDefault(RomFilter.ALL)
    }

    fun setRomFilter(filter: RomFilter) {
        prefs.edit().putString("rom_filter", filter.name).apply()
    }

    fun getRomSortMode(): RomSortMode {
        val name = prefs.getString("rom_sort_mode", RomSortMode.TITLE.name) ?: RomSortMode.TITLE.name
        return runCatching { RomSortMode.valueOf(name) }.getOrDefault(RomSortMode.TITLE)
    }

    fun setRomSortMode(mode: RomSortMode) {
        prefs.edit().putString("rom_sort_mode", mode.name).apply()
    }

    fun getRomViewMode(): RomViewMode {
        val name = prefs.getString("rom_view_mode", RomViewMode.GRID.name) ?: RomViewMode.GRID.name
        return runCatching { RomViewMode.valueOf(name) }.getOrDefault(RomViewMode.GRID)
    }

    fun setRomViewMode(mode: RomViewMode) {
        prefs.edit().putString("rom_view_mode", mode.name).apply()
    }

    fun getKeyCodeForAction(action: PhysicalAction): Int =
        prefs.getInt("key_map_${action.name}", action.defaultKeyCode)

    fun setKeyCodeForAction(action: PhysicalAction, keyCode: Int) {
        prefs.edit().putInt("key_map_${action.name}", keyCode).apply()
    }

    fun clearKeyCodeForAction(action: PhysicalAction) {
        prefs.edit().putInt("key_map_${action.name}", 0).apply()
    }

    fun clearAllKeyMappings() {
        val edit = prefs.edit()
        PhysicalAction.entries.forEach { edit.putInt("key_map_${it.name}", 0) }
        edit.apply()
    }

    fun resetKeyMappings() {
        val edit = prefs.edit()
        PhysicalAction.entries.forEach { edit.remove("key_map_${it.name}") }
        edit.apply()
    }

    // --- Pause Menu Tile Customization (STORM DS parity) ---
    fun getHiddenPauseTiles(): Set<String> = prefs.getStringSet("pause_menu_hidden_tiles", emptySet()) ?: emptySet()
    fun setHiddenPauseTiles(keys: Set<String>) {
        prefs.edit().putStringSet("pause_menu_hidden_tiles", keys).apply()
    }
    fun getActiveQuickSlot(): Int = prefs.getInt("active_quick_slot", 1).coerceIn(1, 10)
    fun setActiveQuickSlot(slot: Int) {
        prefs.edit().putInt("active_quick_slot", slot.coerceIn(1, 10)).apply()
    }

    // --- RetroAchievements full settings (STORM DS parity) ---
    fun getRaUsername(): String = prefs.getString("ra_username", "") ?: ""
    fun getRaToken(): String = prefs.getString("ra_token", "") ?: ""
    fun isRaEnabled(): Boolean = prefs.getBoolean("ra_enabled", true)
    fun isRaHardcore(): Boolean = prefs.getBoolean("ra_hardcore", false)
    fun isRaRichPresence(): Boolean = prefs.getBoolean("ra_rich_presence", true)
    fun isRaEncore(): Boolean = prefs.getBoolean("ra_encore", false)
    fun isRaUnofficial(): Boolean = prefs.getBoolean("ra_unofficial", false)
    fun isRaNotifications(): Boolean = prefs.getBoolean("ra_notifications", true)
    fun isRaOfflineSoftcore(): Boolean = prefs.getBoolean("ra_offline_softcore", true)
    fun isRaChallengeIndicators(): Boolean = prefs.getBoolean("ra_challenge_indicators", true)
    fun isRaProgressIndicators(): Boolean = prefs.getBoolean("ra_progress_indicators", true)
    fun isRaLeaderboardIndicators(): Boolean = prefs.getBoolean("ra_leaderboard_indicators", true)
    fun isLeaderboardPopupsEnabled(): Boolean = isRaLeaderboardIndicators()
    fun getRaNotifPosition(): String = prefs.getString("ra_notif_position", "top") ?: "top"
    fun getRaNotifStyle(): String = prefs.getString("ra_notif_style", "detailed") ?: "detailed"
    fun getRaNotifSound(): String = prefs.getString("ra_notif_sound", "chime") ?: "chime"
    fun getRaPendingQueueCount(): Int {
        val raw = getRaPendingQueueJson()
        return runCatching { org.json.JSONArray(raw).length() }.getOrDefault(0)
    }
    fun getRaPoints(): Int = prefs.getInt("ra_points", 0)
    fun getRaSoftcorePoints(): Int = prefs.getInt("ra_softcore_points", 0)
    fun getRaRank(): Int = prefs.getInt("ra_rank", 0)
    fun getRaMotto(): String = prefs.getString("ra_motto", "") ?: ""

    fun setRaCredentials(username: String, token: String) {
        prefs.edit().putString("ra_username", username).putString("ra_token", token).apply()
    }
    fun setRaProfileStats(points: Int, softcorePoints: Int, rank: Int, motto: String) {
        prefs.edit()
            .putInt("ra_points", points)
            .putInt("ra_softcore_points", softcorePoints)
            .putInt("ra_rank", rank)
            .putString("ra_motto", motto)
            .apply()
    }
    fun setRaEnabled(v: Boolean) { prefs.edit().putBoolean("ra_enabled", v).apply() }
    fun setRaHardcore(v: Boolean) {
        raHardcoreState.value = v
        prefs.edit().putBoolean("ra_hardcore", v).apply()
    }
    fun setRaRichPresence(v: Boolean) { prefs.edit().putBoolean("ra_rich_presence", v).apply() }
    fun setRaEncore(v: Boolean) { prefs.edit().putBoolean("ra_encore", v).apply() }
    fun setRaUnofficial(v: Boolean) { prefs.edit().putBoolean("ra_unofficial", v).apply() }
    fun setRaNotifications(v: Boolean) { prefs.edit().putBoolean("ra_notifications", v).apply() }
    fun setRaOfflineSoftcore(v: Boolean) { prefs.edit().putBoolean("ra_offline_softcore", v).apply() }
    fun setRaChallengeIndicators(v: Boolean) { prefs.edit().putBoolean("ra_challenge_indicators", v).apply() }
    fun setRaProgressIndicators(v: Boolean) { prefs.edit().putBoolean("ra_progress_indicators", v).apply() }
    fun setRaLeaderboardIndicators(v: Boolean) { prefs.edit().putBoolean("ra_leaderboard_indicators", v).apply() }
    fun setLeaderboardPopupsEnabled(v: Boolean) { setRaLeaderboardIndicators(v) }
    fun setRaNotifPosition(v: String) { prefs.edit().putString("ra_notif_position", v).apply() }
    fun setRaNotifStyle(v: String) { prefs.edit().putString("ra_notif_style", v).apply() }
    fun setRaNotifSound(v: String) { prefs.edit().putString("ra_notif_sound", v).apply() }

    fun getRaPendingQueueJson(): String {
        return try {
            prefs.getString("ra_pending_queue", "[]")?.takeIf { it.isNotBlank() } ?: "[]"
        } catch (_: ClassCastException) {
            // Migrate legacy StringSet queue to JSON array format
            val legacy = runCatching { prefs.getStringSet("ra_pending_queue", emptySet()) }.getOrNull().orEmpty()
            val arr = org.json.JSONArray()
            for (item in legacy) {
                val id = item.toIntOrNull() ?: continue
                arr.put(org.json.JSONObject().apply {
                    put("kind", "achievement")
                    put("id", id)
                    put("hardcore", false)
                    put("score", 0)
                    put("hash", "")
                    put("timestamp", System.currentTimeMillis())
                })
            }
            val migrated = arr.toString()
            prefs.edit().remove("ra_pending_queue").putString("ra_pending_queue", migrated).apply()
            migrated
        }
    }

    fun saveRaPendingQueueJson(jsonArrayStr: String) {
        prefs.edit().putString("ra_pending_queue", jsonArrayStr).apply()
    }

    fun clearRaPendingQueue() { prefs.edit().remove("ra_pending_queue").apply() }

    fun clearRaAccount() {
        prefs.edit()
            .remove("ra_username")
            .remove("ra_token")
            .remove("ra_points")
            .remove("ra_softcore_points")
            .remove("ra_rank")
            .remove("ra_motto")
            .apply()
    }

    fun getSkippedUpdateVersion(): String = prefs.getString("skipped_update_version", "") ?: ""
    fun setSkippedUpdateVersion(ver: String) {
        prefs.edit().putString("skipped_update_version", ver).apply()
    }

    private fun loadInstalledVulkanDrivers(): List<InstalledVulkanDriver> {
        val raw = prefs.getString("installed_vulkan_drivers", null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<InstalledVulkanDriver>>(raw) }
            .getOrDefault(emptyList())
            .filter { java.io.File(it.soPath).isFile }
    }

    fun getInstalledVulkanDrivers(): List<InstalledVulkanDriver> {
        if (!::prefs.isInitialized) return emptyList()
        return installedVulkanDriversState.value
    }

    fun saveInstalledVulkanDrivers(drivers: List<InstalledVulkanDriver>) {
        installedVulkanDriversState.value = drivers
        prefs.edit().putString("installed_vulkan_drivers", json.encodeToString(drivers)).apply()
    }

    fun getFsrSharpness(): Int = coreConfigState.value.fsrSharpness.coerceIn(0, 100)
    fun getFsrQualityMode(): Int = coreConfigState.value.fsrQualityMode.coerceIn(0, 3)
    fun isFrameGenerationEnabled(): Boolean = coreConfigState.value.frameGenerationEnabled

    fun exportAllPrefsJson(): String = buildString {
        appendLine("{")
        appendLine("  \"gbSkin\": \"${gbSkinState.value}\",")
        appendLine("  \"gbcSkin\": \"${gbcSkinState.value}\",")
        appendLine("  \"gbaSkin\": \"${gbaSkinState.value}\",")
        appendLine("  \"coreConfig\": ${json.encodeToString(coreConfigState.value)},")
        appendLine("  \"gbOverlay\": ${json.encodeToString(gbOverlayState.value)},")
        appendLine("  \"gbcOverlay\": ${json.encodeToString(gbcOverlayState.value)},")
        appendLine("  \"gbaOverlay\": ${json.encodeToString(gbaOverlayState.value)}")
        append("}")
    }
}
