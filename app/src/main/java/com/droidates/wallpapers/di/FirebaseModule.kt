package com.droidates.wallpapers.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
// FirebaseRemoteConfig import removed
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    // OPTIMIZATION: Firebase instances initialized lazily via Dagger.Lazy<T>
    // When injected as Lazy<FirebaseFirestore>, they won't initialize until .get() is called
    // This prevents blocking app startup with Firebase initialization
    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore {
        return FirebaseFirestore.getInstance()
    }

    // RemoteConfig provider removed

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        return FirebaseAuth.getInstance()
    }
} 