package com.droidates.wallpapers.core.utils

import android.content.Context
import androidx.startup.Initializer
import com.google.firebase.FirebaseApp

/**
 * AndroidX Startup initializer referenced from the manifest.
 *
 * Keep this extremely light: just ensure Firebase is initialized.
 * Any heavy work should remain deferred (WorkManager / lazy DI).
 */
class FirebaseInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        // Safe to call multiple times; Firebase will no-op when already initialized.
        runCatching { FirebaseApp.initializeApp(context) }
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}

