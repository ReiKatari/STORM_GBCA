package com.stormgbca.emulator.ui.theme

import androidx.appcompat.app.AppCompatDelegate
import com.stormgbca.emulator.R

enum class Theme(val nightMode: Int, val preferenceValue: String, val displayNameRu: String, val displayNameEn: String) {
    STORM_NIGHT(AppCompatDelegate.MODE_NIGHT_YES, "storm_night", "STORM Ночь", "STORM Night"),
    STORM_DAY(AppCompatDelegate.MODE_NIGHT_NO, "storm_day", "STORM День", "STORM Day"),
    MIDNIGHT(AppCompatDelegate.MODE_NIGHT_YES, "midnight", "Полночь", "Midnight"),
    CYBERPUNK(AppCompatDelegate.MODE_NIGHT_YES, "cyberpunk", "Киберпанк", "Cyberpunk"),
    GOTHIC(AppCompatDelegate.MODE_NIGHT_YES, "gothic", "Готика", "Gothic"),
    MATRIX(AppCompatDelegate.MODE_NIGHT_YES, "matrix", "Матрица", "Matrix"),
    SOLAR(AppCompatDelegate.MODE_NIGHT_YES, "solar", "Солнечная", "Solar"),
    SAKURA(AppCompatDelegate.MODE_NIGHT_NO, "sakura", "Сакура", "Sakura"),
    CLASSIC_OLED(AppCompatDelegate.MODE_NIGHT_YES, "classic_oled", "Классический OLED", "Classic OLED");

    fun getThemeResId(): Int = when (this) {
        STORM_NIGHT -> R.style.AppTheme_Dark
        STORM_DAY -> R.style.AppTheme_Light
        MIDNIGHT -> R.style.AppTheme_Midnight
        CYBERPUNK -> R.style.AppTheme_Cyberpunk
        GOTHIC -> R.style.AppTheme_Gothic
        MATRIX -> R.style.AppTheme_Matrix
        SOLAR -> R.style.AppTheme_Solar
        SAKURA -> R.style.AppTheme_Sakura
        CLASSIC_OLED -> R.style.AppTheme_ClassicOled
    }

    companion object {
        fun fromPreference(value: String?): Theme {
            return when (value?.lowercase()?.trim()) {
                "dark", "storm_night" -> STORM_NIGHT
                "light", "storm_day" -> STORM_DAY
                "midnight" -> MIDNIGHT
                "cyberpunk" -> CYBERPUNK
                "gothic" -> GOTHIC
                "matrix" -> MATRIX
                "solar" -> SOLAR
                "sakura" -> SAKURA
                "classic_oled", "oled" -> CLASSIC_OLED
                else -> STORM_NIGHT
            }
        }
    }
}
