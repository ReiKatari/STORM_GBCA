package com.stormgbca.emulator.ui.emulator.skin

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import com.stormgbca.emulator.model.ConsoleSystem
import com.stormgbca.emulator.model.ScreenRectConfig

enum class GbcaSkinId(
    val key: String,
    val system: ConsoleSystem?,
    val labelRu: String,
    val labelEn: String,
) {
    NONE("none", null, "Без корпуса (Полный экран)", "No Skin (Fullscreen)"),

    // ==================== GAME BOY (12 authentic models) ====================
    DMG_CLASSIC("dmg_classic", ConsoleSystem.GB, "DMG-01 Классический серый", "DMG-01 Classic Gray"),
    DMG_BLACK("dmg_black", ConsoleSystem.GB, "DMG Play It Loud Черный", "DMG Play It Loud Black"),
    DMG_RED("dmg_red", ConsoleSystem.GB, "DMG Play It Loud Красный", "DMG Play It Loud Red"),
    DMG_YELLOW("dmg_yellow", ConsoleSystem.GB, "DMG Play It Loud Желтый", "DMG Play It Loud Yellow"),
    DMG_GREEN("dmg_green", ConsoleSystem.GB, "DMG Play It Loud Зеленый", "DMG Play It Loud Green"),
    DMG_CLEAR("dmg_clear", ConsoleSystem.GB, "DMG Прозрачный (PCB)", "DMG Clear Skeleton"),
    GBP_SILVER("gbp_silver", ConsoleSystem.GB, "Game Boy Pocket Серебро", "Game Boy Pocket Silver"),
    GBP_ICE_BLUE("gbp_ice_blue", ConsoleSystem.GB, "Game Boy Pocket Ледяной", "Pocket Ice Blue"),
    GBP_EXTREME_GREEN("gbp_extreme_green", ConsoleSystem.GB, "Pocket Неоновый зеленый", "Pocket Extreme Green"),
    GBL_GOLD("gbl_gold", ConsoleSystem.GB, "Game Boy Light Золото", "Game Boy Light Gold"),
    SGB_FRAME("sgb_frame", ConsoleSystem.GB, "Super Game Boy (SNES)", "Super Game Boy Frame"),
    GB_STORM_AURORA("gb_storm_aurora", ConsoleSystem.GB, "STORM Aurora GB", "STORM Aurora GB"),

    // ==================== GAME BOY COLOR (12 authentic models) ====================
    GBC_ATOMIC_PURPLE("gbc_atomic_purple", ConsoleSystem.GBC, "Атомный фиолет (PCB)", "Atomic Purple (Translucent)"),
    GBC_BERRY("gbc_berry", ConsoleSystem.GBC, "Ягодный маджента", "Berry Magenta"),
    GBC_TEAL("gbc_teal", ConsoleSystem.GBC, "Бирюзовый Teal", "Teal"),
    GBC_KIWI("gbc_kiwi", ConsoleSystem.GBC, "Киви Лайм", "Kiwi Lime"),
    GBC_DANDELION("gbc_dandelion", ConsoleSystem.GBC, "Одуванчик Желтый", "Dandelion Yellow"),
    GBC_GRAPE("gbc_grape", ConsoleSystem.GBC, "Виноградный фиолет", "Grape Purple"),
    GBC_MIDNIGHT_BLUE("gbc_midnight_blue", ConsoleSystem.GBC, "Полуночный синий (PCB)", "Midnight Blue (Translucent)"),
    GBC_CLEAR_ICE("gbc_clear_ice", ConsoleSystem.GBC, "Кристальный лед (PCB)", "Clear Ice (Translucent)"),
    GBC_CLEAR_GREEN("gbc_clear_green", ConsoleSystem.GBC, "Изумрудный прозрачный", "Clear Green Japan"),
    GBC_PIKACHU("gbc_pikachu", ConsoleSystem.GBC, "Pokemon Пикачу и Пичу", "Pokemon Pikachu Edition"),
    GBC_GOLD_SILVER("gbc_gold_silver", ConsoleSystem.GBC, "Pokemon Золото / Серебро", "Pokemon Gold & Silver"),
    GBC_STORM_AURORA("gbc_storm_aurora", ConsoleSystem.GBC, "STORM Aurora GBC", "STORM Aurora GBC"),

    // ==================== GAME BOY ADVANCE (16 authentic models: AGB + SP + Micro) ====================
    GBA_INDIGO("gba_indigo", ConsoleSystem.GBA, "AGB-001 Индиго", "AGB-001 Indigo"),
    GBA_GLACIER("gba_glacier", ConsoleSystem.GBA, "AGB-001 Ледник (PCB)", "AGB-001 Glacier"),
    GBA_ARCTIC_WHITE("gba_arctic_white", ConsoleSystem.GBA, "AGB-001 Арктический белый", "AGB-001 Arctic White"),
    GBA_SPICE_ORANGE("gba_spice_orange", ConsoleSystem.GBA, "AGB-001 Пряный оранжевый", "AGB-001 Spice Orange"),
    GBA_FUCHSIA("gba_fuchsia", ConsoleSystem.GBA, "AGB-001 Фуксия (PCB)", "AGB-001 Fuchsia"),
    GBA_MIDNIGHT_BLUE("gba_midnight_blue", ConsoleSystem.GBA, "AGB-001 Полуночный синий", "AGB-001 Midnight Blue"),
    GBASP_COBALT("gbasp_cobalt", ConsoleSystem.GBA, "GBA SP Кобальтовый синий", "GBA SP Cobalt Blue"),
    GBASP_ONYX("gbasp_onyx", ConsoleSystem.GBA, "GBA SP Черный оникс", "GBA SP Onyx Black"),
    GBASP_PLATINUM("gbasp_platinum", ConsoleSystem.GBA, "GBA SP Платина", "GBA SP Platinum Silver"),
    GBASP_FLAME_RED("gbasp_flame_red", ConsoleSystem.GBA, "GBA SP Огненный красный", "GBA SP Flame Red"),
    GBASP_PEARL_BLUE("gbasp_pearl_blue", ConsoleSystem.GBA, "GBA SP Жемчужный AGS-101", "GBA SP Pearl Blue AGS-101"),
    GBASP_NES_CLASSIC("gbasp_nes_classic", ConsoleSystem.GBA, "GBA SP NES Классик", "GBA SP NES Classic"),
    GBASP_ZELDA_GOLD("gbasp_zelda_gold", ConsoleSystem.GBA, "GBA SP Zelda Triforce Gold", "GBA SP Zelda Triforce Gold"),
    GBM_FAMICOM("gbm_famicom", ConsoleSystem.GBA, "GB Micro Famicom 20th", "GB Micro Famicom 20th"),
    GBM_SILVER("gbm_silver", ConsoleSystem.GBA, "GB Micro Классик Серебро", "GB Micro Classic Silver"),
    GBA_STORM_AURORA("gba_storm_aurora", ConsoleSystem.GBA, "STORM Aurora GBA", "STORM Aurora GBA");

    fun localizedLabel(context: android.content.Context): String = when {
        this == NONE -> context.getString(com.stormgbca.emulator.R.string.skin_none)
        java.util.Locale.getDefault().language.equals("ru", ignoreCase = true) -> labelRu
        else -> labelEn
    }

    companion object {
        fun forSystem(system: ConsoleSystem): List<GbcaSkinId> =
            entries.filter { it.system == system } + listOf(NONE)

        fun fromKey(key: String?, system: ConsoleSystem): GbcaSkinId {
            val found = entries.firstOrNull { it.key.equals(key, ignoreCase = true) }
            if (found == NONE) return NONE
            if (found != null && found.system == system) return found
            return when (system) {
                ConsoleSystem.GB -> DMG_CLASSIC
                ConsoleSystem.GBC -> GBC_ATOMIC_PURPLE
                ConsoleSystem.GBA -> GBA_INDIGO
            }
        }
    }
}

private enum class HardwareShellForm {
    DMG,
    POCKET,
    LIGHT,
    SGB,
    GBC,
    AGB,
    GBA_SP,
    GB_MICRO,
    AURORA,
}

private data class SkinColors(
    val bodyTop: Color,
    val bodyBottom: Color,
    val bezelOuter: Color,
    val bezelInner: Color,
    val accent: Color,
    val secondaryTrim: Color,
    val brandText: Color,
    val powerLed: Color,
    val form: HardwareShellForm,
    val translucent: Boolean = false,
    val metallic: Boolean = false,
    val title: String,
    val isDark: Boolean = true,
)

private fun resolvePalette(id: GbcaSkinId): SkinColors = when (id) {
    // GB
    GbcaSkinId.DMG_CLASSIC -> SkinColors(Color(0xFFDAD6CA), Color(0xFFB5B0A1), Color(0xFF6B6F7B), Color(0xFF4C505C), Color(0xFF8C1D4F), Color(0xFF23327A), Color(0xFFDCE2EC), Color(0xFFFF2442), HardwareShellForm.DMG, false, false, "GAME BOY", false)
    GbcaSkinId.DMG_BLACK -> SkinColors(Color(0xFF23262F), Color(0xFF101218), Color(0xFF3E4352), Color(0xFF1C1F27), Color(0xFFE11D48), Color(0xFF2563EB), Color(0xFFE2E8F0), Color(0xFFFF2442), HardwareShellForm.DMG, false, false, "GAME BOY", true)
    GbcaSkinId.DMG_RED -> SkinColors(Color(0xFFDC2626), Color(0xFF991B1B), Color(0xFF3D3F4A), Color(0xFF23252E), Color(0xFFFCA5A5), Color(0xFF1E3A8A), Color(0xFFF8FAFC), Color(0xFFFF2442), HardwareShellForm.DMG, false, false, "GAME BOY", true)
    GbcaSkinId.DMG_YELLOW -> SkinColors(Color(0xFFFACC15), Color(0xFFCA8A04), Color(0xFF3D3F4A), Color(0xFF22242C), Color(0xFF991B1B), Color(0xFF1E3A8A), Color(0xFFF8FAFC), Color(0xFFFF2442), HardwareShellForm.DMG, false, false, "GAME BOY", false)
    GbcaSkinId.DMG_GREEN -> SkinColors(Color(0xFF16A34A), Color(0xFF14532D), Color(0xFF3B3E49), Color(0xFF21242D), Color(0xFF86EFAC), Color(0xFF1E3A8A), Color(0xFFF8FAFC), Color(0xFFFF2442), HardwareShellForm.DMG, false, false, "GAME BOY", true)
    GbcaSkinId.DMG_CLEAR -> SkinColors(Color(0xFFC5D0DE), Color(0xFF8D9CB0), Color(0xFF5A6577), Color(0xFF252D3B), Color(0xFF8C1D4F), Color(0xFF1E40AF), Color(0xFFE2E8F0), Color(0xFFFF2442), HardwareShellForm.DMG, true, false, "GAME BOY", false)
    GbcaSkinId.GBP_SILVER -> SkinColors(Color(0xFFE5E9F0), Color(0xFF9EA8B8), Color(0xFFCBD5E1), Color(0xFF475569), Color(0xFFF8FAFC), Color(0xFF334155), Color(0xFFF8FAFC), Color(0xFFEF4444), HardwareShellForm.POCKET, false, true, "GAME BOY pocket", false)
    GbcaSkinId.GBP_ICE_BLUE -> SkinColors(Color(0xFF67D4F5), Color(0xFF0284C7), Color(0xFF334155), Color(0xFF141B29), Color(0xFF7DD3FC), Color(0xFF0369A1), Color(0xFFF0F9FF), Color(0xFFEF4444), HardwareShellForm.POCKET, true, false, "GAME BOY pocket", true)
    GbcaSkinId.GBP_EXTREME_GREEN -> SkinColors(Color(0xFF4ADE80), Color(0xFF15803D), Color(0xFF27342B), Color(0xFF111A14), Color(0xFF86EFAC), Color(0xFF166534), Color(0xFFECFDF5), Color(0xFFEF4444), HardwareShellForm.POCKET, true, false, "GAME BOY pocket", true)
    GbcaSkinId.GBL_GOLD -> SkinColors(Color(0xFFF6D365), Color(0xFFB48119), Color(0xFFC4A045), Color(0xFF231D0F), Color(0xFF2DD4BF), Color(0xFF0D9488), Color(0xFFCCFBF1), Color(0xFF2DD4BF), HardwareShellForm.LIGHT, false, true, "GAME BOY LIGHT", false)
    GbcaSkinId.SGB_FRAME -> SkinColors(Color(0xFFC4C2CC), Color(0xFF8B8899), Color(0xFF6D28D9), Color(0xFF231936), Color(0xFFA78BFA), Color(0xFF4C1D95), Color(0xFFEDE9FE), Color(0xFF22C55E), HardwareShellForm.SGB, false, false, "SUPER GAME BOY", false)
    GbcaSkinId.GB_STORM_AURORA -> SkinColors(Color(0xFF0E1526), Color(0xFF050810), Color(0xFF00E5FF), Color(0xFF070A12), Color(0xFF00E5FF), Color(0xFF22C55E), Color(0xFFE0F2FE), Color(0xFF00E5FF), HardwareShellForm.AURORA, false, false, "STORM GB AURORA", true)

    // GBC
    GbcaSkinId.GBC_ATOMIC_PURPLE -> SkinColors(Color(0xFF8E63F8), Color(0xFF4A1D96), Color(0xFF282438), Color(0xFF111118), Color(0xFFC4B5FD), Color(0xFF3B0764), Color(0xFFF1F5F9), Color(0xFFFF2442), HardwareShellForm.GBC, true, false, "GAME BOY", true)
    GbcaSkinId.GBC_BERRY -> SkinColors(Color(0xFFE11D6C), Color(0xFF9D174D), Color(0xFF2B222C), Color(0xFF111116), Color(0xFFF472B6), Color(0xFF831843), Color(0xFFF8FAFC), Color(0xFFFF2442), HardwareShellForm.GBC, false, false, "GAME BOY", true)
    GbcaSkinId.GBC_TEAL -> SkinColors(Color(0xFF0EA5A4), Color(0xFF0F766E), Color(0xFF1F2B2E), Color(0xFF101317), Color(0xFF5EEAD4), Color(0xFF115E59), Color(0xFFF8FAFC), Color(0xFFFF2442), HardwareShellForm.GBC, false, false, "GAME BOY", true)
    GbcaSkinId.GBC_KIWI -> SkinColors(Color(0xFF84CC16), Color(0xFF4D7C0F), Color(0xFF23291E), Color(0xFF111312), Color(0xFFBEF264), Color(0xFF3F6212), Color(0xFFF8FAFC), Color(0xFFFF2442), HardwareShellForm.GBC, false, false, "GAME BOY", false)
    GbcaSkinId.GBC_DANDELION -> SkinColors(Color(0xFFFACC15), Color(0xFFD97706), Color(0xFF2A2618), Color(0xFF111114), Color(0xFFFEF08A), Color(0xFFB45309), Color(0xFFF8FAFC), Color(0xFFFF2442), HardwareShellForm.GBC, false, false, "GAME BOY", false)
    GbcaSkinId.GBC_GRAPE -> SkinColors(Color(0xFF6D28D9), Color(0xFF3B0764), Color(0xFF241D33), Color(0xFF111118), Color(0xFFA78BFA), Color(0xFF2E1065), Color(0xFFF8FAFC), Color(0xFFFF2442), HardwareShellForm.GBC, false, false, "GAME BOY", true)
    GbcaSkinId.GBC_MIDNIGHT_BLUE -> SkinColors(Color(0xFF1D4ED8), Color(0xFF172554), Color(0xFF1E263D), Color(0xFF0E111B), Color(0xFF60A5FA), Color(0xFF1E3A8A), Color(0xFFEFF6FF), Color(0xFFFF2442), HardwareShellForm.GBC, true, false, "GAME BOY", true)
    GbcaSkinId.GBC_CLEAR_ICE -> SkinColors(Color(0xFFD8E4F0), Color(0xFF9AB0C7), Color(0xFF3B4354), Color(0xFF14171F), Color(0xFFBAE6FD), Color(0xFF64748B), Color(0xFFF8FAFC), Color(0xFFFF2442), HardwareShellForm.GBC, true, false, "GAME BOY", false)
    GbcaSkinId.GBC_CLEAR_GREEN -> SkinColors(Color(0xFF22C55E), Color(0xFF14532D), Color(0xFF1B2B22), Color(0xFF0F1412), Color(0xFF86EFAC), Color(0xFF166534), Color(0xFFF0FDF4), Color(0xFFFF2442), HardwareShellForm.GBC, true, false, "GAME BOY", true)
    GbcaSkinId.GBC_PIKACHU -> SkinColors(Color(0xFFFDE047), Color(0xFFEAB308), Color(0xFF2563EB), Color(0xFF141824), Color(0xFFEF4444), Color(0xFF1D4ED8), Color(0xFFFEF08A), Color(0xFFFF2442), HardwareShellForm.GBC, false, false, "GAME BOY", false)
    GbcaSkinId.GBC_GOLD_SILVER -> SkinColors(Color(0xFFE8D5A7), Color(0xFFA69062), Color(0xFF524B3B), Color(0xFF171512), Color(0xFFFDE68A), Color(0xFF78716C), Color(0xFFFFFBEB), Color(0xFFFF2442), HardwareShellForm.GBC, false, true, "GAME BOY", false)
    GbcaSkinId.GBC_STORM_AURORA -> SkinColors(Color(0xFF130B29), Color(0xFF070414), Color(0xFFA855F7), Color(0xFF090612), Color(0xFFA855F7), Color(0xFF00E5FF), Color(0xFFF3E8FF), Color(0xFFA855F7), HardwareShellForm.AURORA, false, false, "STORM GBC AURORA", true)

    // GBA AGB-001
    GbcaSkinId.GBA_INDIGO -> SkinColors(Color(0xFF5A52C8), Color(0xFF342E8A), Color(0xFFCBD5E1), Color(0xFF1A1D26), Color(0xFF94A3B8), Color(0xFFCBD5E1), Color(0xFFE2E8F0), Color(0xFF22C55E), HardwareShellForm.AGB, false, false, "GAME BOY ADVANCE", true)
    GbcaSkinId.GBA_GLACIER -> SkinColors(Color(0xFFB5D2E3), Color(0xFF749AB2), Color(0xFFE2E8F0), Color(0xFF1E2430), Color(0xFFE0F2FE), Color(0xFFCBD5E1), Color(0xFFF8FAFC), Color(0xFF22C55E), HardwareShellForm.AGB, true, false, "GAME BOY ADVANCE", false)
    GbcaSkinId.GBA_ARCTIC_WHITE -> SkinColors(Color(0xFFF4F7FA), Color(0xFFD1DAE5), Color(0xFFCBD5E1), Color(0xFF242833), Color(0xFF94A3B8), Color(0xFFE2E8F0), Color(0xFFF8FAFC), Color(0xFF22C55E), HardwareShellForm.AGB, false, false, "GAME BOY ADVANCE", false)
    GbcaSkinId.GBA_SPICE_ORANGE -> SkinColors(Color(0xFFFB7A1A), Color(0xFFC2410C), Color(0xFFE5E7EB), Color(0xFF1C1D24), Color(0xFFFED7AA), Color(0xFFCBD5E1), Color(0xFFFFF7ED), Color(0xFF22C55E), HardwareShellForm.AGB, false, false, "GAME BOY ADVANCE", false)
    GbcaSkinId.GBA_FUCHSIA -> SkinColors(Color(0xFFEC4899), Color(0xFF9D174D), Color(0xFFE2E8F0), Color(0xFF1F1926), Color(0xFFFBCFE8), Color(0xFFE2E8F0), Color(0xFFFDF2F8), Color(0xFF22C55E), HardwareShellForm.AGB, true, false, "GAME BOY ADVANCE", true)
    GbcaSkinId.GBA_MIDNIGHT_BLUE -> SkinColors(Color(0xFF1E40AF), Color(0xFF172554), Color(0xFF94A3B8), Color(0xFF101524), Color(0xFF60A5FA), Color(0xFF64748B), Color(0xFFDBEAFE), Color(0xFF22C55E), HardwareShellForm.AGB, true, false, "GAME BOY ADVANCE", true)

    // GBA SP (AGS-001 / AGS-101)
    GbcaSkinId.GBASP_COBALT -> SkinColors(Color(0xFF2563EB), Color(0xFF162C75), Color(0xFF1E293B), Color(0xFF111520), Color(0xFF60A5FA), Color(0xFF0F172A), Color(0xFFDBEAFE), Color(0xFF22C55E), HardwareShellForm.GBA_SP, false, true, "GAME BOY ADVANCE SP", true)
    GbcaSkinId.GBASP_ONYX -> SkinColors(Color(0xFF2A2B31), Color(0xFF111216), Color(0xFF3F3F46), Color(0xFF0C0D10), Color(0xFFA1A1AA), Color(0xFF18181B), Color(0xFFF4F4F5), Color(0xFF22C55E), HardwareShellForm.GBA_SP, false, true, "GAME BOY ADVANCE SP", true)
    GbcaSkinId.GBASP_PLATINUM -> SkinColors(Color(0xFFE4E7EC), Color(0xFFA5AEBC), Color(0xFF64748B), Color(0xFF1C2029), Color(0xFFF8FAFC), Color(0xFF334155), Color(0xFFF8FAFC), Color(0xFF22C55E), HardwareShellForm.GBA_SP, false, true, "GAME BOY ADVANCE SP", false)
    GbcaSkinId.GBASP_FLAME_RED -> SkinColors(Color(0xFFDC2626), Color(0xFF7F1D1D), Color(0xFF27272A), Color(0xFF131114), Color(0xFFFCA5A5), Color(0xFF450A0A), Color(0xFFFEF2F2), Color(0xFF22C55E), HardwareShellForm.GBA_SP, false, true, "GAME BOY ADVANCE SP", true)
    GbcaSkinId.GBASP_PEARL_BLUE -> SkinColors(Color(0xFF93C5FD), Color(0xFF3B82F6), Color(0xFF334155), Color(0xFF141B29), Color(0xFFDBEAFE), Color(0xFF1D4ED8), Color(0xFFEFF6FF), Color(0xFF22C55E), HardwareShellForm.GBA_SP, false, true, "GAME BOY ADVANCE SP", false)
    GbcaSkinId.GBASP_NES_CLASSIC -> SkinColors(Color(0xFFD5D4CE), Color(0xFFA7A6A0), Color(0xFF262626), Color(0xFF141414), Color(0xFFDC2626), Color(0xFF1F2937), Color(0xFFDC2626), Color(0xFF22C55E), HardwareShellForm.GBA_SP, false, false, "GAME BOY ADVANCE SP", false)
    GbcaSkinId.GBASP_ZELDA_GOLD -> SkinColors(Color(0xFFECC94B), Color(0xFF9A6B12), Color(0xFF451A03), Color(0xFF14110B), Color(0xFFFEF08A), Color(0xFF78350F), Color(0xFFFEF08A), Color(0xFF22C55E), HardwareShellForm.GBA_SP, false, true, "GAME BOY ADVANCE SP", false)

    // Game Boy Micro (OXY-001)
    GbcaSkinId.GBM_FAMICOM -> SkinColors(Color(0xFF991B1B), Color(0xFF560A0A), Color(0xFFF5D061), Color(0xFF1C1008), Color(0xFFFDE047), Color(0xFFD4AF37), Color(0xFFFEF08A), Color(0xFF38BDF8), HardwareShellForm.GB_MICRO, false, true, "GAME BOY micro", true)
    GbcaSkinId.GBM_SILVER -> SkinColors(Color(0xFFD4D8DF), Color(0xFF8C94A2), Color(0xFF27272A), Color(0xFF0D0E12), Color(0xFFF8FAFC), Color(0xFF18181B), Color(0xFFF8FAFC), Color(0xFF38BDF8), HardwareShellForm.GB_MICRO, false, true, "GAME BOY micro", false)

    // STORM Aurora GBA
    GbcaSkinId.GBA_STORM_AURORA, GbcaSkinId.NONE -> SkinColors(Color(0xFF0D1429), Color(0xFF050814), Color(0xFF00E5FF), Color(0xFF070B16), Color(0xFF00E5FF), Color(0xFF7C3AED), Color(0xFFE0F2FE), Color(0xFF00E5FF), HardwareShellForm.AURORA, false, false, "STORM GBA AURORA", true)
}

/**
 * Computes exact game viewport rectangle inside container (containerW x containerH),
 * supporting all 5 screen modes:
 * 0 = Inside 3D Skin, 1 = Max Aspect Fit, 2 = Fullscreen 100% Fill, 3 = Integer Pixel-Perfect, 4 = Free Custom.
 */
fun computeGameViewportRect(
    containerW: Int,
    containerH: Int,
    @Suppress("UNUSED_PARAMETER") system: ConsoleSystem,
    skinId: GbcaSkinId,
    aspectMode: Int,
    nativeW: Int,
    nativeH: Int,
    customScreen: ScreenRectConfig? = null,
): Rect {
    val rotated = customScreen != null && (customScreen.rotationDeg == 90 || customScreen.rotationDeg == 270)
    val srcW = (if (rotated) nativeH else nativeW).coerceAtLeast(144).toFloat()
    val srcH = (if (rotated) nativeW else nativeH).coerceAtLeast(144).toFloat()
    val srcAspect = srcW / srcH
    val cw = containerW.toFloat().coerceAtLeast(1f)
    val ch = containerH.toFloat().coerceAtLeast(1f)
    val isLandscape = containerW > containerH

    // 1. Free Custom screen geometry
    if (customScreen != null && (customScreen.useCustom || customScreen.screenPreset == 4)) {
        val boxW = cw * customScreen.widthFraction.coerceIn(0.20f, 1.0f)
        val boxH = ch * customScreen.heightFraction.coerceIn(0.20f, 1.0f)
        var targetW: Float
        var targetH: Float
        when (customScreen.aspectMode) {
            2, 3 -> {
                targetW = boxW
                targetH = boxH
            }
            1 -> {
                val intScale = minOf((boxW / srcW).toInt(), (boxH / srcH).toInt()).coerceAtLeast(1)
                targetW = srcW * intScale
                targetH = srcH * intScale
            }
            else -> {
                if (boxW / boxH > srcAspect) {
                    targetH = boxH
                    targetW = targetH * srcAspect
                } else {
                    targetW = boxW
                    targetH = targetW / srcAspect
                }
            }
        }
        val cx = cw * customScreen.centerX.coerceIn(0.05f, 0.95f)
        val cy = ch * customScreen.centerY.coerceIn(0.05f, 0.95f)
        val left = (cx - targetW / 2f).coerceIn(0f, (cw - targetW).coerceAtLeast(0f))
        val top = (cy - targetH / 2f).coerceIn(0f, (ch - targetH).coerceAtLeast(0f))
        return Rect(left, top, left + targetW, top + targetH)
    }

    val preset = customScreen?.screenPreset ?: 0
    val effAspect = customScreen?.aspectMode ?: aspectMode

    // 2. Fullscreen Edge-to-Edge 100% Stretch
    if (preset == 2 || (skinId == GbcaSkinId.NONE && effAspect == 2)) {
        return Rect(0f, 0f, cw, ch)
    }

    // 3. Max Aspect Fit (fills full width/height edge-to-edge preserving original ratio!)
    if (preset == 1 || skinId == GbcaSkinId.NONE) {
        var targetW: Float
        var targetH: Float
        if (cw / ch > srcAspect) {
            targetH = ch
            targetW = targetH * srcAspect
        } else {
            targetW = cw
            targetH = targetW / srcAspect
        }
        if (effAspect == 1) {
            val intScale = minOf((targetW / srcW).toInt(), (targetH / srcH).toInt()).coerceAtLeast(1)
            targetW = srcW * intScale
            targetH = srcH * intScale
        }
        val left = (cw - targetW) / 2f
        val top = if (isLandscape || preset == 1) {
            if (isLandscape) (ch - targetH) / 2f else (ch * 0.06f).coerceAtMost((ch - targetH) / 2f)
        } else {
            (ch - targetH) / 2f
        }
        return Rect(left, top, left + targetW, top + targetH)
    }

    // 4. Integer Pixel-Perfect
    if (preset == 3) {
        val intScale = minOf((cw / srcW).toInt(), ((if (isLandscape) ch else ch * 0.62f) / srcH).toInt()).coerceAtLeast(1)
        val targetW = srcW * intScale
        val targetH = srcH * intScale
        val left = (cw - targetW) / 2f
        val top = if (isLandscape) (ch - targetH) / 2f else (ch * 0.08f).coerceAtLeast(16f)
        return Rect(left, top, left + targetW, top + targetH)
    }

    // 5. Default (0): Inside 3D Console Shell Bezel
    val availW = cw * (if (isLandscape) 0.58f else 0.78f)
    val availH = ch * (if (isLandscape) 0.74f else 0.44f)
    var targetW: Float
    var targetH: Float
    if (availW / availH > srcAspect) {
        targetH = availH
        targetW = targetH * srcAspect
    } else {
        targetW = availW
        targetH = targetW / srcAspect
    }
    if (effAspect == 1) {
        val intScale = minOf((targetW / srcW).toInt(), (targetH / srcH).toInt()).coerceAtLeast(1)
        targetW = srcW * intScale
        targetH = srcH * intScale
    }
    val left = (cw - targetW) / 2f
    val top = if (isLandscape) (ch - targetH) / 2f else (ch * 0.085f).coerceAtLeast(22f)
    return Rect(left, top, left + targetW, top + targetH)
}

@Composable
fun ConsoleSkinOverlayView(
    skinId: GbcaSkinId,
    screenRect: Rect,
    dimmingPercent: Int = 100,
    modifier: Modifier = Modifier,
) {
    if (skinId == GbcaSkinId.NONE) return
    val palette = remember(skinId) { resolvePalette(skinId) }
    val dimFactor = (dimmingPercent.coerceIn(30, 100) / 100f)
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val wInt = constraints.maxWidth
        val hInt = constraints.maxHeight
        if (wInt <= 1 || hInt <= 1) return@BoxWithConstraints

        val cachedBitmap = remember(wInt, hInt, skinId, screenRect, dimmingPercent) {
            val imageBitmap = ImageBitmap(wInt, hInt, ImageBitmapConfig.Argb8888)
            val offscreenCanvas = ComposeCanvas(imageBitmap)
            val drawScope = CanvasDrawScope()
            drawScope.draw(
                density = density,
                layoutDirection = layoutDirection,
                canvas = offscreenCanvas,
                size = Size(wInt.toFloat(), hInt.toFloat()),
            ) {
                val w = size.width
                val h = size.height
                val holePath = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = screenRect,
                            cornerRadius = CornerRadius(6f, 6f),
                        )
                    )
                }

                clipPath(holePath, clipOp = ClipOp.Difference) {
                    drawAuthenticHardwareChassis(w, h, screenRect, skinId, palette, dimFactor)
                }

                // Crisp recessed inner LCD glass rim & inner shadow
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.85f),
                    topLeft = Offset(screenRect.left - 2.5f, screenRect.top - 2.5f),
                    size = Size(screenRect.width + 5f, screenRect.height + 5f),
                    cornerRadius = CornerRadius(8f, 8f),
                    style = Stroke(width = 3.5f),
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.14f * dimFactor),
                    topLeft = Offset(screenRect.left - 4f, screenRect.top - 4f),
                    size = Size(screenRect.width + 8f, screenRect.height + 8f),
                    cornerRadius = CornerRadius(10f, 10f),
                    style = Stroke(width = 1.2f),
                )
            }
            imageBitmap.prepareToDraw()
            imageBitmap
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawImage(cachedBitmap)
        }
    }
}

private fun DrawScope.drawAuthenticHardwareChassis(
    w: Float,
    h: Float,
    screenRect: Rect,
    skinId: GbcaSkinId,
    p: SkinColors,
    dim: Float,
) {
    val isLandscape = w > h
    val topCol = p.bodyTop.scaleRgb(dim)
    val botCol = p.bodyBottom.scaleRgb(dim)

    // 1. Base 3D Molded Shell Surface (curved specular plastic or brushed anodized aluminum)
    drawRect(
        brush = Brush.verticalGradient(listOf(topCol, botCol)),
        size = Size(w, h),
    )
    // Specular 3D studio top-left reflection + deep lower ambient occlusion
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = (if (p.metallic) 0.28f else 0.14f) * dim),
                Color.Transparent,
                Color.Black.copy(alpha = 0.24f),
            ),
            start = Offset(0f, 0f),
            end = Offset(w, h),
        ),
        size = Size(w, h),
    )

    // Brushed metallic horizontal micro-sheen for Pocket Silver / Light Gold / GBA SP / GB Micro
    if (p.metallic) {
        val bandCount = 26
        for (i in 0 until bandCount) {
            val y = (h / bandCount) * i
            val alpha = if (i % 3 == 0) 0.055f else 0.025f
            drawLine(
                color = Color.White.copy(alpha = alpha * dim),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.4f,
            )
        }
    }

    // 2. Authentic Translucent Shell Internal Electronics (PCB, CPU, RAM, Traces, Capacitors!)
    if (p.translucent) {
        drawTranslucentPcbInternals(w, h, screenRect, p, dim)
    }

    // 3. Outer 3D Molded Edge Chamfer & Contour Grooves
    val edgeInset = (minOf(w, h) * 0.018f).coerceIn(4f, 12f)
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.32f * dim), Color.Black.copy(alpha = 0.36f)),
        ),
        topLeft = Offset(edgeInset, edgeInset),
        size = Size(w - edgeInset * 2f, h - edgeInset * 2f),
        cornerRadius = CornerRadius(24f, 24f),
        style = Stroke(width = 2.4f),
    )

    // 4. Recessed Screen Lens Frame Coordinates
    val padX = (screenRect.width * 0.095f).coerceIn(14f, 44f)
    val padTop = (screenRect.height * 0.11f).coerceIn(14f, 36f)
    val padBot = (screenRect.height * 0.16f).coerceIn(18f, 50f)
    val bezelRect = Rect(
        left = (screenRect.left - padX).coerceAtLeast(6f),
        top = (screenRect.top - padTop).coerceAtLeast(6f),
        right = (screenRect.right + padX).coerceAtMost(w - 6f),
        bottom = (screenRect.bottom + padBot).coerceAtMost(h - 6f),
    )

    // 5. Hardware-Specific Sculpted Body Details BEFORE Lens
    when (p.form) {
        HardwareShellForm.DMG -> drawDmgBodySculpting(w, h, bezelRect, p, dim)
        HardwareShellForm.POCKET, HardwareShellForm.LIGHT -> drawPocketBodySculpting(w, h, bezelRect, p, dim)
        HardwareShellForm.SGB -> drawSuperGameBoyFrame(w, h, bezelRect, p, dim)
        HardwareShellForm.GBC -> drawGbcBodySculpting(w, h, bezelRect, skinId, p, dim)
        HardwareShellForm.AGB -> drawAgbBodySculpting(w, h, bezelRect, isLandscape, p, dim)
        HardwareShellForm.GBA_SP -> drawGbaSpBodySculpting(w, h, bezelRect, skinId, isLandscape, p, dim)
        HardwareShellForm.GB_MICRO -> drawGbMicroFaceplate(w, h, bezelRect, skinId, p, dim)
        HardwareShellForm.AURORA -> drawAuroraPlasmaAccents(w, h, bezelRect, p, dim)
    }

    // 6. Recessed Dark Glass Lens Plate (with realistic diagonal glass reflection)
    val lensRadius = when (p.form) {
        HardwareShellForm.DMG -> 14f
        HardwareShellForm.AGB -> 24f
        HardwareShellForm.GB_MICRO -> 18f
        else -> 16f
    }
    // Outer lens shadow
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.45f),
        topLeft = Offset(bezelRect.left - 2f, bezelRect.top - 1f),
        size = Size(bezelRect.width + 4f, bezelRect.height + 5f),
        cornerRadius = CornerRadius(lensRadius + 2f, lensRadius + 2f),
    )
    // Lens fill
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(p.bezelInner.scaleRgb(dim * 1.15f), p.bezelInner.scaleRgb(dim * 0.85f)),
            startY = bezelRect.top,
            endY = bezelRect.bottom,
        ),
        topLeft = Offset(bezelRect.left, bezelRect.top),
        size = Size(bezelRect.width, bezelRect.height),
        cornerRadius = CornerRadius(lensRadius, lensRadius),
    )
    // Glossy diagonal glass glint across lens corner
    val glintPath = Path().apply {
        moveTo(bezelRect.left + lensRadius, bezelRect.top)
        lineTo(bezelRect.left + bezelRect.width * 0.45f, bezelRect.top)
        lineTo(bezelRect.left + bezelRect.width * 0.16f, bezelRect.bottom)
        lineTo(bezelRect.left, bezelRect.bottom - lensRadius)
        close()
    }
    drawPath(glintPath, color = Color.White.copy(alpha = 0.045f * dim))

    // Lens border trim (Mirror chrome on Pocket Silver, Neon gradient on Aurora, Gold on Light/Micro)
    when (p.form) {
        HardwareShellForm.AURORA -> drawRoundRect(
            brush = Brush.horizontalGradient(listOf(p.accent.copy(alpha = 0.9f * dim), p.secondaryTrim.copy(alpha = 0.9f * dim))),
            topLeft = Offset(bezelRect.left, bezelRect.top),
            size = Size(bezelRect.width, bezelRect.height),
            cornerRadius = CornerRadius(lensRadius, lensRadius),
            style = Stroke(width = 3f),
        )
        HardwareShellForm.POCKET -> drawRoundRect(
            brush = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.7f * dim), Color(0xFF64748B), Color.White.copy(alpha = 0.5f * dim))),
            topLeft = Offset(bezelRect.left, bezelRect.top),
            size = Size(bezelRect.width, bezelRect.height),
            cornerRadius = CornerRadius(lensRadius, lensRadius),
            style = Stroke(width = 2.6f),
        )
        HardwareShellForm.LIGHT -> drawRoundRect(
            color = Color(0xFF2DD4BF).copy(alpha = 0.75f * dim),
            topLeft = Offset(bezelRect.left, bezelRect.top),
            size = Size(bezelRect.width, bezelRect.height),
            cornerRadius = CornerRadius(lensRadius, lensRadius),
            style = Stroke(width = 2.6f),
        )
        else -> drawRoundRect(
            color = p.bezelOuter.copy(alpha = 0.65f * dim),
            topLeft = Offset(bezelRect.left, bezelRect.top),
            size = Size(bezelRect.width, bezelRect.height),
            cornerRadius = CornerRadius(lensRadius, lensRadius),
            style = Stroke(width = 2f),
        )
    }

    // 7. Lens Details: DMG Twin Stripes, GBA SP 4 Rubber Bumpers, Power/Battery LED, Branding
    drawLensAuthenticDetails(bezelRect, screenRect, padX, padTop, padBot, skinId, p, dim)
}

private fun DrawScope.drawTranslucentPcbInternals(
    w: Float,
    h: Float,
    screenRect: Rect,
    p: SkinColors,
    dim: Float,
) {
    val traceCol = Color(0xFFD4AF37).copy(alpha = 0.16f * dim)
    val pcbDark = Color(0xFF0A241A).copy(alpha = 0.22f * dim)
    val chipCol = Color(0xFF090B10).copy(alpha = 0.30f * dim)

    // Internal motherboard silhouette below screen
    val pcbTop = (screenRect.bottom + 24f).coerceAtMost(h * 0.70f)
    drawRoundRect(
        color = pcbDark,
        topLeft = Offset(w * 0.06f, pcbTop),
        size = Size(w * 0.88f, (h - pcbTop - 16f).coerceAtLeast(24f)),
        cornerRadius = CornerRadius(14f, 14f),
    )

    // Main CPU QFP IC package + gold pins
    val chipSize = minOf(w, h) * 0.13f
    val chipX = w * 0.44f
    val chipY = (pcbTop + 26f).coerceAtMost(h - chipSize - 24f)
    if (chipY > screenRect.bottom + 12f) {
        drawRoundRect(chipCol, Offset(chipX, chipY), Size(chipSize, chipSize), CornerRadius(4f, 4f))
        val pinStep = chipSize / 6f
        for (i in 1..5) {
            drawLine(traceCol, Offset(chipX - 5f, chipY + i * pinStep), Offset(chipX, chipY + i * pinStep), 1.4f)
            drawLine(traceCol, Offset(chipX + chipSize, chipY + i * pinStep), Offset(chipX + chipSize + 5f, chipY + i * pinStep), 1.4f)
        }
    }

    // Gold copper bus traces & via pads on left and right edges
    for (i in 0..6) {
        val y = h * (0.12f + i * 0.12f)
        drawLine(traceCol, Offset(w * 0.04f, y), Offset(w * 0.18f, y), 1.8f)
        drawCircle(traceCol, radius = 2.6f, center = Offset(w * 0.18f, y))
        drawLine(traceCol, Offset(w * 0.82f, y), Offset(w * 0.96f, y), 1.8f)
        drawCircle(traceCol, radius = 2.6f, center = Offset(w * 0.82f, y))
    }

    // Cylindrical silver capacitors + screw posts
    val screwCol = Color.White.copy(alpha = 0.24f * dim)
    listOf(
        Offset(w * 0.09f, h * 0.06f),
        Offset(w * 0.91f, h * 0.06f),
        Offset(w * 0.09f, h * 0.93f),
        Offset(w * 0.91f, h * 0.93f),
    ).forEach { c ->
        drawCircle(Color.Black.copy(alpha = 0.22f), radius = 7f, center = c)
        drawCircle(screwCol, radius = 4f, center = c, style = Stroke(1.3f))
    }
}

private fun DrawScope.drawDmgBodySculpting(w: Float, h: Float, bezelRect: Rect, p: SkinColors, dim: Float) {
    // Authentic DMG-01 top horizontal channel & dual vertical side grooves
    val grooveY = (bezelRect.top * 0.45f).coerceIn(8f, 22f)
    drawLine(Color.Black.copy(alpha = 0.24f), Offset(0f, grooveY), Offset(w, grooveY), 2.5f)
    drawLine(Color.White.copy(alpha = 0.22f * dim), Offset(0f, grooveY + 2f), Offset(w, grooveY + 2f), 1.5f)
    drawLine(Color.Black.copy(alpha = 0.22f), Offset(w * 0.12f, 0f), Offset(w * 0.12f, grooveY), 2f)
    drawLine(Color.Black.copy(alpha = 0.22f), Offset(w * 0.88f, 0f), Offset(w * 0.88f, grooveY), 2f)

    // Iconic DMG-01 sculpted diagonal lower-right corner bevel
    val bevelSize = minOf(w, h) * 0.10f
    val cornerPath = Path().apply {
        moveTo(w - bevelSize, h)
        lineTo(w, h - bevelSize)
        lineTo(w, h)
        close()
    }
    drawPath(cornerPath, color = Color.Black.copy(alpha = 0.26f))
    drawLine(Color.White.copy(alpha = 0.22f * dim), Offset(w - bevelSize, h), Offset(w, h - bevelSize), 2f)

    // Iconic 6 deep diagonal DMG speaker vents (bottom-right)
    val spX = w - minOf(w, h) * 0.26f
    val spY = h - minOf(w, h) * 0.12f
    for (i in 0..5) {
        val ox = i * (minOf(w, h) * 0.032f).coerceIn(6f, 14f)
        drawLine(
            color = Color.Black.copy(alpha = 0.52f),
            start = Offset(spX + ox, spY + 18f),
            end = Offset(spX + ox + 11f, spY - 10f),
            strokeWidth = 4.2f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color.White.copy(alpha = 0.18f * dim),
            start = Offset(spX + ox + 2f, spY + 18f),
            end = Offset(spX + ox + 13f, spY - 10f),
            strokeWidth = 1.2f,
            cap = StrokeCap.Round,
        )
    }

    // Recessed bottom PHONES jack notch
    val jackW = 44f
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.24f),
        topLeft = Offset(w / 2f - jackW / 2f, h - 10f),
        size = Size(jackW, 10f),
        cornerRadius = CornerRadius(4f, 4f),
    )
}

private fun DrawScope.drawPocketBodySculpting(w: Float, h: Float, bezelRect: Rect, p: SkinColors, dim: Float) {
    // Sleek slim side bevel & circular perforated Pocket speaker dots
    val dotBaseX = w - minOf(w, h) * 0.20f
    val dotBaseY = h - minOf(w, h) * 0.12f
    val step = (minOf(w, h) * 0.025f).coerceIn(5f, 10f)
    for (r in 0..3) {
        for (c in 0..4) {
            if ((r == 0 || r == 3) && (c == 0 || c == 4)) continue
            drawCircle(
                color = Color.Black.copy(alpha = 0.46f),
                radius = 2.2f,
                center = Offset(dotBaseX + c * step, dotBaseY + r * step),
            )
        }
    }
}

private fun DrawScope.drawSuperGameBoyFrame(w: Float, h: Float, bezelRect: Rect, p: SkinColors, dim: Float) {
    // Classic SNES purple architectural side columns & corner medallions
    val railW = (w * 0.045f).coerceIn(10f, 26f)
    drawRect(p.bezelOuter.copy(alpha = 0.32f * dim), Offset(0f, 0f), Size(railW, h))
    drawRect(p.bezelOuter.copy(alpha = 0.32f * dim), Offset(w - railW, 0f), Size(railW, h))
    drawRoundRect(
        color = p.secondaryTrim.copy(alpha = 0.55f * dim),
        topLeft = Offset(bezelRect.left - 8f, bezelRect.top - 8f),
        size = Size(bezelRect.width + 16f, bezelRect.height + 16f),
        cornerRadius = CornerRadius(20f, 20f),
        style = Stroke(width = 4f),
    )
}

private fun DrawScope.drawGbcBodySculpting(w: Float, h: Float, bezelRect: Rect, skinId: GbcaSkinId, p: SkinColors, dim: Float) {
    // Top-center glossy dark Infrared (COMM.) port notch!
    val irW = (w * 0.16f).coerceIn(36f, 84f)
    drawRoundRect(
        color = Color(0xFF1A0910).copy(alpha = 0.85f),
        topLeft = Offset(w / 2f - irW / 2f, 0f),
        size = Size(irW, 11f),
        cornerRadius = CornerRadius(4f, 4f),
    )
    // Pikachu Edition royal blue side accents
    if (skinId == GbcaSkinId.GBC_PIKACHU) {
        val sideW = (w * 0.055f).coerceIn(12f, 28f)
        drawRect(Color(0xFF2563EB).copy(alpha = 0.88f * dim), Offset(0f, 0f), Size(sideW, h))
        drawRect(Color(0xFF2563EB).copy(alpha = 0.88f * dim), Offset(w - sideW, 0f), Size(sideW, h))
    }
    // Iconic GBC circular dot-matrix speaker grille (bottom-right)
    val spCenterX = w - minOf(w, h) * 0.15f
    val spCenterY = h - minOf(w, h) * 0.10f
    val step = (minOf(w, h) * 0.024f).coerceIn(4.5f, 9f)
    for (dy in -2..2) {
        for (dx in -2..2) {
            if (dx * dx + dy * dy <= 5) {
                drawCircle(
                    color = Color.Black.copy(alpha = 0.48f),
                    radius = 2.1f,
                    center = Offset(spCenterX + dx * step, spCenterY + dy * step),
                )
            }
        }
    }
}

private fun DrawScope.drawAgbBodySculpting(w: Float, h: Float, bezelRect: Rect, isLandscape: Boolean, p: SkinColors, dim: Float) {
    // Iconic AGB-001 curved side shoulder bumpers & ergonomic wing contour!
    val bumperCol = p.secondaryTrim.scaleRgb(dim)
    val bumperW = (w * 0.045f).coerceIn(10f, 26f)
    val bumperH = (h * (if (isLandscape) 0.46f else 0.18f)).coerceIn(36f, 180f)
    val bumperY = if (isLandscape) (h - bumperH) / 2f else bezelRect.bottom + 14f
    drawRoundRect(bumperCol, Offset(0f, bumperY), Size(bumperW, bumperH), CornerRadius(12f, 12f))
    drawRoundRect(bumperCol, Offset(w - bumperW, bumperY), Size(bumperW, bumperH), CornerRadius(12f, 12f))
    // Subtle sculpted curved wing arc highlight under screen
    drawArc(
        color = Color.White.copy(alpha = 0.16f * dim),
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(w * 0.12f, bezelRect.bottom + 8f),
        size = Size(w * 0.76f, 28f),
        style = Stroke(width = 2.2f),
    )
    // Oval GBA speaker dots (right of screen in landscape, bottom-right in portrait)
    val dotX = if (isLandscape) (bezelRect.right + w) * 0.5f - 12f else w - minOf(w, h) * 0.17f
    val dotY = if (isLandscape) h * 0.25f else h - minOf(w, h) * 0.11f
    for (r in 0..2) {
        for (c in 0..3) {
            drawCircle(Color.Black.copy(alpha = 0.45f), 2.1f, Offset(dotX + c * 7f, dotY + r * 7f))
        }
    }
}

private fun DrawScope.drawGbaSpBodySculpting(
    w: Float,
    h: Float,
    bezelRect: Rect,
    skinId: GbcaSkinId,
    isLandscape: Boolean,
    p: SkinColors,
    dim: Float,
) {
    // Photorealistic 3D Cylindrical Dual-Barrel GBA SP Clamshell Hinge!
    val hingeY = (bezelRect.bottom + (if (isLandscape) 6f else 14f)).coerceAtMost(h - 32f)
    val hingeH = (minOf(w, h) * 0.042f).coerceIn(12f, 24f)
    // Hinge shadow groove
    drawRect(Color.Black.copy(alpha = 0.55f), Offset(0f, hingeY - 2f), Size(w, hingeH + 5f))
    // 3D metallic cylindrical shading
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(
                p.bodyTop.scaleRgb(dim * 1.25f),
                Color.White.copy(alpha = 0.38f * dim),
                p.bodyBottom.scaleRgb(dim * 0.70f),
                Color.Black.copy(alpha = 0.55f),
            ),
            startY = hingeY,
            endY = hingeY + hingeH,
        ),
        topLeft = Offset(w * 0.03f, hingeY),
        size = Size(w * 0.94f, hingeH),
        cornerRadius = CornerRadius(hingeH / 2f, hingeH / 2f),
    )
    // Dual hinge barrel split seams (authentic SP 3-segment barrel!)
    listOf(w * 0.20f, w * 0.80f).forEach { seamX ->
        drawLine(Color.Black.copy(alpha = 0.65f), Offset(seamX, hingeY), Offset(seamX, hingeY + hingeH), 2.5f)
        drawLine(Color.White.copy(alpha = 0.25f * dim), Offset(seamX + 2f, hingeY + 2f), Offset(seamX + 2f, hingeY + hingeH - 2f), 1.2f)
    }

    // Authentic 16-Hole Circular Diamond Speaker Array centered right below the hinge!
    if (!isLandscape && h - (hingeY + hingeH) > 90f) {
        val spCx = w / 2f
        val spCy = hingeY + hingeH + 40f
        val step = (minOf(w, h) * 0.022f).coerceIn(5.5f, 10f)
        for (dy in -2..2) {
            for (dx in -2..2) {
                val m = kotlin.math.abs(dx) + kotlin.math.abs(dy)
                if (m <= 2) {
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.62f),
                        radius = if (m == 0) 2.8f else 2.2f,
                        center = Offset(spCx + dx * step, spCy + dy * step),
                    )
                }
            }
        }
        // Small circular brightness button right above the speaker
        drawCircle(
            color = Color.Black.copy(alpha = 0.45f),
            radius = 8f,
            center = Offset(spCx, hingeY + hingeH + 15f),
        )
        drawCircle(
            color = p.bodyTop.scaleRgb(dim),
            radius = 6f,
            center = Offset(spCx, hingeY + hingeH + 15f),
        )
    }

    // NES Classic Edition authentic black/red faceplate stripe
    if (skinId == GbcaSkinId.GBASP_NES_CLASSIC && !isLandscape && h - (hingeY + hingeH) > 60f) {
        drawRoundRect(
            color = Color(0xFF1A1A1D).copy(alpha = 0.82f * dim),
            topLeft = Offset(w * 0.06f, hingeY + hingeH + 66f),
            size = Size(w * 0.88f, (h - hingeY - hingeH - 84f).coerceAtLeast(20f)),
            cornerRadius = CornerRadius(12f, 12f),
            style = Stroke(width = 2.5f),
        )
    }
    // Zelda Triforce Gold embossed emblem below speaker
    if (skinId == GbcaSkinId.GBASP_ZELDA_GOLD && !isLandscape && h - (hingeY + hingeH) > 120f) {
        val tx = w / 2f
        val ty = hingeY + hingeH + 92f
        val sz = 16f
        val triCol = Color(0xFFFEF08A).copy(alpha = 0.55f * dim)
        fun drawTri(cx: Float, cy: Float, r: Float) {
            val path = Path().apply {
                moveTo(cx, cy - r)
                lineTo(cx - r * 0.9f, cy + r * 0.7f)
                lineTo(cx + r * 0.9f, cy + r * 0.7f)
                close()
            }
            drawPath(path, triCol)
        }
        drawTri(tx, ty - sz * 0.85f, sz * 0.65f)
        drawTri(tx - sz * 0.65f, ty + sz * 0.35f, sz * 0.65f)
        drawTri(tx + sz * 0.65f, ty + sz * 0.35f, sz * 0.65f)
    }
}

private fun DrawScope.drawGbMicroFaceplate(w: Float, h: Float, bezelRect: Rect, skinId: GbcaSkinId, p: SkinColors, dim: Float) {
    // Authentic detachable brushed aluminum/gold faceplate inset with glossy chrome ring!
    val fpPad = (minOf(w, h) * 0.04f).coerceIn(10f, 24f)
    val fpRect = Rect(fpPad, fpPad, w - fpPad, h - fpPad)
    val plateTop = if (skinId == GbcaSkinId.GBM_FAMICOM) Color(0xFFF6D365).scaleRgb(dim) else Color(0xFF23252C).scaleRgb(dim)
    val plateBot = if (skinId == GbcaSkinId.GBM_FAMICOM) Color(0xFFC49424).scaleRgb(dim) else Color(0xFF101116).scaleRgb(dim)
    drawRoundRect(
        brush = Brush.linearGradient(listOf(plateTop, plateBot), Offset(fpRect.left, fpRect.top), Offset(fpRect.right, fpRect.bottom)),
        topLeft = Offset(fpRect.left, fpRect.top),
        size = Size(fpRect.width, fpRect.height),
        cornerRadius = CornerRadius(28f, 28f),
    )
    drawRoundRect(
        brush = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.72f * dim), Color.Black.copy(alpha = 0.45f), Color.White.copy(alpha = 0.55f * dim))),
        topLeft = Offset(fpRect.left, fpRect.top),
        size = Size(fpRect.width, fpRect.height),
        cornerRadius = CornerRadius(28f, 28f),
        style = Stroke(width = 3.2f),
    )
}

private fun DrawScope.drawAuroraPlasmaAccents(w: Float, h: Float, bezelRect: Rect, p: SkinColors, dim: Float) {
    // Flagship STORM Aurora neon edge light bars
    drawLine(
        brush = Brush.verticalGradient(listOf(Color.Transparent, p.accent.copy(alpha = 0.8f * dim), Color.Transparent)),
        start = Offset(3f, h * 0.10f),
        end = Offset(3f, h * 0.90f),
        strokeWidth = 3.5f,
    )
    drawLine(
        brush = Brush.verticalGradient(listOf(Color.Transparent, p.secondaryTrim.copy(alpha = 0.8f * dim), Color.Transparent)),
        start = Offset(w - 3f, h * 0.10f),
        end = Offset(w - 3f, h * 0.90f),
        strokeWidth = 3.5f,
    )
}

private fun DrawScope.drawLensAuthenticDetails(
    bezelRect: Rect,
    screenRect: Rect,
    padX: Float,
    padTop: Float,
    padBot: Float,
    skinId: GbcaSkinId,
    p: SkinColors,
    dim: Float,
) {
    // 1. DMG-01 Iconic Magenta + Navy Twin Top Stripes & "DOT MATRIX WITH STEREO SOUND"
    if (p.form == HardwareShellForm.DMG) {
        val stripeY = bezelRect.top + padTop * 0.50f
        drawLine(p.accent.copy(alpha = 0.92f * dim), Offset(bezelRect.left + 12f, stripeY - 2.6f), Offset(bezelRect.right - 12f, stripeY - 2.6f), 2.3f)
        drawLine(p.secondaryTrim.copy(alpha = 0.92f * dim), Offset(bezelRect.left + 12f, stripeY + 2.6f), Offset(bezelRect.right - 12f, stripeY + 2.6f), 2.3f)
        if (bezelRect.width > 180f) {
            drawIntoCanvas { canvas ->
                val bgP = Paint().apply { color = p.bezelInner.scaleRgb(dim).toArgb() }
                val txtP = Paint().apply {
                    isAntiAlias = true
                    color = p.brandText.copy(alpha = 0.90f * dim).toArgb()
                    textAlign = Paint.Align.CENTER
                    textSize = (padTop * 0.34f).coerceIn(7f, 12f)
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                }
                val label = "DOT MATRIX WITH STEREO SOUND"
                val tw = txtP.measureText(label)
                canvas.nativeCanvas.drawRect(bezelRect.center.x - tw / 2f - 6f, stripeY - 7f, bezelRect.center.x + tw / 2f + 6f, stripeY + 7f, bgP)
                canvas.nativeCanvas.drawText(label, bezelRect.center.x, stripeY + txtP.textSize * 0.35f, txtP)
            }
        }
    }

    // 2. GBA SP 4 Recessed Rubber Screen Bumper Pads in Bezel Corners
    if (p.form == HardwareShellForm.GBA_SP) {
        val bOffX = (padX * 0.45f).coerceIn(6f, 16f)
        val bOffY = (padTop * 0.45f).coerceIn(6f, 14f)
        listOf(
            Offset(bezelRect.left + bOffX, bezelRect.top + bOffY),
            Offset(bezelRect.right - bOffX, bezelRect.top + bOffY),
            Offset(bezelRect.left + bOffX, bezelRect.bottom - bOffY),
            Offset(bezelRect.right - bOffX, bezelRect.bottom - bOffY),
        ).forEach { c ->
            drawCircle(Color.Black.copy(alpha = 0.65f), 4.5f, c)
            drawCircle(Color.White.copy(alpha = 0.14f * dim), 4.5f, c, style = Stroke(1f))
        }
    }

    // 3. Authentic Power / Battery LED (with acoustic waves on DMG/GBC, dual Power+Charge on GBA SP!)
    val ledOnRight = p.form == HardwareShellForm.AGB || p.form == HardwareShellForm.GBA_SP
    val ledCenter = if (ledOnRight) {
        Offset(bezelRect.right - padX * 0.45f, screenRect.top + screenRect.height * 0.24f)
    } else {
        Offset(bezelRect.left + padX * 0.45f, screenRect.top + screenRect.height * 0.34f)
    }
    drawCircle(p.powerLed.copy(alpha = 0.34f), 9.5f, ledCenter)
    drawCircle(p.powerLed, 4.2f, ledCenter)
    drawCircle(Color.White.copy(alpha = 0.65f), 1.4f, Offset(ledCenter.x - 1f, ledCenter.y - 1f))

    if (p.form == HardwareShellForm.GBA_SP) {
        val chargeLed = Offset(ledCenter.x, ledCenter.y + 18f)
        drawCircle(Color(0xFFF59E0B).copy(alpha = 0.25f), 6.5f, chargeLed)
        drawCircle(Color(0xFFF59E0B).copy(alpha = 0.85f * dim), 3.0f, chargeLed)
    }

    // 4. Authentic Bottom Bezel Console Branding (with REAL 5-Color Rainbow "COLOR" on GBC!)
    drawIntoCanvas { canvas ->
        val fontSize = (padBot * 0.42f).coerceIn(10f, 22f)
        val baseY = screenRect.bottom + padBot * 0.68f
        val paint = Paint().apply {
            isAntiAlias = true
            color = p.brandText.copy(alpha = 0.90f * dim).toArgb()
            textAlign = Paint.Align.CENTER
            textSize = fontSize
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC)
            letterSpacing = 0.08f
        }

        if (p.form == HardwareShellForm.GBC) {
            // Draw "GAME BOY " + authentic 5-color painted C-O-L-O-R letters!
            val gbStr = "GAME BOY "
            val colorLetters = listOf(
                "C" to Color(0xFFEF4444),
                "O" to Color(0xFFA855F7),
                "L" to Color(0xFF22C55E),
                "O" to Color(0xFFFACC15),
                "R" to Color(0xFF38BDF8),
            )
            paint.textAlign = Paint.Align.LEFT
            val gbW = paint.measureText(gbStr)
            val colorW = colorLetters.sumOf { paint.measureText(it.first).toDouble() }.toFloat()
            var x = bezelRect.center.x - (gbW + colorW) / 2f
            canvas.nativeCanvas.drawText(gbStr, x, baseY, paint)
            x += gbW
            for ((ch, col) in colorLetters) {
                paint.color = col.copy(alpha = 0.95f * dim).toArgb()
                canvas.nativeCanvas.drawText(ch, x, baseY, paint)
                x += paint.measureText(ch)
            }
        } else {
            canvas.nativeCanvas.drawText(p.title, bezelRect.center.x, baseY, paint)
        }
    }
}

private fun Color.scaleRgb(factor: Float): Color {
    val f = factor.coerceIn(0f, 1.5f)
    return Color(
        red = (red * f).coerceIn(0f, 1f),
        green = (green * f).coerceIn(0f, 1f),
        blue = (blue * f).coerceIn(0f, 1f),
        alpha = alpha,
    )
}
