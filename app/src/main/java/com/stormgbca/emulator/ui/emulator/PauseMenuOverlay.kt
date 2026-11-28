package com.stormgbca.emulator.ui.emulator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.BatteryManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Surface
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.stormgbca.emulator.R
import com.stormgbca.emulator.data.GbcaPrefs
import com.stormgbca.emulator.data.RaGameSession
import com.stormgbca.emulator.data.RomRepository
import com.stormgbca.emulator.model.CheatItem
import com.stormgbca.emulator.model.ConsoleSystem
import com.stormgbca.emulator.model.RomEntry
import com.stormgbca.emulator.ui.common.UnifiedBackButton
import com.stormgbca.emulator.ui.common.WatermelonSwitch
import com.stormgbca.emulator.ui.common.bouncingClickable
import com.stormgbca.emulator.ui.library.formatPlayTime
import com.stormgbca.emulator.ui.ra.GameAchievementsDialog
import com.stormgbca.emulator.ui.ra.RaMeasuredProgress
import com.stormgbca.emulator.ui.theme.SpaceGrotesk
import com.stormgbca.emulator.ui.theme.WatermelonMono
import com.stormgbca.emulator.ui.theme.watermelon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import ru.stormgames.gbca.NativeCore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private enum class PauseSubDialog {
    NONE,
    SLOTS,
    VIDEO,
    AUDIO_MIXER,
    CHEATS,
    SENSORS,
    CORE_ENGINE,
    ACHIEVEMENTS,
    CUSTOMIZER,
}

private enum class ConfirmAction {
    NONE,
    RESET,
    EXIT,
}

private data class PauseTileItem(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val badge: String? = null,
    val extendedOnly: Boolean = false,
    val destructive: Boolean = false,
    val lockedInHardcore: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * In-Game Pause Menu IDENTICAL in layout, telemetry banner, quick-slot strip, compact icon tile grid,
 * extended toggle, customizer and bottom-centered circular back button to STORM DS PauseMenuOverlay.kt.
 */
@Composable
fun PauseMenuOverlay(
    rom: RomEntry,
    system: ConsoleSystem,
    boxArtPath: String?,
    sessionStartMs: Long,
    activeCoreName: String,
    activeBackendLabel: String,
    fastForwardActive: Boolean,
    rewindAvailableFrames: Int,
    raSession: RaGameSession?,
    raHardcoreActive: Boolean = false,
    raRichPresence: String = "",
    raPrimedIds: Set<Int> = emptySet(),
    raMeasuredMap: Map<Int, RaMeasuredProgress> = emptyMap(),
    linkStatus: LinkStatusInfo,
    onResume: () -> Unit,
    onQuickSave: (Int) -> Unit,
    onQuickLoad: (Int) -> Unit,
    onSaveSlot: (Int) -> Unit,
    onLoadSlot: (Int) -> Unit,
    onRewindStep: () -> Unit,
    onToggleFastForward: () -> Unit,
    onToggleHardcoreMode: (Boolean) -> Unit = {},
    onHardcoreBlocked: (String) -> Unit = {},
    onOpenSkinPicker: () -> Unit,
    onOpenOverlayPicker: () -> Unit,
    onOpenLayoutEditor: () -> Unit,
    onOpenRamFinder: () -> Unit,
    onOpenMultiplayer: () -> Unit,
    onOpenRaAccount: () -> Unit,
    onConfigApplied: () -> Unit,
    onCheatsUpdated: (List<CheatItem>) -> Unit,
    onApplySoftPatch: () -> Unit,
    onTakeScreenshot: () -> Unit,
    onOpenSettings: () -> Unit,
    onReset: () -> Unit,
    onExit: () -> Unit,
) {
    val colors = watermelon
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val cfg = GbcaPrefs.coreConfigState.value

    var subDialog by remember { mutableStateOf(PauseSubDialog.NONE) }
    var confirmAction by remember { mutableStateOf(ConfirmAction.NONE) }
    var activeQuickSlot by remember { mutableIntStateOf(GbcaPrefs.getActiveQuickSlot()) }
    var hiddenKeys by remember { mutableStateOf(GbcaPrefs.getHiddenPauseTiles()) }
    val isExtended = cfg.extendedPauseMenu

    // Live Telemetry (Clock + Battery % + Session Duration - Identical to STORM DS!)
    val batteryManager = remember(context) { context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager }
    var batteryLevel by remember { mutableStateOf<Int?>(null) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    var currentTimeStr by remember { mutableStateOf(timeFmt.format(Date())) }
    var sessionDurationMs by remember { mutableStateOf((System.currentTimeMillis() - sessionStartMs).coerceAtLeast(0L)) }

    LaunchedEffect(batteryManager) {
        while (true) {
            val level = withContext(Dispatchers.IO) {
                batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            }
            batteryLevel = if (level in 0..100) level else null
            currentTimeStr = timeFmt.format(Date())
            sessionDurationMs = (System.currentTimeMillis() - sessionStartMs).coerceAtLeast(0L)
            delay(15_000L)
        }
    }

    val formattedSession = remember(sessionDurationMs) {
        val secs = sessionDurationMs / 1000L
        val mins = secs / 60L
        val hrs = mins / 60L
        val rem = mins % 60L
        when {
            mins < 1L -> context.getString(R.string.pause_session_lt_min)
            hrs > 0L -> "${hrs}h ${rem}m"
            else -> "${mins}m"
        }
    }

    BackHandler {
        when {
            subDialog != PauseSubDialog.NONE -> subDialog = PauseSubDialog.NONE
            confirmAction != ConfirmAction.NONE -> confirmAction = ConfirmAction.NONE
            else -> onResume()
        }
    }

    val ffBadge = if (fastForwardActive) "${cfg.fastForwardMultiplier.toInt()}x" else null
    val linkBadge = if (linkStatus.mode != NativeCore.LINK_NONE && linkStatus.connected) "${linkStatus.playerCount}P" else null
    val raBadge = raSession?.let {
        val unlocked = if (raHardcoreActive) it.hardcoreUnlockedCount else it.unlockedCount
        "$unlocked/${it.totalCount}"
    }
    val rwBadge = if (raHardcoreActive) "HC" else if (rewindAvailableFrames > 0) "${rewindAvailableFrames}f" else null

    val allTiles = listOf(
        PauseTileItem("save_state", stringResource(R.string.pause_save_state), Icons.Filled.Download, "#$activeQuickSlot") {
            onQuickSave(activeQuickSlot); onResume()
        },
        PauseTileItem(
            key = "load_state",
            label = stringResource(R.string.pause_load_state),
            icon = Icons.Filled.Upload,
            badge = if (raHardcoreActive) "HC" else "#$activeQuickSlot",
            lockedInHardcore = raHardcoreActive,
        ) {
            if (raHardcoreActive) {
                onHardcoreBlocked(context.getString(R.string.ra_hardcore_blocked_load_state))
            } else {
                onQuickLoad(activeQuickSlot); onResume()
            }
        },
        PauseTileItem("fast_forward", stringResource(R.string.pause_fast_forward), Icons.Filled.FastForward, ffBadge) {
            onToggleFastForward()
        },
        PauseTileItem(
            key = "rewind",
            label = stringResource(R.string.pause_rewind),
            icon = Icons.Filled.History,
            badge = rwBadge,
            lockedInHardcore = raHardcoreActive,
        ) {
            if (raHardcoreActive) {
                onHardcoreBlocked(context.getString(R.string.ra_hardcore_blocked_rewind))
            } else {
                onRewindStep()
            }
        },
        PauseTileItem("multiplayer", stringResource(R.string.pause_multiplayer), Icons.Filled.Cable, linkBadge) {
            onOpenMultiplayer()
        },
        PauseTileItem("screenshot", stringResource(R.string.pause_screenshot), Icons.Filled.CameraAlt) {
            onTakeScreenshot(); onResume()
        },
        PauseTileItem("skins", stringResource(R.string.pause_skins), Icons.Filled.Devices, system.label) {
            onOpenSkinPicker()
        },
        PauseTileItem("overlay", stringResource(R.string.pause_overlay), Icons.Filled.Gamepad) {
            onOpenOverlayPicker()
        },
        PauseTileItem("screen", stringResource(R.string.pause_screen), Icons.Filled.AspectRatio) {
            onOpenLayoutEditor()
        },
        PauseTileItem("video", stringResource(R.string.pause_video), Icons.Filled.Palette, "${cfg.resolutionScale}x", extendedOnly = true) {
            subDialog = PauseSubDialog.VIDEO
        },
        PauseTileItem("audio", stringResource(R.string.pause_audio), Icons.Filled.GraphicEq, extendedOnly = true) {
            subDialog = PauseSubDialog.AUDIO_MIXER
        },
        PauseTileItem(
            key = "cheats",
            label = stringResource(R.string.pause_cheats),
            icon = Icons.Filled.Tune,
            badge = if (raHardcoreActive) "HC" else null,
            lockedInHardcore = raHardcoreActive,
        ) {
            if (raHardcoreActive) {
                onHardcoreBlocked(context.getString(R.string.ra_hardcore_blocked_cheats))
            } else {
                subDialog = PauseSubDialog.CHEATS
            }
        },
        PauseTileItem(
            key = "ram_finder",
            label = stringResource(R.string.pause_ram_finder),
            icon = Icons.Filled.Search,
            badge = if (raHardcoreActive) "HC" else null,
            extendedOnly = true,
            lockedInHardcore = raHardcoreActive,
        ) {
            if (raHardcoreActive) {
                onHardcoreBlocked(context.getString(R.string.ra_hardcore_blocked_ram_finder))
            } else {
                onOpenRamFinder()
            }
        },
        PauseTileItem(
            key = "patch",
            label = stringResource(R.string.pause_patch),
            icon = Icons.Filled.Build,
            badge = if (raHardcoreActive) "HC" else null,
            extendedOnly = true,
            lockedInHardcore = raHardcoreActive,
        ) {
            if (raHardcoreActive) {
                onHardcoreBlocked(context.getString(R.string.ra_hardcore_blocked_patch))
            } else {
                onApplySoftPatch()
            }
        },
        PauseTileItem("sensors", stringResource(R.string.pause_sensors), Icons.Filled.Sensors, extendedOnly = true) {
            subDialog = PauseSubDialog.SENSORS
        },
        PauseTileItem("core", stringResource(R.string.pause_core), Icons.Filled.Memory, activeCoreName, extendedOnly = true) {
            subDialog = PauseSubDialog.CORE_ENGINE
        },
        PauseTileItem("achievements", stringResource(R.string.pause_achievements), Icons.Filled.EmojiEvents, raBadge) {
            subDialog = PauseSubDialog.ACHIEVEMENTS
        },
        PauseTileItem("settings", stringResource(R.string.settings), Icons.Filled.Settings) {
            onOpenSettings()
        },
        PauseTileItem("reset", stringResource(R.string.pause_reset), Icons.Filled.RestartAlt, destructive = true) {
            confirmAction = ConfirmAction.RESET
        },
        PauseTileItem("exit", stringResource(R.string.pause_exit), Icons.AutoMirrored.Filled.Logout, destructive = true) {
            confirmAction = ConfirmAction.EXIT
        },
    )

    val displayedTiles = remember(allTiles, isExtended, hiddenKeys) {
        allTiles.filter { tile ->
            (isExtended || !tile.extendedOnly) && !hiddenKeys.contains(tile.key)
        }
    }

    val gridColumns = if (isLandscape) {
        when {
            displayedTiles.size <= 9 -> 3
            displayedTiles.size <= 12 -> 4
            else -> 5
        }
    } else {
        3
    }
    val panelMaxWidth = if (isLandscape) {
        when (gridColumns) {
            3 -> 480.dp
            4 -> 560.dp
            else -> 650.dp
        }
    } else {
        460.dp
    }
    val horizontalEdge = if (isLandscape) 16.dp else 12.dp
    val compactHeight = displayedTiles.size > 12

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (colors.isDark) 0.84f else 0.68f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onResume() },
        contentAlignment = if (isLandscape) Alignment.Center else Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = panelMaxWidth)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = if (isLandscape) 6.dp else 12.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { /* consume */ },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 1. Top Information Bar (Game Icon, Title, PAUSED badge, Play Time, Telemetry & Resume Pill - STORM DS Exact!)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalEdge, vertical = if (isLandscape) 3.dp else 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.line, RoundedCornerShape(16.dp))
                    .padding(horizontal = 11.dp, vertical = if (isLandscape) 6.dp else 7.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .size(if (isLandscape) 34.dp else 36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.surface2)
                                .border(1.dp, colors.line, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (!boxArtPath.isNullOrBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context).data(File(boxArtPath)).build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Icon(Icons.Filled.VideogameAsset, contentDescription = null, tint = colors.green, modifier = Modifier.size(18.dp))
                            }
                        }

                        Spacer(Modifier.width(9.dp))

                        Column(Modifier.weight(1f)) {
                            Text(
                                text = rom.displayTitle,
                                color = colors.text,
                                fontFamily = SpaceGrotesk,
                                fontSize = if (isLandscape) 13.5.sp else 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 1.dp)) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(colors.red.copy(alpha = 0.18f))
                                        .padding(horizontal = 5.dp, vertical = 1.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.pause_paused_label),
                                        color = colors.red,
                                        fontFamily = SpaceGrotesk,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp,
                                    )
                                }
                                if (raSession != null) {
                                    Spacer(Modifier.width(5.dp))
                                    val modeCol = if (raHardcoreActive) Color(0xFFFACC15) else Color(0xFF38BDF8)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(modeCol.copy(alpha = 0.18f))
                                            .border(0.75.dp, modeCol.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                            .clickable { onToggleHardcoreMode(!raHardcoreActive) }
                                            .padding(horizontal = 5.dp, vertical = 1.dp),
                                    ) {
                                        Text(
                                            text = if (raHardcoreActive) "RA HARDCORE" else "RA CASUAL",
                                            color = modeCol,
                                            fontFamily = WatermelonMono,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                                val pt = formatPlayTime(rom.totalPlaySeconds)
                                if (pt.isNotEmpty()) {
                                    Text(pt, color = colors.text2, fontSize = 9.5.sp, modifier = Modifier.padding(start = 6.dp))
                                }
                                if (isLandscape) {
                                    Text(
                                        text = buildString {
                                            append(" | ")
                                            append(currentTimeStr)
                                            if (batteryLevel != null) append(" | $batteryLevel%")
                                            append(" | ")
                                            append(formattedSession)
                                        },
                                        color = colors.text2,
                                        fontFamily = WatermelonMono,
                                        fontSize = 9.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.width(8.dp))

                        // Right-aligned Resume Pill Button (STORM DS signature!)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.surface2)
                                .border(1.5.dp, colors.green.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
                                .bouncingClickable(onClick = onResume)
                                .padding(horizontal = 11.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = colors.green, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.pause_resume), color = colors.green, fontFamily = SpaceGrotesk, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }

                    if (raRichPresence.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = raRichPresence,
                            color = if (raHardcoreActive) Color(0xFFFACC15) else colors.green,
                            fontFamily = WatermelonMono,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // Portrait Telemetry Strip (Clock | Battery % | Session Duration)
                    if (!isLandscape) {
                        Spacer(Modifier.height(5.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.surface2.copy(alpha = 0.78f))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.AccessTime, contentDescription = null, tint = colors.text2, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(currentTimeStr, color = colors.text, fontFamily = WatermelonMono, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            }
                            if (batteryLevel != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.BatteryStd,
                                        contentDescription = null,
                                        tint = if (batteryLevel!! <= 20) colors.red else colors.green,
                                        modifier = Modifier.size(12.dp),
                                    )
                                    Spacer(Modifier.width(3.dp))
                                    Text("$batteryLevel%", color = colors.text, fontFamily = WatermelonMono, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.HourglassTop, contentDescription = null, tint = colors.text2, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("$formattedSession | $activeBackendLabel", color = colors.text2, fontFamily = WatermelonMono, fontSize = 10.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }

            // 2. Quick Save Slot Bar (Portrait - STORM DS Exact!)
            if (!isLandscape) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalEdge, vertical = 3.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(colors.surface2)
                        .border(1.dp, colors.line, RoundedCornerShape(11.dp))
                        .clickable { subDialog = PauseSubDialog.SLOTS }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Layers, contentDescription = null, tint = colors.green, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(stringResource(R.string.pause_quick_slot_title), color = colors.text, fontFamily = SpaceGrotesk, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(stringResource(R.string.pause_quick_slot_fmt, activeQuickSlot), color = colors.green, fontFamily = WatermelonMono, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // 3. STORM DS Compact Customizable Icon Tile Grid (Zero Section Headers!)
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = if (isLandscape) 260.dp else 430.dp)
                    .padding(horizontal = horizontalEdge, vertical = 4.dp),
            ) {
                items(displayedTiles, key = { it.key }) { tile ->
                    PauseTileCard(tile = tile, compactHeight = compactHeight)
                }
            }

            // 4. Bottom Controls Row: Back (Landscape) + Quick Slot (Landscape) + Extended Menu Toggle + Customize
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalEdge, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isLandscape) {
                    UnifiedBackButton(onClick = onResume, size = 38.dp, iconSize = 18.dp)
                    Row(
                        modifier = Modifier
                            .height(38.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(colors.surface2)
                            .border(1.dp, colors.line, RoundedCornerShape(11.dp))
                            .clickable { subDialog = PauseSubDialog.SLOTS }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(Icons.Filled.Layers, contentDescription = null, tint = colors.green, modifier = Modifier.size(15.dp))
                        Text(stringResource(R.string.pause_quick_slot_fmt, activeQuickSlot), color = colors.green, fontFamily = WatermelonMono, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Extended Menu Switch Pill
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(if (isLandscape) 38.dp else 40.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(colors.surface2)
                        .border(1.dp, colors.line, RoundedCornerShape(11.dp))
                        .clickable { GbcaPrefs.updateCoreConfig { it.copy(extendedPauseMenu = !isExtended) } }
                        .padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f, fill = false)) {
                        Icon(Icons.Filled.DashboardCustomize, contentDescription = null, tint = if (isExtended) colors.green else colors.text2, modifier = Modifier.size(15.dp))
                        Text(
                            text = stringResource(R.string.pause_extended_menu),
                            color = colors.text,
                            fontFamily = SpaceGrotesk,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Switch(
                        checked = isExtended,
                        onCheckedChange = { v -> GbcaPrefs.updateCoreConfig { it.copy(extendedPauseMenu = v) } },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.green,
                            checkedTrackColor = colors.greenDim,
                            uncheckedThumbColor = colors.text,
                            uncheckedTrackColor = colors.line,
                        ),
                    )
                }

                // Customize Button
                Box(
                    modifier = Modifier
                        .height(if (isLandscape) 38.dp else 40.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(colors.surface2)
                        .border(1.dp, colors.line, RoundedCornerShape(11.dp))
                        .bouncingClickable { subDialog = PauseSubDialog.CUSTOMIZER }
                        .padding(horizontal = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(Icons.Filled.Tune, contentDescription = null, tint = colors.green, modifier = Modifier.size(15.dp))
                        Text(stringResource(R.string.pause_customize), color = colors.text, fontFamily = SpaceGrotesk, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // 5. Portrait Bottom-Centered Circular Back Button (STORM DS Exact!)
            if (!isLandscape) {
                Spacer(Modifier.height(6.dp))
                UnifiedBackButton(
                    onClick = onResume,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }

    // Confirmation Dialog for Reset / Exit
    if (confirmAction != ConfirmAction.NONE) {
        val isReset = confirmAction == ConfirmAction.RESET
        Dialog(onDismissRequest = { confirmAction = ConfirmAction.NONE }) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = colors.surface,
                modifier = Modifier.fillMaxWidth().border(1.dp, colors.line, RoundedCornerShape(18.dp)),
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(if (isReset) R.string.pause_reset else R.string.pause_exit),
                        color = colors.red,
                        fontFamily = SpaceGrotesk,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(if (isReset) R.string.pause_confirm_reset else R.string.pause_confirm_exit),
                        color = colors.text,
                        fontSize = 13.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.surface2)
                                .clickable { confirmAction = ConfirmAction.NONE }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(stringResource(R.string.cancel), color = colors.text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.red)
                                .clickable {
                                    val act = confirmAction
                                    confirmAction = ConfirmAction.NONE
                                    if (act == ConfirmAction.RESET) onReset() else onExit()
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(stringResource(R.string.confirm), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    // Sub-Dialogs
    when (subDialog) {
        PauseSubDialog.SLOTS -> SaveSlotsDialog(
            rom = rom,
            activeSlot = activeQuickSlot,
            raHardcoreActive = raHardcoreActive,
            onHardcoreBlocked = onHardcoreBlocked,
            onSelectActive = { slot ->
                activeQuickSlot = slot
                GbcaPrefs.setActiveQuickSlot(slot)
            },
            onSaveSlot = { slot ->
                activeQuickSlot = slot
                GbcaPrefs.setActiveQuickSlot(slot)
                onSaveSlot(slot)
            },
            onLoadSlot = { slot ->
                activeQuickSlot = slot
                GbcaPrefs.setActiveQuickSlot(slot)
                onLoadSlot(slot)
                subDialog = PauseSubDialog.NONE
                onResume()
            },
            onDismiss = { subDialog = PauseSubDialog.NONE },
        )
        PauseSubDialog.VIDEO -> VideoSettingsDialog(system = system, onConfigApplied = onConfigApplied, onDismiss = { subDialog = PauseSubDialog.NONE })
        PauseSubDialog.AUDIO_MIXER -> AudioMixerDialog(onConfigApplied = onConfigApplied, onDismiss = { subDialog = PauseSubDialog.NONE })
        PauseSubDialog.CHEATS -> CheatsDialog(rom = rom, onCheatsUpdated = onCheatsUpdated, onDismiss = { subDialog = PauseSubDialog.NONE })
        PauseSubDialog.SENSORS -> SensorsDialog(onConfigApplied = onConfigApplied, onDismiss = { subDialog = PauseSubDialog.NONE })
        PauseSubDialog.CORE_ENGINE -> CoreEngineDialog(system = system, onConfigApplied = onConfigApplied, onDismiss = { subDialog = PauseSubDialog.NONE })
        PauseSubDialog.ACHIEVEMENTS -> GameAchievementsDialog(
            gameTitleFallback = rom.displayTitle,
            session = raSession,
            raHardcoreActive = raHardcoreActive,
            richPresenceText = raRichPresence,
            primedIds = raPrimedIds,
            measuredMap = raMeasuredMap,
            onToggleHardcoreMode = onToggleHardcoreMode,
            onOpenLogin = { subDialog = PauseSubDialog.NONE; onOpenRaAccount() },
            onDismiss = { subDialog = PauseSubDialog.NONE },
        )
        PauseSubDialog.CUSTOMIZER -> PauseCustomizerDialog(
            allTiles = allTiles,
            hiddenKeys = hiddenKeys,
            onToggleHidden = { key ->
                val next = hiddenKeys.toMutableSet()
                if (next.contains(key)) next.remove(key) else next.add(key)
                hiddenKeys = next
                GbcaPrefs.setHiddenPauseTiles(next)
            },
            onResetAll = {
                hiddenKeys = emptySet()
                GbcaPrefs.setHiddenPauseTiles(emptySet())
            },
            onDismiss = { subDialog = PauseSubDialog.NONE },
        )
        PauseSubDialog.NONE -> {}
    }
}

@Composable
private fun PauseTileCard(
    tile: PauseTileItem,
    compactHeight: Boolean,
) {
    val colors = watermelon
    val iconTint = when {
        tile.lockedInHardcore -> colors.text2.copy(alpha = 0.45f)
        tile.destructive -> colors.red
        else -> colors.green
    }
    val borderCol = when {
        tile.lockedInHardcore -> Color(0xFFFACC15).copy(alpha = 0.35f)
        tile.destructive -> colors.red.copy(alpha = 0.45f)
        else -> colors.line
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compactHeight) 62.dp else 72.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (tile.lockedInHardcore) colors.surface.copy(alpha = 0.55f) else colors.surface)
            .border(1.dp, borderCol, RoundedCornerShape(13.dp))
            .bouncingClickable(onClick = tile.onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        if (!tile.badge.isNullOrBlank()) {
            val badgeBg = if (tile.lockedInHardcore) Color(0x33FACC15) else colors.greenDim
            val badgeFg = if (tile.lockedInHardcore) Color(0xFFFACC15) else colors.green
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(5.dp))
                    .background(badgeBg)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            ) {
                Text(tile.badge, color = badgeFg, fontFamily = WatermelonMono, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(tile.icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(if (compactHeight) 20.dp else 22.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                text = tile.label,
                color = when {
                    tile.lockedInHardcore -> colors.text2.copy(alpha = 0.55f)
                    tile.destructive -> colors.red
                    else -> colors.text
                },
                fontFamily = SpaceGrotesk,
                fontSize = if (compactHeight) 10.5.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PauseCustomizerDialog(
    allTiles: List<PauseTileItem>,
    hiddenKeys: Set<String>,
    onToggleHidden: (String) -> Unit,
    onResetAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = watermelon
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = colors.surface,
            modifier = Modifier.fillMaxWidth().border(1.dp, colors.line, RoundedCornerShape(18.dp)),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.pause_customize), color = colors.text, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.Close, contentDescription = null, tint = colors.text, modifier = Modifier.size(20.dp).clickable(onClick = onDismiss))
                }
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    allTiles.forEach { tile ->
                        val visible = !hiddenKeys.contains(tile.key)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.surface2)
                                .clickable { onToggleHidden(tile.key) }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                        ) {
                            Icon(tile.icon, contentDescription = null, tint = if (tile.destructive) colors.red else colors.green, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(tile.label, color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            WatermelonSwitch(checked = visible, onCheckedChange = { onToggleHidden(tile.key) })
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surface2)
                            .clickable(onClick = onResetAll)
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(R.string.reset), color = colors.text, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.green)
                            .clickable(onClick = onDismiss)
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(R.string.confirm), color = Color(0xFF05060A), fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SaveSlotsDialog(
    rom: RomEntry,
    activeSlot: Int,
    raHardcoreActive: Boolean,
    onHardcoreBlocked: (String) -> Unit,
    onSelectActive: (Int) -> Unit,
    onSaveSlot: (Int) -> Unit,
    onLoadSlot: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = watermelon
    val context = LocalContext.current
    var refreshTick by remember { mutableIntStateOf(0) }
    val fmt = remember { SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = colors.surface,
            modifier = Modifier.fillMaxWidth().border(1.dp, colors.line, RoundedCornerShape(18.dp)),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.pause_slots), color = colors.text, fontFamily = SpaceGrotesk, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.Close, contentDescription = null, tint = colors.text, modifier = Modifier.size(20.dp).clickable(onClick = onDismiss))
                }
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (slot in 1..10) {
                        val file = remember(refreshTick, slot) { RomRepository.getStateFile(context, rom, slot) }
                        val thumb = remember(refreshTick, slot) { RomRepository.getStateThumbFile(context, rom, slot) }
                        val exists = file.exists() && file.length() > 0L
                        val isAct = slot == activeSlot

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surface2)
                                .border(if (isAct) 1.5.dp else 1.dp, if (isAct) colors.green else colors.line, RoundedCornerShape(12.dp))
                                .clickable { onSelectActive(slot) }
                                .padding(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 64.dp, height = 48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF0A0E17)),
                                contentAlignment = Alignment.Center,
                            ) {
                                val thumbPath = thumb.absolutePath
                                val bmp by produceState<ImageBitmap?>(initialValue = null, key1 = thumbPath, key2 = refreshTick) {
                                    value = withContext(Dispatchers.IO) {
                                        val thumbFile = File(thumbPath)
                                        if (!thumbFile.exists() || thumbFile.length() <= 0L) return@withContext null
                                        val opts = BitmapFactory.Options().apply {
                                            inPreferredConfig = Bitmap.Config.RGB_565
                                            inSampleSize = 2
                                        }
                                        BitmapFactory.decodeFile(thumbPath, opts)?.asImageBitmap()
                                    }
                                }
                                val decodedThumb = bmp
                                if (decodedThumb != null) {
                                    Image(bitmap = decodedThumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                } else {
                                    Text("#$slot", color = colors.text2, fontFamily = WatermelonMono, fontSize = 12.sp)
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.pause_quick_slot_fmt, slot), color = colors.text, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    text = if (exists) fmt.format(Date(file.lastModified())) else stringResource(R.string.empty_slot),
                                    color = colors.text2,
                                    fontFamily = WatermelonMono,
                                    fontSize = 11.sp,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.greenDim)
                                    .clickable { onSaveSlot(slot); refreshTick++ }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Text(stringResource(R.string.save), color = colors.green, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            if (exists) {
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (raHardcoreActive) colors.surface else colors.green)
                                        .border(1.dp, if (raHardcoreActive) Color(0xFFFACC15).copy(alpha = 0.5f) else colors.green, RoundedCornerShape(8.dp))
                                        .clickable {
                                            if (raHardcoreActive) {
                                                onHardcoreBlocked(context.getString(R.string.ra_hardcore_blocked_load_state))
                                            } else {
                                                onLoadSlot(slot)
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                ) {
                                    Text(
                                        text = if (raHardcoreActive) "HC" else stringResource(R.string.load),
                                        color = if (raHardcoreActive) Color(0xFFFACC15) else Color(0xFF05060A),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoSettingsDialog(
    system: ConsoleSystem,
    onConfigApplied: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = watermelon
    val cfg = GbcaPrefs.coreConfigState.value
    val overlayProf = when (system) {
        ConsoleSystem.GB -> GbcaPrefs.gbOverlayState.value
        ConsoleSystem.GBC -> GbcaPrefs.gbcOverlayState.value
        ConsoleSystem.GBA -> GbcaPrefs.gbaOverlayState.value
    }
    val activeFilter = overlayProf.shaderOverlay
    val paletteCount = remember { NativeCore.nativeGetDmgPaletteCount().coerceAtLeast(1) }
    val upscaleModes = listOf(
        NativeCore.UPSCALE_NONE to "Nearest",
        NativeCore.UPSCALE_XBRZ to "xBRZ HD",
        NativeCore.UPSCALE_SCALE_EPX to "Scale2x / EPX",
        NativeCore.UPSCALE_MMPX to "MMPX Sharp",
        NativeCore.UPSCALE_LCD_HD to "LCD Subpixel HD",
        NativeCore.UPSCALE_CRT_HD to "CRT Scanlines HD",
    )
    val filterModes = listOf(
        NativeCore.FILTER_RGB_SUBPIXEL to R.string.filter_rgb_subpixel,
        NativeCore.FILTER_LCD_GRID to R.string.filter_lcd_grid,
        NativeCore.FILTER_SHARP_BILINEAR to R.string.filter_sharp_bilinear,
        NativeCore.FILTER_CRT_SCANLINES to R.string.filter_crt_scanlines,
        NativeCore.FILTER_XBRZ_LITE to R.string.filter_xbrz_lite,
        NativeCore.FILTER_NEAREST to R.string.filter_nearest,
        NativeCore.FILTER_BILINEAR to R.string.filter_bilinear,
        NativeCore.FILTER_FSR1 to R.string.filter_fsr1,
        NativeCore.FILTER_FSR3 to R.string.filter_fsr3,
    )
    val fsrQualities = listOf(
        NativeCore.FSR_QUALITY_QUALITY to R.string.vid_fsr_q_quality,
        NativeCore.FSR_QUALITY_BALANCED to R.string.vid_fsr_q_balanced,
        NativeCore.FSR_QUALITY_PERFORMANCE to R.string.vid_fsr_q_performance,
        NativeCore.FSR_QUALITY_ULTRA_PERFORMANCE to R.string.vid_fsr_q_ultra,
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = colors.surface,
            modifier = Modifier.fillMaxWidth().border(1.dp, colors.line, RoundedCornerShape(18.dp)),
        ) {
            Column(
                modifier = Modifier.padding(16.dp).heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.set_video_title), color = colors.text, fontFamily = SpaceGrotesk, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.Close, contentDescription = null, tint = colors.text, modifier = Modifier.size(20.dp).clickable(onClick = onDismiss))
                }

                Text(stringResource(R.string.vid_scale), color = colors.text, fontFamily = WatermelonMono, fontSize = 11.5.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(1, 2, 3, 4).forEach { sc ->
                        val sel = cfg.resolutionScale == sc
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (sel) colors.green else colors.surface2)
                                .border(1.dp, if (sel) colors.green else colors.line, RoundedCornerShape(9.dp))
                                .clickable {
                                    GbcaPrefs.updateCoreConfig {
                                        val nextMode = if (sc > 1 && it.upscaleMode == NativeCore.UPSCALE_NONE) NativeCore.UPSCALE_XBRZ else it.upscaleMode
                                        it.copy(resolutionScale = sc, upscaleMode = nextMode)
                                    }
                                    onConfigApplied()
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${sc}x", color = if (sel) Color(0xFF05060A) else colors.text, fontFamily = WatermelonMono, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                Text(stringResource(R.string.vid_upscaler), color = colors.text, fontFamily = WatermelonMono, fontSize = 11.5.sp)
                upscaleModes.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        pair.forEach { (id, title) ->
                            val sel = cfg.upscaleMode == id
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(if (sel) colors.greenDim else colors.surface2)
                                    .border(1.dp, if (sel) colors.green else colors.line, RoundedCornerShape(9.dp))
                                    .clickable {
                                        GbcaPrefs.updateCoreConfig {
                                            val nextScale = if (id != NativeCore.UPSCALE_NONE && it.resolutionScale < 2) 2 else it.resolutionScale
                                            it.copy(upscaleMode = id, resolutionScale = nextScale)
                                        }
                                        onConfigApplied()
                                    }
                                    .padding(vertical = 8.dp, horizontal = 6.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(title, color = if (sel) colors.green else colors.text, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                }

                val palName = NativeCore.nativeGetDmgPaletteName(cfg.dmgPaletteIndex) ?: "DMG Olive"
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("DMG Palette (${cfg.dmgPaletteIndex + 1}/$paletteCount)", color = colors.text, fontSize = 12.sp)
                    Text(palName, color = colors.green, fontFamily = WatermelonMono, fontSize = 11.sp)
                }
                Slider(
                    value = cfg.dmgPaletteIndex.toFloat(),
                    onValueChange = { v ->
                        GbcaPrefs.updateCoreConfig { it.copy(dmgPaletteIndex = v.toInt().coerceIn(0, paletteCount - 1)) }
                        onConfigApplied()
                    },
                    valueRange = 0f..(paletteCount - 1).toFloat(),
                    colors = SliderDefaults.colors(thumbColor = colors.green, activeTrackColor = colors.green),
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("LCD Ghosting", color = colors.text, fontSize = 12.sp)
                    Text("${cfg.lcdGhosting}%", color = colors.green, fontFamily = WatermelonMono, fontSize = 11.sp)
                }
                Slider(
                    value = cfg.lcdGhosting.toFloat(),
                    onValueChange = { v ->
                        GbcaPrefs.updateCoreConfig { it.copy(lcdGhosting = v.toInt().coerceIn(0, 100)) }
                        onConfigApplied()
                    },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(thumbColor = colors.green, activeTrackColor = colors.green),
                )

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Super Game Boy Borders", color = colors.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    WatermelonSwitch(
                        checked = cfg.sgbBorders,
                        onCheckedChange = { c ->
                            GbcaPrefs.updateCoreConfig { it.copy(sgbBorders = c) }
                            onConfigApplied()
                        },
                    )
                }

                Text(stringResource(R.string.vid_builtin_filter), color = colors.text, fontFamily = WatermelonMono, fontSize = 11.5.sp)
                filterModes.forEach { (id, titleRes) ->
                    val sel = activeFilter == id
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (sel) colors.greenDim else colors.surface2)
                            .border(1.dp, if (sel) colors.green else colors.line, RoundedCornerShape(9.dp))
                            .clickable {
                                GbcaPrefs.updateOverlayProfile(system) { it.copy(shaderOverlay = id) }
                                GbcaPrefs.updateCoreConfig { it.copy(videoFilter = id) }
                                onConfigApplied()
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = stringResource(titleRes),
                            color = if (sel) colors.green else colors.text,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Text(stringResource(R.string.vid_fsr_quality), color = colors.text, fontFamily = WatermelonMono, fontSize = 11.5.sp)
                fsrQualities.forEach { (qId, qRes) ->
                    val sel = cfg.fsrQualityMode == qId
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (sel) colors.greenDim else colors.surface2)
                            .border(1.dp, if (sel) colors.green else colors.line, RoundedCornerShape(9.dp))
                            .clickable {
                                GbcaPrefs.updateCoreConfig { it.copy(fsrQualityMode = qId) }
                                onConfigApplied()
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = stringResource(qRes),
                            color = if (sel) colors.green else colors.text,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.vid_fsr_sharpness), color = colors.text, fontSize = 12.sp)
                    Text("${cfg.fsrSharpness}%", color = colors.green, fontFamily = WatermelonMono, fontSize = 11.sp)
                }
                Slider(
                    value = cfg.fsrSharpness.toFloat(),
                    onValueChange = { v ->
                        GbcaPrefs.updateCoreConfig { it.copy(fsrSharpness = v.toInt().coerceIn(0, 100)) }
                        onConfigApplied()
                    },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(thumbColor = colors.green, activeTrackColor = colors.green),
                )

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(stringResource(R.string.vid_frame_gen_title), color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.vid_frame_gen_sub), color = colors.text2, fontSize = 10.5.sp)
                    }
                    WatermelonSwitch(
                        checked = cfg.frameGenerationEnabled,
                        onCheckedChange = { c ->
                            GbcaPrefs.updateCoreConfig { it.copy(frameGenerationEnabled = c) }
                            onConfigApplied()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioMixerDialog(
    onConfigApplied: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = watermelon
    val cfg = GbcaPrefs.coreConfigState.value
    val chNames = listOf("PSG 1 (Pulse 1)", "PSG 2 (Pulse 2)", "PSG 3 (Wave)", "PSG 4 (Noise)", "DMA A (GBA)", "DMA B (GBA)")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = colors.surface,
            modifier = Modifier.fillMaxWidth().border(1.dp, colors.line, RoundedCornerShape(18.dp)),
        ) {
            Column(
                modifier = Modifier.padding(16.dp).heightIn(max = 490.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.set_audio_title), color = colors.text, fontFamily = SpaceGrotesk, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.Close, contentDescription = null, tint = colors.text, modifier = Modifier.size(20.dp).clickable(onClick = onDismiss))
                }

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.aud_mute), color = colors.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    WatermelonSwitch(
                        checked = cfg.audioMuted,
                        onCheckedChange = { m ->
                            GbcaPrefs.updateCoreConfig { it.copy(audioMuted = m) }
                            NativeCore.nativeAudioPause(m)
                        },
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.aud_master), color = colors.text, fontSize = 12.5.sp)
                    Text("${(cfg.masterVolume * 100).toInt()}%", color = colors.green, fontFamily = WatermelonMono, fontSize = 11.sp)
                }
                Slider(
                    value = cfg.masterVolume,
                    onValueChange = { v -> GbcaPrefs.updateCoreConfig { it.copy(masterVolume = v) }; onConfigApplied() },
                    valueRange = 0f..1.5f,
                    colors = SliderDefaults.colors(thumbColor = colors.green, activeTrackColor = colors.green),
                )

                chNames.forEachIndexed { idx, name ->
                    val vol = cfg.channelVolumes.getOrElse(idx) { 1f }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(name, color = colors.text2, fontSize = 12.sp)
                        Text("${(vol * 100).toInt()}%", color = colors.green, fontFamily = WatermelonMono, fontSize = 11.sp)
                    }
                    Slider(
                        value = vol,
                        onValueChange = { v ->
                            val next = cfg.channelVolumes.toMutableList()
                            while (next.size < 6) next.add(1f)
                            next[idx] = v
                            GbcaPrefs.updateCoreConfig { it.copy(channelVolumes = next) }
                            onConfigApplied()
                        },
                        valueRange = 0f..1.5f,
                        colors = SliderDefaults.colors(thumbColor = colors.green, activeTrackColor = colors.green),
                    )
                }
            }
        }
    }
}

@Composable
private fun CheatsDialog(
    rom: RomEntry,
    onCheatsUpdated: (List<CheatItem>) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = watermelon
    val key = remember(rom) { RomRepository.getRomStorageKey(rom) }
    var cheats by remember { mutableStateOf(GbcaPrefs.getCheatsForRom(key)) }
    var newName by remember { mutableStateOf("") }
    var newCode by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = colors.surface,
            modifier = Modifier.fillMaxWidth().border(1.dp, colors.line, RoundedCornerShape(18.dp)),
        ) {
            Column(
                modifier = Modifier.padding(16.dp).heightIn(max = 490.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.pause_cheats), color = colors.text, fontFamily = SpaceGrotesk, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.Close, contentDescription = null, tint = colors.text, modifier = Modifier.size(20.dp).clickable(onClick = onDismiss))
                }
                val tfColors = TextFieldDefaults.outlinedTextFieldColors(
                    textColor = colors.text,
                    focusedBorderColor = colors.green,
                    unfocusedBorderColor = colors.line,
                    focusedLabelColor = colors.green,
                    unfocusedLabelColor = colors.text2,
                )
                OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Name") }, singleLine = true, colors = tfColors, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = newCode, onValueChange = { newCode = it }, label = { Text("Code (GS / AR / CB / GG)") }, colors = tfColors, modifier = Modifier.fillMaxWidth())
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.green)
                        .clickable {
                            if (newCode.isNotBlank()) {
                                val next = cheats + CheatItem(UUID.randomUUID().toString(), newName.ifBlank { "Cheat ${cheats.size + 1}" }, newCode.trim(), true)
                                cheats = next
                                GbcaPrefs.saveCheatsForRom(key, next)
                                onCheatsUpdated(next)
                                newName = ""; newCode = ""
                            }
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = Color(0xFF05060A), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add", color = Color(0xFF05060A), fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    }
                }
                cheats.forEach { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.surface2).padding(10.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, color = colors.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(item.code, color = colors.text2, fontFamily = WatermelonMono, fontSize = 10.5.sp)
                        }
                        WatermelonSwitch(checked = item.enabled, onCheckedChange = { c ->
                            val next = cheats.map { if (it.id == item.id) it.copy(enabled = c) else it }
                            cheats = next; GbcaPrefs.saveCheatsForRom(key, next); onCheatsUpdated(next)
                        })
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = colors.red, modifier = Modifier.size(20.dp).clickable {
                            val next = cheats.filterNot { it.id == item.id }
                            cheats = next; GbcaPrefs.saveCheatsForRom(key, next); onCheatsUpdated(next)
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun SensorsDialog(
    onConfigApplied: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = watermelon
    val cfg = GbcaPrefs.coreConfigState.value
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = colors.surface,
            modifier = Modifier.fillMaxWidth().border(1.dp, colors.line, RoundedCornerShape(18.dp)),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.pause_sensors), color = colors.text, fontFamily = SpaceGrotesk, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.Close, contentDescription = null, tint = colors.text, modifier = Modifier.size(20.dp).clickable(onClick = onDismiss))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Solar Sensor (Boktai LUX)", color = colors.text, fontSize = 13.sp)
                    Text("${cfg.solarLux}/10", color = colors.green, fontFamily = WatermelonMono, fontSize = 12.sp)
                }
                Slider(
                    value = cfg.solarLux.toFloat(),
                    onValueChange = { v -> GbcaPrefs.updateCoreConfig { it.copy(solarLux = v.toInt().coerceIn(0, 10)) }; onConfigApplied() },
                    valueRange = 0f..10f,
                    steps = 9,
                    colors = SliderDefaults.colors(thumbColor = colors.green, activeTrackColor = colors.green),
                )
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Tilt / Accelerometer", color = colors.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    WatermelonSwitch(checked = cfg.sensorTiltEnabled, onCheckedChange = { c -> GbcaPrefs.updateCoreConfig { it.copy(sensorTiltEnabled = c) } })
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Gyroscope (WarioWare)", color = colors.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    WatermelonSwitch(checked = cfg.sensorGyroEnabled, onCheckedChange = { c -> GbcaPrefs.updateCoreConfig { it.copy(sensorGyroEnabled = c) } })
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Rumble Haptics", color = colors.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    WatermelonSwitch(checked = cfg.rumbleEnabled, onCheckedChange = { c -> GbcaPrefs.updateCoreConfig { it.copy(rumbleEnabled = c) } })
                }
            }
        }
    }
}

@Composable
private fun CoreEngineDialog(
    system: ConsoleSystem,
    onConfigApplied: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = watermelon
    val cfg = GbcaPrefs.coreConfigState.value
    val active = when (system) {
        ConsoleSystem.GB -> cfg.preferredCoreGb
        ConsoleSystem.GBC -> cfg.preferredCoreGbc
        ConsoleSystem.GBA -> NativeCore.CORE_MGBA
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = colors.surface,
            modifier = Modifier.fillMaxWidth().border(1.dp, colors.line, RoundedCornerShape(18.dp)),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("${stringResource(R.string.pause_core)} (${system.label})", color = colors.text, fontFamily = SpaceGrotesk, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.Close, contentDescription = null, tint = colors.text, modifier = Modifier.size(20.dp).clickable(onClick = onDismiss))
                }
                listOf(
                    NativeCore.CORE_MGBA to "mGBA (Primary GB/GBC/GBA + Link + Printer)",
                    NativeCore.CORE_SAMEBOY to "SameBoy (Cycle-Accurate GB/GBC)",
                ).forEach { (id, title) ->
                    val enabled = system != ConsoleSystem.GBA || id == NativeCore.CORE_MGBA
                    val sel = active == id
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (sel) colors.greenDim else colors.surface2)
                            .border(1.dp, if (sel) colors.green else colors.line, RoundedCornerShape(10.dp))
                            .clickable(enabled = enabled) {
                                GbcaPrefs.updateCoreConfig {
                                    when (system) {
                                        ConsoleSystem.GB -> it.copy(preferredCoreGb = id)
                                        ConsoleSystem.GBC -> it.copy(preferredCoreGbc = id)
                                        ConsoleSystem.GBA -> it
                                    }
                                }
                                onConfigApplied()
                            }
                            .padding(12.dp),
                    ) {
                        Text(title, color = if (!enabled) colors.text2.copy(alpha = 0.45f) else if (sel) colors.green else colors.text, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
