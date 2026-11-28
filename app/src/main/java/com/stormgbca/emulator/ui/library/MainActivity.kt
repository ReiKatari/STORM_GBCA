package com.stormgbca.emulator.ui.library

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.lifecycleScope
import com.stormgbca.emulator.R
import com.stormgbca.emulator.data.BoxArtRepository
import com.stormgbca.emulator.data.GbcaPrefs
import com.stormgbca.emulator.data.RaGameSession
import com.stormgbca.emulator.data.RetroAchievementsClient
import com.stormgbca.emulator.data.RomRepository
import com.stormgbca.emulator.data.ShaderRepository
import com.stormgbca.emulator.model.RomEntry
import com.stormgbca.emulator.model.RomFilter
import com.stormgbca.emulator.model.RomSortMode
import com.stormgbca.emulator.model.RomViewMode
import com.stormgbca.emulator.sgw.SgwCatalogGame
import com.stormgbca.emulator.sgw.SgwCatalogRepository
import com.stormgbca.emulator.sgw.SgwCatalogSheet
import com.stormgbca.emulator.sgw.SgwDownloadState
import com.stormgbca.emulator.sgw.SgwGameUpdateInfo
import com.stormgbca.emulator.ui.emulator.EmulatorActivity
import com.stormgbca.emulator.ui.ra.GameAchievementsDialog
import com.stormgbca.emulator.ui.ra.RetroAchievementsAccountDialog
import com.stormgbca.emulator.ui.settings.SettingsActivity
import com.stormgbca.emulator.ui.theme.AppThemeManager
import com.stormgbca.emulator.ui.theme.SpaceGrotesk
import com.stormgbca.emulator.ui.theme.StormWatermelonTheme
import com.stormgbca.emulator.ui.theme.WatermelonMono
import com.stormgbca.emulator.ui.theme.watermelon
import com.stormgbca.emulator.update.StormUpdateDialog
import com.stormgbca.emulator.update.StormUpdateMirrorClient
import com.stormgbca.emulator.update.UpdateCheckResult
import com.stormgbca.emulator.update.UpdateDownloadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import ru.stormgames.gbca.NativeCore
import java.util.concurrent.ConcurrentHashMap

class MainActivity : AppCompatActivity() {
    private val json = Json { encodeDefaults = true }

    private val romsState = mutableStateOf<List<RomEntry>>(emptyList())
    private val boxArtMap = mutableStateMapOf<String, String>()
    private val confirmedRaHashesState = mutableStateOf<Set<String>>(emptySet())
    private val filterState = mutableStateOf(RomFilter.ALL)
    private val sortModeState = mutableStateOf(RomSortMode.TITLE)
    private val viewModeState = mutableStateOf(RomViewMode.GRID)
    private val searchQueryState = mutableStateOf("")
    private val isScanningState = mutableStateOf(false)
    private val latestUpdateState = mutableStateOf<UpdateCheckResult?>(null)
    private val showUpdateDialogState = mutableStateOf(false)
    private val updateDownloadState = mutableStateOf<UpdateDownloadState>(UpdateDownloadState.Idle)
    private val showRaAccountDialogState = mutableStateOf(false)
    private val raGameDialogRomState = mutableStateOf<RomEntry?>(null)
    private val raGameSessionState = mutableStateOf<RaGameSession?>(null)
    private val raGameLoadingState = mutableStateOf(false)
    private val showSgwCatalogState = mutableStateOf(false)
    private val sgwCatalogState = mutableStateOf<List<SgwCatalogGame>>(emptyList())
    private val sgwUpdatesMapState = mutableStateOf<Map<String, SgwGameUpdateInfo>>(emptyMap())
    private val sgwQuickDownloadState = mutableStateOf(SgwDownloadState())

    private var coverLoadJob: Job? = null
    private var sgwUpdateCheckJob: Job? = null
    private var sgwQuickUpdateJob: Job? = null
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
            Toast.makeText(this, getString(R.string.toast_patch_linked), Toast.LENGTH_SHORT).show()
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
        supportActionBar?.hide()
        AppThemeManager.applyEdgeToEdgeTheme(this)

        lifecycleScope.launch(Dispatchers.IO) {
            ShaderRepository.ensureExtracted(this@MainActivity)
        }

        filterState.value = GbcaPrefs.getRomFilter()
        sortModeState.value = GbcaPrefs.getRomSortMode()
        viewModeState.value = GbcaPrefs.getRomViewMode()
        romsState.value = GbcaPrefs.getCachedRoms()

        setContent {
            StormWatermelonTheme {
                val coreCfg = GbcaPrefs.coreConfigState.value
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

                // Re-evaluate covers whenever ROM list OR either cover toggle changes!
                LaunchedEffect(romsState.value, coreCfg.showLibretroCovers, coreCfg.showRaCovers) {
                    preloadCovers(romsState.value, coreCfg.showLibretroCovers, coreCfg.showRaCovers)
                }

                // Verify real RetroAchievements game support per ROM when RA badge is enabled
                LaunchedEffect(romsState.value, coreCfg.showRaBadge) {
                    if (coreCfg.showRaBadge && romsState.value.isNotEmpty()) {
                        verifyRaBadges(romsState.value)
                    }
                }

                RomBrowserScreen(
                    roms = filteredRoms,
                    boxArtMap = boxArtMap,
                    confirmedRaHashes = confirmedRaHashesState.value,
                    gameUpdatesMap = sgwUpdatesMapState.value,
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
                    onOpenCatalog = { showSgwCatalogState.value = true },
                    onUpdateRomFromSgw = { upd -> startQuickRomUpdate(upd) },
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
                            val path = BoxArtRepository.fetchOrGetBoxArt(
                                this@MainActivity,
                                rom,
                                useLibretro = true,
                                useRa = GbcaPrefs.coreConfigState.value.showRaCovers,
                                forceRefresh = true,
                            )
                            if (path != null) {
                                boxArtMap[rom.uriString] = path
                                Toast.makeText(this@MainActivity, getString(R.string.toast_cover_updated), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@MainActivity, getString(R.string.toast_cover_not_found), Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onPickCustomCover = { rom ->
                        pendingCoverRom = rom
                        coverPicker.launch(arrayOf("image/*"))
                    },
                    onViewRomAchievements = { rom ->
                        openRomAchievements(rom)
                    },
                )

                if (showRaAccountDialogState.value) {
                    RetroAchievementsAccountDialog(
                        onDismiss = { showRaAccountDialogState.value = false },
                    )
                }

                raGameDialogRomState.value?.let { rom ->
                    GameAchievementsDialog(
                        gameTitleFallback = rom.displayTitle,
                        session = raGameSessionState.value,
                        isLoading = raGameLoadingState.value,
                        onOpenLogin = {
                            raGameDialogRomState.value = null
                            showRaAccountDialogState.value = true
                        },
                        onDismiss = { raGameDialogRomState.value = null },
                    )
                }

                if (showSgwCatalogState.value) {
                    SgwCatalogSheet(
                        installedRoms = romsState.value,
                        initialCatalog = sgwCatalogState.value,
                        updatesByRomUri = sgwUpdatesMapState.value,
                        onCatalogUpdated = { catalog ->
                            sgwCatalogState.value = catalog
                            recomputeSgwUpdates(romsState.value, catalog)
                        },
                        onRomInstalledOrUpdated = { newRom, _ ->
                            val updatedList = GbcaPrefs.getCachedRoms()
                            romsState.value = updatedList
                            recomputeSgwUpdates(updatedList, sgwCatalogState.value)
                            val cfg = GbcaPrefs.coreConfigState.value
                            preloadCovers(updatedList, cfg.showLibretroCovers, cfg.showRaCovers)
                        },
                        onDismiss = { showSgwCatalogState.value = false },
                    )
                }

                val quickDl = sgwQuickDownloadState.value
                if (quickDl.active) {
                    val colors = watermelon
                    Dialog(onDismissRequest = {}) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = colors.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, colors.line, RoundedCornerShape(16.dp)),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    text = quickDl.title,
                                    color = colors.text,
                                    fontFamily = SpaceGrotesk,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                LinearProgressIndicator(
                                    progress = (quickDl.percent / 100f).coerceIn(0f, 1f),
                                    color = colors.green,
                                    backgroundColor = colors.surface3,
                                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = quickDl.statusText.ifBlank { "${quickDl.percent}%" },
                                        color = colors.text2,
                                        fontFamily = WatermelonMono,
                                        fontSize = 11.sp,
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(colors.surface2)
                                            .border(1.dp, colors.line, RoundedCornerShape(8.dp))
                                            .clickable {
                                                SgwCatalogRepository.cancelActiveDownload()
                                                sgwQuickUpdateJob?.cancel()
                                                sgwQuickDownloadState.value = SgwDownloadState(active = false)
                                            }
                                            .padding(horizontal = 10.dp, vertical = 5.dp),
                                    ) {
                                        Text(getString(R.string.cancel), color = colors.red, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                val updateRes = latestUpdateState.value
                if (showUpdateDialogState.value && updateRes != null) {
                    StormUpdateDialog(
                        release = updateRes.primary,
                        stormRelease = updateRes.stormRelease,
                        githubRelease = updateRes.githubRelease,
                        preferredSource = GbcaPrefs.coreConfigState.value.updateSourcePref,
                        downloadState = updateDownloadState.value,
                        onStartDownload = { selectedRel ->
                            lifecycleScope.launch {
                                StormUpdateMirrorClient.downloadApk(this@MainActivity, selectedRel) { st ->
                                    updateDownloadState.value = st
                                }
                            }
                        },
                        onInstallApk = { apk -> StormUpdateMirrorClient.installApk(this@MainActivity, apk) },
                        onSkipVersion = {
                            GbcaPrefs.setSkippedUpdateVersion(updateRes.primary.version)
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
            checkUpdatesOnLaunch()
        }
    }

    override fun onResume() {
        super.onResume()
        supportActionBar?.hide()
        AppThemeManager.applyImmersiveFullscreen(this)
        if (GbcaPrefs.consumeRomFoldersDirty()) {
            if (GbcaPrefs.getRomFolders().isEmpty() && !RomRepository.hasLocalFallbackRoms(this)) {
                GbcaPrefs.saveCachedRoms(emptyList())
                romsState.value = emptyList()
                sgwUpdatesMapState.value = emptyMap()
            } else {
                rescanLibrary()
            }
        } else {
            val cached = GbcaPrefs.getCachedRoms()
            romsState.value = cached
            recomputeSgwUpdates(cached, sgwCatalogState.value)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            AppThemeManager.applyImmersiveFullscreen(this)
        }
    }

    private fun openRomAchievements(rom: RomEntry) {
        raGameDialogRomState.value = rom
        raGameSessionState.value = null
        if (GbcaPrefs.getRaUsername().isBlank() || GbcaPrefs.getRaToken().isBlank()) {
            raGameLoadingState.value = false
            return
        }
        raGameLoadingState.value = true
        lifecycleScope.launch {
            val hash = withContext(Dispatchers.IO) {
                val uri = runCatching { Uri.parse(rom.uriString) }.getOrNull() ?: return@withContext rom.md5
                val f = RomRepository.materializeUriToCache(this@MainActivity, uri, "ra_lookup")
                val computed = f?.let { NativeCore.nativeComputeRaHash(it.absolutePath, rom.platform) }
                f?.delete()
                computed ?: rom.md5
            }
            raGameSessionState.value = RetroAchievementsClient.lookupGameSession(hash)
            raGameLoadingState.value = false
        }
    }

    private fun rescanLibrary() {
        if (GbcaPrefs.getRomFolders().isEmpty() && !RomRepository.hasLocalFallbackRoms(this)) {
            GbcaPrefs.saveCachedRoms(emptyList())
            romsState.value = emptyList()
            sgwUpdatesMapState.value = emptyMap()
            isScanningState.value = false
            return
        }
        lifecycleScope.launch {
            isScanningState.value = true
            val scanned = RomRepository.scanFolders(this@MainActivity)
            romsState.value = scanned
            isScanningState.value = false
            checkSgwGameUpdates(scanned)
        }
    }

    private fun checkSgwGameUpdates(roms: List<RomEntry>) {
        if (!GbcaPrefs.coreConfigState.value.autoCheckGameUpdates || roms.isEmpty()) {
            sgwUpdatesMapState.value = emptyMap()
            return
        }
        sgwUpdateCheckJob?.cancel()
        sgwUpdateCheckJob = lifecycleScope.launch {
            val cachedCatalog = withContext(Dispatchers.IO) {
                SgwCatalogRepository.getCachedCatalog(this@MainActivity)
            }
            if (cachedCatalog.isNotEmpty()) {
                sgwCatalogState.value = cachedCatalog
                recomputeSgwUpdates(roms, cachedCatalog)
            }
            val res = SgwCatalogRepository.fetchCatalog(this@MainActivity, forceRefresh = false)
            res.onSuccess { catalog ->
                sgwCatalogState.value = catalog
                recomputeSgwUpdates(roms, catalog)
            }
        }
    }

    private fun recomputeSgwUpdates(roms: List<RomEntry>, catalog: List<SgwCatalogGame>) {
        if (!GbcaPrefs.coreConfigState.value.autoCheckGameUpdates || roms.isEmpty() || catalog.isEmpty()) {
            sgwUpdatesMapState.value = emptyMap()
            return
        }
        val updates = SgwCatalogRepository.findUpdatesForInstalled(roms, catalog)
        sgwUpdatesMapState.value = updates.associateBy { it.localRom.uriString }
    }

    private fun startQuickRomUpdate(updateInfo: SgwGameUpdateInfo) {
        if (sgwQuickDownloadState.value.active) return
        sgwQuickUpdateJob?.cancel()
        sgwQuickUpdateJob = lifecycleScope.launch {
            val res = SgwCatalogRepository.downloadOrUpdateRom(
                ctx = this@MainActivity,
                remoteGame = updateInfo.remoteGame,
                replacingRom = updateInfo.localRom,
                targetTreeUri = null,
            ) { st ->
                sgwQuickDownloadState.value = st
            }
            res.onSuccess {
                val updatedList = GbcaPrefs.getCachedRoms()
                romsState.value = updatedList
                recomputeSgwUpdates(updatedList, sgwCatalogState.value)
                val cfg = GbcaPrefs.coreConfigState.value
                preloadCovers(updatedList, cfg.showLibretroCovers, cfg.showRaCovers)
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.sgw_toast_updated_fmt, updateInfo.remoteGame.finalTitle),
                    Toast.LENGTH_SHORT,
                ).show()
            }.onFailure { err ->
                if (err !is kotlinx.coroutines.CancellationException) {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.sgw_toast_error_fmt, err.message ?: "Error"),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    private var raBadgeJob: Job? = null

    private fun verifyRaBadges(roms: List<RomEntry>) {
        raBadgeJob?.cancel()
        raBadgeJob = lifecycleScope.launch {
            val (initialConfirmed, toCheck) = withContext(Dispatchers.IO) {
                val cached = GbcaPrefs.getCachedRaGameIds()
                val confirmed = HashSet<String>()
                for ((hash, id) in cached) {
                    if (id > 0) confirmed.add(hash)
                }
                val missing = roms.map { it.md5.trim().lowercase() }
                    .filter { it.length == 32 && !cached.containsKey(it) }
                    .distinct()
                confirmed.toSet() to missing
            }
            confirmedRaHashesState.value = initialConfirmed
            if (toCheck.isEmpty()) return@launch

            val sem = Semaphore(4)
            val newlyConfirmed = ConcurrentHashMap.newKeySet<String>()
            coroutineScope {
                toCheck.forEach { hash ->
                    launch(Dispatchers.IO) {
                        sem.withPermit {
                            if (RetroAchievementsClient.verifyRomHasAchievements(hash)) {
                                newlyConfirmed.add(hash)
                            }
                        }
                    }
                }
            }
            if (newlyConfirmed.isNotEmpty()) {
                confirmedRaHashesState.value = initialConfirmed + newlyConfirmed
            }
        }
    }

    private fun preloadCovers(roms: List<RomEntry>, useLibretro: Boolean, useRa: Boolean) {
        coverLoadJob?.cancel()
        if (roms.isEmpty()) {
            boxArtMap.clear()
            return
        }

        coverLoadJob = lifecycleScope.launch {
            val (cachedMap, pending) = withContext(Dispatchers.IO) {
                val found = HashMap<String, String>(roms.size)
                val toFetch = ArrayList<RomEntry>()
                for (rom in roms) {
                    val cached = BoxArtRepository.getCachedBoxArtPath(
                        this@MainActivity,
                        rom,
                        allowLibretro = useLibretro,
                        allowRa = useRa,
                    )
                    if (cached != null) {
                        found[rom.uriString] = cached
                    } else if (useLibretro || useRa) {
                        toFetch.add(rom)
                    }
                }
                found to toFetch
            }
            boxArtMap.clear()
            if (cachedMap.isNotEmpty()) {
                boxArtMap.putAll(cachedMap)
            }
            if (pending.isEmpty()) return@launch

            val semaphore = Semaphore(4)
            val fetchedBatch = ConcurrentHashMap<String, String>()
            coroutineScope {
                pending.forEach { rom ->
                    launch(Dispatchers.IO) {
                        semaphore.withPermit {
                            val fetched = BoxArtRepository.fetchOrGetBoxArt(
                                this@MainActivity,
                                rom,
                                useLibretro = useLibretro,
                                useRa = useRa,
                            )
                            if (fetched != null) {
                                fetchedBatch[rom.uriString] = fetched
                            }
                        }
                    }
                }
            }
            if (fetchedBatch.isNotEmpty()) {
                boxArtMap.putAll(fetchedBatch)
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
                Toast.makeText(this@MainActivity, getString(R.string.toast_unsupported_rom), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun launchRom(rom: RomEntry) {
        val intent = Intent(this, EmulatorActivity::class.java).apply {
            putExtra(EmulatorActivity.EXTRA_ROM_JSON, json.encodeToString(rom))
        }
        startActivity(intent)
    }

    private fun checkUpdatesOnLaunch() {
        if (!GbcaPrefs.coreConfigState.value.autoCheckUpdates) return
        lifecycleScope.launch {
            val res = StormUpdateMirrorClient.checkLatestUpdate(GbcaPrefs.coreConfigState.value.updateSourcePref)
            if (res != null && res.primary.version != GbcaPrefs.getSkippedUpdateVersion()) {
                latestUpdateState.value = res
                // Automatically pop up the update dialog on launch when a newer release exists!
                showUpdateDialogState.value = true
            }
        }
    }
}
