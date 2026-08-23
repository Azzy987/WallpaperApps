package com.droidates.wallpapers.data.repository

import android.content.Context
import android.content.Intent
import android.util.Log
import com.droidates.wallpapers.BuildConfig
import com.droidates.wallpapers.R
import com.droidates.wallpapers.model.User
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.droidates.wallpapers.utils.userDocRef
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AuthRepository"
private const val PREMIUM_REFRESH_COOLDOWN_MS = 30_000L // 30 seconds

@Singleton
class AuthRepository @Inject constructor(
    private val context: Context,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    // Premium user state
    private val _isPremiumUser = MutableStateFlow(false)
    val isPremiumUser: StateFlow<Boolean> = _isPremiumUser.asStateFlow()

    // Cooldown tracking: prevents hammering Firestore with rapid premium status checks
    @Volatile
    private var lastPremiumRefreshMs: Long = 0L
    
    private val WEB_CLIENT_ID = com.droidates.wallpapers.config.AppConfig.GOOGLE_WEB_CLIENT_ID

    init {
        // Check premium status of current user on init
        checkCurrentUserPremiumStatus()
    }
    
    // Update premium status for the current user
    private fun checkCurrentUserPremiumStatus() {
        // Update premium status based on current user
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // Check Firestore for premium status using nested collection structure
            firestore.userDocRef(currentUser.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        // Check both fields for premium status (either can make the user premium)
                        val isPremiumField = document.getBoolean("isPremium") ?: false
                        val premiumField = document.getBoolean("premium") ?: false
                        val isPremium = isPremiumField || premiumField

                        _isPremiumUser.value = isPremium
                        Log.d(TAG, "User premium status: $isPremium (isPremium=$isPremiumField, premium=$premiumField)")
                    } else {
                        _isPremiumUser.value = false
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error checking premium status", e)
                    _isPremiumUser.value = false
                }
        } else {
            _isPremiumUser.value = false
        }
    }
    
    // Update premium status in Firestore
    suspend fun updatePremiumStatus(uid: String, isPremium: Boolean, premiumType: String? = null) {
        try {
            val updates = mutableMapOf<String, Any?>(
                "isPremium" to isPremium,
                "premium" to isPremium  // Update both fields for consistency
            )
            
            // Add premium type if provided
            if (premiumType != null) {
                updates["premiumType"] = premiumType
                
                // Set premium dates
                if (isPremium) {
                    // Use server timestamp for premium start date
                    updates["premiumSince"] = FieldValue.serverTimestamp()
                    
                    // Set expiry for non-lifetime subscriptions
                    if (premiumType != "lifetime") {
                        val expiryMap = mapOf(
                            "weekly" to 7,
                            "monthly" to 30,
                            "yearly" to 365
                        )
                        
                        val expiryDays = expiryMap[premiumType] ?: 0
                        if (expiryDays > 0) {
                            // Calculate expiry date based on days from now
                            val calendar = java.util.Calendar.getInstance()
                            calendar.add(java.util.Calendar.DAY_OF_YEAR, expiryDays)
                            updates["premiumExpiry"] = calendar.time
                        }
                    } else {
                        // For lifetime subscriptions, ensure no expiry date is set
                        updates["premiumExpiry"] = FieldValue.delete()
                    }
                } else {
                    // Clear premium dates on cancellation
                    updates["premiumExpiry"] = FieldValue.delete()
                    updates["premiumSince"] = FieldValue.delete()
                }
            }
            
            // Execute the update using nested collection structure
            firestore.userDocRef(uid)
                .update(updates)
                .await()
                
            // Update local state
            _isPremiumUser.value = isPremium
            Log.d(TAG, "Premium status updated to $isPremium for user $uid, type: $premiumType")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating premium status", e)
            throw e
        }
    }

    // Get Google Sign-In client with option to force account selection
    fun getGoogleSignInClient(forceAccountSelection: Boolean = false): GoogleSignInClient {
        Log.d(TAG, "=== GOOGLE SIGN-IN CLIENT CONFIGURATION ===")
        Log.d(TAG, "Creating Google Sign-In client with Web Client ID: $WEB_CLIENT_ID")
        Log.d(TAG, "Package name: ${context.packageName}")
        Log.d(TAG, "Force account selection: $forceAccountSelection")
        
        val gsoBuilder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(WEB_CLIENT_ID)
            .requestEmail()
        
        Log.d(TAG, "GoogleSignInOptions configured:")
        Log.d(TAG, "  - ID Token requested: true")
        Log.d(TAG, "  - Email requested: true")
        Log.d(TAG, "  - Web Client ID: $WEB_CLIENT_ID")
        
        val gso = gsoBuilder.build()
        val client = GoogleSignIn.getClient(context, gso)
        
        Log.d(TAG, "Google Sign-In client created successfully")
        Log.d(TAG, "===============================================")
        
        return client
    }

    // Get sign-in intent with option to force account selection
    fun getSignInIntent(forceAccountSelection: Boolean = true): Intent {
        Log.d(TAG, "Getting sign-in intent, forceAccountSelection: $forceAccountSelection")
        
        // Clear any previous sign-in state if we're forcing selection
        if (forceAccountSelection) {
            val client = getGoogleSignInClient()
            client.signOut()
            Log.d(TAG, "Cleared previous sign-in state to force account selection")
        }
        
        // GOOGLE SIGN-IN FIX: Add configuration logging
        val client = getGoogleSignInClient()
        Log.d(TAG, "Google Sign-in client created successfully")
        Log.d(TAG, "Package name: ${context.packageName}")
        Log.d(TAG, "Make sure this package name matches the one configured in Firebase Console")
        
        val intent = client.signInIntent
        Log.d(TAG, "Sign-in intent created successfully")
        
        return intent
    }
    
    // Sign out from Google
    fun signOut() {
        Log.d(TAG, "Signing out from Google")
        getGoogleSignInClient().signOut()
        
        // Clear Firebase auth
        auth.signOut()
        
        // Immediately update premium status to false
        _isPremiumUser.value = false
        
        // Notify listeners
        Log.d(TAG, "User signed out, premium status reset")
    }
    
    // Handle sign-in result from Intent
    suspend fun handleSignInResult(data: Intent?): GoogleSignInAccount? {
        Log.d(TAG, "=== GOOGLE SIGN-IN RESULT HANDLING DEBUG ===")
        Log.d(TAG, "Handling sign-in result from intent")
        Log.d(TAG, "Intent data: $data")
        
        if (data == null) {
            Log.e(TAG, "Sign-in data is null - user may have canceled or closed sign-in dialog")
            return null
        }
        
        return try {
            Log.d(TAG, "Extracting Google Sign-In account from intent...")
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            Log.d(TAG, "Task created, attempting to get result...")
            
            val account = task.getResult(ApiException::class.java)
            
            Log.d(TAG, "=== GOOGLE SIGN-IN SUCCESS ===")
            Log.d(TAG, "Account Email: ${account.email}")
            Log.d(TAG, "Account Display Name: ${account.displayName}")
            Log.d(TAG, "Account ID: ${account.id}")
            Log.d(TAG, "Account Photo URL: ${account.photoUrl}")
            Log.d(TAG, "ID Token Present: ${account.idToken != null}")
            
            if (account.idToken == null) {
                Log.e(TAG, "ID token is null - this is required for Firebase authentication")
                Log.e(TAG, "Check if your app is properly configured in Firebase Console")
                Log.e(TAG, "Ensure you've uploaded the correct SHA-1 certificate fingerprint")
                return null
            }
            
            Log.d(TAG, "ID Token available, proceeding with Firebase authentication...")
            
            // Authenticate with Firebase
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            Log.d(TAG, "Google credential created, signing in with Firebase...")
            
            val authResult = auth.signInWithCredential(credential).await()
            val user = authResult.user ?: throw IllegalStateException("Firebase auth failed - user is null")
            
            Log.d(TAG, "=== FIREBASE AUTHENTICATION SUCCESS ===")
            Log.d(TAG, "Firebase UID: ${user.uid}")
            Log.d(TAG, "Firebase Email: ${user.email}")
            Log.d(TAG, "Firebase Display Name: ${user.displayName}")
            Log.d(TAG, "Firebase Email Verified: ${user.isEmailVerified}")
            
            // Save user to Firestore
            Log.d(TAG, "Saving user data to Firestore...")
            saveUserToFirestore(user)
            
            Log.d(TAG, "=== SIGN-IN PROCESS COMPLETED SUCCESSFULLY ===")
            
            // Return the Google account for immediate UI updates
            account
        } catch (e: ApiException) {
            Log.e(TAG, "=== GOOGLE SIGN-IN API EXCEPTION ===")
            Log.e(TAG, "Status Code: ${e.statusCode}")
            Log.e(TAG, "Status Message: ${e.message}")
            
            // Common error codes and their meanings
            when (e.statusCode) {
                12501 -> Log.e(TAG, "ERROR 12501: User canceled the sign-in flow")
                12500 -> Log.e(TAG, "ERROR 12500: Sign-in currently in progress")
                7 -> Log.e(TAG, "ERROR 7: Network error - check internet connection")
                8 -> Log.e(TAG, "ERROR 8: Internal error occurred")
                10 -> Log.e(TAG, "ERROR 10: Developer error - check app configuration")
                else -> Log.e(TAG, "Other error occurred: ${e.statusCode}")
            }
            
            Log.e(TAG, "Full exception details:", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "=== UNEXPECTED SIGN-IN ERROR ===")
            Log.e(TAG, "Error type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Error message: ${e.message}")
            Log.e(TAG, "Full exception details:", e)
            null
        }
    }
    
    // Get current user from Firestore
    suspend fun getCurrentUser(): User? {
        val firebaseUser = auth.currentUser ?: return null
        
        Log.d(TAG, "Getting current user data from Firestore for uid: ${firebaseUser.uid}")
        
        return try {
            val userDoc = firestore.userDocRef(firebaseUser.uid)
                .get()
                .await()

            if (userDoc.exists()) {
                val user = userDoc.toObject(User::class.java)
                user?.copy(id = firebaseUser.uid) // Ensure id is set correctly
            } else {
                Log.d(TAG, "User document not found in Firestore, creating new one")

                // If no user found in Firestore, create one from Firebase user data
                val newUser = User(
                    id = firebaseUser.uid,
                    displayName = firebaseUser.displayName ?: "User",
                    email = firebaseUser.email ?: "",
                    photoUrl = firebaseUser.photoUrl?.toString(),
                    isPremium = false
                )

                // Save the new user to Firestore
                firestore.userDocRef(firebaseUser.uid)
                    .set(newUser)
                    .await()
                    
                newUser
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current user from Firestore", e)
            null
        }
    }
    
    // Get user data directly from Firestore
    suspend fun getUserData(uid: String, forceServer: Boolean = false): Result<User> {
        try {
            val source = if (forceServer) {
                Source.SERVER
            } else {
                Source.DEFAULT
            }
            
            val document = firestore.userDocRef(uid)
                .get(source)
                .await()
                
            if (document.exists()) {
                val user = document.toObject(User::class.java)?.copy(id = document.id)
                if (user != null) {
                    Log.d(TAG, "Successfully fetched user data. Premium: ${user.isPremium}, Type: ${user.premiumType}")
                    // Always refresh premium status when user data is fetched
                    _isPremiumUser.value = user.isPremium
                    return Result.success(user)
                }
            }
            
            Log.d(TAG, "User document doesn't exist: $uid")
            return Result.failure(Exception("User not found"))
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user data", e)
            return Result.failure(e)
        }
    }
    
    // Save user to Firestore
    private suspend fun saveUserToFirestore(firebaseUser: FirebaseUser) {
        Log.d(TAG, "Saving user to Firestore: ${firebaseUser.uid}")
        
        try {
            // Check if user already exists
            val existingDoc = firestore.userDocRef(firebaseUser.uid)
                .get()
                .await()

            if (existingDoc.exists()) {
                Log.d(TAG, "User already exists in Firestore, updating")

                // Only update fields that might have changed
                val updates = mapOf(
                    "displayName" to (firebaseUser.displayName ?: "User"),
                    "email" to (firebaseUser.email ?: ""),
                    "photoUrl" to (firebaseUser.photoUrl?.toString())
                )

                firestore.userDocRef(firebaseUser.uid)
                    .update(updates)
                    .await()

                Log.d(TAG, "User updated in Firestore")
            } else {
                Log.d(TAG, "Creating new user in Firestore")

                // Create new user
                val newUser = User(
                    id = firebaseUser.uid,
                    displayName = firebaseUser.displayName ?: "User",
                    email = firebaseUser.email ?: "",
                    photoUrl = firebaseUser.photoUrl?.toString(),
                    isPremium = false
                )

                firestore.userDocRef(firebaseUser.uid)
                    .set(newUser)
                    .await()
                
                Log.d(TAG, "New user created in Firestore")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user to Firestore", e)
            throw e
        }
    }

    // Add method to force refresh premium status from Firestore
    fun refreshPremiumStatus() {
        // Cooldown: skip if called too frequently to avoid hammering Firestore
        val now = System.currentTimeMillis()
        if (now - lastPremiumRefreshMs < PREMIUM_REFRESH_COOLDOWN_MS) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "refreshPremiumStatus skipped — cooldown active (${(PREMIUM_REFRESH_COOLDOWN_MS - (now - lastPremiumRefreshMs)) / 1000}s remaining)")
            }
            return
        }
        lastPremiumRefreshMs = now

        // Only log in debug builds
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Force refreshing premium status from Firestore")
        }

        // Update premium status based on current user
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // Check Firestore for premium status with cache disabled
            firestore.userDocRef(currentUser.uid)
                .get(Source.SERVER) // Force fetch from server, not cache
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        // Check both fields for premium status (either can make the user premium)
                        val isPremiumField = document.getBoolean("isPremium") ?: false
                        val premiumField = document.getBoolean("premium") ?: false
                        val isPremium = isPremiumField || premiumField
                        
                        val oldValue = _isPremiumUser.value
                        if (oldValue != isPremium) {
                            Log.d(TAG, "Premium status changed from $oldValue to $isPremium")
                            _isPremiumUser.value = isPremium
                        } else if (BuildConfig.DEBUG) {
                            // Only log unchanged status in debug builds
                            Log.d(TAG, "User premium status remains unchanged: $isPremium")
                        }
                    } else {
                        _isPremiumUser.value = false
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "User document doesn't exist, setting premium status to false")
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error refreshing premium status", e)
                }
        } else {
            _isPremiumUser.value = false
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "No current user, setting premium status to false")
            }
        }
    }
} 