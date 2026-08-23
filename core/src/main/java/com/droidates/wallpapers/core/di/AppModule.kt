package com.droidates.wallpapers.core.di

import android.content.Context
import com.droidates.wallpapers.core.data.repository.AuthRepository
import com.droidates.wallpapers.core.data.repository.BillingRepository
import com.droidates.wallpapers.core.utils.NetworkUtils
import com.droidates.wallpapers.core.utils.SystemServiceCache
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.Lazy
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context {
        return context
    }

    
    @Provides
    @Singleton
    fun provideAuthRepository(
        @ApplicationContext context: Context,
        firebaseAuth: Lazy<FirebaseAuth>, // Use Lazy to defer initialization
        firestore: Lazy<FirebaseFirestore> // Use Lazy to defer initialization
    ): AuthRepository {
        return AuthRepository(context, firebaseAuth.get(), firestore.get())
    }
    
    @Provides
    @Singleton
    fun provideBillingRepository(
        @ApplicationContext context: Context,
        authRepository: Lazy<AuthRepository> // Use Lazy to defer initialization
    ): BillingRepository {
        return BillingRepository(context, authRepository.get())
    }
    
    @Provides
    @Singleton
    fun provideNetworkUtils(
        @ApplicationContext context: Context,
        systemServiceCache: SystemServiceCache
    ): NetworkUtils {
        return NetworkUtils(context, systemServiceCache)
    }
} 