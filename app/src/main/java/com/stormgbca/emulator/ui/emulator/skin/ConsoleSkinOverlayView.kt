package com.stormgbca.emulator.ui.emulator.skin

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.stormgbca.emulator.model.ConsoleSystem

enum class GbcaSkinId(
    val key: String,
    val system: ConsoleSystem?,
    val labelRu: String,
    val labelEn: String,
) {
    NONE("none", null, "Без корпуса (Полный экран)", "No Skin (Fullscreen)"),

    // GB (8 + NONE)
    DMG_CLASSIC("dmg_classic", ConsoleSystem.GB, "DMG Классический серый", "DMG Classic Gray"),
    DMG_BLACK("dmg_black", ConsoleSystem.GB, "DMG Играй громко Чёрный", "DMG Play It Loud Black"),
    DMG_CLEAR("dmg_clear", ConsoleSystem.GB, "DMG Прозрачный", "DMG Clear"),
    GBP_SILVER("gbp_silver", ConsoleSystem.GB, "Game Boy Pocket Серебро", "Pocket Silver"),
    GBP_ICE_BLUE("gbp_ice_blue", ConsoleSystem.GB, "Game Boy Pocket Ледяной", "Pocket Ice Blue"),
    GBL_GOLD("gbl_gold", ConsoleSystem.GB, "Game Boy Light Золото", "Light Gold"),
    SGB_FRAME("sgb_frame", ConsoleSystem.GB, "Super Game Boy Рамка", "Super Game Boy Frame"),
    GB_STORM_AURORA("gb_storm_aurora", ConsoleSystem.GB, "STORM Aurora GB", "STORM Aurora GB"),

    // GBC (8 + NONE)
    GBC_ATOMIC_PURPLE("gbc_atomic_purple", ConsoleSystem.GBC, "Атомный фиолет (Прозрачный)", "Atomic Purple"),
    GBC_BERRY("gbc_berry", ConsoleSystem.GBC, "Ягодный маджента", "Berry Magenta"),
    GBC_TEAL("gbc_teal", ConsoleSystem.GBC, "Бирюзовый Teal", "Teal"),
    GBC_KIWI("gbc_kiwi", ConsoleSystem.GBC, "Киви Лайм", "Kiwi Green"),
    GBC_DANDELION("gbc_dandelion", ConsoleSystem.GBC, "Одуванчик Жёлтый", "Dandelion Yellow"),
    GBC_GRAPE("gbc_grape", ConsoleSystem.GBC, "Виноградный фиолет", "Grape Purple"),
    GBC_PIKACHU("gbc_pikachu", ConsoleSystem.GBC, "Издание Пикачу", "Pikachu Edition"),
    GBC_STORM_AURORA("gbc_storm_aurora", ConsoleSystem.GBC, "STORM Aurora GBC", "STORM Aurora GBC"),

    // GBA (10 + NONE)
    GBA_INDIGO("gba_indigo", ConsoleSystem.GBA, "Индиго Фиолет", "Indigo"),
    GBA_GLACIER("gba_glacier", ConsoleSystem.GBA, "Ледник Полупрозрачный", "Glacier"),
    GBA_ARCTIC_WHITE("gba_arctic_white", ConsoleSystem.GBA, "Арктический белый", "Arctic White"),
    GBA_SPICE_ORANGE("gba_spice_orange", ConsoleSystem.GBA, "Пряный оранжевый", "Spice Orange"),
    GBA_FUCHSIA("gba_fuchsia", ConsoleSystem.GBA, "Фуксия Прозрачная", "Fuchsia"),
    GBASP_COBALT("gbasp_cobalt", ConsoleSystem.GBA, "SP Кобальтовый синий", "SP Cobalt Blue"),
    GBASP_ONYX("gbasp_onyx", ConsoleSystem.GBA, "SP Чёрный оникс", "SP Onyx Black"),
    GBASP_NES_CLASSIC("gbasp_nes_classic", ConsoleSystem.GBA, "SP NES Классик", "SP NES Classic"),
    GBM_FAMICOM("gbm_famicom", ConsoleSystem.GBA, "Micro Famicom 20th", "Micro Famicom"),
    GBA_STORM_AURORA("gba_storm_aurora", ConsoleSystem.GBA, "STORM Aurora GBA", "STORM Aurora GBA");

    companion object {
        fun forSystem(system: ConsoleSystem): List<GbcaSkinId> =
            entries.filter { it.system == system } + listOf(NONE)

        fun fromKey(key: String?, system: ConsoleSystem): GbcaSkinId {
            val found = entries.firstOrNull { it.key.equals(key, ignoreCase = true) }
            if (found == NONE) return NONE
            if (found != null && found.system == system) return found
            return when (system) {
                ConsoleSystem.GB -> GB_STORM_AURORA
                ConsoleSystem.GBC -> GBC_STORM_AURORA
                ConsoleSystem.GBA -> GBA_STORM_AURORA
            }
        }
    }
}

private data class SkinColors(
    val bodyTop: Color,
    val bodyBottom: Color,
    val bezelOuter: Color,
    val bezelInner: Color,
    val accent: Color,
    val brandText: Color,
    val powerLed: Color,
    val translucent: Boolean = false,
    val title: String,
    val isDark: Boolean = true,
)

private fun resolvePalette(id: GbcaSkinId): SkinColors = when (id) {
    GbcaSkinId.DMG_CLASSIC -> SkinColors(Color(0xFFD6D3C9), Color(0xFFB8B4A8), Color(0xFF5D626E), Color(0xFF1F2329), Color(0xFF8B1D4E), Color(0xFF1E293B), Color(0xFFFF2E55), false, "GAME BOY DMG-01", false)
    GbcaSkinId.DMG_BLACK -> SkinColors(Color(0xFF1F222A), Color(0xFF111318), Color(0xFF333844), Color(0xFF0A0C10), Color(0xFFE11D48), Color(0xFFE2E8F0), Color(0xFFFF2E55), false, "PLAY IT LOUD", true)
    GbcaSkinId.DMG_CLEAR -> SkinColors(Color(0xFFB8C4D4), Color(0xFF8899AE), Color(0xFF475569), Color(0xFF161D29), Color(0xFF8B1D4E), Color(0xFF0F172A), Color(0xFFFF2E55), true, "CLEAR EDITION", false)
    GbcaSkinId.GBP_SILVER -> SkinColors(Color(0xFFE2E8F0), Color(0xFF94A3B8), Color(0xFF334155), Color(0xFF0F172A), Color(0xFF0284C7), Color(0xFF0F172A), Color(0xFFEF4444), false, "GAME BOY pocket", false)
    GbcaSkinId.GBP_ICE_BLUE -> SkinColors(Color(0xFF7DD3FC), Color(0xFF0284C7), Color(0xFF1E3A8A), Color(0xFF091224), Color(0xFF38BDF8), Color(0xFFF8FAFC), Color(0xFFEF4444), true, "POCKET ICE BLUE", true)
    GbcaSkinId.GBL_GOLD -> SkinColors(Color(0xFFF5D061), Color(0xFFB4811B), Color(0xFF4A350B), Color(0xFF1A1205), Color(0xFF00E5FF), Color(0xFF1F1604), Color(0xFF00E5FF), false, "GAME BOY LIGHT", false)
    GbcaSkinId.SGB_FRAME -> SkinColors(Color(0xFF6B7280), Color(0xFF374151), Color(0xFF4C1D95), Color(0xFF111827), Color(0xFFA855F7), Color(0xFFF8FAFC), Color(0xFF10B981), false, "SUPER GAME BOY", true)
    GbcaSkinId.GB_STORM_AURORA -> SkinColors(Color(0xFF0D1322), Color(0xFF05080F), Color(0xFF00E5FF), Color(0xFF070A12), Color(0xFF00E5FF), Color(0xFFE0F2FE), Color(0xFF00E5FF), false, "STORM GB AURORA", true)

    GbcaSkinId.GBC_ATOMIC_PURPLE -> SkinColors(Color(0xFF8B5CF6), Color(0xFF4C1D95), Color(0xFF1E1B4B), Color(0xFF090714), Color(0xFFA78BFA), Color(0xFFEDE9FE), Color(0xFFFF2E55), true, "ATOMIC PURPLE", true)
    GbcaSkinId.GBC_BERRY -> SkinColors(Color(0xFFEC4899), Color(0xFF9D174D), Color(0xFF311022), Color(0xFF12070D), Color(0xFFF472B6), Color(0xFFFCE7F3), Color(0xFFFF2E55), false, "GAME BOY COLOR", true)
    GbcaSkinId.GBC_TEAL -> SkinColors(Color(0xFF14B8A6), Color(0xFF0F766E), Color(0xFF132A2E), Color(0xFF071113), Color(0xFF2DD4BF), Color(0xFFCCFBF1), Color(0xFFFF2E55), false, "GAME BOY COLOR", true)
    GbcaSkinId.GBC_KIWI -> SkinColors(Color(0xFF84CC16), Color(0xFF4D7C0F), Color(0xFF1F2A10), Color(0xFF0C1207), Color(0xFFA3E635), Color(0xFFF7FEE7), Color(0xFFFF2E55), false, "GAME BOY COLOR", false)
    GbcaSkinId.GBC_DANDELION -> SkinColors(Color(0xFFFACC15), Color(0xFFCA8A04), Color(0xFF2D2508), Color(0xFF120E04), Color(0xFFFDE047), Color(0xFF1E1B06), Color(0xFFFF2E55), false, "GAME BOY COLOR", false)
    GbcaSkinId.GBC_GRAPE -> SkinColors(Color(0xFF6D28D9), Color(0xFF3B0764), Color(0xFF1E1035), Color(0xFF090512), Color(0xFFC084FC), Color(0xFFF3E8FF), Color(0xFFFF2E55), false, "GAME BOY COLOR", true)
    GbcaSkinId.GBC_PIKACHU -> SkinColors(Color(0xFFFDE047), Color(0xFF2563EB), Color(0xFF1E3A8A), Color(0xFF081026), Color(0xFFEF4444), Color(0xFF0F172A), Color(0xFFFF2E55), false, "PIKACHU COLOR", false)
    GbcaSkinId.GBC_STORM_AURORA -> SkinColors(Color(0xFF130D29), Color(0xFF070512), Color(0xFFA855F7), Color(0xFF06040F), Color(0xFF00E5FF), Color(0xFFF5F3FF), Color(0xFF00E5FF), false, "STORM GBC AURORA", true)

    GbcaSkinId.GBA_INDIGO -> SkinColors(Color(0xFF4F46E5), Color(0xFF312E81), Color(0xFF1E1B4B), Color(0xFF09081A), Color(0xFF818CF8), Color(0xFFE0E7FF), Color(0xFF10B981), false, "GAME BOY ADVANCE", true)
    GbcaSkinId.GBA_GLACIER -> SkinColors(Color(0xFF93C5FD), Color(0xFF3B82F6), Color(0xFF1E3A8A), Color(0xFF0A142E), Color(0xFF60A5FA), Color(0xFFEFF6FF), Color(0xFF10B981), true, "GBA GLACIER", true)
    GbcaSkinId.GBA_ARCTIC_WHITE -> SkinColors(Color(0xFFF8FAFC), Color(0xFFCBD5E1), Color(0xFF64748B), Color(0xFF1E293B), Color(0xFF0284C7), Color(0xFF0F172A), Color(0xFF10B981), false, "ARCTIC WHITE", false)
    GbcaSkinId.GBA_SPICE_ORANGE -> SkinColors(Color(0xFFF97316), Color(0xFFC2410C), Color(0xFF431407), Color(0xFF1A0904), Color(0xFFFB923C), Color(0xFFFFF7ED), Color(0xFF10B981), false, "SPICE ORANGE", true)
    GbcaSkinId.GBA_FUCHSIA -> SkinColors(Color(0xFFF472B6), Color(0xFFBE185D), Color(0xFF500724), Color(0xFF1A050E), Color(0xFFF9A8D4), Color(0xFFFDF2F8), Color(0xFF10B981), true, "GBA FUCHSIA", true)
    GbcaSkinId.GBASP_COBALT -> SkinColors(Color(0xFF1D4ED8), Color(0xFF1E3A8A), Color(0xFF172554), Color(0xFF070C1E), Color(0xFF60A5FA), Color(0xFFDBEAFE), Color(0xFF10B981), false, "GAME BOY ADVANCE SP", true)
    GbcaSkinId.GBASP_ONYX -> SkinColors(Color(0xFF18181B), Color(0xFF09090B), Color(0xFF27272A), Color(0xFF050507), Color(0xFF94A3B8), Color(0xFFE4E4E7), Color(0xFF10B981), false, "GBA SP ONYX", true)
    GbcaSkinId.GBASP_NES_CLASSIC -> SkinColors(Color(0xFFE5E7EB), Color(0xFF9CA3AF), Color(0xFF1F2937), Color(0xFF111827), Color(0xFFDC2626), Color(0xFF111827), Color(0xFF10B981), false, "NES CLASSIC SP", false)
    GbcaSkinId.GBM_FAMICOM -> SkinColors(Color(0xFF7F1D1D), Color(0xFF450A0A), Color(0xFFFACC15), Color(0xFF1A0505), Color(0xFFFACC15), Color(0xFFFEF08A), Color(0xFF38BDF8), false, "MICRO FAMICOM", true)
    GbcaSkinId.GBA_STORM_AURORA -> SkinColors(Color(0xFF0A101E), Color(0xFF04060D), Color(0xFF00E5FF), Color(0xFF050810), Color(0xFFA855F7), Color(0xFFF1F5F9), Color(0xFF00E5FF), false, "STORM GBA AURORA", true)
    GbcaSkinId.NONE -> SkinColors(Color.Transparent, Color.Transparent, Color.Transparent, Color.Transparent, Color.Transparent, Color.Transparent, Color.Transparent, false, "", true)
}

/**
 * Computes the exact game screen rectangle inside a container of size [containerW] x [containerH],
 * honoring per-system custom screen geometry (Portrait & Landscape independently for GB, GBC, GBA).
 */
fun computeGameViewportRect(
    containerW: Int,
    containerH: Int,
    system: ConsoleSystem,
    skinId: GbcaSkinId,
    aspectMode: Int,
    nativeW: Int,
    nativeH: Int,
    customScreen: com.stormgbca.emulator.model.ScreenRectConfig? = null,
): Rect {
    if (containerW <= 0 || containerH <= 0) return Rect(0f, 0f, 1f, 1f)
    val rotated = (customScreen?.rotationDeg == 90 || customScreen?.rotationDeg == 270)
    val srcW = (if (rotated) nativeH else nativeW).coerceAtLeast(144).toFloat()
    val srcH = (if (rotated) nativeW else nativeH).coerceAtLeast(144).toFloat()
    val srcAspect = srcW / srcH

    // Custom per-system screen size & placement override!
    if (customScreen != null && customScreen.useCustom) {
        val boxW = (containerW * customScreen.widthFraction.coerceIn(0.25f, 1.0f))
        val boxH = (containerH * customScreen.heightFraction.coerceIn(0.20f, 1.0f))
        var targetW: Float
        var targetH: Float
        when (customScreen.aspectMode) {
            2, 3 -> { // Stretch / Free Custom W x H
                targetW = boxW
                targetH = boxH
            }
            1 -> { // Integer scaling inside user's custom bounds
                val intScale = minOf((boxW / srcW).toInt(), (boxH / srcH).toInt()).coerceAtLeast(1)
                targetW = srcW * intScale
                targetH = srcH * intScale
            }
            else -> { // Fit Original aspect inside user's custom bounds
                if (boxW / boxH > srcAspect) {
                    targetH = boxH
                    targetW = targetH * srcAspect
                } else {
                    targetW = boxW
                    targetH = targetW / srcAspect
                }
            }
        }
        val cx = containerW * customScreen.centerX.coerceIn(0.10f, 0.90f)
        val cy = containerH * customScreen.centerY.coerceIn(0.10f, 0.90f)
        val left = (cx - targetW / 2f).coerceIn(0f, (containerW - targetW).coerceAtLeast(0f))
        val top = (cy - targetH / 2f).coerceIn(0f, (containerH - targetH).coerceAtLeast(0f))
        return Rect(left, top, left + targetW, top + targetH)
    }

    val effAspect = customScreen?.aspectMode ?: aspectMode
    val isLandscape = containerW > containerH
    if (skinId == GbcaSkinId.NONE) {
        if (effAspect == 2) {
            return Rect(0f, 0f, containerW.toFloat(), containerH.toFloat())
        }
        val maxW = containerW.toFloat()
        val maxH = containerH.toFloat()
        var targetW: Float
        var targetH: Float
        if (maxW / maxH > srcAspect) {
            targetH = maxH
            targetW = targetH * srcAspect
        } else {
            targetW = maxW
            targetH = targetW / srcAspect
        }
        if (effAspect == 1) {
            val intScale = minOf((targetW / srcW).toInt(), (targetH / srcH).toInt()).coerceAtLeast(1)
            targetW = srcW * intScale
            targetH = srcH * intScale
        }
        val left = (containerW - targetW) / 2f
        val top = (containerH - targetH) / 2f
        return Rect(left, top, left + targetW, top + targetH)
    }

    // With console skin: fit cleanly in upper portion on portrait, or centered bezel in landscape
    val availW = containerW * (if (isLandscape) 0.56f else 0.78f)
    val availH = containerH * (if (isLandscape) 0.76f else 0.44f)
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
    val left = (containerW - targetW) / 2f
    val top = if (isLandscape) {
        (containerH - targetH) / 2f
    } else {
        (containerH * 0.085f).coerceAtLeast(24f)
    }
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
    val palette = resolvePalette(skinId)
    val dimFactor = (dimmingPercent.coerceIn(30, 100) / 100f)

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 1f || h <= 1f) return@Canvas

        val holePath = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = screenRect,
                    cornerRadius = CornerRadius(8f, 8f),
                )
            )
        }

        clipPath(holePath, clipOp = ClipOp.Difference) {
            drawChassis(w, h, screenRect, skinId, palette, dimFactor)
        }

        // Crisp recessed inner bezel rim around game screen cutout
        drawRoundRect(
            color = palette.bezelOuter.copy(alpha = 0.9f * dimFactor),
            topLeft = Offset(screenRect.left - 3f, screenRect.top - 3f),
            size = Size(screenRect.width + 6f, screenRect.height + 6f),
            cornerRadius = CornerRadius(10f, 10f),
            style = Stroke(width = 4f),
        )
    }
}

private fun DrawScope.drawChassis(
    w: Float,
    h: Float,
    screenRect: Rect,
    skinId: GbcaSkinId,
    palette: SkinColors,
    dimFactor: Float,
) {
    val topColor = palette.bodyTop.scaleRgb(dimFactor)
    val botColor = palette.bodyBottom.scaleRgb(dimFactor)
    drawRect(
        brush = Brush.verticalGradient(listOf(topColor, botColor)),
        size = Size(w, h),
    )

    // Translucent shell internal PCB trace & IC silhouette detail
    if (palette.translucent) {
        val traceColor = Color.White.copy(alpha = 0.08f * dimFactor)
        for (i in 0..5) {
            val y = h * (0.12f + i * 0.14f)
            drawLine(traceColor, Offset(w * 0.05f, y), Offset(w * 0.24f, y), strokeWidth = 2f)
            drawLine(traceColor, Offset(w * 0.76f, y), Offset(w * 0.95f, y), strokeWidth = 2f)
        }
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.14f * dimFactor),
            topLeft = Offset(w * 0.08f, h * 0.68f),
            size = Size(w * 0.14f, h * 0.12f),
            cornerRadius = CornerRadius(6f, 6f),
        )
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.White.copy(alpha = 0.16f), Color.Transparent, Color.Black.copy(alpha = 0.18f)),
                start = Offset(0f, 0f),
                end = Offset(w, h),
            )
        )
    }

    // Outer recessed screen lens surround
    val padX = (screenRect.width * 0.08f).coerceIn(18f, 44f)
    val padTop = (screenRect.height * 0.09f).coerceIn(18f, 36f)
    val padBot = (screenRect.height * 0.14f).coerceIn(24f, 48f)
    val bezelRect = Rect(
        left = (screenRect.left - padX).coerceAtLeast(8f),
        top = (screenRect.top - padTop).coerceAtLeast(8f),
        right = (screenRect.right + padX).coerceAtMost(w - 8f),
        bottom = (screenRect.bottom + padBot).coerceAtMost(h - 8f),
    )

    // GBA SP metallic clamshell hinge barrel along lower bezel seam
    if (skinId == GbcaSkinId.GBASP_COBALT || skinId == GbcaSkinId.GBASP_ONYX || skinId == GbcaSkinId.GBASP_NES_CLASSIC) {
        val hingeY = (bezelRect.bottom + 10f).coerceAtMost(h - 24f)
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.25f * dimFactor),
                    Color.Black.copy(alpha = 0.45f * dimFactor),
                    Color.White.copy(alpha = 0.12f * dimFactor),
                ),
                startY = hingeY,
                endY = hingeY + 14f,
            ),
            topLeft = Offset(w * 0.06f, hingeY),
            size = Size(w * 0.88f, 14f),
            cornerRadius = CornerRadius(7f, 7f),
        )
    }

    // Game Boy Micro metallic brushed faceplate rim
    if (skinId == GbcaSkinId.GBM_FAMICOM) {
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFDE047).copy(alpha = 0.40f * dimFactor),
                    Color(0xFFB45309).copy(alpha = 0.25f * dimFactor),
                    Color(0xFFFEF08A).copy(alpha = 0.45f * dimFactor),
                ),
                start = Offset(bezelRect.left - 12f, bezelRect.top - 12f),
                end = Offset(bezelRect.right + 12f, bezelRect.bottom + 12f),
            ),
            topLeft = Offset((bezelRect.left - 10f).coerceAtLeast(4f), (bezelRect.top - 10f).coerceAtLeast(4f)),
            size = Size((bezelRect.width + 20f).coerceAtMost(w - 8f), (bezelRect.height + 20f).coerceAtMost(h - 8f)),
            cornerRadius = CornerRadius(26f, 26f),
            style = Stroke(width = 4f),
        )
    }

    drawRoundRect(
        color = palette.bezelInner.scaleRgb(dimFactor),
        topLeft = Offset(bezelRect.left, bezelRect.top),
        size = Size(bezelRect.width, bezelRect.height),
        cornerRadius = CornerRadius(22f, 22f),
    )

    // STORM Aurora signature neon cyan-to-violet glowing bezel border
    if (skinId == GbcaSkinId.GB_STORM_AURORA || skinId == GbcaSkinId.GBC_STORM_AURORA || skinId == GbcaSkinId.GBA_STORM_AURORA) {
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF00F0FF).copy(alpha = 0.85f * dimFactor),
                    Color(0xFF9D4EDD).copy(alpha = 0.85f * dimFactor),
                ),
                startY = bezelRect.top,
                endY = bezelRect.bottom,
            ),
            topLeft = Offset(bezelRect.left, bezelRect.top),
            size = Size(bezelRect.width, bezelRect.height),
            cornerRadius = CornerRadius(22f, 22f),
            style = Stroke(width = 3.2f),
        )
    } else {
        drawRoundRect(
            color = palette.accent.copy(alpha = 0.45f * dimFactor),
            topLeft = Offset(bezelRect.left, bezelRect.top),
            size = Size(bezelRect.width, bezelRect.height),
            cornerRadius = CornerRadius(22f, 22f),
            style = Stroke(width = 2.2f),
        )
    }

    // DMG classic dual magenta + navy header stripes above screen
    if (skinId == GbcaSkinId.DMG_CLASSIC || skinId == GbcaSkinId.DMG_BLACK || skinId == GbcaSkinId.DMG_CLEAR) {
        val stripeY = bezelRect.top + padTop * 0.45f
        drawLine(
            color = Color(0xFF8B1D4E).copy(alpha = 0.85f * dimFactor),
            start = Offset(bezelRect.left + 16f, stripeY - 2.5f),
            end = Offset(bezelRect.right - 16f, stripeY - 2.5f),
            strokeWidth = 2.2f,
        )
        drawLine(
            color = Color(0xFF1E3A8A).copy(alpha = 0.85f * dimFactor),
            start = Offset(bezelRect.left + 16f, stripeY + 2.5f),
            end = Offset(bezelRect.right - 16f, stripeY + 2.5f),
            strokeWidth = 2.2f,
        )
    }

    // Power LED on left side of bezel (+ second amber charge LED for GBA SP)
    val ledCenter = Offset(bezelRect.left + padX * 0.46f, screenRect.top + screenRect.height * 0.35f)
    drawCircle(
        color = palette.powerLed.copy(alpha = 0.35f),
        radius = 10f,
        center = ledCenter,
    )
    drawCircle(
        color = palette.powerLed,
        radius = 4.5f,
        center = ledCenter,
    )
    if (skinId == GbcaSkinId.GBASP_COBALT || skinId == GbcaSkinId.GBASP_ONYX || skinId == GbcaSkinId.GBASP_NES_CLASSIC) {
        val led2 = Offset(ledCenter.x, ledCenter.y + 18f)
        drawCircle(color = Color(0xFFF59E0B).copy(alpha = 0.25f), radius = 7f, center = led2)
        drawCircle(color = Color(0xFFF59E0B).copy(alpha = 0.75f * dimFactor), radius = 3.2f, center = led2)
    }

    // GBC 5-color rainbow prism accent strip on bottom bezel
    if (skinId.system == ConsoleSystem.GBC && skinId != GbcaSkinId.GBC_STORM_AURORA) {
        val prismColors = listOf(Color(0xFFEF4444), Color(0xFFFACC15), Color(0xFF22C55E), Color(0xFF38BDF8), Color(0xFFA855F7))
        val stripW = 46f
        val startX = bezelRect.right - stripW - 18f
        val stripY = screenRect.bottom + padBot * 0.56f
        prismColors.forEachIndexed { idx, col ->
            drawLine(
                color = col.copy(alpha = 0.85f * dimFactor),
                start = Offset(startX + idx * 9f, stripY),
                end = Offset(startX + idx * 9f + 6f, stripY),
                strokeWidth = 3.5f,
            )
        }
    }

    // Speaker slits at bottom-right corner
    val slitStartX = w - 76f
    val slitStartY = h - 54f
    for (i in 0..4) {
        val dx = i * 11f
        drawLine(
            color = Color.Black.copy(alpha = 0.38f),
            start = Offset(slitStartX + dx, slitStartY + 14f),
            end = Offset(slitStartX + dx + 10f, slitStartY - 8f),
            strokeWidth = 3.5f,
        )
    }

    // Console branding label centered on bottom bezel
    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            isAntiAlias = true
            color = palette.brandText.copy(alpha = 0.85f * dimFactor).toArgb()
            textAlign = Paint.Align.CENTER
            textSize = (padBot * 0.40f).coerceIn(14f, 26f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            letterSpacing = 0.12f
        }
        canvas.nativeCanvas.drawText(
            palette.title,
            bezelRect.center.x,
            screenRect.bottom + padBot * 0.65f,
            paint,
        )
    }
}

private fun Color.scaleRgb(factor: Float): Color {
    return Color(
        red = (red * factor).coerceIn(0f, 1f),
        green = (green * factor).coerceIn(0f, 1f),
        blue = (blue * factor).coerceIn(0f, 1f),
        alpha = alpha,
    )
}
