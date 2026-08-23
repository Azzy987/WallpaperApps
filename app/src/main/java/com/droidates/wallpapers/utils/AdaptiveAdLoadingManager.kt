package com.droidates.wallpapers.utils

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages adaptive ad loading based on network conditions and device performance.
 * This class monitors network quality and device resources to optimize ad loading.
 */
@Singleton
class AdaptiveAdLoadingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val systemServiceCache: SystemServiceCache
) {
    companion object {
        private const val TAG = "AdaptiveAdLoading"
        
        // Memory thresholds
        private const val LOW_MEMORY_THRESHOLD = 0.2f // 20% free memory
        private const val MEDIUM_MEMORY_THRESHOLD = 0.35f // 35% free memory
        
        // Network quality thresholds (in Kbps)
        private const val POOR_NETWORK_THRESHOLD = 150 // 150 Kbps
        private const val MEDIUM_NETWORK_THRESHOLD = 700 // 700 Kbps
    }
    
    // Network quality levels
    enum class NetworkQuality {
        POOR, MEDIUM, GOOD, UNKNOWN
    }
    
    // Device performance levels
    enum class DevicePerformance {
        LOW, MEDIUM, HIGH, UNKNOWN
    }
    
    // Ad loading strategy based on conditions
    enum class AdLoadingStrategy {
        MINIMAL, // Only load essential ads with longer timeouts
        REDUCED, // Load fewer ads with standard timeouts
        STANDARD, // Default ad loading behavior
        AGGRESSIVE // Preload more ads with shorter timeouts
    }
    
    // Current states
    private val _networkQuality = MutableStateFlow(NetworkQuality.UNKNOWN)
    val networkQuality: StateFlow<NetworkQuality> = _networkQuality.asStateFlow()
    
    private val _devicePerformance = MutableStateFlow(DevicePerformance.UNKNOWN)
    val devicePerformance: StateFlow<DevicePerformance> = _devicePerformance.asStateFlow()
    
    private val _adLoadingStrategy = MutableStateFlow(AdLoadingStrategy.STANDARD)
    val adLoadingStrategy: StateFlow<AdLoadingStrategy> = _adLoadingStrategy.asStateFlow()
    
    // Network monitoring
    private val connectivityManager = systemServiceCache.getConnectivityManager()
    private val isMonitoring = AtomicBoolean(false)
    
    // Coroutine scope for background operations
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    init {
        // Initial assessment
        assessDevicePerformance()
        assessNetworkQuality()
        determineAdLoadingStrategy()
    }
    
    /**
     * Start monitoring network and device conditions
     */
    fun startMonitoring() {
        if (isMonitoring.getAndSet(true)) {
            return // Already monitoring
        }
        
        try {
            // Register network callback
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            
            connectivityManager?.registerNetworkCallback(networkRequest, networkCallback)
            
            // Initial assessments
            assessDevicePerformance()
            assessNetworkQuality()
            determineAdLoadingStrategy()
            
            Log.d(TAG, "Started monitoring network and device conditions")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting monitoring: ${e.message}")
            isMonitoring.set(false)
        }
    }
    
    /**
     * Stop monitoring network and device conditions
     */
    fun stopMonitoring() {
        if (!isMonitoring.getAndSet(false)) {
            return // Not monitoring
        }
        
        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
            Log.d(TAG, "Stopped monitoring network and device conditions")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping monitoring: ${e.message}")
        }
    }
    
    /**
     * Network callback to monitor changes in network conditions
     */
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            assessNetworkQuality(capabilities)
            determineAdLoadingStrategy()
        }
        
        override fun onLost(network: Network) {
            _networkQuality.value = NetworkQuality.POOR
            determineAdLoadingStrategy()
        }
    }
    
    /**
     * Assess current network quality
     */
    fun assessNetworkQuality(capabilities: NetworkCapabilities? = null) {
        coroutineScope.launch {
            try {
                val networkCapabilities = capabilities ?: connectivityManager?.activeNetwork?.let {
                    connectivityManager.getNetworkCapabilities(it)
                }
                
                if (networkCapabilities == null) {
                    _networkQuality.value = NetworkQuality.POOR
                    return@launch
                }
                
                // Check for WiFi or cellular
                val isWifi = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                val isCellular = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                
                // Get link download speed if available (API 29+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val downstreamBandwidthKbps = networkCapabilities.linkDownstreamBandwidthKbps
                    
                    _networkQuality.value = when {
                        downstreamBandwidthKbps <= POOR_NETWORK_THRESHOLD -> NetworkQuality.POOR
                        downstreamBandwidthKbps <= MEDIUM_NETWORK_THRESHOLD -> NetworkQuality.MEDIUM
                        else -> NetworkQuality.GOOD
                    }
                } else {
                    // For older Android versions, use transport type as a proxy for quality
                    _networkQuality.value = when {
                        isWifi -> NetworkQuality.GOOD
                        isCellular -> NetworkQuality.MEDIUM
                        else -> NetworkQuality.POOR
                    }
                }
                
                Log.d(TAG, "Network quality assessed: ${_networkQuality.value}")
            } catch (e: Exception) {
                Log.e(TAG, "Error assessing network quality: ${e.message}")
                _networkQuality.value = NetworkQuality.UNKNOWN
            }
        }
    }
    
    /**
     * Assess current device performance based on available memory
     */
    fun assessDevicePerformance() {
        coroutineScope.launch {
            try {
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val memoryInfo = ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(memoryInfo)
                
                val availableMemoryRatio = memoryInfo.availMem.toFloat() / memoryInfo.totalMem.toFloat()
                
                _devicePerformance.value = when {
                    memoryInfo.lowMemory || availableMemoryRatio <= LOW_MEMORY_THRESHOLD -> DevicePerformance.LOW
                    availableMemoryRatio <= MEDIUM_MEMORY_THRESHOLD -> DevicePerformance.MEDIUM
                    else -> DevicePerformance.HIGH
                }
                
                Log.d(TAG, "Device performance assessed: ${_devicePerformance.value} (Available memory: ${availableMemoryRatio * 100}%)")
            } catch (e: Exception) {
                Log.e(TAG, "Error assessing device performance: ${e.message}")
                _devicePerformance.value = DevicePerformance.UNKNOWN
            }
        }
    }
    
    /**
     * Determine the optimal ad loading strategy based on current conditions
     */
    fun determineAdLoadingStrategy() {
        val networkQuality = _networkQuality.value
        val devicePerformance = _devicePerformance.value
        
        _adLoadingStrategy.value = when {
            // Poor conditions - minimal ad loading
            networkQuality == NetworkQuality.POOR && devicePerformance == DevicePerformance.LOW -> {
                AdLoadingStrategy.MINIMAL
            }
            // One poor condition - reduced ad loading
            networkQuality == NetworkQuality.POOR || devicePerformance == DevicePerformance.LOW -> {
                AdLoadingStrategy.REDUCED
            }
            // Good conditions - standard ad loading
            networkQuality == NetworkQuality.GOOD && devicePerformance == DevicePerformance.HIGH -> {
                AdLoadingStrategy.STANDARD
            }
            // Default to standard loading
            else -> {
                AdLoadingStrategy.STANDARD
            }
        }
        
        Log.d(TAG, "Ad loading strategy determined: ${_adLoadingStrategy.value} " +
                "(Network: $networkQuality, Device: $devicePerformance)")
    }
    
    /**
     * Get the recommended ad timeout based on current conditions
     */
    fun getRecommendedAdTimeout(): Long {
        return when (_adLoadingStrategy.value) {
            AdLoadingStrategy.MINIMAL -> 20000L // 20 seconds
            AdLoadingStrategy.REDUCED -> 15000L // 15 seconds
            AdLoadingStrategy.STANDARD -> 10000L // 10 seconds
            AdLoadingStrategy.AGGRESSIVE -> 7000L // 7 seconds
        }
    }
    
    /**
     * Get the recommended retry count based on current conditions
     */
    fun getRecommendedRetryCount(): Int {
        return when (_adLoadingStrategy.value) {
            AdLoadingStrategy.MINIMAL -> 1
            AdLoadingStrategy.REDUCED -> 2
            AdLoadingStrategy.STANDARD -> 3
            AdLoadingStrategy.AGGRESSIVE -> 4
        }
    }
    
    /**
     * Get the recommended retry delay based on current conditions
     */
    fun getRecommendedRetryDelay(): Long {
        return when (_adLoadingStrategy.value) {
            AdLoadingStrategy.MINIMAL -> 5000L // 5 seconds
            AdLoadingStrategy.REDUCED -> 4000L // 4 seconds
            AdLoadingStrategy.STANDARD -> 3000L // 3 seconds
            AdLoadingStrategy.AGGRESSIVE -> 2000L // 2 seconds
        }
    }
    
    /**
     * Get the recommended preload cooldown based on current conditions
     */
    fun getRecommendedPreloadCooldown(): Long {
        return when (_adLoadingStrategy.value) {
            AdLoadingStrategy.MINIMAL -> 120000L // 2 minutes
            AdLoadingStrategy.REDUCED -> 90000L // 1.5 minutes
            AdLoadingStrategy.STANDARD -> 60000L // 1 minute
            AdLoadingStrategy.AGGRESSIVE -> 30000L // 30 seconds
        }
    }
    
    /**
     * Get the recommended cache expiry time based on current conditions
     */
    fun getRecommendedCacheExpiryTime(): Long {
        return when (_adLoadingStrategy.value) {
            AdLoadingStrategy.MINIMAL -> 15 * 60 * 1000L // 15 minutes
            AdLoadingStrategy.REDUCED -> 20 * 60 * 1000L // 20 minutes
            AdLoadingStrategy.STANDARD -> 30 * 60 * 1000L // 30 minutes
            AdLoadingStrategy.AGGRESSIVE -> 45 * 60 * 1000L // 45 minutes
        }
    }
    
    /**
     * Should preload ads based on current conditions
     */
    fun shouldPreloadAds(): Boolean {
        return _adLoadingStrategy.value != AdLoadingStrategy.MINIMAL
    }
    
    /**
     * Get the recommended number of ads to preload based on current conditions
     */
    fun getRecommendedPreloadCount(): Int {
        return when (_adLoadingStrategy.value) {
            AdLoadingStrategy.MINIMAL -> 0
            AdLoadingStrategy.REDUCED -> 1
            AdLoadingStrategy.STANDARD -> 2
            AdLoadingStrategy.AGGRESSIVE -> 3
        }
    }
    
    /**
     * Check if the device has low memory (less than 20% free memory)
     * This is used to determine if we should reduce ad loading
     */
    fun isLowMemoryDevice(): Boolean {
        val activityManager = systemServiceCache.getActivityManager() ?: return false
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        val availableMemory = memoryInfo.availMem.toFloat()
        val totalMemory = memoryInfo.totalMem.toFloat()
        val memoryRatio = availableMemory / totalMemory
        
        return memoryRatio < LOW_MEMORY_THRESHOLD
    }
}
