package com.droidates.iphone17wallpapers

import com.droidates.wallpapers.core.WallpaperApplication
import com.droidates.wallpapers.core.config.AppConfig
import dagger.hilt.android.HiltAndroidApp

/** Installs this app's [IPhone17Spec] before any shared code reads AppConfig. */
@HiltAndroidApp
class WallpaperApp : WallpaperApplication() {
    override fun onCreate() {
        AppConfig.install(IPhone17Spec)
        super.onCreate()
    }
}
