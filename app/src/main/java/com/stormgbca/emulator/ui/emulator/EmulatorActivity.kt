package com.stormgbca.emulator.ui.emulator

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.stormgbca.emulator.R
import com.stormgbca.emulator.data.BoxArtRepository
import com.stormgbca.emulator.data.GbcaPrefs
import com.stormgbca.emulator.data.RaAchievementBadge
import com.stormgbca.emulator.data.RaGameSession
import com.stormgbca.emulator.data.RetroAchievementsClient
import com.stormgbca.emulator.data.RomRepository
import com.stormgbca.emulator.data.ShaderRepository
import com.stormgbca.emulator.model.CheatItem
import com.stormgbca.emulator.model.ConsoleSystem
import com.stormgbca.emulator.model.PhysicalAction
import com.stormgbca.emulator.model.RomEntry
import com.stormgbca.emulator.ui.emulator.overlay.LayoutEditorActivity
import com.stormgbca.emulator.ui.emulator.overlay.OverlayPickerDialog
import com.stormgbca.emulator.ui.emulator.overlay.VirtualGamepadOverlay
import com.stormgbca.emulator.ui.emulator.skin.ConsoleSkinOverlayView
import com.stormgbca.emulator.ui.emulator.skin.ConsoleSkinPickerDialog
import com.stormgbca.emulator.ui.emulator.skin.GbcaSkinId
import com.stormgbca.emulator.ui.emulator.skin.computeGameViewportRect
import com.stormgbca.emulator.ui.ra.RaActiveLeaderboardData
import com.stormgbca.emulator.ui.ra.RaChallengeOverlayStrip
import com.stormgbca.emulator.ui.ra.RaLeaderboardTrackerPill
import com.stormgbca.emulator.ui.ra.RaMeasuredPill
import com.stormgbca.emulator.ui.ra.RaMeasuredProgress
import com.stormgbca.emulator.ui.ra.RaStartupSessionBanner
import com.stormgbca.emulator.ui.ra.RaSwitchToHardcoreConfirmDialog
import com.stormgbca.emulator.ui.ra.RaUnlockToastCard
import com.stormgbca.emulator.ui.ra.RaUnlockToastData
import com.stormgbca.emulator.ui.ra.RetroAchievementsAccountDialog
import com.stormgbca.emulator.ui.settings.SettingsActivity
import com.stormgbca.emulator.ui.theme.AppThemeManager
import com.stormgbca.emulator.ui.theme.StormWatermelonTheme
import com.stormgbca.emulator.ui.theme.Theme
import com.stormgbca.emulator.ui.theme.WatermelonMono
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import ru.stormgames.gbca.NativeCore
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.LockSupport
import java.util.concurrent.locks.ReentrantLock
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.concurrent.withLock
import kotlin.math.roundToInt

class EmulatorActivity : AppCompatActivity(), SensorEventListener {
    companion object {
        const val EXTRA_ROM_JSON = "extra_rom_json"
        private const val FRAME_DURATION_NS = 16_742_706L // 59.7275 Hz exact GB/GBA refresh
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val coreLock = ReentrantLock()
    private val vkLock = ReentrantLock()

    private var nativeHandle: Long = 0L
    private var romEntry: RomEntry? = null
    private var activeSystem: ConsoleSystem = ConsoleSystem.GBA
    private var glView: GLSurfaceView? = null
    private var vkView: SurfaceView? = null
    private var shaderSource: String = ""

    private val useVulkanState = androidx.compose.runtime.mutableStateOf(false)
    private val vulkanReady = AtomicBoolean(false)
    private val pendingVkRender = AtomicBoolean(false)
    @Volatile private var displayRefreshHz: Float = 60f
    @Volatile private var activeGraphicsBackend: Int = 0
    @Volatile private var activeCustomDriverPath: String = ""

    private val isPaused = AtomicBoolean(false)
    private val isRunning = AtomicBoolean(false)
    private val fastForward = AtomicBoolean(false)
    private val touchKeyMask = AtomicInteger(0)
    private val physicalKeyMask = AtomicInteger(0)

    private var emuThread: Thread? = null
    private var sensorManager: SensorManager? = null
    private var powerManager: PowerManager? = null
    private var thermalListenerObj: Any? = null
    private var vibrator: Vibrator? = null
    private var lastRumbleTimeMs = 0L
    private var sessionStartMs = 0L
    private val isThermalThrottled = AtomicBoolean(false)
    @Volatile private var romSupportsTilt = false
    @Volatile private var romSupportsGyro = false
    @Volatile private var isActivityResumed = false

    private val showPauseMenuState = androidx.compose.runtime.mutableStateOf(false)
    private val showSkinPickerState = androidx.compose.runtime.mutableStateOf(false)
    private val showOverlayPickerState = androidx.compose.runtime.mutableStateOf(false)
    private val showRamFinderState = androidx.compose.runtime.mutableStateOf(false)
    private val showMultiplayerState = androidx.compose.runtime.mutableStateOf(false)
    private val showRaAccountState = androidx.compose.runtime.mutableStateOf(false)
    private val linkStatusState = androidx.compose.runtime.mutableStateOf(LinkStatusInfo())
    private val activeCoreLabelState = androidx.compose.runtime.mutableStateOf("mGBA")
    private val activeBackendLabelState = androidx.compose.runtime.mutableStateOf("GLES")
    private val rewindFramesState = androidx.compose.runtime.mutableIntStateOf(0)
    private val statusToastState = androidx.compose.runtime.mutableStateOf<String?>(null)
    private val raSessionState = androidx.compose.runtime.mutableStateOf<RaGameSession?>(null)
    private val raHardcoreActive = AtomicBoolean(false)
    private val raHardcoreActiveState = androidx.compose.runtime.mutableStateOf(false)
    private val showSwitchToHardcoreDialogState = androidx.compose.runtime.mutableStateOf(false)
    private val raStartupBannerState = androidx.compose.runtime.mutableStateOf<Pair<RaGameSession, Boolean>?>(null)
    private val raUnlockToastState = androidx.compose.runtime.mutableStateOf<RaUnlockToastData?>(null)
    private val raMeasuredPillState = androidx.compose.runtime.mutableStateOf<String?>(null)
    private val raActiveLeaderboardState = androidx.compose.runtime.mutableStateOf<RaActiveLeaderboardData?>(null)
    private val raPrimedMapState = androidx.compose.runtime.mutableStateOf<Map<Int, RaAchievementBadge>>(emptyMap())
    private val raMeasuredMapState = androidx.compose.runtime.mutableStateOf<Map<Int, RaMeasuredProgress>>(emptyMap())
    private val raRichPresenceState = androidx.compose.runtime.mutableStateOf("")
    @Volatile private var activeRaHash: String = ""
    @Volatile private var lastRaLoggedInState = false
    private var raPollJob: Job? = null
    private var raPingJob: Job? = null
    private var raBannerJob: Job? = null
    private var raUnlockToastJob: Job? = null
    private var raMeasuredPillJob: Job? = null
    private val videoWidthState = androidx.compose.runtime.mutableIntStateOf(240)
    private val videoHeightState = androidx.compose.runtime.mutableIntStateOf(160)

    private val patchPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) applySoftPatchFromUri(uri)
    }

    @Volatile private var glVpX = 0
    @Volatile private var glVpY = 0
    @Volatile private var vkVpY = 0
    @Volatile private var glVpW = 1
    @Volatile private var glVpH = 1
    @Volatile private var glSurfaceW = 1
    @Volatile private var glSurfaceH = 1
    @Volatile private var activeRotationDeg = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        AppThemeManager.applyEdgeToEdgeTheme(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        AppThemeManager.applyImmersiveFullscreen(this)

        val rawJson = intent.getStringExtra(EXTRA_ROM_JSON)
        romEntry = rawJson?.let { runCatching { json.decodeFromString<RomEntry>(it) }.getOrNull() }
        val rom = romEntry ?: run {
            Toast.makeText(this, getString(R.string.toast_open_rom_fail), Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        activeSystem = rom.consoleSystem
        videoWidthState.intValue = if (activeSystem == ConsoleSystem.GBA) 240 else 160
        videoHeightState.intValue = if (activeSystem == ConsoleSystem.GBA) 160 else 144

        ShaderRepository.ensureExtracted(this)
        shaderSource = runCatching {
            assets.open("storm_gbca_lcd.glsl").bufferedReader().use { it.readText() }
        }.getOrDefault("")

        // Apply active per-system RetroArch .slangp shader immediately before renderer creation
        val initOverlay = GbcaPrefs.getOverlayProfile(activeSystem)
        NativeCore.nativeSetRetroArchShader(initOverlay.retroArchShaderPath)

        refreshDisplayHz()
        val initCfg = GbcaPrefs.coreConfigState.value
        activeGraphicsBackend = initCfg.graphicsBackend
        activeCustomDriverPath = initCfg.customVulkanDriverPath
        val wantVulkan = initCfg.graphicsBackend == 1
        useVulkanState.value = wantVulkan
        activeBackendLabelState.value = if (wantVulkan) "Vulkan" else "GLES"
        refreshTelemetryAsync(wantVulkan)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        romSupportsTilt = rom.hasTilt
        romSupportsGyro = rom.hasGyro
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        val glSurface = createGlSurfaceView()
        glView = glSurface

        val vkSurface = createVulkanSurfaceView()
        vkView = vkSurface

        setContent {
            val initOverlayForTheme = when (activeSystem) {
                ConsoleSystem.GB -> GbcaPrefs.gbOverlayState.value
                ConsoleSystem.GBC -> GbcaPrefs.gbcOverlayState.value
                ConsoleSystem.GBA -> GbcaPrefs.gbaOverlayState.value
            }
            val systemThemeOverride = remember(initOverlayForTheme.uiThemeKey) {
                initOverlayForTheme.uiThemeKey.takeIf { it.isNotBlank() }?.let { Theme.fromPreference(it) }
            }
            StormWatermelonTheme(themeOverride = systemThemeOverride) {
                val skinKey = when (activeSystem) {
                    ConsoleSystem.GB -> GbcaPrefs.gbSkinState.value
                    ConsoleSystem.GBC -> GbcaPrefs.gbcSkinState.value
                    ConsoleSystem.GBA -> GbcaPrefs.gbaSkinState.value
                }
                val skinDim = when (activeSystem) {
                    ConsoleSystem.GB -> GbcaPrefs.gbSkinDimmingState.intValue
                    ConsoleSystem.GBC -> GbcaPrefs.gbcSkinDimmingState.intValue
                    ConsoleSystem.GBA -> GbcaPrefs.gbaSkinDimmingState.intValue
                }
                val overlayProfile = when (activeSystem) {
                    ConsoleSystem.GB -> GbcaPrefs.gbOverlayState.value
                    ConsoleSystem.GBC -> GbcaPrefs.gbcOverlayState.value
                    ConsoleSystem.GBA -> GbcaPrefs.gbaOverlayState.value
                }
                val skinId = remember(skinKey, activeSystem) { GbcaSkinId.fromKey(skinKey, activeSystem) }
                val coreCfg = GbcaPrefs.coreConfigState.value
                val aspectMode = coreCfg.aspectMode
                val isVulkanActive = useVulkanState.value

                LaunchedEffect(overlayProfile.retroArchShaderPath) {
                    NativeCore.nativeSetRetroArchShader(overlayProfile.retroArchShaderPath)
                }

                LaunchedEffect(
                    overlayProfile.shaderOverlay,
                    coreCfg.fsrSharpness,
                    coreCfg.fsrQualityMode,
                    coreCfg.frameGenerationEnabled,
                ) {
                    syncPostProcessConfig()
                    requestVideoRender()
                }

                BackHandler {
                    togglePauseMenu()
                }

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                ) {
                    val density = LocalDensity.current
                    val wPx = with(density) { maxWidth.toPx() }.roundToInt().coerceAtLeast(1)
                    val hPx = with(density) { maxHeight.toPx() }.roundToInt().coerceAtLeast(1)
                    val isLandscape = wPx > hPx
                    val activeScreenCfg = if (isLandscape) overlayProfile.landscapeScreen else overlayProfile.portraitScreen

                    LaunchedEffect(activeScreenCfg.rotationDeg) {
                        activeRotationDeg = activeScreenCfg.rotationDeg
                        NativeCore.nativeGlesSetRotation(activeScreenCfg.rotationDeg)
                        requestVideoRender()
                    }

                    val screenRect = remember(
                        wPx, hPx, activeSystem, skinId, aspectMode,
                        videoWidthState.intValue, videoHeightState.intValue, activeScreenCfg,
                    ) {
                        computeGameViewportRect(
                            containerW = wPx,
                            containerH = hPx,
                            system = activeSystem,
                            skinId = skinId,
                            aspectMode = aspectMode,
                            nativeW = videoWidthState.intValue,
                            nativeH = videoHeightState.intValue,
                            customScreen = activeScreenCfg,
                        )
                    }

                    SideEffect {
                        glVpX = screenRect.left.roundToInt()
                        glVpY = (hPx - screenRect.bottom).roundToInt().coerceAtLeast(0)
                        vkVpY = screenRect.top.roundToInt().coerceAtLeast(0)
                        glVpW = screenRect.width.roundToInt().coerceAtLeast(1)
                        glVpH = screenRect.height.roundToInt().coerceAtLeast(1)
                    }

                    if (isVulkanActive) {
                        AndroidView(
                            factory = { vkSurface },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        AndroidView(
                            factory = { glSurface },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    val effectiveSkinId = remember(skinId, activeScreenCfg, screenRect, wPx, hPx) {
                        val isFullscreenMode = activeScreenCfg.screenPreset == 1 ||
                            activeScreenCfg.screenPreset == 2 ||
                            (activeScreenCfg.useCustom && activeScreenCfg.widthFraction >= 0.96f && activeScreenCfg.heightFraction >= 0.88f)
                        if (isFullscreenMode && activeScreenCfg.hideSkinInFullscreen) GbcaSkinId.NONE else skinId
                    }

                    ConsoleSkinOverlayView(
                        skinId = effectiveSkinId,
                        screenRect = screenRect,
                        dimmingPercent = skinDim,
                        modifier = Modifier.fillMaxSize(),
                    )

                    VirtualGamepadOverlay(
                        system = activeSystem,
                        profile = overlayProfile,
                        fastForwardActive = fastForward.get(),
                        onKeyMaskChanged = { mask ->
                            touchKeyMask.set(mask)
                            pushCombinedKeys()
                        },
                        onQuickSave = { saveStateSlot(0) },
                        onQuickLoad = { loadStateSlot(0) },
                        onToggleFastForward = {
                            val next = !fastForward.get()
                            fastForward.set(next)
                            val rawMult = if (next) GbcaPrefs.coreConfigState.value.fastForwardMultiplier else 1.0f
                            val mult = if (raHardcoreActive.get()) rawMult.coerceAtLeast(1.0f) else rawMult
                            coreLock.withLock {
                                if (nativeHandle != 0L) NativeCore.nativeSetFastForward(nativeHandle, mult)
                            }
                            showToast(if (next) getString(R.string.toast_ff_on_fmt, mult.toInt()) else getString(R.string.toast_ff_off))
                        },
                        onRewindTap = { performRewindStep() },
                        onOpenMenu = { togglePauseMenu() },
                    )

                    // RetroAchievements Section E Startup Mode Banner
                    AnimatedVisibility(
                        visible = raStartupBannerState.value != null,
                        enter = fadeIn() + slideInVertically { -it },
                        exit = fadeOut() + slideOutVertically { -it },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = 12.dp, start = 16.dp, end = 16.dp),
                    ) {
                        raStartupBannerState.value?.let { (sess, hc) ->
                            RaStartupSessionBanner(session = sess, hardcore = hc)
                        }
                    }

                    // RetroAchievements Achievement Unlocked Toast
                    val notifAlign = if (GbcaPrefs.getRaNotifPosition() == "bottom") Alignment.BottomCenter else Alignment.TopCenter
                    AnimatedVisibility(
                        visible = raUnlockToastState.value != null,
                        enter = fadeIn() + slideInVertically { if (notifAlign == Alignment.TopCenter) -it else it },
                        exit = fadeOut() + slideOutVertically { if (notifAlign == Alignment.TopCenter) -it else it },
                        modifier = Modifier
                            .align(notifAlign)
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 18.dp),
                    ) {
                        raUnlockToastState.value?.let { toast ->
                            RaUnlockToastCard(toast = toast)
                        }
                    }

                    // RetroAchievements Primed Challenge Indicators
                    if (GbcaPrefs.isRaChallengeIndicators() && raPrimedMapState.value.isNotEmpty()) {
                        RaChallengeOverlayStrip(
                            primed = raPrimedMapState.value.values.toList(),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .statusBarsPadding()
                                .padding(top = 12.dp, end = 12.dp),
                        )
                    }

                    // RetroAchievements Measured Progress Pill
                    if (GbcaPrefs.isRaProgressIndicators()) {
                        raMeasuredPillState.value?.let { pillText ->
                            RaMeasuredPill(
                                text = pillText,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .statusBarsPadding()
                                    .padding(top = 12.dp, start = 12.dp),
                            )
                        }
                    }

                    // RetroAchievements Active Leaderboard Tracker Pill
                    if (RetroAchievementsClient.isLeaderboardPopupsEnabled()) {
                        raActiveLeaderboardState.value?.let { lbData ->
                            RaLeaderboardTrackerPill(
                                lb = lbData,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 12.dp, bottom = 14.dp),
                            )
                        }
                    }

                    statusToastState.value?.let { msg ->
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 24.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xD90A0D16))
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                        ) {
                            Text(msg, color = Color(0xFF00E5FF), fontFamily = WatermelonMono, fontSize = 12.sp)
                        }
                    }

                    if (showPauseMenuState.value) {
                        val coverPath = remember(rom) {
                            BoxArtRepository.getCachedBoxArtPath(this@EmulatorActivity, rom, allowLibretro = true, allowRa = true)
                        }
                        PauseMenuOverlay(
                            rom = rom,
                            system = activeSystem,
                            boxArtPath = coverPath,
                            sessionStartMs = sessionStartMs,
                            activeCoreName = activeCoreLabelState.value,
                            activeBackendLabel = activeBackendLabelState.value,
                            fastForwardActive = fastForward.get(),
                            rewindAvailableFrames = rewindFramesState.intValue,
                            raSession = raSessionState.value,
                            raHardcoreActive = raHardcoreActiveState.value,
                            raRichPresence = raRichPresenceState.value,
                            raPrimedIds = raPrimedMapState.value.keys,
                            raMeasuredMap = raMeasuredMapState.value,
                            linkStatus = linkStatusState.value,
                            onResume = { setPaused(false) },
                            onQuickSave = { slot -> saveStateSlot(slot) },
                            onQuickLoad = { slot -> loadStateSlot(slot) },
                            onSaveSlot = { slot -> saveStateSlot(slot) },
                            onLoadSlot = { slot -> loadStateSlot(slot) },
                            onRewindStep = { performRewindStep() },
                            onToggleFastForward = {
                                val next = !fastForward.get()
                                fastForward.set(next)
                                val rawMult = if (next) GbcaPrefs.coreConfigState.value.fastForwardMultiplier else 1.0f
                                val mult = if (raHardcoreActive.get()) rawMult.coerceAtLeast(1.0f) else rawMult
                                coreLock.withLock {
                                    if (nativeHandle != 0L) NativeCore.nativeSetFastForward(nativeHandle, mult)
                                }
                            },
                            onToggleHardcoreMode = { targetHc -> requestRaModeSwitch(targetHc) },
                            onHardcoreBlocked = { msg -> showToast(msg) },
                            onOpenSkinPicker = { showSkinPickerState.value = true },
                            onOpenOverlayPicker = { showOverlayPickerState.value = true },
                            onOpenLayoutEditor = {
                                val intent = Intent(this@EmulatorActivity, LayoutEditorActivity::class.java).apply {
                                    putExtra("system", activeSystem.name)
                                }
                                startActivity(intent)
                            },
                            onOpenRamFinder = {
                                if (raHardcoreActive.get()) {
                                    showToast(getString(R.string.ra_hardcore_blocked_ram_finder))
                                } else {
                                    showRamFinderState.value = true
                                }
                            },
                            onOpenMultiplayer = { showMultiplayerState.value = true },
                            onOpenRaAccount = { showRaAccountState.value = true },
                            onConfigApplied = { applyCoreConfig() },
                            onCheatsUpdated = { list -> applyCheats(list) },
                            onApplySoftPatch = {
                                if (raHardcoreActive.get()) {
                                    showToast(getString(R.string.ra_hardcore_blocked_patch))
                                } else {
                                    patchPickerLauncher.launch(arrayOf("*/*"))
                                }
                            },
                            onTakeScreenshot = { captureScreenshot() },
                            onOpenSettings = {
                                startActivity(Intent(this@EmulatorActivity, SettingsActivity::class.java))
                            },
                            onReset = {
                                coreLock.withLock {
                                    if (nativeHandle != 0L) {
                                        NativeCore.nativeReset(nativeHandle)
                                        NativeCore.raReset(nativeHandle)
                                    }
                                }
                                raPrimedMapState.value = emptyMap()
                                raMeasuredMapState.value = emptyMap()
                                raActiveLeaderboardState.value = null
                                setPaused(false)
                            },
                            onExit = {
                                flushSram()
                                finish()
                            },
                        )
                    }

                    if (showMultiplayerState.value) {
                        MultiplayerDialog(
                            nativeHandle = nativeHandle,
                            onStartLink = { mode, ip, port, clockPort, rname ->
                                if (raHardcoreActive.get() && mode != NativeCore.LINK_GB_PRINTER && mode != NativeCore.LINK_NONE) {
                                    showToast(getString(R.string.ra_hardcore_blocked_link))
                                    NativeCore.STORM_ERR_HARDCORE_BLOCKED
                                } else {
                                    val rc = coreLock.withLock {
                                        if (nativeHandle != 0L) {
                                            NativeCore.nativeLinkStart(nativeHandle, mode, ip, port, clockPort, rname)
                                        } else -1
                                    }
                                    refreshLinkStatus()
                                    rc
                                }
                            },
                            onStopLink = {
                                coreLock.withLock {
                                    if (nativeHandle != 0L) NativeCore.nativeLinkStop(nativeHandle)
                                }
                                refreshLinkStatus()
                            },
                            onStatusRefresh = { refreshLinkStatus() },
                            onDismiss = { showMultiplayerState.value = false },
                        )
                    }

                    if (showSkinPickerState.value) {
                        ConsoleSkinPickerDialog(
                            initialSystem = activeSystem,
                            onDismiss = { showSkinPickerState.value = false },
                        )
                    }

                    if (showOverlayPickerState.value) {
                        OverlayPickerDialog(
                            initialSystem = activeSystem,
                            onShaderChanged = { sys, path ->
                                if (sys == activeSystem) {
                                    NativeCore.nativeSetRetroArchShader(path)
                                    requestVideoRender()
                                }
                            },
                            onDismiss = { showOverlayPickerState.value = false },
                        )
                    }

                    if (showRamFinderState.value) {
                        val romKey = remember(rom) { RomRepository.getRomStorageKey(rom) }
                        RamCheatFinderDialog(
                            system = activeSystem,
                            onResetSearch = { region, size ->
                                coreLock.withLock {
                                    if (nativeHandle != 0L) NativeCore.nativeMemSearchReset(nativeHandle, region, size) else 0
                                }
                            },
                            onFilterSearch = { op, target ->
                                coreLock.withLock {
                                    if (nativeHandle != 0L) NativeCore.nativeMemSearchFilter(nativeHandle, op, target) else 0
                                }
                            },
                            onFetchResults = {
                                coreLock.withLock {
                                    if (nativeHandle == 0L) emptyList() else {
                                        val addrs = IntArray(64)
                                        val vals = IntArray(64)
                                        val n = NativeCore.nativeMemSearchGetResults(nativeHandle, addrs, vals)
                                        (0 until n).map { idx -> RamSearchHit(addrs[idx], vals[idx]) }
                                    }
                                }
                            },
                            onWriteValue = { region, offset, value, size ->
                                coreLock.withLock {
                                    nativeHandle != 0L && NativeCore.nativeWriteMemory(nativeHandle, region, offset, value, size) == 0
                                }
                            },
                            onAddAsCheat = { name, code ->
                                val cur = GbcaPrefs.getCheatsForRom(romKey)
                                val next = cur + CheatItem(UUID.randomUUID().toString(), name, code, true)
                                GbcaPrefs.saveCheatsForRom(romKey, next)
                                applyCheats(next)
                            },
                            onDismiss = { showRamFinderState.value = false },
                        )
                    }

                    if (showRaAccountState.value) {
                        RetroAchievementsAccountDialog(
                            onHardcoreChanged = { wantsHardcore ->
                                if (raSessionState.value != null || RetroAchievementsClient.isLoggedIn()) {
                                    requestRaModeSwitch(wantsHardcore)
                                } else {
                                    GbcaPrefs.setRaHardcore(wantsHardcore)
                                    if (wantsHardcore) GbcaPrefs.setRaRichPresence(true)
                                }
                            },
                            onDismiss = {
                                showRaAccountState.value = false
                                refreshRaSessionAsync()
                            },
                        )
                    }

                    if (showSwitchToHardcoreDialogState.value) {
                        RaSwitchToHardcoreConfirmDialog(
                            onConfirm = {
                                showSwitchToHardcoreDialogState.value = false
                                executeSwitchToHardcore()
                            },
                            onDismiss = {
                                showSwitchToHardcoreDialogState.value = false
                                GbcaPrefs.setRaHardcore(false)
                            },
                        )
                    }
                }
            }
        }

        bootNativeCore(rom)
    }

    private fun createGlSurfaceView(): GLSurfaceView = GLSurfaceView(this).apply {
        setEGLContextClientVersion(3)
        preserveEGLContextOnPause = true
        setRenderer(object : GLSurfaceView.Renderer {
            override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
                NativeCore.nativeGlesInit(shaderSource)
                NativeCore.nativeSetRetroArchShader(GbcaPrefs.getOverlayProfile(activeSystem).retroArchShaderPath)
                syncPostProcessConfig()
            }

            override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
                glSurfaceW = width.coerceAtLeast(1)
                glSurfaceH = height.coerceAtLeast(1)
                NativeCore.nativeGlesResize(glSurfaceW, glSurfaceH)
                updateSurfaceFrameRateHint(GbcaPrefs.coreConfigState.value.frameGenerationEnabled)
            }

            override fun onDrawFrame(gl: GL10?) {
                val h = nativeHandle
                if (h == 0L) return
                val cfg = GbcaPrefs.coreConfigState.value
                val overlayProf = GbcaPrefs.getOverlayProfile(activeSystem)
                val displayMode = overlayProf.shaderOverlay
                val ghosting = if (isThermalThrottled.get()) 0f else cfg.lcdGhosting / 100f
                val ccStrength = if (cfg.colorCorrectionMode == NativeCore.CC_DISABLED) 0f else 1f
                val isGray = activeSystem == ConsoleSystem.GB
                val intScale = cfg.aspectMode == 1
                coreLock.withLock {
                    NativeCore.nativeGlesRender(
                        h, glVpX, glVpY, glVpW, glVpH,
                        displayMode, ghosting, ccStrength, isGray, intScale,
                    )
                }
            }
        })
        renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
    }

    private fun createVulkanSurfaceView(): SurfaceView = SurfaceView(this).apply {
        holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                val driverPath = GbcaPrefs.coreConfigState.value.customVulkanDriverPath
                val ok = vkLock.withLock {
                    NativeCore.nativeVkInit(holder.surface, driverPath)
                }
                if (ok) {
                    vulkanReady.set(true)
                    activeBackendLabelState.value = "Vulkan"
                    NativeCore.nativeSetRetroArchShader(GbcaPrefs.getOverlayProfile(activeSystem).retroArchShaderPath)
                    syncPostProcessConfig()
                    pendingVkRender.set(true)
                    refreshTelemetryAsync(true)
                } else {
                    vulkanReady.set(false)
                    useVulkanState.value = false
                    activeBackendLabelState.value = "GLES"
                    showToast(getString(R.string.toast_vulkan_fallback))
                    refreshTelemetryAsync(false)
                }
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                glSurfaceW = width.coerceAtLeast(1)
                glSurfaceH = height.coerceAtLeast(1)
                refreshDisplayHz()
                if (vulkanReady.get()) {
                    vkLock.withLock { NativeCore.nativeVkResize(glSurfaceW, glSurfaceH) }
                    updateSurfaceFrameRateHint(GbcaPrefs.coreConfigState.value.frameGenerationEnabled)
                    pendingVkRender.set(true)
                }
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                vulkanReady.set(false)
                vkLock.withLock { NativeCore.nativeVkDestroy() }
            }
        })
    }

    private fun refreshDisplayHz(): Float {
        val hz = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display?.refreshRate ?: 60f
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay?.refreshRate ?: 60f
            }
        }.getOrDefault(60f)
        displayRefreshHz = hz
        return hz
    }

    private fun refreshTelemetryAsync(expectVulkan: Boolean) {
        activityScope.launch(Dispatchers.IO) {
            val tel = com.stormgbca.emulator.vulkandriver.VulkanDriverRepository.readGraphicsTelemetry(this@EmulatorActivity)
            val vkLabel = tel.vulkanDeviceApi.ifBlank { tel.vulkanInstanceApi }.let { ver ->
                if (ver.isNotBlank()) "Vulkan $ver" else "Vulkan"
            }
            val glesLabel = if (tel.glesVersion.isNotBlank()) "GLES ${tel.glesVersion}" else "GLES"
            withContext(Dispatchers.Main) {
                if (expectVulkan && !tel.vulkanSupported) {
                    useVulkanState.value = false
                    activeBackendLabelState.value = glesLabel
                } else {
                    activeBackendLabelState.value = if (useVulkanState.value) vkLabel else glesLabel
                }
            }
        }
    }

    private fun renderVulkanFrame() {
        if (!vulkanReady.get() || nativeHandle == 0L) return
        val overlayProf = GbcaPrefs.getOverlayProfile(activeSystem)
        val linear = overlayProf.shaderOverlay != NativeCore.FILTER_NEAREST
        vkLock.withLock {
            if (vulkanReady.get()) {
                coreLock.withLock {
                    if (nativeHandle != 0L) {
                        NativeCore.nativeVkRender(nativeHandle, glVpX, vkVpY, glVpW, glVpH, linear, activeRotationDeg)
                    }
                }
            }
        }
    }

    private fun requestVideoRender() {
        if (useVulkanState.value) {
            if (Thread.currentThread() === emuThread) {
                renderVulkanFrame()
            } else {
                pendingVkRender.set(true)
            }
        } else {
            glView?.requestRender()
        }
    }

    private fun bootNativeCore(rom: RomEntry) {
        activityScope.launch(Dispatchers.IO) {
            val cachedRom = RomRepository.materializeRomToCache(this@EmulatorActivity, rom)
            if (cachedRom == null) {
                activityScope.launch(Dispatchers.Main) {
                    Toast.makeText(this@EmulatorActivity, getString(R.string.toast_rom_read_error), Toast.LENGTH_LONG).show()
                    finish()
                }
                return@launch
            }

            val patchFile = if (rom.softPatchUri.isNotBlank()) {
                RomRepository.materializeUriToCache(
                    this@EmulatorActivity,
                    Uri.parse(rom.softPatchUri),
                    "patches",
                )
            } else null

            val h = NativeCore.nativeCreate()
            if (h == 0L) {
                activityScope.launch(Dispatchers.Main) { finish() }
                return@launch
            }

            val raActiveHardcore = RetroAchievementsClient.isLoggedIn() && RetroAchievementsClient.isHardcore()
            raHardcoreActive.set(raActiveHardcore)
            raHardcoreActiveState.value = raActiveHardcore

            coreLock.withLock {
                nativeHandle = h
                applyCoreConfigLocked(h, rom)
                val rc = NativeCore.nativeLoadRomFile(h, cachedRom.absolutePath)
                if (rc != 0) {
                    NativeCore.nativeDestroy(h)
                    nativeHandle = 0L
                    activityScope.launch(Dispatchers.Main) {
                        Toast.makeText(this@EmulatorActivity, getString(R.string.toast_core_start_fail), Toast.LENGTH_LONG).show()
                        finish()
                    }
                    return@launch
                }

                if (patchFile != null) {
                    NativeCore.nativeApplyPatchFile(h, patchFile.absolutePath)
                }

                val sramFile = RomRepository.getSramFile(this@EmulatorActivity, rom)
                if (sramFile.exists() && sramFile.length() > 0L) {
                    NativeCore.nativeLoadSramFile(h, sramFile.absolutePath)
                }

                val wh = IntArray(2)
                NativeCore.nativeGetVideoInfo(h, wh)
                if (wh[0] > 0 && wh[1] > 0) {
                    videoWidthState.intValue = wh[0]
                    videoHeightState.intValue = wh[1]
                }

                NativeCore.getRomInfo(h)?.let { info ->
                    val coreName = info.activeCore.ifBlank { "mGBA" }
                    activeCoreLabelState.value = coreName
                    RetroAchievementsClient.currentCoreName = coreName
                    val newTilt = romSupportsTilt || info.hasTilt
                    val newGyro = romSupportsGyro || info.hasGyro
                    if (newTilt != romSupportsTilt || newGyro != romSupportsGyro) {
                        romSupportsTilt = newTilt
                        romSupportsGyro = newGyro
                        if (isActivityResumed) {
                            runOnUiThread {
                                if (isActivityResumed) registerMotionSensorsIfNeeded()
                            }
                        }
                    }
                }

                // Sync Hardcore gatekeeper BEFORE running any frames (never load save states or cheats in Hardcore)
                NativeCore.setHardcoreMode(h, raActiveHardcore)
                if (raActiveHardcore) {
                    NativeCore.nativeCheatsClear(h)
                } else {
                    val cheats = GbcaPrefs.getCheatsForRom(RomRepository.getRomStorageKey(rom))
                    applyCheatsLocked(h, cheats)
                }

                NativeCore.nativeSetRtc(h, true, System.currentTimeMillis() / 1000L)
                NativeCore.nativeAudioStart(h)
                if (GbcaPrefs.coreConfigState.value.audioMuted) {
                    NativeCore.nativeAudioPause(true)
                }
            }

            sessionStartMs = System.currentTimeMillis()
            syncPostProcessConfig()
            startEmulationLoop()

            // Lookup RetroAchievements & activate live rcheevos runtime if signed in
            lastRaLoggedInState = RetroAchievementsClient.isLoggedIn()
            if (lastRaLoggedInState) {
                val loadedHash = coreLock.withLock {
                    if (nativeHandle != 0L) NativeCore.raGetLoadedRomHash(nativeHandle) else null
                }
                val raHash = loadedHash?.takeIf { it.isNotBlank() }
                    ?: NativeCore.nativeComputeRaHash(cachedRom.absolutePath, rom.platform)
                if (!raHash.isNullOrBlank()) {
                    activeRaHash = raHash
                    val session = RetroAchievementsClient.lookupGameSession(raHash)
                    if (session != null) {
                        raSessionState.value = session
                        val activeHc = raHardcoreActive.get()
                        RetroAchievementsClient.startSession(session.gameId, raHash, activeHc)
                        coreLock.withLock {
                            if (nativeHandle != 0L) {
                                activateRaRuntimeLocked(nativeHandle, session, activeHc)
                            }
                        }
                        showRaStartupBanner(session, activeHc)
                        startRaLoops()
                    }
                }
            }
        }
    }

    private fun refreshRaSessionAsync() {
        val rom = romEntry ?: return
        if (!RetroAchievementsClient.isLoggedIn()) {
            raPollJob?.cancel()
            raPingJob?.cancel()
            raSessionState.value = null
            if (raHardcoreActive.get()) {
                raHardcoreActive.set(false)
                raHardcoreActiveState.value = false
                coreLock.withLock {
                    if (nativeHandle != 0L) {
                        NativeCore.setHardcoreMode(nativeHandle, false)
                        NativeCore.raClearAll(nativeHandle)
                    }
                }
            }
            return
        }
        activityScope.launch(Dispatchers.IO) {
            val loadedHash = coreLock.withLock {
                if (nativeHandle != 0L) NativeCore.raGetLoadedRomHash(nativeHandle) else null
            }
            val raHash = loadedHash?.takeIf { it.isNotBlank() }
                ?: RomRepository.materializeRomToCache(this@EmulatorActivity, rom)?.let { cachedRom ->
                    NativeCore.nativeComputeRaHash(cachedRom.absolutePath, rom.platform)
                }
                ?: return@launch
            activeRaHash = raHash
            val session = RetroAchievementsClient.lookupGameSession(raHash) ?: return@launch
            val wasNull = raSessionState.value == null
            raSessionState.value = session
            if (wasNull) {
                val activeHc = RetroAchievementsClient.isHardcore()
                raHardcoreActive.set(activeHc)
                raHardcoreActiveState.value = activeHc
                RetroAchievementsClient.startSession(session.gameId, raHash, activeHc)
                coreLock.withLock {
                    if (nativeHandle != 0L) {
                        NativeCore.setHardcoreMode(nativeHandle, activeHc)
                        applyCoreConfigLocked(nativeHandle, rom)
                        if (activeHc) NativeCore.nativeCheatsClear(nativeHandle)
                        activateRaRuntimeLocked(nativeHandle, session, activeHc)
                    }
                }
                showRaStartupBanner(session, activeHc)
                startRaLoops()
            }
        }
    }

    private fun sleepUntilNs(targetNs: Long, thermalThrottle: Boolean) {
        var remainingNs = targetNs - System.nanoTime()
        if (remainingNs <= 0L) return
        if (thermalThrottle) {
            LockSupport.parkNanos(remainingNs)
            return
        }
        if (remainingNs > 2_000_000L) {
            LockSupport.parkNanos(remainingNs - 1_500_000L)
        }
        while (isRunning.get() && !isPaused.get()) {
            remainingNs = targetNs - System.nanoTime()
            if (remainingNs <= 0L) break
            if (remainingNs > 400_000L) {
                Thread.yield()
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Thread.onSpinWait()
            } else {
                Thread.yield()
            }
        }
    }

    @Volatile private var lastHintedFrameRate: Float = -1f

    private fun updateSurfaceFrameRateHint(frameGenEnabled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val targetRate = if (frameGenEnabled) 120f else 60f
        if (lastHintedFrameRate == targetRate) return
        runOnUiThread {
            val activeSurface = if (useVulkanState.value) {
                vkView?.holder?.surface
            } else {
                glView?.holder?.surface
            }
            if (activeSurface != null && activeSurface.isValid) {
                runCatching {
                    activeSurface.setFrameRate(targetRate, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
                    lastHintedFrameRate = targetRate
                }
            }
        }
    }

    private fun syncPostProcessConfig() {
        val cfg = GbcaPrefs.coreConfigState.value
        val overlayProf = GbcaPrefs.getOverlayProfile(activeSystem)
        val effectiveFilter = overlayProf.shaderOverlay
        val fsrMode = when (effectiveFilter) {
            NativeCore.FILTER_FSR1 -> 1
            NativeCore.FILTER_FSR3 -> 2
            else -> 0
        }
        runCatching {
            NativeCore.nativeSetPostProcess(
                fsrMode,
                cfg.fsrSharpness.coerceIn(0, 100) / 100f,
                cfg.fsrQualityMode.coerceIn(0, 3),
                cfg.frameGenerationEnabled,
            )
        }
        if (!cfg.frameGenerationEnabled) {
            runCatching { NativeCore.nativeSetFrameGenAlpha(0.0f) }
        }
        updateSurfaceFrameRateHint(cfg.frameGenerationEnabled)
    }

    private fun startEmulationLoop() {
        isRunning.set(true)
        emuThread = Thread({
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY)
            var nextTickNs = System.nanoTime()
            var frameCounter = 0

            while (isRunning.get()) {
                if (isPaused.get() || nativeHandle == 0L) {
                    if (nativeHandle != 0L && useVulkanState.value && pendingVkRender.compareAndSet(true, false)) {
                        renderVulkanFrame()
                    }
                    try {
                        Thread.sleep(16)
                    } catch (_: InterruptedException) {
                        break
                    }
                    nextTickNs = System.nanoTime()
                    continue
                }

                val ff = fastForward.get()
                val cfg = GbcaPrefs.coreConfigState.value
                val mult = if (ff) cfg.fastForwardMultiplier.roundToInt().coerceIn(2, 8) else 1
                var rumble = 0f

                coreLock.withLock {
                    if (nativeHandle != 0L) {
                        for (i in 0 until mult) {
                            NativeCore.nativeRunFrame(nativeHandle)
                        }
                        rumble = NativeCore.nativeGetRumble(nativeHandle)
                    }
                }

                if (rumble > 0.25f && cfg.rumbleEnabled) {
                    val now = System.currentTimeMillis()
                    if (now - lastRumbleTimeMs > 60L) {
                        lastRumbleTimeMs = now
                        runCatching {
                            vibrator?.let { v ->
                                if (v.hasVibrator()) {
                                    val amp = (rumble * 255).roundToInt().coerceIn(40, 255)
                                    v.vibrate(VibrationEffect.createOneShot(32L, amp))
                                }
                            }
                        }
                    }
                }

                val skip = cfg.frameSkip.coerceIn(0, 4)
                val thermalThrottle = isThermalThrottled.get()
                val shouldRender = skip == 0 || (frameCounter % (skip + 1)) == 0
                val canHalfFrameGen = !useVulkanState.value || displayRefreshHz >= 85f
                val frameGenActive = cfg.frameGenerationEnabled && canHalfFrameGen && !ff && !thermalThrottle && skip == 0

                if (shouldRender) {
                    pendingVkRender.set(false)
                    NativeCore.nativeSetFrameGenAlpha(0.0f)
                    requestVideoRender()
                } else if (useVulkanState.value && pendingVkRender.compareAndSet(true, false)) {
                    NativeCore.nativeSetFrameGenAlpha(0.0f)
                    renderVulkanFrame()
                } else if (!frameGenActive) {
                    NativeCore.nativeSetFrameGenAlpha(0.0f)
                }

                frameCounter++
                if (frameCounter % 30 == 0) {
                    pollAndSavePrinterImage()
                }
                if (frameCounter % 120 == 0 && linkStatusState.value.mode != NativeCore.LINK_NONE) {
                    refreshLinkStatus()
                }
                if (frameCounter % 300 == 0) {
                    flushSramIfDirty()
                }

                if (!ff) {
                    nextTickNs += FRAME_DURATION_NS
                    val remainingBefore = nextTickNs - System.nanoTime()
                    if (remainingBefore < -FRAME_DURATION_NS * 4) {
                        nextTickNs = System.nanoTime()
                    } else if (remainingBefore > 0L) {
                        if (frameGenActive) {
                            val midTickNs = nextTickNs - (FRAME_DURATION_NS / 2L)
                            sleepUntilNs(midTickNs, thermalThrottle)
                            if (isRunning.get() && !isPaused.get() && !fastForward.get()) {
                                NativeCore.nativeSetFrameGenAlpha(0.5f)
                                requestVideoRender()
                            }
                        }
                        sleepUntilNs(nextTickNs, thermalThrottle)
                    }
                } else {
                    NativeCore.nativeSetFrameGenAlpha(0.0f)
                    nextTickNs = System.nanoTime()
                }
            }
        }, "StormGbcaEmuThread").apply { start() }
    }

    private fun applyCoreConfig() {
        val rom = romEntry ?: return
        coreLock.withLock {
            if (nativeHandle != 0L) applyCoreConfigLocked(nativeHandle, rom)
        }
        syncPostProcessConfig()
        requestVideoRender()
    }

    private fun applyCoreConfigLocked(h: Long, rom: RomEntry) {
        val cfg = GbcaPrefs.coreConfigState.value
        val overlayProf = GbcaPrefs.getOverlayProfile(activeSystem)
        val effFilter = overlayProf.shaderOverlay
        val hc = raHardcoreActive.get()
        val ov = rom.coreOverride
        val gbCore = if (ov >= 0) ov else cfg.preferredCoreGb
        val gbcCore = if (ov >= 0) ov else cfg.preferredCoreGbc
        val gbaCore = NativeCore.CORE_MGBA
        val thermalThrottle = isThermalThrottled.get()
        val effLcdGhosting = if (thermalThrottle) 0 else cfg.lcdGhosting
        val effRunAhead = if (thermalThrottle) 0 else cfg.runAheadFrames
        val effResScale = if (thermalThrottle) 1 else cfg.resolutionScale
        val effUpscaleMode = if (thermalThrottle) NativeCore.UPSCALE_NONE else cfg.upscaleMode
        val effRewindEnabled = if (hc) false else cfg.rewindEnabled
        val effOverclockPct = if (hc) 100 else cfg.cpuOverclockPct

        NativeCore.nativeSetConfig(
            h,
            gbCore, gbcCore, gbaCore,
            cfg.gbModel, cfg.sgbBorders, cfg.colorCorrectionMode,
            cfg.dmgPaletteIndex, null, effLcdGhosting, effFilter,
            cfg.audioHighpassMode, cfg.audioInterpMode,
            cfg.channelVolumes.toFloatArray(), cfg.masterVolume,
            effRunAhead, effRewindEnabled, cfg.rewindBufferMb, cfg.rewindInterval,
            cfg.biosGbPath, cfg.biosGbcPath, cfg.biosGbaPath, cfg.biosSgbPath,
            cfg.skipBiosIntro, cfg.dynamicRateControl, cfg.drcMaxSkew,
        )
        NativeCore.nativeSetUpscale(h, effResScale, effUpscaleMode)
        NativeCore.nativeSetPerformance(h, effOverclockPct, cfg.frameSkip)
        NativeCore.nativeSetOptimizationProfile(h, GbcaPrefs.getEffectiveOptimizationProfile())
        NativeCore.nativeSetSolarLux(h, cfg.solarLux)
        NativeCore.getRomInfo(h)?.let { info ->
            val coreName = info.activeCore.ifBlank { "mGBA" }
            activeCoreLabelState.value = coreName
            RetroAchievementsClient.currentCoreName = coreName
        }
    }

    private fun refreshLinkStatus(): LinkStatusInfo {
        val st = coreLock.withLock {
            if (nativeHandle != 0L) parseLinkStatus(NativeCore.nativeLinkGetStatus(nativeHandle)) else LinkStatusInfo()
        }
        linkStatusState.value = st
        return st
    }

    private fun pollAndSavePrinterImage() {
        val wh = IntArray(2)
        val pixels = coreLock.withLock {
            if (nativeHandle != 0L) NativeCore.nativePollPrinterImage(nativeHandle, wh) else null
        } ?: return
        val w = wh[0]
        val h = wh[1]
        if (w <= 0 || h <= 0 || pixels.size < w * h) return

        for (i in pixels.indices) {
            val v = pixels[i]
            val r = v and 0xFF
            val g = (v ushr 8) and 0xFF
            val b = (v ushr 16) and 0xFF
            pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(pixels, 0, w, 0, 0, w, h)
        runCatching {
            val dir = java.io.File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "GBPrinter").apply { mkdirs() }
            val out = java.io.File(dir, "gb_print_${System.currentTimeMillis()}.png")
            FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        bmp.recycle()
        showToast(getString(R.string.mp_printer_saved))
    }

    private fun applyCheats(cheats: List<CheatItem>) {
        if (raHardcoreActive.get()) {
            coreLock.withLock {
                if (nativeHandle != 0L) NativeCore.nativeCheatsClear(nativeHandle)
            }
            showToast(getString(R.string.ra_hardcore_blocked_cheats))
            return
        }
        coreLock.withLock {
            if (nativeHandle != 0L) applyCheatsLocked(nativeHandle, cheats)
        }
    }

    private fun applyCheatsLocked(h: Long, cheats: List<CheatItem>) {
        NativeCore.nativeCheatsClear(h)
        cheats.forEach { c ->
            if (c.enabled) {
                NativeCore.nativeCheatAdd(h, c.name, c.code, true)
            }
        }
    }

    private fun saveStateSlot(slot: Int) {
        val rom = romEntry ?: return
        val file = RomRepository.getStateFile(this, rom, slot)
        val thumbFile = RomRepository.getStateThumbFile(this, rom, slot)
        coreLock.withLock {
            if (nativeHandle == 0L) return
            val rc = NativeCore.nativeSaveStateFile(nativeHandle, file.absolutePath)
            if (rc == 0) {
                val progFile = java.io.File("${file.absolutePath}.raprog")
                if (raSessionState.value != null) {
                    val progBytes = NativeCore.raSerializeProgress(nativeHandle)
                    if (progBytes != null && progBytes.isNotEmpty()) {
                        runCatching { progFile.writeBytes(progBytes) }
                    } else if (progFile.exists()) {
                        progFile.delete()
                    }
                }
                val wh = IntArray(2)
                NativeCore.nativeGetVideoInfo(nativeHandle, wh)
                val w = wh[0].coerceAtLeast(160)
                val h = wh[1].coerceAtLeast(144)
                val px = IntArray(w * h)
                if (NativeCore.nativeCopyFrame(nativeHandle, px, w, NativeCore.PIXEL_FORMAT_RGBA8888) == 0) {
                    for (i in px.indices) {
                        val v = px[i]
                        val r = v and 0xFF
                        val g = (v ushr 8) and 0xFF
                        val b = (v ushr 16) and 0xFF
                        px[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                    }
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.setPixels(px, 0, w, 0, 0, w, h)
                    runCatching {
                        FileOutputStream(thumbFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
                    }
                    bmp.recycle()
                }
                showToast(if (slot == 0) getString(R.string.toast_quicksave_ok) else getString(R.string.toast_slot_saved_fmt, slot))
            }
        }
    }

    private fun captureScreenshot() {
        val rom = romEntry ?: return
        coreLock.withLock {
            if (nativeHandle == 0L) return
            val wh = IntArray(2)
            NativeCore.nativeGetVideoInfo(nativeHandle, wh)
            val w = wh[0].coerceAtLeast(160)
            val h = wh[1].coerceAtLeast(144)
            val px = IntArray(w * h)
            if (NativeCore.nativeCopyFrame(nativeHandle, px, w, NativeCore.PIXEL_FORMAT_RGBA8888) == 0) {
                for (i in px.indices) {
                    val v = px[i]
                    val r = v and 0xFF
                    val g = (v ushr 8) and 0xFF
                    val b = (v ushr 16) and 0xFF
                    px[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.setPixels(px, 0, w, 0, 0, w, h)
                val dir = java.io.File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "STORM_GBCA").apply { mkdirs() }
                val safeTitle = rom.displayTitle.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(32).ifBlank { "shot" }
                val outFile = java.io.File(dir, "${safeTitle}_${System.currentTimeMillis()}.png")
                val saved = runCatching {
                    FileOutputStream(outFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    true
                }.getOrDefault(false)
                bmp.recycle()
                showToast(if (saved) getString(R.string.toast_screenshot_saved_fmt, outFile.name) else getString(R.string.toast_screenshot_fail))
            }
        }
    }

    private fun applySoftPatchFromUri(uri: Uri) {
        if (raHardcoreActive.get()) {
            showToast(getString(R.string.ra_hardcore_blocked_patch))
            return
        }
        val cachePatch = java.io.File(cacheDir, "active_soft_patch_${System.currentTimeMillis()}.patch")
        val copied = runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(cachePatch).use { output -> input.copyTo(output) }
            }
            cachePatch.exists() && cachePatch.length() > 4L
        }.getOrDefault(false)
        if (!copied) {
            cachePatch.delete()
            showToast(getString(R.string.toast_patch_read_fail))
            return
        }
        val rc = coreLock.withLock {
            if (nativeHandle == 0L) -1 else NativeCore.nativeApplyPatchFile(nativeHandle, cachePatch.absolutePath)
        }
        cachePatch.delete()
        if (rc == 0) {
            setPaused(false)
            showToast(getString(R.string.toast_patch_applied))
        } else {
            showToast(getString(R.string.toast_patch_incompatible))
        }
    }

    private fun loadStateSlot(slot: Int) {
        if (raHardcoreActive.get()) {
            showToast(getString(R.string.ra_hardcore_blocked_load_state))
            return
        }
        val rom = romEntry ?: return
        val file = RomRepository.getStateFile(this, rom, slot)
        if (!file.exists()) {
            showToast(getString(R.string.toast_slot_empty_fmt, slot))
            return
        }
        coreLock.withLock {
            if (nativeHandle == 0L) return
            val rc = NativeCore.nativeLoadStateFile(nativeHandle, file.absolutePath)
            if (rc == NativeCore.STORM_ERR_HARDCORE_BLOCKED) {
                showToast(getString(R.string.ra_hardcore_blocked_load_state))
            } else if (rc == 0) {
                val progFile = java.io.File("${file.absolutePath}.raprog")
                if (progFile.exists() && raSessionState.value != null) {
                    runCatching {
                        val bytes = progFile.readBytes()
                        if (bytes.isNotEmpty()) {
                            NativeCore.raDeserializeProgress(nativeHandle, bytes)
                        }
                    }
                }
                requestVideoRender()
                showToast(if (slot == 0) getString(R.string.toast_quickload_ok) else getString(R.string.toast_slot_loaded_fmt, slot))
            }
        }
    }

    private fun performRewindStep() {
        if (raHardcoreActive.get()) {
            showToast(getString(R.string.ra_hardcore_blocked_rewind))
            return
        }
        coreLock.withLock {
            if (nativeHandle == 0L) return
            val rc = NativeCore.nativeRewindStep(nativeHandle)
            if (rc == NativeCore.STORM_ERR_HARDCORE_BLOCKED) {
                showToast(getString(R.string.ra_hardcore_blocked_rewind))
            } else if (rc == 0) {
                rewindFramesState.intValue = NativeCore.nativeRewindAvailableFrames(nativeHandle)
                requestVideoRender()
            } else {
                showToast(getString(R.string.toast_rewind_empty))
            }
        }
    }

    private fun togglePauseMenu() {
        setPaused(!showPauseMenuState.value)
    }

    private fun setPaused(paused: Boolean) {
        isPaused.set(paused)
        showPauseMenuState.value = paused
        NativeCore.nativeAudioPause(paused || GbcaPrefs.coreConfigState.value.audioMuted)
        if (paused) {
            coreLock.withLock {
                if (nativeHandle != 0L) {
                    rewindFramesState.intValue = NativeCore.nativeRewindAvailableFrames(nativeHandle)
                }
            }
            flushSramIfDirty()
        }
    }

    private fun pushCombinedKeys() {
        val combined = touchKeyMask.get() or physicalKeyMask.get()
        if (nativeHandle != 0L) {
            NativeCore.nativeSetKeys(nativeHandle, combined)
        }
    }

    private fun flushSramIfDirty() {
        val rom = romEntry ?: return
        coreLock.withLock {
            if (nativeHandle != 0L && NativeCore.nativeIsSramDirty(nativeHandle)) {
                val sramFile = RomRepository.getSramFile(this, rom)
                NativeCore.nativeSaveSramFile(nativeHandle, sramFile.absolutePath)
            }
        }
    }

    private fun flushSram() {
        val rom = romEntry ?: return
        coreLock.withLock {
            if (nativeHandle != 0L) {
                val sramFile = RomRepository.getSramFile(this, rom)
                NativeCore.nativeSaveSramFile(nativeHandle, sramFile.absolutePath)
            }
        }
        if (sessionStartMs > 0L) {
            val deltaSec = ((System.currentTimeMillis() - sessionStartMs) / 1000L).coerceAtLeast(0L)
            sessionStartMs = System.currentTimeMillis()
            val list = GbcaPrefs.getCachedRoms().map {
                if (it.uriString == rom.uriString) {
                    it.copy(
                        lastPlayedEpochMs = System.currentTimeMillis(),
                        totalPlaySeconds = it.totalPlaySeconds + deltaSec,
                    )
                } else it
            }
            GbcaPrefs.saveCachedRoms(list)
        }
    }

    private fun showToast(msg: String) {
        activityScope.launch(Dispatchers.Main) {
            statusToastState.value = msg
            kotlinx.coroutines.delay(1800)
            if (statusToastState.value == msg) statusToastState.value = null
        }
    }

    private fun registerMotionSensorsIfNeeded() {
        val mgr = sensorManager ?: return
        mgr.unregisterListener(this)
        if (romSupportsTilt) {
            mgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
                mgr.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }
        if (romSupportsGyro) {
            mgr.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let {
                mgr.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun registerThermalListenerApi29() {
        val pm = powerManager ?: return
        val initialThrottled = pm.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE
        if (isThermalThrottled.getAndSet(initialThrottled) != initialThrottled) {
            applyCoreConfig()
        }
        val listener = (thermalListenerObj as? PowerManager.OnThermalStatusChangedListener)
            ?: PowerManager.OnThermalStatusChangedListener { status ->
                val throttle = status >= PowerManager.THERMAL_STATUS_MODERATE
                if (isThermalThrottled.getAndSet(throttle) != throttle) {
                    applyCoreConfig()
                }
            }.also { thermalListenerObj = it }
        runCatching { pm.addThermalStatusListener(listener) }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun unregisterThermalListenerApi29() {
        val pm = powerManager ?: return
        val listener = thermalListenerObj as? PowerManager.OnThermalStatusChangedListener ?: return
        runCatching { pm.removeThermalStatusListener(listener) }
    }

    override fun onResume() {
        super.onResume()
        isActivityResumed = true
        supportActionBar?.hide()
        AppThemeManager.applyImmersiveFullscreen(this)
        refreshDisplayHz()
        val resumeCfg = GbcaPrefs.coreConfigState.value
        val backendChanged = resumeCfg.graphicsBackend != activeGraphicsBackend
        val driverChanged = resumeCfg.customVulkanDriverPath != activeCustomDriverPath
        if (backendChanged || driverChanged) {
            activeGraphicsBackend = resumeCfg.graphicsBackend
            activeCustomDriverPath = resumeCfg.customVulkanDriverPath
            if (resumeCfg.graphicsBackend == 1) {
                if (!useVulkanState.value) {
                    glView?.onPause()
                    useVulkanState.value = true
                    activeBackendLabelState.value = "Vulkan"
                    refreshTelemetryAsync(true)
                } else if (driverChanged) {
                    val surface = vkView?.holder?.surface
                    if (surface != null && surface.isValid) {
                        val ok = vkLock.withLock {
                            NativeCore.nativeVkDestroy()
                            NativeCore.nativeVkInit(surface, resumeCfg.customVulkanDriverPath)
                        }
                        vulkanReady.set(ok)
                        if (ok) {
                            vkLock.withLock { NativeCore.nativeVkResize(glSurfaceW, glSurfaceH) }
                            pendingVkRender.set(true)
                            refreshTelemetryAsync(true)
                        } else {
                            useVulkanState.value = false
                            activeBackendLabelState.value = "GLES"
                            showToast(getString(R.string.toast_vulkan_fallback))
                            refreshTelemetryAsync(false)
                        }
                    } else {
                        showToast(getString(R.string.toast_vulkan_fallback))
                    }
                }
            } else if (useVulkanState.value) {
                vulkanReady.set(false)
                vkLock.withLock { NativeCore.nativeVkDestroy() }
                useVulkanState.value = false
                activeBackendLabelState.value = "GLES"
                refreshTelemetryAsync(false)
            }
        }
        if (!useVulkanState.value) glView?.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            registerThermalListenerApi29()
        }
        val isLoggedInNow = RetroAchievementsClient.isLoggedIn()
        val wantHardcore = isLoggedInNow && GbcaPrefs.isRaHardcore()
        val wasHardcore = raHardcoreActive.get()
        if (wantHardcore && !wasHardcore) {
            executeSwitchToHardcore()
        } else if (!wantHardcore && wasHardcore) {
            performSwitchToCasual(persistPref = false)
        } else if (!isLoggedInNow && raSessionState.value != null) {
            refreshRaSessionAsync()
        } else if (isLoggedInNow && !lastRaLoggedInState && raSessionState.value == null) {
            refreshRaSessionAsync()
        }
        lastRaLoggedInState = isLoggedInNow
        applyCoreConfig()
        NativeCore.nativeSetRetroArchShader(GbcaPrefs.getOverlayProfile(activeSystem).retroArchShaderPath)
        if (!showPauseMenuState.value) {
            isPaused.set(false)
            NativeCore.nativeAudioPause(GbcaPrefs.coreConfigState.value.audioMuted)
        }
        registerMotionSensorsIfNeeded()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            AppThemeManager.applyImmersiveFullscreen(this)
        }
    }

    override fun onPause() {
        super.onPause()
        isActivityResumed = false
        isPaused.set(true)
        NativeCore.nativeAudioPause(true)
        flushSram()
        sensorManager?.unregisterListener(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            unregisterThermalListenerApi29()
        }
        if (!useVulkanState.value) glView?.onPause()
    }

    override fun onStop() {
        super.onStop()
        flushSram()
    }

    override fun onDestroy() {
        isRunning.set(false)
        isPaused.set(false)
        pendingVkRender.set(false)
        val thread = emuThread
        emuThread = null
        thread?.interrupt()
        runCatching { thread?.join(2000L) }
        flushSram()
        coreLock.withLock {
            if (nativeHandle != 0L) {
                NativeCore.nativeDestroy(nativeHandle)
                nativeHandle = 0L
            }
        }
        vulkanReady.set(false)
        vkLock.withLock { NativeCore.nativeVkDestroy() }
        glView?.queueEvent { NativeCore.nativeGlesDestroy() }
        RetroAchievementsClient.currentCoreName = null
        activityScope.cancel()
        super.onDestroy()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || nativeHandle == 0L) return
        val cfg = GbcaPrefs.coreConfigState.value
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> if (cfg.sensorTiltEnabled) {
                val tx = (event.values[0] / 9.81f).coerceIn(-2f, 2f)
                val ty = (event.values[1] / 9.81f).coerceIn(-2f, 2f)
                NativeCore.nativeSetTilt(nativeHandle, tx, ty)
            }
            Sensor.TYPE_GYROSCOPE -> if (cfg.sensorGyroEnabled) {
                NativeCore.nativeSetGyro(nativeHandle, event.values[2].coerceIn(-4f, 4f))
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN) return super.onKeyDown(keyCode, event)
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_BUTTON_MODE) {
            togglePauseMenu()
            return true
        }
        val matched = PhysicalAction.entries.firstOrNull {
            val mapped = GbcaPrefs.getKeyCodeForAction(it)
            mapped != 0 && mapped == keyCode
        }
        if (matched != null) {
            if (matched.coreKeyMask != 0) {
                physicalKeyMask.updateAndGet { it or matched.coreKeyMask }
                pushCombinedKeys()
            } else if (event?.repeatCount == 0) {
                handleHotkey(matched, pressed = true)
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN) return super.onKeyUp(keyCode, event)
        val matched = PhysicalAction.entries.firstOrNull {
            val mapped = GbcaPrefs.getKeyCodeForAction(it)
            mapped != 0 && mapped == keyCode
        }
        if (matched != null) {
            if (matched.coreKeyMask != 0) {
                physicalKeyMask.updateAndGet { it and matched.coreKeyMask.inv() }
                pushCombinedKeys()
            } else {
                handleHotkey(matched, pressed = false)
            }
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent?): Boolean {
        if (event != null &&
            ((event.source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
             (event.source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD)
        ) {
            val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            val stickX = event.getAxisValue(MotionEvent.AXIS_X)
            val stickY = event.getAxisValue(MotionEvent.AXIS_Y)
            val ax = if (kotlin.math.abs(hatX) > 0.3f) hatX else stickX
            val ay = if (kotlin.math.abs(hatY) > 0.3f) hatY else stickY

            var maskBits = 0
            if (ax < -0.45f) maskBits = maskBits or NativeCore.KEY_LEFT
            if (ax > 0.45f) maskBits = maskBits or NativeCore.KEY_RIGHT
            if (ay < -0.45f) maskBits = maskBits or NativeCore.KEY_UP
            if (ay > 0.45f) maskBits = maskBits or NativeCore.KEY_DOWN

            val lTrig = maxOf(
                event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
                event.getAxisValue(MotionEvent.AXIS_BRAKE),
            )
            val rTrig = maxOf(
                event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
                event.getAxisValue(MotionEvent.AXIS_GAS),
            )
            if (lTrig > 0.35f) maskBits = maskBits or NativeCore.KEY_L
            if (rTrig > 0.35f) maskBits = maskBits or NativeCore.KEY_R

            val axisControlled = NativeCore.KEY_LEFT or NativeCore.KEY_RIGHT or
                NativeCore.KEY_UP or NativeCore.KEY_DOWN or
                NativeCore.KEY_L or NativeCore.KEY_R
            physicalKeyMask.updateAndGet { (it and axisControlled.inv()) or maskBits }
            pushCombinedKeys()
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    private fun handleHotkey(action: PhysicalAction, pressed: Boolean) {
        when (action) {
            PhysicalAction.FAST_FORWARD -> {
                fastForward.set(pressed)
                val rawMult = if (pressed) GbcaPrefs.coreConfigState.value.fastForwardMultiplier else 1.0f
                val mult = if (raHardcoreActive.get()) rawMult.coerceAtLeast(1.0f) else rawMult
                coreLock.withLock {
                    if (nativeHandle != 0L) NativeCore.nativeSetFastForward(nativeHandle, mult)
                }
            }
            PhysicalAction.REWIND -> if (pressed) performRewindStep()
            PhysicalAction.QUICK_SAVE -> if (pressed) saveStateSlot(0)
            PhysicalAction.QUICK_LOAD -> if (pressed) loadStateSlot(0)
            PhysicalAction.SOLAR_UP -> if (pressed) {
                GbcaPrefs.updateCoreConfig { it.copy(solarLux = (it.solarLux + 1).coerceAtMost(10)) }
                applyCoreConfig()
                showToast(getString(R.string.toast_solar_fmt, GbcaPrefs.coreConfigState.value.solarLux))
            }
            PhysicalAction.SOLAR_DOWN -> if (pressed) {
                GbcaPrefs.updateCoreConfig { it.copy(solarLux = (it.solarLux - 1).coerceAtLeast(0)) }
                applyCoreConfig()
                showToast(getString(R.string.toast_solar_fmt, GbcaPrefs.coreConfigState.value.solarLux))
            }
            else -> {}
        }
    }

    private fun activateRaRuntimeLocked(h: Long, session: RaGameSession, hardcore: Boolean) {
        NativeCore.raClearAll(h)
        for (ach in session.achievements) {
            val alreadyUnlocked = if (hardcore) ach.unlockedHardcore else ach.unlocked
            if (ach.flags == 3 && ach.memAddr.isNotBlank() && !alreadyUnlocked) {
                NativeCore.raActivateAchievement(h, ach.id, ach.memAddr)
            }
        }
        for (lb in session.leaderboards) {
            if (!lb.hidden && lb.mem.isNotBlank()) {
                NativeCore.raActivateLeaderboard(h, lb.id, lb.mem, lb.format)
            }
        }
        if (session.richPresencePatch.isNotBlank() && (hardcore || GbcaPrefs.isRaRichPresence())) {
            NativeCore.raActivateRichPresence(h, session.richPresencePatch)
        }
    }

    private fun showRaStartupBanner(session: RaGameSession, hardcore: Boolean) {
        raBannerJob?.cancel()
        raBannerJob = activityScope.launch(Dispatchers.Main) {
            raStartupBannerState.value = session to hardcore
            delay(4000L)
            raStartupBannerState.value = null
        }
    }

    private fun showRaUnlockToast(ach: RaAchievementBadge, hardcore: Boolean) {
        if (!GbcaPrefs.isRaNotifications()) return
        raUnlockToastJob?.cancel()
        raUnlockToastJob = activityScope.launch(Dispatchers.Main) {
            raUnlockToastState.value = RaUnlockToastData(ach, hardcore)
            delay(3600L)
            raUnlockToastState.value = null
        }
    }

    private fun showRaMeasuredPill(text: String) {
        if (!GbcaPrefs.isRaProgressIndicators()) return
        raMeasuredPillJob?.cancel()
        raMeasuredPillJob = activityScope.launch(Dispatchers.Main) {
            raMeasuredPillState.value = text
            delay(2800L)
            raMeasuredPillState.value = null
        }
    }

    private fun startRaLoops() {
        raPollJob?.cancel()
        raPingJob?.cancel()

        raPollJob = activityScope.launch(Dispatchers.Default) {
            while (isActive && isRunning.get()) {
                delay(100L)
                if (isPaused.get() || nativeHandle == 0L) continue
                val events = mutableListOf<String>()
                var rpText = ""
                coreLock.withLock {
                    if (nativeHandle != 0L) {
                        while (true) {
                            val ev = NativeCore.raPollEvent(nativeHandle) ?: break
                            events.add(ev)
                        }
                        rpText = NativeCore.raGetRichPresence(nativeHandle)
                    }
                }
                if (rpText.isNotBlank() || events.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        if (rpText.isNotBlank()) {
                            raRichPresenceState.value = rpText
                        }
                        for (ev in events) {
                            handleRaEvent(ev)
                        }
                    }
                }
            }
        }

        raPingJob = activityScope.launch(Dispatchers.IO) {
            delay(5_000L)
            while (isActive && isRunning.get()) {
                val sess = raSessionState.value
                val hash = activeRaHash
                if (sess != null && hash.isNotBlank() && nativeHandle != 0L) {
                    val rp = coreLock.withLock {
                        if (nativeHandle != 0L) NativeCore.raGetRichPresence(nativeHandle) else ""
                    }
                    if (rp.isNotBlank()) {
                        raRichPresenceState.value = rp
                    }
                    RetroAchievementsClient.sendPing(sess.gameId, hash, rp, raHardcoreActive.get())
                }
                delay(120_000L)
            }
        }
    }

    private fun handleRaEvent(rawEvent: String) {
        val parts = rawEvent.split('|', limit = 5)
        val type = parts.getOrNull(0)?.toIntOrNull() ?: return
        val id = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val value = parts.getOrNull(2)?.toLongOrNull() ?: 0L
        val target = parts.getOrNull(3)?.toLongOrNull() ?: 0L
        val formatted = parts.getOrNull(4) ?: ""
        val sess = raSessionState.value ?: return
        val isHc = raHardcoreActive.get()

        when (type) {
            NativeCore.RA_EVT_ACH_TRIGGERED -> {
                val ach = sess.achievements.firstOrNull { it.id == id } ?: return
                val updatedList = sess.achievements.map {
                    if (it.id == id) {
                        it.copy(
                            unlocked = true,
                            unlockedCasual = true,
                            unlockedHardcore = it.unlockedHardcore || isHc,
                        )
                    } else it
                }
                raSessionState.value = sess.copy(achievements = updatedList)
                raPrimedMapState.value = raPrimedMapState.value - id
                raMeasuredMapState.value = raMeasuredMapState.value - id
                coreLock.withLock {
                    if (nativeHandle != 0L) NativeCore.raDeactivateAchievement(nativeHandle, id)
                }
                showRaUnlockToast(ach, isHc)
                val hash = activeRaHash.ifBlank { sess.gameHash }
                activityScope.launch(Dispatchers.IO) {
                    RetroAchievementsClient.awardAchievement(id, isHc, hash)
                }
            }
            NativeCore.RA_EVT_ACH_PRIMED -> {
                val ach = sess.achievements.firstOrNull { it.id == id } ?: return
                raPrimedMapState.value = raPrimedMapState.value + (id to ach)
            }
            NativeCore.RA_EVT_ACH_UNPRIMED, NativeCore.RA_EVT_ACH_RESET -> {
                raPrimedMapState.value = raPrimedMapState.value - id
            }
            NativeCore.RA_EVT_ACH_MEASURED -> {
                val ach = sess.achievements.firstOrNull { it.id == id }
                raMeasuredMapState.value = raMeasuredMapState.value + (id to RaMeasuredProgress(value, target, formatted))
                val label = formatted.ifBlank { "$value/$target" }
                showRaMeasuredPill(if (ach != null) "${ach.title}: $label" else label)
            }
            NativeCore.RA_EVT_LBOARD_STARTED, NativeCore.RA_EVT_LBOARD_UPDATED -> {
                if (RetroAchievementsClient.isLeaderboardPopupsEnabled()) {
                    val lb = sess.leaderboards.firstOrNull { it.id == id }
                    val scoreLabel = formatted.ifBlank { value.toString() }
                    raActiveLeaderboardState.value = RaActiveLeaderboardData(
                        id = id,
                        title = lb?.title ?: "Leaderboard #$id",
                        scoreText = scoreLabel,
                    )
                }
            }
            NativeCore.RA_EVT_LBOARD_CANCELED -> {
                if (raActiveLeaderboardState.value?.id == id) {
                    raActiveLeaderboardState.value = null
                }
            }
            NativeCore.RA_EVT_LBOARD_TRIGGERED -> {
                if (raActiveLeaderboardState.value?.id == id) {
                    raActiveLeaderboardState.value = null
                }
                val lb = sess.leaderboards.firstOrNull { it.id == id }
                val scoreLabel = formatted.ifBlank { value.toString() }
                if (isHc) {
                    val hash = activeRaHash.ifBlank { sess.gameHash }
                    activityScope.launch(Dispatchers.IO) {
                        RetroAchievementsClient.submitLeaderboard(id, value.toInt(), hash, true)
                    }
                    if (RetroAchievementsClient.isLeaderboardPopupsEnabled()) {
                        showToast(getString(R.string.ra_lb_submitted_fmt, lb?.title ?: "Leaderboard #$id", scoreLabel))
                    }
                }
            }
        }
    }

    private fun requestRaModeSwitch(targetHardcore: Boolean) {
        if (targetHardcore == raHardcoreActive.get()) {
            GbcaPrefs.setRaHardcore(targetHardcore)
            return
        }
        if (!targetHardcore) {
            // Hardcore -> Casual allowed immediately without console reset
            performSwitchToCasual(persistPref = true)
        } else {
            // Casual -> Hardcore MUST confirm full console reset
            showSwitchToHardcoreDialogState.value = true
        }
    }

    private fun performSwitchToCasual(persistPref: Boolean = true) {
        if (persistPref) {
            GbcaPrefs.setRaHardcore(false)
        }
        raHardcoreActive.set(false)
        raHardcoreActiveState.value = false
        coreLock.withLock {
            if (nativeHandle != 0L) {
                NativeCore.setHardcoreMode(nativeHandle, false)
                romEntry?.let { rom ->
                    applyCoreConfigLocked(nativeHandle, rom)
                    val cheats = GbcaPrefs.getCheatsForRom(RomRepository.getRomStorageKey(rom))
                    applyCheatsLocked(nativeHandle, cheats)
                }
                raSessionState.value?.let { activateRaRuntimeLocked(nativeHandle, it, false) }
            }
        }
        showToast(getString(R.string.ra_switched_casual_toast))
        val sess = raSessionState.value
        if (RetroAchievementsClient.isLoggedIn()) {
            if (sess != null) {
                showRaStartupBanner(sess, false)
                val hash = activeRaHash.ifBlank { sess.gameHash }
                if (hash.isNotBlank()) {
                    activityScope.launch(Dispatchers.IO) {
                        RetroAchievementsClient.startSession(sess.gameId, hash, false)
                    }
                }
            } else {
                refreshRaSessionAsync()
            }
        } else {
            refreshRaSessionAsync()
        }
    }

    private fun executeSwitchToHardcore() {
        GbcaPrefs.setRaHardcore(true)
        GbcaPrefs.setRaRichPresence(true)
        raHardcoreActive.set(true)
        raHardcoreActiveState.value = true
        raPrimedMapState.value = emptyMap()
        raMeasuredMapState.value = emptyMap()
        raActiveLeaderboardState.value = null

        coreLock.withLock {
            if (nativeHandle != 0L) {
                NativeCore.nativeCheatsClear(nativeHandle)
                val currentLinkMode = parseLinkStatus(NativeCore.nativeLinkGetStatus(nativeHandle)).mode
                if (currentLinkMode != NativeCore.LINK_NONE && currentLinkMode != NativeCore.LINK_GB_PRINTER) {
                    NativeCore.nativeLinkStop(nativeHandle)
                }
                if (fastForward.get()) {
                    val clamped = GbcaPrefs.coreConfigState.value.fastForwardMultiplier.coerceAtLeast(1.0f)
                    NativeCore.nativeSetFastForward(nativeHandle, clamped)
                }
                romEntry?.let { applyCoreConfigLocked(nativeHandle, it) }
                NativeCore.setHardcoreMode(nativeHandle, true)
                NativeCore.nativeReset(nativeHandle)
                NativeCore.raReset(nativeHandle)
                raSessionState.value?.let { activateRaRuntimeLocked(nativeHandle, it, true) }
            }
        }

        refreshLinkStatus()
        requestVideoRender()
        showToast(getString(R.string.ra_switched_hardcore_toast))
        val sess = raSessionState.value
        if (sess != null) {
            showRaStartupBanner(sess, true)
            val hash = activeRaHash.ifBlank { sess.gameHash }
            if (hash.isNotBlank()) {
                activityScope.launch(Dispatchers.IO) {
                    RetroAchievementsClient.startSession(sess.gameId, hash, true)
                }
            }
            startRaLoops()
        } else if (RetroAchievementsClient.isLoggedIn()) {
            refreshRaSessionAsync()
        }
    }
}
