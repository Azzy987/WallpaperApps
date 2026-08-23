package com.droidates.samsungfold8wallpapers

import com.droidates.wallpapers.core.WallpaperApplication
import com.droidates.wallpapers.core.config.AppConfig
import dagger.hilt.android.HiltAndroidApp

/** Installs this app's [SamsungFold8Spec] before any shared code reads AppConfig. */
@HiltAndroidApp
class WallpaperApp : WallpaperApplication() {
    override fun onCreate() {
        AppConfig.install(SamsungFold8Spec)
        super.onCreate()
    }
}
