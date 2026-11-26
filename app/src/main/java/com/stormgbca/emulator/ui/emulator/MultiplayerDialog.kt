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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Wifi
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.stormgbca.emulator.R
import com.stormgbca.emulator.data.GbcaPrefs
import com.stormgbca.emulator.ui.common.UnifiedBackButton
import com.stormgbca.emulator.ui.common.WatermelonCard
import com.stormgbca.emulator.ui.common.WatermelonSectionLabel
import com.stormgbca.emulator.ui.theme.SpaceGrotesk
import com.stormgbca.emulator.ui.theme.WatermelonMono
import com.stormgbca.emulator.ui.theme.watermelon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.stormgames.gbca.NativeCore

data class LinkStatusInfo(
    val mode: Int = NativeCore.LINK_NONE,
    val connected: Boolean = false,
    val playerId: Int = 0,
    val playerCount: Int = 1,
    val pingMs: Int = 0,
    val port: Int = 0,
    val roomName: String = "",
    val peerInfo: String = "",
    val statusText: String = "",
)

data class DiscoveredLanRoom(
    val hostIp: String,
    val port: Int,
    val mode: Int,
    val platform: Int,
    val playerCount: Int,
    val maxPlayers: Int,
    val roomName: String,
    val gameTitle: String,
)

fun parseLinkStatus(raw: String): LinkStatusInfo {
    val p = raw.split('\t')
    if (p.size < 9) return LinkStatusInfo(statusText = raw)
    return LinkStatusInfo(
        mode = p[0].toIntOrNull() ?: 0,
        connected = (p[1].toIntOrNull() ?: 0) != 0,
        playerId = p[2].toIntOrNull() ?: 0,
        playerCount = p[3].toIntOrNull() ?: 1,
        pingMs = p[4].toIntOrNull() ?: 0,
        port = p[5].toIntOrNull() ?: 0,
        roomName = p[6],
        peerInfo = p[7],
        statusText = p[8],
    )
}

fun parseDiscoveredRooms(raw: String): List<DiscoveredLanRoom> {
    if (raw.isBlank()) return emptyList()
    return raw.lineSequence().mapNotNull { line ->
        val p = line.split('\t')
        if (p.size < 8) return@mapNotNull null
        DiscoveredLanRoom(
            hostIp = p[0],
            port = p[1].toIntOrNull() ?: 54320,
            mode = p[2].toIntOrNull() ?: NativeCore.LINK_LAN_HOST,
            platform = p[3].toIntOrNull() ?: NativeCore.PLATFORM_GBA,
            playerCount = p[4].toIntOrNull() ?: 1,
            maxPlayers = p[5].toIntOrNull() ?: 4,
            roomName = p[6],
            gameTitle = p[7],
        )
    }.toList()
}

@Composable
fun MultiplayerPanelContent(
    nativeHandle: Long,
    onStartLink: ((mode: Int, ip: String, port: Int, clockPort: Int, roomName: String) -> Int)? = null,
    onStopLink: (() -> Unit)? = null,
    onStatusRefresh: (() -> LinkStatusInfo)? = null,
) {
    val colors = watermelon
    val scope = rememberCoroutineScope()
    val cfg = GbcaPrefs.coreConfigState.value

    var selectedCategory by remember { mutableIntStateOf(0) } // 0=LAN Link, 1=Wireless RFU, 2=IR, 3=Dolphin, 4=GB Printer, 5=Loopback
    var roomName by remember { mutableStateOf(cfg.mpRoomName) }
    var hostIp by remember { mutableStateOf(cfg.mpLastHostIp) }
    var portStr by remember { mutableStateOf(cfg.mpPort.toString()) }
    var dolphinIp by remember { mutableStateOf(cfg.mpDolphinIp) }
    var dolphinDataPort by remember { mutableStateOf(cfg.mpDolphinDataPort.toString()) }
    var dolphinClockPort by remember { mutableStateOf(cfg.mpDolphinClockPort.toString()) }

    var status by remember { mutableStateOf(onStatusRefresh?.invoke() ?: LinkStatusInfo()) }
    var scanning by remember { mutableStateOf(false) }
    var rooms by remember { mutableStateOf<List<DiscoveredLanRoom>>(emptyList()) }
    var feedback by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(nativeHandle) {
        while (onStatusRefresh != null) {
            status = onStatusRefresh()
            delay(1000L)
        }
    }

    val fieldColors = TextFieldDefaults.outlinedTextFieldColors(
        textColor = colors.text,
        cursorColor = colors.green,
        focusedBorderColor = colors.green,
        unfocusedBorderColor = colors.line,
        focusedLabelColor = colors.green,
        unfocusedLabelColor = colors.text2,
        backgroundColor = colors.surface2,
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Live Status Card
        WatermelonCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (status.mode != NativeCore.LINK_NONE && status.connected) Color(0xFF22C55E) else colors.red),
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (status.mode == NativeCore.LINK_NONE) {
                            stringResource(R.string.mp_status_disconnected)
                        } else {
                            status.statusText.ifBlank { "Active (P${status.playerId + 1} / ${status.playerCount}P)" }
                        },
                        color = colors.text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                    )
                    if (status.mode != NativeCore.LINK_NONE) {
                        Text(
                            text = "P${status.playerId + 1} | ${status.playerCount}P | ${status.pingMs} ms | ${status.peerInfo}",
                            color = colors.green,
                            fontFamily = WatermelonMono,
                            fontSize = 11.sp,
                        )
                    }
                }
                if (status.mode != NativeCore.LINK_NONE && onStopLink != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(colors.red.copy(alpha = 0.2f))
                            .border(1.dp, colors.red, RoundedCornerShape(9.dp))
                            .clickable {
                                onStopLink()
                                status = onStatusRefresh?.invoke() ?: LinkStatusInfo()
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.LinkOff, contentDescription = null, tint = colors.red, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.mp_disconnect), color = colors.red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Mode Selection (Clean vertical cards, ZERO horizontal scrolling)
        val modes: List<Triple<Int, ImageVector, Int>> = listOf(
            Triple(0, Icons.Filled.Cable, R.string.mp_mode_lan_link),
            Triple(1, Icons.Filled.Wifi, R.string.mp_mode_wireless),
            Triple(2, Icons.Filled.Sensors, R.string.mp_mode_ir),
            Triple(3, Icons.Filled.SportsEsports, R.string.mp_mode_dolphin),
            Triple(4, Icons.Filled.Print, R.string.mp_mode_printer),
            Triple(5, Icons.Filled.Router, R.string.mp_mode_loopback),
        )
        WatermelonCard {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                modes.forEach { (idx, icon, titleRes) ->
                    val sel = selectedCategory == idx
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (sel) colors.greenDim else colors.surface2)
                            .border(1.dp, if (sel) colors.green else colors.line, RoundedCornerShape(10.dp))
                            .clickable { selectedCategory = idx; feedback = null }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Icon(icon, contentDescription = null, tint = if (sel) colors.green else colors.text, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(titleRes), color = if (sel) colors.green else colors.text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        // Mode-Specific Controls
        WatermelonCard {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (selectedCategory) {
                    0, 1, 2 -> {
                        val hostMode = when (selectedCategory) {
                            1 -> NativeCore.LINK_WIRELESS_HOST
                            2 -> NativeCore.LINK_IR_HOST
                            else -> NativeCore.LINK_LAN_HOST
                        }
                        val clientMode = when (selectedCategory) {
                            1 -> NativeCore.LINK_WIRELESS_CLIENT
                            2 -> NativeCore.LINK_IR_CLIENT
                            else -> NativeCore.LINK_LAN_CLIENT
                        }

                        OutlinedTextField(
                            value = roomName,
                            onValueChange = { roomName = it },
                            label = { Text(stringResource(R.string.mp_room_name)) },
                            singleLine = true,
                            colors = fieldColors,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = hostIp,
                                onValueChange = { hostIp = it },
                                label = { Text(stringResource(R.string.mp_host_ip)) },
                                singleLine = true,
                                colors = fieldColors,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1.7f),
                            )
                            OutlinedTextField(
                                value = portStr,
                                onValueChange = { portStr = it.filter { ch -> ch.isDigit() } },
                                label = { Text(stringResource(R.string.mp_port)) },
                                singleLine = true,
                                colors = fieldColors,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f),
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.green)
                                    .clickable {
                                        val p = portStr.toIntOrNull() ?: 54320
                                        GbcaPrefs.updateCoreConfig { it.copy(mpRoomName = roomName, mpPort = p) }
                                        if (onStartLink != null) {
                                            val rc = onStartLink(hostMode, "", p, 49420, roomName)
                                            status = onStatusRefresh?.invoke() ?: status
                                            feedback = if (rc == 0) "Room hosted on :$p" else "Failed to host on :$p"
                                        } else {
                                            feedback = "Saved! Launch a game and open Pause -> Link / Net to start."
                                        }
                                    }
                                    .padding(vertical = 11.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(stringResource(R.string.mp_host_room), color = Color(0xFF05060A), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.surface2)
                                    .border(1.dp, colors.green, RoundedCornerShape(10.dp))
                                    .clickable {
                                        val p = portStr.toIntOrNull() ?: 54320
                                        GbcaPrefs.updateCoreConfig { it.copy(mpLastHostIp = hostIp, mpPort = p) }
                                        if (onStartLink != null) {
                                            scope.launch {
                                                val rc = withContext(Dispatchers.IO) { onStartLink(clientMode, hostIp.trim(), p, 49420, "") }
                                                status = onStatusRefresh?.invoke() ?: status
                                                feedback = if (rc == 0) "Connected to $hostIp:$p" else "Could not connect to $hostIp:$p"
                                            }
                                        } else {
                                            feedback = "Saved! Launch a game and open Pause -> Link / Net to connect."
                                        }
                                    }
                                    .padding(vertical = 11.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(stringResource(R.string.mp_join_room), color = colors.green, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }

                        // UDP LAN Auto-Discovery Button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.surface2)
                                .border(1.dp, colors.line, RoundedCornerShape(10.dp))
                                .clickable(enabled = !scanning) {
                                    scope.launch {
                                        scanning = true
                                        feedback = null
                                        val raw = withContext(Dispatchers.IO) { NativeCore.nativeLanDiscover(800) }
                                        rooms = parseDiscoveredRooms(raw)
                                        scanning = false
                                    }
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (scanning) {
                                    CircularProgressIndicator(color = colors.green, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                } else {
                                    Icon(Icons.Filled.Radar, contentDescription = null, tint = colors.green, modifier = Modifier.size(17.dp))
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.mp_scan_lan), color = colors.text, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                            }
                        }

                        if (!scanning && rooms.isEmpty()) {
                            Text(stringResource(R.string.mp_no_rooms), color = colors.text2, fontSize = 11.5.sp)
                        } else {
                            rooms.forEach { r ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.surface2)
                                        .border(1.dp, colors.green.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                        .clickable {
                                            hostIp = r.hostIp
                                            portStr = r.port.toString()
                                            if (onStartLink != null) {
                                                scope.launch {
                                                    val joinMode = when (r.mode) {
                                                        NativeCore.LINK_WIRELESS_HOST -> NativeCore.LINK_WIRELESS_CLIENT
                                                        NativeCore.LINK_IR_HOST -> NativeCore.LINK_IR_CLIENT
                                                        else -> NativeCore.LINK_LAN_CLIENT
                                                    }
                                                    val rc = withContext(Dispatchers.IO) { onStartLink(joinMode, r.hostIp, r.port, 49420, "") }
                                                    status = onStatusRefresh?.invoke() ?: status
                                                    feedback = if (rc == 0) "Joined ${r.roomName}" else "Connection failed"
                                                }
                                            }
                                        }
                                        .padding(10.dp),
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${r.roomName} (${r.gameTitle})", color = colors.text, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                        Text("${r.hostIp}:${r.port} | ${r.playerCount}/${r.maxPlayers}P", color = colors.green, fontFamily = WatermelonMono, fontSize = 11.sp)
                                    }
                                    Text(stringResource(R.string.mp_join_room), color = colors.green, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    3 -> {
                        // Dolphin GameCube JoyBus
                        OutlinedTextField(
                            value = dolphinIp,
                            onValueChange = { dolphinIp = it },
                            label = { Text("Dolphin IP") },
                            singleLine = true,
                            colors = fieldColors,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = dolphinDataPort,
                                onValueChange = { dolphinDataPort = it.filter { c -> c.isDigit() } },
                                label = { Text("Data Port (54970)") },
                                singleLine = true,
                                colors = fieldColors,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = dolphinClockPort,
                                onValueChange = { dolphinClockPort = it.filter { c -> c.isDigit() } },
                                label = { Text("Clock Port (49420)") },
                                singleLine = true,
                                colors = fieldColors,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.green)
                                .clickable {
                                    val dp = dolphinDataPort.toIntOrNull() ?: 54970
                                    val cp = dolphinClockPort.toIntOrNull() ?: 49420
                                    GbcaPrefs.updateCoreConfig { it.copy(mpDolphinIp = dolphinIp, mpDolphinDataPort = dp, mpDolphinClockPort = cp) }
                                    if (onStartLink != null) {
                                        scope.launch {
                                            val rc = withContext(Dispatchers.IO) { onStartLink(NativeCore.LINK_DOLPHIN_JOYBUS, dolphinIp.trim(), dp, cp, "") }
                                            status = onStatusRefresh?.invoke() ?: status
                                            feedback = if (rc == 0) "Dolphin JoyBus connected!" else "Dolphin refused connection (enable GBA JoyBus in Dolphin)"
                                        }
                                    } else {
                                        feedback = "Saved! Start GBA game and open Pause -> Link / Net."
                                    }
                                }
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(stringResource(R.string.mp_join_room), color = Color(0xFF05060A), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    4 -> {
                        // Game Boy Printer
                        Text(
                            "Emulates Nintendo Game Boy Printer (MGB-007) for Game Boy Camera, Pokemon Gold/Silver/Crystal Pokedex/Diploma, Zelda DX, Mario Bros DX. Printed images automatically save as PNG in Screenshots.",
                            color = colors.text2,
                            fontSize = 12.sp,
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.green)
                                .clickable {
                                    if (onStartLink != null) {
                                        val rc = onStartLink(NativeCore.LINK_GB_PRINTER, "", 0, 0, "")
                                        status = onStatusRefresh?.invoke() ?: status
                                        feedback = if (rc == 0) "Game Boy Printer attached!" else "Attach failed"
                                    } else {
                                        feedback = "Launch a GB/GBC game and attach Game Boy Printer from Pause -> Link / Net."
                                    }
                                }
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(stringResource(R.string.mp_mode_printer), color = Color(0xFF05060A), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    5 -> {
                        // Local Loopback
                        Text(
                            "Attaches a virtual local loopback link cable for instant 2-player SIO testing or solo link checks without Wi-Fi.",
                            color = colors.text2,
                            fontSize = 12.sp,
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.green)
                                .clickable {
                                    if (onStartLink != null) {
                                        val rc = onStartLink(NativeCore.LINK_LOCAL_LOOPBACK, "127.0.0.1", 0, 0, "Local")
                                        status = onStatusRefresh?.invoke() ?: status
                                        feedback = if (rc == 0) "Local Loopback Link active!" else "Failed"
                                    } else {
                                        feedback = "Launch a game and activate Local Loopback from Pause -> Link / Net."
                                    }
                                }
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(stringResource(R.string.mp_mode_loopback), color = Color(0xFF05060A), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }

                feedback?.let { msg ->
                    Text(msg, color = colors.green, fontFamily = WatermelonMono, fontSize = 11.5.sp)
                }
            }
        }
    }
}

@Composable
fun MultiplayerDialog(
    nativeHandle: Long,
    onStartLink: (mode: Int, ip: String, port: Int, clockPort: Int, roomName: String) -> Int,
    onStopLink: () -> Unit,
    onStatusRefresh: () -> LinkStatusInfo,
    onDismiss: () -> Unit,
) {
    val colors = watermelon
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
                    Icon(Icons.Filled.Cable, contentDescription = null, tint = colors.green, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.mp_title), color = colors.text, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
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
                    MultiplayerPanelContent(
                        nativeHandle = nativeHandle,
                        onStartLink = onStartLink,
                        onStopLink = onStopLink,
                        onStatusRefresh = onStatusRefresh,
                    )
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
