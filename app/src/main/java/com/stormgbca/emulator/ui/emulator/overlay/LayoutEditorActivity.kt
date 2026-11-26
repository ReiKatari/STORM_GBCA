package com.stormgbca.emulator.ui.emulator.overlay

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stormgbca.emulator.data.GbcaPrefs
import com.stormgbca.emulator.model.ConsoleSystem
import com.stormgbca.emulator.model.CustomControlPos
import com.stormgbca.emulator.model.OverlayLayoutPreset
import com.stormgbca.emulator.ui.theme.AppThemeManager
import com.stormgbca.emulator.ui.theme.SpaceGrotesk
import com.stormgbca.emulator.ui.theme.StormWatermelonTheme
import com.stormgbca.emulator.ui.theme.WatermelonMono
import com.stormgbca.emulator.ui.theme.watermelon
import kotlin.math.roundToInt

class LayoutEditorActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppThemeManager.applyEdgeToEdgeTheme(this)
        val systemName = intent.getStringExtra("system") ?: ConsoleSystem.GBA.name
        val system = runCatching { ConsoleSystem.valueOf(systemName) }.getOrDefault(ConsoleSystem.GBA)

        setContent {
            StormWatermelonTheme {
                LayoutEditorScreen(
                    system = system,
                    onDone = { finish() },
                )
            }
        }
    }
}

@Composable
private fun LayoutEditorScreen(
    system: ConsoleSystem,
    onDone: () -> Unit,
) {
    val colors = watermelon
    var profile by remember { mutableStateOf(GbcaPrefs.getOverlayProfile(system)) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF05070E))
            .systemBarsPadding(),
    ) {
        val w = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val h = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        // Mock game viewport guide
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 56.dp)
                .size(width = 240.dp, height = 160.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0F172A))
                .border(1.5.dp, colors.green.copy(alpha = 0.55f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("Экран ${system.label}", color = colors.text2, fontFamily = WatermelonMono, fontSize = 12.sp)
        }

        // Top controls bar
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface.copy(alpha = 0.92f))
                .border(1.dp, colors.line, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text("Редактор (${system.label})", color = colors.text, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surface2)
                    .clickable {
                        val def = GbcaPrefs.defaultOverlayFor(system)
                        profile = def
                        GbcaPrefs.updateOverlayProfile(system) { def }
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text("Сброс", color = colors.text2, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.green)
                    .clickable {
                        GbcaPrefs.updateOverlayProfile(system) {
                            profile.copy(layoutPreset = OverlayLayoutPreset.CUSTOM.key)
                        }
                        onDone()
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text("Готово", color = Color(0xFF05060A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        DraggableNode(
            label = "Крестовина",
            pos = profile.dpadPos,
            containerW = w,
            containerH = h,
            sizeDp = 110,
            accent = colors.green,
            onMove = { next -> profile = profile.copy(dpadPos = next, layoutPreset = OverlayLayoutPreset.CUSTOM.key) },
        )

        DraggableNode(
            label = "A / B",
            pos = profile.abPos,
            containerW = w,
            containerH = h,
            sizeDp = 110,
            accent = colors.red,
            onMove = { next -> profile = profile.copy(abPos = next, layoutPreset = OverlayLayoutPreset.CUSTOM.key) },
        )

        DraggableNode(
            label = "START / SELECT",
            pos = profile.startSelectPos,
            containerW = w,
            containerH = h,
            sizeDp = 96,
            accent = colors.green,
            onMove = { next -> profile = profile.copy(startSelectPos = next, layoutPreset = OverlayLayoutPreset.CUSTOM.key) },
        )

        if (profile.showShoulders || system == ConsoleSystem.GBA) {
            DraggableNode(
                label = "L",
                pos = profile.lPos,
                containerW = w,
                containerH = h,
                sizeDp = 76,
                accent = colors.green,
                onMove = { next -> profile = profile.copy(lPos = next, layoutPreset = OverlayLayoutPreset.CUSTOM.key) },
            )
            DraggableNode(
                label = "R",
                pos = profile.rPos,
                containerW = w,
                containerH = h,
                sizeDp = 76,
                accent = colors.green,
                onMove = { next -> profile = profile.copy(rPos = next, layoutPreset = OverlayLayoutPreset.CUSTOM.key) },
            )
        }
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
            .background(accent.copy(alpha = 0.22f))
            .border(2.dp, accent, CircleShape)
            .pointerInput(containerW, containerH, pos) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val nx = ((pos.xFraction * containerW + dragAmount.x) / containerW).coerceIn(0.06f, 0.94f)
                    val ny = ((pos.yFraction * containerH + dragAmount.y) / containerH).coerceIn(0.08f, 0.94f)
                    onMove(pos.copy(xFraction = nx, yFraction = ny))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontFamily = WatermelonMono, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
