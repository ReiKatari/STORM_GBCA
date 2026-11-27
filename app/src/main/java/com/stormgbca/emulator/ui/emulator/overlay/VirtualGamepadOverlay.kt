package com.stormgbca.emulator.ui.emulator.overlay

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Upload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormgbca.emulator.model.ConsoleSystem
import com.stormgbca.emulator.model.OverlayLayoutPreset
import com.stormgbca.emulator.model.SystemOverlayProfile
import com.stormgbca.emulator.ui.theme.SpaceGrotesk
import com.stormgbca.emulator.ui.theme.WatermelonMono
import ru.stormgames.gbca.NativeCore
import kotlin.math.roundToInt

private fun resolveVibrator(context: Context): Vibrator? = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
}.getOrNull()

private fun triggerHapticTick(vibrator: Vibrator?, enabled: Boolean) {
    if (!enabled || vibrator == null) return
    runCatching {
        if (vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(14L, 90))
        }
    }
}

data class ResolvedControlRects(
    val dpadCenter: Offset,
    val dpadRadius: Float,
    val btnACenter: Offset,
    val btnBCenter: Offset,
    val btnRadius: Float,
    val selectCenter: Offset,
    val startCenter: Offset,
    val pillW: Float,
    val pillH: Float,
    val shoulderLCenter: Offset,
    val shoulderRCenter: Offset,
    val shoulderW: Float,
    val shoulderH: Float,
    val menuCenter: Offset,
    val rewindCenter: Offset,
    val fastForwardCenter: Offset,
    val quickSaveCenter: Offset,
    val quickLoadCenter: Offset,
    val quickBtnRadius: Float,
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun VirtualGamepadOverlay(
    system: ConsoleSystem,
    profile: SystemOverlayProfile,
    fastForwardActive: Boolean,
    onKeyMaskChanged: (Int) -> Unit,
    onQuickSave: () -> Unit,
    onQuickLoad: () -> Unit,
    onToggleFastForward: () -> Unit,
    onRewindTap: () -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val vibrator = remember(context) { resolveVibrator(context) }
    val style = remember(profile.buttonStyle) { ButtonThemeManager.resolve(profile.buttonStyle) }
    val alpha = (profile.opacityPercent.coerceIn(20, 100) / 100f)
    val baseScale = (profile.scalePercent.coerceIn(70, 140) / 100f)
    val preset = OverlayLayoutPreset.fromKey(profile.layoutPreset)
    val showShoulders = profile.showShoulders || system == ConsoleSystem.GBA

    var activeTouchMask by remember { mutableIntStateOf(0) }
    val touchMaskProvider = remember { { activeTouchMask } }
    val pointerButtons = remember { mutableMapOf<Int, Int>() }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val h = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val isLandscape = w > h
        val activeControls = if (isLandscape) profile.landscapeControls else profile.portraitControls

        val rects = remember(w, h, isLandscape, preset, profile, baseScale, activeControls) {
            val minDim = minOf(w, h)
            val dpadR = (minDim * 0.155f * baseScale).coerceIn(84f, 180f)
            val abR = (minDim * 0.080f * baseScale).coerceIn(46f, 95f)
            val pillW = (minDim * 0.125f * baseScale).coerceIn(76f, 142f)
            val pillH = pillW * 0.36f
            val shW = (minDim * 0.175f * baseScale).coerceIn(106f, 200f)
            val shH = shW * 0.39f
            val qBtnR = (minDim * 0.048f * baseScale).coerceIn(34f, 60f)

            val dpadC: Offset
            val abBase: Offset
            val selectC: Offset
            val startC: Offset
            val lC: Offset
            val rC: Offset

            when (preset) {
                OverlayLayoutPreset.CUSTOM -> {
                    dpadC = Offset(w * activeControls.dpadPos.xFraction, h * activeControls.dpadPos.yFraction)
                    abBase = Offset(w * activeControls.abPos.xFraction, h * activeControls.abPos.yFraction)
                    selectC = Offset(w * activeControls.selectPos.xFraction, h * activeControls.selectPos.yFraction)
                    startC = Offset(w * activeControls.startPos.xFraction, h * activeControls.startPos.yFraction)
                    lC = Offset(w * activeControls.lPos.xFraction, h * activeControls.lPos.yFraction)
                    rC = Offset(w * activeControls.rPos.xFraction, h * activeControls.rPos.yFraction)
                }
                OverlayLayoutPreset.WIDE_GRIP -> {
                    dpadC = Offset(w * (if (isLandscape) 0.095f else 0.170f), h * (if (isLandscape) 0.690f else 0.810f))
                    abBase = Offset(w * (if (isLandscape) 0.905f else 0.830f), h * (if (isLandscape) 0.690f else 0.810f))
                    selectC = Offset(w * (if (isLandscape) 0.095f else 0.35f), h * (if (isLandscape) 0.905f else 0.925f))
                    startC = Offset(w * (if (isLandscape) 0.865f else 0.65f), h * (if (isLandscape) 0.905f else 0.925f))
                    lC = Offset(w * (if (isLandscape) 0.085f else 0.115f), h * (if (isLandscape) 0.210f else 0.580f))
                    rC = Offset(w * (if (isLandscape) 0.915f else 0.885f), h * (if (isLandscape) 0.210f else 0.580f))
                }
                OverlayLayoutPreset.COMPACT_MINIMAL -> {
                    dpadC = Offset(w * (if (isLandscape) 0.100f else 0.175f), h * (if (isLandscape) 0.745f else 0.825f))
                    abBase = Offset(w * (if (isLandscape) 0.900f else 0.825f), h * (if (isLandscape) 0.745f else 0.825f))
                    selectC = Offset(w * (if (isLandscape) 0.100f else 0.36f), h * (if (isLandscape) 0.92f else 0.935f))
                    startC = Offset(w * (if (isLandscape) 0.865f else 0.64f), h * (if (isLandscape) 0.92f else 0.935f))
                    lC = Offset(w * (if (isLandscape) 0.088f else 0.120f), h * (if (isLandscape) 0.220f else 0.595f))
                    rC = Offset(w * (if (isLandscape) 0.912f else 0.880f), h * (if (isLandscape) 0.220f else 0.595f))
                }
                OverlayLayoutPreset.FLOATING_GLASS -> {
                    dpadC = Offset(w * (if (isLandscape) 0.105f else 0.180f), h * (if (isLandscape) 0.705f else 0.805f))
                    abBase = Offset(w * (if (isLandscape) 0.895f else 0.820f), h * (if (isLandscape) 0.705f else 0.805f))
                    selectC = Offset(w * (if (isLandscape) 0.105f else 0.36f), h * (if (isLandscape) 0.905f else 0.920f))
                    startC = Offset(w * (if (isLandscape) 0.865f else 0.64f), h * (if (isLandscape) 0.905f else 0.920f))
                    lC = Offset(w * (if (isLandscape) 0.090f else 0.120f), h * (if (isLandscape) 0.215f else 0.585f))
                    rC = Offset(w * (if (isLandscape) 0.910f else 0.880f), h * (if (isLandscape) 0.215f else 0.585f))
                }
                OverlayLayoutPreset.CLASSIC_HANDHELD -> {
                    // Shifted D-Pad slightly left, B/A slightly right, SELECT & START completely off screen!
                    dpadC = Offset(w * activeControls.dpadPos.xFraction, h * activeControls.dpadPos.yFraction)
                    abBase = Offset(w * activeControls.abPos.xFraction, h * activeControls.abPos.yFraction)
                    selectC = Offset(w * activeControls.selectPos.xFraction, h * activeControls.selectPos.yFraction)
                    startC = Offset(w * activeControls.startPos.xFraction, h * activeControls.startPos.yFraction)
                    lC = Offset(w * activeControls.lPos.xFraction, h * activeControls.lPos.yFraction)
                    rC = Offset(w * activeControls.rPos.xFraction, h * activeControls.rPos.yFraction)
                }
            }

            // Separate independent A & B positioning OR guaranteed non-overlapping diagonal cluster!
            val btnA: Offset
            val btnB: Offset
            if (profile.separateABButtons) {
                btnA = Offset(
                    (w * activeControls.btnAPos.xFraction).coerceIn(abR + 8f, (w - abR - 8f).coerceAtLeast(abR + 8f)),
                    (h * activeControls.btnAPos.yFraction).coerceIn(abR + 8f, (h - abR - 8f).coerceAtLeast(abR + 8f)),
                )
                btnB = Offset(
                    (w * activeControls.btnBPos.xFraction).coerceIn(abR + 8f, (w - abR - 8f).coerceAtLeast(abR + 8f)),
                    (h * activeControls.btnBPos.yFraction).coerceIn(abR + 8f, (h - abR - 8f).coerceAtLeast(abR + 8f)),
                )
            } else {
                val spacingFactor = (profile.abSpacingPercent.coerceIn(100, 180) / 100f)
                val halfDist = abR * 1.12f * spacingFactor
                val dx = halfDist * 0.86f
                val dy = halfDist * 0.51f
                val maxCenterX = (w - abR - 10f - dx).coerceAtLeast(abR + dx + 10f)
                val clampedBaseX = abBase.x.coerceIn(abR + dx + 10f, maxCenterX)
                btnB = Offset(clampedBaseX - dx, (abBase.y + dy).coerceIn(abR + 8f, h - abR - 8f))
                btnA = Offset(clampedBaseX + dx, (abBase.y - dy).coerceIn(abR + 8f, h - abR - 8f))
            }

            fun clampQuick(xf: Float, yf: Float): Offset = Offset(
                (w * xf).coerceIn(qBtnR + 6f, (w - qBtnR - 6f).coerceAtLeast(qBtnR + 6f)),
                (h * yf).coerceIn(qBtnR + 6f, (h - qBtnR - 6f).coerceAtLeast(qBtnR + 6f)),
            )

            ResolvedControlRects(
                dpadCenter = Offset(
                    dpadC.x.coerceIn(dpadR + 8f, (w - dpadR - 8f).coerceAtLeast(dpadR + 8f)),
                    dpadC.y.coerceIn(dpadR + 8f, (h - dpadR - 8f).coerceAtLeast(dpadR + 8f)),
                ),
                dpadRadius = dpadR,
                btnACenter = btnA,
                btnBCenter = btnB,
                btnRadius = abR,
                selectCenter = Offset(
                    selectC.x.coerceIn(pillW * 0.55f + 6f, (w - pillW * 0.55f - 6f).coerceAtLeast(pillW * 0.55f + 6f)),
                    selectC.y.coerceIn(pillH + 6f, (h - pillH - 6f).coerceAtLeast(pillH + 6f)),
                ),
                startCenter = Offset(
                    startC.x.coerceIn(pillW * 0.55f + 6f, (w - pillW * 0.55f - 6f).coerceAtLeast(pillW * 0.55f + 6f)),
                    startC.y.coerceIn(pillH + 6f, (h - pillH - 6f).coerceAtLeast(pillH + 6f)),
                ),
                pillW = pillW,
                pillH = pillH,
                shoulderLCenter = Offset(
                    lC.x.coerceIn(shW * 0.5f + 8f, (w - shW * 0.5f - 8f).coerceAtLeast(shW * 0.5f + 8f)),
                    lC.y.coerceIn(shH * 0.5f + 8f, (h - shH * 0.5f - 8f).coerceAtLeast(shH * 0.5f + 8f)),
                ),
                shoulderRCenter = Offset(
                    rC.x.coerceIn(shW * 0.5f + 8f, (w - shW * 0.5f - 8f).coerceAtLeast(shW * 0.5f + 8f)),
                    rC.y.coerceIn(shH * 0.5f + 8f, (h - shH * 0.5f - 8f).coerceAtLeast(shH * 0.5f + 8f)),
                ),
                shoulderW = shW,
                shoulderH = shH,
                menuCenter = clampQuick(activeControls.menuBtnPos.xFraction, activeControls.menuBtnPos.yFraction),
                rewindCenter = clampQuick(activeControls.rewindBtnPos.xFraction, activeControls.rewindBtnPos.yFraction),
                fastForwardCenter = clampQuick(activeControls.fastForwardBtnPos.xFraction, activeControls.fastForwardBtnPos.yFraction),
                quickSaveCenter = clampQuick(activeControls.quickSaveBtnPos.xFraction, activeControls.quickSaveBtnPos.yFraction),
                quickLoadCenter = clampQuick(activeControls.quickLoadBtnPos.xFraction, activeControls.quickLoadBtnPos.yFraction),
                quickBtnRadius = qBtnR,
            )
        }

        fun resolveMaskAt(pt: Offset): Int {
            val dx = pt.x - rects.dpadCenter.x
            val dy = pt.y - rects.dpadCenter.y
            val distSq = dx * dx + dy * dy
            if (distSq <= (rects.dpadRadius * 1.25f) * (rects.dpadRadius * 1.25f)) {
                var mask = 0
                val dead = rects.dpadRadius * 0.22f
                if (dx < -dead) mask = mask or NativeCore.KEY_LEFT
                if (dx > dead) mask = mask or NativeCore.KEY_RIGHT
                if (dy < -dead) mask = mask or NativeCore.KEY_UP
                if (dy > dead) mask = mask or NativeCore.KEY_DOWN
                return mask
            }

            fun distSqTo(c: Offset): Float {
                val cx = pt.x - c.x
                val cy = pt.y - c.y
                return cx * cx + cy * cy
            }
            val hitR2 = (rects.btnRadius * 1.16f) * (rects.btnRadius * 1.16f)
            val dA = distSqTo(rects.btnACenter)
            val dB = distSqTo(rects.btnBCenter)
            if (dA <= hitR2 && dB <= hitR2) {
                return if (dA <= dB) NativeCore.KEY_A else NativeCore.KEY_B
            }
            if (dA <= hitR2) return NativeCore.KEY_A
            if (dB <= hitR2) return NativeCore.KEY_B

            fun insideRect(c: Offset, rw: Float, rh: Float): Boolean {
                return kotlin.math.abs(pt.x - c.x) <= rw * 0.62f && kotlin.math.abs(pt.y - c.y) <= rh * 0.85f
            }
            if (insideRect(rects.selectCenter, rects.pillW, rects.pillH)) return NativeCore.KEY_SELECT
            if (insideRect(rects.startCenter, rects.pillW, rects.pillH)) return NativeCore.KEY_START
            if (showShoulders && insideRect(rects.shoulderLCenter, rects.shoulderW, rects.shoulderH)) return NativeCore.KEY_L
            if (showShoulders && insideRect(rects.shoulderRCenter, rects.shoulderW, rects.shoulderH)) return NativeCore.KEY_R
            return 0
        }

        // Multi-touch surface capturing all physical-style virtual buttons
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(alpha)
                .pointerInteropFilter { ev ->
                    val action = ev.actionMasked
                    val idx = ev.actionIndex
                    var changed = false
                    when (action) {
                        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                            val pid = ev.getPointerId(idx)
                            val mask = resolveMaskAt(Offset(ev.getX(idx), ev.getY(idx)))
                            if (mask != 0) {
                                pointerButtons[pid] = mask
                                triggerHapticTick(vibrator, profile.hapticsEnabled)
                                changed = true
                            }
                        }
                        MotionEvent.ACTION_MOVE -> {
                            for (i in 0 until ev.pointerCount) {
                                val pid = ev.getPointerId(i)
                                val mask = resolveMaskAt(Offset(ev.getX(i), ev.getY(i)))
                                val prev = pointerButtons[pid] ?: 0
                                if (mask != prev) {
                                    if (mask == 0) pointerButtons.remove(pid) else pointerButtons[pid] = mask
                                    changed = true
                                }
                            }
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                            val pid = ev.getPointerId(idx)
                            if (pointerButtons.remove(pid) != null) changed = true
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            if (pointerButtons.isNotEmpty()) {
                                pointerButtons.clear()
                                changed = true
                            }
                        }
                    }
                    if (changed) {
                        var combined = 0
                        for (m in pointerButtons.values) combined = combined or m
                        activeTouchMask = combined
                        onKeyMaskChanged(combined)
                    }
                    pointerButtons.isNotEmpty()
                },
        ) {
            val dpadDraw = remember(style, alpha, rects) {
                val c = rects.dpadCenter
                val r = rects.dpadRadius
                val arm = r * 0.54f
                val len = r * 1.66f
                object {
                    val center = c
                    val radius = r
                    val bgColor = style.dpadBgBottom.copy(alpha = 0.42f)
                    val borderColor = style.dpadBorder.copy(alpha = 0.55f)
                    val borderStroke = Stroke(width = 2.5f)
                    val vTopLeft = Offset(c.x - arm / 2f, c.y - len / 2f)
                    val vSize = Size(arm, len)
                    val hTopLeft = Offset(c.x - len / 2f, c.y - arm / 2f)
                    val hSize = Size(len, arm)
                    val vBrush = Brush.verticalGradient(
                        listOf(style.dpadBgTop, style.dpadBgBottom),
                        startY = vTopLeft.y,
                        endY = vTopLeft.y + len,
                    )
                    val hBrush = Brush.verticalGradient(
                        listOf(style.dpadBgTop, style.dpadBgBottom),
                        startY = hTopLeft.y,
                        endY = hTopLeft.y + arm,
                    )
                    val corner = CornerRadius(12f, 12f)
                    val centerDotColor = Color.Black.copy(alpha = 0.28f)
                    val centerDotRadius = arm * 0.28f
                    val pressGlowColor = style.glow.copy(alpha = 0.32f)
                    val dirR = arm * 0.24f
                    val upOffset = Offset(c.x, c.y - len * 0.34f)
                    val downOffset = Offset(c.x, c.y + len * 0.34f)
                    val leftOffset = Offset(c.x - len * 0.34f, c.y)
                    val rightOffset = Offset(c.x + len * 0.34f, c.y)
                }
            }

            // D-Pad visual (reads touchMaskProvider strictly inside DrawScope, zero recomposition)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val mask = touchMaskProvider()
                drawCircle(
                    color = dpadDraw.bgColor,
                    radius = dpadDraw.radius,
                    center = dpadDraw.center,
                )
                drawCircle(
                    color = dpadDraw.borderColor,
                    radius = dpadDraw.radius,
                    center = dpadDraw.center,
                    style = dpadDraw.borderStroke,
                )
                drawRoundRect(
                    brush = dpadDraw.vBrush,
                    topLeft = dpadDraw.vTopLeft,
                    size = dpadDraw.vSize,
                    cornerRadius = dpadDraw.corner,
                )
                drawRoundRect(
                    brush = dpadDraw.hBrush,
                    topLeft = dpadDraw.hTopLeft,
                    size = dpadDraw.hSize,
                    cornerRadius = dpadDraw.corner,
                )
                drawCircle(
                    color = dpadDraw.centerDotColor,
                    radius = dpadDraw.centerDotRadius,
                    center = dpadDraw.center,
                )
                if ((mask and NativeCore.KEY_UP) != 0) drawCircle(dpadDraw.pressGlowColor, dpadDraw.dirR, dpadDraw.upOffset)
                if ((mask and NativeCore.KEY_DOWN) != 0) drawCircle(dpadDraw.pressGlowColor, dpadDraw.dirR, dpadDraw.downOffset)
                if ((mask and NativeCore.KEY_LEFT) != 0) drawCircle(dpadDraw.pressGlowColor, dpadDraw.dirR, dpadDraw.leftOffset)
                if ((mask and NativeCore.KEY_RIGHT) != 0) drawCircle(dpadDraw.pressGlowColor, dpadDraw.dirR, dpadDraw.rightOffset)
            }

            ConcaveCircleButton(
                label = "B",
                center = rects.btnBCenter,
                radius = rects.btnRadius,
                topColor = style.btnBTop,
                bottomColor = style.btnBBottom,
                borderGlow = style.glow,
                textColor = style.labelColor,
                keyBit = NativeCore.KEY_B,
                touchMaskProvider = touchMaskProvider,
            )
            ConcaveCircleButton(
                label = "A",
                center = rects.btnACenter,
                radius = rects.btnRadius,
                topColor = style.btnATop,
                bottomColor = style.btnABottom,
                borderGlow = style.glow,
                textColor = style.labelColor,
                keyBit = NativeCore.KEY_A,
                touchMaskProvider = touchMaskProvider,
            )

            PillButton(
                label = "SELECT",
                center = rects.selectCenter,
                widthPx = rects.pillW,
                heightPx = rects.pillH,
                bgColor = style.pillBg,
                borderColor = style.dpadBorder,
                textColor = style.labelColor,
                keyBit = NativeCore.KEY_SELECT,
                touchMaskProvider = touchMaskProvider,
            )
            PillButton(
                label = "START",
                center = rects.startCenter,
                widthPx = rects.pillW,
                heightPx = rects.pillH,
                bgColor = style.pillBg,
                borderColor = style.dpadBorder,
                textColor = style.labelColor,
                keyBit = NativeCore.KEY_START,
                touchMaskProvider = touchMaskProvider,
            )

            if (showShoulders) {
                ShoulderButton(
                    label = "L",
                    center = rects.shoulderLCenter,
                    widthPx = rects.shoulderW,
                    heightPx = rects.shoulderH,
                    topColor = style.shoulderTop,
                    bottomColor = style.shoulderBottom,
                    borderGlow = style.glow,
                    textColor = style.labelColor,
                    keyBit = NativeCore.KEY_L,
                    touchMaskProvider = touchMaskProvider,
                )
                ShoulderButton(
                    label = "R",
                    center = rects.shoulderRCenter,
                    widthPx = rects.shoulderW,
                    heightPx = rects.shoulderH,
                    topColor = style.shoulderTop,
                    bottomColor = style.shoulderBottom,
                    borderGlow = style.glow,
                    textColor = style.labelColor,
                    keyBit = NativeCore.KEY_R,
                    touchMaskProvider = touchMaskProvider,
                )
            }
        }

        // 5 SEPARATE INDEPENDENTLY POSITIONABLE QUICK ACTION BUTTONS (STORM DS Parity!)
        val showAnyQuick = profile.showQuickStrip
        if (showAnyQuick && profile.showRewindBtn) {
            FloatingQuickActionButton(
                icon = Icons.Filled.History,
                center = rects.rewindCenter,
                radiusPx = rects.quickBtnRadius,
                accent = Color(0xFF94A3B8),
                borderGlow = style.glow,
                active = false,
                alpha = alpha,
                onClick = {
                    triggerHapticTick(vibrator, profile.hapticsEnabled)
                    onRewindTap()
                },
            )
        }
        if (showAnyQuick && profile.showFastForwardBtn) {
            FloatingQuickActionButton(
                icon = Icons.Filled.FastForward,
                center = rects.fastForwardCenter,
                radiusPx = rects.quickBtnRadius,
                accent = style.glow,
                borderGlow = style.glow,
                active = fastForwardActive,
                alpha = alpha,
                onClick = {
                    triggerHapticTick(vibrator, profile.hapticsEnabled)
                    onToggleFastForward()
                },
            )
        }
        if (showAnyQuick && profile.showQuickSaveBtn) {
            FloatingQuickActionButton(
                icon = Icons.Filled.Save,
                center = rects.quickSaveCenter,
                radiusPx = rects.quickBtnRadius,
                accent = Color(0xFF38BDF8),
                borderGlow = style.glow,
                active = false,
                alpha = alpha,
                onClick = {
                    triggerHapticTick(vibrator, profile.hapticsEnabled)
                    onQuickSave()
                },
            )
        }
        if (showAnyQuick && profile.showQuickLoadBtn) {
            FloatingQuickActionButton(
                icon = Icons.Filled.Upload,
                center = rects.quickLoadCenter,
                radiusPx = rects.quickBtnRadius,
                accent = Color(0xFFA855F7),
                borderGlow = style.glow,
                active = false,
                alpha = alpha,
                onClick = {
                    triggerHapticTick(vibrator, profile.hapticsEnabled)
                    onQuickLoad()
                },
            )
        }
        if (profile.showMenuBtn || !showAnyQuick) {
            FloatingQuickActionButton(
                icon = Icons.Filled.Menu,
                center = rects.menuCenter,
                radiusPx = rects.quickBtnRadius,
                accent = Color.White,
                borderGlow = style.glow,
                active = false,
                alpha = alpha.coerceAtLeast(0.55f),
                onClick = {
                    triggerHapticTick(vibrator, profile.hapticsEnabled)
                    onOpenMenu()
                },
            )
        }
    }
}

@Composable
private fun FloatingQuickActionButton(
    icon: ImageVector,
    center: Offset,
    radiusPx: Float,
    accent: Color,
    borderGlow: Color,
    active: Boolean,
    alpha: Float,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    val diameterDp = with(density) { (radiusPx * 2f).toDp() }
    val iconDp = with(density) { (radiusPx * 1.05f).toDp() }
    val bgBrush = remember(active, accent) {
        Brush.verticalGradient(
            if (active) {
                listOf(accent.copy(alpha = 0.42f), Color(0xDD0A0E1A))
            } else {
                listOf(Color(0xCC161C2E), Color(0xCC090C14))
            }
        )
    }
    Box(
        modifier = Modifier
            .offset { IntOffset((center.x - radiusPx).roundToInt(), (center.y - radiusPx).roundToInt()) }
            .size(diameterDp)
            .alpha(if (active) 1f else alpha.coerceIn(0.45f, 0.96f))
            .shadow(if (active) 10.dp else 4.dp, CircleShape, spotColor = if (active) accent else borderGlow)
            .clip(CircleShape)
            .background(bgBrush)
            .border(
                width = if (active) 1.8.dp else 1.1.dp,
                color = if (active) accent else borderGlow.copy(alpha = 0.55f),
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) accent else Color.White,
            modifier = Modifier.size(iconDp),
        )
    }
}

@Composable
private fun ConcaveCircleButton(
    label: String,
    center: Offset,
    radius: Float,
    topColor: Color,
    bottomColor: Color,
    borderGlow: Color,
    textColor: Color,
    keyBit: Int,
    touchMaskProvider: () -> Int,
) {
    val density = LocalDensity.current
    val diameterDp = with(density) { (radius * 2f).toDp() }
    val drawResources = remember(topColor, bottomColor, borderGlow, radius, density) {
        val normalStrokePx = with(density) { 1.6.dp.toPx() }
        val pressedStrokePx = with(density) { 2.5.dp.toPx() }
        object {
            val glowBrush = Brush.radialGradient(
                colors = listOf(borderGlow.copy(alpha = 0.24f), Color.Transparent),
                center = Offset(radius, radius),
                radius = radius * 1.16f,
            )
            val normalBrush = Brush.verticalGradient(listOf(topColor, bottomColor), startY = 0f, endY = radius * 2f)
            val pressedBrush = Brush.verticalGradient(listOf(bottomColor, topColor), startY = 0f, endY = radius * 2f)
            val normalBorderCol = borderGlow.copy(alpha = 0.72f)
            val pressedBorderCol = borderGlow
            val normalStroke = Stroke(width = normalStrokePx)
            val pressedStroke = Stroke(width = pressedStrokePx)
        }
    }
    Box(
        modifier = Modifier
            .offset { IntOffset((center.x - radius).roundToInt(), (center.y - radius).roundToInt()) }
            .size(diameterDp)
            .graphicsLayer {
                val pressed = (touchMaskProvider() and keyBit) != 0
                val s = if (pressed) 0.91f else 1f
                scaleX = s
                scaleY = s
            }
            .drawBehind {
                val pressed = (touchMaskProvider() and keyBit) != 0
                drawCircle(brush = drawResources.glowBrush, radius = radius * 1.16f)
                drawCircle(brush = if (pressed) drawResources.pressedBrush else drawResources.normalBrush, radius = radius)
                drawCircle(
                    color = if (pressed) drawResources.pressedBorderCol else drawResources.normalBorderCol,
                    radius = radius - 1f,
                    style = if (pressed) drawResources.pressedStroke else drawResources.normalStroke,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontFamily = SpaceGrotesk,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PillButton(
    label: String,
    center: Offset,
    widthPx: Float,
    heightPx: Float,
    bgColor: Color,
    borderColor: Color,
    textColor: Color,
    keyBit: Int,
    touchMaskProvider: () -> Int,
) {
    val density = LocalDensity.current
    val wDp = with(density) { widthPx.toDp() }
    val hDp = with(density) { heightPx.toDp() }
    val drawRes = remember(bgColor, borderColor, widthPx, heightPx, density) {
        object {
            val normalBg = bgColor
            val pressedBg = borderColor.copy(alpha = 0.65f)
            val borderCol = borderColor.copy(alpha = 0.85f)
            val borderStroke = Stroke(width = with(density) { 1.2.dp.toPx() })
            val corner = CornerRadius(heightPx / 2f, heightPx / 2f)
        }
    }
    Box(
        modifier = Modifier
            .offset { IntOffset((center.x - widthPx / 2f).roundToInt(), (center.y - heightPx / 2f).roundToInt()) }
            .size(wDp, hDp)
            .rotate(-10f)
            .drawBehind {
                val pressed = (touchMaskProvider() and keyBit) != 0
                drawRoundRect(
                    color = if (pressed) drawRes.pressedBg else drawRes.normalBg,
                    cornerRadius = drawRes.corner,
                )
                drawRoundRect(
                    color = drawRes.borderCol,
                    cornerRadius = drawRes.corner,
                    style = drawRes.borderStroke,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontFamily = WatermelonMono,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ShoulderButton(
    label: String,
    center: Offset,
    widthPx: Float,
    heightPx: Float,
    topColor: Color,
    bottomColor: Color,
    borderGlow: Color,
    textColor: Color,
    keyBit: Int,
    touchMaskProvider: () -> Int,
) {
    val density = LocalDensity.current
    val wDp = with(density) { widthPx.toDp() }
    val hDp = with(density) { heightPx.toDp() }
    val drawRes = remember(topColor, bottomColor, borderGlow, widthPx, heightPx, density) {
        val cornerPx = with(density) { 14.dp.toPx() }
        object {
            val bgBrush = Brush.verticalGradient(listOf(topColor, bottomColor), startY = 0f, endY = heightPx)
            val normalBorderCol = borderGlow.copy(alpha = 0.72f)
            val pressedBorderCol = borderGlow
            val normalStroke = Stroke(width = with(density) { 1.4.dp.toPx() })
            val pressedStroke = Stroke(width = with(density) { 2.2.dp.toPx() })
            val corner = CornerRadius(cornerPx, cornerPx)
        }
    }
    Box(
        modifier = Modifier
            .offset { IntOffset((center.x - widthPx / 2f).roundToInt(), (center.y - heightPx / 2f).roundToInt()) }
            .size(wDp, hDp)
            .drawBehind {
                val pressed = (touchMaskProvider() and keyBit) != 0
                drawRoundRect(
                    brush = drawRes.bgBrush,
                    cornerRadius = drawRes.corner,
                )
                drawRoundRect(
                    color = if (pressed) drawRes.pressedBorderCol else drawRes.normalBorderCol,
                    cornerRadius = drawRes.corner,
                    style = if (pressed) drawRes.pressedStroke else drawRes.normalStroke,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontFamily = SpaceGrotesk,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
        )
    }
}
