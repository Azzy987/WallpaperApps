package com.droidates.wallpapers.di

import android.content.Context
import com.droidates.wallpapers.data.repository.AuthRepository
import com.droidates.wallpapers.utils.AdManager
import com.droidates.wallpapers.utils.AdaptiveAdLoadingManager
import com.droidates.wallpapers.utils.CircuitBreakerManager
import com.droidates.wallpapers.utils.NavigationPredictionManager
import com.droidates.wallpapers.utils.PreferencesManager
import com.droidates.wallpapers.utils.SystemServiceCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AdModule {
    
    @Provides
    @Singleton
    fun provideAdManager(
        @ApplicationContext context: Context,
        authRepository: AuthRepository,
        adaptiveAdLoadingManager: AdaptiveAdLoadingManager,
        navigationPredictionManager: NavigationPredictionManager,
        circuitBreakerManager: CircuitBreakerManager
    ): AdManager = AdManager(context, authRepository, adaptiveAdLoadingManager, navigationPredictionManager, circuitBreakerManager)
    
    @Provides
    @Singleton
    fun provideAdaptiveAdLoadingManager(
        @ApplicationContext context: Context,
        systemServiceCache: SystemServiceCache
    ): AdaptiveAdLoadingManager = AdaptiveAdLoadingManager(context, systemServiceCache)
    
    @Provides
    @Singleton
    fun provideNavigationPredictionManager(
        @ApplicationContext context: Context,
        preferencesManager: PreferencesManager
    ): NavigationPredictionManager = NavigationPredictionManager(context, preferencesManager)
    
    @Provides
    @Singleton
    fun provideCircuitBreakerManager(
        @ApplicationContext context: Context,
        preferencesManager: PreferencesManager
    ): CircuitBreakerManager = CircuitBreakerManager(context, preferencesManager)
} 