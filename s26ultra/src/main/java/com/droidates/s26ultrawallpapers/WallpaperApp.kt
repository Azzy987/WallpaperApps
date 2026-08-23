package com.droidates.s26ultrawallpapers

import com.droidates.wallpapers.core.WallpaperApplication
import com.droidates.wallpapers.core.config.AppConfig
import dagger.hilt.android.HiltAndroidApp

/** Installs this app's [S26UltraSpec] before any shared code reads AppConfig. */
@HiltAndroidApp
class WallpaperApp : WallpaperApplication() {
    override fun onCreate() {
        AppConfig.install(S26UltraSpec)
        super.onCreate()
    }
}
