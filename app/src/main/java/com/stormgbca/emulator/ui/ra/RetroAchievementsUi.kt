package com.stormgbca.emulator.ui.ra

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.stormgbca.emulator.data.GbcaPrefs
import com.stormgbca.emulator.data.RaAchievementBadge
import com.stormgbca.emulator.data.RaGameSession
import com.stormgbca.emulator.data.RetroAchievementsClient
import com.stormgbca.emulator.ui.common.WatermelonCard
import com.stormgbca.emulator.ui.common.WatermelonRowSeparator
import com.stormgbca.emulator.ui.common.WatermelonSectionLabel
import com.stormgbca.emulator.ui.common.WatermelonSwitch
import com.stormgbca.emulator.ui.theme.SpaceGrotesk
import com.stormgbca.emulator.ui.theme.WatermelonMono
import com.stormgbca.emulator.ui.theme.watermelon
import kotlinx.coroutines.launch

@Composable
fun RetroAchievementsAccountDialog(
    onDismiss: () -> Unit,
) {
    val colors = watermelon
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var profile by remember { mutableStateOf(RetroAchievementsClient.getCachedProfile()) }
    var username by remember { mutableStateOf(GbcaPrefs.getRaUsername()) }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    var statusError by remember { mutableStateOf(false) }

    var hardcore by remember { mutableStateOf(GbcaPrefs.isRaHardcore()) }
    var encore by remember { mutableStateOf(GbcaPrefs.isRaEncore()) }
    var unofficial by remember { mutableStateOf(GbcaPrefs.isRaUnofficial()) }
    var notifications by remember { mutableStateOf(GbcaPrefs.isRaNotifications()) }

    LaunchedEffect(Unit) {
        if (profile.connected) {
            busy = true
            profile = RetroAchievementsClient.refreshProfileWithToken()
            busy = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            color = colors.bg,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 560.dp)
                .heightIn(max = 720.dp)
                .border(1.dp, colors.line, RoundedCornerShape(22.dp)),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
            ) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x33D4A017)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = Color(0xFFFACC15), modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("RetroAchievements", color = colors.text, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (profile.connected) Color(0xFF22C55E) else colors.text3),
                            )
                            Text(
                                text = if (profile.connected) "Подключен: ${profile.username}" else "Аккаунт не подключен",
                                color = if (profile.connected) Color(0xFF22C55E) else colors.text2,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    if (profile.connected) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    busy = true
                                    profile = RetroAchievementsClient.refreshProfileWithToken()
                                    statusMsg = if (profile.error.isBlank()) "Профиль обновлен" else profile.error
                                    statusError = profile.error.isNotBlank()
                                    busy = false
                                }
                            },
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Обновить", tint = colors.text2)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = colors.text2)
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Profile Summary Card (when logged in)
                if (profile.connected) {
                    WatermelonCard {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(colors.surface2)
                                        .border(1.5.dp, Color(0xFFFACC15), RoundedCornerShape(14.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (profile.avatarUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context).data(profile.avatarUrl).crossfade(true).build(),
                                            contentDescription = profile.username,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(56.dp),
                                        )
                                    } else {
                                        Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = Color(0xFFFACC15))
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(profile.username, color = colors.text, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                    Text(
                                        text = if (hardcore) "Режим Hardcore активен" else "Режим Softcore активен",
                                        color = if (hardcore) Color(0xFFFACC15) else colors.green,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    if (profile.motto.isNotBlank()) {
                                        Text(profile.motto, color = colors.text3, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                RaStatPill("Hardcore", "${profile.points} pts", Color(0xFFFACC15), Modifier.weight(1f))
                                RaStatPill("Softcore", "${profile.softcorePoints} pts", Color(0xFF38BDF8), Modifier.weight(1f))
                                if (profile.rank > 0) {
                                    RaStatPill("Рейтинг", "#${profile.rank}", colors.green, Modifier.weight(1f))
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.red.copy(alpha = 0.16f))
                                    .border(1.dp, colors.red.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                    .clickable {
                                        GbcaPrefs.clearRaAccount()
                                        profile = RetroAchievementsClient.getCachedProfile()
                                        password = ""
                                        statusMsg = "Вы вышли из аккаунта"
                                        statusError = false
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("Выйти из аккаунта", color = colors.red, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                } else {
                    // Clean spacious Login Form
                    WatermelonSectionLabel("Вход в аккаунт RetroAchievements")
                    WatermelonCard {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            val fieldColors = TextFieldDefaults.outlinedTextFieldColors(
                                textColor = colors.text,
                                cursorColor = colors.green,
                                focusedBorderColor = colors.green,
                                unfocusedBorderColor = colors.line,
                                focusedLabelColor = colors.green,
                                unfocusedLabelColor = colors.text2,
                                backgroundColor = colors.surface2,
                            )
                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it },
                                label = { Text("Логин (Username)") },
                                singleLine = true,
                                colors = fieldColors,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Пароль") },
                                singleLine = true,
                                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showPassword = !showPassword }) {
                                        Icon(
                                            if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                            contentDescription = "Показать пароль",
                                            tint = colors.text2,
                                        )
                                    }
                                },
                                colors = fieldColors,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (busy) colors.surface2 else colors.green)
                                    .clickable(enabled = !busy) {
                                        scope.launch {
                                            busy = true
                                            statusMsg = null
                                            val res = RetroAchievementsClient.login(username, password)
                                            busy = false
                                            if (res.success) {
                                                profile = res.profile
                                                password = ""
                                                statusMsg = "Успешный вход! Очки Hardcore: ${res.profile.points} pts"
                                                statusError = false
                                            } else {
                                                statusMsg = res.error
                                                statusError = true
                                            }
                                        }
                                    }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (busy) {
                                    CircularProgressIndicator(color = colors.green, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                                } else {
                                    Text("Войти в RetroAchievements", color = Color(0xFF05060A), fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                if (!statusMsg.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background((if (statusError) colors.red else colors.green).copy(alpha = 0.14f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = statusMsg!!,
                            color = if (statusError) colors.red else colors.green,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // Modes & Settings Card
                WatermelonSectionLabel("Параметры достижений")
                WatermelonCard {
                    Column {
                        RaToggleRow("Режим Hardcore", "Без читов, перемотки назад и загрузки состояний", hardcore) {
                            hardcore = it; GbcaPrefs.setRaHardcore(it)
                        }
                        WatermelonRowSeparator()
                        RaToggleRow("Режим Encore", "Повторное получение уже открытых достижений в сессии", encore) {
                            encore = it; GbcaPrefs.setRaEncore(it)
                        }
                        WatermelonRowSeparator()
                        RaToggleRow("Неофициальные достижения", "Показывать тестовые и фанатские наборы", unofficial) {
                            unofficial = it; GbcaPrefs.setRaUnofficial(it)
                        }
                        WatermelonRowSeparator()
                        RaToggleRow("Уведомления в игре", "Показывать баннер статуса и полученных достижений", notifications) {
                            notifications = it; GbcaPrefs.setRaNotifications(it)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GameAchievementsDialog(
    gameTitleFallback: String,
    session: RaGameSession?,
    isLoading: Boolean = false,
    onOpenLogin: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val colors = watermelon
    val context = LocalContext.current
    var filterTab by remember { mutableIntStateOf(0) } // 0=All, 1=Unlocked, 2=Locked
    val isLoggedIn = GbcaPrefs.getRaUsername().isNotBlank() && GbcaPrefs.getRaToken().isNotBlank()

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
                .heightIn(max = 740.dp)
                .border(1.dp, colors.line, RoundedCornerShape(22.dp)),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    if (!session?.gameIconUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(session!!.gameIconUrl).crossfade(true).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, colors.line, RoundedCornerShape(10.dp)),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x33D4A017)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = Color(0xFFFACC15))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = session?.gameTitle ?: gameTitleFallback,
                            color = colors.text,
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (session != null) {
                                "ID #${session.gameId} | ${if (GbcaPrefs.isRaHardcore()) "Hardcore" else "Softcore"}"
                            } else {
                                "RetroAchievements"
                            },
                            color = colors.text2,
                            fontFamily = WatermelonMono,
                            fontSize = 11.sp,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = colors.text2)
                    }
                }

                Spacer(Modifier.height(12.dp))

                when {
                    isLoading -> {
                        Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = colors.green)
                                Spacer(Modifier.height(10.dp))
                                Text("Загрузка списка достижений...", color = colors.text2, fontSize = 13.sp)
                            }
                        }
                    }
                    !isLoggedIn -> {
                        WatermelonCard {
                            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Аккаунт RetroAchievements не подключен", color = colors.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Spacer(Modifier.height(6.dp))
                                Text("Войдите в свой аккаунт, чтобы отслеживать прогресс, очки и трофеи в играх GB, GBC и GBA.", color = colors.text2, fontSize = 12.sp)
                                if (onOpenLogin != null) {
                                    Spacer(Modifier.height(12.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(colors.green)
                                            .clickable { onOpenLogin() }
                                            .padding(horizontal = 18.dp, vertical = 10.dp),
                                    ) {
                                        Text("Войти в аккаунт", color = Color(0xFF05060A), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                    session == null -> {
                        WatermelonCard {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Достижения не найдены для этого ROM", color = colors.text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(Modifier.height(6.dp))
                                Text("Для данной версии ROM (или ромхака) отсутствует официальный набор на сервере RetroAchievements.", color = colors.text2, fontSize = 12.sp)
                            }
                        }
                    }
                    else -> {
                        // Mastery & Points Summary Card
                        WatermelonCard {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    RaStatPill("Открыто", "${session.unlockedCount} / ${session.totalCount}", colors.green, Modifier.weight(1f))
                                    RaStatPill("Очки", "${session.earnedPoints} / ${session.totalPoints} pts", Color(0xFFFACC15), Modifier.weight(1f))
                                    RaStatPill("Прогресс", "${session.completionPercent}%", Color(0xFF38BDF8), Modifier.weight(1f))
                                }
                                Spacer(Modifier.height(10.dp))
                                LinearProgressIndicator(
                                    progress = if (session.totalCount > 0) session.unlockedCount.toFloat() / session.totalCount else 0f,
                                    color = if (session.hardcoreUnlockedCount == session.totalCount && session.totalCount > 0) Color(0xFFFACC15) else colors.green,
                                    backgroundColor = colors.surface2,
                                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // Filter tabs
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            listOf(
                                0 to "Все (${session.totalCount})",
                                1 to "Полученные (${session.unlockedCount})",
                                2 to "Неполученные (${session.totalCount - session.unlockedCount})",
                            ).forEach { (idx, label) ->
                                val active = filterTab == idx
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (active) colors.green else colors.surface2)
                                        .border(1.dp, if (active) colors.green else colors.line, RoundedCornerShape(10.dp))
                                        .clickable { filterTab = idx }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = label,
                                        color = if (active) Color(0xFF05060A) else colors.text,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        val filtered = remember(session, filterTab) {
                            when (filterTab) {
                                1 -> session.achievements.filter { it.unlocked || it.unlockedHardcore }
                                2 -> session.achievements.filter { !it.unlocked && !it.unlockedHardcore }
                                else -> session.achievements
                            }
                        }

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f, fill = false).heightIn(max = 460.dp),
                        ) {
                            items(filtered, key = { it.id }) { ach ->
                                AchievementRowCard(ach)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AchievementRowCard(ach: RaAchievementBadge) {
    val colors = watermelon
    val context = LocalContext.current
    val isEarned = ach.unlocked || ach.unlockedHardcore
    val borderCol = when {
        ach.unlockedHardcore -> Color(0xFFFACC15)
        ach.unlocked -> colors.green
        else -> colors.line
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isEarned) colors.surface else colors.surface.copy(alpha = 0.72f))
            .border(1.dp, borderCol.copy(alpha = if (isEarned) 0.8f else 0.45f), RoundedCornerShape(14.dp))
            .padding(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surface2)
                .border(1.5.dp, borderCol, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (ach.badgeUrl.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(ach.badgeUrl).crossfade(true).build(),
                    contentDescription = ach.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp),
                )
            } else {
                Icon(
                    if (isEarned) Icons.Filled.CheckCircle else Icons.Filled.Lock,
                    contentDescription = null,
                    tint = if (isEarned) borderCol else colors.text3,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = ach.title,
                    color = colors.text,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (ach.type.isNotBlank()) {
                    val typeLabel = when (ach.type.lowercase()) {
                        "progression" -> "Сюжет"
                        "win_condition" -> "Финал"
                        "missable" -> "Пропускаемое"
                        else -> ach.type
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.surface2)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(typeLabel, color = colors.text2, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(ach.description, color = colors.text2, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x26FACC15))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text("${ach.points} pts", color = Color(0xFFFACC15), fontFamily = WatermelonMono, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            if (isEarned) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (ach.unlockedHardcore) "HARDCORE" else "ОТКРЫТО",
                    color = borderCol,
                    fontFamily = WatermelonMono,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun RaStatPill(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    val colors = watermelon
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surface2)
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(vertical = 8.dp, horizontal = 6.dp),
    ) {
        Text(value, color = accent, fontFamily = WatermelonMono, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
        Text(label, color = colors.text3, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun RaToggleRow(title: String, subtitle: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    val colors = watermelon
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChanged(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = colors.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, color = colors.text3, fontSize = 11.sp)
        }
        Spacer(Modifier.width(12.dp))
        WatermelonSwitch(checked = checked, onCheckedChange = onChanged)
    }
}
