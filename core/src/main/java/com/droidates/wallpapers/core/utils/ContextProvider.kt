package com.droidates.wallpapers.core.utils

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton class that provides application context to ViewModels and other classes
 * that need context but can't directly inject it.
 */
@Singleton
class ContextProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Returns the application context
     */
    fun getContext(): Context {
        return context
    }
} 