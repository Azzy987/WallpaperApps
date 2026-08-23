package com.droidates.wallpapers.core.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
// FirebaseRemoteConfig import removed
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateManager @Inject constructor(
    private val firestore: com.google.firebase.firestore.FirebaseFirestore
) {
    var appUpdateManager: AppUpdateManager? = null
    private var installStateUpdatedListener: InstallStateUpdatedListener? = null
    
    companion object {
        private const val TAG = "UpdateManager"
        private const val UPDATE_REQUEST_CODE = 100

        private val COLLECTION_APP_UPDATES = com.droidates.wallpapers.core.config.AppConfig.COLLECTION_APP_UPDATE
        private val DOCUMENT_APP_NAME = com.droidates.wallpapers.core.config.AppConfig.DOCUMENT_APP_UPDATE
        private val FIELD_VERSION = com.droidates.wallpapers.core.config.AppConfig.FIELD_UPDATE_VERSION
        private val FIELD_MANDATORY_UPDATE = com.droidates.wallpapers.core.config.AppConfig.FIELD_UPDATE_MANDATORY
        private val FIELD_MESSAGE = com.droidates.wallpapers.core.config.AppConfig.FIELD_UPDATE_MESSAGE
    }
    
    fun initialize(activity: Activity) {
        appUpdateManager = AppUpdateManagerFactory.create(activity)
        
        // Create a listener to track the state of the update
        installStateUpdatedListener = InstallStateUpdatedListener { state ->
            when (state.installStatus()) {
                InstallStatus.DOWNLOADED -> {
                    Log.d(TAG, "Update downloaded")
                    // Prompt the user to complete the update
                    appUpdateManager?.completeUpdate()
                }
                InstallStatus.INSTALLED -> {
                    Log.d(TAG, "Update installed")
                    // Clear the listener
                    unregisterListener()
                }
                else -> {
                    Log.d(TAG, "Install status: ${state.installStatus()}")
                }
            }
        }
        
        // Register the listener with explicit flags
        try {
            appUpdateManager?.registerListener(installStateUpdatedListener!!)
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to register update listener", e)
        }
    }
    
    fun unregisterListener() {
        installStateUpdatedListener?.let {
            appUpdateManager?.unregisterListener(it)
        }
    }
    
    fun checkForUpdates(activity: Activity, onUpdateAvailable: (String, Boolean) -> Unit) {
        try {
            // Get current app version code (integer)
            val currentVersionCode = getCurrentAppVersionCode(activity)
            Log.d(TAG, "Current app version code: $currentVersionCode")

            // Check for updates in Firestore using your Firebase structure
            firestore.collection(COLLECTION_APP_UPDATES)
                .document(DOCUMENT_APP_NAME)
                .get(Source.SERVER)
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        // Extract version information from your Firebase structure
                        val requiredVersionCode = document.getLong(FIELD_VERSION)?.toInt() ?: 0
                        val isMandatoryUpdate = document.getBoolean(FIELD_MANDATORY_UPDATE) ?: false
                        val firebaseMessage = document.getString(FIELD_MESSAGE) ?: ""

                        Log.d(TAG, "Firebase version code: $requiredVersionCode, mandatory: $isMandatoryUpdate, message: $firebaseMessage")

                        // Compare version codes directly (integers)
                        if (requiredVersionCode > currentVersionCode) {
                            Log.d(TAG, "Update needed: current=$currentVersionCode, required=$requiredVersionCode")

                            // Use Firebase message or create fallback message based on mandatory status
                            val updateMessage = firebaseMessage.ifEmpty {
                                if (isMandatoryUpdate) {
                                    "A mandatory update is required to continue using the app."
                                } else {
                                    "A new version of the app is available with improvements and bug fixes!"
                                }
                            }

                            Log.d(TAG, "Update details - mandatory: $isMandatoryUpdate, message: $updateMessage")
                            onUpdateAvailable(updateMessage, isMandatoryUpdate)
                        } else {
                            Log.d(TAG, "App is up to date")
                            // App is up to date, optionally check Play Store for other updates
                            checkPlayStoreUpdate(activity, onUpdateAvailable)
                        }
                    } else {
                        Log.d(TAG, "No update document found in Firebase, checking Play Store")
                        // No document exists, check Play Store instead
                        checkPlayStoreUpdate(activity, onUpdateAvailable)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error fetching update info from Firestore", e)
                    // Fallback to Play Store check on failure
                    checkPlayStoreUpdate(activity, onUpdateAvailable)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
        }
    }
    
    // Check for updates in the Play Store
    private fun checkPlayStoreUpdate(activity: Activity, onUpdateAvailable: (String, Boolean) -> Unit) {
        appUpdateManager?.appUpdateInfo?.addOnSuccessListener { appUpdateInfo ->
            when (appUpdateInfo.updateAvailability()) {
                UpdateAvailability.UPDATE_AVAILABLE -> {
                Log.d(TAG, "Update available in Play Store")
                val updateMessage = "A new version is available in the Play Store!"
                val isMandatory = false
                onUpdateAvailable(updateMessage, isMandatory)
                }
                UpdateAvailability.UPDATE_NOT_AVAILABLE -> {
                Log.d(TAG, "No update available in Play Store")
            }
                UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> {
                    Log.d(TAG, "Update already in progress")
                }
                else -> {
                    Log.d(TAG, "Update availability status: ${appUpdateInfo.updateAvailability()}")
                }
            }
        }?.addOnFailureListener { e ->
            Log.e(TAG, "Failed to check Play Store update", e)
        }
    }
    
    
    fun startImmediateUpdate(activity: Activity) {
        appUpdateManager?.appUpdateInfo?.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                
                try {
                    Log.d(TAG, "Starting immediate update flow")
                    // Start immediate update with improved error handling
                appUpdateManager?.startUpdateFlowForResult(
                    appUpdateInfo,
                    activity,
                    AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
                    UPDATE_REQUEST_CODE
                )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start immediate update", e)
                }
            } else {
                Log.d(TAG, "Immediate update not available or not allowed")
            }
        }?.addOnFailureListener { e ->
            Log.e(TAG, "Failed to check for immediate update", e)
        }
    }
    
    fun startFlexibleUpdate(activity: Activity) {
        appUpdateManager?.appUpdateInfo?.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                
                try {
                    Log.d(TAG, "Starting flexible update flow")
                    // Start flexible update with improved error handling
                appUpdateManager?.startUpdateFlowForResult(
                    appUpdateInfo,
                    activity,
                    AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
                    UPDATE_REQUEST_CODE
                )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start flexible update", e)
                }
            } else {
                Log.d(TAG, "Flexible update not available or not allowed")
            }
        }?.addOnFailureListener { e ->
            Log.e(TAG, "Failed to check for flexible update", e)
        }
    }
    
    /**
     * Check for updates that have been downloaded but not installed
     * If an update has been downloaded, prompt the user to complete the installation
     */
    fun checkForCompletedUpdates(activity: Activity) {
        appUpdateManager?.appUpdateInfo?.addOnSuccessListener { appUpdateInfo ->
            try {
                when (appUpdateInfo.installStatus()) {
                    InstallStatus.DOWNLOADED -> {
                    Log.d(TAG, "Update has been downloaded, prompting to complete installation")
                    appUpdateManager?.completeUpdate()
                    }
                    InstallStatus.DOWNLOADING -> {
                        Log.d(TAG, "Update is currently downloading")
                    }
                    InstallStatus.FAILED -> {
                        Log.e(TAG, "Update installation failed")
                    }
                    InstallStatus.INSTALLED -> {
                        Log.d(TAG, "Update has been successfully installed")
                    }
                    InstallStatus.PENDING -> {
                        Log.d(TAG, "Update installation is pending")
                    }
                    else -> {
                        Log.d(TAG, "Update status: ${appUpdateInfo.installStatus()}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking or completing update", e)
            }
        }?.addOnFailureListener { e ->
            Log.e(TAG, "Failed to get app update info", e)
        }
    }
    
    fun openPlayStore(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("market://details?id=${context.packageName}")
                setPackage("com.android.vending")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // If Play Store app is not installed, open it in the browser
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
    
    fun getCurrentAppVersion(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "Unknown"
        } catch (e: PackageManager.NameNotFoundException) {
            "Unknown"
        }
    }

    fun getCurrentAppVersionCode(context: Context): Int {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                packageInfo.versionCode
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Failed to get version code", e)
            0
        }
    }
    
    /**
     * Handle activity result from update flow
     * Call this from onActivityResult in your activity
     */
    fun handleUpdateResult(requestCode: Int, resultCode: Int) {
        if (requestCode == UPDATE_REQUEST_CODE) {
            when (resultCode) {
                Activity.RESULT_OK -> {
                    Log.d(TAG, "Update flow completed successfully")
                }
                Activity.RESULT_CANCELED -> {
                    Log.d(TAG, "Update flow was cancelled by user")
                }
                else -> {
                    Log.d(TAG, "Update flow completed with result code: $resultCode")
                }
            }
        }
    }
    
    /**
     * Check if the app has been updated recently and handle post-update actions
     */
    fun handlePostUpdateActions(context: Context) {
        try {
            Log.d(TAG, "Handling post-update actions")
            // You can add specific logic here for what to do after an update
            // For example: clear cache, show what's new dialog, etc.
        } catch (e: Exception) {
            Log.e(TAG, "Error handling post-update actions", e)
        }
    }
} 