package com.stormgbca.emulator

import android.app.Application
import com.stormgbca.emulator.data.GbcaPrefs
import com.stormgbca.emulator.ui.theme.AppThemeManager

class StormGbcaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        GbcaPrefs.init(this)
        AppThemeManager.init(this)
    }
}
