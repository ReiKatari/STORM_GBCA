package com.stormgbca.emulator.ui.settings

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.stormgbca.emulator.BuildConfig
import com.stormgbca.emulator.R
import com.stormgbca.emulator.data.BoxArtRepository
import com.stormgbca.emulator.data.GbcaPrefs
import com.stormgbca.emulator.data.RomRepository
import com.stormgbca.emulator.data.ShaderRepository
import com.stormgbca.emulator.model.ConsoleSystem
import com.stormgbca.emulator.model.PhysicalAction
import com.stormgbca.emulator.ui.common.UnifiedBackButton
import com.stormgbca.emulator.ui.common.WatermelonCard
import com.stormgbca.emulator.ui.common.WatermelonRowSeparator
import com.stormgbca.emulator.ui.common.WatermelonSectionLabel
import com.stormgbca.emulator.ui.common.WatermelonSwitch
import com.stormgbca.emulator.ui.emulator.MultiplayerPanelContent
import com.stormgbca.emulator.ui.emulator.overlay.LayoutEditorActivity
import com.stormgbca.emulator.ui.emulator.overlay.OverlayPickerDialog
import com.stormgbca.emulator.ui.emulator.skin.ConsoleSkinPickerDialog
import com.stormgbca.emulator.ui.ra.RetroAchievementsSettingsContent
import com.stormgbca.emulator.ui.theme.ACCENT_PRESETS
import com.stormgbca.emulator.ui.theme.AppThemeManager
import com.stormgbca.emulator.ui.theme.SpaceGrotesk
import com.stormgbca.emulator.ui.theme.StormWatermelonTheme
import com.stormgbca.emulator.ui.theme.Theme
import com.stormgbca.emulator.ui.theme.WatermelonMono
import com.stormgbca.emulator.ui.theme.watermelon
import com.stormgbca.emulator.update.StormUpdateDialog
import com.stormgbca.emulator.update.StormUpdateMirrorClient
import com.stormgbca.emulator.update.UpdateDownloadState
import com.stormgbca.emulator.update.UpdateReleaseInfo
import kotlinx.coroutines.launch
import ru.stormgames.gbca.NativeCore

private enum class SettingsPage {
    HOME,
    SYSTEM,
    VIDEO,
    AUDIO,
    CONTROLS,
    MULTIPLAYER,
    RETROACHIEVEMENTS,
    LIBRARY,
    GENERAL,
    ABOUT,
}

class SettingsActivity : AppCompatActivity() {
    private val awaitingActionState = mutableStateOf<PhysicalAction?>(null)
    private var pendingBiosTarget: String? = null
    private var pendingShaderSystem: ConsoleSystem = ConsoleSystem.GBA
    private val shaderRefreshTick = mutableIntStateOf(0)

    private val biosPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = pendingBiosTarget ?: return@registerForActivityResult
        pendingBiosTarget = null
        if (uri != null) {
            lifecycleScope.launch {
                val f = RomRepository.materializeUriToCache(this@SettingsActivity, uri, "bios")
                if (f != null) {
                    GbcaPrefs.updateCoreConfig { cfg ->
                        when (target) {
                            "gb" -> cfg.copy(biosGbPath = f.absolutePath)
                            "gbc" -> cfg.copy(biosGbcPath = f.absolutePath)
                            "gba" -> cfg.copy(biosGbaPath = f.absolutePath)
                            "sgb" -> cfg.copy(biosSgbPath = f.absolutePath)
                            else -> cfg
                        }
                    }
                    Toast.makeText(this@SettingsActivity, "BIOS: ${f.name}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private val driverPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            lifecycleScope.launch {
                val f = RomRepository.materializeUriToCache(this@SettingsActivity, uri, "vulkan_drivers")
                if (f != null) {
                    GbcaPrefs.updateCoreConfig { it.copy(customVulkanDriverPath = f.absolutePath) }
                    Toast.makeText(this@SettingsActivity, "Vulkan: ${f.name}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private val shaderImportPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            lifecycleScope.launch {
                val docName = runCatching { DocumentFile.fromSingleUri(this@SettingsActivity, uri)?.name }.getOrNull()
                val imported = ShaderRepository.importCustomShader(this@SettingsActivity, uri, docName)
                if (imported != null) {
                    GbcaPrefs.updateOverlayProfile(pendingShaderSystem) { it.copy(retroArchShaderPath = imported.path) }
                    shaderRefreshTick.intValue++
                    Toast.makeText(this@SettingsActivity, "${pendingShaderSystem.label}: ${imported.name}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private val backupExportPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.openOutputStream(uri)?.use { it.write(GbcaPrefs.exportAllPrefsJson().toByteArray()) }
                Toast.makeText(this, getString(R.string.export_backup), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val romFolderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            GbcaPrefs.addRomFolder(uri.toString())
            lifecycleScope.launch {
                RomRepository.scanFolders(this@SettingsActivity)
            }
            Toast.makeText(this, getString(R.string.add_rom_folder), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        AppThemeManager.applyEdgeToEdgeTheme(this)
        AppThemeManager.applyImmersiveFullscreen(this)

        setContent {
            StormWatermelonTheme {
                SettingsScreen(
                    awaitingAction = awaitingActionState.value,
                    shaderTick = shaderRefreshTick.intValue,
                    onCloseActivity = { finish() },
                    onPickBios = { target ->
                        pendingBiosTarget = target
                        biosPicker.launch(arrayOf("*/*"))
                    },
                    onPickVulkanDriver = { driverPicker.launch(arrayOf("*/*")) },
                    onImportShader = { sys ->
                        pendingShaderSystem = sys
                        shaderImportPicker.launch(arrayOf("*/*"))
                    },
                    onOpenLayoutEditor = { sys ->
                        val intent = Intent(this@SettingsActivity, LayoutEditorActivity::class.java).apply {
                            putExtra("system", sys.name)
                        }
                        startActivity(intent)
                    },
                    onStartBindKey = { action -> awaitingActionState.value = action },
                    onExportBackup = { backupExportPicker.launch("storm_gbca_backup.json") },
                    onAddRomFolder = { romFolderPicker.launch(null) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        supportActionBar?.hide()
        AppThemeManager.applyImmersiveFullscreen(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            AppThemeManager.applyImmersiveFullscreen(this)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val action = awaitingActionState.value
        if (action != null) {
            GbcaPrefs.setKeyCodeForAction(action, keyCode)
            awaitingActionState.value = null
            Toast.makeText(this, "${action.name}: ${KeyEvent.keyCodeToString(keyCode)}", Toast.LENGTH_SHORT).show()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

@Composable
private fun SettingsScreen(
    awaitingAction: PhysicalAction?,
    shaderTick: Int,
    onCloseActivity: () -> Unit,
    onPickBios: (String) -> Unit,
    onPickVulkanDriver: () -> Unit,
    onImportShader: (ConsoleSystem) -> Unit,
    onOpenLayoutEditor: (ConsoleSystem) -> Unit,
    onStartBindKey: (PhysicalAction) -> Unit,
    onExportBackup: () -> Unit,
    onAddRomFolder: () -> Unit,
) {
    val colors = watermelon
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cfg = GbcaPrefs.coreConfigState.value

    var currentPage by remember { mutableStateOf(SettingsPage.HOME) }
    var skinDialogSystem by remember { mutableStateOf<ConsoleSystem?>(null) }
    var overlayDialogSystem by remember { mutableStateOf<ConsoleSystem?>(null) }
    var shaderTabSystem by remember { mutableStateOf(ConsoleSystem.GBA) }
    var showInputDialog by remember { mutableStateOf(false) }
    var foundUpdate by remember { mutableStateOf<UpdateReleaseInfo?>(null) }
    var downloadState by remember { mutableStateOf<UpdateDownloadState>(UpdateDownloadState.Idle) }
    var updateStatus by remember { mutableStateOf<String?>(null) }

    val vkSupported = remember { runCatching { NativeCore.nativeIsVulkanSupported() }.getOrDefault(false) }
    val vkGpuName = remember { runCatching { NativeCore.nativeGetVulkanDeviceName() }.getOrDefault("") }
    val librashaderReady = remember { runCatching { NativeCore.nativeIsLibrashaderAvailable() }.getOrDefault(false) }
    val shaderPresets = remember(shaderTick) { ShaderRepository.listPresets(context) }

    val handleBack: () -> Unit = {
        if (currentPage == SettingsPage.HOME) onCloseActivity() else currentPage = SettingsPage.HOME
    }

    BackHandler(onBack = handleBack)

    val pageTitle = when (currentPage) {
        SettingsPage.HOME -> stringResource(R.string.settings)
        SettingsPage.SYSTEM -> stringResource(R.string.set_system_title)
        SettingsPage.VIDEO -> stringResource(R.string.set_video_title)
        SettingsPage.AUDIO -> stringResource(R.string.set_audio_title)
        SettingsPage.CONTROLS -> stringResource(R.string.set_controls_title)
        SettingsPage.MULTIPLAYER -> stringResource(R.string.set_multiplayer_title)
        SettingsPage.RETROACHIEVEMENTS -> stringResource(R.string.set_ra_title)
        SettingsPage.LIBRARY -> stringResource(R.string.set_library_title)
        SettingsPage.GENERAL -> stringResource(R.string.set_general_title)
        SettingsPage.ABOUT -> stringResource(R.string.set_about_title)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .padding(top = 10.dp),
    ) {
        // Clean STORM DS Header (Title + Version; back button is strictly at the bottom center!)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Text(
                text = pageTitle,
                color = colors.text,
                fontFamily = SpaceGrotesk,
                fontSize = 21.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = "STORM GBCA v${BuildConfig.VERSION_NAME}",
                color = colors.green,
                fontFamily = WatermelonMono,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // Scrollable vertical body (ZERO horizontal scrolling anywhere!)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            when (currentPage) {
                SettingsPage.HOME -> {
                    // 1. Emulation & Hardware (STORM DS category #1)
                    WatermelonSectionLabel(stringResource(R.string.cat_emulation_hardware))
                    WatermelonCard {
                        SettingsNavRow(Icons.Filled.Memory, stringResource(R.string.set_system_title), stringResource(R.string.set_system_summary)) {
                            currentPage = SettingsPage.SYSTEM
                        }
                        WatermelonRowSeparator()
                        SettingsNavRow(Icons.Filled.Videocam, stringResource(R.string.set_video_title), stringResource(R.string.set_video_summary)) {
                            currentPage = SettingsPage.VIDEO
                        }
                        WatermelonRowSeparator()
                        SettingsNavRow(Icons.Filled.GraphicEq, stringResource(R.string.set_audio_title), stringResource(R.string.set_audio_summary)) {
                            currentPage = SettingsPage.AUDIO
                        }
                        WatermelonRowSeparator()
                        SettingsNavRow(Icons.Filled.Gamepad, stringResource(R.string.set_controls_title), stringResource(R.string.set_controls_summary)) {
                            currentPage = SettingsPage.CONTROLS
                        }
                    }

                    // 2. Features & Tools (STORM DS category #2)
                    WatermelonSectionLabel(stringResource(R.string.cat_features_tools))
                    WatermelonCard {
                        SettingsNavRow(Icons.Filled.Cable, stringResource(R.string.set_multiplayer_title), stringResource(R.string.set_multiplayer_summary)) {
                            currentPage = SettingsPage.MULTIPLAYER
                        }
                        WatermelonRowSeparator()
                        val raUser = GbcaPrefs.getRaUsername()
                        val raSub = if (raUser.isNotBlank() && GbcaPrefs.getRaToken().isNotBlank()) {
                            stringResource(R.string.ra_connected_as, "$raUser (${GbcaPrefs.getRaPoints()} pts)")
                        } else {
                            stringResource(R.string.ra_not_connected)
                        }
                        SettingsNavRow(Icons.Filled.EmojiEvents, stringResource(R.string.set_ra_title), raSub, Color(0xFFFACC15)) {
                            currentPage = SettingsPage.RETROACHIEVEMENTS
                        }
                    }

                    // 3. Library & Data (STORM DS category #3)
                    WatermelonSectionLabel(stringResource(R.string.cat_library_data))
                    WatermelonCard {
                        SettingsNavRow(Icons.Filled.Folder, stringResource(R.string.set_library_title), stringResource(R.string.set_library_summary)) {
                            currentPage = SettingsPage.LIBRARY
                        }
                    }

                    // 4. App & System (STORM DS category #4)
                    WatermelonSectionLabel(stringResource(R.string.cat_app_system))
                    WatermelonCard {
                        SettingsNavRow(Icons.Filled.Palette, stringResource(R.string.set_general_title), stringResource(R.string.set_general_summary)) {
                            currentPage = SettingsPage.GENERAL
                        }
                        WatermelonRowSeparator()
                        SettingsNavRow(Icons.Filled.Info, stringResource(R.string.set_about_title), stringResource(R.string.set_about_summary)) {
                            currentPage = SettingsPage.ABOUT
                        }
                    }
                }

                SettingsPage.SYSTEM -> {
                    // Device Optimization Profile (Old & New Devices)
                    WatermelonSectionLabel(stringResource(R.string.opt_profile_title))
                    WatermelonCard {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            val recLabel = if (GbcaPrefs.isLowEndDevice) stringResource(R.string.opt_lite) else stringResource(R.string.opt_balanced)
                            Text(
                                text = stringResource(R.string.opt_device_info_fmt, GbcaPrefs.detectedPrimaryAbi, GbcaPrefs.detectedTotalRamMb, recLabel),
                                color = colors.green,
                                fontFamily = WatermelonMono,
                                fontSize = 11.sp,
                            )
                            val activeOpt = GbcaPrefs.getEffectiveOptimizationProfile()
                            val optChoices = listOf(
                                NativeCore.OPT_LITE to stringResource(R.string.opt_lite),
                                NativeCore.OPT_BALANCED to stringResource(R.string.opt_balanced),
                                NativeCore.OPT_PERFORMANCE to stringResource(R.string.opt_performance),
                                NativeCore.OPT_HIGH_ACCURACY to stringResource(R.string.opt_accuracy),
                            )
                            optChoices.chunked(2).forEach { pair ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    pair.forEach { (id, label) ->
                                        GridSelectChip(label, activeOpt == id, Modifier.weight(1f)) {
                                            GbcaPrefs.updateCoreConfig { c ->
                                                when (id) {
                                                    NativeCore.OPT_LITE -> c.copy(
                                                        optimizationProfile = id,
                                                        rewindBufferMb = 4,
                                                        rewindInterval = 2,
                                                        runAheadFrames = 0,
                                                        audioInterpMode = NativeCore.INTERP_NEAREST,
                                                        lcdGhosting = 0,
                                                        resolutionScale = 1,
                                                    )
                                                    NativeCore.OPT_BALANCED -> c.copy(
                                                        optimizationProfile = id,
                                                        rewindBufferMb = 16,
                                                        rewindInterval = 1,
                                                        audioInterpMode = NativeCore.INTERP_LINEAR,
                                                    )
                                                    NativeCore.OPT_PERFORMANCE -> c.copy(
                                                        optimizationProfile = id,
                                                        rewindBufferMb = 16,
                                                        rewindInterval = 1,
                                                        runAheadFrames = 0,
                                                        audioInterpMode = NativeCore.INTERP_LINEAR,
                                                    )
                                                    else -> c.copy(
                                                        optimizationProfile = id,
                                                        rewindBufferMb = 24,
                                                        rewindInterval = 1,
                                                        audioInterpMode = NativeCore.INTERP_HERMITE,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    WatermelonSectionLabel(stringResource(R.string.set_system_title))
                    WatermelonCard {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            CoreChoiceRow(stringResource(R.string.sys_core_gb), cfg.preferredCoreGb) { id ->
                                GbcaPrefs.updateCoreConfig { it.copy(preferredCoreGb = id) }
                            }
                            CoreChoiceRow(stringResource(R.string.sys_core_gbc), cfg.preferredCoreGbc) { id ->
                                GbcaPrefs.updateCoreConfig { it.copy(preferredCoreGbc = id) }
                            }

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.sys_overclock), color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("${cfg.cpuOverclockPct}%", color = colors.green, fontFamily = WatermelonMono, fontSize = 12.sp)
                            }
                            Slider(
                                value = cfg.cpuOverclockPct.toFloat(),
                                onValueChange = { v ->
                                    val snapped = ((v / 25f).toInt() * 25).coerceIn(50, 400)
                                    GbcaPrefs.updateCoreConfig { it.copy(cpuOverclockPct = snapped) }
                                },
                                valueRange = 50f..400f,
                                colors = SliderDefaults.colors(thumbColor = colors.green, activeTrackColor = colors.green),
                            )

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.sys_frame_skip), color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(if (cfg.frameSkip == 0) stringResource(R.string.off_label) else "${cfg.frameSkip}", color = colors.green, fontFamily = WatermelonMono, fontSize = 12.sp)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                (0..4).forEach { fs ->
                                    GridSelectChip("$fs", cfg.frameSkip == fs, Modifier.weight(1f)) {
                                        GbcaPrefs.updateCoreConfig { it.copy(frameSkip = fs) }
                                    }
                                }
                            }

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.sys_run_ahead), color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("${cfg.runAheadFrames}", color = colors.green, fontFamily = WatermelonMono, fontSize = 12.sp)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                (0..2).forEach { ra ->
                                    GridSelectChip("$ra", cfg.runAheadFrames == ra, Modifier.weight(1f)) {
                                        GbcaPrefs.updateCoreConfig { it.copy(runAheadFrames = ra) }
                                    }
                                }
                            }

                            SettingsSwitchRow(stringResource(R.string.sys_rewind), cfg.rewindEnabled) { c ->
                                GbcaPrefs.updateCoreConfig { it.copy(rewindEnabled = c) }
                            }
                            SettingsSwitchRow(stringResource(R.string.sys_skip_bios), cfg.skipBiosIntro) { c ->
                                GbcaPrefs.updateCoreConfig { it.copy(skipBiosIntro = c) }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                BiosChip("BIOS GB", cfg.biosGbPath.isNotBlank(), Modifier.weight(1f)) { onPickBios("gb") }
                                BiosChip("BIOS GBC", cfg.biosGbcPath.isNotBlank(), Modifier.weight(1f)) { onPickBios("gbc") }
                                BiosChip("BIOS GBA", cfg.biosGbaPath.isNotBlank(), Modifier.weight(1f)) { onPickBios("gba") }
                            }
                        }
                    }
                }

                SettingsPage.VIDEO -> {
                    WatermelonSectionLabel(stringResource(R.string.vid_api))
                    WatermelonCard {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                GridSelectChip("OpenGL ES 3.2", cfg.graphicsBackend == 0, Modifier.weight(1f)) {
                                    GbcaPrefs.updateCoreConfig { it.copy(graphicsBackend = 0) }
                                }
                                GridSelectChip("Vulkan 1.1", cfg.graphicsBackend == 1, Modifier.weight(1f), enabled = vkSupported) {
                                    GbcaPrefs.updateCoreConfig { it.copy(graphicsBackend = 1) }
                                }
                            }
                            if (vkGpuName.isNotBlank()) {
                                Text("GPU Vulkan: $vkGpuName", color = if (vkSupported) Color(0xFF22C55E) else colors.text2, fontFamily = WatermelonMono, fontSize = 11.sp)
                            }
                            if (cfg.graphicsBackend == 1) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(colors.surface2)
                                            .border(1.dp, colors.line, RoundedCornerShape(9.dp))
                                            .clickable(onClick = onPickVulkanDriver)
                                            .padding(horizontal = 10.dp, vertical = 9.dp),
                                    ) {
                                        Text(
                                            text = if (cfg.customVulkanDriverPath.isBlank()) stringResource(R.string.vid_turnip_pick) else java.io.File(cfg.customVulkanDriverPath).name,
                                            color = colors.text,
                                            fontSize = 12.sp,
                                        )
                                    }
                                    if (cfg.customVulkanDriverPath.isNotBlank()) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(9.dp))
                                                .background(colors.red.copy(alpha = 0.2f))
                                                .clickable { GbcaPrefs.updateCoreConfig { it.copy(customVulkanDriverPath = "") } }
                                                .padding(horizontal = 10.dp, vertical = 9.dp),
                                        ) {
                                            Text(stringResource(R.string.reset), color = colors.red, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            Text(stringResource(R.string.vid_scale), color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                listOf(1, 2, 3, 4).forEach { sc ->
                                    GridSelectChip("${sc}x", cfg.resolutionScale == sc, Modifier.weight(1f)) {
                                        GbcaPrefs.updateCoreConfig {
                                            val mode = if (sc > 1 && it.upscaleMode == NativeCore.UPSCALE_NONE) NativeCore.UPSCALE_XBRZ else it.upscaleMode
                                            it.copy(resolutionScale = sc, upscaleMode = mode)
                                        }
                                    }
                                }
                            }

                            // 2x3 Upscaler Grid (ZERO horizontal scrolling!)
                            Text(stringResource(R.string.vid_upscaler), color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            val upscalers = listOf(
                                NativeCore.UPSCALE_NONE to "Nearest",
                                NativeCore.UPSCALE_XBRZ to "xBRZ HD",
                                NativeCore.UPSCALE_SCALE_EPX to "Scale2x / EPX",
                                NativeCore.UPSCALE_MMPX to "MMPX Sharp",
                                NativeCore.UPSCALE_LCD_HD to "LCD HD",
                                NativeCore.UPSCALE_CRT_HD to "CRT HD",
                            )
                            upscalers.chunked(2).forEach { pair ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    pair.forEach { (id, title) ->
                                        GridSelectChip(title, cfg.upscaleMode == id, Modifier.weight(1f)) {
                                            GbcaPrefs.updateCoreConfig {
                                                val sc = if (id != NativeCore.UPSCALE_NONE && it.resolutionScale < 2) 2 else it.resolutionScale
                                                it.copy(upscaleMode = id, resolutionScale = sc)
                                            }
                                        }
                                    }
                                }
                            }

                            Text(stringResource(R.string.vid_aspect), color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                listOf(
                                    0 to stringResource(R.string.vid_aspect_orig),
                                    1 to stringResource(R.string.vid_aspect_int),
                                    2 to stringResource(R.string.vid_aspect_full),
                                ).forEach { (id, label) ->
                                    GridSelectChip(label, cfg.aspectMode == id, Modifier.weight(1f)) {
                                        GbcaPrefs.updateCoreConfig { it.copy(aspectMode = id) }
                                    }
                                }
                            }
                        }
                    }

                    // RetroArch Shaders (Clean vertical list - ZERO horizontal scrolling!)
                    WatermelonSectionLabel(stringResource(R.string.vid_shaders))
                    WatermelonCard {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = if (librashaderReady) "librashader: Active (GLES3 + Vulkan)" else "librashader: Fallback",
                                    color = if (librashaderReady) Color(0xFF22C55E) else colors.text2,
                                    fontFamily = WatermelonMono,
                                    fontSize = 11.sp,
                                    modifier = Modifier.weight(1f),
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.greenDim)
                                        .border(1.dp, colors.green, RoundedCornerShape(8.dp))
                                        .clickable { onImportShader(shaderTabSystem) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                ) {
                                    Text(stringResource(R.string.vid_import_shader), color = colors.green, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                ConsoleSystem.entries.forEach { sys ->
                                    GridSelectChip(sys.label, sys == shaderTabSystem, Modifier.weight(1f)) {
                                        shaderTabSystem = sys
                                    }
                                }
                            }

                            val activeShaderPath = when (shaderTabSystem) {
                                ConsoleSystem.GB -> GbcaPrefs.gbOverlayState.value.retroArchShaderPath
                                ConsoleSystem.GBC -> GbcaPrefs.gbcOverlayState.value.retroArchShaderPath
                                ConsoleSystem.GBA -> GbcaPrefs.gbaOverlayState.value.retroArchShaderPath
                            }
                            shaderPresets.chunked(2).forEach { pair ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    pair.forEach { preset ->
                                        GridSelectChip(preset.name, activeShaderPath == preset.path, Modifier.weight(1f)) {
                                            GbcaPrefs.updateOverlayProfile(shaderTabSystem) { it.copy(retroArchShaderPath = preset.path) }
                                        }
                                    }
                                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                SettingsPage.AUDIO -> {
                    WatermelonSectionLabel(stringResource(R.string.set_audio_title))
                    WatermelonCard {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            SettingsSwitchRow(stringResource(R.string.aud_mute), cfg.audioMuted) { m ->
                                GbcaPrefs.updateCoreConfig { it.copy(audioMuted = m) }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.aud_master), color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("${(cfg.masterVolume * 100).toInt()}%", color = colors.green, fontFamily = WatermelonMono, fontSize = 12.sp)
                            }
                            Slider(
                                value = cfg.masterVolume,
                                onValueChange = { v -> GbcaPrefs.updateCoreConfig { it.copy(masterVolume = v) } },
                                valueRange = 0f..1.5f,
                                colors = SliderDefaults.colors(thumbColor = colors.green, activeTrackColor = colors.green),
                            )

                            Text(stringResource(R.string.aud_highpass), color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                listOf(
                                    NativeCore.HIGHPASS_OFF to stringResource(R.string.off_label),
                                    NativeCore.HIGHPASS_ACCURATE to stringResource(R.string.aud_hp_accurate),
                                    NativeCore.HIGHPASS_REMOVE_DC to stringResource(R.string.aud_hp_dc),
                                ).forEach { (id, label) ->
                                    GridSelectChip(label, cfg.audioHighpassMode == id, Modifier.weight(1f)) {
                                        GbcaPrefs.updateCoreConfig { it.copy(audioHighpassMode = id) }
                                    }
                                }
                            }

                            SettingsSwitchRow(stringResource(R.string.aud_drc), cfg.dynamicRateControl) { c ->
                                GbcaPrefs.updateCoreConfig { it.copy(dynamicRateControl = c) }
                            }
                        }
                    }
                }

                SettingsPage.CONTROLS -> {
                    WatermelonSectionLabel(stringResource(R.string.set_controls_title))
                    WatermelonCard {
                        ConsoleSystem.entries.forEachIndexed { idx, sys ->
                            if (idx > 0) WatermelonRowSeparator()
                            val ov = when (sys) {
                                ConsoleSystem.GB -> GbcaPrefs.gbOverlayState.value
                                ConsoleSystem.GBC -> GbcaPrefs.gbcOverlayState.value
                                ConsoleSystem.GBA -> GbcaPrefs.gbaOverlayState.value
                            }
                            SettingsNavRow(
                                icon = Icons.Filled.AspectRatio,
                                title = stringResource(R.string.ctrl_screen_fmt, sys.label),
                                subtitle = if (ov.portraitScreen.useCustom || ov.landscapeScreen.useCustom) "Custom" else stringResource(R.string.auto),
                            ) { onOpenLayoutEditor(sys) }
                            WatermelonRowSeparator()
                            SettingsNavRow(
                                icon = Icons.Filled.Devices,
                                title = stringResource(R.string.ctrl_skin_fmt, sys.label),
                                subtitle = "${GbcaPrefs.getSkinKey(sys)} (${GbcaPrefs.getSkinDimming(sys)}%)",
                            ) { skinDialogSystem = sys }
                            WatermelonRowSeparator()
                            SettingsNavRow(
                                icon = Icons.Filled.Gamepad,
                                title = stringResource(R.string.ctrl_overlay_fmt, sys.label),
                                subtitle = ov.buttonStyle,
                            ) { overlayDialogSystem = sys }
                        }
                        WatermelonRowSeparator()
                        SettingsNavRow(
                            icon = Icons.Filled.Gamepad,
                            title = stringResource(R.string.ctrl_gamepad),
                            subtitle = stringResource(R.string.ctrl_gamepad_sub),
                        ) { showInputDialog = true }
                    }
                }

                SettingsPage.MULTIPLAYER -> {
                    MultiplayerPanelContent(nativeHandle = 0L)
                }

                SettingsPage.RETROACHIEVEMENTS -> {
                    RetroAchievementsSettingsContent()
                }

                SettingsPage.LIBRARY -> {
                    // ROM Folders Management (moved cleanly from header into Library Settings, matching STORM DS)
                    WatermelonSectionLabel(stringResource(R.string.rom_folders_section))
                    WatermelonCard {
                        val folderCount = GbcaPrefs.getRomFolders().size
                        SettingsNavRow(
                            icon = Icons.Filled.Folder,
                            title = stringResource(R.string.add_rom_folder),
                            subtitle = stringResource(R.string.rom_folders_count_fmt, folderCount),
                        ) { onAddRomFolder() }
                    }

                    // Independent Cover Sources (ALL OFF BY DEFAULT - STORM DS Parity!)
                    WatermelonSectionLabel(stringResource(R.string.sec_covers))
                    WatermelonCard {
                        SettingsSwitchSubRow(
                            title = stringResource(R.string.toggle_libretro_covers),
                            subtitle = stringResource(R.string.toggle_libretro_covers_sub),
                            checked = cfg.showLibretroCovers,
                        ) { c -> GbcaPrefs.updateCoreConfig { it.copy(showLibretroCovers = c) } }
                        WatermelonRowSeparator()
                        SettingsSwitchSubRow(
                            title = stringResource(R.string.toggle_ra_covers),
                            subtitle = stringResource(R.string.toggle_ra_covers_sub),
                            checked = cfg.showRaCovers,
                        ) { c -> GbcaPrefs.updateCoreConfig { it.copy(showRaCovers = c) } }
                        WatermelonRowSeparator()
                        SettingsNavRow(
                            icon = Icons.Filled.DeleteSweep,
                            title = stringResource(R.string.clear_cover_cache),
                            subtitle = "Libretro + RetroAchievements",
                        ) {
                            val removed = BoxArtRepository.clearAllCachedCovers(context)
                            Toast.makeText(context, context.getString(R.string.toast_covers_cleared, removed), Toast.LENGTH_SHORT).show()
                        }
                    }

                    // Independent Card Badges (ALL OFF BY DEFAULT - STORM DS Parity!)
                    WatermelonSectionLabel(stringResource(R.string.sec_badges))
                    WatermelonCard {
                        SettingsSwitchSubRow(stringResource(R.string.toggle_platform_badge), "", cfg.showPlatformBadge) { c ->
                            GbcaPrefs.updateCoreConfig { it.copy(showPlatformBadge = c) }
                        }
                        WatermelonRowSeparator()
                        SettingsSwitchSubRow(stringResource(R.string.toggle_region_badge), "", cfg.showRegionBadge) { c ->
                            GbcaPrefs.updateCoreConfig { it.copy(showRegionBadge = c) }
                        }
                        WatermelonRowSeparator()
                        SettingsSwitchSubRow(stringResource(R.string.toggle_hw_badges), "", cfg.showHardwareBadges) { c ->
                            GbcaPrefs.updateCoreConfig { it.copy(showHardwareBadges = c) }
                        }
                        WatermelonRowSeparator()
                        SettingsSwitchSubRow(stringResource(R.string.toggle_ra_badge), "", cfg.showRaBadge) { c ->
                            GbcaPrefs.updateCoreConfig { it.copy(showRaBadge = c) }
                        }
                    }

                    WatermelonSectionLabel(stringResource(R.string.card_style_title))
                    WatermelonCard {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                listOf(
                                    "3d_boxart" to stringResource(R.string.card_style_3d),
                                    "glassmorphism" to stringResource(R.string.card_style_glass),
                                    "flat_minimal" to stringResource(R.string.card_style_minimal),
                                ).forEach { (k, label) ->
                                    GridSelectChip(label, AppThemeManager.cardStyleState.value == k, Modifier.weight(1f)) {
                                        AppThemeManager.updateCardStyle(context, k)
                                    }
                                }
                            }
                            Text(stringResource(R.string.display_name_mode), color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                GridSelectChip(stringResource(R.string.display_name_file), cfg.romDisplayNameMode == 0, Modifier.weight(1f)) {
                                    GbcaPrefs.updateCoreConfig { it.copy(romDisplayNameMode = 0) }
                                }
                                GridSelectChip(stringResource(R.string.display_name_header), cfg.romDisplayNameMode == 1, Modifier.weight(1f)) {
                                    GbcaPrefs.updateCoreConfig { it.copy(romDisplayNameMode = 1) }
                                }
                            }
                        }
                    }
                }

                SettingsPage.GENERAL -> {
                    // 9 English Themes in clean 3x3 grid (ZERO horizontal scrolling!)
                    WatermelonSectionLabel(stringResource(R.string.themes_title))
                    WatermelonCard {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Theme.entries.chunked(3).forEach { rowThemes ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    rowThemes.forEach { t ->
                                        GridSelectChip(t.displayName, AppThemeManager.themeState.value == t, Modifier.weight(1f)) {
                                            AppThemeManager.updateTheme(context, t)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 12 Accents in clean wrapped grid (ZERO horizontal scrolling!)
                    WatermelonSectionLabel(stringResource(R.string.accents_title))
                    WatermelonCard {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ACCENT_PRESETS.chunked(5).forEach { rowPresets ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    rowPresets.forEach { preset ->
                                        val selected = AppThemeManager.accentColorState.value == preset.key
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(preset.previewColor)
                                                .border(if (selected) 3.dp else 1.5.dp, if (selected) colors.text else colors.line, CircleShape)
                                                .clickable { AppThemeManager.updateAccent(context, preset.key) },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 10 Languages in clean 2-column grid (ZERO horizontal scrolling!)
                    WatermelonSectionLabel(stringResource(R.string.language_title))
                    WatermelonCard {
                        val langs = listOf(
                            "system" to stringResource(R.string.language_system),
                            "en" to "English",
                            "ru" to "Русский",
                            "de" to "Deutsch",
                            "fr" to "Français",
                            "es" to "Español",
                            "it" to "Italiano",
                            "pt-BR" to "Português (BR)",
                            "ja" to "日本語",
                            "zh-Hans" to "简体中文",
                            "ko" to "한국어",
                        )
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            langs.chunked(2).forEach { pair ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    pair.forEach { (tag, label) ->
                                        GridSelectChip(label, cfg.appLanguage == tag, Modifier.weight(1f)) {
                                            GbcaPrefs.updateCoreConfig { it.copy(appLanguage = tag) }
                                            GbcaPrefs.applyAppLanguage(tag)
                                        }
                                    }
                                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    WatermelonSectionLabel(stringResource(R.string.cat_app_system))
                    WatermelonCard {
                        SettingsSwitchSubRow(stringResource(R.string.auto_check_updates), "", cfg.autoCheckUpdates) { c ->
                            GbcaPrefs.updateCoreConfig { it.copy(autoCheckUpdates = c) }
                        }
                        WatermelonRowSeparator()
                        SettingsNavRow(
                            icon = Icons.Filled.Restore,
                            title = stringResource(R.string.export_backup),
                            subtitle = stringResource(R.string.export_backup_sub),
                        ) { onExportBackup() }
                    }
                }

                SettingsPage.ABOUT -> {
                    WatermelonSectionLabel("STORM GBCA")
                    WatermelonCard {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("STORM GBCA v${BuildConfig.VERSION_NAME}", color = colors.text, fontFamily = SpaceGrotesk, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                            Text("mGBA + SameBoy + rcheevos + librashader + Oboe", color = colors.green, fontFamily = WatermelonMono, fontSize = 11.5.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})", color = colors.text2, fontFamily = WatermelonMono, fontSize = 11.5.sp)
                            Text("ABI: ${GbcaPrefs.detectedPrimaryAbi} | RAM: ${GbcaPrefs.detectedTotalRamMb} MB", color = colors.text2, fontFamily = WatermelonMono, fontSize = 11.5.sp)
                            Text("Core: ${runCatching { NativeCore.nativeVersion() }.getOrDefault(BuildConfig.VERSION_NAME)}", color = colors.text2, fontFamily = WatermelonMono, fontSize = 11.5.sp)
                        }
                    }

                    WatermelonSectionLabel(stringResource(R.string.check_updates))
                    WatermelonCard {
                        SettingsNavRow(
                            icon = Icons.Filled.SystemUpdate,
                            title = stringResource(R.string.check_updates),
                            subtitle = updateStatus ?: "GitHub + STORM GAMES WORLD",
                        ) {
                            updateStatus = context.getString(R.string.update_checking)
                            scope.launch {
                                val rel = StormUpdateMirrorClient.checkLatestUpdate()
                                if (rel != null) {
                                    foundUpdate = rel
                                    updateStatus = context.getString(R.string.update_available_fmt, rel.version)
                                } else {
                                    updateStatus = context.getString(R.string.update_latest, BuildConfig.VERSION_NAME)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        // STORM DS Identical Bottom-Centered Circular Back Button Footer (watermelon_settings_footer.xml)!
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bg)
                .padding(top = 12.dp, bottom = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            UnifiedBackButton(onClick = handleBack)
        }
    }

    skinDialogSystem?.let { sys ->
        ConsoleSkinPickerDialog(initialSystem = sys, onDismiss = { skinDialogSystem = null })
    }

    overlayDialogSystem?.let { sys ->
        OverlayPickerDialog(initialSystem = sys, onDismiss = { overlayDialogSystem = null })
    }

    if (showInputDialog) {
        PhysicalInputSetupDialog(
            awaitingAction = awaitingAction,
            onStartBind = onStartBindKey,
            onDismiss = { showInputDialog = false },
        )
    }

    foundUpdate?.let { rel ->
        StormUpdateDialog(
            release = rel,
            downloadState = downloadState,
            onStartDownload = {
                scope.launch {
                    StormUpdateMirrorClient.downloadApk(context, rel) { downloadState = it }
                }
            },
            onInstallApk = { apk -> StormUpdateMirrorClient.installApk(context, apk) },
            onSkipVersion = {
                GbcaPrefs.setSkippedUpdateVersion(rel.version)
                foundUpdate = null
            },
            onDismiss = { foundUpdate = null },
        )
    }
}

@Composable
private fun GridSelectChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = watermelon
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.green else colors.surface2)
            .border(1.dp, if (selected) colors.green else colors.line, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (!enabled) colors.text2.copy(alpha = 0.45f) else if (selected) Color(0xFF05060A) else colors.text,
            fontFamily = SpaceGrotesk,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SettingsSwitchRow(title: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    val colors = watermelon
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChanged(!checked) }
            .padding(vertical = 4.dp),
    ) {
        Text(title, color = colors.text, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        WatermelonSwitch(checked = checked, onCheckedChange = onChanged)
    }
}

@Composable
private fun SettingsSwitchSubRow(title: String, subtitle: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    val colors = watermelon
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChanged(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle.isNotBlank()) {
                Text(subtitle, color = colors.text2, fontSize = 11.5.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        WatermelonSwitch(checked = checked, onCheckedChange = onChanged)
    }
}

@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color? = null,
    onClick: () -> Unit,
) {
    val colors = watermelon
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background((iconTint ?: colors.green).copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconTint ?: colors.green, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = colors.text, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            if (subtitle.isNotBlank()) {
                Text(subtitle, color = colors.text2, fontSize = 11.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.text2, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun CoreChoiceRow(title: String, selected: Int, onSelect: (Int) -> Unit) {
    val colors = watermelon
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            GridSelectChip("mGBA", selected == NativeCore.CORE_MGBA, Modifier.weight(1f)) { onSelect(NativeCore.CORE_MGBA) }
            GridSelectChip("SameBoy", selected == NativeCore.CORE_SAMEBOY, Modifier.weight(1f)) { onSelect(NativeCore.CORE_SAMEBOY) }
        }
    }
}

@Composable
private fun BiosChip(label: String, loaded: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = watermelon
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (loaded) colors.greenDim else colors.surface2)
            .border(1.dp, if (loaded) colors.green else colors.line, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (loaded) "$label OK" else label,
            color = if (loaded) colors.green else colors.text,
            fontFamily = WatermelonMono,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun PhysicalInputSetupDialog(
    awaitingAction: PhysicalAction?,
    onStartBind: (PhysicalAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = watermelon
    var refreshTick by remember { mutableIntStateOf(0) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = colors.surface,
            modifier = Modifier.fillMaxWidth().border(1.dp, colors.line, RoundedCornerShape(18.dp)),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.ctrl_gamepad), color = colors.text, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.Close, contentDescription = null, tint = colors.text, modifier = Modifier.size(20.dp).clickable(onClick = onDismiss))
                }
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PhysicalAction.entries.forEach { action ->
                        val waiting = awaitingAction == action
                        val code = remember(refreshTick, awaitingAction) { GbcaPrefs.getKeyCodeForAction(action) }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (waiting) colors.greenDim else colors.surface2)
                                .border(1.dp, if (waiting) colors.green else colors.line, RoundedCornerShape(10.dp))
                                .clickable { onStartBind(action) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Text(action.name, color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(
                                text = if (waiting) "..." else KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_"),
                                color = if (waiting) colors.green else colors.text2,
                                fontFamily = WatermelonMono,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.red.copy(alpha = 0.16f))
                        .clickable {
                            GbcaPrefs.resetKeyMappings()
                            refreshTick++
                        }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.reset), color = colors.red, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                }
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    UnifiedBackButton(onClick = onDismiss)
                }
            }
        }
    }
}
