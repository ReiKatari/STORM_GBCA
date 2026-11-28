package com.stormgbca.emulator.ui.emulator.overlay

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.MotionEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Upload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
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
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

private const val DPAD_DIRECTION_MASK =
    NativeCore.KEY_UP or NativeCore.KEY_DOWN or NativeCore.KEY_LEFT or NativeCore.KEY_RIGHT

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

    var stickDx by remember { mutableFloatStateOf(0f) }
    var stickDy by remember { mutableFloatStateOf(0f) }
    var stickPointerId by remember { mutableIntStateOf(-1) }
    val stickDxProvider = remember { { stickDx } }
    val stickDyProvider = remember { { stickDy } }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val h = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val isLandscape = w > h
        val activeControls = if (isLandscape) profile.landscapeControls else profile.portraitControls

        val rects = remember(w, h, isLandscape, preset, profile, baseScale, activeControls) {
            val minDim = minOf(w, h)
            val dpadR = (minDim * 0.155f * baseScale).coerceIn(84f, 180f)
            val abR = (minDim * 0.080f * baseScale).coerceIn(46f, 95f)
            val pillW = (minDim * 0.152f * baseScale).coerceIn(96f, 174f)
            val pillH = pillW * 0.39f
            val shW = (minDim * 0.240f * baseScale).coerceIn(140f, 275f)
            val shH = shW * 0.40f
            val qBtnR = (minDim * 0.048f * baseScale).coerceIn(34f, 60f)

            val dpadC: Offset
            val abBase: Offset
            val selectC: Offset
            val startC: Offset
            val lC: Offset
            val rC: Offset

            when (preset) {
                OverlayLayoutPreset.CUSTOM, OverlayLayoutPreset.CLASSIC_HANDHELD -> {
                    dpadC = Offset(w * activeControls.dpadPos.xFraction, h * activeControls.dpadPos.yFraction)
                    abBase = Offset(w * activeControls.abPos.xFraction, h * activeControls.abPos.yFraction)
                    selectC = Offset(w * activeControls.selectPos.xFraction, h * activeControls.selectPos.yFraction)
                    startC = Offset(w * activeControls.startPos.xFraction, h * activeControls.startPos.yFraction)
                    lC = Offset(w * activeControls.lPos.xFraction, h * activeControls.lPos.yFraction)
                    rC = Offset(w * activeControls.rPos.xFraction, h * activeControls.rPos.yFraction)
                }
                OverlayLayoutPreset.WIDE_GRIP -> {
                    dpadC = Offset(w * (if (isLandscape) 0.095f else 0.170f), h * (if (isLandscape) 0.605f else 0.740f))
                    abBase = Offset(w * (if (isLandscape) 0.905f else 0.830f), h * (if (isLandscape) 0.605f else 0.740f))
                    selectC = Offset(w * (if (isLandscape) 0.095f else 0.330f), h * (if (isLandscape) 0.905f else 0.922f))
                    startC = Offset(w * (if (isLandscape) 0.905f else 0.670f), h * (if (isLandscape) 0.905f else 0.922f))
                    lC = Offset(w * (if (isLandscape) 0.095f else 0.170f), h * (if (isLandscape) 0.335f else 0.560f))
                    rC = Offset(w * (if (isLandscape) 0.905f else 0.830f), h * (if (isLandscape) 0.335f else 0.560f))
                }
                OverlayLayoutPreset.COMPACT_MINIMAL -> {
                    dpadC = Offset(w * (if (isLandscape) 0.105f else 0.185f), h * (if (isLandscape) 0.635f else 0.760f))
                    abBase = Offset(w * (if (isLandscape) 0.895f else 0.815f), h * (if (isLandscape) 0.635f else 0.760f))
                    selectC = Offset(w * (if (isLandscape) 0.105f else 0.340f), h * (if (isLandscape) 0.915f else 0.930f))
                    startC = Offset(w * (if (isLandscape) 0.895f else 0.660f), h * (if (isLandscape) 0.915f else 0.930f))
                    lC = Offset(w * (if (isLandscape) 0.105f else 0.185f), h * (if (isLandscape) 0.365f else 0.580f))
                    rC = Offset(w * (if (isLandscape) 0.895f else 0.815f), h * (if (isLandscape) 0.365f else 0.580f))
                }
                OverlayLayoutPreset.FLOATING_GLASS -> {
                    dpadC = Offset(w * (if (isLandscape) 0.110f else 0.190f), h * (if (isLandscape) 0.610f else 0.745f))
                    abBase = Offset(w * (if (isLandscape) 0.890f else 0.810f), h * (if (isLandscape) 0.610f else 0.745f))
                    selectC = Offset(w * (if (isLandscape) 0.110f else 0.335f), h * (if (isLandscape) 0.900f else 0.920f))
                    startC = Offset(w * (if (isLandscape) 0.890f else 0.665f), h * (if (isLandscape) 0.900f else 0.920f))
                    lC = Offset(w * (if (isLandscape) 0.110f else 0.190f), h * (if (isLandscape) 0.340f else 0.565f))
                    rC = Offset(w * (if (isLandscape) 0.890f else 0.810f), h * (if (isLandscape) 0.340f else 0.565f))
                }
            }

            // Separate independent A & B positioning OR guaranteed non-overlapping diagonal cluster
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

        fun resolveDirectionalMask(dx: Float, dy: Float): Int {
            val dist = hypot(dx, dy)
            if (profile.useAnalogStick) {
                val deadzone = rects.dpadRadius * 0.14f
                if (dist < deadzone) return 0
                var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
                if (angle < 0.0) angle += 360.0
                return when {
                    angle >= 337.5 || angle < 22.5 -> NativeCore.KEY_RIGHT
                    angle < 67.5 -> NativeCore.KEY_RIGHT or NativeCore.KEY_DOWN
                    angle < 112.5 -> NativeCore.KEY_DOWN
                    angle < 157.5 -> NativeCore.KEY_DOWN or NativeCore.KEY_LEFT
                    angle < 202.5 -> NativeCore.KEY_LEFT
                    angle < 247.5 -> NativeCore.KEY_LEFT or NativeCore.KEY_UP
                    angle < 292.5 -> NativeCore.KEY_UP
                    else -> NativeCore.KEY_UP or NativeCore.KEY_RIGHT
                }
            }
            val dead = rects.dpadRadius * 0.22f
            var mask = 0
            if (dx < -dead) mask = mask or NativeCore.KEY_LEFT
            if (dx > dead) mask = mask or NativeCore.KEY_RIGHT
            if (dy < -dead) mask = mask or NativeCore.KEY_UP
            if (dy > dead) mask = mask or NativeCore.KEY_DOWN
            return mask
        }

        fun updateStickDisplacement(dx: Float, dy: Float) {
            val maxTravel = rects.dpadRadius * 0.44f
            val dist = hypot(dx, dy)
            if (dist > maxTravel && dist > 0.001f) {
                val s = maxTravel / dist
                stickDx = dx * s
                stickDy = dy * s
            } else {
                stickDx = dx
                stickDy = dy
            }
        }

        fun resolveNonDpadMaskAt(pt: Offset): Int {
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
                return abs(pt.x - c.x) <= rw * 0.60f && abs(pt.y - c.y) <= rh * 0.85f
            }
            if (insideRect(rects.selectCenter, rects.pillW, rects.pillH)) return NativeCore.KEY_SELECT
            if (insideRect(rects.startCenter, rects.pillW, rects.pillH)) return NativeCore.KEY_START
            if (showShoulders && insideRect(rects.shoulderLCenter, rects.shoulderW, rects.shoulderH)) return NativeCore.KEY_L
            if (showShoulders && insideRect(rects.shoulderRCenter, rects.shoulderW, rects.shoulderH)) return NativeCore.KEY_R
            return 0
        }

        fun isInsideDpadArea(pt: Offset, radiusMultiplier: Float = 1.25f): Boolean {
            val dx = pt.x - rects.dpadCenter.x
            val dy = pt.y - rects.dpadCenter.y
            val hitR = rects.dpadRadius * radiusMultiplier
            return dx * dx + dy * dy <= hitR * hitR
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
                            val pt = Offset(ev.getX(idx), ev.getY(idx))
                            if (stickPointerId == -1 && isInsideDpadArea(pt, 1.25f)) {
                                stickPointerId = pid
                                val dx = pt.x - rects.dpadCenter.x
                                val dy = pt.y - rects.dpadCenter.y
                                updateStickDisplacement(dx, dy)
                                pointerButtons[pid] = resolveDirectionalMask(dx, dy)
                                changed = true
                            } else {
                                val mask = resolveNonDpadMaskAt(pt)
                                if (mask != 0) {
                                    pointerButtons[pid] = mask
                                    changed = true
                                }
                            }
                        }
                        MotionEvent.ACTION_MOVE -> {
                            for (i in 0 until ev.pointerCount) {
                                val pid = ev.getPointerId(i)
                                val pt = Offset(ev.getX(i), ev.getY(i))
                                if (pid == stickPointerId) {
                                    val dx = pt.x - rects.dpadCenter.x
                                    val dy = pt.y - rects.dpadCenter.y
                                    if (profile.useAnalogStick || isInsideDpadArea(pt, 1.32f)) {
                                        updateStickDisplacement(dx, dy)
                                        val mask = resolveDirectionalMask(dx, dy)
                                        val prev = pointerButtons[pid] ?: 0
                                        if (mask != prev) {
                                            pointerButtons[pid] = mask
                                            changed = true
                                        }
                                    } else {
                                        stickPointerId = -1
                                        stickDx = 0f
                                        stickDy = 0f
                                        val mask = resolveNonDpadMaskAt(pt)
                                        val prev = pointerButtons[pid] ?: 0
                                        if (mask != prev) {
                                            if (mask == 0) pointerButtons.remove(pid) else pointerButtons[pid] = mask
                                            changed = true
                                        }
                                    }
                                } else if (stickPointerId == -1 && isInsideDpadArea(pt, 1.25f)) {
                                    stickPointerId = pid
                                    val dx = pt.x - rects.dpadCenter.x
                                    val dy = pt.y - rects.dpadCenter.y
                                    updateStickDisplacement(dx, dy)
                                    val mask = resolveDirectionalMask(dx, dy)
                                    val prev = pointerButtons[pid] ?: 0
                                    if (mask != prev) {
                                        pointerButtons[pid] = mask
                                        changed = true
                                    }
                                } else {
                                    val mask = resolveNonDpadMaskAt(pt)
                                    val prev = pointerButtons[pid] ?: 0
                                    if (mask != prev) {
                                        if (mask == 0) pointerButtons.remove(pid) else pointerButtons[pid] = mask
                                        changed = true
                                    }
                                }
                            }
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                            val pid = ev.getPointerId(idx)
                            if (pid == stickPointerId) {
                                stickPointerId = -1
                                stickDx = 0f
                                stickDy = 0f
                            }
                            if (pointerButtons.remove(pid) != null) changed = true
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            stickPointerId = -1
                            stickDx = 0f
                            stickDy = 0f
                            if (pointerButtons.isNotEmpty()) {
                                pointerButtons.clear()
                                changed = true
                            }
                        }
                    }
                    if (changed) {
                        var combined = 0
                        for (m in pointerButtons.values) combined = combined or m
                        val prevCombined = activeTouchMask
                        if (combined != prevCombined) {
                            val newlyPressed = combined and prevCombined.inv()
                            if (newlyPressed != 0) {
                                triggerHapticTick(vibrator, profile.hapticsEnabled)
                            }
                            activeTouchMask = combined
                            onKeyMaskChanged(combined)
                        }
                    }
                    pointerButtons.isNotEmpty() || stickPointerId != -1
                },
        ) {
            if (profile.useAnalogStick) {
                ModernAnalogStick(
                    center = rects.dpadCenter,
                    radius = rects.dpadRadius,
                    stickDxProvider = stickDxProvider,
                    stickDyProvider = stickDyProvider,
                    style = style,
                    touchMaskProvider = touchMaskProvider,
                )
            } else {
                Modern3DDpad(
                    center = rects.dpadCenter,
                    radius = rects.dpadRadius,
                    style = style,
                    touchMaskProvider = touchMaskProvider,
                )
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
                glowColor = style.glow,
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
                glowColor = style.glow,
                textColor = style.labelColor,
                keyBit = NativeCore.KEY_START,
                touchMaskProvider = touchMaskProvider,
            )

            if (showShoulders) {
                ShoulderButton(
                    label = "L",
                    isLeft = true,
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
                    isLeft = false,
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
private fun Modern3DDpad(
    center: Offset,
    radius: Float,
    style: GbcaButtonStyle,
    touchMaskProvider: () -> Int,
) {
    val upPressed by remember { derivedStateOf { (touchMaskProvider() and NativeCore.KEY_UP) != 0 } }
    val downPressed by remember { derivedStateOf { (touchMaskProvider() and NativeCore.KEY_DOWN) != 0 } }
    val leftPressed by remember { derivedStateOf { (touchMaskProvider() and NativeCore.KEY_LEFT) != 0 } }
    val rightPressed by remember { derivedStateOf { (touchMaskProvider() and NativeCore.KEY_RIGHT) != 0 } }

    val dpadSpring = remember { spring<Float>(stiffness = 850f, dampingRatio = 0.72f) }
    val upAnim by animateFloatAsState(if (upPressed) 1f else 0f, dpadSpring, label = "dpadUp")
    val downAnim by animateFloatAsState(if (downPressed) 1f else 0f, dpadSpring, label = "dpadDown")
    val leftAnim by animateFloatAsState(if (leftPressed) 1f else 0f, dpadSpring, label = "dpadLeft")
    val rightAnim by animateFloatAsState(if (rightPressed) 1f else 0f, dpadSpring, label = "dpadRight")

    val wellR = radius * 1.04f
    val armWidth = radius * 0.56f
    val armLen = radius * 1.78f
    val crossCorner = CornerRadius(armWidth * 0.22f, armWidth * 0.22f)

    val wellBrush = remember(center, wellR, style) {
        Brush.radialGradient(
            colors = listOf(
                Color(0xBB05070D),
                style.dpadBgBottom.copy(alpha = 0.56f),
                style.dpadBgBottom.copy(alpha = 0.34f),
            ),
            center = center,
            radius = wellR,
        )
    }
    val crossBodyBrush = remember(center, armLen, style) {
        Brush.verticalGradient(
            colors = listOf(style.dpadBgTop, style.dpadBgBottom),
            startY = center.y - armLen * 0.5f,
            endY = center.y + armLen * 0.5f,
        )
    }
    val centerDimpleRadius = armWidth * 0.34f
    val baseArrowColor = remember(style) { style.labelColor.copy(alpha = 0.42f) }

    Canvas(modifier = Modifier.fillMaxSize()) {
        // 1. Recessed circular housing well + bevel ring
        drawCircle(brush = wellBrush, radius = wellR, center = center)
        drawCircle(
            color = style.dpadBorder.copy(alpha = 0.48f),
            radius = wellR,
            center = center,
            style = Stroke(width = 2.4f),
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.12f),
            radius = wellR - 2.4f,
            center = center,
            style = Stroke(width = 1.2f),
        )

        // 2. Directional 3D tilt offsets
        val tiltX = (rightAnim - leftAnim) * 4.5f
        val tiltY = (downAnim - upAnim) * 4.5f
        val bodyCenter = Offset(center.x + tiltX * 0.38f, center.y + tiltY * 0.38f)
        val shadowCenter = Offset(center.x + tiltX * 0.90f, center.y + 4f + tiltY * 0.90f)

        // 3. Bottom 3D extrusion drop shadow
        val shadowCol = Color.Black.copy(alpha = 0.55f)
        drawRoundRect(
            color = shadowCol,
            topLeft = Offset(shadowCenter.x - armWidth * 0.5f, shadowCenter.y - armLen * 0.5f),
            size = Size(armWidth, armLen),
            cornerRadius = crossCorner,
        )
        drawRoundRect(
            color = shadowCol,
            topLeft = Offset(shadowCenter.x - armLen * 0.5f, shadowCenter.y - armWidth * 0.5f),
            size = Size(armLen, armWidth),
            cornerRadius = crossCorner,
        )

        // 4. Sculpted 3D cross body
        val vTopLeft = Offset(bodyCenter.x - armWidth * 0.5f, bodyCenter.y - armLen * 0.5f)
        val hTopLeft = Offset(bodyCenter.x - armLen * 0.5f, bodyCenter.y - armWidth * 0.5f)
        drawRoundRect(
            brush = crossBodyBrush,
            topLeft = vTopLeft,
            size = Size(armWidth, armLen),
            cornerRadius = crossCorner,
        )
        drawRoundRect(
            brush = crossBodyBrush,
            topLeft = hTopLeft,
            size = Size(armLen, armWidth),
            cornerRadius = crossCorner,
        )

        // Crisp top-edge specular rim highlights
        val rimColor = Color.White.copy(alpha = 0.24f)
        drawLine(
            color = rimColor,
            start = Offset(vTopLeft.x + armWidth * 0.18f, vTopLeft.y + 2f),
            end = Offset(vTopLeft.x + armWidth * 0.82f, vTopLeft.y + 2f),
            strokeWidth = 2f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = rimColor,
            start = Offset(hTopLeft.x + armWidth * 0.16f, hTopLeft.y + 2f),
            end = Offset(hTopLeft.x + armLen - armWidth * 0.16f, hTopLeft.y + 2f),
            strokeWidth = 1.6f,
            cap = StrokeCap.Round,
        )

        // Subtle outer border on cross
        val crossOutline = style.dpadBorder.copy(alpha = 0.55f)
        drawRoundRect(
            color = crossOutline,
            topLeft = vTopLeft,
            size = Size(armWidth, armLen),
            cornerRadius = crossCorner,
            style = Stroke(width = 1.5f),
        )
        drawRoundRect(
            color = crossOutline,
            topLeft = hTopLeft,
            size = Size(armLen, armWidth),
            cornerRadius = crossCorner,
            style = Stroke(width = 1.5f),
        )

        // 5. Per-arm neon radial press bloom + etched triangle arrows
        val armDist = armLen * 0.34f
        val bloomR = armWidth * 0.62f
        val arrowSize = armWidth * 0.22f

        fun drawArmBloomAndArrow(
            anim: Float,
            armOffset: Offset,
            dirX: Float,
            dirY: Float,
        ) {
            if (anim > 0.01f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(style.glow.copy(alpha = 0.50f * anim), Color.Transparent),
                        center = armOffset,
                        radius = bloomR * (0.9f + 0.2f * anim),
                    ),
                    radius = bloomR * (0.9f + 0.2f * anim),
                    center = armOffset,
                )
            }
            val arrowColor = lerp(baseArrowColor, style.glow, anim)
            drawEtchedTriangle(
                tipCenter = armOffset,
                dirX = dirX,
                dirY = dirY,
                size = arrowSize,
                color = arrowColor,
            )
        }

        drawArmBloomAndArrow(upAnim, Offset(bodyCenter.x, bodyCenter.y - armDist), 0f, -1f)
        drawArmBloomAndArrow(downAnim, Offset(bodyCenter.x, bodyCenter.y + armDist), 0f, 1f)
        drawArmBloomAndArrow(leftAnim, Offset(bodyCenter.x - armDist, bodyCenter.y), -1f, 0f)
        drawArmBloomAndArrow(rightAnim, Offset(bodyCenter.x + armDist, bodyCenter.y), 1f, 0f)

        // 6. Sculpted concave center dimple
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.45f),
                    style.dpadBgBottom.copy(alpha = 0.72f),
                ),
                center = Offset(bodyCenter.x, bodyCenter.y + 1.5f),
                radius = centerDimpleRadius,
            ),
            radius = centerDimpleRadius,
            center = bodyCenter,
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.14f),
            radius = centerDimpleRadius,
            center = bodyCenter,
            style = Stroke(width = 1.2f),
        )
    }
}

private fun DrawScope.drawEtchedTriangle(
    tipCenter: Offset,
    dirX: Float,
    dirY: Float,
    size: Float,
    color: Color,
) {
    // Perpendicular vector
    val px = -dirY
    val py = dirX
    val tip = Offset(tipCenter.x + dirX * size * 0.65f, tipCenter.y + dirY * size * 0.65f)
    val baseCenter = Offset(tipCenter.x - dirX * size * 0.50f, tipCenter.y - dirY * size * 0.50f)
    val leftCorner = Offset(baseCenter.x + px * size * 0.72f, baseCenter.y + py * size * 0.72f)
    val rightCorner = Offset(baseCenter.x - px * size * 0.72f, baseCenter.y - py * size * 0.72f)

    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(leftCorner.x, leftCorner.y)
        lineTo(rightCorner.x, rightCorner.y)
        close()
    }
    drawPath(path = path, color = color, style = Fill)
}

@Composable
private fun ModernAnalogStick(
    center: Offset,
    radius: Float,
    stickDxProvider: () -> Float,
    stickDyProvider: () -> Float,
    style: GbcaButtonStyle,
    touchMaskProvider: () -> Int,
) {
    val targetDx = stickDxProvider()
    val targetDy = stickDyProvider()
    val stickSpring = remember { spring<Float>(stiffness = 680f, dampingRatio = 0.68f) }
    val animDx by animateFloatAsState(targetDx, stickSpring, label = "stickDx")
    val animDy by animateFloatAsState(targetDy, stickSpring, label = "stickDy")

    val isDeflected by remember(radius) {
        derivedStateOf {
            hypot(stickDxProvider(), stickDyProvider()) > radius * 0.06f ||
                (touchMaskProvider() and DPAD_DIRECTION_MASK) != 0
        }
    }
    val activeGlowAnim by animateFloatAsState(
        targetValue = if (isDeflected) 1f else 0f,
        animationSpec = spring(stiffness = 680f, dampingRatio = 0.72f),
        label = "stickActiveGlow",
    )

    val socketR = radius * 0.98f
    val thumbR = radius * 0.52f

    val socketWellBrush = remember(center, socketR, style) {
        Brush.radialGradient(
            colors = listOf(
                Color(0xCC04060B),
                style.dpadBgBottom.copy(alpha = 0.66f),
                style.dpadBgBottom.copy(alpha = 0.34f),
            ),
            center = center,
            radius = socketR,
        )
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        // 1. Recessed circular socket ring + metallic rim + 4 cardinal alignment ticks (N, S, E, W)
        drawCircle(brush = socketWellBrush, radius = socketR, center = center)
        drawCircle(
            color = lerp(style.dpadBorder.copy(alpha = 0.52f), style.glow.copy(alpha = 0.85f), activeGlowAnim * 0.55f),
            radius = socketR,
            center = center,
            style = Stroke(width = 2.4f),
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.10f),
            radius = socketR - 3f,
            center = center,
            style = Stroke(width = 1.1f),
        )

        val tickInner = socketR * 0.82f
        val tickOuter = socketR * 0.94f
        val tickColor = style.dpadBorder.copy(alpha = 0.68f)
        // North, South, East, West ticks
        drawLine(tickColor, Offset(center.x, center.y - tickInner), Offset(center.x, center.y - tickOuter), strokeWidth = 2.4f, cap = StrokeCap.Round)
        drawLine(tickColor, Offset(center.x, center.y + tickInner), Offset(center.x, center.y + tickOuter), strokeWidth = 2.4f, cap = StrokeCap.Round)
        drawLine(tickColor, Offset(center.x - tickInner, center.y), Offset(center.x - tickOuter, center.y), strokeWidth = 2.4f, cap = StrokeCap.Round)
        drawLine(tickColor, Offset(center.x + tickInner, center.y), Offset(center.x + tickOuter, center.y), strokeWidth = 2.4f, cap = StrokeCap.Round)

        // 2. Active directional radial rim glow along deflected direction
        val deflDist = hypot(animDx, animDy)
        if (activeGlowAnim > 0.02f && deflDist > 1f) {
            val nx = animDx / deflDist
            val ny = animDy / deflDist
            val rimGlowCenter = Offset(center.x + nx * socketR * 0.88f, center.y + ny * socketR * 0.88f)
            val rimGlowR = socketR * 0.62f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(style.glow.copy(alpha = 0.42f * activeGlowAnim), Color.Transparent),
                    center = rimGlowCenter,
                    radius = rimGlowR,
                ),
                radius = rimGlowR,
                center = rimGlowCenter,
            )
        }

        // 3. 3D Thumb Cap at (c.x + animDx, c.y + animDy)
        val thumbCenter = Offset(center.x + animDx, center.y + animDy)

        // Dynamic offset drop shadow
        val shadowCenter = Offset(thumbCenter.x + animDx * 0.14f, thumbCenter.y + 5f + animDy * 0.14f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent),
                center = shadowCenter,
                radius = thumbR * 1.15f,
            ),
            radius = thumbR * 1.15f,
            center = shadowCenter,
        )

        // Expanding active aura ring
        if (activeGlowAnim > 0.01f) {
            val auraR = thumbR * (1.08f + 0.12f * activeGlowAnim)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(style.glow.copy(alpha = 0.40f * activeGlowAnim), Color.Transparent),
                    center = thumbCenter,
                    radius = auraR,
                ),
                radius = auraR,
                center = thumbCenter,
            )
        }

        // 3D vertical dome gradient + glossy top bevel rim
        drawCircle(
            brush = Brush.verticalGradient(
                colors = listOf(style.dpadBgTop, style.dpadBgBottom),
                startY = thumbCenter.y - thumbR,
                endY = thumbCenter.y + thumbR,
            ),
            radius = thumbR,
            center = thumbCenter,
        )
        drawCircle(
            color = lerp(style.dpadBorder.copy(alpha = 0.72f), style.glow, activeGlowAnim),
            radius = thumbR,
            center = thumbCenter,
            style = Stroke(width = 2.0f + 1.0f * activeGlowAnim),
        )
        // Glossy top bevel highlight
        drawArc(
            color = Color.White.copy(alpha = 0.30f),
            startAngle = 210f,
            sweepAngle = 120f,
            useCenter = false,
            topLeft = Offset(thumbCenter.x - thumbR + 2f, thumbCenter.y - thumbR + 2f),
            size = Size((thumbR - 2f) * 2f, (thumbR - 2f) * 2f),
            style = Stroke(width = 2.2f, cap = StrokeCap.Round),
        )

        // Inner concave thumb dish + inner rim highlight
        val dishR = thumbR * 0.66f
        drawCircle(
            brush = Brush.verticalGradient(
                colors = listOf(style.dpadBgBottom, style.dpadBgTop.copy(alpha = 0.92f)),
                startY = thumbCenter.y - dishR,
                endY = thumbCenter.y + dishR,
            ),
            radius = dishR,
            center = thumbCenter,
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.14f),
            radius = dishR,
            center = thumbCenter,
            style = Stroke(width = 1.2f),
        )

        // Central grip ring
        val gripR = thumbR * 0.24f
        drawCircle(
            color = lerp(style.dpadBorder.copy(alpha = 0.45f), style.glow.copy(alpha = 0.90f), activeGlowAnim),
            radius = gripR,
            center = thumbCenter,
            style = Stroke(width = 1.8f),
        )
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
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressAnim by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = spring(stiffness = 900f, dampingRatio = 0.68f),
        label = "quickBtnPress",
    )
    val bgBrush = remember(active, accent) {
        Brush.verticalGradient(
            if (active) {
                listOf(accent.copy(alpha = 0.42f), Color(0xDD0A0E1A))
            } else {
                listOf(Color(0xCC161C2E), Color(0xCC090C14))
            },
        )
    }
    Box(
        modifier = Modifier
            .offset { IntOffset((center.x - radiusPx).roundToInt(), (center.y - radiusPx).roundToInt()) }
            .size(diameterDp)
            .graphicsLayer {
                val s = 1f - 0.13f * pressAnim
                scaleX = s
                scaleY = s
            }
            .drawBehind {
                if (pressAnim > 0.01f) {
                    val ringR = radiusPx * (1.05f + 0.28f * pressAnim)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(accent.copy(alpha = 0.42f * pressAnim), Color.Transparent),
                            center = Offset(radiusPx, radiusPx),
                            radius = ringR,
                        ),
                        radius = ringR,
                    )
                }
            }
            .alpha(if (active) 1f else alpha.coerceIn(0.45f, 0.96f))
            .shadow(if (active) 10.dp else 4.dp, CircleShape, spotColor = if (active) accent else borderGlow)
            .clip(CircleShape)
            .background(bgBrush)
            .border(
                width = if (active || isPressed) 1.8.dp else 1.1.dp,
                color = if (active || isPressed) accent else borderGlow.copy(alpha = 0.55f),
                shape = CircleShape,
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active || isPressed) accent else Color.White,
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
    val pressed by remember(keyBit) { derivedStateOf { (touchMaskProvider() and keyBit) != 0 } }
    val pressAnim by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(stiffness = 900f, dampingRatio = 0.70f),
        label = "circlePress_$label",
    )
    val normalStrokePx = with(density) { 1.6.dp.toPx() }
    val pressedStrokePx = with(density) { 2.7.dp.toPx() }

    Box(
        modifier = Modifier
            .offset { IntOffset((center.x - radius).roundToInt(), (center.y - radius).roundToInt()) }
            .size(diameterDp)
            .graphicsLayer {
                val s = 1f - 0.11f * pressAnim
                scaleX = s
                scaleY = s
                translationY = 3.5f * pressAnim
            }
            .drawBehind {
                val glowR = radius * (1.16f + 0.16f * pressAnim)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(borderGlow.copy(alpha = 0.22f + 0.34f * pressAnim), Color.Transparent),
                        center = Offset(radius, radius),
                        radius = glowR,
                    ),
                    radius = glowR,
                )
                val gradTop = lerp(topColor, bottomColor, pressAnim * 0.72f)
                val gradBottom = lerp(bottomColor, topColor, pressAnim * 0.50f)
                drawCircle(
                    brush = Brush.verticalGradient(listOf(gradTop, gradBottom), startY = 0f, endY = radius * 2f),
                    radius = radius,
                )
                // Glossy top-crescent highlight
                drawArc(
                    color = Color.White.copy(alpha = 0.28f * (1f - 0.45f * pressAnim)),
                    startAngle = 210f,
                    sweepAngle = 120f,
                    useCenter = false,
                    topLeft = Offset(3f, 3f),
                    size = Size((radius - 3f) * 2f, (radius - 3f) * 2f),
                    style = Stroke(width = 2.4f, cap = StrokeCap.Round),
                )
                val borderCol = lerp(borderGlow.copy(alpha = 0.72f), borderGlow, pressAnim)
                val strokeW = normalStrokePx + (pressedStrokePx - normalStrokePx) * pressAnim
                drawCircle(
                    color = borderCol,
                    radius = radius - 1f,
                    style = Stroke(width = strokeW),
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
    glowColor: Color,
    textColor: Color,
    keyBit: Int,
    touchMaskProvider: () -> Int,
) {
    val density = LocalDensity.current
    val wDp = with(density) { widthPx.toDp() }
    val hDp = with(density) { heightPx.toDp() }
    val pressed by remember(keyBit) { derivedStateOf { (touchMaskProvider() and keyBit) != 0 } }
    val pressAnim by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(stiffness = 900f, dampingRatio = 0.70f),
        label = "pillPress_$label",
    )
    val corner = remember(heightPx) { CornerRadius(heightPx / 2f, heightPx / 2f) }
    val normalStroke = with(density) { 1.2.dp.toPx() }
    val pressedStroke = with(density) { 2.0.dp.toPx() }

    Box(
        modifier = Modifier
            .offset { IntOffset((center.x - widthPx / 2f).roundToInt(), (center.y - heightPx / 2f).roundToInt()) }
            .size(wDp, hDp)
            .rotate(-10f)
            .graphicsLayer {
                val s = 1f - 0.10f * pressAnim
                scaleX = s
                scaleY = s
                translationY = 2.5f * pressAnim
            }
            .drawBehind {
                if (pressAnim > 0.01f) {
                    val glowR = widthPx * 0.62f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(glowColor.copy(alpha = 0.34f * pressAnim), Color.Transparent),
                            center = Offset(widthPx * 0.5f, heightPx * 0.5f),
                            radius = glowR,
                        ),
                        radius = glowR,
                    )
                }
                val fillCol = lerp(bgColor, glowColor.copy(alpha = 0.55f), pressAnim)
                val strokeCol = lerp(borderColor.copy(alpha = 0.85f), glowColor, pressAnim)
                drawRoundRect(color = fillCol, cornerRadius = corner)
                drawRoundRect(
                    color = strokeCol,
                    cornerRadius = corner,
                    style = Stroke(width = normalStroke + (pressedStroke - normalStroke) * pressAnim),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontFamily = WatermelonMono,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ShoulderButton(
    label: String,
    isLeft: Boolean,
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
    val pressed by remember(keyBit) { derivedStateOf { (touchMaskProvider() and keyBit) != 0 } }
    val pressAnim by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(stiffness = 900f, dampingRatio = 0.70f),
        label = "shoulderPress_$label",
    )
    val normalStrokePx = with(density) { 1.5.dp.toPx() }
    val pressedStrokePx = with(density) { 2.5.dp.toPx() }

    val bumperPath = remember(isLeft, widthPx, heightPx) {
        val outerTopR = heightPx * 0.72f
        val innerTopR = heightPx * 0.28f
        val bottomR = heightPx * 0.26f
        val tl = if (isLeft) outerTopR else innerTopR
        val tr = if (isLeft) innerTopR else outerTopR
        Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(0f, 0f, widthPx, heightPx),
                    topLeft = CornerRadius(tl, tl),
                    topRight = CornerRadius(tr, tr),
                    bottomRight = CornerRadius(bottomR, bottomR),
                    bottomLeft = CornerRadius(bottomR, bottomR),
                ),
            )
        }
    }

    Box(
        modifier = Modifier
            .offset { IntOffset((center.x - widthPx / 2f).roundToInt(), (center.y - heightPx / 2f).roundToInt()) }
            .size(wDp, hDp)
            .graphicsLayer {
                val s = 1f - 0.08f * pressAnim
                scaleX = s
                scaleY = s
                translationY = 4f * pressAnim
            }
            .drawBehind {
                if (pressAnim > 0.01f) {
                    val glowR = widthPx * 0.60f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(borderGlow.copy(alpha = 0.36f * pressAnim), Color.Transparent),
                            center = Offset(widthPx * 0.5f, heightPx * 0.5f),
                            radius = glowR,
                        ),
                        radius = glowR,
                    )
                }
                val gradTop = lerp(topColor, bottomColor, pressAnim * 0.65f)
                val gradBottom = lerp(bottomColor, borderGlow.copy(alpha = 0.45f), pressAnim * 0.40f)
                drawPath(
                    path = bumperPath,
                    brush = Brush.verticalGradient(listOf(gradTop, gradBottom), startY = 0f, endY = heightPx),
                )
                // Top specular lip
                val lipLeft = if (isLeft) heightPx * 0.48f else heightPx * 0.22f
                val lipRight = widthPx - (if (isLeft) heightPx * 0.22f else heightPx * 0.48f)
                drawLine(
                    color = Color.White.copy(alpha = 0.26f * (1f - 0.4f * pressAnim)),
                    start = Offset(lipLeft, 2.5f),
                    end = Offset(lipRight, 2.5f),
                    strokeWidth = 2.0f,
                    cap = StrokeCap.Round,
                )
                drawPath(
                    path = bumperPath,
                    color = lerp(borderGlow.copy(alpha = 0.72f), borderGlow, pressAnim),
                    style = Stroke(
                        width = normalStrokePx + (pressedStrokePx - normalStrokePx) * pressAnim,
                        join = StrokeJoin.Round,
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontFamily = SpaceGrotesk,
            fontSize = 15.5.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
        )
    }
}
