package com.droidates.wallpapers.utils

import android.content.Context
import android.util.Log
import androidx.startup.Initializer
import com.droidates.wallpapers.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "OptimizedAppInitializer"

/**
 * Optimized App Initializer using App Startup library
 * Handles critical initialization tasks with minimal main thread impact
 */
@Singleton
class OptimizedAppInitializer @Inject constructor() {
    
    private val isInitialized = AtomicBoolean(false)
    
    // Optimized single-threaded executor for sequential critical tasks
    private val criticalTaskExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "CriticalInit").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY
        }
    }
    
    // Background scope for non-critical tasks
    private val backgroundScope = CoroutineScope(
        SupervisorJob() + 
        Dispatchers.IO.limitedParallelism(2) + // Limit parallelism to reduce resource usage
        CoroutineExceptionHandler { _, throwable ->
            if (throwable !is CancellationException) {
                Log.e(TAG, "Background initialization error: ${throwable.message}", throwable)
            }
        }
    )
    
    /**
     * Initialize critical components immediately with minimal main thread impact
     */
    fun initializeCriticalComponents(context: Context) {
        if (isInitialized.getAndSet(true)) {
            Log.d(TAG, "Already initialized, skipping")
            return
        }
        
        Log.d(TAG, "Starting optimized initialization")
        
        // Submit critical tasks to single-threaded executor to avoid overwhelming the system
        criticalTaskExecutor.execute {
            try {
                initializeFirebaseSync(context)
                
                // Schedule non-critical tasks after a delay
                backgroundScope.launch {
                    delay(2000) // 2 second delay to ensure UI is responsive
                    initializeNonCriticalComponents(context)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Critical initialization failed", e)
            }
        }
    }
    
    /**
     * Initialize Firebase synchronously on background thread
     */
    private fun initializeFirebaseSync(context: Context) {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
                Log.d(TAG, "Firebase initialized on background thread")
            } else {
                Log.d(TAG, "Firebase already initialized")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Firebase initialization failed", e)
        }
    }
    
    /**
     * Initialize non-critical components with proper error handling
     */
    private suspend fun initializeNonCriticalComponents(context: Context) {
        try {
            // Initialize FCM topic subscription
            withTimeoutOrNull(10000) { // 10 second timeout
                suspendCancellableCoroutine<Unit> { continuation ->
                    FirebaseMessaging.getInstance().subscribeToTopic("all_users")
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                Log.d(TAG, "FCM topic subscription successful")
                            } else {
                                Log.w(TAG, "FCM topic subscription failed", task.exception)
                            }
                            continuation.resume(Unit, null)
                        }
                }
            }
            
            // Get FCM token only in debug builds
            if (BuildConfig.DEBUG) {
                withTimeoutOrNull(5000) {
                    suspendCancellableCoroutine<String?> { continuation ->
                        FirebaseMessaging.getInstance().token
                            .addOnCompleteListener { task ->
                                val token = if (task.isSuccessful) task.result else null
                                token?.let { Log.d(TAG, "FCM Token: $it") }
                                continuation.resume(token, null)
                            }
                    }
                }
            }
            
            Log.d(TAG, "Non-critical initialization completed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Non-critical initialization error", e)
        }
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        try {
            backgroundScope.cancel()
            criticalTaskExecutor.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup error", e)
        }
    }
}

/**
 * App Startup Initializer for Firebase
 */
class FirebaseInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        Log.d(TAG, "FirebaseInitializer.create() called")
        
        // Only do minimal Firebase initialization here
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
                Log.d(TAG, "Firebase initialized via App Startup")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Firebase initialization failed in App Startup", e)
        }
    }
    
    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
} 