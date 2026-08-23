package com.droidates.xiaomi17ultrawallpapers

import com.droidates.wallpapers.core.WallpaperApplication
import com.droidates.wallpapers.core.config.AppConfig
import dagger.hilt.android.HiltAndroidApp

/** Installs this app's [Xiaomi17UltraSpec] before any shared code reads AppConfig. */
@HiltAndroidApp
class WallpaperApp : WallpaperApplication() {
    override fun onCreate() {
        AppConfig.install(Xiaomi17UltraSpec)
        super.onCreate()
    }
}
