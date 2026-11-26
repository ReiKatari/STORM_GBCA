package com.stormgbca.emulator.ui.library

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.stormgbca.emulator.model.RomEntry
import com.stormgbca.emulator.ui.theme.AppThemeManager
import com.stormgbca.emulator.ui.theme.SpaceGrotesk
import com.stormgbca.emulator.ui.theme.WatermelonColors
import com.stormgbca.emulator.ui.theme.WatermelonMono
import com.stormgbca.emulator.ui.theme.watermelon
import ru.stormgames.gbca.NativeCore
import java.io.File
import kotlin.math.absoluteValue

fun platformBadgeColors(platform: Int): Pair<Color, Color> = when (platform) {
    NativeCore.PLATFORM_GB -> Color(0xFF8BAC0F) to Color(0xFF0F1704)
    NativeCore.PLATFORM_GBC -> Color(0xFF7B42F6) to Color(0xFFFFFFFF)
    NativeCore.PLATFORM_GBA -> Color(0xFF4F46E5) to Color(0xFFFFFFFF)
    NativeCore.PLATFORM_SGB -> Color(0xFFF59E0B) to Color(0xFF140C02)
    else -> Color(0xFF8BAC0F) to Color(0xFF0F1704)
}

fun shortRegionCode(region: String): String = when (region.uppercase()) {
    "USA", "US" -> "USA"
    "EUROPE", "EUR", "EU" -> "EUR"
    "JAPAN", "JPN", "JP" -> "JPN"
    "RUSSIA", "RUS", "RU" -> "RUS"
    else -> "WLD"
}

private fun fallbackGradient(title: String): Brush {
    val hash = title.hashCode()
    val h1 = (hash.absoluteValue % 360).toFloat()
    val h2 = ((hash / 360).absoluteValue % 360).toFloat()
    return Brush.linearGradient(listOf(Color.hsl(h1, 0.65f, 0.44f), Color.hsl(h2, 0.58f, 0.24f)))
}

fun formatPlayTime(seconds: Long): String {
    if (seconds < 60L) return ""
    val mins = seconds / 60L
    val hours = mins / 60L
    val remMins = mins % 60L
    return if (hours > 0L) "${hours}ч ${remMins}м" else "${remMins}м"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RomGridCard(
    rom: RomEntry,
    boxArtPath: String?,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = watermelon
    val context = LocalContext.current
    val cardStyle = AppThemeManager.cardStyleState.value
    val corner = when (cardStyle) {
        "3d_boxart" -> 6.dp
        "flat_minimal" -> 3.dp
        else -> 14.dp
    }
    val shape = RoundedCornerShape(corner)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else if (focused) 1.03f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "card_scale",
    )

    val (platBg, platFg) = platformBadgeColors(rom.platform)
    val regCode = shortRegionCode(rom.region)
    val hwTags = remember(rom.hwFlags) {
        buildList {
            if (rom.hasRtc) add("RTC")
            if (rom.hasSolar) add("SOLAR")
            if (rom.hasRumble) add("RUMBLE")
            if (rom.hasGyro || rom.hasTilt) add("GYRO")
        }
    }

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .fillMaxWidth()
            .aspectRatio(1f)
            .shadow(if (cardStyle == "flat_minimal") 0.dp else 10.dp, shape)
            .clip(shape)
            .background(fallbackGradient(rom.displayTitle))
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongPress,
            ),
    ) {
        if (!boxArtPath.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(File(boxArtPath))
                    .crossfade(true)
                    .build(),
                contentDescription = rom.displayTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val initials = rom.displayTitle.split(' ', '-', '_').filter { it.isNotBlank() }.take(2)
                .joinToString("") { it.take(1).uppercase() }.ifBlank { "GB" }
            Text(
                text = initials,
                color = Color.White.copy(alpha = 0.24f),
                fontFamily = SpaceGrotesk,
                fontSize = 42.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        // 3D Plastic Box Spine Highlight
        if (cardStyle == "3d_boxart") {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(9.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.62f),
                                Color.White.copy(alpha = 0.30f),
                                Color.Transparent,
                            )
                        )
                    ),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.25f), Color.Transparent)
                        )
                    ),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(if (focused) 2.5.dp else 1.2.dp, if (focused) colors.red else Color.White.copy(alpha = 0.24f), shape),
        )

        // Top-left Platform & Region Badges
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(7.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(platBg)
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            ) {
                Text(rom.platformLabel, color = platFg, fontFamily = WatermelonMono, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Text(regCode, color = Color(0xFFE2E8F0), fontFamily = WatermelonMono, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (rom.isFavorite) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = WatermelonColors.favoriteStar,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(7.dp)
                    .size(15.dp),
            )
        }

        // Bottom Title & Hardware Badges
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))))
                .padding(start = 8.dp, end = 8.dp, top = 22.dp, bottom = 7.dp),
        ) {
            if (hwTags.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                    hwTags.forEach { tag ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xCC00E5FF).copy(alpha = 0.22f))
                                .border(0.7.dp, Color(0xFF00E5FF), RoundedCornerShape(3.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        ) {
                            Text(tag, color = Color(0xFF00E5FF), fontFamily = WatermelonMono, fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Text(
                text = rom.displayTitle,
                color = Color.White,
                fontFamily = SpaceGrotesk,
                fontSize = 11.5.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val pt = formatPlayTime(rom.totalPlaySeconds)
            if (pt.isNotEmpty()) {
                Text(pt, color = Color.White.copy(alpha = 0.68f), fontFamily = WatermelonMono, fontSize = 9.sp)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RomListRow(
    rom: RomEntry,
    boxArtPath: String?,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = watermelon
    val context = LocalContext.current
    val (platBg, platFg) = platformBadgeColors(rom.platform)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(colors.surface)
            .border(1.dp, colors.line, RoundedCornerShape(13.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(fallbackGradient(rom.displayTitle)),
            contentAlignment = Alignment.Center,
        ) {
            if (!boxArtPath.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(File(boxArtPath)).crossfade(true).build(),
                    contentDescription = rom.displayTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(rom.platformLabel, color = Color.White, fontFamily = WatermelonMono, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = rom.displayTitle,
                color = colors.text,
                fontFamily = SpaceGrotesk,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(platBg)
                        .padding(horizontal = 5.dp, vertical = 1.5.dp),
                ) {
                    Text(rom.platformLabel, color = platFg, fontFamily = WatermelonMono, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                Text(shortRegionCode(rom.region), color = colors.text2, fontFamily = WatermelonMono, fontSize = 10.sp)
                if (rom.gameCode.isNotBlank()) {
                    Text(rom.gameCode, color = colors.text3, fontFamily = WatermelonMono, fontSize = 10.sp)
                }
            }
        }
        if (rom.isFavorite) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = WatermelonColors.favoriteStar, modifier = Modifier.size(18.dp))
        }
    }
}
