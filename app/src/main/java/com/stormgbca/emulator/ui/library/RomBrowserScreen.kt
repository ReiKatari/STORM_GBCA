package com.stormgbca.emulator.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.stormgbca.emulator.model.RomEntry
import com.stormgbca.emulator.model.RomFilter
import com.stormgbca.emulator.model.RomSortMode
import com.stormgbca.emulator.model.RomViewMode
import com.stormgbca.emulator.ui.common.WatermelonMark
import com.stormgbca.emulator.ui.theme.SpaceGrotesk
import com.stormgbca.emulator.ui.theme.WatermelonColors
import com.stormgbca.emulator.ui.theme.WatermelonMono
import com.stormgbca.emulator.ui.theme.watermelon
import ru.stormgames.gbca.NativeCore
import java.io.File

@Composable
fun RomBrowserScreen(
    roms: List<RomEntry>,
    boxArtMap: Map<String, String>,
    filter: RomFilter,
    sortMode: RomSortMode,
    viewMode: RomViewMode,
    searchQuery: String,
    isScanning: Boolean,
    hasUpdateAvailable: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onFilterChange: (RomFilter) -> Unit,
    onSortChange: (RomSortMode) -> Unit,
    onToggleViewMode: () -> Unit,
    onAddFolder: () -> Unit,
    onOpenSingleFile: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenUpdateDialog: () -> Unit,
    onLaunchRom: (RomEntry) -> Unit,
    onToggleFavorite: (RomEntry) -> Unit,
    onSetCoreOverride: (RomEntry, Int) -> Unit,
    onPickSoftPatch: (RomEntry) -> Unit,
    onRefreshBoxArt: (RomEntry) -> Unit,
    onPickCustomCover: (RomEntry) -> Unit,
) {
    val colors = watermelon
    var searchOpen by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var contextRom by remember { mutableStateOf<RomEntry?>(null) }
    var fullscreenCoverRom by remember { mutableStateOf<Pair<RomEntry, String>?>(null) }
    var infoRom by remember { mutableStateOf<RomEntry?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .systemBarsPadding(),
    ) {
        // Watermelon Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(horizontal = 14.dp),
        ) {
            if (searchOpen) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = colors.green, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = colors.text, fontSize = 15.sp),
                    cursorBrush = SolidColor(colors.green),
                    decorationBox = { inner ->
                        Box {
                            if (searchQuery.isEmpty()) Text("Поиск игр...", color = colors.text3, fontSize = 15.sp)
                            inner()
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { searchOpen = false; onSearchQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = colors.text2)
                }
            } else {
                WatermelonMark(height = 26.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "STORM GBCA",
                    color = colors.text,
                    fontFamily = SpaceGrotesk,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { searchOpen = true }) {
                    Icon(Icons.Filled.Search, contentDescription = "Поиск", tint = colors.text2)
                }
                IconButton(onClick = onToggleViewMode) {
                    Icon(
                        if (viewMode == RomViewMode.GRID) Icons.Filled.ViewList else Icons.Filled.GridView,
                        contentDescription = "Вид",
                        tint = colors.text2,
                    )
                }
                Box {
                    IconButton(onClick = { sortMenuOpen = true }) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Сортировка", tint = colors.text2)
                    }
                    DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                        RomSortMode.entries.forEach { mode ->
                            DropdownMenuItem(onClick = { onSortChange(mode); sortMenuOpen = false }) {
                                Text(
                                    text = mode.labelRu,
                                    color = if (mode == sortMode) colors.green else colors.text,
                                    fontWeight = if (mode == sortMode) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
                if (hasUpdateAvailable) {
                    IconButton(onClick = onOpenUpdateDialog) {
                        Icon(Icons.Filled.SystemUpdate, contentDescription = "Обновление", tint = colors.green)
                    }
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Настройки", tint = colors.text2)
                }
            }
        }

        if (isScanning) {
            LinearProgressIndicator(
                color = colors.green,
                backgroundColor = colors.surface2,
                modifier = Modifier.fillMaxWidth().height(3.dp),
            )
        }

        // Platform Filter Pills + Action Buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            RomFilter.entries.forEach { f ->
                val selected = f == filter
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selected) colors.green else colors.surface2)
                        .border(1.dp, if (selected) colors.green else colors.line, RoundedCornerShape(20.dp))
                        .clickable { onFilterChange(f) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = f.labelRu,
                        color = if (selected) Color(0xFF05060A) else colors.text,
                        fontFamily = SpaceGrotesk,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            HeaderActionPill(Icons.Filled.CreateNewFolder, "Папка", onAddFolder)
            HeaderActionPill(Icons.Filled.FileOpen, "Файл", onOpenSingleFile)
            HeaderActionPill(Icons.Filled.Refresh, "Обновить", onRefresh)
        }

        if (roms.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    WatermelonMark(height = 54.dp)
                    Text(
                        text = if (isScanning) "Сканирование библиотеки..." else "Библиотека игр пуста",
                        color = colors.text,
                        fontFamily = SpaceGrotesk,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Выберите папку с играми GB, GBC, GBA, SGB (поддерживаются ZIP и 7Z) или откройте отдельный файл.",
                        color = colors.text2,
                        fontSize = 13.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.green)
                                .clickable(onClick = onAddFolder)
                                .padding(horizontal = 16.dp, vertical = 11.dp),
                        ) {
                            Text("Добавить папку ROM", color = Color(0xFF05060A), fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surface2)
                                .border(1.dp, colors.line, RoundedCornerShape(12.dp))
                                .clickable(onClick = onOpenSingleFile)
                                .padding(horizontal = 16.dp, vertical = 11.dp),
                        ) {
                            Text("Открыть файл", color = colors.text, fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }
            }
        } else if (viewMode == RomViewMode.GRID) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 148.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(roms, key = { it.uriString }) { rom ->
                    RomGridCard(
                        rom = rom,
                        boxArtPath = boxArtMap[rom.uriString],
                        onClick = { onLaunchRom(rom) },
                        onLongPress = { contextRom = rom },
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(roms, key = { it.uriString }) { rom ->
                    RomListRow(
                        rom = rom,
                        boxArtPath = boxArtMap[rom.uriString],
                        onClick = { onLaunchRom(rom) },
                        onLongPress = { contextRom = rom },
                    )
                }
            }
        }
    }

    contextRom?.let { rom ->
        RomContextDialog(
            rom = rom,
            boxArtPath = boxArtMap[rom.uriString],
            onDismiss = { contextRom = null },
            onLaunch = { contextRom = null; onLaunchRom(rom) },
            onToggleFavorite = { onToggleFavorite(rom); contextRom = null },
            onSetCoreOverride = { ov -> onSetCoreOverride(rom, ov); contextRom = null },
            onPickSoftPatch = { onPickSoftPatch(rom); contextRom = null },
            onRefreshBoxArt = { onRefreshBoxArt(rom); contextRom = null },
            onPickCustomCover = { onPickCustomCover(rom); contextRom = null },
            onViewCover = { path -> contextRom = null; fullscreenCoverRom = rom to path },
            onShowInfo = { contextRom = null; infoRom = rom },
        )
    }

    fullscreenCoverRom?.let { (rom, path) ->
        Dialog(onDismissRequest = { fullscreenCoverRom = null }) {
            val ctx = LocalContext.current
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.surface,
                modifier = Modifier.fillMaxWidth().border(1.dp, colors.line, RoundedCornerShape(16.dp)),
            ) {
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(
                        model = ImageRequest.Builder(ctx).data(File(path)).build(),
                        contentDescription = rom.displayTitle,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(340.dp).clip(RoundedCornerShape(12.dp)),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(rom.displayTitle, color = colors.text, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }

    infoRom?.let { rom ->
        Dialog(onDismissRequest = { infoRom = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.surface,
                modifier = Modifier.fillMaxWidth().border(1.dp, colors.line, RoundedCornerShape(16.dp)),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Информация об игре", color = colors.text, fontFamily = SpaceGrotesk, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.Close, contentDescription = null, tint = colors.text2, modifier = Modifier.size(20.dp).clickable { infoRom = null })
                    }
                    InfoRow("Название", rom.displayTitle)
                    InfoRow("Заголовок ROM", rom.internalTitle)
                    InfoRow("Платформа", rom.platformLabel)
                    InfoRow("Код игры", rom.gameCode.ifBlank { "-" })
                    InfoRow("Регион", rom.region)
                    InfoRow("Размер", "${rom.romSize / 1024} КБ")
                    InfoRow("CRC32", rom.crc32.toString(16).uppercase())
                    InfoRow("MD5", rom.md5)
                }
            }
        }
    }
}

@Composable
private fun HeaderActionPill(icon: ImageVector, label: String, onClick: () -> Unit) {
    val colors = watermelon
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surface2)
            .border(1.dp, colors.line, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.green, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, color = colors.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val colors = watermelon
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = colors.text3, fontSize = 12.sp)
        Text(value, color = colors.text, fontFamily = WatermelonMono, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RomContextDialog(
    rom: RomEntry,
    boxArtPath: String?,
    onDismiss: () -> Unit,
    onLaunch: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSetCoreOverride: (Int) -> Unit,
    onPickSoftPatch: () -> Unit,
    onRefreshBoxArt: () -> Unit,
    onPickCustomCover: () -> Unit,
    onViewCover: (String) -> Unit,
    onShowInfo: () -> Unit,
) {
    val colors = watermelon
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = colors.surface,
            modifier = Modifier.fillMaxWidth().border(1.dp, colors.line, RoundedCornerShape(18.dp)),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(rom.displayTitle, color = colors.text, fontFamily = SpaceGrotesk, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${rom.platformLabel}  |  ${rom.region}", color = colors.text3, fontFamily = WatermelonMono, fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))

                ContextBtn(Icons.Filled.PlayArrow, "Запустить игру", colors.green, onLaunch)
                ContextBtn(if (rom.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline, if (rom.isFavorite) "Убрать из избранного" else "В избранное", WatermelonColors.favoriteStar, onToggleFavorite)
                if (rom.platform != NativeCore.PLATFORM_GBA) {
                    val nextCore = when (rom.coreOverride) {
                        -1 -> NativeCore.CORE_MGBA
                        NativeCore.CORE_MGBA -> NativeCore.CORE_SAMEBOY
                        else -> -1
                    }
                    val curLabel = when (rom.coreOverride) {
                        NativeCore.CORE_MGBA -> "mGBA"
                        NativeCore.CORE_SAMEBOY -> "SameBoy"
                        else -> "Авто"
                    }
                    ContextBtn(Icons.Filled.Memory, "Ядро для игры: $curLabel", colors.text2) { onSetCoreOverride(nextCore) }
                }
                ContextBtn(Icons.Filled.Tune, "Применить патч (IPS / UPS / BPS)", colors.text2, onPickSoftPatch)
                ContextBtn(Icons.Filled.Refresh, "Обновить обложку", colors.text2, onRefreshBoxArt)
                ContextBtn(Icons.Filled.Image, "Выбрать свою обложку", colors.text2, onPickCustomCover)
                if (!boxArtPath.isNullOrBlank()) {
                    ContextBtn(Icons.Filled.Image, "Просмотреть обложку", colors.green) { onViewCover(boxArtPath) }
                }
                ContextBtn(Icons.Filled.Info, "Техническая информация", colors.text2, onShowInfo)
            }
        }
    }
}

@Composable
private fun ContextBtn(icon: ImageVector, text: String, tint: Color, onClick: () -> Unit) {
    val colors = watermelon
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(colors.surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
