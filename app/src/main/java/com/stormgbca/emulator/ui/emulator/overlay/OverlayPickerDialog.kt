package com.stormgbca.emulator.ui.emulator.overlay

import android.content.Intent
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.stormgbca.emulator.data.GbcaPrefs
import com.stormgbca.emulator.model.ConsoleSystem
import com.stormgbca.emulator.model.OverlayLayoutPreset
import com.stormgbca.emulator.ui.common.WatermelonSwitch
import com.stormgbca.emulator.ui.theme.SpaceGrotesk
import com.stormgbca.emulator.ui.theme.WatermelonMono
import com.stormgbca.emulator.ui.theme.watermelon
import ru.stormgames.gbca.NativeCore

private val SHADER_OPTIONS = listOf(
    NativeCore.FILTER_RGB_SUBPIXEL to "SkyEmu ЖК-субпиксели",
    NativeCore.FILTER_LCD_GRID to "DMG Оливковая матрица / Сетка",
    NativeCore.FILTER_SHARP_BILINEAR to "Чёткий билинейный (AGS-101)",
    NativeCore.FILTER_CRT_SCANLINES to "CRT Строки развёртки",
    NativeCore.FILTER_XBRZ_LITE to "xBRZ-Lite Сглаживание",
    NativeCore.FILTER_NEAREST to "Чёткие пиксели (Nearest)",
    NativeCore.FILTER_BILINEAR to "Мягкое сглаживание (Bilinear)",
)

@Composable
fun OverlayPickerDialog(
    initialSystem: ConsoleSystem,
    onDismiss: () -> Unit,
) {
    val colors = watermelon
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(initialSystem) }

    val profile = when (activeTab) {
        ConsoleSystem.GB -> GbcaPrefs.gbOverlayState.value
        ConsoleSystem.GBC -> GbcaPrefs.gbcOverlayState.value
        ConsoleSystem.GBA -> GbcaPrefs.gbaOverlayState.value
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = colors.surface,
            elevation = 20.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.line, RoundedCornerShape(18.dp)),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.greenDim)
                            .border(1.dp, colors.green.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Gamepad, contentDescription = null, tint = colors.green, modifier = Modifier.size(19.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Управление и оверлей", color = colors.text, fontFamily = SpaceGrotesk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Независимые настройки для GB, GBC и GBA", color = colors.text2, fontSize = 11.sp)
                    }
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surface2)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = null, tint = colors.text2, modifier = Modifier.size(16.dp))
                    }
                }

                // [GB] [GBC] [GBA] Tabs
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                ) {
                    ConsoleSystem.entries.forEach { sys ->
                        val selected = sys == activeTab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) colors.green else colors.surface2)
                                .border(1.dp, if (selected) colors.green else colors.line, RoundedCornerShape(10.dp))
                                .clickable { activeTab = sys }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = sys.label,
                                color = if (selected) Color(0xFF05060A) else colors.text,
                                fontFamily = WatermelonMono,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 490.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Open Drag-and-Drop Layout Editor
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.green)
                            .clickable {
                                val intent = Intent(context, LayoutEditorActivity::class.java).apply {
                                    putExtra("system", activeTab.name)
                                }
                                context.startActivity(intent)
                            }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.OpenWith, contentDescription = null, tint = Color(0xFF05060A), modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Редактор позиций кнопок (${activeTab.label})", color = Color(0xFF05060A), fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    // Layout Presets
                    Text("Раскладка кнопок", color = colors.text2, fontFamily = WatermelonMono, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OverlayLayoutPreset.entries.forEach { preset ->
                            val selected = profile.layoutPreset.equals(preset.key, ignoreCase = true)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) colors.greenDim else colors.surface2)
                                    .border(1.dp, if (selected) colors.green else colors.line, RoundedCornerShape(10.dp))
                                    .clickable {
                                        GbcaPrefs.updateOverlayProfile(activeTab) { it.copy(layoutPreset = preset.key) }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                            ) {
                                Text(preset.labelRu, color = if (selected) colors.green else colors.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = colors.green, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // Button Styles
                    Text("Стиль кнопок (${activeTab.label})", color = colors.text2, fontFamily = WatermelonMono, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ButtonThemeManager.STYLES.forEach { st ->
                            val selected = profile.buttonStyle.equals(st.key, ignoreCase = true)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) colors.greenDim else colors.surface2)
                                    .border(1.dp, if (selected) colors.green else colors.line, RoundedCornerShape(10.dp))
                                    .clickable {
                                        GbcaPrefs.updateOverlayProfile(activeTab) { it.copy(buttonStyle = st.key) }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(Brush.verticalGradient(listOf(st.btnATop, st.btnABottom)))
                                            .border(1.dp, st.glow, CircleShape),
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(Brush.verticalGradient(listOf(st.btnBTop, st.btnBBottom)))
                                            .border(1.dp, st.glow, CircleShape),
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(st.labelRu, color = if (selected) colors.green else colors.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = colors.green, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // Shader Overlay
                    Text("Экранный фильтр (${activeTab.label})", color = colors.text2, fontFamily = WatermelonMono, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SHADER_OPTIONS.forEach { (mode, title) ->
                            val selected = profile.shaderOverlay == mode
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) colors.greenDim else colors.surface2)
                                    .border(1.dp, if (selected) colors.green else colors.line, RoundedCornerShape(10.dp))
                                    .clickable {
                                        GbcaPrefs.updateOverlayProfile(activeTab) { it.copy(shaderOverlay = mode) }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Text(title, color = if (selected) colors.green else colors.text, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                                if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = colors.green, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // Sliders & Toggles
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Прозрачность кнопок", color = colors.text, fontSize = 13.sp)
                        Text("${profile.opacityPercent}%", color = colors.green, fontFamily = WatermelonMono, fontSize = 12.sp)
                    }
                    Slider(
                        value = profile.opacityPercent.toFloat(),
                        onValueChange = { v -> GbcaPrefs.updateOverlayProfile(activeTab) { it.copy(opacityPercent = v.toInt().coerceIn(20, 100)) } },
                        valueRange = 20f..100f,
                        colors = SliderDefaults.colors(thumbColor = colors.green, activeTrackColor = colors.green, inactiveTrackColor = colors.surface3),
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Масштаб кнопок", color = colors.text, fontSize = 13.sp)
                        Text("${profile.scalePercent}%", color = colors.green, fontFamily = WatermelonMono, fontSize = 12.sp)
                    }
                    Slider(
                        value = profile.scalePercent.toFloat(),
                        onValueChange = { v -> GbcaPrefs.updateOverlayProfile(activeTab) { it.copy(scalePercent = v.toInt().coerceIn(70, 140)) } },
                        valueRange = 70f..140f,
                        colors = SliderDefaults.colors(thumbColor = colors.green, activeTrackColor = colors.green, inactiveTrackColor = colors.surface3),
                    )

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Виброотклик при нажатии", color = colors.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        WatermelonSwitch(
                            checked = profile.hapticsEnabled,
                            onCheckedChange = { c -> GbcaPrefs.updateOverlayProfile(activeTab) { it.copy(hapticsEnabled = c) } },
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Показывать триггеры L / R", color = colors.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        WatermelonSwitch(
                            checked = profile.showShoulders,
                            onCheckedChange = { c -> GbcaPrefs.updateOverlayProfile(activeTab) { it.copy(showShoulders = c) } },
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Панель быстрого доступа", color = colors.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        WatermelonSwitch(
                            checked = profile.showQuickStrip,
                            onCheckedChange = { c -> GbcaPrefs.updateOverlayProfile(activeTab) { it.copy(showQuickStrip = c) } },
                        )
                    }
                }
            }
        }
    }
}
