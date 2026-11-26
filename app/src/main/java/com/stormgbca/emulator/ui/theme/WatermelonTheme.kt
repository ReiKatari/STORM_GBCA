package com.stormgbca.emulator.ui.theme

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material.Colors
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toArgb
import androidx.preference.PreferenceManager

object AppThemeManager {
    const val DEFAULT_ACCENT_KEY = "theme_default"

    val themeState = mutableStateOf(Theme.STORM_NIGHT)
    val accentColorState = mutableStateOf(DEFAULT_ACCENT_KEY)
    val cardStyleState = mutableStateOf("3d_boxart")

    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    fun init(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        themeState.value = Theme.fromPreference(prefs.getString("theme", "storm_night"))
        accentColorState.value = prefs.getString("theme_accent_color", DEFAULT_ACCENT_KEY) ?: DEFAULT_ACCENT_KEY
        cardStyleState.value = prefs.getString("rom_card_style", "3d_boxart") ?: "3d_boxart"
        applyNightMode(themeState.value)

        if (listener == null) {
            val l = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
                when (key) {
                    "theme" -> {
                        val t = Theme.fromPreference(sp.getString("theme", "storm_night"))
                        themeState.value = t
                        applyNightMode(t)
                    }
                    "theme_accent_color" -> {
                        accentColorState.value = sp.getString("theme_accent_color", DEFAULT_ACCENT_KEY) ?: DEFAULT_ACCENT_KEY
                    }
                    "rom_card_style" -> {
                        cardStyleState.value = sp.getString("rom_card_style", "3d_boxart") ?: "3d_boxart"
                    }
                }
            }
            listener = l
            prefs.registerOnSharedPreferenceChangeListener(l)
        }
    }

    private fun applyNightMode(theme: Theme) {
        if (AppCompatDelegate.getDefaultNightMode() != theme.nightMode) {
            AppCompatDelegate.setDefaultNightMode(theme.nightMode)
        }
    }

    fun isDarkTheme(): Boolean = baseWatermelonColorsFor(themeState.value).isDark

    fun getCurrentWatermelonColors(): WatermelonColors {
        val base = baseWatermelonColorsFor(themeState.value)
        return getCustomizedAccentColors(base, accentColorState.value)
    }

    fun getAccentColor(): Int = getCurrentWatermelonColors().green.toArgb()

    fun updateTheme(context: Context, theme: Theme) {
        themeState.value = theme
        applyNightMode(theme)
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            .edit()
            .putString("theme", theme.preferenceValue)
            .apply()
    }

    fun updateAccent(context: Context, accentKey: String) {
        accentColorState.value = accentKey
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            .edit()
            .putString("theme_accent_color", accentKey)
            .apply()
    }

    fun updateCardStyle(context: Context, style: String) {
        cardStyleState.value = style
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
            .edit()
            .putString("rom_card_style", style)
            .apply()
    }

    fun applyEdgeToEdgeTheme(activity: ComponentActivity) {
        init(activity)
        activity.setTheme(themeState.value.getThemeResId())
        val isLight = !isDarkTheme()
        if (isLight) {
            activity.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            )
        } else {
            activity.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            )
        }
    }
}

val watermelon: WatermelonColors
    @Composable get() = LocalWatermelonColors.current

private fun watermelonMaterialColors(colors: WatermelonColors): Colors {
    val onAccent = if (colors.green.red * 0.299f + colors.green.green * 0.587f + colors.green.blue * 0.114f > 0.55f) {
        androidx.compose.ui.graphics.Color(0xFF0A0A0F)
    } else {
        androidx.compose.ui.graphics.Color.White
    }
    return if (colors.isDark) {
        darkColors(
            primary = colors.green,
            primaryVariant = colors.greenDim,
            secondary = colors.green,
            secondaryVariant = colors.greenDim,
            background = colors.bg,
            surface = colors.surface,
            error = colors.red,
            onPrimary = onAccent,
            onSecondary = onAccent,
            onBackground = colors.text,
            onSurface = colors.text,
            onError = androidx.compose.ui.graphics.Color.White,
        )
    } else {
        lightColors(
            primary = colors.green,
            primaryVariant = colors.greenDim,
            secondary = colors.green,
            secondaryVariant = colors.greenDim,
            background = colors.bg,
            surface = colors.surface,
            error = colors.red,
            onPrimary = onAccent,
            onSecondary = onAccent,
            onBackground = colors.text,
            onSurface = colors.text,
            onError = androidx.compose.ui.graphics.Color.White,
        )
    }
}

@Composable
fun StormWatermelonTheme(content: @Composable () -> Unit) {
    val theme = AppThemeManager.themeState.value
    val accentKey = AppThemeManager.accentColorState.value
    val watermelonColors = getCustomizedAccentColors(baseWatermelonColorsFor(theme), accentKey)

    CompositionLocalProvider(
        LocalWatermelonColors provides watermelonColors,
        LocalContentColor provides watermelonColors.text,
    ) {
        MaterialTheme(
            colors = watermelonMaterialColors(watermelonColors),
            typography = StormTypography,
        ) {
            content()
        }
    }
}
