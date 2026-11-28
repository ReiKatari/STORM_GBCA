package com.stormgbca.emulator.model

import kotlinx.serialization.Serializable
import ru.stormgames.gbca.NativeCore

enum class ConsoleSystem(val label: String) {
    GB("GB"),
    GBC("GBC"),
    GBA("GBA");

    companion object {
        fun fromPlatform(platform: Int): ConsoleSystem = when (platform) {
            NativeCore.PLATFORM_GBC -> GBC
            NativeCore.PLATFORM_GBA -> GBA
            else -> GB
        }
    }
}

enum class RomFilter {
    ALL,
    GB,
    GBC,
    GBA,
    FAVORITES,
}

enum class RomSortMode {
    TITLE,
    RECENT,
    PLATFORM,
    PLAY_TIME,
}

enum class RomViewMode {
    GRID,
    LIST,
}

enum class OverlayLayoutPreset(val key: String) {
    CLASSIC_HANDHELD("classic_handheld"),
    WIDE_GRIP("wide_grip"),
    COMPACT_MINIMAL("compact_minimal"),
    FLOATING_GLASS("floating_glass"),
    CUSTOM("custom");

    companion object {
        fun fromKey(key: String?): OverlayLayoutPreset =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: CLASSIC_HANDHELD
    }
}

@Serializable
data class CustomControlPos(
    val xFraction: Float,
    val yFraction: Float,
    val scale: Float = 1.0f,
)

@Serializable
data class OrientationControlsLayout(
    val dpadPos: CustomControlPos,
    val abPos: CustomControlPos,
    val btnAPos: CustomControlPos,
    val btnBPos: CustomControlPos,
    val selectPos: CustomControlPos,
    val startPos: CustomControlPos,
    val lPos: CustomControlPos,
    val rPos: CustomControlPos,
    val menuBtnPos: CustomControlPos,
    val rewindBtnPos: CustomControlPos,
    val fastForwardBtnPos: CustomControlPos,
    val quickSaveBtnPos: CustomControlPos,
    val quickLoadBtnPos: CustomControlPos,
) {
    companion object {
        @Suppress("UNUSED_PARAMETER")
        fun defaultPortrait(hasShoulders: Boolean = true): OrientationControlsLayout {
            val qy = 0.048f
            return OrientationControlsLayout(
                dpadPos = CustomControlPos(0.185f, 0.745f, 1.0f),
                abPos = CustomControlPos(0.815f, 0.745f, 1.0f),
                btnAPos = CustomControlPos(0.910f, 0.688f, 1.0f),
                btnBPos = CustomControlPos(0.705f, 0.802f, 1.0f),
                selectPos = CustomControlPos(0.335f, 0.922f, 1.0f),
                startPos = CustomControlPos(0.665f, 0.922f, 1.0f),
                lPos = CustomControlPos(0.185f, 0.565f, 1.0f),
                rPos = CustomControlPos(0.815f, 0.565f, 1.0f),
                rewindBtnPos = CustomControlPos(0.120f, qy, 1.0f),
                fastForwardBtnPos = CustomControlPos(0.310f, qy, 1.0f),
                menuBtnPos = CustomControlPos(0.500f, qy, 1.0f),
                quickSaveBtnPos = CustomControlPos(0.690f, qy, 1.0f),
                quickLoadBtnPos = CustomControlPos(0.880f, qy, 1.0f),
            )
        }

        fun defaultLandscape(): OrientationControlsLayout = OrientationControlsLayout(
            dpadPos = CustomControlPos(0.100f, 0.615f, 1.0f),
            abPos = CustomControlPos(0.895f, 0.615f, 1.0f),
            btnAPos = CustomControlPos(0.948f, 0.530f, 1.0f),
            btnBPos = CustomControlPos(0.825f, 0.695f, 1.0f),
            selectPos = CustomControlPos(0.100f, 0.905f, 1.0f),
            startPos = CustomControlPos(0.900f, 0.905f, 1.0f),
            lPos = CustomControlPos(0.100f, 0.340f, 1.0f),
            rPos = CustomControlPos(0.895f, 0.340f, 1.0f),
            rewindBtnPos = CustomControlPos(0.055f, 0.090f, 1.0f),
            fastForwardBtnPos = CustomControlPos(0.138f, 0.090f, 1.0f),
            quickSaveBtnPos = CustomControlPos(0.862f, 0.090f, 1.0f),
            quickLoadBtnPos = CustomControlPos(0.945f, 0.090f, 1.0f),
            menuBtnPos = CustomControlPos(0.500f, 0.068f, 1.0f),
        )
    }
}

@Serializable
data class ScreenRectConfig(
    val useCustom: Boolean = false,
    val screenPreset: Int = 0, // 0=Inside 3D Skin, 1=Max Aspect Fit, 2=Fullscreen Edge-to-Edge, 3=Integer Pixel-Perfect, 4=Free Custom
    val hideSkinInFullscreen: Boolean = true,
    val centerX: Float = 0.50f,
    val centerY: Float = 0.30f,
    val widthFraction: Float = 0.92f,
    val heightFraction: Float = 0.46f,
    val aspectMode: Int = 0, // 0=Fit Original, 1=Integer, 2=Stretch, 3=Free Custom
    val rotationDeg: Int = 0, // 0, 90, 180, 270
)

@Serializable
data class SystemOverlayProfile(
    val layoutVersion: Int = 1, // 2 = 1.4.0 separate orientation controls + individual quick action buttons
    val layoutPreset: String = OverlayLayoutPreset.CLASSIC_HANDHELD.key,
    val buttonStyle: String = "storm_neon",
    val uiThemeKey: String = "", // "" = follow app theme, or any Theme.preferenceValue per GB/GBC/GBA!
    val shaderOverlay: Int = NativeCore.FILTER_RGB_SUBPIXEL,
    val retroArchShaderPath: String = "",
    val opacityPercent: Int = 78,
    val scalePercent: Int = 100,
    val hapticsEnabled: Boolean = true,
    val showShoulders: Boolean = false,
    val showQuickStrip: Boolean = true,
    // Individual Quick Action Button toggles (STORM DS parity)
    val showMenuBtn: Boolean = true,
    val showRewindBtn: Boolean = true,
    val showFastForwardBtn: Boolean = true,
    val showQuickSaveBtn: Boolean = true,
    val showQuickLoadBtn: Boolean = true,
    val separateABButtons: Boolean = false,
    val abSpacingPercent: Int = 150, // 100..180% gap multiplier so A and B never overlap
    val useAnalogStick: Boolean = false,
    val portraitScreen: ScreenRectConfig = ScreenRectConfig(
        useCustom = false, screenPreset = 0, hideSkinInFullscreen = true, centerX = 0.50f, centerY = 0.28f, widthFraction = 0.94f, heightFraction = 0.46f, aspectMode = 0, rotationDeg = 0,
    ),
    val landscapeScreen: ScreenRectConfig = ScreenRectConfig(
        useCustom = false, screenPreset = 0, hideSkinInFullscreen = true, centerX = 0.50f, centerY = 0.50f, widthFraction = 0.64f, heightFraction = 0.88f, aspectMode = 0, rotationDeg = 0,
    ),
    val portraitControls: OrientationControlsLayout = OrientationControlsLayout.defaultPortrait(true),
    val landscapeControls: OrientationControlsLayout = OrientationControlsLayout.defaultLandscape(),
    // Legacy version 1 fields preserved for seamless JSON deserialization
    val dpadPos: CustomControlPos = CustomControlPos(0.100f, 0.615f, 1.0f),
    val abPos: CustomControlPos = CustomControlPos(0.895f, 0.615f, 1.0f),
    val btnAPos: CustomControlPos = CustomControlPos(0.948f, 0.530f, 1.0f),
    val btnBPos: CustomControlPos = CustomControlPos(0.825f, 0.695f, 1.0f),
    val startSelectPos: CustomControlPos = CustomControlPos(0.500f, 0.922f, 1.0f),
    val lPos: CustomControlPos = CustomControlPos(0.100f, 0.340f, 1.0f),
    val rPos: CustomControlPos = CustomControlPos(0.895f, 0.340f, 1.0f),
    val quickStripPos: CustomControlPos = CustomControlPos(0.500f, 0.068f, 1.0f),
)

@Serializable
data class CheatItem(
    val id: String,
    val name: String,
    val code: String,
    val enabled: Boolean = true,
)

private val YEAR_TAG_REGEX = Regex("""\s*[\(\[]\s*(?:19|20)\d{2}\b.*""")
private val BRACKET_TAG_REGEX = Regex("""\s*[\(\[].*""")

@Serializable
data class RomEntry(
    val uriString: String,
    val fileName: String,
    val internalTitle: String,
    val platform: Int,
    val gameCode: String,
    val region: String,
    val crc32: Long,
    val md5: String,
    val romSize: Long,
    val hwFlags: Int,
    val isFavorite: Boolean = false,
    val coreOverride: Int = -1, // -1 = auto/global, 0 = mGBA, 1 = SameBoy
    val softPatchUri: String = "",
    val lastPlayedEpochMs: Long = 0L,
    val totalPlaySeconds: Long = 0L,
) {
    val consoleSystem: ConsoleSystem get() = ConsoleSystem.fromPlatform(platform)

    val platformLabel: String get() = when (platform) {
        NativeCore.PLATFORM_GB -> "GB"
        NativeCore.PLATFORM_GBC -> "GBC"
        NativeCore.PLATFORM_GBA -> "GBA"
        NativeCore.PLATFORM_SGB -> "SGB"
        else -> "GB"
    }

    val fileExtension: String get() {
        val ext = fileName.substringAfterLast('.', "").trim()
        return if (ext.isNotBlank() && ext.length <= 8) ".${ext.uppercase()}" else "-"
    }

    val displayTitle: String get() {
        val base = fileName.substringBeforeLast('.')
        return if (base.isNotBlank()) base else internalTitle.ifBlank { fileName }
    }

    val cleanTitleBeforeYear: String get() {
        val raw = displayTitle
        val yearMatch = YEAR_TAG_REGEX.find(raw)
        val cut = if (yearMatch != null) {
            raw.substring(0, yearMatch.range.first)
        } else {
            raw.replace(BRACKET_TAG_REGEX, "")
        }
        val trimmed = cut.trim().trimEnd('-', '_').trim()
        return trimmed.ifBlank { raw }
    }

    fun formattedTitle(useInternalTitle: Boolean): String =
        if (useInternalTitle && internalTitle.isNotBlank()) internalTitle else displayTitle

    val hasRtc: Boolean get() = (hwFlags and NativeCore.HW_RTC) != 0
    val hasRumble: Boolean get() = (hwFlags and NativeCore.HW_RUMBLE) != 0
    val hasSolar: Boolean get() = (hwFlags and NativeCore.HW_SOLAR) != 0
    val hasGyro: Boolean get() = (hwFlags and NativeCore.HW_GYRO) != 0
    val hasTilt: Boolean get() = (hwFlags and NativeCore.HW_TILT) != 0
}

@Serializable
data class GbcaCoreConfig(
    val preferredCoreGb: Int = NativeCore.CORE_MGBA,
    val preferredCoreGbc: Int = NativeCore.CORE_MGBA,
    val preferredCoreGba: Int = NativeCore.CORE_MGBA,
    val gbModel: Int = NativeCore.GB_MODEL_AUTO,
    val sgbBorders: Boolean = true,
    val colorCorrectionMode: Int = NativeCore.CC_MODERN_BALANCED,
    val dmgPaletteIndex: Int = 0,
    val lcdGhosting: Int = 18,
    val videoFilter: Int = NativeCore.FILTER_RGB_SUBPIXEL,
    val aspectMode: Int = 0, // 0=Fit Aspect, 1=Integer, 2=Stretch
    val audioHighpassMode: Int = NativeCore.HIGHPASS_ACCURATE,
    val audioInterpMode: Int = NativeCore.INTERP_HERMITE,
    val channelVolumes: List<Float> = listOf(1f, 1f, 1f, 1f, 1f, 1f),
    val masterVolume: Float = 1.0f,
    val audioMuted: Boolean = false,
    val runAheadFrames: Int = 0,
    val rewindEnabled: Boolean = true,
    val rewindBufferMb: Int = 16,
    val rewindInterval: Int = 1,
    val biosGbPath: String = "",
    val biosGbcPath: String = "",
    val biosGbaPath: String = "",
    val biosSgbPath: String = "",
    val skipBiosIntro: Boolean = true,
    val dynamicRateControl: Boolean = true,
    val drcMaxSkew: Float = 0.005f,
    val fastForwardMultiplier: Float = 2.0f,
    val sensorTiltEnabled: Boolean = true,
    val sensorGyroEnabled: Boolean = true,
    val rumbleEnabled: Boolean = true,
    val solarLux: Int = 5,
    val showBootSplash: Boolean = false,

    // Separate Cover & Badge toggles (ALL OFF by default, identical to STORM DS!)
    val showLibretroCovers: Boolean = false,
    val showRaCovers: Boolean = false,
    val showPlatformBadge: Boolean = false,
    val showRegionBadge: Boolean = false,
    val showHardwareBadges: Boolean = false,
    val showRaBadge: Boolean = false,
    val romDisplayNameMode: Int = 0, // 0=File name, 1=Internal ROM header

    // Graphics, Vulkan, Upscaling, AMD FSR & Device Optimization (Old + New devices)
    val graphicsBackend: Int = 0, // 0=OpenGL ES, 1=Vulkan
    val customVulkanDriverPath: String = "",
    val resolutionScale: Int = 1, // 1..4x internal upscaling
    val upscaleMode: Int = NativeCore.UPSCALE_NONE,
    val fsrSharpness: Int = 65, // 0..100 RCAS sharpness
    val fsrQualityMode: Int = 0, // 0=Quality 67% - 1.5x, 1=Balanced 59% - 1.7x, 2=Performance 50% - 2.0x, 3=Ultra Performance 33% - 3.0x
    val frameGenerationEnabled: Boolean = false, // Optional 60 -> 120 FPS optical-flow interpolation
    val cpuOverclockPct: Int = 100, // 50..400%
    val frameSkip: Int = 0, // 0..4
    val optimizationProfile: Int = -1, // -1=Auto-detect hardware, 0=Lite, 1=Balanced, 2=Performance, 3=High Accuracy

    // App language, Pause Menu, Auto-Update
    val appLanguage: String = "system",
    val extendedPauseMenu: Boolean = false,
    val autoCheckUpdates: Boolean = true,
    val updateSourcePref: String = "storm",
    val autoCheckGameUpdates: Boolean = true,

    // Multiplayer defaults
    val mpRoomName: String = "STORM GBCA Room",
    val mpPort: Int = 54320,
    val mpLastHostIp: String = "192.168.1.2",
    val mpDolphinIp: String = "127.0.0.1",
    val mpDolphinDataPort: Int = 54970,
    val mpDolphinClockPort: Int = 49420,
)

enum class PhysicalAction(val defaultKeyCode: Int, val coreKeyMask: Int = 0) {
    UP(android.view.KeyEvent.KEYCODE_DPAD_UP, NativeCore.KEY_UP),
    DOWN(android.view.KeyEvent.KEYCODE_DPAD_DOWN, NativeCore.KEY_DOWN),
    LEFT(android.view.KeyEvent.KEYCODE_DPAD_LEFT, NativeCore.KEY_LEFT),
    RIGHT(android.view.KeyEvent.KEYCODE_DPAD_RIGHT, NativeCore.KEY_RIGHT),
    A(android.view.KeyEvent.KEYCODE_BUTTON_A, NativeCore.KEY_A),
    B(android.view.KeyEvent.KEYCODE_BUTTON_B, NativeCore.KEY_B),
    L(android.view.KeyEvent.KEYCODE_BUTTON_L1, NativeCore.KEY_L),
    R(android.view.KeyEvent.KEYCODE_BUTTON_R1, NativeCore.KEY_R),
    START(android.view.KeyEvent.KEYCODE_BUTTON_START, NativeCore.KEY_START),
    SELECT(android.view.KeyEvent.KEYCODE_BUTTON_SELECT, NativeCore.KEY_SELECT),
    FAST_FORWARD(android.view.KeyEvent.KEYCODE_BUTTON_R2),
    REWIND(android.view.KeyEvent.KEYCODE_BUTTON_L2),
    QUICK_SAVE(android.view.KeyEvent.KEYCODE_BUTTON_X),
    QUICK_LOAD(android.view.KeyEvent.KEYCODE_BUTTON_Y),
    SOLAR_UP(android.view.KeyEvent.KEYCODE_PLUS),
    SOLAR_DOWN(android.view.KeyEvent.KEYCODE_MINUS),
}
