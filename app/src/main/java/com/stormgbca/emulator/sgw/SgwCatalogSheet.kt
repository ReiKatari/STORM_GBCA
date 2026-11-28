package com.stormgbca.emulator.sgw

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Translate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.documentfile.provider.DocumentFile
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.stormgbca.emulator.R
import com.stormgbca.emulator.data.GbcaPrefs
import com.stormgbca.emulator.model.ConsoleSystem
import com.stormgbca.emulator.model.RomEntry
import com.stormgbca.emulator.ui.library.platformBadgeColors
import com.stormgbca.emulator.ui.theme.SpaceGrotesk
import com.stormgbca.emulator.ui.theme.WatermelonColors
import com.stormgbca.emulator.ui.theme.WatermelonMono
import com.stormgbca.emulator.ui.theme.watermelon
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.stormgames.gbca.NativeCore
import java.util.Locale

@Composable
fun SgwCatalogSheet(
    installedRoms: List<RomEntry>,
    initialCatalog: List<SgwCatalogGame>,
    updatesByRomUri: Map<String, SgwGameUpdateInfo>,
    onCatalogUpdated: (List<SgwCatalogGame>) -> Unit,
    onRomInstalledOrUpdated: (newRom: RomEntry, replacedUri: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = watermelon
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var catalog by remember {
        mutableStateOf(
            initialCatalog.ifEmpty { SgwCatalogRepository.getCachedCatalog(context) }
        )
    }
    var isLoading by remember { mutableStateOf(catalog.isEmpty()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember {
        mutableStateOf(if (updatesByRomUri.isNotEmpty()) SgwPlatformFilter.UPDATES else SgwPlatformFilter.ALL)
    }
    var targetFolderUri by remember {
        mutableStateOf(
            GbcaPrefs.getRomFoldersList().firstOrNull()?.let { runCatching { Uri.parse(it) }.getOrNull() }
        )
    }
    var downloadState by remember { mutableStateOf(SgwDownloadState()) }
    var activeDownloadJob by remember { mutableStateOf<Job?>(null) }
    var detailsGame by remember { mutableStateOf<SgwCatalogGame?>(null) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            GbcaPrefs.addRomFolder(uri.toString())
            targetFolderUri = uri
            val label = DocumentFile.fromTreeUri(context, uri)?.name ?: uri.lastPathSegment ?: "ROMs"
            Toast.makeText(context, context.getString(R.string.sgw_folder_selected_fmt, label), Toast.LENGTH_SHORT).show()
        }
    }

    fun refreshCatalog(force: Boolean) {
        scope.launch {
            isLoading = true
            loadError = null
            val res = SgwCatalogRepository.fetchCatalog(context, forceRefresh = force)
            res.onSuccess { list ->
                catalog = list
                onCatalogUpdated(list)
            }.onFailure { err ->
                if (catalog.isEmpty()) {
                    loadError = err.message ?: context.getString(R.string.sgw_error_load)
                }
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshCatalog(force = false)
    }

    val metaByUri = remember(installedRoms) { GbcaPrefs.getSgwInstalledMeta() }
    val updatesByGameId = remember(updatesByRomUri) {
        updatesByRomUri.values.associateBy { it.remoteGame.gameId }
    }
    val installedByGameId = remember(installedRoms, metaByUri) {
        val map = HashMap<Int, RomEntry>()
        for (rom in installedRoms) {
            val meta = metaByUri[rom.uriString]
            if (meta != null && meta.gameId > 0) {
                map[meta.gameId] = rom
            }
        }
        map
    }
    val installedByCanonical = remember(installedRoms) {
        installedRoms.groupBy { rom ->
            val raw = rom.fileName.substringBeforeLast('.').ifBlank { rom.internalTitle }
            rom.consoleSystem to SgwCatalogRepository.canonicalKey(raw)
        }
    }

    val filteredGames = remember(catalog, selectedFilter, searchQuery, updatesByGameId) {
        val q = searchQuery.trim().lowercase(Locale.ROOT)
        catalog.filter { game ->
            val matchesFilter = when (selectedFilter) {
                SgwPlatformFilter.ALL -> true
                SgwPlatformFilter.GB -> game.platform == ConsoleSystem.GB
                SgwPlatformFilter.GBC -> game.platform == ConsoleSystem.GBC
                SgwPlatformFilter.GBA -> game.platform == ConsoleSystem.GBA
                SgwPlatformFilter.TRANSLATIONS -> game.isFromTranslations || game.isRussian
                SgwPlatformFilter.UPDATES -> updatesByGameId.containsKey(game.gameId)
            }
            val matchesQuery = q.isEmpty() ||
                game.finalTitle.lowercase(Locale.ROOT).contains(q) ||
                game.title.lowercase(Locale.ROOT).contains(q) ||
                game.author.lowercase(Locale.ROOT).contains(q)
            matchesFilter && matchesQuery
        }
    }

    fun startDownload(game: SgwCatalogGame, replacingRom: RomEntry?) {
        if (downloadState.active) return
        activeDownloadJob?.cancel()
        activeDownloadJob = scope.launch {
            val res = SgwCatalogRepository.downloadOrUpdateRom(
                ctx = context,
                remoteGame = game,
                replacingRom = replacingRom,
                targetTreeUri = targetFolderUri,
            ) { st ->
                downloadState = st
            }
            res.onSuccess { newRom ->
                onRomInstalledOrUpdated(newRom, replacingRom?.uriString)
                val msg = if (replacingRom != null) {
                    context.getString(R.string.sgw_toast_updated_fmt, game.finalTitle)
                } else {
                    context.getString(R.string.sgw_toast_downloaded_fmt, game.finalTitle)
                }
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                if (err !is kotlinx.coroutines.CancellationException) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.sgw_toast_error_fmt, err.message ?: "Error"),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    val folderLabel = remember(targetFolderUri, context) {
        val uri = targetFolderUri
        if (uri == null) {
            context.getString(R.string.sgw_folder_default)
        } else if (uri.scheme == "file") {
            uri.path?.substringAfterLast('/') ?: "ROMs"
        } else {
            DocumentFile.fromTreeUri(context, uri)?.name ?: uri.lastPathSegment ?: "ROMs"
        }
    }

    Dialog(
        onDismissRequest = {
            if (!downloadState.active) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            color = colors.bg,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.green.copy(alpha = 0.14f))
                            .border(1.dp, colors.green.copy(alpha = 0.45f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Public, contentDescription = null, tint = colors.green, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "STORM GAMES WORLD",
                            color = colors.text,
                            fontFamily = SpaceGrotesk,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(R.string.sgw_catalog_subtitle_fmt, catalog.size, folderLabel),
                            color = colors.text2,
                            fontFamily = WatermelonMono,
                            fontSize = 10.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { folderPicker.launch(null) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = stringResource(R.string.sgw_pick_folder), tint = colors.green, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { refreshCatalog(force = true) }, enabled = !isLoading, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh), tint = colors.text, modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = {
                            if (downloadState.active) {
                                SgwCatalogRepository.cancelActiveDownload()
                                activeDownloadJob?.cancel()
                            }
                            onDismiss()
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close), tint = colors.text, modifier = Modifier.size(20.dp))
                    }
                }

                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))

                // Search bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surface2)
                        .border(1.dp, colors.line, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = colors.green, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(color = colors.text, fontFamily = SpaceGrotesk, fontSize = 13.5.sp),
                        cursorBrush = SolidColor(colors.green),
                        decorationBox = { inner ->
                            Box {
                                if (searchQuery.isEmpty()) {
                                    Text(stringResource(R.string.sgw_search_hint), color = colors.text2, fontSize = 13.5.sp)
                                }
                                inner()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = null,
                            tint = colors.text2,
                            modifier = Modifier.size(17.dp).clickable { searchQuery = "" },
                        )
                    }
                }

                // Filter chips
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    val updatesCount = updatesByGameId.size
                    val chips = listOf(
                        SgwPlatformFilter.ALL to context.getString(R.string.filter_all),
                        SgwPlatformFilter.GB to "GB",
                        SgwPlatformFilter.GBC to "GBC",
                        SgwPlatformFilter.GBA to "GBA",
                        SgwPlatformFilter.TRANSLATIONS to context.getString(R.string.sgw_filter_translations),
                        SgwPlatformFilter.UPDATES to context.getString(R.string.sgw_filter_updates_fmt, updatesCount),
                    )
                    items(chips, key = { it.first.name }) { (filter, label) ->
                        val selected = selectedFilter == filter
                        val highlightUpdates = filter == SgwPlatformFilter.UPDATES && updatesCount > 0 && !selected
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    when {
                                        selected -> colors.green
                                        highlightUpdates -> colors.green.copy(alpha = 0.16f)
                                        else -> colors.surface2
                                    }
                                )
                                .border(
                                    1.dp,
                                    when {
                                        selected -> colors.green
                                        highlightUpdates -> colors.green.copy(alpha = 0.6f)
                                        else -> colors.line
                                    },
                                    RoundedCornerShape(16.dp),
                                )
                                .clickable { selectedFilter = filter }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = label,
                                color = when {
                                    selected -> Color(0xFF05060A)
                                    highlightUpdates -> colors.green
                                    else -> colors.text
                                },
                                fontFamily = SpaceGrotesk,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                    }
                }

                // Active download banner
                if (downloadState.active) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surface)
                            .border(1.dp, colors.green.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.CloudDownload, contentDescription = null, tint = colors.green, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = downloadState.title,
                                color = colors.text,
                                fontFamily = SpaceGrotesk,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${downloadState.percent}%",
                                color = colors.green,
                                fontFamily = WatermelonMono,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.width(10.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.surface2)
                                    .border(1.dp, colors.line, RoundedCornerShape(8.dp))
                                    .clickable {
                                        SgwCatalogRepository.cancelActiveDownload()
                                        activeDownloadJob?.cancel()
                                        downloadState = SgwDownloadState(active = false)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(stringResource(R.string.cancel), color = colors.red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (downloadState.totalBytes > 0L) {
                            LinearProgressIndicator(
                                progress = (downloadState.percent / 100f).coerceIn(0f, 1f),
                                color = colors.green,
                                backgroundColor = colors.surface3,
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
                            )
                        } else {
                            LinearProgressIndicator(
                                color = colors.green,
                                backgroundColor = colors.surface3,
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
                            )
                        }
                        if (downloadState.statusText.isNotBlank()) {
                            Text(downloadState.statusText, color = colors.text2, fontFamily = WatermelonMono, fontSize = 10.5.sp)
                        }
                    }
                }

                // Body
                if (isLoading && catalog.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(color = colors.green, strokeWidth = 3.dp)
                            Text(stringResource(R.string.sgw_loading), color = colors.text2, fontFamily = SpaceGrotesk, fontSize = 13.sp)
                        }
                    }
                } else if (loadError != null && catalog.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(loadError ?: "", color = colors.red, fontSize = 13.sp)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(colors.green)
                                    .clickable { refreshCatalog(force = true) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            ) {
                                Text(stringResource(R.string.refresh), color = Color(0xFF05060A), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                } else if (filteredGames.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.sgw_empty_results), color = colors.text2, fontFamily = SpaceGrotesk, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(filteredGames, key = { "${it.gameId}_${it.releaseId}" }) { game ->
                            val updateInfo = updatesByGameId[game.gameId]
                            val installedMatch = installedByGameId[game.gameId]
                                ?: installedByCanonical[game.platform to SgwCatalogRepository.canonicalKey(game.finalTitle)]?.firstOrNull()
                                ?: installedByCanonical[game.platform to SgwCatalogRepository.canonicalKey(game.title)]?.firstOrNull()

                            SgwCatalogCard(
                                game = game,
                                updateInfo = updateInfo,
                                installedMatch = installedMatch,
                                downloadActive = downloadState.active,
                                colors = colors,
                                onCardClick = { detailsGame = game },
                                onDownloadClick = {
                                    startDownload(game, replacingRom = updateInfo?.localRom)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    detailsGame?.let { game ->
        val updateInfo = updatesByGameId[game.gameId]
        val installedMatch = installedByGameId[game.gameId]
            ?: installedByCanonical[game.platform to SgwCatalogRepository.canonicalKey(game.finalTitle)]?.firstOrNull()
        SgwGameDetailsDialog(
            game = game,
            updateInfo = updateInfo,
            installedMatch = installedMatch,
            downloadActive = downloadState.active,
            colors = colors,
            onDismiss = { detailsGame = null },
            onDownload = {
                detailsGame = null
                startDownload(game, replacingRom = updateInfo?.localRom)
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SgwCatalogCard(
    game: SgwCatalogGame,
    updateInfo: SgwGameUpdateInfo?,
    installedMatch: RomEntry?,
    downloadActive: Boolean,
    colors: WatermelonColors,
    onCardClick: () -> Unit,
    onDownloadClick: () -> Unit,
) {
    val context = LocalContext.current
    val nativePlatform = when (game.platform) {
        ConsoleSystem.GB -> NativeCore.PLATFORM_GB
        ConsoleSystem.GBC -> NativeCore.PLATFORM_GBC
        ConsoleSystem.GBA -> NativeCore.PLATFORM_GBA
    }
    val (platBg, platFg) = platformBadgeColors(nativePlatform)
    val cleanVer = SgwCatalogRepository.cleanVersion(game.version)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(
                1.dp,
                if (updateInfo != null) colors.green.copy(alpha = 0.55f) else colors.line,
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onCardClick)
            .padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(listOf(platBg.copy(alpha = 0.7f), colors.surface3))),
                contentAlignment = Alignment.Center,
            ) {
                if (game.coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(game.coverUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = game.finalTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(game.platform.label, color = platFg, fontFamily = WatermelonMono, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.width(11.dp))

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = game.finalTitle.ifBlank { game.title },
                    color = colors.text,
                    fontFamily = SpaceGrotesk,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SmallPill(game.platform.label, platBg, platFg)
                    if (game.isRussian) {
                        SmallPill("RUS", colors.green.copy(alpha = 0.18f), colors.green, borderColor = colors.green.copy(alpha = 0.5f))
                    } else {
                        val reg = game.regions.firstOrNull()?.uppercase(Locale.ROOT)?.take(3) ?: "ENG"
                        SmallPill(reg, colors.surface2, colors.text2, borderColor = colors.line)
                    }
                    if (cleanVer.isNotBlank()) {
                        SmallPill(cleanVer, colors.surface2, colors.text, borderColor = colors.line)
                    }
                    if (game.sizeLabel.isNotBlank()) {
                        SmallPill(game.sizeLabel, colors.surface2, colors.text2, borderColor = colors.line)
                    }
                    if (game.hasRetroAchievements) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color(0xFF0F172A))
                                .border(0.8.dp, Color(0xFFFACC15).copy(alpha = 0.7f), RoundedCornerShape(5.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                        ) {
                            Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = Color(0xFFFACC15), modifier = Modifier.size(10.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("RA", color = Color(0xFFFACC15), fontFamily = WatermelonMono, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (game.author.isNotBlank() || game.changelog.isNotBlank()) {
                    val preview = listOf(game.author, game.changelog.lines().firstOrNull { it.isNotBlank() }.orEmpty())
                        .filter { it.isNotBlank() }
                        .joinToString(" - ")
                    Text(
                        text = preview,
                        color = colors.text2,
                        fontSize = 11.5.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Action bar
        if (updateInfo != null) {
            val curV = SgwCatalogRepository.cleanVersion(updateInfo.currentVersion)
            val newV = SgwCatalogRepository.cleanVersion(updateInfo.remoteVersion)
            val btnLabel = if (updateInfo.kind == SgwUpdateKind.TRANSLATION_AVAILABLE) {
                stringResource(R.string.sgw_btn_install_translation_fmt, newV)
            } else {
                stringResource(R.string.sgw_btn_update_fmt, curV, newV)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (downloadActive) colors.surface3 else colors.green)
                        .clickable(enabled = !downloadActive, onClick = onDownloadClick)
                        .padding(vertical = 9.dp, horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            imageVector = if (updateInfo.kind == SgwUpdateKind.TRANSLATION_AVAILABLE) Icons.Filled.Translate else Icons.Filled.SystemUpdateAlt,
                            contentDescription = null,
                            tint = Color(0xFF05060A),
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = btnLabel,
                            color = Color(0xFF05060A),
                            fontFamily = SpaceGrotesk,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.sgw_safe_replace_hint),
                    color = colors.green,
                    fontSize = 10.5.sp,
                    maxLines = 2,
                )
            }
        } else if (installedMatch != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.green.copy(alpha = 0.14f))
                        .border(1.dp, colors.green.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                        .padding(vertical = 8.dp),
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = colors.green, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.sgw_status_installed),
                        color = colors.green,
                        fontFamily = SpaceGrotesk,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surface2)
                        .border(1.dp, colors.line, RoundedCornerShape(10.dp))
                        .clickable(enabled = !downloadActive, onClick = onDownloadClick)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Download, contentDescription = stringResource(R.string.sgw_btn_redownload), tint = colors.text, modifier = Modifier.size(16.dp))
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (downloadActive) colors.surface3 else colors.green)
                    .clickable(enabled = !downloadActive, onClick = onDownloadClick)
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Download, contentDescription = null, tint = Color(0xFF05060A), modifier = Modifier.size(16.dp))
                    Text(
                        text = stringResource(R.string.sgw_btn_download),
                        color = Color(0xFF05060A),
                        fontFamily = SpaceGrotesk,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SmallPill(
    label: String,
    bg: Color,
    fg: Color,
    borderColor: Color? = null,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(bg)
            .let { if (borderColor != null) it.border(0.8.dp, borderColor, RoundedCornerShape(5.dp)) else it }
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            color = fg,
            fontFamily = WatermelonMono,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SgwGameDetailsDialog(
    game: SgwCatalogGame,
    updateInfo: SgwGameUpdateInfo?,
    installedMatch: RomEntry?,
    downloadActive: Boolean,
    colors: WatermelonColors,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
) {
    val context = LocalContext.current
    val cleanVer = SgwCatalogRepository.cleanVersion(game.version)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = colors.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .border(1.dp, colors.line, RoundedCornerShape(20.dp)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = game.finalTitle.ifBlank { game.title },
                        color = colors.text,
                        fontFamily = SpaceGrotesk,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close), tint = colors.text, modifier = Modifier.size(18.dp))
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (game.coverUrl.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(game.coverUrl).crossfade(true).build(),
                            contentDescription = game.finalTitle,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.bg),
                        )
                    }

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SmallPill(game.platform.label, colors.surface2, colors.text, colors.line)
                        if (cleanVer.isNotBlank()) SmallPill(cleanVer, colors.surface2, colors.text, colors.line)
                        if (game.sizeLabel.isNotBlank()) SmallPill(game.sizeLabel, colors.surface2, colors.text2, colors.line)
                        if (game.isRussian) SmallPill("RUS", colors.green.copy(alpha = 0.16f), colors.green, colors.green.copy(alpha = 0.5f))
                        if (game.regions.isNotEmpty()) SmallPill(game.regions.joinToString(", "), colors.surface2, colors.text2, colors.line)
                        if (game.author.isNotBlank()) SmallPill(game.author, colors.surface2, colors.text, colors.line)
                    }

                    if (game.changelog.isNotBlank()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.bg)
                                .border(1.dp, colors.line, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.sgw_details_changelog),
                                color = colors.green,
                                fontFamily = WatermelonMono,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = game.changelog,
                                color = colors.text2,
                                fontSize = 12.5.sp,
                                lineHeight = 17.sp,
                            )
                        }
                    }

                    if (game.description.isNotBlank()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.bg)
                                .border(1.dp, colors.line, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.sgw_details_description),
                                color = colors.text2,
                                fontFamily = WatermelonMono,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = game.description,
                                color = colors.text2,
                                fontSize = 12.5.sp,
                                lineHeight = 17.sp,
                            )
                        }
                    }
                }

                if (updateInfo != null) {
                    Text(
                        text = stringResource(R.string.sgw_safe_replace_hint),
                        color = colors.green,
                        fontSize = 11.sp,
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (downloadActive) colors.surface3 else colors.green)
                        .clickable(enabled = !downloadActive, onClick = onDownload)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val ctaLabel = when {
                        updateInfo != null && updateInfo.kind == SgwUpdateKind.TRANSLATION_AVAILABLE ->
                            stringResource(R.string.sgw_btn_install_translation_fmt, SgwCatalogRepository.cleanVersion(updateInfo.remoteVersion))
                        updateInfo != null ->
                            stringResource(R.string.sgw_btn_update_fmt, SgwCatalogRepository.cleanVersion(updateInfo.currentVersion), SgwCatalogRepository.cleanVersion(updateInfo.remoteVersion))
                        installedMatch != null -> stringResource(R.string.sgw_btn_redownload)
                        else -> stringResource(R.string.sgw_btn_download)
                    }
                    Text(
                        text = ctaLabel,
                        color = Color(0xFF05060A),
                        fontFamily = SpaceGrotesk,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
