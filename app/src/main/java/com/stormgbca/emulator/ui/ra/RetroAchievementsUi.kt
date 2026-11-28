package com.stormgbca.emulator.ui.ra

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.res.stringResource
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
import com.stormgbca.emulator.R
import com.stormgbca.emulator.data.GbcaPrefs
import com.stormgbca.emulator.data.RaAchievementBadge
import com.stormgbca.emulator.data.RaConnectionDiagnostics
import com.stormgbca.emulator.data.RaGameSession
import com.stormgbca.emulator.data.RaLeaderboard
import com.stormgbca.emulator.data.RaLeaderboardEntry
import com.stormgbca.emulator.data.RetroAchievementsClient
import com.stormgbca.emulator.ui.common.UnifiedBackButton
import com.stormgbca.emulator.ui.common.WatermelonCard
import com.stormgbca.emulator.ui.common.WatermelonRowSeparator
import com.stormgbca.emulator.ui.common.WatermelonSectionLabel
import com.stormgbca.emulator.ui.common.WatermelonSwitch
import com.stormgbca.emulator.ui.theme.SpaceGrotesk
import com.stormgbca.emulator.ui.theme.WatermelonMono
import com.stormgbca.emulator.ui.theme.watermelon
import kotlinx.coroutines.launch

data class RaMeasuredProgress(
    val value: Long,
    val target: Long,
    val formatted: String = "",
)

data class RaUnlockToastData(
    val achievement: RaAchievementBadge,
    val hardcore: Boolean,
    val key: Long = System.currentTimeMillis(),
)

data class RaActiveLeaderboardData(
    val id: Int,
    val title: String,
    val scoreText: String,
)

/**
 * Full STORM DS-parity RetroAchievements settings panel (usable inside Settings sub-screen OR modal dialog).
 * Zero horizontal scrolling, 100% localized via stringResource, high-contrast white/black text.
 */
@Composable
fun RetroAchievementsSettingsContent(
    modifier: Modifier = Modifier,
    onHardcoreChanged: ((Boolean) -> Unit)? = null,
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
    var diagResult by remember { mutableStateOf<RaConnectionDiagnostics?>(null) }
    var diagBusy by remember { mutableStateOf(false) }

    var raEnabled by remember { mutableStateOf(GbcaPrefs.isRaEnabled()) }
    var hardcore by remember { mutableStateOf(GbcaPrefs.isRaHardcore()) }
    var richPresence by remember { mutableStateOf(GbcaPrefs.isRaRichPresence()) }
    var encore by remember { mutableStateOf(GbcaPrefs.isRaEncore()) }
    var unofficial by remember { mutableStateOf(GbcaPrefs.isRaUnofficial()) }
    var offlineSoftcore by remember { mutableStateOf(GbcaPrefs.isRaOfflineSoftcore()) }
    var challengeInd by remember { mutableStateOf(GbcaPrefs.isRaChallengeIndicators()) }
    var progressInd by remember { mutableStateOf(GbcaPrefs.isRaProgressIndicators()) }
    var leaderboardInd by remember { mutableStateOf(GbcaPrefs.isRaLeaderboardIndicators()) }
    var notifications by remember { mutableStateOf(GbcaPrefs.isRaNotifications()) }
    var notifPos by remember { mutableStateOf(GbcaPrefs.getRaNotifPosition()) }
    var notifStyle by remember { mutableStateOf(GbcaPrefs.getRaNotifStyle()) }
    var notifSound by remember { mutableStateOf(GbcaPrefs.getRaNotifSound()) }
    var pendingCount by remember { mutableIntStateOf(GbcaPrefs.getRaPendingQueueCount()) }

    LaunchedEffect(Unit) {
        if (profile.connected) {
            busy = true
            profile = RetroAchievementsClient.refreshProfileWithToken()
            busy = false
        }
    }

    Column(modifier = modifier) {
        // Profile Summary Card or Login Form
        if (profile.connected) {
            WatermelonCard {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(58.dp)
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
                                    modifier = Modifier.size(58.dp),
                                )
                            } else {
                                Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = Color(0xFFFACC15))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(profile.username, color = colors.text, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF22C55E)))
                                Text(
                                    text = if (hardcore) stringResource(R.string.ra_mode_hardcore) else stringResource(R.string.ra_mode_casual),
                                    color = if (hardcore) Color(0xFFFACC15) else Color(0xFF38BDF8),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            if (profile.motto.isNotBlank()) {
                                Text(profile.motto, color = colors.text2, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        IconButton(
                            onClick = {
                                scope.launch {
                                    busy = true
                                    profile = RetroAchievementsClient.refreshProfileWithToken()
                                    statusMsg = if (profile.error.isBlank()) "OK" else profile.error
                                    statusError = profile.error.isNotBlank()
                                    busy = false
                                }
                            },
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh), tint = colors.text)
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        RaStatPill("Hardcore", "${profile.points} pts", Color(0xFFFACC15), Modifier.weight(1f))
                        RaStatPill("Casual", "${profile.softcorePoints} pts", Color(0xFF38BDF8), Modifier.weight(1f))
                        if (profile.rank > 0) {
                            RaStatPill(stringResource(R.string.ra_rank), "#${profile.rank}", colors.green, Modifier.weight(1f))
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.red.copy(alpha = 0.16f))
                            .border(1.dp, colors.red.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                            .clickable {
                                GbcaPrefs.clearRaAccount()
                                profile = RetroAchievementsClient.getCachedProfile()
                                password = ""
                                statusMsg = null
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(R.string.ra_sign_out), color = colors.red, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        } else {
            WatermelonSectionLabel(stringResource(R.string.ra_login_title))
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
                        label = { Text(stringResource(R.string.ra_username)) },
                        singleLine = true,
                        colors = fieldColors,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.ra_password)) },
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = null,
                                    tint = colors.text,
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
                                        statusMsg = "OK - ${res.profile.username} (${res.profile.points} pts)"
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
                            Text(stringResource(R.string.ra_sign_in), color = Color(0xFF05060A), fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        if (!statusMsg.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background((if (statusError) colors.red else colors.green).copy(alpha = 0.14f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(statusMsg!!, color = if (statusError) colors.red else colors.green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // Core Integration Modes (Full STORM DS Parity)
        WatermelonSectionLabel("RetroAchievements")
        WatermelonCard {
            RaToggleRow(stringResource(R.string.ra_enable), stringResource(R.string.ra_enable_sub), raEnabled) {
                raEnabled = it; GbcaPrefs.setRaEnabled(it)
            }
            WatermelonRowSeparator()
            RaToggleRow(stringResource(R.string.ra_hardcore), stringResource(R.string.ra_hardcore_sub), hardcore) {
                hardcore = it; GbcaPrefs.setRaHardcore(it)
                if (it) { richPresence = true; GbcaPrefs.setRaRichPresence(true) }
                onHardcoreChanged?.invoke(it)
            }
            WatermelonRowSeparator()
            RaToggleRow(stringResource(R.string.ra_rich_presence), stringResource(R.string.ra_rich_presence_sub), if (hardcore) true else richPresence) {
                if (hardcore) {
                    richPresence = true
                    GbcaPrefs.setRaRichPresence(true)
                } else {
                    richPresence = it
                    GbcaPrefs.setRaRichPresence(it)
                }
            }
            WatermelonRowSeparator()
            RaToggleRow(stringResource(R.string.ra_encore), stringResource(R.string.ra_encore_sub), encore) {
                encore = it; GbcaPrefs.setRaEncore(it)
            }
            WatermelonRowSeparator()
            RaToggleRow(stringResource(R.string.ra_unofficial), stringResource(R.string.ra_unofficial_sub), unofficial) {
                unofficial = it; GbcaPrefs.setRaUnofficial(it)
            }
            WatermelonRowSeparator()
            RaToggleRow(stringResource(R.string.ra_offline_casual), stringResource(R.string.ra_offline_casual_sub), offlineSoftcore) {
                offlineSoftcore = it; GbcaPrefs.setRaOfflineSoftcore(it)
            }
            WatermelonRowSeparator()
            RaToggleRow(stringResource(R.string.ra_challenge_ind), "", challengeInd) {
                challengeInd = it; GbcaPrefs.setRaChallengeIndicators(it)
            }
            WatermelonRowSeparator()
            RaToggleRow(stringResource(R.string.ra_progress_ind), "", progressInd) {
                progressInd = it; GbcaPrefs.setRaProgressIndicators(it)
            }
            WatermelonRowSeparator()
            RaToggleRow(stringResource(R.string.ra_lb_ind), "", leaderboardInd) {
                leaderboardInd = it; GbcaPrefs.setRaLeaderboardIndicators(it)
            }
        }

        // Notifications & Sound (ZERO horizontal scrolling - clean segmented rows)
        WatermelonSectionLabel(stringResource(R.string.ra_notif_section))
        WatermelonCard {
            RaToggleRow(stringResource(R.string.ra_notif_enabled), "", notifications) {
                notifications = it; GbcaPrefs.setRaNotifications(it)
            }
            WatermelonRowSeparator()
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.ra_notif_pos), color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("top" to stringResource(R.string.ra_pos_top), "bottom" to stringResource(R.string.ra_pos_bottom)).forEach { (k, label) ->
                        val sel = notifPos == k
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (sel) colors.green else colors.surface2)
                                .border(1.dp, if (sel) colors.green else colors.line, RoundedCornerShape(9.dp))
                                .clickable { notifPos = k; GbcaPrefs.setRaNotifPosition(k) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(label, color = if (sel) Color(0xFF05060A) else colors.text, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                Text(stringResource(R.string.ra_notif_style), color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("detailed" to stringResource(R.string.ra_style_detailed), "compact" to stringResource(R.string.ra_style_compact)).forEach { (k, label) ->
                        val sel = notifStyle == k
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (sel) colors.green else colors.surface2)
                                .border(1.dp, if (sel) colors.green else colors.line, RoundedCornerShape(9.dp))
                                .clickable { notifStyle = k; GbcaPrefs.setRaNotifStyle(k) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(label, color = if (sel) Color(0xFF05060A) else colors.text, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                Text(stringResource(R.string.ra_notif_sound), color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                val sounds = listOf(
                    "chime" to stringResource(R.string.ra_sound_chime),
                    "trophy" to stringResource(R.string.ra_sound_trophy),
                    "retro" to stringResource(R.string.ra_sound_retro),
                    "none" to stringResource(R.string.ra_sound_none),
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    sounds.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            pair.forEach { (k, label) ->
                                val sel = notifSound == k
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(if (sel) colors.green else colors.surface2)
                                        .border(1.dp, if (sel) colors.green else colors.line, RoundedCornerShape(9.dp))
                                        .clickable { notifSound = k; GbcaPrefs.setRaNotifSound(k) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(label, color = if (sel) Color(0xFF05060A) else colors.text, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sync & Diagnostics (STORM DS Parity: Pending Sync + Test Connection!)
        WatermelonSectionLabel(stringResource(R.string.ra_test_connection))
        WatermelonCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        scope.launch {
                            val synced = RetroAchievementsClient.syncPendingSubmissions()
                            pendingCount = GbcaPrefs.getRaPendingQueueCount()
                            statusMsg = if (synced > 0) "Synced $synced pending submission(s)" else context.getString(R.string.ra_sync_all_synced)
                            statusError = false
                        }
                    }
                    .padding(14.dp),
            ) {
                Icon(Icons.Filled.CloudSync, contentDescription = null, tint = colors.green, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.ra_sync_pending), color = colors.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        text = if (pendingCount == 0) stringResource(R.string.ra_sync_all_synced) else "$pendingCount pending",
                        color = colors.text2,
                        fontSize = 11.5.sp,
                    )
                }
            }
            WatermelonRowSeparator()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !diagBusy) {
                        scope.launch {
                            diagBusy = true
                            diagResult = RetroAchievementsClient.testConnection()
                            diagBusy = false
                        }
                    }
                    .padding(14.dp),
            ) {
                Icon(Icons.Filled.NetworkCheck, contentDescription = null, tint = colors.green, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.ra_test_connection), color = colors.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(stringResource(R.string.ra_test_connection_sub), color = colors.text2, fontSize = 11.5.sp)
                }
                if (diagBusy) {
                    CircularProgressIndicator(color = colors.green, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                }
            }
            diagResult?.let { d ->
                WatermelonRowSeparator()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background((if (d.serverReachable) Color(0xFF22C55E) else colors.red).copy(alpha = 0.14f))
                        .padding(12.dp),
                ) {
                    Icon(
                        if (d.serverReachable) Icons.Filled.CheckCircle else Icons.Filled.Error,
                        contentDescription = null,
                        tint = if (d.serverReachable) Color(0xFF22C55E) else colors.red,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(d.message, color = colors.text, fontFamily = WatermelonMono, fontSize = 11.5.sp)
                }
            }
        }
    }
}

@Composable
fun RetroAchievementsAccountDialog(
    onHardcoreChanged: ((Boolean) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val colors = watermelon
    val profile = RetroAchievementsClient.getCachedProfile()

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
                .heightIn(max = 760.dp)
                .border(1.dp, colors.line, RoundedCornerShape(22.dp)),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x33D4A017)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = Color(0xFFFACC15), modifier = Modifier.size(21.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("RetroAchievements", color = colors.text, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(
                            text = if (profile.connected) {
                                stringResource(R.string.ra_connected_as, profile.username)
                            } else {
                                stringResource(R.string.ra_not_connected)
                            },
                            color = if (profile.connected) Color(0xFF22C55E) else colors.text2,
                            fontSize = 12.sp,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close), tint = colors.text)
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    RetroAchievementsSettingsContent(onHardcoreChanged = onHardcoreChanged)
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
fun GameAchievementsDialog(
    gameTitleFallback: String,
    session: RaGameSession?,
    isLoading: Boolean = false,
    raHardcoreActive: Boolean = GbcaPrefs.isRaHardcore(),
    richPresenceText: String = "",
    primedIds: Set<Int> = emptySet(),
    measuredMap: Map<Int, RaMeasuredProgress> = emptyMap(),
    onToggleHardcoreMode: ((Boolean) -> Unit)? = null,
    onOpenLogin: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val colors = watermelon
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var filterTab by remember { mutableIntStateOf(0) } // 0=All, 1=Unlocked, 2=Locked, 3=Missable, 4=Leaderboards
    var selectedLb by remember { mutableStateOf<RaLeaderboard?>(null) }
    var lbEntries by remember { mutableStateOf<List<RaLeaderboardEntry>>(emptyList()) }
    var lbLoading by remember { mutableStateOf(false) }
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
                .heightIn(max = 760.dp)
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
                                .size(46.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, colors.line, RoundedCornerShape(10.dp)),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
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
                                "ID #${session.gameId} | ${if (raHardcoreActive) stringResource(R.string.ra_mode_hardcore) else stringResource(R.string.ra_mode_casual)}"
                            } else {
                                "RetroAchievements"
                            },
                            color = if (session != null && raHardcoreActive) Color(0xFFFACC15) else colors.text2,
                            fontFamily = WatermelonMono,
                            fontSize = 11.sp,
                        )
                    }
                    if (session != null && onToggleHardcoreMode != null) {
                        val modeColor = if (raHardcoreActive) Color(0xFFFACC15) else Color(0xFF38BDF8)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(9.dp))
                                .background(modeColor.copy(alpha = 0.18f))
                                .border(1.dp, modeColor, RoundedCornerShape(9.dp))
                                .clickable { onToggleHardcoreMode(!raHardcoreActive) }
                                .padding(horizontal = 9.dp, vertical = 5.dp),
                        ) {
                            Text(
                                text = if (raHardcoreActive) "Hardcore" else "Casual",
                                color = modeColor,
                                fontFamily = WatermelonMono,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    if (session != null) {
                        IconButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(session.webUrl)))
                                }
                            },
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = stringResource(R.string.ra_open_web), tint = colors.green)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close), tint = colors.text)
                    }
                }

                Spacer(Modifier.height(12.dp))

                when {
                    isLoading -> {
                        Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = colors.green)
                                Spacer(Modifier.height(10.dp))
                                Text(stringResource(R.string.ra_loading), color = colors.text, fontSize = 13.sp)
                            }
                        }
                    }
                    !isLoggedIn -> {
                        WatermelonCard {
                            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(stringResource(R.string.ra_not_connected), color = colors.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Spacer(Modifier.height(6.dp))
                                Text(stringResource(R.string.ra_enable_sub), color = colors.text2, fontSize = 12.sp)
                                if (onOpenLogin != null) {
                                    Spacer(Modifier.height(12.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(colors.green)
                                            .clickable { onOpenLogin() }
                                            .padding(horizontal = 18.dp, vertical = 10.dp),
                                    ) {
                                        Text(stringResource(R.string.ra_sign_in), color = Color(0xFF05060A), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                    session == null -> {
                        WatermelonCard {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(stringResource(R.string.ra_not_found_title), color = colors.text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(Modifier.height(6.dp))
                                Text(stringResource(R.string.ra_not_found_desc), color = colors.text2, fontSize = 12.sp)
                            }
                        }
                    }
                    else -> {
                        // STORM DS-Style Dual Hardcore vs Softcore Progress Summary Card
                        WatermelonCard {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    RaStatPill(stringResource(R.string.ra_unlocked_label), "${session.unlockedCount}/${session.totalCount}", colors.green, Modifier.weight(1f))
                                    RaStatPill("Hardcore", "${session.hardcoreUnlockedCount}/${session.totalCount}", Color(0xFFFACC15), Modifier.weight(1f))
                                    RaStatPill(stringResource(R.string.ra_points), "${session.earnedPoints}/${session.totalPoints}", Color(0xFF38BDF8), Modifier.weight(1f))
                                }
                                LinearProgressIndicator(
                                    progress = if (session.totalCount > 0) session.unlockedCount.toFloat() / session.totalCount else 0f,
                                    color = if (session.hardcoreUnlockedCount == session.totalCount && session.totalCount > 0) Color(0xFFFACC15) else colors.green,
                                    backgroundColor = colors.surface2,
                                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
                                )
                                if (richPresenceText.isNotBlank()) {
                                    Text(
                                        text = richPresenceText,
                                        color = colors.green,
                                        fontFamily = WatermelonMono,
                                        fontSize = 11.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // 2-row compact filter grid (ZERO horizontal scrolling!)
                        val tabs = listOf(
                            0 to "${stringResource(R.string.ra_tab_all)} (${session.totalCount})",
                            1 to "${stringResource(R.string.ra_tab_unlocked)} (${session.unlockedCount})",
                            2 to "${stringResource(R.string.ra_tab_locked)} (${session.totalCount - session.unlockedCount})",
                            3 to "${stringResource(R.string.ra_tab_missable)} (${session.missableCount})",
                            4 to "${stringResource(R.string.ra_tab_leaderboards)} (${session.leaderboards.size})",
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                tabs.take(3).forEach { (idx, label) ->
                                    RaTabChip(label, filterTab == idx, Modifier.weight(1f)) { filterTab = idx; selectedLb = null }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                tabs.drop(3).forEach { (idx, label) ->
                                    RaTabChip(label, filterTab == idx, Modifier.weight(1f)) { filterTab = idx; selectedLb = null }
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        if (filterTab == 4) {
                            // Leaderboards tab
                            if (session.leaderboards.isEmpty()) {
                                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                    Text(stringResource(R.string.ra_no_leaderboards), color = colors.text2, fontSize = 13.sp)
                                }
                            } else if (selectedLb != null) {
                                Column(modifier = Modifier.weight(1f, fill = false).heightIn(max = 420.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        Text(selectedLb!!.title, color = colors.green, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                        Text(
                                            stringResource(R.string.back),
                                            color = colors.text,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.clickable { selectedLb = null }.padding(6.dp),
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    if (lbLoading) {
                                        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator(color = colors.green)
                                        }
                                    } else {
                                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            items(lbEntries) { e ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(colors.surface2)
                                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Text("#${e.rank}", color = Color(0xFFFACC15), fontFamily = WatermelonMono, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(38.dp))
                                                    Text(e.user, color = colors.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                                    Text(e.scoreFormatted, color = colors.green, fontFamily = WatermelonMono, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f, fill = false).heightIn(max = 420.dp),
                                ) {
                                    items(session.leaderboards, key = { it.id }) { lb ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(colors.surface2)
                                                .border(1.dp, colors.line, RoundedCornerShape(12.dp))
                                                .clickable {
                                                    selectedLb = lb
                                                    lbEntries = emptyList()
                                                    lbLoading = true
                                                    scope.launch {
                                                        lbEntries = RetroAchievementsClient.fetchLeaderboardEntries(lb.id)
                                                        lbLoading = false
                                                    }
                                                }
                                                .padding(12.dp),
                                        ) {
                                            Icon(Icons.Filled.Leaderboard, contentDescription = null, tint = Color(0xFFFACC15), modifier = Modifier.size(22.dp))
                                            Spacer(Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(lb.title, color = colors.text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                if (lb.description.isNotBlank()) {
                                                    Text(lb.description, color = colors.text2, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            val filtered = remember(session, filterTab) {
                                when (filterTab) {
                                    1 -> session.achievements.filter { it.unlocked || it.unlockedHardcore }
                                    2 -> session.achievements.filter { !it.unlocked && !it.unlockedHardcore }
                                    3 -> session.achievements.filter { it.type.equals("missable", ignoreCase = true) }
                                    else -> session.achievements
                                }
                            }

                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f, fill = false).heightIn(max = 420.dp),
                            ) {
                                items(filtered, key = { it.id }) { ach ->
                                    AchievementRowCard(
                                        ach = ach,
                                        isPrimed = primedIds.contains(ach.id),
                                        measured = measuredMap[ach.id],
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    UnifiedBackButton(onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun RaTabChip(label: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = watermelon
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) colors.green else colors.surface2)
            .border(1.dp, if (active) colors.green else colors.line, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (active) Color(0xFF05060A) else colors.text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AchievementRowCard(
    ach: RaAchievementBadge,
    isPrimed: Boolean = false,
    measured: RaMeasuredProgress? = null,
) {
    val colors = watermelon
    val context = LocalContext.current
    val isEarned = ach.unlocked || ach.unlockedHardcore
    val borderCol = when {
        ach.unlockedHardcore -> Color(0xFFFACC15)
        ach.unlocked -> Color(0xFF38BDF8)
        isPrimed -> Color(0xFFFACC15)
        else -> colors.line
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(1.dp, borderCol.copy(alpha = if (isEarned || isPrimed) 0.85f else 0.5f), RoundedCornerShape(14.dp))
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
                    tint = if (isEarned) borderCol else colors.text2,
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
                if (isPrimed && !isEarned) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x33FACC15))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    ) {
                        Text("PRIMED", color = Color(0xFFFACC15), fontFamily = WatermelonMono, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (ach.type.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.surface2)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(ach.type.uppercase(), color = colors.green, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(ach.description, color = colors.text2, fontSize = 11.5.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!isEarned && measured != null && measured.target > 0L) {
                Spacer(Modifier.height(4.dp))
                val frac = (measured.value.toFloat() / measured.target.toFloat()).coerceIn(0f, 1f)
                val label = measured.formatted.ifBlank { "${measured.value}/${measured.target}" }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(
                        progress = frac,
                        color = Color(0xFF38BDF8),
                        backgroundColor = colors.surface2,
                        modifier = Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)),
                    )
                    Text(label, color = Color(0xFF38BDF8), fontFamily = WatermelonMono, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                }
            }
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
                    text = if (ach.unlockedHardcore) "HARDCORE" else "CASUAL",
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
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
            .padding(vertical = 8.dp, horizontal = 6.dp),
    ) {
        Text(value, color = accent, fontFamily = WatermelonMono, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
        Text(label, color = colors.text2, fontSize = 10.sp, maxLines = 1)
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
            if (subtitle.isNotBlank()) {
                Text(subtitle, color = colors.text2, fontSize = 11.5.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        WatermelonSwitch(checked = checked, onCheckedChange = onChanged)
    }
}

@Composable
fun RaStartupSessionBanner(
    session: RaGameSession,
    hardcore: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val accent = if (hardcore) Color(0xFFFACC15) else Color(0xFF38BDF8)
    val unlocked = if (hardcore) session.hardcoreUnlockedCount else session.unlockedCount
    val modeLabel = if (hardcore) {
        stringResource(R.string.ra_banner_hardcore)
    } else {
        stringResource(R.string.ra_banner_casual)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .widthIn(max = 420.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xE80A0E18))
            .border(1.5.dp, accent, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color(0xFF141A29))
                .border(1.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (session.gameIconUrl.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(session.gameIconUrl).crossfade(true).build(),
                    contentDescription = session.gameTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(38.dp),
                )
            } else {
                Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = session.gameTitle,
                color = Color.White,
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = modeLabel,
                color = accent,
                fontFamily = WatermelonMono,
                fontWeight = FontWeight.Bold,
                fontSize = 10.5.sp,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(accent.copy(alpha = 0.2f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = "$unlocked / ${session.totalCount}",
                color = accent,
                fontFamily = WatermelonMono,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
fun RaUnlockToastCard(
    toast: RaUnlockToastData,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val ach = toast.achievement
    val accent = if (toast.hardcore) Color(0xFFFACC15) else Color(0xFF38BDF8)
    val isDetailed = GbcaPrefs.getRaNotifStyle() != "compact"
    val badgeUrl = if (ach.badgeName.isNotBlank()) {
        "https://media.retroachievements.org/Badge/${ach.badgeName}.png"
    } else ""

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .widthIn(max = 380.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xF00B101C))
            .border(1.8.dp, accent, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(if (isDetailed) 46.dp else 36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF161D2E))
                .border(1.5.dp, accent, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (badgeUrl.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(badgeUrl).crossfade(true).build(),
                    contentDescription = ach.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(if (isDetailed) 46.dp else 36.dp),
                )
            } else {
                Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (toast.hardcore) "HARDCORE UNLOCK" else "CASUAL UNLOCK",
                    color = accent,
                    fontFamily = WatermelonMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.5.sp,
                )
                Text(
                    text = "+${ach.points} pts",
                    color = accent,
                    fontFamily = WatermelonMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.5.sp,
                )
            }
            Text(
                text = ach.title,
                color = Color.White,
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (isDetailed && ach.description.isNotBlank()) {
                Text(
                    text = ach.description,
                    color = Color(0xFFCBD5E1),
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun RaChallengeOverlayStrip(
    primed: List<RaAchievementBadge>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        primed.take(4).forEach { ach ->
            val url = if (ach.badgeName.isNotBlank()) {
                "https://media.retroachievements.org/Badge/${ach.badgeName}.png"
            } else ""
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0xE00B101C))
                    .border(1.5.dp, Color(0xFFFACC15), RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (url.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(url).crossfade(true).build(),
                        contentDescription = ach.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(34.dp),
                    )
                } else {
                    Icon(Icons.Filled.Bolt, contentDescription = ach.title, tint = Color(0xFFFACC15), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun RaMeasuredPill(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xE00A0E18))
            .border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Icon(Icons.Filled.Shield, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
        Text(
            text = text,
            color = Color.White,
            fontFamily = WatermelonMono,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun RaLeaderboardTrackerPill(
    lb: RaActiveLeaderboardData,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xE00A0E18))
            .border(1.dp, Color(0xFFFACC15), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Icon(Icons.Filled.Leaderboard, contentDescription = null, tint = Color(0xFFFACC15), modifier = Modifier.size(14.dp))
        Text(
            text = "${lb.title}: ${lb.scoreText}",
            color = Color(0xFFFACC15),
            fontFamily = WatermelonMono,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun RaSwitchToHardcoreConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = watermelon
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = colors.surface,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFFACC15), RoundedCornerShape(18.dp)),
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFFACC15), modifier = Modifier.size(22.dp))
                    Text(
                        text = stringResource(R.string.ra_switch_to_hardcore_title),
                        color = Color(0xFFFACC15),
                        fontFamily = SpaceGrotesk,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = stringResource(R.string.ra_switch_to_hardcore_msg),
                    color = colors.text,
                    fontSize = 13.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surface2)
                            .clickable(onClick = onDismiss)
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(R.string.cancel), color = colors.text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFFACC15))
                            .clickable(onClick = onConfirm)
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(R.string.confirm), color = Color(0xFF05060A), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
