package com.droidates.wallpapers.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "NetworkUtils"
private const val VERBOSE_LOGGING = false // Set to false to reduce logs

// OPTIMIZATION: Shared preferences key for caching network state
private const val PREF_NAME = "network_utils_prefs"
private const val KEY_LAST_CONNECTED_STATE = "last_connected_state"
private const val KEY_LAST_CONNECTION_QUALITY = "last_connection_quality"
private const val KEY_LAST_CHECK_TIME = "last_check_time"

/**
 * Connection quality enum
 */
enum class ConnectionQuality {
    UNKNOWN, POOR, MODERATE, GOOD, EXCELLENT
}

/**
 * Utility class to handle network connectivity checks and status updates
 */
@Singleton
class NetworkUtils @Inject constructor(
    private val context: Context,
    private val systemServiceCache: SystemServiceCache
) {
    // OPTIMIZATION: Use SystemServiceCache instead of directly calling getSystemService()
    private val connectivityManager by lazy { 
        systemServiceCache.getConnectivityManager() 
            ?: context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager 
    }
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val _isConnected = MutableStateFlow(isNetworkAvailable())
    val isConnected = _isConnected.asStateFlow()
    
    // Always set to false to disable slow connection notification
    private val _isSlowConnection = MutableStateFlow(false)
    val isSlowConnection = _isSlowConnection.asStateFlow()
    
    private val _connectionQuality = MutableStateFlow(ConnectionQuality.UNKNOWN)
    val connectionQuality = _connectionQuality.asStateFlow()
    
    // Threshold for slow connection in ms
    private val slowConnectionThreshold = 2000L
    
    // Network callback to monitor connection changes
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val wasConnected = _isConnected.value
            _isConnected.value = true
            
            // Only log important state changes
            if (!wasConnected) {
                Log.d(TAG, "Network available - connection restored")
            } else if (VERBOSE_LOGGING) {
                Log.d(TAG, "Network available")
            }
            
            checkConnectionQuality()
        }
        
        override fun onLost(network: Network) {
            val wasConnected = _isConnected.value
            _isConnected.value = false
            _isSlowConnection.value = false
            _connectionQuality.value = ConnectionQuality.UNKNOWN
            
            // Always log connection loss as it's important
            if (wasConnected) {
                Log.d(TAG, "Network lost - connection interrupted")
            }
        }
        
        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            if (VERBOSE_LOGGING) {
                Log.d(TAG, "Network capabilities changed")
            }
            checkConnectionQuality(networkCapabilities)
        }
    }
    
    init {
        // Move registration to explicit initialization method to prevent slowdowns during app restart
        // This allows the app to initialize faster when reopened after clearing from recents
    }
    
    /**
     * Initialize network tracking explicitly
     * This method should be called early in the application lifecycle,
     * but moved out of init block to prevent automatic initialization that slows app startup
     */
    fun initializeNetworkTracking() {
        try {
            // OPTIMIZATION: First load cached network state to avoid UI flicker during startup
            loadCachedNetworkState()
            
            // Then check if network is available immediately without callbacks
            val currentNetworkState = isNetworkAvailable()
            _isConnected.value = currentNetworkState
            
            // Save the current state for future app launches
            saveNetworkState(currentNetworkState)
            
            // Register callbacks on background thread with a slight delay
            // to prevent ConnectivityManager$TooManyRequestsException during startup
            coroutineScope.launch {
                delay(500) // Short delay to prevent overloading ConnectivityManager
                registerNetworkCallback()
                monitorConnectionSpeed()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing network tracking: ${e.message}")
            // Default to true to avoid UI disruption on error
            _isConnected.value = true
        }
    }
    
    /**
     * Register network callback to monitor changes
     */
    private fun registerNetworkCallback() {
        try {
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
            if (VERBOSE_LOGGING) {
                Log.d(TAG, "Network callback registered")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error registering network callback: ${e.message}")
        }
    }
    
    /**
     * Unregister network callback when no longer needed
     */
    fun unregisterNetworkCallback() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            if (VERBOSE_LOGGING) {
                Log.d(TAG, "Network callback unregistered")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering network callback: ${e.message}")
        }
    }
    
    /**
     * Check if network is currently available
     */
    fun isNetworkAvailable(): Boolean {
        val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        return networkCapabilities != null && (
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        )
    }
    
    /**
     * Refresh network state - to be called when app resumes from background
     * This forces an immediate check of network status and updates the flow
     */
    fun refreshNetworkState() {
        val isNetworkAvailable = isNetworkAvailable()
        Log.d(TAG, "Refreshing network state, available: $isNetworkAvailable")
        
        // Only update if there's a change in state to avoid unnecessary recompositions
        if (_isConnected.value != isNetworkAvailable) {
            _isConnected.value = isNetworkAvailable
            Log.d(TAG, "Network state updated to: $isNetworkAvailable")
            
            // OPTIMIZATION: Save the updated state for future app launches
            saveNetworkState(isNetworkAvailable)
            
            // Trigger connection quality check if network is available
            if (isNetworkAvailable) {
                checkConnectionQuality()
            } else {
                _connectionQuality.value = ConnectionQuality.UNKNOWN
                _isSlowConnection.value = false
            }
        }
    }
    
    /**
     * Check connection quality based on capabilities
     */
    private fun checkConnectionQuality(networkCapabilities: NetworkCapabilities? = null) {
        try {
            val capabilities = networkCapabilities ?: run {
                val network = connectivityManager.activeNetwork ?: return
                connectivityManager.getNetworkCapabilities(network) ?: return
            }
            
            // Check connection type and speed
            val hasWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val hasCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            val hasEthernet = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            
            val quality = when {
                hasEthernet -> ConnectionQuality.EXCELLENT
                hasWifi -> {
                    // Use signal strength or bandwidth if available in newer Android versions
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        val downstreamBandwidth = capabilities.getLinkDownstreamBandwidthKbps()
                        val upstreamBandwidth = capabilities.getLinkUpstreamBandwidthKbps()
                        
                        when {
                            downstreamBandwidth >= 20000 -> ConnectionQuality.EXCELLENT // 20 Mbps+
                            downstreamBandwidth >= 5000 -> ConnectionQuality.GOOD // 5 Mbps+
                            downstreamBandwidth >= 1000 -> ConnectionQuality.MODERATE // 1 Mbps+
                            else -> ConnectionQuality.POOR
                        }
                    } else {
                        ConnectionQuality.GOOD // Assume good for WiFi if can't determine speed
                    }
                }
                hasCellular -> {
                    // Use signal strength or bandwidth if available in newer Android versions
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        val downstreamBandwidth = capabilities.getLinkDownstreamBandwidthKbps()
                        
                        when {
                            downstreamBandwidth >= 10000 -> ConnectionQuality.GOOD // 10 Mbps+
                            downstreamBandwidth >= 2000 -> ConnectionQuality.MODERATE // 2 Mbps+
                            else -> ConnectionQuality.POOR
                        }
                    } else {
                        ConnectionQuality.MODERATE // Assume moderate for cellular if can't determine speed
                    }
                }
                else -> ConnectionQuality.POOR
            }
            
            // Only log if quality changed or in verbose mode
            val oldQuality = _connectionQuality.value
            if (quality != oldQuality) {
                Log.d(TAG, "Connection quality changed: $oldQuality → $quality")
                _connectionQuality.value = quality
                
                // Only update slow connection if changed
                val isNowSlow = quality == ConnectionQuality.POOR
                if (isNowSlow != _isSlowConnection.value) {
                    _isSlowConnection.value = isNowSlow
                    Log.d(TAG, "Slow connection state changed: $isNowSlow")
                }
            } else if (VERBOSE_LOGGING) {
                Log.d(TAG, "Connection quality: $quality")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking connection quality: ${e.message}")
            _connectionQuality.value = ConnectionQuality.UNKNOWN
            _isSlowConnection.value = false
        }
    }
    
    /**
     * Monitor connection speed by pinging a server periodically
     */
    private fun monitorConnectionSpeed() {
        coroutineScope.launch {
            while (true) {
                if (_isConnected.value) {
                    val startTime = System.currentTimeMillis()
                    try {
                        // Basic connectivity check
                        val latency = measureLatency()
                        
                        // Disabled setting slow connection flag - only log the measurements
                        if (VERBOSE_LOGGING) {
                            Log.d(TAG, "Connection latency: $latency ms")
                        }
                    } catch (e: Exception) {
                        if (VERBOSE_LOGGING) {
                            Log.e(TAG, "Error checking connection speed", e)
                        }
                    }
                }
                // Check connection speed every 10 seconds
                delay(10000)
            }
        }
    }
    
    /**
     * Simple latency measurement
     */
    private suspend fun measureLatency(): Long {
        val startTime = System.currentTimeMillis()
        try {
            // We're just checking if we can reach Google's DNS
            val runtime = Runtime.getRuntime()
            val pingProcess = runtime.exec("ping -c 1 8.8.8.8")
            val exitValue = pingProcess.waitFor()
            
            return if (exitValue == 0) {
                System.currentTimeMillis() - startTime
            } else {
                // If ping failed, report slow connection
                slowConnectionThreshold + 1000
            }
        } catch (e: Exception) {
            if (VERBOSE_LOGGING) {
                Log.e(TAG, "Error measuring latency", e)
            }
            return slowConnectionThreshold + 1000
        }
    }
    
    /**
     * OPTIMIZATION: Save network state to SharedPreferences
     * This allows for faster app startup by avoiding expensive network checks
     */
    private fun saveNetworkState(isConnected: Boolean) {
        try {
            // Use SharedPreferences with apply() for better performance
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                putBoolean(KEY_LAST_CONNECTED_STATE, isConnected)
                putString(KEY_LAST_CONNECTION_QUALITY, _connectionQuality.value.name)
                putLong(KEY_LAST_CHECK_TIME, System.currentTimeMillis())
                apply() // Use apply() instead of commit() for better performance
            }
            
            if (VERBOSE_LOGGING) {
                Log.d(TAG, "Saved network state: connected=$isConnected, quality=${_connectionQuality.value}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving network state", e)
        }
    }
    
    /**
     * OPTIMIZATION: Load cached network state from SharedPreferences
     * This provides an immediate network state during app startup
     */
    private fun loadCachedNetworkState() {
        try {
            // Use SharedPreferences directly
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val lastConnected = prefs.getBoolean(KEY_LAST_CONNECTED_STATE, true) // Default to true
            val lastQualityStr = prefs.getString(KEY_LAST_CONNECTION_QUALITY, ConnectionQuality.UNKNOWN.name)
            val lastCheckTime = prefs.getLong(KEY_LAST_CHECK_TIME, 0)
            
            // Only use cached value if it's recent (within last 30 minutes)
            val isCacheRecent = System.currentTimeMillis() - lastCheckTime < 30 * 60 * 1000
            
            if (isCacheRecent) {
                _isConnected.value = lastConnected
                
                try {
                    val lastQuality = ConnectionQuality.valueOf(lastQualityStr ?: ConnectionQuality.UNKNOWN.name)
                    _connectionQuality.value = lastQuality
                    _isSlowConnection.value = lastQuality == ConnectionQuality.POOR
                } catch (e: Exception) {
                    _connectionQuality.value = ConnectionQuality.UNKNOWN
                }
                
                Log.d(TAG, "Loaded cached network state: connected=$lastConnected, quality=${_connectionQuality.value}")
            } else {
                Log.d(TAG, "Cached network state too old, using default values")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading cached network state", e)
        }
    }
} 