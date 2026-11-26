package com.stormgbca.emulator.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.preference.PreferenceManager
import com.stormgbca.emulator.model.CheatItem
import com.stormgbca.emulator.model.ConsoleSystem
import com.stormgbca.emulator.model.GbcaCoreConfig
import com.stormgbca.emulator.model.PhysicalAction
import com.stormgbca.emulator.model.RomEntry
import com.stormgbca.emulator.model.RomFilter
import com.stormgbca.emulator.model.RomSortMode
import com.stormgbca.emulator.model.RomViewMode
import com.stormgbca.emulator.model.SystemOverlayProfile
import kotlinx.serialization.json.Json
import ru.stormgames.gbca.NativeCore

object GbcaPrefs {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var prefs: SharedPreferences

    val coreConfigState = mutableStateOf(GbcaCoreConfig())
    val gbSkinState = mutableStateOf("gb_storm_aurora")
    val gbcSkinState = mutableStateOf("gbc_storm_aurora")
    val gbaSkinState = mutableStateOf("gba_storm_aurora")
    val gbSkinDimmingState = mutableIntStateOf(100)
    val gbcSkinDimmingState = mutableIntStateOf(100)
    val gbaSkinDimmingState = mutableIntStateOf(100)

    val gbOverlayState = mutableStateOf(defaultOverlayFor(ConsoleSystem.GB))
    val gbcOverlayState = mutableStateOf(defaultOverlayFor(ConsoleSystem.GBC))
    val gbaOverlayState = mutableStateOf(defaultOverlayFor(ConsoleSystem.GBA))

    fun defaultOverlayFor(system: ConsoleSystem): SystemOverlayProfile = when (system) {
        ConsoleSystem.GB -> SystemOverlayProfile(
            buttonStyle = "dmg_magenta",
            shaderOverlay = NativeCore.FILTER_LCD_GRID,
            showShoulders = false,
        )
        ConsoleSystem.GBC -> SystemOverlayProfile(
            buttonStyle = "gbc_atomic_purple",
            shaderOverlay = NativeCore.FILTER_RGB_SUBPIXEL,
            showShoulders = false,
        )
        ConsoleSystem.GBA -> SystemOverlayProfile(
            buttonStyle = "gba_indigo",
            shaderOverlay = NativeCore.FILTER_RGB_SUBPIXEL,
            showShoulders = true,
        )
    }

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

        coreConfigState.value = loadCoreConfig()
        gbSkinState.value = prefs.getString("gb_console_skin", "gb_storm_aurora") ?: "gb_storm_aurora"
        gbcSkinState.value = prefs.getString("gbc_console_skin", "gbc_storm_aurora") ?: "gbc_storm_aurora"
        gbaSkinState.value = prefs.getString("gba_console_skin", "gba_storm_aurora") ?: "gba_storm_aurora"

        gbSkinDimmingState.intValue = prefs.getInt("gb_skin_dimming", 100).coerceIn(30, 100)
        gbcSkinDimmingState.intValue = prefs.getInt("gbc_skin_dimming", 100).coerceIn(30, 100)
        gbaSkinDimmingState.intValue = prefs.getInt("gba_skin_dimming", 100).coerceIn(30, 100)

        gbOverlayState.value = loadOverlayProfile(ConsoleSystem.GB)
        gbcOverlayState.value = loadOverlayProfile(ConsoleSystem.GBC)
        gbaOverlayState.value = loadOverlayProfile(ConsoleSystem.GBA)
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
            runCatching { return json.decodeFromString<SystemOverlayProfile>(raw) }
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

    fun getRomFolders(): Set<String> = prefs.getStringSet("rom_folder_uris", emptySet()) ?: emptySet()

    fun addRomFolder(uriString: String) {
        val set = getRomFolders().toMutableSet()
        set.add(uriString)
        prefs.edit().putStringSet("rom_folder_uris", set).apply()
    }

    fun removeRomFolder(uriString: String) {
        val set = getRomFolders().toMutableSet()
        set.remove(uriString)
        prefs.edit().putStringSet("rom_folder_uris", set).apply()
    }

    fun getCachedRoms(): List<RomEntry> {
        val raw = prefs.getString("cached_roms_json", null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<RomEntry>>(raw) }.getOrDefault(emptyList())
    }

    fun saveCachedRoms(roms: List<RomEntry>) {
        prefs.edit().putString("cached_roms_json", json.encodeToString(roms)).apply()
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

    fun resetKeyMappings() {
        val edit = prefs.edit()
        PhysicalAction.entries.forEach { edit.remove("key_map_${it.name}") }
        edit.apply()
    }

    fun getRaUsername(): String = prefs.getString("ra_username", "") ?: ""
    fun getRaToken(): String = prefs.getString("ra_token", "") ?: ""
    fun isRaHardcore(): Boolean = prefs.getBoolean("ra_hardcore", false)
    fun setRaCredentials(username: String, token: String) {
        prefs.edit().putString("ra_username", username).putString("ra_token", token).apply()
    }
    fun setRaHardcore(hardcore: Boolean) {
        prefs.edit().putBoolean("ra_hardcore", hardcore).apply()
    }

    fun getSkippedUpdateVersion(): String = prefs.getString("skipped_update_version", "") ?: ""
    fun setSkippedUpdateVersion(ver: String) {
        prefs.edit().putString("skipped_update_version", ver).apply()
    }

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
