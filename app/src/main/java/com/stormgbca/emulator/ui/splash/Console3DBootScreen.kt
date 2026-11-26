package com.stormgbca.emulator.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.stormgbca.emulator.R
import com.stormgbca.emulator.ui.theme.SpaceGrotesk
import com.stormgbca.emulator.ui.theme.WatermelonMono
import kotlinx.coroutines.delay

@Composable
fun Console3DBootScreen(
    statusText: String = stringResource(R.string.boot_initializing),
    onFinished: () -> Unit,
) {
    val revealAnim = remember { Animatable(0f) }
    val fadeOutAnim = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        revealAnim.animateTo(1f, animationSpec = tween(750, easing = FastOutSlowInEasing))
        delay(950)
        fadeOutAnim.animateTo(0f, animationSpec = tween(350, easing = FastOutSlowInEasing))
        onFinished()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "gbca_3d_boot")
    val spinY by infiniteTransition.animateFloat(
        initialValue = -16f,
        targetValue = 16f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "spin_y",
    )
    val tiltX by infiniteTransition.animateFloat(
        initialValue = 6f,
        targetValue = -4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "tilt_x",
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "led_pulse",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(fadeOutAnim.value)
            .clickable { onFinished() }
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF131D36),
                        Color(0xFF080C16),
                        Color(0xFF04050A),
                    ),
                    radius = 1200f,
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp),
        ) {
            // 3D Rotating GBA + GB/GBC Console Silhouette
            Box(
                modifier = Modifier
                    .width(304.dp)
                    .height(178.dp)
                    .graphicsLayer {
                        rotationY = spinY
                        rotationX = tiltX
                        scaleX = 0.85f + 0.15f * revealAnim.value
                        scaleY = 0.85f + 0.15f * revealAnim.value
                        cameraDistance = 14f * density
                    }
                    .shadow(28.dp, RoundedCornerShape(38.dp), spotColor = Color(0xFF00E5FF))
                    .clip(RoundedCornerShape(38.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF312E81), Color(0xFF1E1B4B), Color(0xFF0F172A)),
                            start = Offset(0f, 0f),
                            end = Offset(304f, 178f),
                        )
                    )
                    .border(
                        width = 1.8.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFF00E5FF), Color(0xFF7B2CBF), Color(0xFF38BDF8)),
                        ),
                        shape = RoundedCornerShape(38.dp),
                    )
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Left D-Pad + Start/Select
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.width(52.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0B0E17))
                                .border(1.dp, Color(0x5500E5FF), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Canvas(modifier = Modifier.size(26.dp)) {
                                val arm = size.width * 0.34f
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                drawRoundRect(Color(0xFF94A3B8), Offset(cx - arm / 2f, 0f), Size(arm, size.height), CornerRadius(3f, 3f))
                                drawRoundRect(Color(0xFF94A3B8), Offset(0f, cy - arm / 2f), Size(size.width, arm), CornerRadius(3f, 3f))
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Box(Modifier.size(width = 20.dp, height = 5.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF475569)))
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.size(width = 20.dp, height = 5.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF475569)))
                    }

                    // Center Recessed LCD Screen with STORM Lightning Glow
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(132.dp)
                            .padding(horizontal = 10.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF06080F))
                            .border(1.4.dp, Color(0xFF00E5FF).copy(alpha = 0.65f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            drawRect(
                                brush = Brush.linearGradient(
                                    colors = listOf(Color(0xFF091B34), Color(0xFF141132), Color(0xFF050A14)),
                                )
                            )
                            val cx = w / 2f
                            val cy = h / 2f
                            val bolt = Path().apply {
                                moveTo(cx + 8f, cy - 30f)
                                lineTo(cx - 18f, cy + 2f)
                                lineTo(cx - 2f, cy + 2f)
                                lineTo(cx - 9f, cy + 30f)
                                lineTo(cx + 18f, cy - 3f)
                                lineTo(cx + 2f, cy - 3f)
                                close()
                            }
                            drawPath(
                                path = bolt,
                                brush = Brush.linearGradient(
                                    colors = listOf(Color(0xFF00E5FF), Color(0xFFFFFFFF), Color(0xFFA855F7)),
                                ),
                            )
                            drawPath(
                                path = bolt,
                                color = Color(0xFF00E5FF).copy(alpha = 0.55f * pulse),
                                style = Stroke(width = 2.5f),
                            )
                        }
                        Text(
                            text = "GAME BOY  |  GBC  |  GBA",
                            color = Color(0xFF94A3B8),
                            fontFamily = WatermelonMono,
                            fontSize = 8.sp,
                            letterSpacing = 0.9.sp,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 6.dp),
                        )
                    }

                    // Right A/B + Power LED
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.width(52.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E676).copy(alpha = pulse))
                                .shadow(6.dp, CircleShape, spotColor = Color(0xFF00E676)),
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00E5FF).copy(alpha = 0.25f))
                                    .border(1.2.dp, Color(0xFF00E5FF), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("B", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFA855F7).copy(alpha = 0.30f))
                                    .border(1.2.dp, Color(0xFFA855F7), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("A", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            Text(
                text = "STORM GBCA",
                color = Color.White,
                fontFamily = SpaceGrotesk,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.2.sp,
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = statusText,
                color = Color(0xFF94A3B8),
                fontFamily = WatermelonMono,
                fontSize = 11.sp,
            )

            Spacer(Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = revealAnim.value,
                color = Color(0xFF00E5FF),
                backgroundColor = Color(0xFF1E293B),
                modifier = Modifier
                    .width(200.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
            )
        }
    }
}
