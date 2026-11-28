package com.stormgbca.emulator.ui.emulator.overlay

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Upload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormgbca.emulator.R
import com.stormgbca.emulator.data.GbcaPrefs
import com.stormgbca.emulator.model.ConsoleSystem
import com.stormgbca.emulator.model.CustomControlPos
import com.stormgbca.emulator.model.OrientationControlsLayout
import com.stormgbca.emulator.model.OverlayLayoutPreset
import com.stormgbca.emulator.model.ScreenRectConfig
import com.stormgbca.emulator.ui.common.WatermelonSwitch
import com.stormgbca.emulator.ui.emulator.skin.ConsoleSkinOverlayView
import com.stormgbca.emulator.ui.emulator.skin.GbcaSkinId
import com.stormgbca.emulator.ui.emulator.skin.computeGameViewportRect
import com.stormgbca.emulator.ui.theme.AppThemeManager
import com.stormgbca.emulator.ui.theme.SpaceGrotesk
import com.stormgbca.emulator.ui.theme.StormWatermelonTheme
import com.stormgbca.emulator.ui.theme.WatermelonMono
import com.stormgbca.emulator.ui.theme.watermelon
import kotlin.math.roundToInt

class LayoutEditorActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        AppThemeManager.applyEdgeToEdgeTheme(this)
        val systemName = intent.getStringExtra("system") ?: ConsoleSystem.GBA.name
        val initialSystem = runCatching { ConsoleSystem.valueOf(systemName) }.getOrDefault(ConsoleSystem.GBA)

        setContent {
            StormWatermelonTheme {
                LayoutEditorScreen(
                    initialSystem = initialSystem,
                    onDone = { finish() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AppThemeManager.applyImmersiveFullscreen(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) AppThemeManager.applyImmersiveFullscreen(this)
    }
}

@Composable
private fun LayoutEditorScreen(
    initialSystem: ConsoleSystem,
    onDone: () -> Unit,
) {
    val colors = watermelon
    var activeSystem by remember { mutableStateOf(initialSystem) }
    var profile by remember(activeSystem) { mutableStateOf(GbcaPrefs.getOverlayProfile(activeSystem)) }
    var panelExpanded by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF05070E)),
    ) {
        val w = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val h = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val isLandscape = w > h
        val density = LocalDensity.current

        val currentScreenCfg = if (isLandscape) profile.landscapeScreen else profile.portraitScreen
        val currentControls = if (isLandscape) profile.landscapeControls else profile.portraitControls

        fun updateScreenCfg(transform: (ScreenRectConfig) -> ScreenRectConfig) {
            val nextCfg = transform(currentScreenCfg)
            profile = if (isLandscape) {
                profile.copy(landscapeScreen = nextCfg)
            } else {
                profile.copy(portraitScreen = nextCfg)
            }
        }

        fun updateControls(transform: (OrientationControlsLayout) -> OrientationControlsLayout) {
            val nextCtrl = transform(currentControls)
            profile = if (isLandscape) {
                profile.copy(layoutPreset = OverlayLayoutPreset.CUSTOM.key, landscapeControls = nextCtrl)
            } else {
                profile.copy(layoutPreset = OverlayLayoutPreset.CUSTOM.key, portraitControls = nextCtrl)
            }
        }

        val nativeW = if (activeSystem == ConsoleSystem.GBA) 240 else 160
        val nativeH = if (activeSystem == ConsoleSystem.GBA) 160 else 144
        val activeSkin = GbcaSkinId.fromKey(GbcaPrefs.getSkinKey(activeSystem), activeSystem)
        val viewportRect = computeGameViewportRect(
            containerW = w.roundToInt(),
            containerH = h.roundToInt(),
            system = activeSystem,
            skinId = activeSkin,
            aspectMode = currentScreenCfg.aspectMode,
            nativeW = nativeW,
            nativeH = nativeH,
            customScreen = currentScreenCfg,
        )

        val hideSkinNow = currentScreenCfg.hideSkinInFullscreen && (
            currentScreenCfg.screenPreset == 1 ||
                currentScreenCfg.screenPreset == 2 ||
                (currentScreenCfg.useCustom && currentScreenCfg.widthFraction >= 0.96f && currentScreenCfg.heightFraction >= 0.88f)
            )
        if (!hideSkinNow && activeSkin != GbcaSkinId.NONE) {
            ConsoleSkinOverlayView(
                skinId = activeSkin,
                screenRect = viewportRect,
                dimmingPercent = GbcaPrefs.getSkinDimming(activeSystem),
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Alignment center snap guides
        Canvas(modifier = Modifier.fillMaxSize()) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
            drawLine(
                color = colors.green.copy(alpha = 0.20f),
                start = Offset(size.width / 2f, 0f),
                end = Offset(size.width / 2f, size.height),
                strokeWidth = 1.5f,
                pathEffect = dash,
            )
            drawLine(
                color = colors.green.copy(alpha = 0.20f),
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 1.5f,
                pathEffect = dash,
            )
        }

        // INTERACTIVE DRAGGABLE & RESIZABLE GAME SCREEN FRAME
        val vpWidthDp = with(density) { viewportRect.width.toDp() }
        val vpHeightDp = with(density) { viewportRect.height.toDp() }
        Box(
            modifier = Modifier
                .offset { IntOffset(viewportRect.left.roundToInt(), viewportRect.top.roundToInt()) }
                .size(width = vpWidthDp, height = vpHeightDp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xBB0F172A))
                .border(2.dp, colors.green, RoundedCornerShape(8.dp))
                .pointerInput(w, h, activeSystem, isLandscape, currentScreenCfg) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val curCx = (viewportRect.left + viewportRect.width / 2f) / w
                        val curCy = (viewportRect.top + viewportRect.height / 2f) / h
                        val curWf = viewportRect.width / w
                        val curHf = viewportRect.height / h
                        var nx = (curCx + dragAmount.x / w).coerceIn(0.05f, 0.95f)
                        val ny = (curCy + dragAmount.y / h).coerceIn(0.05f, 0.95f)
                        if (kotlin.math.abs(nx - 0.5f) < 0.018f) nx = 0.5f
                        updateScreenCfg {
                            it.copy(
                                useCustom = true,
                                screenPreset = 4,
                                centerX = nx,
                                centerY = ny,
                                widthFraction = if (it.useCustom) it.widthFraction else curWf.coerceIn(0.20f, 1.0f),
                                heightFraction = if (it.useCustom) it.heightFraction else curHf.coerceIn(0.20f, 1.0f),
                            )
                        }
                    }
                },
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.align(Alignment.Center).padding(6.dp),
            ) {
                Icon(Icons.Filled.OpenWith, contentDescription = null, tint = colors.green, modifier = Modifier.size(20.dp))
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.layout_screen_title_fmt,
                        activeSystem.label,
                        stringResource(if (isLandscape) R.string.layout_landscape else R.string.layout_portrait),
                    ),
                    color = Color.White,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                )
                Text(
                    text = "${viewportRect.width.roundToInt()}x${viewportRect.height.roundToInt()} px | ${currentScreenCfg.rotationDeg}°",
                    color = colors.green,
                    fontFamily = WatermelonMono,
                    fontSize = 10.sp,
                )
                Text(
                    text = stringResource(R.string.layout_drag_hint),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 9.5.sp,
                )
            }

            // Bottom-Right Resize Handle
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(36.dp)
                    .clip(RoundedCornerShape(topStart = 10.dp))
                    .background(colors.green)
                    .pointerInput(w, h, activeSystem, isLandscape, currentScreenCfg) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val curWf = if (currentScreenCfg.useCustom) currentScreenCfg.widthFraction else (viewportRect.width / w)
                            val curHf = if (currentScreenCfg.useCustom) currentScreenCfg.heightFraction else (viewportRect.height / h)
                            val nwf = (curWf + (dragAmount.x * 1.6f) / w).coerceIn(0.20f, 1.0f)
                            val nhf = (curHf + (dragAmount.y * 1.6f) / h).coerceIn(0.20f, 1.0f)
                            updateScreenCfg {
                                it.copy(
                                    useCustom = true,
                                    screenPreset = 4,
                                    widthFraction = nwf,
                                    heightFraction = nhf,
                                )
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("⤡", color = Color(0xFF05060A), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }
        }

        // Draggable Main Virtual Controls (per-orientation!)
        DraggableNode(
            label = stringResource(if (profile.useAnalogStick) R.string.layout_stick else R.string.layout_dpad),
            pos = currentControls.dpadPos,
            containerW = w,
            containerH = h,
            sizeDp = 96,
            accent = colors.green,
            onMove = { next -> updateControls { it.copy(dpadPos = next) } },
        )

        if (profile.separateABButtons) {
            DraggableNode(
                label = "A",
                pos = currentControls.btnAPos,
                containerW = w,
                containerH = h,
                sizeDp = 64,
                accent = colors.green,
                onMove = { next -> updateControls { it.copy(btnAPos = next) } },
            )
            DraggableNode(
                label = "B",
                pos = currentControls.btnBPos,
                containerW = w,
                containerH = h,
                sizeDp = 64,
                accent = colors.red,
                onMove = { next -> updateControls { it.copy(btnBPos = next) } },
            )
        } else {
            DraggableNode(
                label = "B / A",
                pos = currentControls.abPos,
                containerW = w,
                containerH = h,
                sizeDp = 98,
                accent = colors.red,
                onMove = { next -> updateControls { it.copy(abPos = next) } },
            )
        }

        // Separate draggable SELECT and START nodes!
        DraggableNode(
            label = "SELECT",
            pos = currentControls.selectPos,
            containerW = w,
            containerH = h,
            sizeDp = 68,
            accent = colors.green,
            onMove = { next -> updateControls { it.copy(selectPos = next) } },
        )
        DraggableNode(
            label = "START",
            pos = currentControls.startPos,
            containerW = w,
            containerH = h,
            sizeDp = 68,
            accent = colors.green,
            onMove = { next -> updateControls { it.copy(startPos = next) } },
        )

        if (profile.showShoulders || activeSystem == ConsoleSystem.GBA) {
            DraggableNode(
                label = "L",
                pos = currentControls.lPos,
                containerW = w,
                containerH = h,
                sizeDp = 78,
                accent = colors.green,
                onMove = { next -> updateControls { it.copy(lPos = next) } },
            )
            DraggableNode(
                label = "R",
                pos = currentControls.rPos,
                containerW = w,
                containerH = h,
                sizeDp = 78,
                accent = colors.green,
                onMove = { next -> updateControls { it.copy(rPos = next) } },
            )
        }

        // 5 SEPARATELY DRAGGABLE QUICK ACTION BUTTONS (STORM DS Parity!)
        if (profile.showQuickStrip && profile.showRewindBtn) {
            DraggableIconNode(
                icon = Icons.Filled.History,
                pos = currentControls.rewindBtnPos,
                containerW = w,
                containerH = h,
                accent = Color(0xFF94A3B8),
                onMove = { next -> updateControls { it.copy(rewindBtnPos = next) } },
            )
        }
        if (profile.showQuickStrip && profile.showFastForwardBtn) {
            DraggableIconNode(
                icon = Icons.Filled.FastForward,
                pos = currentControls.fastForwardBtnPos,
                containerW = w,
                containerH = h,
                accent = colors.green,
                onMove = { next -> updateControls { it.copy(fastForwardBtnPos = next) } },
            )
        }
        if (profile.showQuickStrip && profile.showQuickSaveBtn) {
            DraggableIconNode(
                icon = Icons.Filled.Save,
                pos = currentControls.quickSaveBtnPos,
                containerW = w,
                containerH = h,
                accent = Color(0xFF38BDF8),
                onMove = { next -> updateControls { it.copy(quickSaveBtnPos = next) } },
            )
        }
        if (profile.showQuickStrip && profile.showQuickLoadBtn) {
            DraggableIconNode(
                icon = Icons.Filled.Upload,
                pos = currentControls.quickLoadBtnPos,
                containerW = w,
                containerH = h,
                accent = Color(0xFFA855F7),
                onMove = { next -> updateControls { it.copy(quickLoadBtnPos = next) } },
            )
        }
        if (profile.showMenuBtn || !profile.showQuickStrip) {
            DraggableIconNode(
                icon = Icons.Filled.Menu,
                pos = currentControls.menuBtnPos,
                containerW = w,
                containerH = h,
                accent = Color.White,
                onMove = { next -> updateControls { it.copy(menuBtnPos = next) } },
            )
        }

        // Floating Top Bar + Collapsible Precision Screen & Quick Buttons Panel
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp, start = 8.dp, end = 8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surface.copy(alpha = 0.95f))
                    .border(1.dp, colors.line, RoundedCornerShape(14.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                ConsoleSystem.entries.forEach { sys ->
                    val sel = sys == activeSystem
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (sel) colors.green else colors.surface2)
                            .clickable {
                                GbcaPrefs.updateOverlayProfile(activeSystem) { profile }
                                activeSystem = sys
                            }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                    ) {
                        Text(sys.label, color = if (sel) Color(0xFF05060A) else colors.text, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surface2)
                        .clickable { panelExpanded = !panelExpanded }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.layout_screen_btn), color = colors.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Icon(
                            if (panelExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            tint = colors.green,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surface2)
                        .clickable {
                            val def = GbcaPrefs.defaultOverlayFor(activeSystem)
                            profile = def
                            GbcaPrefs.updateOverlayProfile(activeSystem) { def }
                        }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                ) {
                    Text(stringResource(R.string.reset), color = colors.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.green)
                        .clickable {
                            GbcaPrefs.updateOverlayProfile(activeSystem) { profile }
                            onDone()
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(stringResource(R.string.layout_done), color = Color(0xFF05060A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (panelExpanded) {
                Spacer(Modifier.height(6.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surface.copy(alpha = 0.96f))
                        .border(1.dp, colors.line, RoundedCornerShape(16.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(
                                R.string.layout_geometry_fmt,
                                activeSystem.label,
                                stringResource(if (isLandscape) R.string.layout_landscape else R.string.layout_portrait),
                            ),
                            color = colors.text,
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.surface2)
                                .clickable { updateScreenCfg { it.copy(rotationDeg = (it.rotationDeg + 90) % 360) } }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = null, tint = colors.green, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("${currentScreenCfg.rotationDeg}°", color = colors.text, fontFamily = WatermelonMono, fontSize = 10.sp)
                            }
                        }
                    }

                    // 1-Tap Screen Mode Presets
                    val presets = listOf(
                        2 to R.string.screen_preset_fullscreen,
                        1 to R.string.screen_preset_max_fit,
                        0 to R.string.screen_preset_skin,
                        3 to R.string.screen_preset_integer,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        presets.forEach { (pid, labelRes) ->
                            val sel = !currentScreenCfg.useCustom && currentScreenCfg.screenPreset == pid
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (sel) colors.green else colors.surface2)
                                    .border(1.dp, if (sel) colors.green else colors.line, RoundedCornerShape(8.dp))
                                    .clickable {
                                        updateScreenCfg {
                                            it.copy(
                                                useCustom = false,
                                                screenPreset = pid,
                                                aspectMode = if (pid == 2) 2 else if (pid == 3) 1 else 0,
                                                widthFraction = if (pid == 2 || pid == 1) 1.0f else it.widthFraction,
                                                heightFraction = if (pid == 2 || pid == 1) 1.0f else it.heightFraction,
                                                centerX = 0.50f,
                                                centerY = if (pid == 2 || pid == 1) 0.50f else 0.28f,
                                            )
                                        }
                                    }
                                    .padding(vertical = 6.dp, horizontal = 3.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (pid == 2) {
                                        Icon(Icons.Filled.Fullscreen, contentDescription = null, tint = if (sel) Color(0xFF05060A) else colors.green, modifier = Modifier.size(12.dp))
                                        Spacer(Modifier.width(2.dp))
                                    }
                                    Text(stringResource(labelRes), color = if (sel) Color(0xFF05060A) else colors.text, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                            }
                        }
                    }

                    // Aspect Mode Row
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(0 to R.string.vid_aspect_orig, 1 to R.string.vid_aspect_int, 2 to R.string.vid_aspect_full, 3 to R.string.layout_aspect_free).forEach { (m, labelRes) ->
                            val sel = currentScreenCfg.aspectMode == m
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (sel) colors.green else colors.surface2)
                                    .clickable { updateScreenCfg { it.copy(useCustom = true, screenPreset = 4, aspectMode = m) } }
                                    .padding(vertical = 5.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(stringResource(labelRes), color = if (sel) Color(0xFF05060A) else colors.text, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Position Snaps
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(
                            0.24f to R.string.screen_snap_top,
                            0.50f to R.string.screen_snap_middle,
                            0.74f to R.string.screen_snap_bottom,
                        ).forEach { (yVal, lblRes) ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.surface2)
                                    .clickable { updateScreenCfg { it.copy(useCustom = true, screenPreset = 4, centerY = yVal) } }
                                    .padding(vertical = 5.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(stringResource(lblRes), color = colors.text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .weight(1.1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.surface2)
                                .clickable { updateScreenCfg { it.copy(useCustom = true, screenPreset = 4, centerX = 0.50f) } }
                                .padding(vertical = 5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CenterFocusStrong, contentDescription = null, tint = colors.green, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("X=50%", color = colors.text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    ScreenSliderRow(stringResource(R.string.layout_width), "${(currentScreenCfg.widthFraction * 100).roundToInt()}%", currentScreenCfg.widthFraction, 0.20f..1.0f) { v ->
                        updateScreenCfg { it.copy(useCustom = true, screenPreset = 4, widthFraction = v) }
                    }
                    ScreenSliderRow(stringResource(R.string.layout_height), "${(currentScreenCfg.heightFraction * 100).roundToInt()}%", currentScreenCfg.heightFraction, 0.20f..1.0f) { v ->
                        updateScreenCfg { it.copy(useCustom = true, screenPreset = 4, heightFraction = v) }
                    }
                    ScreenSliderRow(stringResource(R.string.layout_pos_x), "${(currentScreenCfg.centerX * 100).roundToInt()}%", currentScreenCfg.centerX, 0.05f..0.95f) { v ->
                        updateScreenCfg { it.copy(useCustom = true, screenPreset = 4, centerX = v) }
                    }
                    ScreenSliderRow(stringResource(R.string.layout_pos_y), "${(currentScreenCfg.centerY * 100).roundToInt()}%", currentScreenCfg.centerY, 0.05f..0.95f) { v ->
                        updateScreenCfg { it.copy(useCustom = true, screenPreset = 4, centerY = v) }
                    }

                    // Virtual Analog Stick vs D-Pad Toggle
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(stringResource(R.string.overlay_use_analog_stick), color = colors.text, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.overlay_use_analog_stick_sub), color = colors.text2, fontSize = 9.5.sp)
                        }
                        WatermelonSwitch(
                            checked = profile.useAnalogStick,
                            onCheckedChange = { c -> profile = profile.copy(useAnalogStick = c) },
                        )
                    }

                    // Separate A/B Toggle & Spacing
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.overlay_separate_ab), color = colors.text, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        WatermelonSwitch(
                            checked = profile.separateABButtons,
                            onCheckedChange = { c -> profile = profile.copy(separateABButtons = c) },
                        )
                    }
                    if (!profile.separateABButtons) {
                        ScreenSliderRow(stringResource(R.string.overlay_ab_spacing), "${profile.abSpacingPercent}%", profile.abSpacingPercent.toFloat(), 100f..180f) { v ->
                            profile = profile.copy(abSpacingPercent = v.roundToInt().coerceIn(100, 180))
                        }
                    }

                    // Individual Quick Action Buttons visibility toggles
                    Text(stringResource(R.string.quick_buttons_section), color = colors.text2, fontFamily = WatermelonMono, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
                        QuickToggleChip(stringResource(R.string.qbtn_rewind), profile.showRewindBtn, Modifier.weight(1f)) {
                            profile = profile.copy(showRewindBtn = !profile.showRewindBtn)
                        }
                        QuickToggleChip(stringResource(R.string.qbtn_ff), profile.showFastForwardBtn, Modifier.weight(1f)) {
                            profile = profile.copy(showFastForwardBtn = !profile.showFastForwardBtn)
                        }
                        QuickToggleChip(stringResource(R.string.qbtn_menu), profile.showMenuBtn, Modifier.weight(1f)) {
                            profile = profile.copy(showMenuBtn = !profile.showMenuBtn)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
                        QuickToggleChip(stringResource(R.string.qbtn_qsave), profile.showQuickSaveBtn, Modifier.weight(1f)) {
                            profile = profile.copy(showQuickSaveBtn = !profile.showQuickSaveBtn)
                        }
                        QuickToggleChip(stringResource(R.string.qbtn_qload), profile.showQuickLoadBtn, Modifier.weight(1f)) {
                            profile = profile.copy(showQuickLoadBtn = !profile.showQuickLoadBtn)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.screen_hide_skin_fs), color = colors.text, fontSize = 11.5.sp, modifier = Modifier.weight(1f))
                        WatermelonSwitch(
                            checked = currentScreenCfg.hideSkinInFullscreen,
                            onCheckedChange = { c -> updateScreenCfg { it.copy(hideSkinInFullscreen = c) } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickToggleChip(label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = watermelon
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) colors.green else colors.surface2)
            .border(1.dp, if (active) colors.green else colors.line, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (active) Color(0xFF05060A) else colors.text, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun ScreenSliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    val colors = watermelon
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = colors.text2, fontSize = 11.sp)
            Text(valueText, color = colors.green, fontFamily = WatermelonMono, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = colors.green, activeTrackColor = colors.green),
            modifier = Modifier.height(26.dp),
        )
    }
}

@Composable
private fun DraggableNode(
    label: String,
    pos: CustomControlPos,
    containerW: Float,
    containerH: Float,
    sizeDp: Int,
    accent: Color,
    onMove: (CustomControlPos) -> Unit,
) {
    val density = LocalDensity.current
    val sizePx = with(density) { sizeDp.dp.toPx() }
    val xPx = pos.xFraction * containerW
    val yPx = pos.yFraction * containerH

    Box(
        modifier = Modifier
            .offset { IntOffset((xPx - sizePx / 2f).roundToInt(), (yPx - sizePx / 2f).roundToInt()) }
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.28f))
            .border(2.dp, accent, CircleShape)
            .pointerInput(containerW, containerH, pos) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val nx = ((pos.xFraction * containerW + dragAmount.x) / containerW).coerceIn(0.03f, 0.97f)
                    val ny = ((pos.yFraction * containerH + dragAmount.y) / containerH).coerceIn(0.05f, 0.95f)
                    onMove(pos.copy(xFraction = nx, yFraction = ny))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 10.5.sp,
        )
    }
}

@Composable
private fun DraggableIconNode(
    icon: ImageVector,
    pos: CustomControlPos,
    containerW: Float,
    containerH: Float,
    accent: Color,
    onMove: (CustomControlPos) -> Unit,
) {
    val density = LocalDensity.current
    val sizeDp = 44
    val sizePx = with(density) { sizeDp.dp.toPx() }
    val xPx = pos.xFraction * containerW
    val yPx = pos.yFraction * containerH

    Box(
        modifier = Modifier
            .offset { IntOffset((xPx - sizePx / 2f).roundToInt(), (yPx - sizePx / 2f).roundToInt()) }
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(Color(0xCC0D1220))
            .border(1.8.dp, accent, CircleShape)
            .pointerInput(containerW, containerH, pos) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val nx = ((pos.xFraction * containerW + dragAmount.x) / containerW).coerceIn(0.03f, 0.97f)
                    val ny = ((pos.yFraction * containerH + dragAmount.y) / containerH).coerceIn(0.05f, 0.95f)
                    onMove(pos.copy(xFraction = nx, yFraction = ny))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
    }
}
