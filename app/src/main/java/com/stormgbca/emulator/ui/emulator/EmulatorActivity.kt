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
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.stormgbca.emulator.data.GbcaPrefs
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
import com.stormgbca.emulator.ui.ra.RetroAchievementsAccountDialog
import com.stormgbca.emulator.ui.settings.SettingsActivity
import com.stormgbca.emulator.ui.theme.AppThemeManager
import com.stormgbca.emulator.ui.theme.StormWatermelonTheme
import com.stormgbca.emulator.ui.theme.WatermelonMono
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import ru.stormgames.gbca.NativeCore
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
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

    private val isPaused = AtomicBoolean(false)
    private val isRunning = AtomicBoolean(false)
    private val fastForward = AtomicBoolean(false)
    private val touchKeyMask = AtomicInteger(0)
    private val physicalKeyMask = AtomicInteger(0)

    private var emuThread: Thread? = null
    private var sensorManager: SensorManager? = null
    private var vibrator: Vibrator? = null
    private var lastRumbleTimeMs = 0L
    private var sessionStartMs = 0L

    private val showPauseMenuState = androidx.compose.runtime.mutableStateOf(false)
    private val showSkinPickerState = androidx.compose.runtime.mutableStateOf(false)
    private val showOverlayPickerState = androidx.compose.runtime.mutableStateOf(false)
    private val showRamFinderState = androidx.compose.runtime.mutableStateOf(false)
    private val showRaAccountState = androidx.compose.runtime.mutableStateOf(false)
    private val activeCoreLabelState = androidx.compose.runtime.mutableStateOf("mGBA")
    private val activeBackendLabelState = androidx.compose.runtime.mutableStateOf("GLES 3.2")
    private val rewindFramesState = androidx.compose.runtime.mutableIntStateOf(0)
    private val statusToastState = androidx.compose.runtime.mutableStateOf<String?>(null)
    private val raSessionState = androidx.compose.runtime.mutableStateOf<RaGameSession?>(null)
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
            Toast.makeText(this, "Не удалось открыть ROM", Toast.LENGTH_SHORT).show()
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

        val wantVulkan = GbcaPrefs.coreConfigState.value.graphicsBackend == 1 &&
            runCatching { NativeCore.nativeIsVulkanSupported() }.getOrDefault(false)
        useVulkanState.value = wantVulkan
        activeBackendLabelState.value = if (wantVulkan) "Vulkan 1.1" else "GLES 3.2"

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
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
            StormWatermelonTheme {
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
                    activeRotationDeg = activeScreenCfg.rotationDeg
                    NativeCore.nativeGlesSetRotation(activeScreenCfg.rotationDeg)

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

                    glVpX = screenRect.left.roundToInt()
                    glVpY = (hPx - screenRect.bottom).roundToInt().coerceAtLeast(0)
                    vkVpY = screenRect.top.roundToInt().coerceAtLeast(0)
                    glVpW = screenRect.width.roundToInt().coerceAtLeast(1)
                    glVpH = screenRect.height.roundToInt().coerceAtLeast(1)

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

                    ConsoleSkinOverlayView(
                        skinId = skinId,
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
                            val mult = if (next) GbcaPrefs.coreConfigState.value.fastForwardMultiplier else 1.0f
                            coreLock.withLock {
                                if (nativeHandle != 0L) NativeCore.nativeSetFastForward(nativeHandle, mult)
                            }
                            showToast(if (next) "Ускорение x${mult.toInt()}" else "Нормальная скорость")
                        },
                        onRewindTap = { performRewindStep() },
                        onOpenMenu = { togglePauseMenu() },
                    )

                    // Subtle live HUD pill (Core + Platform + Backend + Scale)
                    if (!showPauseMenuState.value) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 8.dp, end = 10.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x99060912))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            val ffTag = if (fastForward.get()) " | FF" else ""
                            val scaleTag = if (coreCfg.resolutionScale > 1) " | ${coreCfg.resolutionScale}x" else ""
                            Text(
                                text = "${activeCoreLabelState.value} | ${activeSystem.label} | ${activeBackendLabelState.value}$scaleTag$ffTag",
                                color = Color(0xCC94A3B8),
                                fontFamily = WatermelonMono,
                                fontSize = 10.sp,
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
                        PauseMenuOverlay(
                            rom = rom,
                            system = activeSystem,
                            activeCoreName = activeCoreLabelState.value,
                            activeBackendLabel = activeBackendLabelState.value,
                            fastForwardActive = fastForward.get(),
                            rewindAvailableFrames = rewindFramesState.intValue,
                            raSession = raSessionState.value,
                            onResume = { setPaused(false) },
                            onQuickSave = { saveStateSlot(0) },
                            onQuickLoad = { loadStateSlot(0) },
                            onSaveSlot = { slot -> saveStateSlot(slot) },
                            onLoadSlot = { slot -> loadStateSlot(slot) },
                            onRewindStep = { performRewindStep() },
                            onToggleFastForward = {
                                val next = !fastForward.get()
                                fastForward.set(next)
                                val mult = if (next) GbcaPrefs.coreConfigState.value.fastForwardMultiplier else 1.0f
                                coreLock.withLock {
                                    if (nativeHandle != 0L) NativeCore.nativeSetFastForward(nativeHandle, mult)
                                }
                            },
                            onOpenSkinPicker = { showSkinPickerState.value = true },
                            onOpenOverlayPicker = { showOverlayPickerState.value = true },
                            onOpenLayoutEditor = {
                                val intent = Intent(this@EmulatorActivity, LayoutEditorActivity::class.java).apply {
                                    putExtra("system", activeSystem.name)
                                }
                                startActivity(intent)
                            },
                            onOpenRamFinder = { showRamFinderState.value = true },
                            onOpenRaAccount = { showRaAccountState.value = true },
                            onConfigApplied = { applyCoreConfig() },
                            onCheatsUpdated = { list -> applyCheats(list) },
                            onApplySoftPatch = { patchPickerLauncher.launch(arrayOf("*/*")) },
                            onTakeScreenshot = { captureScreenshot() },
                            onOpenSettings = {
                                startActivity(Intent(this@EmulatorActivity, SettingsActivity::class.java))
                            },
                            onReset = {
                                coreLock.withLock {
                                    if (nativeHandle != 0L) NativeCore.nativeReset(nativeHandle)
                                }
                                setPaused(false)
                            },
                            onExit = {
                                flushSram()
                                finish()
                            },
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
                            onDismiss = {
                                showRaAccountState.value = false
                                refreshRaSessionAsync()
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
            }

            override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
                glSurfaceW = width.coerceAtLeast(1)
                glSurfaceH = height.coerceAtLeast(1)
                NativeCore.nativeGlesResize(glSurfaceW, glSurfaceH)
            }

            override fun onDrawFrame(gl: GL10?) {
                val h = nativeHandle
                if (h == 0L) return
                val cfg = GbcaPrefs.coreConfigState.value
                val overlayProf = GbcaPrefs.getOverlayProfile(activeSystem)
                val displayMode = overlayProf.shaderOverlay
                val ghosting = cfg.lcdGhosting / 100f
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
                    activeBackendLabelState.value = "Vulkan 1.1"
                    NativeCore.nativeSetRetroArchShader(GbcaPrefs.getOverlayProfile(activeSystem).retroArchShaderPath)
                } else {
                    vulkanReady.set(false)
                    useVulkanState.value = false
                    activeBackendLabelState.value = "GLES 3.2"
                    showToast("Vulkan недоступен на этом устройстве - переключено на OpenGL ES 3.2")
                }
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                glSurfaceW = width.coerceAtLeast(1)
                glSurfaceH = height.coerceAtLeast(1)
                if (vulkanReady.get()) {
                    vkLock.withLock { NativeCore.nativeVkResize(glSurfaceW, glSurfaceH) }
                }
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                vulkanReady.set(false)
                vkLock.withLock { NativeCore.nativeVkDestroy() }
            }
        })
    }

    private fun requestVideoRender() {
        if (useVulkanState.value) {
            if (vulkanReady.get() && nativeHandle != 0L) {
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
        } else {
            glView?.requestRender()
        }
    }

    private fun bootNativeCore(rom: RomEntry) {
        activityScope.launch(Dispatchers.IO) {
            val cachedRom = RomRepository.materializeRomToCache(this@EmulatorActivity, rom)
            if (cachedRom == null) {
                activityScope.launch(Dispatchers.Main) {
                    Toast.makeText(this@EmulatorActivity, "Ошибка чтения ROM", Toast.LENGTH_LONG).show()
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

            coreLock.withLock {
                nativeHandle = h
                applyCoreConfigLocked(h, rom)
                val rc = NativeCore.nativeLoadRomFile(h, cachedRom.absolutePath)
                if (rc != 0) {
                    NativeCore.nativeDestroy(h)
                    nativeHandle = 0L
                    activityScope.launch(Dispatchers.Main) {
                        Toast.makeText(this@EmulatorActivity, "Ядро не смогло запустить ROM", Toast.LENGTH_LONG).show()
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
                    activeCoreLabelState.value = info.activeCore.ifBlank { "mGBA" }
                }

                val cheats = GbcaPrefs.getCheatsForRom(RomRepository.getRomStorageKey(rom))
                applyCheatsLocked(h, cheats)

                NativeCore.nativeSetRtc(h, true, System.currentTimeMillis() / 1000L)
                NativeCore.nativeAudioStart(h)
                if (GbcaPrefs.coreConfigState.value.audioMuted) {
                    NativeCore.nativeAudioPause(true)
                }
            }

            sessionStartMs = System.currentTimeMillis()
            startEmulationLoop()

            // Lookup RetroAchievements in background if signed in
            if (GbcaPrefs.getRaToken().isNotBlank()) {
                val raHash = NativeCore.nativeComputeRaHash(cachedRom.absolutePath, rom.platform)
                if (!raHash.isNullOrBlank()) {
                    val session = RetroAchievementsClient.lookupGameSession(raHash)
                    if (session != null) {
                        raSessionState.value = session
                        if (GbcaPrefs.isRaNotifications()) {
                            showToast("RetroAchievements: ${session.unlockedCount}/${session.totalCount} (${session.earnedPoints} pts)")
                        }
                    }
                }
            }
        }
    }

    private fun refreshRaSessionAsync() {
        val rom = romEntry ?: return
        if (GbcaPrefs.getRaToken().isBlank()) return
        activityScope.launch(Dispatchers.IO) {
            val cachedRom = RomRepository.materializeRomToCache(this@EmulatorActivity, rom) ?: return@launch
            val raHash = NativeCore.nativeComputeRaHash(cachedRom.absolutePath, rom.platform) ?: return@launch
            val session = RetroAchievementsClient.lookupGameSession(raHash)
            if (session != null) raSessionState.value = session
        }
    }

    private fun startEmulationLoop() {
        isRunning.set(true)
        emuThread = Thread({
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY)
            var nextTickNs = System.nanoTime()
            var frameCounter = 0

            while (isRunning.get()) {
                if (isPaused.get() || nativeHandle == 0L) {
                    Thread.sleep(16)
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
                if (skip == 0 || (frameCounter % (skip + 1)) == 0) {
                    requestVideoRender()
                }

                frameCounter++
                if (frameCounter % 300 == 0) {
                    flushSramIfDirty()
                }

                if (!ff) {
                    nextTickNs += FRAME_DURATION_NS
                    val sleepNs = nextTickNs - System.nanoTime()
                    if (sleepNs > 1_000_000L) {
                        Thread.sleep(sleepNs / 1_000_000L, (sleepNs % 1_000_000L).toInt())
                    } else if (sleepNs < -FRAME_DURATION_NS * 4) {
                        nextTickNs = System.nanoTime()
                    }
                } else {
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
        requestVideoRender()
    }

    private fun applyCoreConfigLocked(h: Long, rom: RomEntry) {
        val cfg = GbcaPrefs.coreConfigState.value
        val ov = rom.coreOverride
        val gbCore = if (ov >= 0) ov else cfg.preferredCoreGb
        val gbcCore = if (ov >= 0) ov else cfg.preferredCoreGbc
        val gbaCore = NativeCore.CORE_MGBA

        NativeCore.nativeSetConfig(
            h,
            gbCore, gbcCore, gbaCore,
            cfg.gbModel, cfg.sgbBorders, cfg.colorCorrectionMode,
            cfg.dmgPaletteIndex, null, cfg.lcdGhosting, cfg.videoFilter,
            cfg.audioHighpassMode, cfg.audioInterpMode,
            cfg.channelVolumes.toFloatArray(), cfg.masterVolume,
            cfg.runAheadFrames, cfg.rewindEnabled, cfg.rewindBufferMb, cfg.rewindInterval,
            cfg.biosGbPath, cfg.biosGbcPath, cfg.biosGbaPath, cfg.biosSgbPath,
            cfg.skipBiosIntro, cfg.dynamicRateControl, cfg.drcMaxSkew,
        )
        NativeCore.nativeSetUpscale(h, cfg.resolutionScale, cfg.upscaleMode)
        NativeCore.nativeSetPerformance(h, cfg.cpuOverclockPct, cfg.frameSkip)
        NativeCore.nativeSetSolarLux(h, cfg.solarLux)
        NativeCore.getRomInfo(h)?.let { info ->
            activeCoreLabelState.value = info.activeCore.ifBlank { "mGBA" }
        }
    }

    private fun applyCheats(cheats: List<CheatItem>) {
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
                showToast(if (slot == 0) "Быстрое сохранение выполнено" else "Сохранено в слот $slot")
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
                showToast(if (saved) "Скриншот сохранен: ${outFile.name}" else "Ошибка сохранения скриншота")
            }
        }
    }

    private fun applySoftPatchFromUri(uri: Uri) {
        val cachePatch = java.io.File(cacheDir, "active_soft_patch_${System.currentTimeMillis()}.patch")
        val copied = runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(cachePatch).use { output -> input.copyTo(output) }
            }
            cachePatch.exists() && cachePatch.length() > 4L
        }.getOrDefault(false)
        if (!copied) {
            cachePatch.delete()
            showToast("Не удалось прочитать файл патча")
            return
        }
        val rc = coreLock.withLock {
            if (nativeHandle == 0L) -1 else NativeCore.nativeApplyPatchFile(nativeHandle, cachePatch.absolutePath)
        }
        cachePatch.delete()
        if (rc == 0) {
            setPaused(false)
            showToast("Мягкий патч успешно применен")
        } else {
            showToast("Патч несовместим с этим ROM")
        }
    }

    private fun loadStateSlot(slot: Int) {
        val rom = romEntry ?: return
        val file = RomRepository.getStateFile(this, rom, slot)
        if (!file.exists()) {
            showToast("Слот $slot пуст")
            return
        }
        coreLock.withLock {
            if (nativeHandle == 0L) return
            if (NativeCore.nativeLoadStateFile(nativeHandle, file.absolutePath) == 0) {
                requestVideoRender()
                showToast(if (slot == 0) "Быстрое состояние загружено" else "Загружен слот $slot")
            }
        }
    }

    private fun performRewindStep() {
        coreLock.withLock {
            if (nativeHandle == 0L) return
            if (NativeCore.nativeRewindStep(nativeHandle) == 0) {
                rewindFramesState.intValue = NativeCore.nativeRewindAvailableFrames(nativeHandle)
                requestVideoRender()
            } else {
                showToast("Нет кадров для перемотки")
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

    override fun onResume() {
        super.onResume()
        supportActionBar?.hide()
        AppThemeManager.applyImmersiveFullscreen(this)
        if (!useVulkanState.value) glView?.onResume()
        applyCoreConfig()
        NativeCore.nativeSetRetroArchShader(GbcaPrefs.getOverlayProfile(activeSystem).retroArchShaderPath)
        if (!showPauseMenuState.value) {
            isPaused.set(false)
            NativeCore.nativeAudioPause(GbcaPrefs.coreConfigState.value.audioMuted)
        }
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            AppThemeManager.applyImmersiveFullscreen(this)
        }
    }

    override fun onPause() {
        super.onPause()
        isPaused.set(true)
        NativeCore.nativeAudioPause(true)
        flushSram()
        sensorManager?.unregisterListener(this)
        if (!useVulkanState.value) glView?.onPause()
    }

    override fun onStop() {
        super.onStop()
        flushSram()
    }

    override fun onDestroy() {
        isRunning.set(false)
        emuThread?.join(500)
        flushSram()
        coreLock.withLock {
            if (nativeHandle != 0L) {
                NativeCore.nativeDestroy(nativeHandle)
                nativeHandle = 0L
            }
        }
        vulkanReady.set(false)
        vkLock.withLock { NativeCore.nativeVkDestroy() }
        NativeCore.nativeGlesDestroy()
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
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_BUTTON_MODE) {
            togglePauseMenu()
            return true
        }
        val matched = PhysicalAction.entries.firstOrNull { GbcaPrefs.getKeyCodeForAction(it) == keyCode }
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
        val matched = PhysicalAction.entries.firstOrNull { GbcaPrefs.getKeyCodeForAction(it) == keyCode }
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
                val mult = if (pressed) GbcaPrefs.coreConfigState.value.fastForwardMultiplier else 1.0f
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
                showToast("Солнце: ${GbcaPrefs.coreConfigState.value.solarLux}/10")
            }
            PhysicalAction.SOLAR_DOWN -> if (pressed) {
                GbcaPrefs.updateCoreConfig { it.copy(solarLux = (it.solarLux - 1).coerceAtLeast(0)) }
                applyCoreConfig()
                showToast("Солнце: ${GbcaPrefs.coreConfigState.value.solarLux}/10")
            }
            else -> {}
        }
    }
}
