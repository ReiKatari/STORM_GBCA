package com.stormgbca.emulator.ui.emulator.skin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Devices
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import com.stormgbca.emulator.R
import com.stormgbca.emulator.ui.common.UnifiedBackButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.stormgbca.emulator.data.GbcaPrefs
import com.stormgbca.emulator.model.ConsoleSystem
import com.stormgbca.emulator.ui.theme.SpaceGrotesk
import com.stormgbca.emulator.ui.theme.WatermelonMono
import com.stormgbca.emulator.ui.theme.watermelon

@Composable
fun ConsoleSkinPickerDialog(
    initialSystem: ConsoleSystem,
    onDismiss: () -> Unit,
) {
    val colors = watermelon
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(initialSystem) }

    val selectedKey = when (activeTab) {
        ConsoleSystem.GB -> GbcaPrefs.gbSkinState.value
        ConsoleSystem.GBC -> GbcaPrefs.gbcSkinState.value
        ConsoleSystem.GBA -> GbcaPrefs.gbaSkinState.value
    }
    val dimming = when (activeTab) {
        ConsoleSystem.GB -> GbcaPrefs.gbSkinDimmingState.intValue
        ConsoleSystem.GBC -> GbcaPrefs.gbcSkinDimmingState.intValue
        ConsoleSystem.GBA -> GbcaPrefs.gbaSkinDimmingState.intValue
    }
    val skins = remember(activeTab) { GbcaSkinId.forSystem(activeTab) }

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
                // Header
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
                        Icon(Icons.Filled.Devices, contentDescription = null, tint = colors.green, modifier = Modifier.size(19.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.skin_title), color = colors.text, fontFamily = SpaceGrotesk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.skin_subtitle), color = colors.text2, fontSize = 11.sp)
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

                // Night dimming slider
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.skin_brightness_fmt, activeTab.label), color = colors.text2, fontSize = 12.sp)
                        Text("$dimming%", color = colors.green, fontFamily = WatermelonMono, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = dimming.toFloat(),
                        onValueChange = { GbcaPrefs.setSkinDimming(activeTab, it.toInt()) },
                        valueRange = 30f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = colors.green,
                            activeTrackColor = colors.green,
                            inactiveTrackColor = colors.surface3,
                        ),
                    )
                }

                // 2-Column Live Canvas Preview Grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(skins, key = { "${activeTab.name}_${it.key}" }) { skin ->
                        val isSelected = skin.key.equals(selectedKey, ignoreCase = true)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) colors.green.copy(alpha = 0.14f) else colors.surface2)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) colors.green else colors.line,
                                    shape = RoundedCornerShape(14.dp),
                                )
                                .clickable {
                                    GbcaPrefs.setSkinKey(activeTab, skin.key)
                                }
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.85f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF0A0E17))
                                    .border(0.8.dp, colors.line, RoundedCornerShape(10.dp)),
                            ) {
                                SkinMiniPreview(
                                    system = activeTab,
                                    skinId = skin,
                                    dimming = dimming,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(colors.green),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = skin.localizedLabel(context),
                                color = if (isSelected) colors.green else colors.text,
                                fontFamily = SpaceGrotesk,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                minLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 14.sp,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    UnifiedBackButton(onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun SkinMiniPreview(
    system: ConsoleSystem,
    skinId: GbcaSkinId,
    dimming: Int,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier) {
        val wPx = with(density) { maxWidth.toPx() }.toInt().coerceAtLeast(1)
        val hPx = with(density) { maxHeight.toPx() }.toInt().coerceAtLeast(1)
        val natW = if (system == ConsoleSystem.GBA) 240 else 160
        val natH = if (system == ConsoleSystem.GBA) 160 else 144
        val rect = remember(wPx, hPx, system, skinId) {
            computeGameViewportRect(wPx, hPx, system, skinId, 0, natW, natH)
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF0F2B46), Color(0xFF1D4E89), Color(0xFF38BDF8)),
                    startY = rect.top,
                    endY = rect.bottom,
                ),
                topLeft = Offset(rect.left, rect.top),
                size = Size(rect.width, rect.height),
            )
        }
        ConsoleSkinOverlayView(
            skinId = skinId,
            screenRect = rect,
            dimmingPercent = dimming,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
