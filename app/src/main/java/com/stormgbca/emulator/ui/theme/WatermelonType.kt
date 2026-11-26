package com.stormgbca.emulator.ui.theme

import androidx.compose.material.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.stormgbca.emulator.R

val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Normal),
    Font(R.font.space_grotesk, FontWeight.Medium),
    Font(R.font.space_grotesk, FontWeight.SemiBold),
    Font(R.font.space_grotesk, FontWeight.Bold),
    Font(R.font.space_grotesk, FontWeight.ExtraBold),
)

val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)

val WatermelonMono = FontFamily.Monospace

val StormTypography = Typography(
    defaultFontFamily = Manrope,
    h4 = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 24.sp),
    h5 = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 20.sp),
    h6 = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    subtitle1 = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    body1 = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    body2 = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    caption = TextStyle(fontFamily = WatermelonMono, fontWeight = FontWeight.Medium, fontSize = 11.sp),
)
