package com.droidates.oneplus15wallpapers

import com.droidates.wallpapers.core.WallpaperApplication
import com.droidates.wallpapers.core.config.AppConfig
import dagger.hilt.android.HiltAndroidApp

/** Installs this app's [OnePlus15Spec] before any shared code reads AppConfig. */
@HiltAndroidApp
class WallpaperApp : WallpaperApplication() {
    override fun onCreate() {
        AppConfig.install(OnePlus15Spec)
        super.onCreate()
    }
}
