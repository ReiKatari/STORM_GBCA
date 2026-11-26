package com.stormgbca.emulator.ui.emulator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.stormgbca.emulator.R
import com.stormgbca.emulator.model.ConsoleSystem
import com.stormgbca.emulator.ui.common.UnifiedBackButton
import com.stormgbca.emulator.ui.theme.SpaceGrotesk
import com.stormgbca.emulator.ui.theme.WatermelonMono
import com.stormgbca.emulator.ui.theme.watermelon
import ru.stormgames.gbca.NativeCore

data class RamSearchHit(
    val offset: Int,
    val value: Int,
)

@Composable
fun RamCheatFinderDialog(
    system: ConsoleSystem,
    onResetSearch: (region: Int, valueSize: Int) -> Int,
    onFilterSearch: (cmpOp: Int, targetVal: Int) -> Int,
    onFetchResults: () -> List<RamSearchHit>,
    onWriteValue: (region: Int, offset: Int, value: Int, sizeBytes: Int) -> Boolean,
    onAddAsCheat: (name: String, code: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = watermelon
    val context = LocalContext.current
    val regions = remember(system) {
        if (system == ConsoleSystem.GBA) {
            listOf(NativeCore.MEM_IWRAM to "IWRAM (32 KB)", NativeCore.MEM_EWRAM to "EWRAM (256 KB)", NativeCore.MEM_CART_RAM to "SRAM")
        } else {
            listOf(NativeCore.MEM_WRAM to "WRAM (8 KB)", NativeCore.MEM_HRAM to "HRAM", NativeCore.MEM_CART_RAM to "SRAM")
        }
    }
    var selectedRegion by remember { mutableIntStateOf(regions.first().first) }
    var valueSize by remember { mutableIntStateOf(1) }
    var valueInput by remember { mutableStateOf("0") }
    var writeInput by remember { mutableStateOf("99") }
    var matchCount by remember { mutableIntStateOf(-1) }
    var hits by remember { mutableStateOf<List<RamSearchHit>>(emptyList()) }
    var statusText by remember { mutableStateOf(context.getString(R.string.ram_hint_initial)) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            color = colors.bg,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 580.dp)
                .heightIn(max = 700.dp)
                .border(1.dp, colors.line, RoundedCornerShape(22.dp)),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.ram_title), color = colors.text, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(statusText, color = colors.green, fontFamily = WatermelonMono, fontSize = 11.sp)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    regions.forEach { (reg, lbl) ->
                        val active = selectedRegion == reg
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (active) colors.green else colors.surface2)
                                .clickable { selectedRegion = reg }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(lbl, color = if (active) Color(0xFF05060A) else colors.text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(1 to R.string.ram_8bit, 2 to R.string.ram_16bit, 4 to R.string.ram_32bit).forEach { (sz, lblRes) ->
                        val active = valueSize == sz
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (active) Color(0xFF38BDF8) else colors.surface2)
                                .clickable { valueSize = sz }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(stringResource(lblRes), color = if (active) Color(0xFF05060A) else colors.text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    val tfColors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = colors.text,
                        focusedBorderColor = colors.green,
                        unfocusedBorderColor = colors.line,
                        focusedLabelColor = colors.green,
                        unfocusedLabelColor = colors.text2,
                        backgroundColor = colors.surface2,
                    )
                    OutlinedTextField(
                        value = valueInput,
                        onValueChange = { valueInput = it },
                        label = { Text(stringResource(R.string.ram_search_val)) },
                        singleLine = true,
                        colors = tfColors,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = writeInput,
                        onValueChange = { writeInput = it },
                        label = { Text(stringResource(R.string.ram_write_val)) },
                        singleLine = true,
                        colors = tfColors,
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    RamBtn(stringResource(R.string.ram_new_search), colors.green, Color(0xFF05060A), Modifier.weight(1.2f)) {
                        val cnt = onResetSearch(selectedRegion, valueSize)
                        matchCount = cnt
                        hits = if (cnt in 1..60) onFetchResults() else emptyList()
                        statusText = context.getString(R.string.ram_snapshot_fmt, cnt)
                    }
                    RamBtn(stringResource(R.string.ram_eq), colors.surface2, colors.text, Modifier.weight(1f)) {
                        val v = valueInput.trim().toIntOrNull() ?: 0
                        matchCount = onFilterSearch(NativeCore.SEARCH_EQ, v)
                        hits = onFetchResults()
                        statusText = context.getString(R.string.ram_matches_fmt, matchCount)
                    }
                    RamBtn(stringResource(R.string.ram_gt), colors.surface2, colors.text, Modifier.weight(1f)) {
                        val v = valueInput.trim().toIntOrNull() ?: 0
                        matchCount = onFilterSearch(NativeCore.SEARCH_GT, v)
                        hits = onFetchResults()
                        statusText = context.getString(R.string.ram_matches_fmt, matchCount)
                    }
                    RamBtn(stringResource(R.string.ram_lt), colors.surface2, colors.text, Modifier.weight(1f)) {
                        val v = valueInput.trim().toIntOrNull() ?: 0
                        matchCount = onFilterSearch(NativeCore.SEARCH_LT, v)
                        hits = onFetchResults()
                        statusText = context.getString(R.string.ram_matches_fmt, matchCount)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    RamBtn(stringResource(R.string.ram_inc), colors.surface2, colors.green, Modifier.weight(1f)) {
                        matchCount = onFilterSearch(NativeCore.SEARCH_INCREASED, 0)
                        hits = onFetchResults()
                        statusText = context.getString(R.string.ram_inc_fmt, matchCount)
                    }
                    RamBtn(stringResource(R.string.ram_dec), colors.surface2, Color(0xFFF59E0B), Modifier.weight(1f)) {
                        matchCount = onFilterSearch(NativeCore.SEARCH_DECREASED, 0)
                        hits = onFetchResults()
                        statusText = context.getString(R.string.ram_dec_fmt, matchCount)
                    }
                    RamBtn(stringResource(R.string.ram_changed), colors.surface2, Color(0xFF38BDF8), Modifier.weight(1f)) {
                        matchCount = onFilterSearch(NativeCore.SEARCH_CHANGED, 0)
                        hits = onFetchResults()
                        statusText = context.getString(R.string.ram_changed_fmt, matchCount)
                    }
                    RamBtn(stringResource(R.string.ram_unchanged), colors.surface2, colors.text2, Modifier.weight(1f)) {
                        matchCount = onFilterSearch(NativeCore.SEARCH_UNCHANGED, 0)
                        hits = onFetchResults()
                        statusText = context.getString(R.string.ram_unchanged_fmt, matchCount)
                    }
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false).heightIn(min = 100.dp, max = 260.dp),
                ) {
                    items(hits, key = { it.offset }) { hit ->
                        val baseAddr = when {
                            system == ConsoleSystem.GBA && selectedRegion == NativeCore.MEM_EWRAM -> 0x02000000
                            system == ConsoleSystem.GBA -> 0x03000000
                            selectedRegion == NativeCore.MEM_HRAM -> 0xFF80
                            selectedRegion == NativeCore.MEM_CART_RAM -> 0xA000
                            else -> 0xC000
                        }
                        val fullAddr = baseAddr + hit.offset
                        val hexAddr = "0x" + fullAddr.toString(16).uppercase().padStart(if (system == ConsoleSystem.GBA) 8 else 4, '0')
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.surface)
                                .border(1.dp, colors.line, RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(hexAddr, color = colors.green, fontFamily = WatermelonMono, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(
                                    stringResource(R.string.ram_current_fmt, hit.value, hit.value.toString(16).uppercase()),
                                    color = colors.text2,
                                    fontFamily = WatermelonMono,
                                    fontSize = 11.sp,
                                )
                            }
                            RamBtn(stringResource(R.string.ram_write_btn), colors.green, Color(0xFF05060A), Modifier) {
                                val wv = writeInput.trim().toIntOrNull() ?: 0
                                if (onWriteValue(selectedRegion, hit.offset, wv, valueSize)) {
                                    statusText = context.getString(R.string.ram_written_fmt, wv, hexAddr)
                                    hits = onFetchResults()
                                }
                            }
                            Spacer(Modifier.width(6.dp))
                            RamBtn(stringResource(R.string.ram_add_cheat_btn), Color(0xFF7B42F6), Color.White, Modifier) {
                                val wv = writeInput.trim().toIntOrNull() ?: hit.value
                                val code = if (system == ConsoleSystem.GBA) {
                                    "${fullAddr.toString(16).uppercase().padStart(8, '0')}:${wv.toString(16).uppercase().padStart(valueSize * 2, '0')}"
                                } else {
                                    "01${(wv and 0xFF).toString(16).uppercase().padStart(2, '0')}${(fullAddr and 0xFF).toString(16).uppercase().padStart(2, '0')}${((fullAddr ushr 8) and 0xFF).toString(16).uppercase().padStart(2, '0')}"
                                }
                                onAddAsCheat("RAM $hexAddr", code)
                                statusText = context.getString(R.string.ram_cheat_added_fmt, code)
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    UnifiedBackButton(onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun RamBtn(label: String, bg: Color, fg: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
