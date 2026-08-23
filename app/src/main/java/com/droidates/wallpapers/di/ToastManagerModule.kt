package com.droidates.wallpapers.di

import com.droidates.wallpapers.ui.components.ToastManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ToastManagerModule {
    
    @Provides
    @Singleton
    fun provideToastManager(): ToastManager {
        return ToastManager()
    }
} 