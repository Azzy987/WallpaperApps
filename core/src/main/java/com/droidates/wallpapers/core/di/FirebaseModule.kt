package com.droidates.wallpapers.core.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
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
        return FirebaseFirestore.getInstance().apply {
            // COST: wallpaper browsing is overwhelmingly repeat reads of documents that
            // rarely change, and every cache miss is a billed read. Persistence is on by
            // default but capped at 100MB, after which Firestore evicts and those reads
            // start being billed again.
            //
            // The catalogue is small (documents are metadata; the images live in Storage
            // and never touch this cache), so an unbounded cache costs little on device
            // and keeps repeat browsing free. Queries still hit the server when a
            // listener or an explicit Source.SERVER asks them to.
            firestoreSettings = firestoreSettings {
                setLocalCacheSettings(
                    persistentCacheSettings {
                        setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                    }
                )
            }
        }
    }

    // RemoteConfig provider removed

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        return FirebaseAuth.getInstance()
    }
} 