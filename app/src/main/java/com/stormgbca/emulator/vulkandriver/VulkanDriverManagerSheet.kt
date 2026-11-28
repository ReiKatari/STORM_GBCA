package com.stormgbca.emulator.vulkandriver

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.RadioButton
import androidx.compose.material.RadioButtonDefaults
import androidx.compose.material.Surface
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.TabRowDefaults
import androidx.compose.material.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.GetApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.stormgbca.emulator.R
import com.stormgbca.emulator.data.GbcaPrefs
import com.stormgbca.emulator.ui.common.UnifiedBackButton
import com.stormgbca.emulator.ui.theme.SpaceGrotesk
import com.stormgbca.emulator.ui.theme.WatermelonColors
import com.stormgbca.emulator.ui.theme.WatermelonMono
import com.stormgbca.emulator.ui.theme.watermelon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private data class PendingDriverInstall(
    val downloadKey: String,
    val displayName: String,
    val urls: List<String>,
    val sourceRepo: String,
    val assetFileName: String,
)

@Composable
fun VulkanDriverManagerSheet(
    onDismiss: () -> Unit,
) {
    val colors = watermelon
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var gpuProfile by remember {
        mutableStateOf(
            GpuDeviceProfile(
                telemetry = GraphicsTelemetry(
                    vulkanSupported = false,
                    gpuName = "",
                    vulkanDeviceApi = "",
                    vulkanInstanceApi = "",
                    glesVersion = "3.0",
                ),
                gpuDescription = "",
                vendorFamily = GpuVendorFamily.OTHER,
                adrenoSubFamily = AdrenoSubFamily.NON_ADRENO,
                maliArchFamily = MaliArchFamily.NON_MALI,
                isAdrenoTurnipCapable = false,
                isMalisx2Supported = false,
                supportsCustomDrivers = false,
                blockReason = null,
            )
        )
    }
    val installedDrivers = GbcaPrefs.installedVulkanDriversState.value
    val activeDriverPath = GbcaPrefs.coreConfigState.value.customVulkanDriverPath

    var selectedTab by remember { mutableIntStateOf(0) }
    var repoGroups by remember { mutableStateOf<List<VulkanDriverRepoGroup>>(emptyList()) }
    var isSyncingRepos by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(DriverRepoCategory.ALL) }
    var downloadState by remember { mutableStateOf<DriverDownloadState>(DriverDownloadState.Idle) }
    var activeDownloadJob by remember { mutableStateOf<Job?>(null) }
    var isImportingLocal by remember { mutableStateOf(false) }
    var statusBanner by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<InstalledVulkanDriver?>(null) }
    var pending8xxWarn by remember { mutableStateOf<PendingDriverInstall?>(null) }

    val recommendedRepoIds = remember(gpuProfile) { VulkanDriverRepository.getRecommendedRepoIds(gpuProfile) }
    val recommendedGroups = remember(repoGroups, gpuProfile) {
        VulkanDriverRepository.filterRecommendedGroups(repoGroups, gpuProfile)
    }

    fun syncRepos(force: Boolean) {
        scope.launch {
            isSyncingRepos = true
            repoGroups = VulkanDriverRepository.fetchAllRepositories(context, forceRefresh = force)
            isSyncingRepos = false
        }
    }

    LaunchedEffect(Unit) {
        val (profile, cached) = withContext(Dispatchers.IO) {
            VulkanDriverRepository.detectGpuProfile(context) to
                VulkanDriverRepository.getCachedAllRepositories(context)
        }
        gpuProfile = profile
        repoGroups = cached
        syncRepos(force = false)
    }

    fun startInstall(req: PendingDriverInstall) {
        if (downloadState is DriverDownloadState.Downloading || isImportingLocal) return
        activeDownloadJob?.cancel()
        activeDownloadJob = scope.launch {
            downloadState = DriverDownloadState.Downloading(
                key = req.downloadKey,
                displayName = req.displayName,
                bytesRead = 0L,
                totalBytes = -1L,
                percent = -1,
            )
            val result = VulkanDriverManager.downloadAndInstall(
                context = context,
                urls = req.urls,
                fallbackDisplayName = req.displayName,
                sourceRepo = req.sourceRepo,
                assetFileName = req.assetFileName,
            ) { bytes, total, pct ->
                downloadState = DriverDownloadState.Downloading(
                    key = req.downloadKey,
                    displayName = req.displayName,
                    bytesRead = bytes,
                    totalBytes = total,
                    percent = pct,
                )
            }
            result.onSuccess { drv ->
                downloadState = DriverDownloadState.Idle
                statusBanner = context.getString(R.string.vk_mgr_installed_ok_fmt, drv.displayName)
            }.onFailure { err ->
                downloadState = DriverDownloadState.Idle
                statusBanner = mapDriverErrorMessage(context, err)
            }
        }
    }

    fun requestInstallAsset(repo: VulkanDriverRepoSpec, release: VulkanDriverRelease, asset: VulkanDriverAsset) {
        val cleanAsset = asset.name
            .removeSuffix(".adpkg.zip")
            .removeSuffix(".zip")
            .removeSuffix(".adpkg")
        val displayName = if (release.assets.size > 1) "${release.title} - $cleanAsset" else release.title
        val req = PendingDriverInstall(
            downloadKey = asset.downloadUrl,
            displayName = displayName,
            urls = asset.allUrls(),
            sourceRepo = repo.id,
            assetFileName = asset.name,
        )
        val warn8xx = gpuProfile.isAdrenoTurnipCapable &&
            VulkanDriverRepository.isDriverPotentiallyIncompatibleWithAdreno8xx(
                driverName = "$displayName ${asset.name}",
                description = "${repo.displayName} ${release.releaseNotes}",
                version = release.tagName,
                subFamily = gpuProfile.adrenoSubFamily,
            )
        if (warn8xx) {
            pending8xxWarn = req
        } else {
            startInstall(req)
        }
    }

    fun installStormQuick() {
        val liveRelease = repoGroups.firstOrNull { it.spec.id == "storm_driver" }?.releases?.firstOrNull()
        val liveAsset = liveRelease?.assets?.firstOrNull()
        val urls = if (liveAsset != null) {
            if (liveAsset.name.equals("STORM_DRIVER_12.0.0.zip", ignoreCase = true)) {
                (liveAsset.allUrls() + VulkanDriverRepository.STORM_DRIVER_MIRRORS).distinct()
            } else {
                liveAsset.allUrls()
            }
        } else {
            VulkanDriverRepository.STORM_DRIVER_MIRRORS
        }
        startInstall(
            PendingDriverInstall(
                downloadKey = "storm_quick",
                displayName = liveRelease?.title ?: "STORM DRIVER 12.0.0",
                urls = urls,
                sourceRepo = "storm_driver",
                assetFileName = liveAsset?.name ?: "STORM_DRIVER_12.0.0.zip",
            )
        )
    }

    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null && !isImportingLocal && downloadState !is DriverDownloadState.Downloading) {
            scope.launch {
                isImportingLocal = true
                val res = VulkanDriverManager.importFromUri(context, uri)
                isImportingLocal = false
                res.onSuccess { drv ->
                    statusBanner = context.getString(R.string.vk_mgr_installed_ok_fmt, drv.displayName)
                }.onFailure { err ->
                    statusBanner = mapDriverErrorMessage(context, err)
                }
            }
        }
    }

    fun launchZipPicker() {
        zipPicker.launch(
            arrayOf(
                "application/zip",
                "application/x-zip-compressed",
                "application/octet-stream",
                "application/x-compressed",
                "*/*",
            )
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colors.bg,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.vk_mgr_title),
                        color = colors.text,
                        fontFamily = SpaceGrotesk,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (gpuProfile.supportsCustomDrivers) {
                        val busy = downloadState is DriverDownloadState.Downloading || isImportingLocal
                        IconButton(enabled = !busy, onClick = { launchZipPicker() }) {
                            Icon(
                                Icons.Filled.FileOpen,
                                contentDescription = stringResource(R.string.vk_mgr_import_zip),
                                tint = colors.green,
                            )
                        }
                        IconButton(onClick = { syncRepos(force = true) }) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.vk_mgr_refresh),
                                tint = colors.text,
                            )
                        }
                    }
                }

                // GPU Telemetry Header
                GpuTelemetryHeader(
                    profile = gpuProfile,
                    installedDrivers = installedDrivers,
                    activeDriverPath = activeDriverPath,
                )

                // Status message toast banner
                statusBanner?.let { msg ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.greenDim)
                            .border(1.dp, colors.green.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(msg, color = colors.green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = null,
                            tint = colors.green,
                            modifier = Modifier.size(16.dp).clickable { statusBanner = null },
                        )
                    }
                }

                // Active Download Progress Banner with Cancel
                val dl = downloadState as? DriverDownloadState.Downloading
                if (dl != null) {
                    ActiveDriverDownloadBanner(
                        state = dl,
                        onCancel = {
                            VulkanDriverManager.cancelActiveDownload()
                            activeDownloadJob?.cancel()
                            downloadState = DriverDownloadState.Idle
                        },
                    )
                }

                if (!gpuProfile.supportsCustomDrivers) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            backgroundColor = colors.surface,
                            border = BorderStroke(1.dp, colors.red),
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.vk_mgr_system_title), color = colors.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(stringResource(R.string.vk_mgr_blocked_desc), color = colors.text2, fontSize = 13.sp)
                            }
                        }
                    }
                } else {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        backgroundColor = colors.surface,
                        contentColor = colors.green,
                        indicator = { tabPositions ->
                            if (selectedTab < tabPositions.size) {
                                TabRowDefaults.Indicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                    color = colors.green,
                                    height = 3.dp,
                                )
                            }
                        },
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = {
                                Text(
                                    text = stringResource(R.string.vk_mgr_tab_installed, installedDrivers.size + 1),
                                    color = if (selectedTab == 0) colors.green else colors.text2,
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 12.sp,
                                )
                            },
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = {
                                Text(
                                    text = stringResource(R.string.vk_mgr_tab_recommended),
                                    color = if (selectedTab == 1) colors.green else colors.text2,
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 12.sp,
                                )
                            },
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = {
                                Text(
                                    text = stringResource(R.string.vk_mgr_tab_catalog, repoGroups.size),
                                    color = if (selectedTab == 2) colors.green else colors.text2,
                                    fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 12.sp,
                                )
                            },
                        )
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (selectedTab) {
                            0 -> InstalledDriversTabContent(
                                profile = gpuProfile,
                                installedDrivers = installedDrivers,
                                activeDriverPath = activeDriverPath,
                                isBusy = dl != null || isImportingLocal,
                                onSelectSystem = { VulkanDriverManager.setSystemDriver() },
                                onSelectDriver = { VulkanDriverManager.selectDriver(it) },
                                onDeleteDriver = { pendingDelete = it },
                                onInstallStorm = { installStormQuick() },
                                onImportZip = { launchZipPicker() },
                            )
                            1 -> RecommendedDriversTabContent(
                                profile = gpuProfile,
                                recommendedGroups = recommendedGroups,
                                installedDrivers = installedDrivers,
                                activeDriverPath = activeDriverPath,
                                isBusy = dl != null || isImportingLocal,
                                isSyncing = isSyncingRepos,
                                onSelectSystem = { VulkanDriverManager.setSystemDriver() },
                                onInstallStorm = { installStormQuick() },
                                onSelectDriver = { VulkanDriverManager.selectDriver(it) },
                                onInstallAsset = { repo, rel, asset -> requestInstallAsset(repo, rel, asset) },
                                onImportZip = { launchZipPicker() },
                            )
                            else -> CatalogDriversTabContent(
                                repoGroups = repoGroups,
                                recommendedRepoIds = recommendedRepoIds,
                                installedDrivers = installedDrivers,
                                activeDriverPath = activeDriverPath,
                                searchQuery = searchQuery,
                                selectedCategory = selectedCategory,
                                isSyncing = isSyncingRepos,
                                isBusy = dl != null || isImportingLocal,
                                onSearchQueryChange = { searchQuery = it },
                                onCategorySelect = { selectedCategory = it },
                                onSelectDriver = { VulkanDriverManager.selectDriver(it) },
                                onInstallAsset = { repo, rel, asset -> requestInstallAsset(repo, rel, asset) },
                            )
                        }
                    }
                }

                // Bottom-centered STORM DS Back Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface)
                        .padding(top = 8.dp, bottom = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    UnifiedBackButton(onClick = onDismiss)
                }
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            backgroundColor = colors.surface,
            contentColor = colors.text,
            title = { Text(stringResource(R.string.vk_mgr_delete_title), color = colors.text, fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.vk_mgr_delete_msg_fmt, target.displayName), color = colors.text2) },
            confirmButton = {
                Button(
                    onClick = {
                        VulkanDriverManager.deleteDriver(context, target.id)
                        statusBanner = context.getString(R.string.vk_mgr_removed_fmt, target.displayName)
                        pendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = colors.red, contentColor = Color.White),
                ) {
                    Text(stringResource(R.string.vk_mgr_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.cancel), color = colors.text2)
                }
            },
        )
    }

    pending8xxWarn?.let { pending ->
        AlertDialog(
            onDismissRequest = { pending8xxWarn = null },
            backgroundColor = colors.surface,
            contentColor = colors.text,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = WatermelonColors.gold)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.vk_mgr_warn_8xx_title), color = colors.text, fontWeight = FontWeight.Bold)
                }
            },
            text = { Text(stringResource(R.string.vk_mgr_warn_8xx_msg_fmt, pending.displayName), color = colors.text2) },
            confirmButton = {
                Button(
                    onClick = {
                        pending8xxWarn = null
                        startInstall(pending)
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = WatermelonColors.gold, contentColor = Color.Black),
                ) {
                    Text(stringResource(R.string.vk_mgr_install_anyway), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pending8xxWarn = null }) {
                    Text(stringResource(R.string.cancel), color = colors.text2)
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GpuTelemetryHeader(
    profile: GpuDeviceProfile,
    installedDrivers: List<InstalledVulkanDriver>,
    activeDriverPath: String,
) {
    val colors = watermelon
    val tel = profile.telemetry
    val activeCustom = activeDriverPath.takeIf { it.isNotBlank() }?.let { path ->
        installedDrivers.firstOrNull { it.soPath == path }
    }
    val activeLabel = activeCustom?.displayName
        ?: activeDriverPath.takeIf { it.isNotBlank() }?.let { java.io.File(it).name }
        ?: stringResource(R.string.vk_mgr_system_title)

    val vkVerText = if (tel.vulkanSupported) {
        val ver = tel.vulkanDeviceApi.ifBlank { tel.vulkanInstanceApi }.ifBlank { "1.1" }
        stringResource(R.string.vid_min_vk_fmt, ver)
    } else {
        stringResource(R.string.vid_vk_not_supported)
    }
    val glesVerText = stringResource(R.string.vid_min_gles_fmt, tel.glesVersion)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        shape = RoundedCornerShape(14.dp),
        backgroundColor = colors.surface,
        border = BorderStroke(1.dp, colors.line),
        elevation = 4.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Memory, contentDescription = null, tint = colors.green, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = profile.gpuDescription,
                    color = colors.text,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DriverPill(
                    label = vkVerText,
                    bg = if (tel.vulkanSupported) colors.surface2 else colors.redGlow,
                    fg = if (tel.vulkanSupported) colors.text2 else colors.red,
                )
                DriverPill(
                    label = glesVerText,
                    bg = colors.surface2,
                    fg = colors.text2,
                )
                DriverPill(
                    label = activeLabel,
                    bg = if (activeDriverPath.isNotBlank()) colors.greenDim else colors.surface3,
                    fg = if (activeDriverPath.isNotBlank()) colors.green else colors.text2,
                )
            }
        }
    }
}

@Composable
private fun DriverPill(label: String, bg: Color, fg: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = bg) {
        Text(
            text = label,
            color = fg,
            fontFamily = WatermelonMono,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ActiveDriverDownloadBanner(
    state: DriverDownloadState.Downloading,
    onCancel: () -> Unit,
) {
    val colors = watermelon
    val sizeText = when {
        state.totalBytes > 0L -> String.format(
            Locale.US,
            "%.1f / %.1f MB",
            state.bytesRead / (1024.0 * 1024.0),
            state.totalBytes / (1024.0 * 1024.0),
        )
        state.bytesRead > 0L -> String.format(Locale.US, "%.1f MB", state.bytesRead / (1024.0 * 1024.0))
        else -> "..."
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        color = colors.surface2,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.vk_mgr_downloading_fmt, state.displayName, sizeText),
                    color = colors.text,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (state.percent in 0..100) {
                    Spacer(Modifier.width(6.dp))
                    Text("${state.percent}%", color = colors.green, fontFamily = WatermelonMono, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.cancel),
                    color = colors.red,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onCancel).padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            if (state.percent in 0..100) {
                LinearProgressIndicator(
                    progress = state.percent / 100f,
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = colors.green,
                    backgroundColor = colors.surface3,
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = colors.green,
                    backgroundColor = colors.surface3,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InstalledDriversTabContent(
    profile: GpuDeviceProfile,
    installedDrivers: List<InstalledVulkanDriver>,
    activeDriverPath: String,
    isBusy: Boolean,
    onSelectSystem: () -> Unit,
    onSelectDriver: (InstalledVulkanDriver) -> Unit,
    onDeleteDriver: (InstalledVulkanDriver) -> Unit,
    onInstallStorm: () -> Unit,
    onImportZip: () -> Unit,
) {
    val colors = watermelon
    val isSystemActive = activeDriverPath.isBlank()
    val stormInstalled = installedDrivers.any {
        it.sourceRepo == "storm_driver" || it.displayName.contains("STORM", ignoreCase = true)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (profile.isAdrenoTurnipCapable) {
            item {
                StormQuickInstallCard(isInstalled = stormInstalled, isBusy = isBusy, onClick = onInstallStorm)
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onSelectSystem() },
                shape = RoundedCornerShape(12.dp),
                backgroundColor = colors.surface,
                border = BorderStroke(if (isSystemActive) 1.5.dp else 1.dp, if (isSystemActive) colors.green else colors.line),
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = isSystemActive,
                        onClick = onSelectSystem,
                        colors = RadioButtonDefaults.colors(selectedColor = colors.green, unselectedColor = colors.text3),
                    )
                    Spacer(Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.vk_mgr_system_title), color = colors.text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(stringResource(R.string.vk_mgr_system_sub), color = colors.text2, fontSize = 12.sp)
                    }
                    if (isSystemActive) {
                        DriverPill(stringResource(R.string.vk_mgr_active_badge), colors.greenDim, colors.green)
                    }
                }
            }
        }

        if (installedDrivers.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    backgroundColor = colors.surface,
                    border = BorderStroke(1.dp, colors.line),
                ) {
                    Text(stringResource(R.string.vk_mgr_no_installed), color = colors.text2, fontSize = 13.sp, modifier = Modifier.padding(14.dp))
                }
            }
        } else {
            items(installedDrivers, key = { it.id }) { drv ->
                val isActive = activeDriverPath == drv.soPath
                val isCompatible = VulkanDriverRepository.isDriverCompatibleWithCurrentGpu(
                    driverName = "${drv.displayName} ${drv.soFileName}",
                    description = drv.description,
                    version = drv.driverVersion,
                    sourceRepo = drv.sourceRepo ?: drv.assetFileName,
                    profile = profile,
                )
                val sizeLabel = remember(drv.id, drv.driverDir) { VulkanDriverManager.computeInstalledDriverSize(drv) }
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onSelectDriver(drv) },
                    shape = RoundedCornerShape(12.dp),
                    backgroundColor = colors.surface,
                    border = BorderStroke(
                        if (isActive) 1.5.dp else 1.dp,
                        when {
                            isActive -> colors.green
                            !isCompatible -> WatermelonColors.gold
                            else -> colors.line
                        },
                    ),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = isActive,
                                onClick = { onSelectDriver(drv) },
                                colors = RadioButtonDefaults.colors(selectedColor = colors.green, unselectedColor = colors.text3),
                            )
                            Spacer(Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(drv.displayName, color = colors.text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                drv.description?.takeIf { it.isNotBlank() }?.let { desc ->
                                    Text(desc, color = colors.text2, fontSize = 11.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            IconButton(onClick = { onDeleteDriver(drv) }) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.vk_mgr_delete), tint = colors.red)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (isActive) {
                                DriverPill(stringResource(R.string.vk_mgr_active_badge), colors.greenDim, colors.green)
                            }
                            if (!isCompatible) {
                                DriverPill(stringResource(R.string.vk_mgr_incompatible_badge), colors.redGlow, colors.red)
                            }
                            DriverPill(drv.soFileName, colors.surface2, colors.text2)
                            sizeLabel?.let { DriverPill(it, colors.surface2, colors.text3) }
                            drv.driverVersion?.takeIf { it.isNotBlank() }?.let { DriverPill(it, colors.surface3, colors.text2) }
                            drv.author?.takeIf { it.isNotBlank() }?.let { DriverPill(it, colors.surface3, colors.text3) }
                        }
                    }
                }
            }
        }

        item {
            OutlinedButton(
                enabled = !isBusy,
                onClick = onImportZip,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, colors.line),
            ) {
                Icon(Icons.Filled.FileOpen, contentDescription = null, tint = colors.green, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.vk_mgr_import_zip), color = colors.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun RecommendedDriversTabContent(
    profile: GpuDeviceProfile,
    recommendedGroups: List<VulkanDriverRepoGroup>,
    installedDrivers: List<InstalledVulkanDriver>,
    activeDriverPath: String,
    isBusy: Boolean,
    isSyncing: Boolean,
    onSelectSystem: () -> Unit,
    onInstallStorm: () -> Unit,
    onSelectDriver: (InstalledVulkanDriver) -> Unit,
    onInstallAsset: (VulkanDriverRepoSpec, VulkanDriverRelease, VulkanDriverAsset) -> Unit,
    onImportZip: () -> Unit,
) {
    val colors = watermelon
    val stormInstalled = installedDrivers.any {
        it.sourceRepo == "storm_driver" || it.displayName.contains("STORM", ignoreCase = true)
    }
    val adviceRes = when {
        profile.isAdrenoTurnipCapable -> R.string.vk_mgr_advice_adreno
        profile.isMalisx2Supported -> R.string.vk_mgr_advice_mali
        else -> R.string.vk_mgr_advice_system
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                backgroundColor = colors.surface,
                border = BorderStroke(1.dp, colors.green),
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = WatermelonColors.gold)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(adviceRes), color = colors.text, fontSize = 13.sp)
                }
            }
        }

        if (isSyncing) {
            item {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(3.dp), color = colors.green, backgroundColor = colors.surface3)
            }
        }

        if (profile.isAdrenoTurnipCapable) {
            item {
                StormQuickInstallCard(isInstalled = stormInstalled, isBusy = isBusy, onClick = onInstallStorm)
            }
        }

        if (!profile.isAdrenoTurnipCapable && !profile.isMalisx2Supported) {
            item {
                val isSystemActive = activeDriverPath.isBlank()
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    backgroundColor = colors.surface,
                    border = BorderStroke(1.5.dp, if (isSystemActive) colors.green else WatermelonColors.gold),
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.vk_mgr_system_title), color = colors.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(stringResource(R.string.vk_mgr_system_sub), color = colors.text2, fontSize = 12.sp)
                        if (isSystemActive) {
                            DriverPill(stringResource(R.string.vk_mgr_active_badge), colors.greenDim, colors.green)
                        } else {
                            Button(
                                onClick = onSelectSystem,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(backgroundColor = colors.green, contentColor = Color(0xFF05060A)),
                            ) {
                                Text(stringResource(R.string.vk_mgr_activate), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        items(recommendedGroups, key = { "rec_${it.spec.id}" }) { group ->
            RepoGroupCard(
                group = group,
                isRecommended = true,
                expanded = true,
                onToggleExpand = {},
                installedDrivers = installedDrivers,
                activeDriverPath = activeDriverPath,
                isBusy = isBusy,
                maxReleasesShown = 3,
                onSelectDriver = onSelectDriver,
                onInstallAsset = { rel, asset -> onInstallAsset(group.spec, rel, asset) },
            )
        }

        item {
            OutlinedButton(
                enabled = !isBusy,
                onClick = onImportZip,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, colors.line),
            ) {
                Icon(Icons.Filled.FileOpen, contentDescription = null, tint = colors.green, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.vk_mgr_import_zip), color = colors.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun CatalogDriversTabContent(
    repoGroups: List<VulkanDriverRepoGroup>,
    recommendedRepoIds: List<String>,
    installedDrivers: List<InstalledVulkanDriver>,
    activeDriverPath: String,
    searchQuery: String,
    selectedCategory: DriverRepoCategory,
    isSyncing: Boolean,
    isBusy: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onCategorySelect: (DriverRepoCategory) -> Unit,
    onSelectDriver: (InstalledVulkanDriver) -> Unit,
    onInstallAsset: (VulkanDriverRepoSpec, VulkanDriverRelease, VulkanDriverAsset) -> Unit,
) {
    val colors = watermelon
    val query = searchQuery.trim()
    val expandedMap = remember { mutableStateMapOf("storm_driver" to true) }

    val filtered = remember(repoGroups, query, selectedCategory) {
        repoGroups.filter { group ->
            val catOk = selectedCategory == DriverRepoCategory.ALL || group.spec.category == selectedCategory
            if (!catOk) return@filter false
            if (query.isBlank()) return@filter true
            group.spec.displayName.contains(query, ignoreCase = true) ||
                group.spec.repoPath.contains(query, ignoreCase = true) ||
                group.releases.any { rel ->
                    rel.title.contains(query, ignoreCase = true) ||
                        rel.tagName.contains(query, ignoreCase = true) ||
                        rel.assets.any { it.name.contains(query, ignoreCase = true) }
                }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(R.string.vk_mgr_search_hint), color = colors.text2, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = colors.text2) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = null, tint = colors.text2)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                textColor = colors.text,
                cursorColor = colors.green,
                focusedBorderColor = colors.green,
                unfocusedBorderColor = colors.line,
                backgroundColor = colors.surface,
            ),
        )

        val categories = listOf(
            DriverRepoCategory.ALL to stringResource(R.string.vk_mgr_cat_all, repoGroups.size),
            DriverRepoCategory.STORM to stringResource(R.string.vk_mgr_cat_storm),
            DriverRepoCategory.TURNIP to stringResource(R.string.vk_mgr_cat_turnip),
            DriverRepoCategory.ADRENO to stringResource(R.string.vk_mgr_cat_adreno),
            DriverRepoCategory.MALI to stringResource(R.string.vk_mgr_cat_mali),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(categories, key = { it.first.name }) { (cat, label) ->
                val sel = selectedCategory == cat
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (sel) colors.green else colors.surface,
                    border = BorderStroke(1.dp, if (sel) colors.green else colors.line),
                    modifier = Modifier.clickable { onCategorySelect(cat) },
                ) {
                    Text(
                        text = label,
                        color = if (sel) Color(0xFF05060A) else colors.text,
                        fontSize = 12.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }

        if (isSyncing) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp).height(3.dp),
                color = colors.green,
                backgroundColor = colors.surface3,
            )
            Text(
                text = stringResource(R.string.vk_mgr_loading),
                color = colors.text2,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (filtered.isEmpty() && !isSyncing) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), backgroundColor = colors.surface) {
                        Text(stringResource(R.string.vk_mgr_no_matches), color = colors.text2, modifier = Modifier.padding(16.dp))
                    }
                }
            } else {
                items(filtered, key = { it.spec.id }) { group ->
                    val expanded = if (query.isNotBlank()) true else (expandedMap[group.spec.id] == true)
                    RepoGroupCard(
                        group = group,
                        isRecommended = group.spec.id in recommendedRepoIds,
                        expanded = expanded,
                        onToggleExpand = { expandedMap[group.spec.id] = !expanded },
                        installedDrivers = installedDrivers,
                        activeDriverPath = activeDriverPath,
                        isBusy = isBusy,
                        maxReleasesShown = 6,
                        onSelectDriver = onSelectDriver,
                        onInstallAsset = { rel, asset -> onInstallAsset(group.spec, rel, asset) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StormQuickInstallCard(
    isInstalled: Boolean,
    isBusy: Boolean,
    onClick: () -> Unit,
) {
    val colors = watermelon
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        backgroundColor = colors.surface,
        border = BorderStroke(1.5.dp, WatermelonColors.gold),
        elevation = 4.dp,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Bolt, contentDescription = null, tint = WatermelonColors.gold)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.vk_mgr_storm_title),
                            color = colors.text,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(6.dp))
                        DriverPill(
                            label = stringResource(R.string.vk_mgr_recommended_badge),
                            bg = WatermelonColors.gold.copy(alpha = 0.2f),
                            fg = WatermelonColors.gold,
                        )
                    }
                    Text(stringResource(R.string.vk_mgr_storm_sub), color = colors.text2, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onClick,
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = WatermelonColors.gold, contentColor = Color.Black),
            ) {
                Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(if (isInstalled) R.string.vk_mgr_storm_reinstall else R.string.vk_mgr_storm_install),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun RepoGroupCard(
    group: VulkanDriverRepoGroup,
    isRecommended: Boolean,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    installedDrivers: List<InstalledVulkanDriver>,
    activeDriverPath: String,
    isBusy: Boolean,
    maxReleasesShown: Int,
    onSelectDriver: (InstalledVulkanDriver) -> Unit,
    onInstallAsset: (VulkanDriverRelease, VulkanDriverAsset) -> Unit,
) {
    val colors = watermelon
    val visibleReleases = remember(group.releases, maxReleasesShown) {
        group.releases.take(maxReleasesShown)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        backgroundColor = colors.surface,
        border = BorderStroke(
            width = if (group.spec.id == "storm_driver") 1.5.dp else 1.dp,
            color = if (group.spec.id == "storm_driver") WatermelonColors.gold else colors.line,
        ),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (group.spec.id == "storm_driver") Icons.Filled.Bolt else Icons.Filled.Storage,
                    contentDescription = null,
                    tint = if (group.spec.id == "storm_driver") WatermelonColors.gold else colors.green,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = group.spec.displayName,
                            color = colors.text,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (isRecommended) {
                            Spacer(Modifier.width(5.dp))
                            Icon(Icons.Filled.Star, contentDescription = null, tint = WatermelonColors.gold, modifier = Modifier.size(14.dp))
                        }
                    }
                    Text(group.spec.repoPath, color = colors.text2, fontFamily = WatermelonMono, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (group.releases.isNotEmpty()) {
                    DriverPill(stringResource(R.string.vk_mgr_releases_fmt, group.releases.size), colors.surface2, colors.text2)
                }
                Spacer(Modifier.width(4.dp))
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, tint = colors.text2)
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                    if (group.error != null && visibleReleases.isEmpty()) {
                        Text(group.error, color = colors.red, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
                    }
                    visibleReleases.forEach { release ->
                        Divider(color = colors.line, thickness = 1.dp)
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(release.title, color = colors.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            if (release.publishedAt.isNotBlank()) {
                                Spacer(Modifier.width(8.dp))
                                Text(release.publishedAt, color = colors.text3, fontFamily = WatermelonMono, fontSize = 10.5.sp)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        release.assets.forEach { asset ->
                            val matching = installedDrivers.firstOrNull { inst ->
                                (inst.sourceRepo == group.spec.id && inst.assetFileName == asset.name) ||
                                    (release.assets.size == 1 && inst.displayName.equals(release.title, ignoreCase = true))
                            }
                            val isActive = matching != null && matching.soPath == activeDriverPath
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = colors.surface2,
                            ) {
                                Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(asset.name, color = colors.text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        if (asset.sizeBytes > 0L) {
                                            Text(String.format(Locale.US, "%.1f MB", asset.sizeBytes / (1024.0 * 1024.0)), color = colors.text2, fontFamily = WatermelonMono, fontSize = 10.5.sp)
                                        }
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    when {
                                        isActive -> DriverPill(stringResource(R.string.vk_mgr_active_badge), colors.greenDim, colors.green)
                                        matching != null -> OutlinedButton(
                                            onClick = { onSelectDriver(matching) },
                                            enabled = !isBusy,
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, colors.green),
                                        ) {
                                            Icon(Icons.Filled.Check, contentDescription = null, tint = colors.green, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(stringResource(R.string.vk_mgr_activate), color = colors.green, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                        else -> Button(
                                            onClick = { onInstallAsset(release, asset) },
                                            enabled = !isBusy,
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(backgroundColor = colors.green, contentColor = Color(0xFF05060A)),
                                        ) {
                                            Icon(Icons.Filled.GetApp, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(stringResource(R.string.vk_mgr_install), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

private fun mapDriverErrorMessage(context: android.content.Context, t: Throwable): String {
    val ex = t as? VulkanDriverManager.DriverException
    return when (ex?.reason) {
        VulkanDriverManager.DriverException.Reason.InvalidArchive,
        VulkanDriverManager.DriverException.Reason.InvalidZip,
        VulkanDriverManager.DriverException.Reason.NotZip,
        -> context.getString(R.string.vk_mgr_err_invalid_archive)
        VulkanDriverManager.DriverException.Reason.NoDriver,
        VulkanDriverManager.DriverException.Reason.AmbiguousDriver,
        -> context.getString(R.string.vk_mgr_err_no_driver)
        VulkanDriverManager.DriverException.Reason.ApiTooLow ->
            context.getString(R.string.vk_mgr_err_api_low_fmt, ex.detailInt)
        VulkanDriverManager.DriverException.Reason.UnsupportedDevice ->
            context.getString(R.string.vk_mgr_blocked_desc)
        VulkanDriverManager.DriverException.Reason.Cancelled ->
            context.getString(R.string.cancel)
        null ->
            context.getString(R.string.vk_mgr_err_download_fmt, t.localizedMessage ?: t.message ?: "Error")
    }
}
