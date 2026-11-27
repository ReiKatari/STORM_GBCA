package com.stormgbca.emulator.ui.settings

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormgbca.emulator.R
import com.stormgbca.emulator.data.GbcaPrefs
import com.stormgbca.emulator.model.ConsoleSystem
import com.stormgbca.emulator.model.PhysicalAction
import com.stormgbca.emulator.ui.emulator.overlay.ButtonThemeManager
import com.stormgbca.emulator.ui.emulator.overlay.GbcaButtonStyle
import com.stormgbca.emulator.ui.theme.SpaceGrotesk
import com.stormgbca.emulator.ui.theme.watermelon

private val CONSOLE_ACTIONS = listOf(
    PhysicalAction.UP,
    PhysicalAction.DOWN,
    PhysicalAction.LEFT,
    PhysicalAction.RIGHT,
    PhysicalAction.A,
    PhysicalAction.B,
    PhysicalAction.L,
    PhysicalAction.R,
    PhysicalAction.START,
    PhysicalAction.SELECT,
)

private val HOTKEY_ACTIONS = listOf(
    PhysicalAction.FAST_FORWARD,
    PhysicalAction.REWIND,
    PhysicalAction.QUICK_SAVE,
    PhysicalAction.QUICK_LOAD,
    PhysicalAction.SOLAR_UP,
    PhysicalAction.SOLAR_DOWN,
)

@Composable
fun PhysicalInputSetupContent(
    awaitingAction: PhysicalAction?,
    onStartBind: (PhysicalAction) -> Unit,
    onCancelBind: () -> Unit,
) {
    val colors = watermelon
    var refreshTick by remember { mutableIntStateOf(0) }
    val activeStyle = remember(GbcaPrefs.gbaOverlayState.value.buttonStyle) {
        ButtonThemeManager.resolve(GbcaPrefs.gbaOverlayState.value.buttonStyle)
    }

    val mappedCount = remember(refreshTick, awaitingAction) {
        PhysicalAction.entries.count { GbcaPrefs.getKeyCodeForAction(it) != 0 }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Pulsing listening banner when binding a key
        if (awaitingAction != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.greenDim)
                    .border(1.5.dp, colors.green, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                ConsoleButtonCap3D(
                    action = awaitingAction,
                    style = activeStyle,
                    size = 38.dp,
                    isHighlighted = true,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${actionLabel(awaitingAction)}: ${stringResource(R.string.input_press_any)}",
                        color = colors.green,
                        fontFamily = SpaceGrotesk,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.input_waiting_sub),
                        color = colors.text2,
                        fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surface2)
                        .border(1.dp, colors.line, RoundedCornerShape(8.dp))
                        .clickable(onClick = onCancelBind)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(stringResource(R.string.cancel), color = colors.text, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // STORM DS-Style 3D Summary Card (A, B, L, R Mini 3D Caps + Mapped Count + Auto-Map / Clear All)
        InputSummaryCard3D(
            mappedCount = mappedCount,
            totalCount = PhysicalAction.entries.size,
            style = activeStyle,
            onAutoMap = {
                GbcaPrefs.resetKeyMappings()
                refreshTick++
            },
            onClearAll = {
                GbcaPrefs.clearAllKeyMappings()
                refreshTick++
            },
        )

        // Section 1: Console Buttons (D-Pad, A, B, L, R, Start, Select)
        InputSectionHeader(
            title = stringResource(R.string.input_group_console),
            icon = Icons.Filled.SportsEsports,
            accent = activeStyle.glow,
        )
        CONSOLE_ACTIONS.forEach { action ->
            val keyCode = remember(refreshTick, awaitingAction) { GbcaPrefs.getKeyCodeForAction(action) }
            InputRow3D(
                action = action,
                keyCode = keyCode,
                style = activeStyle,
                isBeingConfigured = awaitingAction == action,
                onClick = { onStartBind(action) },
                onClearClick = {
                    GbcaPrefs.clearKeyCodeForAction(action)
                    refreshTick++
                },
            )
        }

        Spacer(Modifier.height(4.dp))

        // Section 2: Hotkeys & Sensors
        InputSectionHeader(
            title = stringResource(R.string.input_group_hotkeys),
            icon = Icons.Filled.Bolt,
            accent = colors.green,
        )
        HOTKEY_ACTIONS.forEach { action ->
            val keyCode = remember(refreshTick, awaitingAction) { GbcaPrefs.getKeyCodeForAction(action) }
            InputRow3D(
                action = action,
                keyCode = keyCode,
                style = activeStyle,
                isBeingConfigured = awaitingAction == action,
                onClick = { onStartBind(action) },
                onClearClick = {
                    GbcaPrefs.clearKeyCodeForAction(action)
                    refreshTick++
                },
            )
        }
    }
}

@Composable
private fun InputSummaryCard3D(
    mappedCount: Int,
    totalCount: Int,
    style: GbcaButtonStyle,
    onAutoMap: () -> Unit,
    onClearAll: () -> Unit,
) {
    val colors = watermelon
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.Black.copy(alpha = if (colors.isDark) 0.45f else 0.14f))
            .padding(bottom = 3.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Brush.verticalGradient(listOf(colors.surface2, colors.surface)))
                .border(1.2.dp, style.glow.copy(alpha = 0.45f), shape)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Mini3DCap("A", style.btnATop to style.btnABottom, style.glow)
                    Mini3DCap("B", style.btnBTop to style.btnBBottom, style.glow)
                    Mini3DCap("L", style.shoulderTop to style.shoulderBottom, style.glow)
                    Mini3DCap("R", style.shoulderTop to style.shoulderBottom, style.glow)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.ctrl_gamepad),
                        color = colors.text,
                        fontFamily = SpaceGrotesk,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.greenDim)
                            .border(1.dp, colors.green.copy(alpha = 0.40f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.input_mapped_count_fmt, mappedCount, totalCount),
                            color = colors.green,
                            fontFamily = SpaceGrotesk,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Elevated3DActionButton(
                    label = stringResource(R.string.input_auto_map),
                    icon = Icons.Filled.AutoFixHigh,
                    accent = colors.green,
                    onClick = onAutoMap,
                    modifier = Modifier.weight(1f),
                )
                Elevated3DActionButton(
                    label = stringResource(R.string.input_clear_all),
                    icon = Icons.Filled.DeleteOutline,
                    accent = colors.red,
                    onClick = onClearAll,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Elevated3DActionButton(
    label: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = watermelon
    val shape = RoundedCornerShape(11.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(accent.copy(alpha = if (colors.isDark) 0.25f else 0.16f))
            .padding(bottom = 2.5.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.18f), colors.surface2)))
                .border(1.1.dp, accent.copy(alpha = 0.55f), shape)
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                color = colors.text,
                fontFamily = SpaceGrotesk,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Mini3DCap(
    letter: String,
    capColors: Pair<Color, Color>,
    borderGlow: Color,
) {
    val labelColor = if (capColors.first.luminance() > 0.62f) Color(0xFF0F172A) else Color(0xFFF8FAFC)
    Box(
        modifier = Modifier
            .size(25.dp)
            .clip(CircleShape)
            .background(capColors.second.copy(alpha = 0.78f))
            .padding(bottom = 1.5.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(capColors.first, capColors.second)))
                .border(1.2.dp, borderGlow.copy(alpha = 0.85f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(0.75f)
                    .fillMaxHeight(0.42f)
                    .padding(top = 1.5.dp)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.42f), Color.Transparent))),
            )
            Text(
                text = letter,
                color = labelColor,
                fontFamily = SpaceGrotesk,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@Composable
private fun InputSectionHeader(
    title: String,
    icon: ImageVector,
    accent: Color,
) {
    val colors = watermelon
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(accent.copy(alpha = 0.18f))
                .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
        }
        Text(
            text = title,
            color = colors.text,
            fontFamily = SpaceGrotesk,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.45f), Color.Transparent))),
        )
    }
}

private data class CapSpec(
    val colors: Pair<Color, Color>,
    val shape: Shape,
    val textLabel: String?,
    val icon: ImageVector?,
    val isFace: Boolean = false,
)

@Composable
private fun ConsoleButtonCap3D(
    action: PhysicalAction,
    style: GbcaButtonStyle,
    size: Dp,
    isHighlighted: Boolean = false,
) {
    val colors = watermelon
    val neutralTop = if (colors.isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
    val neutralBot = if (colors.isDark) Color(0xFF1E293B) else Color(0xFFCBD5E1)
    val shoulderTop = style.shoulderTop
    val shoulderBot = style.shoulderBottom

    val spec = when (action) {
        PhysicalAction.A -> CapSpec(style.btnATop to style.btnABottom, CircleShape, "A", null, isFace = true)
        PhysicalAction.B -> CapSpec(style.btnBTop to style.btnBBottom, CircleShape, "B", null, isFace = true)
        PhysicalAction.UP -> CapSpec(neutralTop to neutralBot, RoundedCornerShape(10.dp), null, Icons.Filled.KeyboardArrowUp)
        PhysicalAction.DOWN -> CapSpec(neutralTop to neutralBot, RoundedCornerShape(10.dp), null, Icons.Filled.KeyboardArrowDown)
        PhysicalAction.LEFT -> CapSpec(neutralTop to neutralBot, RoundedCornerShape(10.dp), null, Icons.Filled.KeyboardArrowLeft)
        PhysicalAction.RIGHT -> CapSpec(neutralTop to neutralBot, RoundedCornerShape(10.dp), null, Icons.Filled.KeyboardArrowRight)
        PhysicalAction.L -> CapSpec(shoulderTop to shoulderBot, RoundedCornerShape(10.dp), "L", null)
        PhysicalAction.R -> CapSpec(shoulderTop to shoulderBot, RoundedCornerShape(10.dp), "R", null)
        PhysicalAction.START -> CapSpec(neutralTop to neutralBot, RoundedCornerShape(10.dp), "ST", null)
        PhysicalAction.SELECT -> CapSpec(neutralTop to neutralBot, RoundedCornerShape(10.dp), "SE", null)
        PhysicalAction.FAST_FORWARD -> CapSpec(colors.surface3 to colors.surface2, RoundedCornerShape(11.dp), null, Icons.Filled.FastForward)
        PhysicalAction.REWIND -> CapSpec(colors.surface3 to colors.surface2, RoundedCornerShape(11.dp), null, Icons.Filled.FastRewind)
        PhysicalAction.QUICK_SAVE -> CapSpec(colors.surface3 to colors.surface2, RoundedCornerShape(11.dp), null, Icons.Filled.Save)
        PhysicalAction.QUICK_LOAD -> CapSpec(colors.surface3 to colors.surface2, RoundedCornerShape(11.dp), null, Icons.Filled.FolderOpen)
        PhysicalAction.SOLAR_UP -> CapSpec(colors.surface3 to colors.surface2, RoundedCornerShape(11.dp), null, Icons.Filled.WbSunny)
        PhysicalAction.SOLAR_DOWN -> CapSpec(colors.surface3 to colors.surface2, RoundedCornerShape(11.dp), null, Icons.Filled.BrightnessLow)
    }

    val isConsole = action in CONSOLE_ACTIONS
    val rimColor = when {
        isHighlighted -> colors.green
        spec.isFace -> style.glow.copy(alpha = 0.88f)
        isConsole -> style.glow.copy(alpha = 0.50f)
        else -> colors.green.copy(alpha = 0.45f)
    }
    val fgColor = when {
        spec.isFace || isConsole -> if (spec.colors.first.luminance() > 0.62f) Color(0xFF0F172A) else Color(0xFFF8FAFC)
        else -> colors.green
    }

    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        if (spec.isFace || isHighlighted) {
            val glowColor = if (isHighlighted) colors.green else style.glow
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.radialGradient(listOf(glowColor.copy(alpha = 0.34f), Color.Transparent)), spec.shape),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp)
                .clip(spec.shape)
                .background(spec.colors.second.copy(alpha = 0.85f))
                .padding(bottom = 2.5.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(spec.shape)
                    .background(Brush.verticalGradient(listOf(spec.colors.first, spec.colors.second)))
                    .border(if (isHighlighted) 1.8.dp else 1.2.dp, rimColor, spec.shape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(0.78f)
                        .fillMaxHeight(0.45f)
                        .padding(top = 2.dp)
                        .clip(spec.shape)
                        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.36f), Color.Transparent))),
                )
                if (spec.textLabel != null) {
                    Text(
                        text = spec.textLabel,
                        color = fgColor,
                        fontFamily = SpaceGrotesk,
                        fontSize = if (spec.textLabel.length > 1) 11.5.sp else 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                } else if (spec.icon != null) {
                    Icon(spec.icon, contentDescription = null, tint = fgColor, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun InputRow3D(
    action: PhysicalAction,
    keyCode: Int,
    style: GbcaButtonStyle,
    isBeingConfigured: Boolean,
    onClick: () -> Unit,
    onClearClick: () -> Unit,
) {
    val colors = watermelon
    val shape = RoundedCornerShape(14.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.Black.copy(alpha = if (colors.isDark) 0.38f else 0.10f))
            .padding(bottom = 2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        if (isBeingConfigured) listOf(colors.surface3, colors.surface2) else listOf(colors.surface2, colors.surface)
                    )
                )
                .border(
                    width = if (isBeingConfigured) 2.dp else 1.dp,
                    color = if (isBeingConfigured) colors.green else colors.line,
                    shape = shape,
                )
                .clickable(onClick = onClick)
                .padding(start = 12.dp, top = 10.dp, end = 10.dp, bottom = 10.dp),
        ) {
            ConsoleButtonCap3D(
                action = action,
                style = style,
                size = 42.dp,
                isHighlighted = isBeingConfigured,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = actionLabel(action),
                    color = colors.text,
                    fontFamily = SpaceGrotesk,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (isBeingConfigured) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(colors.greenDim)
                            .border(1.dp, colors.green, RoundedCornerShape(7.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.input_press_any),
                            color = colors.green,
                            fontFamily = SpaceGrotesk,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                } else if (keyCode == 0) {
                    UnmappedDashedChip(stringResource(R.string.input_not_set))
                } else {
                    PhysicalKeycapChip3D(keyCode = keyCode)
                }
            }

            if (keyCode != 0 && !isBeingConfigured) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(colors.surface3.copy(alpha = 0.72f))
                        .border(1.dp, colors.line, CircleShape)
                        .clickable(onClick = onClearClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = null,
                        tint = colors.text,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun UnmappedDashedChip(text: String) {
    val colors = watermelon
    val dashColor = colors.text2.copy(alpha = 0.55f)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(colors.surface.copy(alpha = 0.45f))
            .drawBehind {
                drawRoundRect(
                    color = dashColor,
                    cornerRadius = CornerRadius(7.dp.toPx(), 7.dp.toPx()),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f),
                    ),
                )
            }
            .padding(horizontal = 9.dp, vertical = 3.5.dp),
    ) {
        Text(text = text, color = colors.text2, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun PhysicalKeycapChip3D(keyCode: Int) {
    val colors = watermelon
    val chipShape = RoundedCornerShape(7.dp)
    val (icon, label) = remember(keyCode) { formatKeyCodeChip(keyCode) }

    Box(
        modifier = Modifier
            .clip(chipShape)
            .background(Color.Black.copy(alpha = if (colors.isDark) 0.55f else 0.16f))
            .padding(bottom = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .clip(chipShape)
                .background(Brush.verticalGradient(listOf(colors.surface3, colors.surface2)))
                .border(1.dp, colors.green.copy(alpha = 0.42f), chipShape)
                .padding(horizontal = 8.dp, vertical = 3.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(icon, contentDescription = null, tint = colors.green, modifier = Modifier.size(13.dp))
            Text(
                text = label,
                color = colors.text,
                fontFamily = SpaceGrotesk,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun formatKeyCodeChip(keyCode: Int): Pair<ImageVector, String> {
    val raw = KeyEvent.keyCodeToString(keyCode)
    val isGamepad = raw.contains("BUTTON_") || raw.contains("DPAD_")
    val clean = raw
        .replace("KEYCODE_", "")
        .replace("BUTTON_", "")
        .replace("_", " ")
        .trim()
        .ifBlank { "#$keyCode" }
    val icon = if (isGamepad) Icons.Filled.Gamepad else Icons.Filled.Keyboard
    return icon to clean
}

@Composable
private fun actionLabel(action: PhysicalAction): String = when (action) {
    PhysicalAction.UP -> stringResource(R.string.key_up)
    PhysicalAction.DOWN -> stringResource(R.string.key_down)
    PhysicalAction.LEFT -> stringResource(R.string.key_left)
    PhysicalAction.RIGHT -> stringResource(R.string.key_right)
    PhysicalAction.A -> stringResource(R.string.key_a)
    PhysicalAction.B -> stringResource(R.string.key_b)
    PhysicalAction.L -> stringResource(R.string.key_l)
    PhysicalAction.R -> stringResource(R.string.key_r)
    PhysicalAction.START -> stringResource(R.string.key_start)
    PhysicalAction.SELECT -> stringResource(R.string.key_select)
    PhysicalAction.FAST_FORWARD -> stringResource(R.string.key_ff)
    PhysicalAction.REWIND -> stringResource(R.string.key_rewind)
    PhysicalAction.QUICK_SAVE -> stringResource(R.string.key_qsave)
    PhysicalAction.QUICK_LOAD -> stringResource(R.string.key_qload)
    PhysicalAction.SOLAR_UP -> stringResource(R.string.key_solar_up)
    PhysicalAction.SOLAR_DOWN -> stringResource(R.string.key_solar_down)
}
