package com.stormgbca.emulator.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.lifecycleScope
import com.stormgbca.emulator.BuildConfig
import com.stormgbca.emulator.data.GbcaPrefs
import com.stormgbca.emulator.data.RetroAchievementsClient
import com.stormgbca.emulator.data.RomRepository
import com.stormgbca.emulator.model.ConsoleSystem
import com.stormgbca.emulator.model.PhysicalAction
import com.stormgbca.emulator.ui.common.UnifiedBackButton
import com.stormgbca.emulator.ui.common.WatermelonCard
import com.stormgbca.emulator.ui.common.WatermelonRowSeparator
import com.stormgbca.emulator.ui.common.WatermelonSectionLabel
import com.stormgbca.emulator.ui.common.WatermelonSwitch
import com.stormgbca.emulator.ui.emulator.overlay.OverlayPickerDialog
import com.stormgbca.emulator.ui.emulator.skin.ConsoleSkinPickerDialog
import com.stormgbca.emulator.ui.theme.ACCENT_PRESETS
import com.stormgbca.emulator.ui.theme.AppThemeManager
import com.stormgbca.emulator.ui.theme.SpaceGrotesk
import com.stormgbca.emulator.ui.theme.StormWatermelonTheme
import com.stormgbca.emulator.ui.theme.Theme
import com.stormgbca.emulator.ui.theme.WatermelonMono
import com.stormgbca.emulator.ui.theme.watermelon
import com.stormgbca.emulator.update.StormUpdateDialog
import com.stormgbca.emulator.update.StormUpdateMirrorClient
import com.stormgbca.emulator.update.UpdateDownloadState
import com.stormgbca.emulator.update.UpdateReleaseInfo
import kotlinx.coroutines.launch
import ru.stormgames.gbca.NativeCore

class SettingsActivity : AppCompatActivity() {
    private val awaitingActionState = mutableStateOf<PhysicalAction?>(null)
    private var pendingBiosTarget: String? = null

    private val biosPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = pendingBiosTarget ?: return@registerForActivityResult
        pendingBiosTarget = null
        if (uri != null) {
            lifecycleScope.launch {
                val f = RomRepository.materializeUriToCache(this@SettingsActivity, uri, "bios")
                if (f != null) {
                    GbcaPrefs.updateCoreConfig { cfg ->
                        when (target) {
                            "gb" -> cfg.copy(biosGbPath = f.absolutePath)
                            "gbc" -> cfg.copy(biosGbcPath = f.absolutePath)
                            "gba" -> cfg.copy(biosGbaPath = f.absolutePath)
                            "sgb" -> cfg.copy(biosSgbPath = f.absolutePath)
                            else -> cfg
                        }
                    }
                    Toast.makeText(this@SettingsActivity, "BIOS сохранён: ${f.name}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private val backupExportPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.openOutputStream(uri)?.use { it.write(GbcaPrefs.exportAllPrefsJson().toByteArray()) }
                Toast.makeText(this, "Резервная копия сохранена", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppThemeManager.applyEdgeToEdgeTheme(this)

        setContent {
            StormWatermelonTheme {
                SettingsScreen(
                    awaitingAction = awaitingActionState.value,
                    onBack = { finish() },
                    onPickBios = { target ->
                        pendingBiosTarget = target
                        biosPicker.launch(arrayOf("*/*"))
                    },
                    onStartBindKey = { action -> awaitingActionState.value = action },
                    onExportBackup = { backupExportPicker.launch("storm_gbca_backup.json") },
                )
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val action = awaitingActionState.value
        if (action != null) {
            GbcaPrefs.setKeyCodeForAction(action, keyCode)
            awaitingActionState.value = null
            Toast.makeText(this, "${action.labelRu}: ${KeyEvent.keyCodeToString(keyCode)}", Toast.LENGTH_SHORT).show()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

@Composable
private fun SettingsScreen(
    awaitingAction: PhysicalAction?,
    onBack: () -> Unit,
    onPickBios: (String) -> Unit,
    onStartBindKey: (PhysicalAction) -> Unit,
    onExportBackup: () -> Unit,
) {
    val colors = watermelon
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val cfg = GbcaPrefs.coreConfigState.value

    var skinDialogSystem by remember { mutableStateOf<ConsoleSystem?>(null) }
    var overlayDialogSystem by remember { mutableStateOf<ConsoleSystem?>(null) }
    var showInputDialog by remember { mutableStateOf(false) }
    var showRaDialog by remember { mutableStateOf(false) }
    var foundUpdate by remember { mutableStateOf<UpdateReleaseInfo?>(null) }
    var downloadState by remember { mutableStateOf<UpdateDownloadState>(UpdateDownloadState.Idle) }
    var updateStatus by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .systemBarsPadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            UnifiedBackButton(onClick = onBack)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Настройки", color = colors.text, fontFamily = SpaceGrotesk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("STORM GBCA v${BuildConfig.VERSION_NAME}", color = colors.text3, fontFamily = WatermelonMono, fontSize = 11.sp)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            WatermelonSectionLabel("Тема и оформление (9 тем + 12 акцентов)")
            WatermelonCard {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        Theme.entries.forEach { t ->
                            val selected = AppThemeManager.themeState.value == t
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) colors.green else colors.surface2)
                                    .border(1.dp, if (selected) colors.green else colors.line, RoundedCornerShape(10.dp))
                                    .clickable { AppThemeManager.updateTheme(context, t) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = t.displayNameRu,
                                    color = if (selected) Color(0xFF05060A) else colors.text,
                                    fontFamily = SpaceGrotesk,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        ACCENT_PRESETS.forEach { preset ->
                            val selected = AppThemeManager.accentColorState.value == preset.key
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(preset.previewColor)
                                    .border(if (selected) 3.dp else 1.dp, if (selected) Color.White else colors.line, CircleShape)
                                    .clickable { AppThemeManager.updateAccent(context, preset.key) },
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("3d_boxart" to "3D Коробка", "glassmorphism" to "Стекло", "flat_minimal" to "Минимализм").forEach { (k, label) ->
                            val sel = AppThemeManager.cardStyleState.value == k
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(if (sel) colors.greenDim else colors.surface2)
                                    .border(1.dp, if (sel) colors.green else colors.line, RoundedCornerShape(9.dp))
                                    .clickable { AppThemeManager.updateCardStyle(context, k) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(label, color = if (sel) colors.green else colors.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    SettingsSwitchRow("3D заставка при запуске", cfg.showBootSplash) { c ->
                        GbcaPrefs.updateCoreConfig { it.copy(showBootSplash = c) }
                    }
                }
            }

            WatermelonSectionLabel("Персонализация по консолям (GB / GBC / GBA)")
            WatermelonCard {
                ConsoleSystem.entries.forEachIndexed { idx, sys ->
                    if (idx > 0) WatermelonRowSeparator()
                    SettingsActionRow(
                        icon = Icons.Filled.Devices,
                        title = "Корпус ${sys.label}",
                        subtitle = "Текущий: ${GbcaPrefs.getSkinKey(sys)} (${GbcaPrefs.getSkinDimming(sys)}%)",
                        onClick = { skinDialogSystem = sys },
                    )
                    WatermelonRowSeparator()
                    SettingsActionRow(
                        icon = Icons.Filled.Gamepad,
                        title = "Оверлей и кнопки ${sys.label}",
                        subtitle = "Профиль: ${GbcaPrefs.getOverlayProfile(sys).buttonStyle}",
                        onClick = { overlayDialogSystem = sys },
                    )
                }
            }

            WatermelonSectionLabel("Ядро эмуляции и BIOS")
            WatermelonCard {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CoreChoiceRow("Ядро Game Boy (GB)", cfg.preferredCoreGb) { id ->
                        GbcaPrefs.updateCoreConfig { it.copy(preferredCoreGb = id) }
                    }
                    CoreChoiceRow("Ядро Game Boy Color (GBC)", cfg.preferredCoreGbc) { id ->
                        GbcaPrefs.updateCoreConfig { it.copy(preferredCoreGbc = id) }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Упреждение ввода (Run-Ahead)", color = colors.text, fontSize = 13.sp)
                        Text("${cfg.runAheadFrames} кадра", color = colors.green, fontFamily = WatermelonMono, fontSize = 12.sp)
                    }
                    Slider(
                        value = cfg.runAheadFrames.toFloat(),
                        onValueChange = { v -> GbcaPrefs.updateCoreConfig { it.copy(runAheadFrames = v.toInt().coerceIn(0, 2)) } },
                        valueRange = 0f..2f,
                        steps = 1,
                        colors = SliderDefaults.colors(thumbColor = colors.green, activeTrackColor = colors.green),
                    )

                    SettingsSwitchRow("Перемотка времени (Rewind)", cfg.rewindEnabled) { c ->
                        GbcaPrefs.updateCoreConfig { it.copy(rewindEnabled = c) }
                    }
                    SettingsSwitchRow("Пропускать заставку BIOS", cfg.skipBiosIntro) { c ->
                        GbcaPrefs.updateCoreConfig { it.copy(skipBiosIntro = c) }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BiosChip("BIOS GB", cfg.biosGbPath.isNotBlank()) { onPickBios("gb") }
                        BiosChip("BIOS GBC", cfg.biosGbcPath.isNotBlank()) { onPickBios("gbc") }
                        BiosChip("BIOS GBA", cfg.biosGbaPath.isNotBlank()) { onPickBios("gba") }
                    }
                }
            }

            WatermelonSectionLabel("Видео и аудио")
            WatermelonCard {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Соотношение сторон", color = colors.text2, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to "Оригинал", 1 to "Целые пиксели", 2 to "На весь экран").forEach { (id, title) ->
                            val sel = cfg.aspectMode == id
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(if (sel) colors.greenDim else colors.surface2)
                                    .border(1.dp, if (sel) colors.green else colors.line, RoundedCornerShape(9.dp))
                                    .clickable { GbcaPrefs.updateCoreConfig { it.copy(aspectMode = id) } }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(title, color = if (sel) colors.green else colors.text, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    Text("Фильтр ВЧ звука (SameBoy)", color = colors.text2, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            NativeCore.HIGHPASS_OFF to "Выкл",
                            NativeCore.HIGHPASS_ACCURATE to "Точный",
                            NativeCore.HIGHPASS_REMOVE_DC to "Без DC",
                        ).forEach { (id, title) ->
                            val sel = cfg.audioHighpassMode == id
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(if (sel) colors.greenDim else colors.surface2)
                                    .border(1.dp, if (sel) colors.green else colors.line, RoundedCornerShape(9.dp))
                                    .clickable { GbcaPrefs.updateCoreConfig { it.copy(audioHighpassMode = id) } }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(title, color = if (sel) colors.green else colors.text, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    SettingsSwitchRow("Динамический контроль частоты (DRC)", cfg.dynamicRateControl) { c ->
                        GbcaPrefs.updateCoreConfig { it.copy(dynamicRateControl = c) }
                    }
                }
            }

            WatermelonSectionLabel("Геймпад, RetroAchievements и обновления")
            WatermelonCard {
                SettingsActionRow(
                    icon = Icons.Filled.Gamepad,
                    title = "Физический контроллер и клавиши",
                    subtitle = "Настройка кнопок Bluetooth / USB геймпада",
                    onClick = { showInputDialog = true },
                )
                WatermelonRowSeparator()
                SettingsActionRow(
                    icon = Icons.Filled.EmojiEvents,
                    title = "RetroAchievements",
                    subtitle = GbcaPrefs.getRaUsername().ifBlank { "Не авторизован" },
                    onClick = { showRaDialog = true },
                )
                WatermelonRowSeparator()
                SettingsActionRow(
                    icon = Icons.Filled.Restore,
                    title = "Экспорт резервной копии настроек",
                    subtitle = "Сохранить профили, скины и оверлеи в JSON",
                    onClick = onExportBackup,
                )
                WatermelonRowSeparator()
                SettingsActionRow(
                    icon = Icons.Filled.SystemUpdate,
                    title = "Проверить обновления",
                    subtitle = updateStatus ?: "GitHub + STORM GAMES WORLD",
                    onClick = {
                        updateStatus = "Проверка..."
                        scope.launch {
                            val rel = StormUpdateMirrorClient.checkLatestUpdate()
                            if (rel != null) {
                                foundUpdate = rel
                                updateStatus = "Доступна версия v${rel.version}"
                            } else {
                                updateStatus = "Установлена актуальная версия v${BuildConfig.VERSION_NAME}"
                            }
                        }
                    },
                )
            }

            Spacer(Modifier.height(28.dp))
        }
    }

    skinDialogSystem?.let { sys ->
        ConsoleSkinPickerDialog(initialSystem = sys, onDismiss = { skinDialogSystem = null })
    }

    overlayDialogSystem?.let { sys ->
        OverlayPickerDialog(initialSystem = sys, onDismiss = { overlayDialogSystem = null })
    }

    if (showInputDialog) {
        PhysicalInputSetupDialog(
            awaitingAction = awaitingAction,
            onStartBind = onStartBindKey,
            onDismiss = { showInputDialog = false },
        )
    }

    if (showRaDialog) {
        RetroAchievementsLoginDialog(onDismiss = { showRaDialog = false })
    }

    foundUpdate?.let { rel ->
        StormUpdateDialog(
            release = rel,
            downloadState = downloadState,
            onStartDownload = {
                scope.launch {
                    StormUpdateMirrorClient.downloadApk(context, rel) { downloadState = it }
                }
            },
            onInstallApk = { apk -> StormUpdateMirrorClient.installApk(context, apk) },
            onSkipVersion = {
                GbcaPrefs.setSkippedUpdateVersion(rel.version)
                foundUpdate = null
            },
            onDismiss = { foundUpdate = null },
        )
    }
}

@Composable
private fun SettingsSwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = watermelon
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Text(title, color = colors.text, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
        WatermelonSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val colors = watermelon
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.green, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = colors.text, fontFamily = SpaceGrotesk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = colors.text3, fontSize = 11.5.sp)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.text3, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun CoreChoiceRow(label: String, current: Int, onSelect: (Int) -> Unit) {
    val colors = watermelon
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = colors.text2, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(NativeCore.CORE_MGBA to "mGBA", NativeCore.CORE_SAMEBOY to "SameBoy").forEach { (id, title) ->
                val sel = current == id
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (sel) colors.greenDim else colors.surface2)
                        .border(1.dp, if (sel) colors.green else colors.line, RoundedCornerShape(9.dp))
                        .clickable { onSelect(id) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(title, color = if (sel) colors.green else colors.text, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BiosChip(label: String, loaded: Boolean, onClick: () -> Unit) {
    val colors = watermelon
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(9.dp))
            .background(if (loaded) colors.greenDim else colors.surface2)
            .border(1.dp, if (loaded) colors.green else colors.line, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (loaded) colors.green else colors.text, fontFamily = WatermelonMono, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PhysicalInputSetupDialog(
    awaitingAction: PhysicalAction?,
    onStartBind: (PhysicalAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = watermelon
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = colors.surface,
            modifier = Modifier.fillMaxWidth().border(1.dp, colors.line, RoundedCornerShape(18.dp)),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Настройка клавиш геймпада", color = colors.text, fontFamily = SpaceGrotesk, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.Close, contentDescription = null, tint = colors.text2, modifier = Modifier.size(20.dp).clickable(onClick = onDismiss))
                }
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier.height(380.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PhysicalAction.entries.forEach { action ->
                        val waiting = awaitingAction == action
                        val code = GbcaPrefs.getKeyCodeForAction(action)
                        val keyName = KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (waiting) colors.greenDim else colors.surface2)
                                .border(1.dp, if (waiting) colors.green else colors.line, RoundedCornerShape(10.dp))
                                .clickable { onStartBind(action) }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                        ) {
                            Text(action.labelRu, color = colors.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(colors.bg)
                                    .border(1.dp, colors.green.copy(alpha = 0.5f), RoundedCornerShape(7.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = if (waiting) "Нажмите кнопку..." else keyName,
                                    color = colors.green,
                                    fontFamily = WatermelonMono,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surface2)
                        .clickable { GbcaPrefs.resetKeyMappings() }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Сбросить по умолчанию", color = colors.text2, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun RetroAchievementsLoginDialog(onDismiss: () -> Unit) {
    val colors = watermelon
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var username by remember { mutableStateOf(GbcaPrefs.getRaUsername()) }
    var password by remember { mutableStateOf("") }
    var hardcore by remember { mutableStateOf(GbcaPrefs.isRaHardcore()) }
    var status by remember { mutableStateOf<String?>(null) }

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
                val tfColors = TextFieldDefaults.outlinedTextFieldColors(
                    textColor = colors.text,
                    focusedBorderColor = colors.green,
                    unfocusedBorderColor = colors.line,
                    focusedLabelColor = colors.green,
                    unfocusedLabelColor = colors.text3,
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Логин RetroAchievements") },
                    singleLine = true,
                    colors = tfColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Пароль") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = tfColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Режим Hardcore", color = colors.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    WatermelonSwitch(
                        checked = hardcore,
                        onCheckedChange = {
                            hardcore = it
                            GbcaPrefs.setRaHardcore(it)
                        },
                    )
                }
                status?.let { Text(it, color = colors.green, fontSize = 12.sp) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.green)
                        .clickable {
                            status = "Авторизация..."
                            scope.launch {
                                val res = RetroAchievementsClient.login(username, password)
                                status = if (res.success) "Вход выполнен: ${res.username} (${res.score} pts)" else "Ошибка: ${res.error}"
                            }
                        }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Войти", color = Color(0xFF05060A), fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
