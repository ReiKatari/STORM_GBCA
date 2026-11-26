package com.stormgbca.emulator.ui.emulator.overlay

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.OpenWith
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.documentfile.provider.DocumentFile
import com.stormgbca.emulator.R
import com.stormgbca.emulator.data.GbcaPrefs
import com.stormgbca.emulator.data.ShaderRepository
import com.stormgbca.emulator.model.ConsoleSystem
import com.stormgbca.emulator.model.OverlayLayoutPreset
import com.stormgbca.emulator.ui.common.UnifiedBackButton
import com.stormgbca.emulator.ui.common.WatermelonSwitch
import com.stormgbca.emulator.ui.theme.SpaceGrotesk
import com.stormgbca.emulator.ui.theme.WatermelonMono
import com.stormgbca.emulator.ui.theme.watermelon
import kotlinx.coroutines.launch
import ru.stormgames.gbca.NativeCore

private val SHADER_OPTIONS = listOf(
    NativeCore.FILTER_RGB_SUBPIXEL to R.string.filter_rgb_subpixel,
    NativeCore.FILTER_LCD_GRID to R.string.filter_lcd_grid,
    NativeCore.FILTER_SHARP_BILINEAR to R.string.filter_sharp_bilinear,
    NativeCore.FILTER_CRT_SCANLINES to R.string.filter_crt_scanlines,
    NativeCore.FILTER_XBRZ_LITE to R.string.filter_xbrz_lite,
    NativeCore.FILTER_NEAREST to R.string.filter_nearest,
    NativeCore.FILTER_BILINEAR to R.string.filter_bilinear,
)

private fun OverlayLayoutPreset.labelRes(): Int = when (this) {
    OverlayLayoutPreset.CLASSIC_HANDHELD -> R.string.preset_classic_handheld
    OverlayLayoutPreset.WIDE_GRIP -> R.string.preset_wide_grip
    OverlayLayoutPreset.COMPACT_MINIMAL -> R.string.preset_compact_minimal
    OverlayLayoutPreset.FLOATING_GLASS -> R.string.preset_floating_glass
    OverlayLayoutPreset.CUSTOM -> R.string.preset_custom
}

@Composable
fun OverlayPickerDialog(
    initialSystem: ConsoleSystem,
    onShaderChanged: ((ConsoleSystem, String) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val colors = watermelon
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var activeTab by remember { mutableStateOf(initialSystem) }
    var shaderListTick by remember { mutableIntStateOf(0) }

    val raPresets = remember(shaderListTick) { ShaderRepository.listPresets(context) }
    val librashaderReady = remember { runCatching { NativeCore.nativeIsLibrashaderAvailable() }.getOrDefault(false) }

    val shaderImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val docName = runCatching { DocumentFile.fromSingleUri(context, uri)?.name }.getOrNull()
                val imported = ShaderRepository.importCustomShader(context, uri, docName)
                if (imported != null) {
                    GbcaPrefs.updateOverlayProfile(activeTab) { it.copy(retroArchShaderPath = imported.path) }
                    onShaderChanged?.invoke(activeTab, imported.path)
                    shaderListTick++
                    Toast.makeText(context, context.getString(R.string.overlay_shader_added_fmt, imported.name), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, context.getString(R.string.overlay_shader_import_fail), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

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
                        Text(stringResource(R.string.overlay_title), color = colors.text, fontFamily = SpaceGrotesk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.overlay_subtitle), color = colors.text2, fontSize = 11.sp)
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
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Open Interactive Screen Size + Position + Buttons Editor
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
                            Text(
                                stringResource(R.string.overlay_editor_btn_fmt, activeTab.label),
                                color = Color(0xFF05060A),
                                fontFamily = SpaceGrotesk,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                            )
                        }
                    }

                    // RetroArch .slangp Shader Selector (librashader)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.overlay_ra_shader_fmt, activeTab.label),
                                color = colors.text2,
                                fontFamily = WatermelonMono,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                if (librashaderReady) stringResource(R.string.overlay_librashader_active) else stringResource(R.string.overlay_builtin_fallback),
                                color = if (librashaderReady) Color(0xFF22C55E) else colors.text2,
                                fontSize = 10.sp,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.greenDim)
                                .border(1.dp, colors.green.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .clickable { shaderImportLauncher.launch(arrayOf("*/*")) }
                                .padding(horizontal = 9.dp, vertical = 5.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Add, contentDescription = null, tint = colors.green, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.vid_import_shader), color = colors.green, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        raPresets.forEach { preset ->
                            val selected = profile.retroArchShaderPath == preset.path
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) colors.greenDim else colors.surface2)
                                    .border(1.dp, if (selected) colors.green else colors.line, RoundedCornerShape(10.dp))
                                    .clickable {
                                        GbcaPrefs.updateOverlayProfile(activeTab) { it.copy(retroArchShaderPath = preset.path) }
                                        onShaderChanged?.invoke(activeTab, preset.path)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(preset.name, color = if (selected) colors.green else colors.text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                    Text(preset.description, color = colors.text2, fontSize = 10.sp)
                                }
                                if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = colors.green, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // Layout Presets
                    Text(stringResource(R.string.overlay_layout_presets), color = colors.text2, fontFamily = WatermelonMono, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                                Text(stringResource(preset.labelRes()), color = if (selected) colors.green else colors.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = colors.green, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // Button Styles
                    Text(stringResource(R.string.overlay_button_style_fmt, activeTab.label), color = colors.text2, fontFamily = WatermelonMono, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                                Text(st.localizedLabel(), color = if (selected) colors.green else colors.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = colors.green, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // Built-in GLSL Shader Overlay
                    Text(stringResource(R.string.overlay_builtin_filter_fmt, activeTab.label), color = colors.text2, fontFamily = WatermelonMono, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SHADER_OPTIONS.forEach { (mode, titleRes) ->
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
                                Text(stringResource(titleRes), color = if (selected) colors.green else colors.text, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                                if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = colors.green, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // Sliders & Toggles
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.overlay_opacity), color = colors.text, fontSize = 13.sp)
                        Text("${profile.opacityPercent}%", color = colors.green, fontFamily = WatermelonMono, fontSize = 12.sp)
                    }
                    Slider(
                        value = profile.opacityPercent.toFloat(),
                        onValueChange = { v -> GbcaPrefs.updateOverlayProfile(activeTab) { it.copy(opacityPercent = v.toInt().coerceIn(20, 100)) } },
                        valueRange = 20f..100f,
                        colors = SliderDefaults.colors(thumbColor = colors.green, activeTrackColor = colors.green, inactiveTrackColor = colors.surface3),
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.overlay_scale), color = colors.text, fontSize = 13.sp)
                        Text("${profile.scalePercent}%", color = colors.green, fontFamily = WatermelonMono, fontSize = 12.sp)
                    }
                    Slider(
                        value = profile.scalePercent.toFloat(),
                        onValueChange = { v -> GbcaPrefs.updateOverlayProfile(activeTab) { it.copy(scalePercent = v.toInt().coerceIn(70, 140)) } },
                        valueRange = 70f..140f,
                        colors = SliderDefaults.colors(thumbColor = colors.green, activeTrackColor = colors.green, inactiveTrackColor = colors.surface3),
                    )

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.overlay_haptics), color = colors.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        WatermelonSwitch(
                            checked = profile.hapticsEnabled,
                            onCheckedChange = { c -> GbcaPrefs.updateOverlayProfile(activeTab) { it.copy(hapticsEnabled = c) } },
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.overlay_shoulders), color = colors.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        WatermelonSwitch(
                            checked = profile.showShoulders,
                            onCheckedChange = { c -> GbcaPrefs.updateOverlayProfile(activeTab) { it.copy(showShoulders = c) } },
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.overlay_quick_strip), color = colors.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        WatermelonSwitch(
                            checked = profile.showQuickStrip,
                            onCheckedChange = { c -> GbcaPrefs.updateOverlayProfile(activeTab) { it.copy(showQuickStrip = c) } },
                        )
                    }
                }

                // Bottom-centered STORM DS Back Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    UnifiedBackButton(onClick = onDismiss)
                }
            }
        }
    }
}
