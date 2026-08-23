package com.droidates.wallpapers.viewmodel

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.droidates.wallpapers.data.repository.AuthRepository
import com.droidates.wallpapers.data.repository.FavoritesRepository
import com.droidates.wallpapers.model.User
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import com.droidates.wallpapers.config.AppConfig
import com.droidates.wallpapers.utils.userDocRef
import javax.inject.Inject

private const val TAG = "AuthViewModel"

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val context: Context,
    private val auth: FirebaseAuth,
    private val authRepository: AuthRepository,
    private val favoritesRepository: FavoritesRepository,
    private val adManager: com.droidates.wallpapers.utils.AdManager
) : ViewModel() {

    private val WEB_CLIENT_ID = com.droidates.wallpapers.config.AppConfig.GOOGLE_WEB_CLIENT_ID

    // Launcher for Google Sign-In
    companion object {
        var signInLauncher: ActivityResultLauncher<Intent>? = null
    }

    // State for sign-in result - now using SharedFlow for immediate emission with replay
    private val _signInState = MutableSharedFlow<SignInState>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val signInState: SharedFlow<SignInState> = _signInState

    // State for user sign-in status
    private val _isSignedIn = MutableStateFlow(auth.currentUser != null)
    val isSignedIn: StateFlow<Boolean> = _isSignedIn

    // User data
    private val _userData = MutableStateFlow<User?>(null)
    val userData: StateFlow<User?> = _userData

    // Additional state variables
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _signInIntent = MutableStateFlow<Intent?>(null)
    val signInIntent: StateFlow<Intent?> = _signInIntent

    // Counter for favorites 
    private val _favoritesCount = MutableStateFlow(0)
    val favoritesCount: StateFlow<Int> = _favoritesCount
    
    // Counter for favorites added in a session
    private val _favoritesAdded = MutableStateFlow(0)
    val favoritesAdded: StateFlow<Int> = _favoritesAdded

    init {
        // Check if user is already signed in
        auth.currentUser?.let {
            Log.d(TAG, "User is already signed in: ${it.uid}")
            _isSignedIn.value = true
            fetchUserData()
            _signInState.tryEmit(SignInState.Success)
            
            // Load favorites for already signed-in user
            loadFavoritesFromFirestore()
        } ?: run {
            _signInState.tryEmit(SignInState.Idle)
        }
        
        // Setup Firebase Auth state listener
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            val wasSignedIn = _isSignedIn.value
            val isNowSignedIn = user != null
            
            if (wasSignedIn != isNowSignedIn) {
                Log.d(TAG, "Auth state changed from Firebase listener: $wasSignedIn → $isNowSignedIn")
                _isSignedIn.value = isNowSignedIn
                
                if (isNowSignedIn) {
                    // Update user data immediately
                    // user is guaranteed to be non-null here since isNowSignedIn = user != null
                    val tempUser = User(
                            id = user.uid,
                            displayName = user.displayName ?: "User",
                            email = user.email ?: "",
                            photoUrl = user.photoUrl?.toString()
                        )
                        _userData.value = tempUser
                        _signInState.tryEmit(SignInState.Success)
                        
                        // Then fetch complete data
                        fetchUserData()
                        
                        // Load favorites from Firestore when user signs in
                        loadFavoritesFromFirestore()
                    }
                } else {
                    // Clear user data
                    _userData.value = null
                    _signInState.tryEmit(SignInState.Idle)
                    
                    // Reset sync state in FavoritesRepository to use local database only (non-blocking)
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        favoritesRepository.resetSyncState()
                    }
                }
            }
        }

    private fun loadFavoritesFromFirestore() {
        viewModelScope.launch {
            try {
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    Log.d(TAG, "Loading favorites from Firestore after sign-in")
                    favoritesRepository.loadFavoritesFromFirestore()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading favorites from Firestore: ${e.message}", e)
            }
        }
    }

    fun signIn() {
        Log.d(TAG, "Beginning sign-in process")
        _signInState.tryEmit(SignInState.Loading)
        _isLoading.value = true
        _error.value = null
        
        viewModelScope.launch {
            try {
                // ALWAYS force account selection dialog to appear
                val signInIntent = authRepository.getSignInIntent(forceAccountSelection = true)
                _signInIntent.value = signInIntent
                
                // Explicitly sign out first to ensure account chooser appears
                authRepository.signOut()
                
                Log.d(TAG, "Sign-in intent created with forced account selection, existing session cleared")
                
                // Launch the sign-in flow using the launcher
                signInLauncher?.launch(signInIntent)
                Log.d(TAG, "Sign-in launcher triggered")
            } catch (e: Exception) {
                _error.value = "Failed to start sign-in: ${e.message}"
                _signInState.tryEmit(SignInState.Error("Failed to start sign-in: ${e.message}"))
                _isLoading.value = false
                Log.e(TAG, "Error creating sign-in intent: ${e.message}", e)
            }
        }
    }

    fun handleSignInResult(data: Intent?) {
        Log.d(TAG, "=== AUTH VIEWMODEL SIGN-IN RESULT HANDLING ===")
        Log.d(TAG, "Handling sign-in result in AuthViewModel")
        Log.d(TAG, "Intent data received: $data")
        Log.d(TAG, "Setting loading state to true")
        _isLoading.value = true
        
        viewModelScope.launch {
            try {
                Log.d(TAG, "Starting to process sign-in result in coroutine")
                Log.d(TAG, "Current loading state: ${_isLoading.value}")
                
                if (data == null) {
                    Log.w(TAG, "Sign-in data is null - user likely canceled or dialog was closed")
                    Log.d(TAG, "Setting error state and resetting loading")
                    _error.value = "Sign-in canceled"
                    _signInState.tryEmit(SignInState.Error("Sign-in canceled"))
                    _isLoading.value = false
                    return@launch
                }
                
                Log.d(TAG, "Valid intent data received, delegating to AuthRepository")
                Log.d(TAG, "Intent action: ${data.action}")
                Log.d(TAG, "Intent extras keys: ${data.extras?.keySet()}")
                
                val account = authRepository.handleSignInResult(data)
                
                if (account != null) {
                    Log.d(TAG, "Sign-in successful for account: ${account.email}")
                    
                    // Create a temporary user immediately
                    val tempUser = User(
                        id = account.id ?: "",
                        displayName = account.displayName ?: "User",
                        email = account.email ?: "",
                        photoUrl = account.photoUrl?.toString(),
                        isPremium = false // Default, will be updated from Firestore
                    )
                    
                    // Update user state immediately
                    _userData.value = tempUser
                    _isSignedIn.value = true
                    _signInState.tryEmit(SignInState.Success)
                    Log.d(TAG, "User data updated with temporary data: $tempUser")
                    
                    // Reset favorites sync state for new sign-in (non-blocking)
                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        favoritesRepository.resetSyncState()
                    }
                    
                    // Then fetch complete user data
                    refreshUserData()
                    
                    // Explicitly update premium status in AdManager
                    updatePremiumStatus()
                } else {
                    val errorMessage = "Google Sign-in failed. Please check your internet connection and try again."
                    _error.value = errorMessage
                    _signInState.tryEmit(SignInState.Error(errorMessage))
                    Log.e(TAG, "Sign-in failed: No account data returned")
                }
            } catch (e: Exception) {
                _error.value = "Sign-in failed: ${e.message}"
                _signInState.tryEmit(SignInState.Error("Sign-in failed: ${e.message}"))
                Log.e(TAG, "Error in handleSignInResult: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun signOut() {
        try {
            Log.d(TAG, "Signing out user")
            _signInState.tryEmit(SignInState.Loading)
            authRepository.signOut()
            auth.signOut()

            // Reset favorites sync state on sign-out (non-blocking)
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                favoritesRepository.resetSyncState()
            }
            
            // These will also be updated by the Firebase auth listener,
            // but we set them immediately for faster UI updates
            _isSignedIn.value = false
            _userData.value = null
            _signInState.tryEmit(SignInState.Idle)
            Log.d(TAG, "User signed out successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error signing out", e)
            _error.value = "Error signing out: ${e.message}"
            _signInState.tryEmit(SignInState.Error("Error signing out: ${e.message}"))
        }
    }

    // Make function public to allow calling it from UI
    fun fetchUserData() {
        Log.d(TAG, "Fetching complete user data from Firestore")
        viewModelScope.launch {
            try {
                // First set loading state to indicate data is being refreshed
                _signInState.tryEmit(SignInState.Loading)
                
                val userData = authRepository.getCurrentUser()
                if (userData != null) {
                    _userData.value = userData
                    Log.d(TAG, "User data fetched successfully: $userData")
                    
                    // Ensure sign-in state is updated
                    _isSignedIn.value = true
                    
                    // Notify listeners that user data has been updated
                    // This ensures all UI components recompose
                    _signInState.tryEmit(SignInState.Success)
                    
                    // Update premium status in AdManager based on the fetched user data
                    if (userData.isPremium) {
                        updatePremiumStatus()
                    }
                } else {
                    Log.d(TAG, "No user data found in Firestore, keeping temporary user")
                    
                    // Still emit success to trigger UI updates
                    _signInState.tryEmit(SignInState.Success)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching user data: ${e.message}", e)
                _signInState.tryEmit(SignInState.Error("Failed to fetch user data: ${e.message}"))
            }
        }
    }

    // Method to immediately reflect Firebase Auth state changes
    fun checkAuthState() {
        Log.d(TAG, "Checking current auth state")
        val currentUser = auth.currentUser
        val wasSignedIn = _isSignedIn.value
        val newSignedInState = currentUser != null
        
        // Only update if there was a change
        if (wasSignedIn != newSignedInState) {
            Log.d(TAG, "Auth state changed from $wasSignedIn to $newSignedInState")
            _isSignedIn.value = newSignedInState
            
            if (newSignedInState) {
                // Reset favorites sync state for new sign-in (non-blocking)
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    favoritesRepository.resetSyncState()
                }
                
                // Signed in - update user data immediately with basic info
                // currentUser is guaranteed to be non-null here since newSignedInState = currentUser != null
                val tempUser = User(
                        id = currentUser.uid,
                        displayName = currentUser.displayName ?: "User",
                        email = currentUser.email ?: "",
                        photoUrl = currentUser.photoUrl?.toString()
                    )
                    _userData.value = tempUser
                
                // Then fetch full data
                fetchUserData()
                
                // Also load favorites
                loadFavoritesFromFirestore()
                
                _signInState.tryEmit(SignInState.Success)
            } else {
                // Signed out - clear user data
                _userData.value = null
                _signInState.tryEmit(SignInState.Idle)
            }
        }
    }
    
    private fun createUserFromFirebaseData(currentUser: FirebaseUser) {
        val newUser = User(
            id = currentUser.uid,
            displayName = currentUser.displayName ?: "User",
            email = currentUser.email ?: "",
            photoUrl = currentUser.photoUrl?.toString()
        )
        createUser(newUser)
        _userData.value = newUser
        // Ensure isSignedIn is updated to reflect current state
        _isSignedIn.value = true
    }
    
    private fun createUser(user: User) {
        viewModelScope.launch {
            try {
                // Use AuthRepository to create the user in Firestore with nested collection structure
                val userRef = FirebaseFirestore.getInstance()
                    .userDocRef(user.id)
                
                userRef.set(user)
                    .addOnSuccessListener {
                        Log.d(TAG, "User created successfully in Firestore")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Error creating user in Firestore", e)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating user", e)
            }
        }
    }

    fun updateFavoriteCount() {
        viewModelScope.launch {
            try {
                val currentUser = auth.currentUser
                if (currentUser != null) {
                    val userResult = authRepository.getUserData(currentUser.uid)
                    if (userResult.isSuccess) {
                        val user = userResult.getOrNull()
                        user?.let {
                            _favoritesCount.value = it.favorites.size
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating favorite count", e)
            }
        }
    }

    fun incrementFavoritesAdded() {
        _favoritesAdded.value = _favoritesAdded.value + 1
    }
    
    fun resetFavoritesCounter() {
        _favoritesAdded.value = 0
    }
    
    // Reset the error state
    fun clearError() {
        _error.value = null
    }

    // Navigate to settings when profile is clicked
    fun navigateToSettings(navigationState: com.droidates.wallpapers.navigation.NavigationState) {
        navigationState.navigateToSettings()
    }

    // Force refresh user data from Firestore, bypassing any cache
    fun refreshUserData(forceServerFetch: Boolean = false) {
        Log.d(TAG, "Forcing refresh of user data from Firestore, forceServerFetch=$forceServerFetch")
        val currentUser = auth.currentUser ?: run {
            Log.d(TAG, "No authenticated user to refresh data for")
            return
        }
        
        viewModelScope.launch {
            try {
                _signInState.tryEmit(SignInState.Loading)
                
                // Get user data from Firestore - use server source if force requested
                val source = if (forceServerFetch) {
                    com.google.firebase.firestore.Source.SERVER
                } else {
                    com.google.firebase.firestore.Source.DEFAULT
                }
                
                val userDocRef = FirebaseFirestore.getInstance()
                    .userDocRef(currentUser.uid)
                
                // Handle the Task directly since await() isn't available
                userDocRef.get(source).addOnSuccessListener { userDoc ->
                    if (userDoc.exists()) {
                        // Updated to include premium details in mapping
                        val userData = User(
                            id = currentUser.uid,
                            displayName = userDoc.getString("displayName") ?: currentUser.displayName ?: "User",
                            email = userDoc.getString("email") ?: currentUser.email ?: "",
                            photoUrl = userDoc.getString("photoUrl") ?: currentUser.photoUrl?.toString(),
                            isPremium = userDoc.getBoolean("isPremium") ?: false || userDoc.getBoolean("premium") ?: false,
                            premiumType = userDoc.getString("premiumType"),
                            premiumSince = userDoc.getTimestamp("premiumSince")?.toDate(),
                            premiumExpiry = userDoc.getTimestamp("premiumExpiry")?.toDate(),
                            favorites = (userDoc.get("favorites") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                        )
                        
                        _userData.value = userData
                        _signInState.tryEmit(SignInState.Success)
                        
                        // After user data is refreshed, ensure favorites are loaded
                        loadFavoritesFromFirestore()
                        
                        // Also update premium status in auth repository
                        if (userData.isPremium) {
                            authRepository.refreshPremiumStatus()
                        }
                        
                        Log.d(TAG, "Successfully refreshed user data: $userData")
                    } else {
                        Log.d(TAG, "User document does not exist in Firestore")
                        _signInState.tryEmit(SignInState.Failed("User data not found"))
                    }
                }.addOnFailureListener { e ->
                    Log.e(TAG, "Error refreshing user data", e)
                    _signInState.tryEmit(SignInState.Failed("Error refreshing user data: ${e.message}"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing user data", e)
                _signInState.tryEmit(SignInState.Failed("Error refreshing user data: ${e.message}"))
            }
        }
    }

    // Add method to check premium status using adManager
    private fun updatePremiumStatus() {
        Log.d(TAG, "Explicitly updating premium status in AdManager")
        try {
            adManager.checkPremiumStatus()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating premium status in AdManager", e)
        }
    }

    sealed class SignInState {
        object Idle : SignInState()
        object Loading : SignInState()
        object Success : SignInState()
        data class Error(val message: String) : SignInState()
        data class Failed(val message: String) : SignInState()
    }
}