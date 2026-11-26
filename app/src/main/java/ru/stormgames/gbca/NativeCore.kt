package ru.stormgames.gbca

data class RomInfo(
    val platform: Int,
    val title: String,
    val gameCode: String,
    val maker: String,
    val region: String,
    val crc32: Long,
    val md5: String,
    val romSize: Long,
    val hwFlags: Int,
    val activeCore: String,
) {
    val hasRtc: Boolean get() = (hwFlags and NativeCore.HW_RTC) != 0
    val hasRumble: Boolean get() = (hwFlags and NativeCore.HW_RUMBLE) != 0
    val hasSolar: Boolean get() = (hwFlags and NativeCore.HW_SOLAR) != 0
    val hasGyro: Boolean get() = (hwFlags and NativeCore.HW_GYRO) != 0
    val hasTilt: Boolean get() = (hwFlags and NativeCore.HW_TILT) != 0
    val hasSgb: Boolean get() = (hwFlags and NativeCore.HW_SGB) != 0
    val hasCgb: Boolean get() = (hwFlags and NativeCore.HW_CGB) != 0
}

object NativeCore {
    init {
        System.loadLibrary("storm_gbca_core")
    }

    // Key bitmask (matches storm_gbca_core.h)
    const val KEY_A = 1 shl 0
    const val KEY_B = 1 shl 1
    const val KEY_SELECT = 1 shl 2
    const val KEY_START = 1 shl 3
    const val KEY_RIGHT = 1 shl 4
    const val KEY_LEFT = 1 shl 5
    const val KEY_UP = 1 shl 6
    const val KEY_DOWN = 1 shl 7
    const val KEY_R = 1 shl 8
    const val KEY_L = 1 shl 9

    // Platform IDs
    const val PLATFORM_NONE = 0
    const val PLATFORM_GB = 1
    const val PLATFORM_GBC = 2
    const val PLATFORM_GBA = 3
    const val PLATFORM_SGB = 4

    // Core backend IDs
    const val CORE_MGBA = 0
    const val CORE_SAMEBOY = 1

    // GB Hardware Model overrides
    const val GB_MODEL_AUTO = 0
    const val GB_MODEL_DMG = 1
    const val GB_MODEL_MGB = 2
    const val GB_MODEL_SGB = 3
    const val GB_MODEL_SGB2 = 4
    const val GB_MODEL_CGB = 5
    const val GB_MODEL_AGB = 6

    // Pixel formats
    const val PIXEL_FORMAT_RGBA8888 = 0
    const val PIXEL_FORMAT_BGRA8888 = 1

    // Color correction modes
    const val CC_DISABLED = 0
    const val CC_CORRECT_CURVES = 1
    const val CC_MODERN_BALANCED = 2
    const val CC_MODERN_BOOST = 3
    const val CC_REDUCE_CONTRAST = 4
    const val CC_LOW_CONTRAST = 5
    const val CC_MODERN_ACCURATE = 6
    const val CC_GBA_AGB_AGS = 7

    // Video filter / GLES3 display modes
    const val FILTER_NEAREST = 0
    const val FILTER_BILINEAR = 1
    const val FILTER_LCD_GRID = 2
    const val FILTER_RGB_SUBPIXEL = 3
    const val FILTER_XBRZ_LITE = 4
    const val FILTER_CRT_SCANLINES = 5
    const val FILTER_SHARP_BILINEAR = 6

    // Audio highpass & interp
    const val HIGHPASS_OFF = 0
    const val HIGHPASS_ACCURATE = 1
    const val HIGHPASS_REMOVE_DC = 2

    const val INTERP_NEAREST = 0
    const val INTERP_LINEAR = 1
    const val INTERP_HERMITE = 2

    // Hardware capability flags
    const val HW_RTC = 1 shl 0
    const val HW_RUMBLE = 1 shl 1
    const val HW_SOLAR = 1 shl 2
    const val HW_GYRO = 1 shl 3
    const val HW_TILT = 1 shl 4
    const val HW_SGB = 1 shl 5
    const val HW_CGB = 1 shl 6

    // Memory regions
    const val MEM_CART_ROM = 0
    const val MEM_CART_RAM = 1
    const val MEM_WRAM = 2
    const val MEM_IWRAM = 3
    const val MEM_EWRAM = 4
    const val MEM_VRAM = 5
    const val MEM_OAM = 6
    const val MEM_HRAM = 7
    const val MEM_IO = 8
    const val MEM_SYSTEM_BUS = 9

    fun parseRomInfo(raw: String?): RomInfo? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.split('\t')
        if (parts.size < 10) return null
        return RomInfo(
            platform = parts[0].toIntOrNull() ?: PLATFORM_NONE,
            title = parts[1],
            gameCode = parts[2],
            maker = parts[3],
            region = parts[4],
            crc32 = parts[5].toLongOrNull() ?: 0L,
            md5 = parts[6],
            romSize = parts[7].toLongOrNull() ?: 0L,
            hwFlags = parts[8].toIntOrNull() ?: 0,
            activeCore = parts[9],
        )
    }

    fun inspectRomFile(path: String): RomInfo? = parseRomInfo(nativeInspectRomFile(path))
    fun inspectRomBuffer(data: ByteArray, virtualName: String): RomInfo? = parseRomInfo(nativeInspectRomBuffer(data, virtualName))
    fun getRomInfo(handle: Long): RomInfo? = parseRomInfo(nativeGetRomInfo(handle))

    @JvmStatic external fun nativeVersion(): String
    @JvmStatic external fun nativeCreate(): Long
    @JvmStatic external fun nativeDestroy(handle: Long)
    @JvmStatic external fun nativeReset(handle: Long)
    @JvmStatic external fun nativeLoadRomFile(handle: Long, path: String): Int
    @JvmStatic external fun nativeLoadRomBuffer(handle: Long, data: ByteArray, virtualName: String): Int
    @JvmStatic external fun nativeUnloadRom(handle: Long)
    @JvmStatic external fun nativeInspectRomFile(path: String): String?
    @JvmStatic external fun nativeInspectRomBuffer(data: ByteArray, virtualName: String): String?
    @JvmStatic external fun nativeGetRomInfo(handle: Long): String?
    @JvmStatic external fun nativeApplyPatchFile(handle: Long, patchPath: String): Int

    @JvmStatic external fun nativeSetConfig(
        handle: Long,
        preferredCoreGb: Int,
        preferredCoreGbc: Int,
        preferredCoreGba: Int,
        gbModel: Int,
        sgbBorders: Boolean,
        colorCorrectionMode: Int,
        dmgPaletteIndex: Int,
        customDmgPalette: IntArray?,
        lcdGhosting: Int,
        videoFilter: Int,
        audioHighpassMode: Int,
        audioInterpMode: Int,
        channelVolumes: FloatArray?,
        masterVolume: Float,
        runAheadFrames: Int,
        rewindEnabled: Boolean,
        rewindBufferMb: Int,
        rewindInterval: Int,
        biosGbPath: String?,
        biosGbcPath: String?,
        biosGbaPath: String?,
        biosSgbPath: String?,
        skipBiosIntro: Boolean,
        dynamicRateControl: Boolean,
        drcMaxSkew: Float,
    ): Int

    @JvmStatic external fun nativeSetKeys(handle: Long, mask: Int)
    @JvmStatic external fun nativeRunFrame(handle: Long): Int
    @JvmStatic external fun nativeSetFastForward(handle: Long, mult: Float)
    @JvmStatic external fun nativeGetVideoInfo(handle: Long, outWH: IntArray): Float
    @JvmStatic external fun nativeCopyFrame(handle: Long, dstPixels: IntArray, dstStridePx: Int, pixelFormat: Int): Int
    @JvmStatic external fun nativeRewindStep(handle: Long): Int
    @JvmStatic external fun nativeRewindAvailableFrames(handle: Long): Int

    @JvmStatic external fun nativeSaveStateFile(handle: Long, path: String): Int
    @JvmStatic external fun nativeLoadStateFile(handle: Long, path: String): Int
    @JvmStatic external fun nativeSaveSramFile(handle: Long, path: String): Int
    @JvmStatic external fun nativeLoadSramFile(handle: Long, path: String): Int
    @JvmStatic external fun nativeIsSramDirty(handle: Long): Boolean

    @JvmStatic external fun nativeSetSolarLux(handle: Long, lux: Int)
    @JvmStatic external fun nativeSetTilt(handle: Long, x: Float, y: Float)
    @JvmStatic external fun nativeSetGyro(handle: Long, z: Float)
    @JvmStatic external fun nativeGetRumble(handle: Long): Float
    @JvmStatic external fun nativeSetRtc(handle: Long, useHost: Boolean, epoch: Long)

    @JvmStatic external fun nativeCheatsClear(handle: Long)
    @JvmStatic external fun nativeCheatAdd(handle: Long, name: String, code: String, enabled: Boolean): Int
    @JvmStatic external fun nativeReadMemory(handle: Long, region: Int, addr: Int, dst: ByteArray): Int
    @JvmStatic external fun nativeComputeRaHash(path: String, platform: Int): String?
    @JvmStatic external fun nativeComputeRaHashBuffer(data: ByteArray, platform: Int): String?

    @JvmStatic external fun nativeGetDmgPaletteCount(): Int
    @JvmStatic external fun nativeGetDmgPaletteName(index: Int): String?

    @JvmStatic external fun nativeAudioStart(handle: Long): Boolean
    @JvmStatic external fun nativeAudioStop()
    @JvmStatic external fun nativeAudioPause(paused: Boolean)

    @JvmStatic external fun nativeGlesInit(shaderSource: String): Boolean
    @JvmStatic external fun nativeGlesResize(width: Int, height: Int)
    @JvmStatic external fun nativeGlesSetRotation(rotDeg: Int)
    @JvmStatic external fun nativeGlesRender(
        handle: Long,
        vpX: Int,
        vpY: Int,
        vpW: Int,
        vpH: Int,
        displayMode: Int,
        ghosting: Float,
        ccStrength: Float,
        isGrayscale: Boolean,
        integerScaling: Boolean,
    )
    @JvmStatic external fun nativeGlesDestroy()

    // v1.1.0 Upscaling, Performance, RAM Cheat Finder, RetroArch librashader, Vulkan
    const val UPSCALE_NONE = 0
    const val UPSCALE_XBRZ = 1
    const val UPSCALE_SCALE_EPX = 2
    const val UPSCALE_MMPX = 3
    const val UPSCALE_LCD_HD = 4
    const val UPSCALE_CRT_HD = 5

    const val SEARCH_EQ = 0
    const val SEARCH_NEQ = 1
    const val SEARCH_GT = 2
    const val SEARCH_LT = 3
    const val SEARCH_CHANGED = 4
    const val SEARCH_UNCHANGED = 5
    const val SEARCH_INCREASED = 6
    const val SEARCH_DECREASED = 7

    @JvmStatic external fun nativeSetUpscale(handle: Long, scale: Int, mode: Int)
    @JvmStatic external fun nativeSetPerformance(handle: Long, cpuOverclockPct: Int, frameSkip: Int)
    @JvmStatic external fun nativeWriteMemory(handle: Long, region: Int, addr: Int, value: Int, sizeBytes: Int): Int
    @JvmStatic external fun nativeMemSearchReset(handle: Long, region: Int, valueSize: Int): Int
    @JvmStatic external fun nativeMemSearchFilter(handle: Long, cmpOp: Int, targetValue: Int): Int
    @JvmStatic external fun nativeMemSearchGetResults(handle: Long, outAddrs: IntArray, outVals: IntArray): Int

    @JvmStatic external fun nativeIsLibrashaderAvailable(): Boolean
    @JvmStatic external fun nativeGetLibrashaderStatus(): String
    @JvmStatic external fun nativeSetRetroArchShader(presetPath: String)

    @JvmStatic external fun nativeIsVulkanSupported(): Boolean
    @JvmStatic external fun nativeGetVulkanDeviceName(): String
    @JvmStatic external fun nativeVkInit(surface: android.view.Surface, customDriverPath: String): Boolean
    @JvmStatic external fun nativeVkResize(width: Int, height: Int)
    @JvmStatic external fun nativeVkRender(handle: Long, vpX: Int, vpY: Int, vpW: Int, vpH: Int, linearFilter: Boolean, rotDeg: Int)
    @JvmStatic external fun nativeVkDestroy()

    // v1.2.0 Device Optimization & Full Multiplayer / Link Cable / Wireless / JoyBus / GB Printer
    const val OPT_LITE = 0
    const val OPT_BALANCED = 1
    const val OPT_PERFORMANCE = 2
    const val OPT_HIGH_ACCURACY = 3

    const val LINK_NONE = 0
    const val LINK_LAN_HOST = 1
    const val LINK_LAN_CLIENT = 2
    const val LINK_WIRELESS_HOST = 3
    const val LINK_WIRELESS_CLIENT = 4
    const val LINK_IR_HOST = 5
    const val LINK_IR_CLIENT = 6
    const val LINK_DOLPHIN_JOYBUS = 7
    const val LINK_GB_PRINTER = 8
    const val LINK_LOCAL_LOOPBACK = 9

    @JvmStatic external fun nativeSetOptimizationProfile(handle: Long, profile: Int)
    @JvmStatic external fun nativeLinkStart(handle: Long, mode: Int, hostIp: String, port: Int, clockPort: Int, roomName: String): Int
    @JvmStatic external fun nativeLinkStop(handle: Long)
    @JvmStatic external fun nativeLinkGetStatus(handle: Long): String
    @JvmStatic external fun nativeLanDiscover(timeoutMs: Int): String
    @JvmStatic external fun nativePollPrinterImage(handle: Long, outWH: IntArray): IntArray?
}
