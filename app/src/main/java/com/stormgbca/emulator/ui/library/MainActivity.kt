package com.stormgbca.emulator.ui.library

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.stormgbca.emulator.data.BoxArtRepository
import com.stormgbca.emulator.data.GbcaPrefs
import com.stormgbca.emulator.data.RomRepository
import com.stormgbca.emulator.model.RomEntry
import com.stormgbca.emulator.model.RomFilter
import com.stormgbca.emulator.model.RomSortMode
import com.stormgbca.emulator.model.RomViewMode
import com.stormgbca.emulator.ui.emulator.EmulatorActivity
import com.stormgbca.emulator.ui.settings.SettingsActivity
import com.stormgbca.emulator.ui.splash.Console3DBootScreen
import com.stormgbca.emulator.ui.theme.AppThemeManager
import com.stormgbca.emulator.ui.theme.StormWatermelonTheme
import com.stormgbca.emulator.update.StormUpdateDialog
import com.stormgbca.emulator.update.StormUpdateMirrorClient
import com.stormgbca.emulator.update.UpdateDownloadState
import com.stormgbca.emulator.update.UpdateReleaseInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import ru.stormgames.gbca.NativeCore

class MainActivity : AppCompatActivity() {
    private val json = Json { encodeDefaults = true }

    private val romsState = mutableStateOf<List<RomEntry>>(emptyList())
    private val boxArtMap = mutableStateMapOf<String, String>()
    private val filterState = mutableStateOf(RomFilter.ALL)
    private val sortModeState = mutableStateOf(RomSortMode.TITLE)
    private val viewModeState = mutableStateOf(RomViewMode.GRID)
    private val searchQueryState = mutableStateOf("")
    private val isScanningState = mutableStateOf(false)
    private val showSplashState = mutableStateOf(false)
    private val latestUpdateState = mutableStateOf<UpdateReleaseInfo?>(null)
    private val showUpdateDialogState = mutableStateOf(false)
    private val updateDownloadState = mutableStateOf<UpdateDownloadState>(UpdateDownloadState.Idle)

    private var pendingPatchRom: RomEntry? = null
    private var pendingCoverRom: RomEntry? = null

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            GbcaPrefs.addRomFolder(uri.toString())
            rescanLibrary()
        }
    }

    private val singleFilePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            openRomFromUri(uri)
        }
    }

    private val patchPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val rom = pendingPatchRom ?: return@registerForActivityResult
        pendingPatchRom = null
        if (uri != null) {
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val updated = romsState.value.map {
                if (it.uriString == rom.uriString) it.copy(softPatchUri = uri.toString()) else it
            }
            romsState.value = updated
            GbcaPrefs.saveCachedRoms(updated)
            Toast.makeText(this, "Патч привязан к игре", Toast.LENGTH_SHORT).show()
        }
    }

    private val coverPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val rom = pendingCoverRom ?: return@registerForActivityResult
        pendingCoverRom = null
        if (uri != null) {
            lifecycleScope.launch {
                val saved = BoxArtRepository.saveCustomCover(this@MainActivity, rom, uri)
                if (saved != null) {
                    boxArtMap[rom.uriString] = saved
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppThemeManager.applyEdgeToEdgeTheme(this)

        filterState.value = GbcaPrefs.getRomFilter()
        sortModeState.value = GbcaPrefs.getRomSortMode()
        viewModeState.value = GbcaPrefs.getRomViewMode()
        romsState.value = GbcaPrefs.getCachedRoms()
        showSplashState.value = savedInstanceState == null && GbcaPrefs.coreConfigState.value.showBootSplash

        setContent {
            StormWatermelonTheme {
                val filteredRoms = remember(
                    romsState.value,
                    filterState.value,
                    sortModeState.value,
                    searchQueryState.value,
                ) {
                    val q = searchQueryState.value.trim().lowercase()
                    romsState.value
                        .filter { rom ->
                            val matchesFilter = when (filterState.value) {
                                RomFilter.ALL -> true
                                RomFilter.GB -> rom.platform == NativeCore.PLATFORM_GB || rom.platform == NativeCore.PLATFORM_SGB
                                RomFilter.GBC -> rom.platform == NativeCore.PLATFORM_GBC
                                RomFilter.GBA -> rom.platform == NativeCore.PLATFORM_GBA
                                RomFilter.FAVORITES -> rom.isFavorite
                            }
                            val matchesQuery = q.isEmpty() ||
                                rom.displayTitle.lowercase().contains(q) ||
                                rom.internalTitle.lowercase().contains(q) ||
                                rom.gameCode.lowercase().contains(q)
                            matchesFilter && matchesQuery
                        }
                        .let { list ->
                            when (sortModeState.value) {
                                RomSortMode.TITLE -> list.sortedBy { it.displayTitle.lowercase() }
                                RomSortMode.RECENT -> list.sortedByDescending { it.lastPlayedEpochMs }
                                RomSortMode.PLATFORM -> list.sortedWith(compareBy({ it.platform }, { it.displayTitle.lowercase() }))
                                RomSortMode.PLAY_TIME -> list.sortedByDescending { it.totalPlaySeconds }
                            }
                        }
                }

                LaunchedEffect(romsState.value) {
                    preloadCovers(romsState.value)
                }

                if (showSplashState.value) {
                    Console3DBootScreen(
                        onFinished = { showSplashState.value = false },
                    )
                } else {
                    RomBrowserScreen(
                        roms = filteredRoms,
                        boxArtMap = boxArtMap,
                        filter = filterState.value,
                        sortMode = sortModeState.value,
                        viewMode = viewModeState.value,
                        searchQuery = searchQueryState.value,
                        isScanning = isScanningState.value,
                        hasUpdateAvailable = latestUpdateState.value != null,
                        onSearchQueryChange = { searchQueryState.value = it },
                        onFilterChange = { f ->
                            filterState.value = f
                            GbcaPrefs.setRomFilter(f)
                        },
                        onSortChange = { m ->
                            sortModeState.value = m
                            GbcaPrefs.setRomSortMode(m)
                        },
                        onToggleViewMode = {
                            val next = if (viewModeState.value == RomViewMode.GRID) RomViewMode.LIST else RomViewMode.GRID
                            viewModeState.value = next
                            GbcaPrefs.setRomViewMode(next)
                        },
                        onAddFolder = { folderPicker.launch(null) },
                        onOpenSingleFile = { singleFilePicker.launch(arrayOf("*/*")) },
                        onRefresh = { rescanLibrary() },
                        onOpenSettings = { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) },
                        onOpenUpdateDialog = { showUpdateDialogState.value = true },
                        onLaunchRom = { launchRom(it) },
                        onToggleFavorite = { rom ->
                            val updated = romsState.value.map {
                                if (it.uriString == rom.uriString) it.copy(isFavorite = !it.isFavorite) else it
                            }
                            romsState.value = updated
                            GbcaPrefs.saveCachedRoms(updated)
                        },
                        onSetCoreOverride = { rom, ov ->
                            val updated = romsState.value.map {
                                if (it.uriString == rom.uriString) it.copy(coreOverride = ov) else it
                            }
                            romsState.value = updated
                            GbcaPrefs.saveCachedRoms(updated)
                        },
                        onPickSoftPatch = { rom ->
                            pendingPatchRom = rom
                            patchPicker.launch(arrayOf("*/*"))
                        },
                        onRefreshBoxArt = { rom ->
                            lifecycleScope.launch {
                                val path = BoxArtRepository.fetchOrGetBoxArt(this@MainActivity, rom, forceRefresh = true)
                                if (path != null) boxArtMap[rom.uriString] = path
                            }
                        },
                        onPickCustomCover = { rom ->
                            pendingCoverRom = rom
                            coverPicker.launch(arrayOf("image/*"))
                        },
                    )
                }

                val updateRel = latestUpdateState.value
                if (showUpdateDialogState.value && updateRel != null) {
                    StormUpdateDialog(
                        release = updateRel,
                        downloadState = updateDownloadState.value,
                        onStartDownload = {
                            lifecycleScope.launch {
                                StormUpdateMirrorClient.downloadApk(this@MainActivity, updateRel) { st ->
                                    updateDownloadState.value = st
                                }
                            }
                        },
                        onInstallApk = { apk -> StormUpdateMirrorClient.installApk(this@MainActivity, apk) },
                        onSkipVersion = {
                            GbcaPrefs.setSkippedUpdateVersion(updateRel.version)
                            showUpdateDialogState.value = false
                        },
                        onDismiss = { showUpdateDialogState.value = false },
                    )
                }
            }
        }

        if (intent?.action == Intent.ACTION_VIEW && intent.data != null) {
            openRomFromUri(intent.data!!)
        } else {
            rescanLibrary()
            checkUpdatesQuietly()
        }
    }

    override fun onResume() {
        super.onResume()
        romsState.value = GbcaPrefs.getCachedRoms()
    }

    private fun rescanLibrary() {
        if (GbcaPrefs.getRomFolders().isEmpty()) return
        lifecycleScope.launch {
            isScanningState.value = true
            val scanned = RomRepository.scanFolders(this@MainActivity)
            romsState.value = scanned
            isScanningState.value = false
        }
    }

    private fun preloadCovers(roms: List<RomEntry>) {
        lifecycleScope.launch {
            for (rom in roms) {
                BoxArtRepository.getCachedBoxArtPath(this@MainActivity, rom)?.let {
                    boxArtMap[rom.uriString] = it
                    continue
                }
                val fetched = BoxArtRepository.fetchOrGetBoxArt(this@MainActivity, rom)
                if (fetched != null) {
                    boxArtMap[rom.uriString] = fetched
                }
            }
        }
    }

    private fun openRomFromUri(uri: Uri) {
        runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        lifecycleScope.launch {
            val entry = withContext(Dispatchers.IO) { RomRepository.inspectUri(this@MainActivity, uri) }
            if (entry != null) {
                launchRom(entry)
            } else {
                Toast.makeText(this@MainActivity, "Неподдерживаемый файл ROM", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun launchRom(rom: RomEntry) {
        val intent = Intent(this, EmulatorActivity::class.java).apply {
            putExtra(EmulatorActivity.EXTRA_ROM_JSON, json.encodeToString(rom))
        }
        startActivity(intent)
    }

    private fun checkUpdatesQuietly() {
        lifecycleScope.launch {
            val rel = StormUpdateMirrorClient.checkLatestUpdate()
            if (rel != null && rel.version != GbcaPrefs.getSkippedUpdateVersion()) {
                latestUpdateState.value = rel
            }
        }
    }
}
