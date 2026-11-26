package com.stormgbca.emulator.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun WatermelonMark(
    modifier: Modifier = Modifier,
    height: Dp = 26.dp,
) {
    Canvas(modifier = modifier.size(width = height * 22f / 24f, height = height)) {
        val u = size.height / 24f
        val fullW = 22f * u
        val fullH = 24f * u

        val shellBrush = Brush.linearGradient(
            colors = listOf(Color(0xFF00E5FF), Color(0xFF3B82F6), Color(0xFF7B2CBF)),
            start = Offset(0f, 0f),
            end = Offset(fullW, fullH),
        )
        // Handheld Cartridge/Console Body
        drawRoundRect(
            brush = shellBrush,
            topLeft = Offset(1.5f * u, 0.5f * u),
            size = Size(fullW - 3f * u, fullH - 1f * u),
            cornerRadius = CornerRadius(3.2f * u, 3.2f * u),
        )
        // Recessed LCD screen bezel
        drawRoundRect(
            color = Color(0xFF0B0E17),
            topLeft = Offset(3.4f * u, 2.5f * u),
            size = Size(fullW - 6.8f * u, 11.2f * u),
            cornerRadius = CornerRadius(1.6f * u, 1.6f * u),
        )
        drawRoundRect(
            color = Color(0xFF00E5FF),
            topLeft = Offset(4.4f * u, 3.4f * u),
            size = Size(fullW - 8.8f * u, 9.4f * u),
            cornerRadius = CornerRadius(1.0f * u, 1.0f * u),
            style = Stroke(width = 0.7f * u),
        )
        // STORM Lightning Spark inside screen
        val cx = fullW * 0.5f
        val top = 4.5f * u
        val lightningPath = Path().apply {
            moveTo(cx + 1.1f * u, top)
            lineTo(cx - 2.0f * u, top + 3.9f * u)
            lineTo(cx + 0.1f * u, top + 3.9f * u)
            lineTo(cx - 1.1f * u, top + 7.4f * u)
            lineTo(cx + 2.2f * u, top + 3.1f * u)
            lineTo(cx + 0.1f * u, top + 3.1f * u)
            close()
        }
        drawPath(path = lightningPath, color = Color.White)

        // D-Pad + A/B dots at bottom deck
        drawCircle(color = Color(0xFF0B0E17), radius = 1.6f * u, center = Offset(6.2f * u, 18.0f * u))
        drawCircle(color = Color(0xFFFF2E55), radius = 1.15f * u, center = Offset(15.8f * u, 17.2f * u))
        drawCircle(color = Color(0xFFFF2E55), radius = 1.15f * u, center = Offset(13.2f * u, 19.3f * u))
    }
}
