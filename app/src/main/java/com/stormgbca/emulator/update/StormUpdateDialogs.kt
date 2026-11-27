package com.stormgbca.emulator.update

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.stormgbca.emulator.BuildConfig
import com.stormgbca.emulator.R
import com.stormgbca.emulator.ui.theme.SpaceGrotesk
import com.stormgbca.emulator.ui.theme.WatermelonColors
import com.stormgbca.emulator.ui.theme.WatermelonMono
import com.stormgbca.emulator.ui.theme.watermelon
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private sealed interface ReleaseNoteBlock {
    data class Heading(val text: String, val level: Int) : ReleaseNoteBlock
    data class Bullet(val text: String) : ReleaseNoteBlock
    data class TableRow(val cells: List<String>) : ReleaseNoteBlock
    data class Paragraph(val text: String) : ReleaseNoteBlock
    data object Divider : ReleaseNoteBlock
}

private fun normalizeReleaseLocaleCode(raw: String): String {
    val clean = raw.trim().lowercase(Locale.ROOT).replace('_', '-')
    return when {
        clean == "in" || clean.startsWith("in-") -> "id" + clean.removePrefix("in")
        clean == "iw" || clean.startsWith("iw-") -> "he" + clean.removePrefix("iw")
        else -> clean
    }
}

private fun matchReleaseLanguageHeading(rawHeading: String): String? {
    val trimmed = rawHeading.trim()
        .removePrefix("###").removePrefix("##").removePrefix("#")
        .trim()
        .trim('[', ']', '(', ')')
        .lowercase(Locale.ROOT)
        .replace('_', '-')
    return when {
        trimmed == "ru" || trimmed.contains("русский") || trimmed.contains("russian") -> "ru"
        trimmed == "en" || trimmed.contains("english") || trimmed.contains("английск") -> "en"
        trimmed == "de" || trimmed.contains("deutsch") || trimmed.contains("german") -> "de"
        trimmed == "es" || trimmed.contains("español") || trimmed.contains("espanol") || trimmed.contains("spanish") -> "es"
        trimmed == "fr" || trimmed.contains("français") || trimmed.contains("francais") || trimmed.contains("french") -> "fr"
        trimmed == "id" || trimmed.contains("bahasa indonesia") || trimmed.contains("indonesian") -> "id"
        trimmed == "it" || trimmed.contains("italiano") || trimmed.contains("italian") -> "it"
        trimmed == "ja" || trimmed.contains("日本語") || trimmed.contains("japanese") -> "ja"
        trimmed == "ko" || trimmed.contains("한국어") || trimmed.contains("korean") -> "ko"
        trimmed == "hi" || trimmed.contains("हिन्दी") || trimmed.contains("हिंदी") || trimmed.contains("hindi") -> "hi"
        trimmed == "pt-br" || trimmed == "pt" || trimmed.contains("português") || trimmed.contains("portugues") || trimmed.contains("portuguese") ->
            if (trimmed.contains("br")) "pt-br" else "pt"
        trimmed == "zh-hans" || trimmed == "zh-cn" || trimmed == "zh" || trimmed.contains("简体中文") || trimmed.contains("中文") || trimmed.contains("chinese") ->
            if (trimmed.contains("hans") || trimmed.contains("cn") || trimmed.contains("简体")) "zh-hans" else "zh"
        else -> null
    }
}

internal fun selectLocalizedReleaseNotes(raw: String, locale: Locale = Locale.getDefault()): String {
    val normalized = raw.replace("\r\n", "\n")
    val lines = normalized.lines()
    val commentTagRegex = Regex("""<!--\s*lang\s*:\s*([a-zA-Z_-]+)\s*-->""", RegexOption.IGNORE_CASE)
    val commentEndRegex = Regex("""<!--\s*/lang\s*-->""", RegexOption.IGNORE_CASE)
    val htmlCommentLineRegex = Regex("""^\s*<!--.*-->\s*$""")

    // Pass 1: explicit <!-- lang:XX --> ... <!-- /lang --> blocks
    val markerSections = linkedMapOf<String, MutableList<String>>()
    var activeMarker: String? = null
    for (line in lines) {
        val startMatch = commentTagRegex.find(line)
        if (startMatch != null) {
            activeMarker = normalizeReleaseLocaleCode(startMatch.groupValues[1])
            markerSections.getOrPut(activeMarker) { mutableListOf() }
            val stripped = line.replace(commentTagRegex, "").replace(commentEndRegex, "").trimEnd()
            if (stripped.isNotBlank()) markerSections[activeMarker]?.add(stripped)
            if (commentEndRegex.containsMatchIn(line)) activeMarker = null
            continue
        }
        if (commentEndRegex.containsMatchIn(line)) {
            activeMarker = null
            continue
        }
        val current = activeMarker
        if (current != null && !htmlCommentLineRegex.matches(line)) {
            markerSections[current]?.add(line)
        }
    }

    // Pass 2: Markdown heading sections (## Русский / ## English / ...)
    val headingSections = linkedMapOf<String, MutableList<String>>()
    if (markerSections.size < 2) {
        var activeHeadingLang: String? = null
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("## ") || trimmed.startsWith("### ")) {
                val detected = matchReleaseLanguageHeading(trimmed)
                if (detected != null) {
                    activeHeadingLang = detected
                    headingSections.getOrPut(detected) { mutableListOf() }
                    continue
                }
            }
            val current = activeHeadingLang
            if (current != null && !htmlCommentLineRegex.matches(line)) {
                headingSections[current]?.add(line)
            }
        }
    }

    val chosenMap = when {
        markerSections.size >= 2 -> markerSections
        headingSections.size >= 2 -> headingSections
        else -> null
    }
    if (chosenMap == null) {
        return lines.filterNot { htmlCommentLineRegex.matches(it) }.joinToString("\n").trim()
    }

    val lang = normalizeReleaseLocaleCode(locale.language)
    val country = locale.country.lowercase(Locale.ROOT)
    val script = locale.script.lowercase(Locale.ROOT)
    val candidates = buildList {
        if (lang.isNotEmpty() && script.isNotEmpty()) add("$lang-$script")
        if (lang.isNotEmpty() && country.isNotEmpty()) add("$lang-$country")
        if (lang == "zh") {
            add("zh-hans")
            add("zh-cn")
        }
        if (lang.isNotEmpty()) add(lang)
        if (lang == "pt") add("pt-br")
        add("en")
        add("ru")
    }

    val picked = candidates.firstNotNullOfOrNull { key ->
        chosenMap[key]?.takeIf { list -> list.any { it.isNotBlank() } }
    } ?: chosenMap.values.firstOrNull { list -> list.any { it.isNotBlank() } }

    return picked
        ?.filterNot { htmlCommentLineRegex.matches(it) }
        ?.joinToString("\n")
        ?.trim()
        ?: normalized.trim()
}

private fun parseReleaseNotes(raw: String): List<ReleaseNoteBlock> {
    val lines = raw.replace("\r\n", "\n").lines()
    val result = mutableListOf<ReleaseNoteBlock>()
    for (line in lines) {
        val trimmed = line.trim()
        when {
            trimmed.isEmpty() -> continue
            trimmed.startsWith("<!--") && trimmed.endsWith("-->") -> continue
            trimmed == "---" || trimmed == "***" || trimmed == "___" -> {
                result += ReleaseNoteBlock.Divider
            }
            trimmed.startsWith("### ") -> {
                result += ReleaseNoteBlock.Heading(trimmed.removePrefix("### ").trim(), 3)
            }
            trimmed.startsWith("## ") -> {
                result += ReleaseNoteBlock.Heading(trimmed.removePrefix("## ").trim(), 2)
            }
            trimmed.startsWith("# ") -> {
                result += ReleaseNoteBlock.Heading(trimmed.removePrefix("# ").trim(), 1)
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                result += ReleaseNoteBlock.Bullet(trimmed.drop(2).trim())
            }
            trimmed.startsWith("|") && trimmed.endsWith("|") -> {
                val cells = trimmed.trim('|').split('|').map { it.trim() }
                val isAlignRow = cells.isNotEmpty() && cells.all { cell ->
                    cell.isNotEmpty() && cell.all { ch -> ch == '-' || ch == ':' || ch == ' ' }
                }
                if (!isAlignRow && cells.any { it.isNotEmpty() }) {
                    result += ReleaseNoteBlock.TableRow(cells)
                }
            }
            else -> {
                result += ReleaseNoteBlock.Paragraph(trimmed)
            }
        }
    }
    return result
}

private fun buildInlineStyledText(
    text: String,
    baseColor: Color,
    boldColor: Color,
    codeColor: Color,
): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > i + 2) {
                    withStyle(SpanStyle(color = boldColor, fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    withStyle(SpanStyle(color = baseColor)) { append("**") }
                    i += 2
                }
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > i + 1) {
                    withStyle(
                        SpanStyle(
                            color = codeColor,
                            fontFamily = WatermelonMono,
                            fontWeight = FontWeight.Medium,
                        )
                    ) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    withStyle(SpanStyle(color = baseColor)) { append('`') }
                    i += 1
                }
            }
            else -> {
                val nextBold = text.indexOf("**", i)
                val nextCode = text.indexOf('`', i)
                val nextSpecial = listOf(nextBold, nextCode).filter { it >= 0 }.minOrNull() ?: text.length
                val end = if (nextSpecial == i) i + 1 else nextSpecial
                withStyle(SpanStyle(color = baseColor)) {
                    append(text.substring(i, end))
                }
                i = end
            }
        }
    }
}

private fun formatPublishedDate(rawIso: String): String {
    val clean = rawIso.trim()
    if (clean.isBlank()) return ""
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd",
    )
    for (pattern in patterns) {
        val parsed = runCatching {
            SimpleDateFormat(pattern, Locale.ROOT).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(clean)
        }.getOrNull()
        if (parsed != null) {
            return runCatching {
                SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(parsed)
            }.getOrDefault("")
        }
    }
    return ""
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StormUpdateDialog(
    release: UpdateReleaseInfo,
    downloadState: UpdateDownloadState,
    onStartDownload: () -> Unit,
    onInstallApk: (java.io.File) -> Unit,
    onSkipVersion: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = watermelon
    val context = LocalContext.current
    val cleanCur = BuildConfig.VERSION_NAME.trim().removePrefix("v").removePrefix("V")
    val cleanNew = release.version.trim().removePrefix("v").removePrefix("V")
    val sizeMb = (release.apkAsset?.sizeBytes ?: 0L) / (1024.0 * 1024.0)
    val sizeLabel = if (sizeMb > 0.0) stringResource(R.string.update_dialog_size_mb_fmt, sizeMb) else ""
    val dateLabel = remember(release.publishedAt) { formatPublishedDate(release.publishedAt) }

    val activeLocale = remember(context) {
        AppCompatDelegate.getApplicationLocales()[0]
            ?: context.resources.configuration.locales.let {
                if (!it.isEmpty) it[0] else Locale.getDefault()
            }
    }
    val parsedBlocks = remember(release.changelogMd, cleanNew, activeLocale) {
        val localized = selectLocalizedReleaseNotes(
            raw = release.changelogMd.ifBlank { "STORM GBCA $cleanNew" },
            locale = activeLocale,
        ).ifBlank { "STORM GBCA $cleanNew" }
        parseReleaseNotes(localized)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = colors.surface,
            elevation = 20.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .border(1.2.dp, colors.line, RoundedCornerShape(20.dp)),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.green.copy(alpha = 0.14f))
                            .border(1.dp, colors.green.copy(alpha = 0.45f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SystemUpdate,
                            contentDescription = null,
                            tint = colors.green,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.update_dialog_title),
                            color = colors.text,
                            fontFamily = SpaceGrotesk,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "STORM GBCA",
                            color = colors.text2,
                            fontFamily = WatermelonMono,
                            fontSize = 11.sp,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(colors.surface2)
                            .border(1.dp, colors.line, RoundedCornerShape(9.dp))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = colors.text,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Version Transition Card: Current -> New (zero 'v' prefix)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.surface2)
                            .border(1.dp, colors.line, RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        VersionBadgeColumn(
                            label = stringResource(R.string.update_dialog_current_label),
                            version = cleanCur,
                            accent = false,
                            colors = colors,
                            modifier = Modifier.weight(1f),
                        )
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .size(30.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.green.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = colors.green,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        VersionBadgeColumn(
                            label = stringResource(R.string.update_dialog_new_label),
                            version = cleanNew,
                            accent = true,
                            colors = colors,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // Metadata Chips: Mirror Source, Size, Release Date (wrapped, zero horizontal scroll)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MetadataChip(Icons.Filled.Public, release.sourceLabel, colors, highlighted = true)
                        if (sizeLabel.isNotBlank()) {
                            MetadataChip(Icons.Filled.Storage, sizeLabel, colors)
                        }
                        if (dateLabel.isNotBlank()) {
                            MetadataChip(Icons.Filled.CalendarToday, dateLabel, colors)
                        }
                    }

                    // Changelog Section Label
                    Text(
                        text = stringResource(R.string.update_dialog_whats_new).uppercase(activeLocale),
                        color = colors.text2,
                        fontFamily = WatermelonMono,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.7.sp,
                    )

                    // Scrollable Structured Localized Changelog Card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 110.dp, max = 230.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.bg)
                            .border(1.dp, colors.line, RoundedCornerShape(14.dp))
                            .verticalScroll(rememberScrollState())
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        parsedBlocks.forEach { block ->
                            when (block) {
                                is ReleaseNoteBlock.Divider -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp)
                                            .height(1.dp)
                                            .background(colors.line),
                                    )
                                }
                                is ReleaseNoteBlock.Heading -> {
                                    val topPad = if (block.level <= 2) 4.dp else 2.dp
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = topPad),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(3.dp)
                                                .height(14.dp)
                                                .clip(RoundedCornerShape(1.5.dp))
                                                .background(colors.green),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = buildInlineStyledText(block.text, colors.text, colors.text, colors.green),
                                            color = colors.text,
                                            fontFamily = SpaceGrotesk,
                                            fontSize = if (block.level <= 2) 13.5.sp else 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                                is ReleaseNoteBlock.Bullet -> {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 4.dp),
                                        verticalAlignment = Alignment.Top,
                                    ) {
                                        Text(
                                            text = "-",
                                            color = colors.green,
                                            fontFamily = WatermelonMono,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(end = 7.dp),
                                        )
                                        Text(
                                            text = buildInlineStyledText(block.text, colors.text2, colors.text, colors.green),
                                            fontFamily = SpaceGrotesk,
                                            fontSize = 12.5.sp,
                                            lineHeight = 17.sp,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                                is ReleaseNoteBlock.TableRow -> {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(colors.surface2.copy(alpha = 0.65f))
                                            .padding(horizontal = 8.dp, vertical = 5.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            text = block.cells.joinToString(" | "),
                                            color = colors.text2,
                                            fontFamily = WatermelonMono,
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                                is ReleaseNoteBlock.Paragraph -> {
                                    Text(
                                        text = buildInlineStyledText(block.text, colors.text2, colors.text, colors.green),
                                        fontFamily = SpaceGrotesk,
                                        fontSize = 12.5.sp,
                                        lineHeight = 17.sp,
                                    )
                                }
                            }
                        }
                    }

                    // Download state area
                    when (downloadState) {
                        is UpdateDownloadState.Downloading -> {
                            val progress = if (downloadState.totalBytes > 0) {
                                (downloadState.bytesRead.toFloat() / downloadState.totalBytes.toFloat()).coerceIn(0f, 1f)
                            } else 0f
                            val mbSpeed = downloadState.speedBytesPerSec / (1024.0 * 1024.0)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                LinearProgressIndicator(
                                    progress = progress,
                                    color = colors.green,
                                    backgroundColor = colors.surface3,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                                )
                                Text(
                                    text = stringResource(R.string.update_downloading_fmt, progress * 100f, mbSpeed),
                                    color = colors.text2,
                                    fontFamily = WatermelonMono,
                                    fontSize = 11.5.sp,
                                )
                            }
                        }
                        is UpdateDownloadState.Ready -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(colors.green)
                                    .clickable { onInstallApk(downloadState.apkFile) }
                                    .padding(vertical = 13.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.update_install),
                                    color = Color(0xFF07131A),
                                    fontFamily = SpaceGrotesk,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        is UpdateDownloadState.Error -> {
                            Text(
                                text = stringResource(R.string.update_error_fmt, downloadState.message),
                                color = colors.red,
                                fontFamily = SpaceGrotesk,
                                fontSize = 12.sp,
                            )
                        }
                        UpdateDownloadState.Idle -> {}
                    }

                    if (downloadState !is UpdateDownloadState.Ready) {
                        // Primary Update CTA
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(13.dp))
                                .background(colors.green)
                                .clickable(
                                    enabled = downloadState !is UpdateDownloadState.Downloading,
                                    onClick = onStartDownload,
                                )
                                .padding(vertical = 13.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CloudDownload,
                                    contentDescription = null,
                                    tint = Color(0xFF07131A),
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = stringResource(R.string.update_now),
                                    color = Color(0xFF07131A),
                                    fontFamily = SpaceGrotesk,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }

                        // Secondary Actions: Remind Later + Skip Version
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            SecondaryActionButton(
                                text = stringResource(R.string.update_dialog_remind_later),
                                colors = colors,
                                modifier = Modifier.weight(1f),
                                onClick = onDismiss,
                            )
                            SecondaryActionButton(
                                text = stringResource(R.string.update_dialog_skip_version),
                                colors = colors,
                                modifier = Modifier.weight(1f),
                                onClick = onSkipVersion,
                            )
                        }
                    }

                    // Footer Link to Open Release Page
                    if (release.releasePageUrl.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(release.releasePageUrl))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                tint = colors.green,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = stringResource(R.string.update_dialog_open_page),
                                color = colors.green,
                                fontFamily = SpaceGrotesk,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionBadgeColumn(
    label: String,
    version: String,
    accent: Boolean,
    colors: WatermelonColors,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (accent) colors.green.copy(alpha = 0.14f) else colors.bg)
            .border(
                1.dp,
                if (accent) colors.green.copy(alpha = 0.48f) else colors.line,
                RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            color = colors.text2,
            fontFamily = SpaceGrotesk,
            fontSize = 10.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = version,
            color = if (accent) colors.green else colors.text,
            fontFamily = WatermelonMono,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MetadataChip(
    icon: ImageVector,
    label: String,
    colors: WatermelonColors,
    highlighted: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (highlighted) colors.green.copy(alpha = 0.12f) else colors.surface2)
            .border(
                1.dp,
                if (highlighted) colors.green.copy(alpha = 0.40f) else colors.line,
                RoundedCornerShape(9.dp),
            )
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (highlighted) colors.green else colors.text2,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = label,
            color = if (highlighted) colors.text else colors.text2,
            fontFamily = WatermelonMono,
            fontSize = 11.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun SecondaryActionButton(
    text: String,
    colors: WatermelonColors,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(colors.surface2)
            .border(1.dp, colors.line, RoundedCornerShape(11.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = colors.text,
            fontFamily = SpaceGrotesk,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
