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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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

private fun triggerHapticTick(context: Context, enabled: Boolean) {
    if (!enabled) return
    runCatching {
        val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vib != null && vib.hasVibrator()) {
            vib.vibrate(VibrationEffect.createOneShot(14L, 90))
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
    val density = LocalDensity.current
    val style = remember(profile.buttonStyle) { ButtonThemeManager.resolve(profile.buttonStyle) }
    val alpha = (profile.opacityPercent.coerceIn(20, 100) / 100f)
    val baseScale = (profile.scalePercent.coerceIn(70, 140) / 100f)
    val preset = OverlayLayoutPreset.fromKey(profile.layoutPreset)
    val showShoulders = profile.showShoulders || system == ConsoleSystem.GBA

    var activeTouchMask by remember { mutableIntStateOf(0) }
    val pointerButtons = remember { mutableMapOf<Int, Int>() }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val h = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val isLandscape = w > h

        val rects = remember(w, h, isLandscape, preset, profile, baseScale) {
            val minDim = minOf(w, h)
            val dpadR = (minDim * 0.160f * baseScale).coerceIn(88f, 185f)
            val abR = (minDim * 0.082f * baseScale).coerceIn(48f, 98f)
            val pillW = (minDim * 0.12f * baseScale).coerceIn(76f, 140f)
            val pillH = pillW * 0.36f
            val shW = (minDim * 0.18f * baseScale).coerceIn(110f, 210f)
            val shH = shW * 0.40f

            val dpadC: Offset
            val abBase: Offset
            val ssCenter: Offset
            val lC: Offset
            val rC: Offset

            when (preset) {
                OverlayLayoutPreset.CUSTOM -> {
                    dpadC = Offset(w * profile.dpadPos.xFraction, h * profile.dpadPos.yFraction)
                    abBase = Offset(w * profile.abPos.xFraction, h * profile.abPos.yFraction)
                    ssCenter = Offset(w * profile.startSelectPos.xFraction, h * profile.startSelectPos.yFraction)
                    lC = Offset(w * profile.lPos.xFraction, h * profile.lPos.yFraction)
                    rC = Offset(w * profile.rPos.xFraction, h * profile.rPos.yFraction)
                }
                OverlayLayoutPreset.WIDE_GRIP -> {
                    dpadC = Offset(w * 0.15f, h * (if (isLandscape) 0.64f else 0.74f))
                    abBase = Offset(w * 0.84f, h * (if (isLandscape) 0.64f else 0.74f))
                    ssCenter = Offset(w * 0.50f, h * 0.90f)
                    lC = Offset(w * 0.12f, h * (if (isLandscape) 0.16f else 0.55f))
                    rC = Offset(w * 0.88f, h * (if (isLandscape) 0.16f else 0.55f))
                }
                OverlayLayoutPreset.COMPACT_MINIMAL -> {
                    dpadC = Offset(w * 0.17f, h * 0.78f)
                    abBase = Offset(w * 0.82f, h * 0.78f)
                    ssCenter = Offset(w * 0.50f, h * 0.92f)
                    lC = Offset(w * 0.14f, h * 0.56f)
                    rC = Offset(w * 0.86f, h * 0.56f)
                }
                OverlayLayoutPreset.FLOATING_GLASS -> {
                    dpadC = Offset(w * 0.18f, h * (if (isLandscape) 0.66f else 0.73f))
                    abBase = Offset(w * 0.81f, h * (if (isLandscape) 0.66f else 0.73f))
                    ssCenter = Offset(w * 0.50f, h * 0.88f)
                    lC = Offset(w * 0.13f, h * (if (isLandscape) 0.18f else 0.55f))
                    rC = Offset(w * 0.87f, h * (if (isLandscape) 0.18f else 0.55f))
                }
                OverlayLayoutPreset.CLASSIC_HANDHELD -> {
                    dpadC = Offset(w * (if (isLandscape) 0.15f else 0.20f), h * (if (isLandscape) 0.66f else 0.74f))
                    abBase = Offset(w * (if (isLandscape) 0.84f else 0.78f), h * (if (isLandscape) 0.66f else 0.74f))
                    ssCenter = Offset(w * 0.50f, h * (if (isLandscape) 0.88f else 0.90f))
                    lC = Offset(w * 0.13f, h * (if (isLandscape) 0.15f else 0.55f))
                    rC = Offset(w * 0.87f, h * (if (isLandscape) 0.15f else 0.55f))
                }
            }

            // Separate independent A & B positioning OR guaranteed non-overlapping diagonal cluster!
            val btnA: Offset
            val btnB: Offset
            if (profile.separateABButtons) {
                btnA = Offset(
                    (w * profile.btnAPos.xFraction).coerceIn(abR + 8f, (w - abR - 8f).coerceAtLeast(abR + 8f)),
                    (h * profile.btnAPos.yFraction).coerceIn(abR + 8f, (h - abR - 8f).coerceAtLeast(abR + 8f)),
                )
                btnB = Offset(
                    (w * profile.btnBPos.xFraction).coerceIn(abR + 8f, (w - abR - 8f).coerceAtLeast(abR + 8f)),
                    (h * profile.btnBPos.yFraction).coerceIn(abR + 8f, (h - abR - 8f).coerceAtLeast(abR + 8f)),
                )
            } else {
                // Guaranteed zero-overlap geometry: center-to-center distance = 2.65 * abR * (spacing/125) >= 2.24 * abR
                val spacingFactor = (profile.abSpacingPercent.coerceIn(100, 180) / 100f)
                val halfDist = abR * 1.12f * spacingFactor
                val dx = halfDist * 0.86f
                val dy = halfDist * 0.51f
                val maxCenterX = (w - abR - 10f - dx).coerceAtLeast(abR + dx + 10f)
                val clampedBaseX = abBase.x.coerceIn(abR + dx + 10f, maxCenterX)
                btnB = Offset(clampedBaseX - dx, (abBase.y + dy).coerceIn(abR + 8f, h - abR - 8f))
                btnA = Offset(clampedBaseX + dx, (abBase.y - dy).coerceIn(abR + 8f, h - abR - 8f))
            }
            val selectC = Offset(ssCenter.x - pillW * 0.68f, ssCenter.y)
            val startC = Offset(ssCenter.x + pillW * 0.68f, ssCenter.y)

            ResolvedControlRects(
                dpadCenter = dpadC,
                dpadRadius = dpadR,
                btnACenter = btnA,
                btnBCenter = btnB,
                btnRadius = abR,
                selectCenter = selectC,
                startCenter = startC,
                pillW = pillW,
                pillH = pillH,
                shoulderLCenter = lC,
                shoulderRCenter = rC,
                shoulderW = shW,
                shoulderH = shH,
            )
        }

        fun resolveMaskAt(pt: Offset): Int {
            // D-Pad with diagonal slide support
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
                                triggerHapticTick(context, profile.hapticsEnabled)
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
            // D-Pad visual
            Canvas(modifier = Modifier.fillMaxSize()) {
                val c = rects.dpadCenter
                val r = rects.dpadRadius
                drawCircle(
                    color = style.dpadBgBottom.copy(alpha = 0.42f),
                    radius = r,
                    center = c,
                )
                drawCircle(
                    color = style.dpadBorder.copy(alpha = 0.55f),
                    radius = r,
                    center = c,
                    style = Stroke(width = 2.5f),
                )
                val arm = r * 0.54f
                val len = r * 1.66f
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(style.dpadBgTop, style.dpadBgBottom)),
                    topLeft = Offset(c.x - arm / 2f, c.y - len / 2f),
                    size = Size(arm, len),
                    cornerRadius = CornerRadius(12f, 12f),
                )
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(style.dpadBgTop, style.dpadBgBottom)),
                    topLeft = Offset(c.x - len / 2f, c.y - arm / 2f),
                    size = Size(len, arm),
                    cornerRadius = CornerRadius(12f, 12f),
                )
                drawCircle(
                    color = Color.Black.copy(alpha = 0.28f),
                    radius = arm * 0.28f,
                    center = c,
                )
            }

            // Centered single-glyph A & B buttons
            ConcaveCircleButton(
                label = "B",
                center = rects.btnBCenter,
                radius = rects.btnRadius,
                topColor = style.btnBTop,
                bottomColor = style.btnBBottom,
                borderGlow = style.glow,
                textColor = style.labelColor,
                pressed = (activeTouchMask and NativeCore.KEY_B) != 0,
            )
            ConcaveCircleButton(
                label = "A",
                center = rects.btnACenter,
                radius = rects.btnRadius,
                topColor = style.btnATop,
                bottomColor = style.btnABottom,
                borderGlow = style.glow,
                textColor = style.labelColor,
                pressed = (activeTouchMask and NativeCore.KEY_A) != 0,
            )

            // Angled Pill SELECT / START
            PillButton(
                label = "SELECT",
                center = rects.selectCenter,
                widthPx = rects.pillW,
                heightPx = rects.pillH,
                bgColor = style.pillBg,
                borderColor = style.dpadBorder,
                textColor = style.labelColor,
                pressed = (activeTouchMask and NativeCore.KEY_SELECT) != 0,
            )
            PillButton(
                label = "START",
                center = rects.startCenter,
                widthPx = rects.pillW,
                heightPx = rects.pillH,
                bgColor = style.pillBg,
                borderColor = style.dpadBorder,
                textColor = style.labelColor,
                pressed = (activeTouchMask and NativeCore.KEY_START) != 0,
            )

            // GBA Shoulder L / R Triggers
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
                    pressed = (activeTouchMask and NativeCore.KEY_L) != 0,
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
                    pressed = (activeTouchMask and NativeCore.KEY_R) != 0,
                )
            }
        }

        // Quick Action Strip (Fast Forward, Rewind, Quick Save, Quick Load, Menu)
        if (profile.showQuickStrip) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xB3090C14))
                    .border(1.dp, style.glow.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                QuickStripChip(Icons.Filled.History, false, Color(0xFF94A3B8), onRewindTap)
                QuickStripChip(Icons.Filled.FastForward, fastForwardActive, style.glow, onToggleFastForward)
                QuickStripChip(Icons.Filled.Save, false, Color(0xFF38BDF8), onQuickSave)
                QuickStripChip(Icons.Filled.Upload, false, Color(0xFFA855F7), onQuickLoad)
                QuickStripChip(Icons.Filled.Menu, false, Color.White, onOpenMenu)
            }
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0x99090C14))
                    .border(1.dp, style.glow.copy(alpha = 0.55f), CircleShape)
                    .clickable(onClick = onOpenMenu),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Menu, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun QuickStripChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(if (active) tint.copy(alpha = 0.25f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (active) tint else Color(0xFFE2E8F0), modifier = Modifier.size(17.dp))
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
    pressed: Boolean,
) {
    val density = LocalDensity.current
    val diameterDp = with(density) { (radius * 2f).toDp() }
    val scale = if (pressed) 0.90f else 1.0f
    Box(
        modifier = Modifier
            .offset { IntOffset((center.x - radius).roundToInt(), (center.y - radius).roundToInt()) }
            .size(diameterDp)
            .shadow(if (pressed) 2.dp else 8.dp, CircleShape, spotColor = borderGlow)
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    if (pressed) listOf(bottomColor, topColor) else listOf(topColor, bottomColor)
                )
            )
            .border(if (pressed) 2.5.dp else 1.6.dp, borderGlow.copy(alpha = if (pressed) 1f else 0.72f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontFamily = SpaceGrotesk,
            fontSize = (18f * scale).sp,
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
    pressed: Boolean,
) {
    val density = LocalDensity.current
    val wDp = with(density) { widthPx.toDp() }
    val hDp = with(density) { heightPx.toDp() }
    Box(
        modifier = Modifier
            .offset { IntOffset((center.x - widthPx / 2f).roundToInt(), (center.y - heightPx / 2f).roundToInt()) }
            .size(wDp, hDp)
            .rotate(-14f)
            .clip(RoundedCornerShape(50))
            .background(if (pressed) borderColor.copy(alpha = 0.65f) else bgColor)
            .border(1.2.dp, borderColor.copy(alpha = 0.85f), RoundedCornerShape(50)),
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
    pressed: Boolean,
) {
    val density = LocalDensity.current
    val wDp = with(density) { widthPx.toDp() }
    val hDp = with(density) { heightPx.toDp() }
    Box(
        modifier = Modifier
            .offset { IntOffset((center.x - widthPx / 2f).roundToInt(), (center.y - heightPx / 2f).roundToInt()) }
            .size(wDp, hDp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(topColor, bottomColor)))
            .border(if (pressed) 2.2.dp else 1.4.dp, borderGlow.copy(alpha = if (pressed) 1f else 0.72f), RoundedCornerShape(14.dp)),
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
