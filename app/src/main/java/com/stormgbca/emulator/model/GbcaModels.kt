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

enum class RomFilter(val labelRu: String, val labelEn: String) {
    ALL("Все", "All"),
    GB("GB", "GB"),
    GBC("GBC", "GBC"),
    GBA("GBA", "GBA"),
    FAVORITES("Избранное", "Favorites"),
}

enum class RomSortMode(val labelRu: String, val labelEn: String) {
    TITLE("По названию", "By Title"),
    RECENT("Недавние", "Recently Played"),
    PLATFORM("По платформе", "By Platform"),
    PLAY_TIME("По времени игры", "By Play Time"),
}

enum class RomViewMode {
    GRID,
    LIST,
}

enum class OverlayLayoutPreset(val key: String, val labelRu: String, val labelEn: String) {
    CLASSIC_HANDHELD("classic_handheld", "Классическая консоль", "Classic Handheld"),
    WIDE_GRIP("wide_grip", "Широкий хват", "Wide Grip"),
    COMPACT_MINIMAL("compact_minimal", "Компактный минимал", "Compact Minimal"),
    FLOATING_GLASS("floating_glass", "Парящее стекло", "Floating Glass"),
    CUSTOM("custom", "Пользовательская раскладка", "Custom Layout");

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
data class ScreenRectConfig(
    val useCustom: Boolean = false,
    val centerX: Float = 0.50f,
    val centerY: Float = 0.30f,
    val widthFraction: Float = 0.92f,
    val heightFraction: Float = 0.46f,
    val aspectMode: Int = 0, // 0=Fit Original, 1=Integer, 2=Stretch, 3=Free Custom
    val rotationDeg: Int = 0, // 0, 90, 180, 270
)

@Serializable
data class SystemOverlayProfile(
    val layoutPreset: String = OverlayLayoutPreset.CLASSIC_HANDHELD.key,
    val buttonStyle: String = "storm_neon",
    val shaderOverlay: Int = NativeCore.FILTER_RGB_SUBPIXEL,
    val retroArchShaderPath: String = "",
    val opacityPercent: Int = 78,
    val scalePercent: Int = 100,
    val hapticsEnabled: Boolean = true,
    val showShoulders: Boolean = false,
    val showQuickStrip: Boolean = true,
    val portraitScreen: ScreenRectConfig = ScreenRectConfig(
        useCustom = false, centerX = 0.50f, centerY = 0.28f, widthFraction = 0.94f, heightFraction = 0.46f, aspectMode = 0, rotationDeg = 0,
    ),
    val landscapeScreen: ScreenRectConfig = ScreenRectConfig(
        useCustom = false, centerX = 0.50f, centerY = 0.50f, widthFraction = 0.64f, heightFraction = 0.88f, aspectMode = 0, rotationDeg = 0,
    ),
    val dpadPos: CustomControlPos = CustomControlPos(0.16f, 0.72f, 1.0f),
    val abPos: CustomControlPos = CustomControlPos(0.84f, 0.72f, 1.0f),
    val startSelectPos: CustomControlPos = CustomControlPos(0.50f, 0.89f, 1.0f),
    val lPos: CustomControlPos = CustomControlPos(0.10f, 0.14f, 1.0f),
    val rPos: CustomControlPos = CustomControlPos(0.90f, 0.14f, 1.0f),
    val quickStripPos: CustomControlPos = CustomControlPos(0.50f, 0.08f, 1.0f),
)

@Serializable
data class CheatItem(
    val id: String,
    val name: String,
    val code: String,
    val enabled: Boolean = true,
)

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

    val displayTitle: String get() {
        val base = fileName.substringBeforeLast('.')
        return if (base.isNotBlank()) base else internalTitle.ifBlank { fileName }
    }

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
    val autoFetchCovers: Boolean = true,
    val graphicsBackend: Int = 0, // 0=OpenGL ES 3.2, 1=Vulkan 1.1
    val customVulkanDriverPath: String = "",
    val resolutionScale: Int = 1, // 1..4x internal upscaling
    val upscaleMode: Int = NativeCore.UPSCALE_NONE, // 0=None, 1=xBRZ, 2=Scale2x, 3=MMPX, 4=LCD HD, 5=CRT HD
    val cpuOverclockPct: Int = 100, // 50..400%
    val frameSkip: Int = 0, // 0..4
)

enum class PhysicalAction(val labelRu: String, val labelEn: String, val defaultKeyCode: Int, val coreKeyMask: Int = 0) {
    UP("Вверх", "Up", android.view.KeyEvent.KEYCODE_DPAD_UP, NativeCore.KEY_UP),
    DOWN("Вниз", "Down", android.view.KeyEvent.KEYCODE_DPAD_DOWN, NativeCore.KEY_DOWN),
    LEFT("Влево", "Left", android.view.KeyEvent.KEYCODE_DPAD_LEFT, NativeCore.KEY_LEFT),
    RIGHT("Вправо", "Right", android.view.KeyEvent.KEYCODE_DPAD_RIGHT, NativeCore.KEY_RIGHT),
    A("Кнопка A", "Button A", android.view.KeyEvent.KEYCODE_BUTTON_A, NativeCore.KEY_A),
    B("Кнопка B", "Button B", android.view.KeyEvent.KEYCODE_BUTTON_B, NativeCore.KEY_B),
    L("Триггер L", "Shoulder L", android.view.KeyEvent.KEYCODE_BUTTON_L1, NativeCore.KEY_L),
    R("Триггер R", "Shoulder R", android.view.KeyEvent.KEYCODE_BUTTON_R1, NativeCore.KEY_R),
    START("Старт", "Start", android.view.KeyEvent.KEYCODE_BUTTON_START, NativeCore.KEY_START),
    SELECT("Выбор", "Select", android.view.KeyEvent.KEYCODE_BUTTON_SELECT, NativeCore.KEY_SELECT),
    FAST_FORWARD("Ускорение", "Fast Forward", android.view.KeyEvent.KEYCODE_BUTTON_R2),
    REWIND("Перемотка назад", "Rewind", android.view.KeyEvent.KEYCODE_BUTTON_L2),
    QUICK_SAVE("Быстрое сохранение", "Quick Save", android.view.KeyEvent.KEYCODE_BUTTON_X),
    QUICK_LOAD("Быстрая загрузка", "Quick Load", android.view.KeyEvent.KEYCODE_BUTTON_Y),
    SOLAR_UP("Датчик солнца +", "Solar Sensor +", android.view.KeyEvent.KEYCODE_PLUS),
    SOLAR_DOWN("Датчик солнца -", "Solar Sensor -", android.view.KeyEvent.KEYCODE_MINUS),
}
