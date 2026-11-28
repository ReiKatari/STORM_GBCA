package com.stormgbca.emulator.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.SystemUpdateAlt
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.stormgbca.emulator.R
import com.stormgbca.emulator.model.RomEntry
import com.stormgbca.emulator.model.RomFilter
import com.stormgbca.emulator.model.RomSortMode
import com.stormgbca.emulator.model.RomViewMode
import com.stormgbca.emulator.sgw.SgwGameUpdateInfo
import com.stormgbca.emulator.ui.common.UnifiedBackButton
import com.stormgbca.emulator.ui.common.WatermelonMark
import com.stormgbca.emulator.ui.theme.SpaceGrotesk
import com.stormgbca.emulator.ui.theme.WatermelonColors
import com.stormgbca.emulator.ui.theme.WatermelonMono
import com.stormgbca.emulator.ui.theme.watermelon
import ru.stormgames.gbca.NativeCore
import java.io.File

@Composable
private fun filterLabel(f: RomFilter): String = when (f) {
    RomFilter.ALL -> stringResource(R.string.filter_all)
    RomFilter.GB -> stringResource(R.string.filter_gb)
    RomFilter.GBC -> stringResource(R.string.filter_gbc)
    RomFilter.GBA -> stringResource(R.string.filter_gba)
    RomFilter.FAVORITES -> stringResource(R.string.filter_favorites)
}

@Composable
private fun sortLabel(m: RomSortMode): String = when (m) {
    RomSortMode.TITLE -> stringResource(R.string.sort_title)
    RomSortMode.RECENT -> stringResource(R.string.sort_recent)
    RomSortMode.PLATFORM -> stringResource(R.string.sort_platform)
    RomSortMode.PLAY_TIME -> stringResource(R.string.sort_playtime)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RomBrowserScreen(
    roms: List<RomEntry>,
    boxArtMap: Map<String, String>,
    confirmedRaHashes: Set<String> = emptySet(),
    gameUpdatesMap: Map<String, SgwGameUpdateInfo> = emptyMap(),
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
    onOpenCatalog: () -> Unit = {},
    onUpdateRomFromSgw: (SgwGameUpdateInfo) -> Unit = {},
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
    onViewRomAchievements: (RomEntry) -> Unit = {},
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
            .padding(top = 4.dp),
    ) {
        // Compact STORM DS / STORM 3DS Single-Line Header (No RA icon in header)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(start = 14.dp, end = 6.dp),
        ) {
            if (searchOpen) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = colors.green, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = colors.text, fontSize = 15.sp),
                    cursorBrush = SolidColor(colors.green),
                    decorationBox = { inner ->
                        Box {
                            if (searchQuery.isEmpty()) Text(stringResource(R.string.hint_search_roms), color = colors.text2, fontSize = 15.sp)
                            inner()
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { searchOpen = false; onSearchQueryChange("") }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = colors.text, modifier = Modifier.size(19.dp))
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 4.dp),
                ) {
                    WatermelonMark(height = 22.dp)
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = colors.text, fontWeight = FontWeight.Bold)) {
                                append("STORM ")
                            }
                            withStyle(SpanStyle(color = colors.green, fontWeight = FontWeight.Bold)) {
                                append("GBCA")
                            }
                        },
                        fontFamily = SpaceGrotesk,
                        fontSize = 18.sp,
                        letterSpacing = (-0.3).sp,
                        softWrap = false,
                        maxLines = 1,
                    )
                }

                Spacer(Modifier.weight(1f))

                IconButton(onClick = { searchOpen = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.hint_search_roms), tint = colors.text, modifier = Modifier.size(19.dp))
                }
                IconButton(onClick = onToggleViewMode, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (viewMode == RomViewMode.GRID) Icons.Filled.ViewList else Icons.Filled.GridView,
                        contentDescription = null,
                        tint = colors.text,
                        modifier = Modifier.size(19.dp),
                    )
                }
                Box {
                    IconButton(onClick = { sortMenuOpen = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, tint = colors.text, modifier = Modifier.size(19.dp))
                    }
                    DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                        RomSortMode.entries.forEach { mode ->
                            DropdownMenuItem(onClick = { onSortChange(mode); sortMenuOpen = false }) {
                                Text(
                                    text = sortLabel(mode),
                                    color = if (mode == sortMode) colors.green else colors.text,
                                    fontWeight = if (mode == sortMode) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
                if (hasUpdateAvailable) {
                    IconButton(onClick = onOpenUpdateDialog, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.SystemUpdate, contentDescription = stringResource(R.string.update_dialog_title), tint = colors.green, modifier = Modifier.size(19.dp))
                    }
                }
                IconButton(onClick = onOpenSettings, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings), tint = colors.text, modifier = Modifier.size(19.dp))
                }
            }
        }

        if (isScanning) {
            LinearProgressIndicator(
                color = colors.green,
                backgroundColor = colors.surface2,
                modifier = Modifier.fillMaxWidth().height(2.5.dp),
            )
        }

        // Clean Symmetrical 2-Row Top Menu Bar:
        // Row 1: All | GB | GBC | GBA
        // Row 2: Favorites | File | Refresh (Folder button removed to Settings & Empty state)
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 5.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                listOf(RomFilter.ALL, RomFilter.GB, RomFilter.GBC, RomFilter.GBA).forEach { f ->
                    val selected = f == filter
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selected) colors.green else colors.surface2)
                            .border(1.dp, if (selected) colors.green else colors.line, RoundedCornerShape(16.dp))
                            .clickable { onFilterChange(f) }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = filterLabel(f),
                            color = if (selected) Color(0xFF05060A) else colors.text,
                            fontFamily = SpaceGrotesk,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                val updatesCount = gameUpdatesMap.size
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1.02f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (updatesCount > 0) colors.green.copy(alpha = 0.16f) else colors.surface2)
                        .border(1.dp, if (updatesCount > 0) colors.green else colors.line, RoundedCornerShape(16.dp))
                        .clickable(onClick = onOpenCatalog)
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Filled.Public, contentDescription = null, tint = colors.green, modifier = Modifier.size(13.5.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.catalog_btn),
                        color = colors.text,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (updatesCount > 0) {
                        Spacer(Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(colors.green)
                                .padding(horizontal = 4.5.dp, vertical = 0.5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = updatesCount.toString(),
                                color = Color(0xFF05060A),
                                fontFamily = WatermelonMono,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                            )
                        }
                    }
                }
                val favSelected = filter == RomFilter.FAVORITES
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1.08f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (favSelected) colors.green else colors.surface2)
                        .border(1.dp, if (favSelected) colors.green else colors.line, RoundedCornerShape(16.dp))
                        .clickable { onFilterChange(if (favSelected) RomFilter.ALL else RomFilter.FAVORITES) }
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                ) {
                    Icon(
                        imageVector = if (favSelected) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = null,
                        tint = if (favSelected) Color(0xFF05060A) else WatermelonColors.favoriteStar,
                        modifier = Modifier.size(13.5.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = filterLabel(RomFilter.FAVORITES),
                        color = if (favSelected) Color(0xFF05060A) else colors.text,
                        fontFamily = SpaceGrotesk,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                HeaderActionPill(Icons.Filled.FileOpen, stringResource(R.string.file_btn), onOpenSingleFile, Modifier.weight(0.88f))
                HeaderActionPill(Icons.Filled.Refresh, stringResource(R.string.refresh_library), onRefresh, Modifier.weight(0.95f))
            }
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
                        text = if (isScanning) stringResource(R.string.scanning_library) else stringResource(R.string.empty_library_title),
                        color = colors.text,
                        fontFamily = SpaceGrotesk,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(R.string.empty_library_desc),
                        color = colors.text2,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.green)
                                .clickable(onClick = onAddFolder)
                                .padding(horizontal = 16.dp, vertical = 11.dp),
                        ) {
                            Text(stringResource(R.string.add_rom_folder), color = Color(0xFF05060A), fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surface2)
                                .border(1.dp, colors.line, RoundedCornerShape(12.dp))
                                .clickable(onClick = onOpenSingleFile)
                                .padding(horizontal = 16.dp, vertical = 11.dp),
                        ) {
                            Text(stringResource(R.string.open_rom_file), color = colors.text, fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.green.copy(alpha = 0.14f))
                            .border(1.dp, colors.green.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                            .clickable(onClick = onOpenCatalog)
                            .padding(horizontal = 18.dp, vertical = 11.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Icon(Icons.Filled.Public, contentDescription = null, tint = colors.green, modifier = Modifier.size(17.dp))
                            Text(stringResource(R.string.sgw_open_catalog_cta), color = colors.green, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                        hasRaAchievements = rom.md5.lowercase() in confirmedRaHashes,
                        gameUpdate = gameUpdatesMap[rom.uriString],
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
                        hasRaAchievements = rom.md5.lowercase() in confirmedRaHashes,
                        gameUpdate = gameUpdatesMap[rom.uriString],
                        onClick = { onLaunchRom(rom) },
                        onLongPress = { contextRom = rom },
                    )
                }
            }
        }
    }

    contextRom?.let { rom ->
        val ctxUpdate = gameUpdatesMap[rom.uriString]
        RomContextDialog(
            rom = rom,
            boxArtPath = boxArtMap[rom.uriString],
            gameUpdate = ctxUpdate,
            onDismiss = { contextRom = null },
            onLaunch = { contextRom = null; onLaunchRom(rom) },
            onUpdateFromSgw = {
                contextRom = null
                if (ctxUpdate != null) onUpdateRomFromSgw(ctxUpdate)
            },
            onToggleFavorite = { onToggleFavorite(rom); contextRom = null },
            onSetCoreOverride = { ov -> onSetCoreOverride(rom, ov); contextRom = null },
            onPickSoftPatch = { onPickSoftPatch(rom); contextRom = null },
            onRefreshBoxArt = { onRefreshBoxArt(rom); contextRom = null },
            onPickCustomCover = { onPickCustomCover(rom); contextRom = null },
            onViewCover = { path -> contextRom = null; fullscreenCoverRom = rom to path },
            onViewAchievements = { contextRom = null; onViewRomAchievements(rom) },
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
                shape = RoundedCornerShape(18.dp),
                color = colors.surface,
                modifier = Modifier.fillMaxWidth().border(1.dp, colors.line, RoundedCornerShape(18.dp)),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.info_title),
                            color = colors.text,
                            fontFamily = SpaceGrotesk,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = null,
                            tint = colors.text,
                            modifier = Modifier.size(20.dp).clickable { infoRom = null },
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        InfoBlockRow(stringResource(R.string.info_name), rom.cleanTitleBeforeYear)
                        InfoBlockRow(stringResource(R.string.info_filename), rom.fileName)
                        InfoRow(stringResource(R.string.info_extension), rom.fileExtension)
                        InfoRow(stringResource(R.string.info_rom_header), rom.internalTitle.ifBlank { "-" })
                        InfoRow(stringResource(R.string.info_platform), rom.platformLabel)
                        InfoRow(stringResource(R.string.info_code), rom.gameCode.ifBlank { "-" })
                        InfoRow(stringResource(R.string.info_region), rom.region)
                        InfoRow(stringResource(R.string.info_size), "${rom.romSize / 1024} KB")
                        InfoRow("CRC32", rom.crc32.toString(16).uppercase().padStart(8, '0'))
                        InfoBlockRow("MD5", rom.md5.ifBlank { "-" })
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        UnifiedBackButton(onClick = { infoRom = null })
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderActionPill(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = watermelon
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface2)
            .border(1.dp, colors.line, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.green, modifier = Modifier.size(13.5.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            color = colors.text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun InfoBlockRow(label: String, value: String) {
    val colors = watermelon
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surface2)
            .border(1.dp, colors.line, RoundedCornerShape(10.dp))
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = label,
            color = colors.text2,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = value,
            color = colors.text,
            fontFamily = WatermelonMono,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            softWrap = true,
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val colors = watermelon
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            color = colors.text2,
            fontSize = 12.sp,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(
            text = value,
            color = colors.text,
            fontFamily = WatermelonMono,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.End,
            softWrap = true,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RomContextDialog(
    rom: RomEntry,
    boxArtPath: String?,
    gameUpdate: SgwGameUpdateInfo? = null,
    onDismiss: () -> Unit,
    onLaunch: () -> Unit,
    onUpdateFromSgw: () -> Unit = {},
    onToggleFavorite: () -> Unit,
    onSetCoreOverride: (Int) -> Unit,
    onPickSoftPatch: () -> Unit,
    onRefreshBoxArt: () -> Unit,
    onPickCustomCover: () -> Unit,
    onViewCover: (String) -> Unit,
    onViewAchievements: () -> Unit,
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
                Text("${rom.platformLabel}  |  ${rom.region}", color = colors.text2, fontFamily = WatermelonMono, fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))

                ContextBtn(Icons.Filled.PlayArrow, stringResource(R.string.ctx_launch), colors.green, onLaunch)
                if (gameUpdate != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(11.dp))
                            .background(colors.green.copy(alpha = 0.16f))
                            .border(1.dp, colors.green.copy(alpha = 0.6f), RoundedCornerShape(11.dp))
                            .clickable(onClick = onUpdateFromSgw)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Icon(Icons.Filled.SystemUpdateAlt, contentDescription = null, tint = colors.green, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.ctx_update_sgw),
                            color = colors.green,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                ContextBtn(
                    if (rom.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    stringResource(if (rom.isFavorite) R.string.ctx_fav_remove else R.string.ctx_fav_add),
                    WatermelonColors.favoriteStar,
                    onToggleFavorite,
                )
                ContextBtn(Icons.Filled.EmojiEvents, stringResource(R.string.ctx_achievements), Color(0xFFFACC15), onViewAchievements)
                if (rom.platform != NativeCore.PLATFORM_GBA) {
                    val nextCore = when (rom.coreOverride) {
                        -1 -> NativeCore.CORE_MGBA
                        NativeCore.CORE_MGBA -> NativeCore.CORE_SAMEBOY
                        else -> -1
                    }
                    val curLabel = when (rom.coreOverride) {
                        NativeCore.CORE_MGBA -> "mGBA"
                        NativeCore.CORE_SAMEBOY -> "SameBoy"
                        else -> stringResource(R.string.auto)
                    }
                    ContextBtn(Icons.Filled.Memory, stringResource(R.string.ctx_core_fmt, curLabel), colors.text) { onSetCoreOverride(nextCore) }
                }
                ContextBtn(Icons.Filled.Tune, stringResource(R.string.ctx_patch), colors.text, onPickSoftPatch)
                ContextBtn(Icons.Filled.Refresh, stringResource(R.string.ctx_refresh_cover), colors.text, onRefreshBoxArt)
                ContextBtn(Icons.Filled.Image, stringResource(R.string.ctx_custom_cover), colors.text, onPickCustomCover)
                if (!boxArtPath.isNullOrBlank()) {
                    ContextBtn(Icons.Filled.Image, stringResource(R.string.ctx_view_cover), colors.green) { onViewCover(boxArtPath) }
                }
                ContextBtn(Icons.Filled.Info, stringResource(R.string.ctx_info), colors.text, onShowInfo)
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
