package com.droidates.wallpapers.di

import android.content.Context
import androidx.navigation.NavHostController
import com.droidates.wallpapers.navigation.NavigationState
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object NavigationModule {
    @Provides
    @Singleton
    fun provideNavigationState(
        @ApplicationContext context: Context
    ): NavigationState {
        val navController = NavHostController(context)
        val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        return NavigationState(navController, coroutineScope)
    }
} 