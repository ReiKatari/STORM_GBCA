package com.stormgbca.emulator.ui.emulator

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.stormgbca.emulator.data.GbcaPrefs
import com.stormgbca.emulator.data.RaGameSession
import com.stormgbca.emulator.data.RomRepository
import com.stormgbca.emulator.model.CheatItem
import com.stormgbca.emulator.model.ConsoleSystem
import com.stormgbca.emulator.model.RomEntry
import com.stormgbca.emulator.ui.common.WatermelonSwitch
import com.stormgbca.emulator.ui.theme.SpaceGrotesk
import com.stormgbca.emulator.ui.theme.WatermelonMono
import com.stormgbca.emulator.ui.theme.watermelon
import ru.stormgames.gbca.NativeCore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private enum class PauseSubDialog {
    NONE,
    SLOTS,
    VIDEO,
    AUDIO_MIXER,
    CHEATS,
    SENSORS,
    CORE_ENGINE,
    ACHIEVEMENTS,
}

private data class PauseTile(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tint: Color,
    val onClick: () -> Unit,
)

@Composable
fun PauseMenuOverlay(
    rom: RomEntry,
    system: ConsoleSystem,
    activeCoreName: String,
    fastForwardActive: Boolean,
    rewindAvailableFrames: Int,
    raSession: RaGameSession?,
    onResume: () -> Unit,
    onQuickSave: () -> Unit,
    onQuickLoad: () -> Unit,
    onSaveSlot: (Int) -> Unit,
    onLoadSlot: (Int) -> Unit,
    onRewindStep: () -> Unit,
    onToggleFastForward: () -> Unit,
    onOpenSkinPicker: () -> Unit,
    onOpenOverlayPicker: () -> Unit,
    onConfigApplied: () -> Unit,
    onCheatsUpdated: (List<CheatItem>) -> Unit,
    onApplySoftPatch: () -> Unit,
    onTakeScreenshot: () -> Unit,
    onOpenSettings: () -> Unit,
    onReset: () -> Unit,
    onExit: () -> Unit,
) {
    val colors = watermelon
    var subDialog by remember { mutableStateOf(PauseSubDialog.NONE) }

    val tiles = listOf(
        PauseTile("Продолжить", "Вернуться в игру", Icons.Filled.PlayArrow, colors.green, onResume),
        PauseTile("Быстрое сохранение", "Слот быстрого доступа", Icons.Filled.Download, Color(0xFF38BDF8), { onQuickSave(); onResume() }),
        PauseTile("Быстрая загрузка", "Восстановить состояние", Icons.Filled.Upload, Color(0xFFA855F7), { onQuickLoad(); onResume() }),
        PauseTile("Слоты сохранений", "10 слотов со скриншотами", Icons.Filled.Layers, Color(0xFF818CF8), { subDialog = PauseSubDialog.SLOTS }),
        PauseTile("Перемотка назад", "$rewindAvailableFrames кадров", Icons.Filled.History, Color(0xFF2DD4BF), onRewindStep),
        PauseTile("Ускорение", if (fastForwardActive) "Включено" else "Выключено", Icons.Filled.FastForward, if (fastForwardActive) colors.green else colors.text2, onToggleFastForward),
        PauseTile("Корпус (${system.label})", "Выбрать 3D скин", Icons.Filled.Devices, Color(0xFFF472B6), onOpenSkinPicker),
        PauseTile("Оверлей (${system.label})", "Кнопки и раскладка", Icons.Filled.Gamepad, Color(0xFFFB923C), onOpenOverlayPicker),
        PauseTile("Видео и палитра", "Цветокоррекция и DMG", Icons.Filled.Palette, Color(0xFFFACC15), { subDialog = PauseSubDialog.VIDEO }),
        PauseTile("Аудиомикшер", "Громкость и 6 каналов", Icons.Filled.GraphicEq, Color(0xFF34D399), { subDialog = PauseSubDialog.AUDIO_MIXER }),
        PauseTile("Чит-коды", "GameShark / AR / Genie", Icons.Filled.Tune, Color(0xFFE879F9), { subDialog = PauseSubDialog.CHEATS }),
        PauseTile("Мягкий патч", "IPS / UPS / BPS", Icons.Filled.Build, Color(0xFF38BDF8), onApplySoftPatch),
        PauseTile("Скриншот", "Сохранить кадр в PNG", Icons.Filled.CameraAlt, Color(0xFF22D3EE), { onTakeScreenshot(); onResume() }),
        PauseTile("Датчики", "Солнце, гироскоп, вибрация", Icons.Filled.Sensors, Color(0xFFF59E0B), { subDialog = PauseSubDialog.SENSORS }),
        PauseTile("Ядро эмуляции", activeCoreName, Icons.Filled.Memory, Color(0xFF60A5FA), { subDialog = PauseSubDialog.CORE_ENGINE }),
        PauseTile("Достижения", if (raSession != null) "${raSession.achievements.size} достижений" else "RetroAchievements", Icons.Filled.EmojiEvents, Color(0xFFD4A017), { subDialog = PauseSubDialog.ACHIEVEMENTS }),
        PauseTile("Настройки", "Общие параметры", Icons.Filled.Settings, colors.text2, onOpenSettings),
        PauseTile("Перезапуск", "Сбросить консоль", Icons.Filled.RestartAlt, colors.red, onReset),
        PauseTile("Выход в библиотеку", "Сохранить SRAM и выйти", Icons.AutoMirrored.Filled.Logout, colors.red, onExit),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC04060D))
            .systemBarsPadding()
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = colors.surface.copy(alpha = 0.94f),
            elevation = 24.dp,
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxWidth()
                .border(1.2.dp, colors.line, RoundedCornerShape(22.dp)),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = rom.displayTitle,
                            color = colors.text,
                            fontFamily = SpaceGrotesk,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${rom.platformLabel}  |  $activeCoreName  |  ${rom.region}",
                            color = colors.text2,
                            fontFamily = WatermelonMono,
                            fontSize = 11.sp,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(colors.surface2)
                            .border(1.dp, colors.line, CircleShape)
                            .clickable(onClick = onResume),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = null, tint = colors.text, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(12.dp))

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 136.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp),
                    contentPadding = PaddingValues(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(tiles) { tile ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.surface2)
                                .border(1.dp, colors.line, RoundedCornerShape(14.dp))
                                .clickable(onClick = tile.onClick)
                                .padding(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(tile.tint.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(tile.icon, contentDescription = null, tint = tile.tint, modifier = Modifier.size(19.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(tile.title, color = colors.text, fontFamily = SpaceGrotesk, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(tile.subtitle, color = colors.text3, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }

    when (subDialog) {
        PauseSubDialog.SLOTS -> SaveSlotsDialog(rom = rom, onSaveSlot = onSaveSlot, onLoadSlot = { onLoadSlot(it); subDialog = PauseSubDialog.NONE; onResume() }, onDismiss = { subDialog = PauseSubDialog.NONE })
        PauseSubDialog.VIDEO -> VideoSettingsDialog(onConfigApplied = onConfigApplied, onDismiss = { subDialog = PauseSubDialog.NONE })
        PauseSubDialog.AUDIO_MIXER -> AudioMixerDialog(onConfigApplied = onConfigApplied, onDismiss = { subDialog = PauseSubDialog.NONE })
        PauseSubDialog.CHEATS -> CheatsDialog(rom = rom, onCheatsUpdated = onCheatsUpdated, onDismiss = { subDialog = PauseSubDialog.NONE })
        PauseSubDialog.SENSORS -> SensorsDialog(onConfigApplied = onConfigApplied, onDismiss = { subDialog = PauseSubDialog.NONE })
        PauseSubDialog.CORE_ENGINE -> CoreEngineDialog(system = system, onConfigApplied = onConfigApplied, onDismiss = { subDialog = PauseSubDialog.NONE })
        PauseSubDialog.ACHIEVEMENTS -> AchievementsDialog(session = raSession, onDismiss = { subDialog = PauseSubDialog.NONE })
        PauseSubDialog.NONE -> {}
    }
}

@Composable
private fun SaveSlotsDialog(
    rom: RomEntry,
    onSaveSlot: (Int) -> Unit,
    onLoadSlot: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = watermelon
    val context = LocalContext.current
    var refreshTick by remember { mutableIntStateOf(0) }
    val fmt = remember { SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = colors.surface,
            modifier = Modifier.fillMaxWidth().border(1.dp, colors.line, RoundedCornerShape(18.dp)),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Слоты сохранений", color = colors.text, fontFamily = SpaceGrotesk, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.Close, contentDescription = null, tint = colors.text2, modifier = Modifier.size(20.dp).clickable(onClick = onDismiss))
                }
                Spacer(Modifier.height(10.dp))
                Column(
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (slot in 1..10) {
                        val file = remember(refreshTick, slot) { RomRepository.getStateFile(context, rom, slot) }
                        val thumb = remember(refreshTick, slot) { RomRepository.getStateThumbFile(context, rom, slot) }
                        val exists = file.exists() && file.length() > 0L

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surface2)
                                .border(1.dp, colors.line, RoundedCornerShape(12.dp))
                                .padding(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 64.dp, height = 48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF0A0E17)),
                                contentAlignment = Alignment.Center,
                            ) {
                                val bmp = remember(refreshTick, slot) {
                                    if (thumb.exists()) BitmapFactory.decodeFile(thumb.absolutePath) else null
                                }
                                if (bmp != null) {
                                    Image(bitmap = bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                } else {
                                    Text("#$slot", color = colors.text3, fontFamily = WatermelonMono, fontSize = 12.sp)
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Слот $slot", color = colors.text, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    text = if (exists) fmt.format(Date(file.lastModified())) else "Пусто",
                                    color = colors.text3,
                                    fontFamily = WatermelonMono,
                                    fontSize = 11.sp,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.greenDim)
                                    .clickable {
                                        onSaveSlot(slot)
                                        refreshTick++
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Text("Сохранить", color = colors.green, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            if (exists) {
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.green)
                                        .clickable { onLoadSlot(slot) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                ) {
                                    Text("Загрузить", color = Color(0xFF05060A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoSettingsDialog(
    onConfigApplied: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = watermelon
    val cfg = GbcaPrefs.coreConfigState.value
    val paletteCount = remember { NativeCore.nativeGetDmgPaletteCount().coerceAtLeast(1) }
    val ccModes = listOf(
        NativeCore.CC_DISABLED to "Отключено",
        NativeCore.CC_CORRECT_CURVES to "Точные кривые",
        NativeCore.CC_MODERN_BALANCED to "Современный баланс",
        NativeCore.CC_MODERN_BOOST to "Современный яркий",
        NativeCore.CC_REDUCE_CONTRAST to "Сниженный контраст",
        NativeCore.CC_LOW_CONTRAST to "Низкий контраст",
        NativeCore.CC_MODERN_ACCURATE to "Точный современный",
        NativeCore.CC_GBA_AGB_AGS to "GBA AGB / AGS матрица",
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = colors.surface,
            modifier = Modifier.fillMaxWidth().border(1.dp, colors.line, RoundedCornerShape(18.dp)),
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .heightIn(max = 490.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Видео и палитра", color = colors.text, fontFamily = SpaceGrotesk, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.Close, contentDescription = null, tint = colors.text2, modifier = Modifier.size(20.dp).clickable(onClick = onDismiss))
                }

                Text("Цветокоррекция", color = colors.text2, fontFamily = WatermelonMono, fontSize = 11.sp)
                ccModes.forEach { (id, title) ->
                    val sel = cfg.colorCorrectionMode == id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (sel) colors.greenDim else colors.surface2)
                            .clickable {
                                GbcaPrefs.updateCoreConfig { it.copy(colorCorrectionMode = id) }
                                onConfigApplied()
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(title, color = if (sel) colors.green else colors.text, fontSize = 12.5.sp)
                    }
                }

                val palName = NativeCore.nativeGetDmgPaletteName(cfg.dmgPaletteIndex) ?: "DMG Olive"
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Палитра DMG (${cfg.dmgPaletteIndex + 1}/$paletteCount)", color = colors.text2, fontSize = 12.sp)
                    Text(palName, color = colors.green, fontFamily = WatermelonMono, fontSize = 11.sp)
                }
                Slider(
                    value = cfg.dmgPaletteIndex.toFloat(),
                    onValueChange = { v ->
                        GbcaPrefs.updateCoreConfig { it.copy(dmgPaletteIndex = v.toInt().coerceIn(0, paletteCount - 1)) }
                        onConfigApplied()
                    },
                    valueRange = 0f..(paletteCount - 1).toFloat(),
                    colors = SliderDefaults.colors(thumbColor = colors.green, activeTrackColor = colors.green),
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Инерция матрицы (Ghosting)", color = colors.text2, fontSize = 12.sp)
                    Text("${cfg.lcdGhosting}%", color = colors.green, fontFamily = WatermelonMono, fontSize = 11.sp)
                }
                Slider(
                    value = cfg.lcdGhosting.toFloat(),
                    onValueChange = { v ->
                        GbcaPrefs.updateCoreConfig { it.copy(lcdGhosting = v.toInt().coerceIn(0, 100)) }
                        onConfigApplied()
                    },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(thumbColor = colors.green, activeTrackColor = colors.green),
                )

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Рамки Super Game Boy", color = colors.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    WatermelonSwitch(
                        checked = cfg.sgbBorders,
                        onCheckedChange = { c ->
                            GbcaPrefs.updateCoreConfig { it.copy(sgbBorders = c) }
                            onConfigApplied()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioMixerDialog(
    onConfigApplied: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = watermelon
    val cfg = GbcaPrefs.coreConfigState.value
    val chNames = listOf("PSG 1 (Импульс 1)", "PSG 2 (Импульс 2)", "PSG 3 (Волна)", "PSG 4 (Шум)", "DMA A (GBA)", "DMA B (GBA)")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = colors.surface,
            modifier = Modifier.fillMaxWidth().border(1.dp, colors.line, RoundedCornerShape(18.dp)),
        ) {
            Column(
                modifier = Modifier.padding(16.dp).heightIn(max = 490.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Аудиомикшер (6 каналов)", color = colors.text, fontFamily = SpaceGrotesk, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.Close, contentDescription = null, tint = colors.text2, modifier = Modifier.size(20.dp).clickable(onClick = onDismiss))
                }

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Отключить звук", color = colors.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    WatermelonSwitch(
                        checked = cfg.audioMuted,
                        onCheckedChange = { m ->
                            GbcaPrefs.updateCoreConfig { it.copy(audioMuted = m) }
                            NativeCore.nativeAudioPause(m)
                        },
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Общая громкость", color = colors.text, fontSize = 12.5.sp)
                    Text("${(cfg.masterVolume * 100).toInt()}%", color = colors.green, fontFamily = WatermelonMono, fontSize = 11.sp)
                }
                Slider(
                    value = cfg.masterVolume,
                    onValueChange = { v ->
                        GbcaPrefs.updateCoreConfig { it.copy(masterVolume = v) }
                        onConfigApplied()
                    },
                    valueRange = 0f..1.5f,
                    colors = SliderDefaults.colors(thumbColor = colors.green, activeTrackColor = colors.green),
                )

                chNames.forEachIndexed { idx, name ->
                    val vol = cfg.channelVolumes.getOrElse(idx) { 1f }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(name, color = colors.text2, fontSize = 12.sp)
                        Text("${(vol * 100).toInt()}%", color = colors.green, fontFamily = WatermelonMono, fontSize = 11.sp)
                    }
                    Slider(
                        value = vol,
                        onValueChange = { v ->
                            val next = cfg.channelVolumes.toMutableList()
                            while (next.size < 6) next.add(1f)
                            next[idx] = v
                            GbcaPrefs.updateCoreConfig { it.copy(channelVolumes = next) }
                            onConfigApplied()
                        },
                        valueRange = 0f..1.5f,
                        colors = SliderDefaults.colors(thumbColor = colors.green, activeTrackColor = colors.green),
                    )
                }
            }
        }
    }
}

@Composable
private fun CheatsDialog(
    rom: RomEntry,
    onCheatsUpdated: (List<CheatItem>) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = watermelon
    val key = remember(rom) { RomRepository.getRomStorageKey(rom) }
    var cheats by remember { mutableStateOf(GbcaPrefs.getCheatsForRom(key)) }
    var newName by remember { mutableStateOf("") }
    var newCode by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = colors.surface,
            modifier = Modifier.fillMaxWidth().border(1.dp, colors.line, RoundedCornerShape(18.dp)),
        ) {
            Column(
                modifier = Modifier.padding(16.dp).heightIn(max = 490.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Чит-коды", color = colors.text, fontFamily = SpaceGrotesk, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.Close, contentDescription = null, tint = colors.text2, modifier = Modifier.size(20.dp).clickable(onClick = onDismiss))
                }

                val tfColors = TextFieldDefaults.outlinedTextFieldColors(
                    textColor = colors.text,
                    focusedBorderColor = colors.green,
                    unfocusedBorderColor = colors.line,
                    focusedLabelColor = colors.green,
                    unfocusedLabelColor = colors.text3,
                )
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Название чита") },
                    singleLine = true,
                    colors = tfColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = newCode,
                    onValueChange = { newCode = it },
                    label = { Text("Код (GameShark / Action Replay / Game Genie)") },
                    colors = tfColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.green)
                        .clickable {
                            if (newCode.isNotBlank()) {
                                val next = cheats + CheatItem(
                                    id = UUID.randomUUID().toString(),
                                    name = newName.ifBlank { "Чит ${cheats.size + 1}" },
                                    code = newCode.trim(),
                                    enabled = true,
                                )
                                cheats = next
                                GbcaPrefs.saveCheatsForRom(key, next)
                                onCheatsUpdated(next)
                                newName = ""
                                newCode = ""
                            }
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = Color(0xFF05060A), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Добавить код", color = Color(0xFF05060A), fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    }
                }

                cheats.forEach { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surface2)
                            .padding(10.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, color = colors.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(item.code, color = colors.text3, fontFamily = WatermelonMono, fontSize = 10.5.sp)
                        }
                        WatermelonSwitch(
                            checked = item.enabled,
                            onCheckedChange = { c ->
                                val next = cheats.map { if (it.id == item.id) it.copy(enabled = c) else it }
                                cheats = next
                                GbcaPrefs.saveCheatsForRom(key, next)
                                onCheatsUpdated(next)
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = null,
                            tint = colors.red,
                            modifier = Modifier.size(20.dp).clickable {
                                val next = cheats.filterNot { it.id == item.id }
                                cheats = next
                                GbcaPrefs.saveCheatsForRom(key, next)
                                onCheatsUpdated(next)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SensorsDialog(
    onConfigApplied: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = watermelon
    val cfg = GbcaPrefs.coreConfigState.value

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = colors.surface,
            modifier = Modifier.fillMaxWidth().border(1.dp, colors.line, RoundedCornerShape(18.dp)),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Аппаратные датчики", color = colors.text, fontFamily = SpaceGrotesk, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.Close, contentDescription = null, tint = colors.text2, modifier = Modifier.size(20.dp).clickable(onClick = onDismiss))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Датчик солнца (Boktai LUX)", color = colors.text, fontSize = 13.sp)
                    Text("${cfg.solarLux} / 10", color = colors.green, fontFamily = WatermelonMono, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = cfg.solarLux.toFloat(),
                    onValueChange = { v ->
                        GbcaPrefs.updateCoreConfig { it.copy(solarLux = v.toInt().coerceIn(0, 10)) }
                        onConfigApplied()
                    },
                    valueRange = 0f..10f,
                    steps = 9,
                    colors = SliderDefaults.colors(thumbColor = colors.green, activeTrackColor = colors.green),
                )
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Акселерометр (Tilt)", color = colors.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    WatermelonSwitch(cfg.sensorTiltEnabled, { c -> GbcaPrefs.updateCoreConfig { it.copy(sensorTiltEnabled = c) } })
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Гироскоп (WarioWare Twisted)", color = colors.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    WatermelonSwitch(cfg.sensorGyroEnabled, { c -> GbcaPrefs.updateCoreConfig { it.copy(sensorGyroEnabled = c) } })
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Вибрация картриджа (Rumble)", color = colors.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    WatermelonSwitch(cfg.rumbleEnabled, { c -> GbcaPrefs.updateCoreConfig { it.copy(rumbleEnabled = c) } })
                }
            }
        }
    }
}

@Composable
private fun CoreEngineDialog(
    system: ConsoleSystem,
    onConfigApplied: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = watermelon
    val cfg = GbcaPrefs.coreConfigState.value
    val active = when (system) {
        ConsoleSystem.GB -> cfg.preferredCoreGb
        ConsoleSystem.GBC -> cfg.preferredCoreGbc
        ConsoleSystem.GBA -> cfg.preferredCoreGba
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = colors.surface,
            modifier = Modifier.fillMaxWidth().border(1.dp, colors.line, RoundedCornerShape(18.dp)),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Ядро эмуляции (${system.label})", color = colors.text, fontFamily = SpaceGrotesk, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.Close, contentDescription = null, tint = colors.text2, modifier = Modifier.size(20.dp).clickable(onClick = onDismiss))
                }
                listOf(NativeCore.CORE_MGBA to "mGBA (Универсальное, GB/GBC/GBA)", NativeCore.CORE_SAMEBOY to "SameBoy (Точное для GB/GBC)").forEach { (id, title) ->
                    val enabled = system != ConsoleSystem.GBA || id == NativeCore.CORE_MGBA
                    val sel = active == id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (sel) colors.greenDim else colors.surface2)
                            .border(1.dp, if (sel) colors.green else colors.line, RoundedCornerShape(10.dp))
                            .clickable(enabled = enabled) {
                                GbcaPrefs.updateCoreConfig {
                                    when (system) {
                                        ConsoleSystem.GB -> it.copy(preferredCoreGb = id)
                                        ConsoleSystem.GBC -> it.copy(preferredCoreGbc = id)
                                        ConsoleSystem.GBA -> it.copy(preferredCoreGba = NativeCore.CORE_MGBA)
                                    }
                                }
                                onConfigApplied()
                                onDismiss()
                            }
                            .padding(12.dp),
                    ) {
                        Text(title, color = if (!enabled) colors.text3 else if (sel) colors.green else colors.text, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AchievementsDialog(
    session: RaGameSession?,
    onDismiss: () -> Unit,
) {
    val colors = watermelon
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = colors.surface,
            modifier = Modifier.fillMaxWidth().border(1.dp, colors.line, RoundedCornerShape(18.dp)),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("RetroAchievements", color = colors.text, fontFamily = SpaceGrotesk, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.Close, contentDescription = null, tint = colors.text2, modifier = Modifier.size(20.dp).clickable(onClick = onDismiss))
                }
                if (session == null) {
                    Text(
                        text = "Войдите в аккаунт RetroAchievements в настройках, чтобы видеть достижения для этой игры.",
                        color = colors.text2,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Start,
                    )
                } else {
                    Text(session.gameTitle, color = colors.green, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Column(
                        modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        session.achievements.forEach { ach ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.surface2)
                                    .padding(10.dp),
                            ) {
                                Icon(
                                    Icons.Filled.EmojiEvents,
                                    contentDescription = null,
                                    tint = if (ach.unlocked) Color(0xFFD4A017) else colors.text3,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${ach.title} (${ach.points} pts)", color = colors.text, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                                    Text(ach.description, color = colors.text3, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
