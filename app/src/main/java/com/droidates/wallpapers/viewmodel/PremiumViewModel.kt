package com.droidates.wallpapers.viewmodel

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.droidates.wallpapers.data.repository.AuthRepository
import com.droidates.wallpapers.data.repository.BillingRepository
import com.droidates.wallpapers.model.PremiumFeature
import com.droidates.wallpapers.model.PremiumPlan
import com.droidates.wallpapers.model.PlanType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.NoAdultContent
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Tune
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import com.droidates.wallpapers.R
import com.droidates.wallpapers.config.AppConfig

@HiltViewModel
class PremiumViewModel @Inject constructor(
    private val billingRepository: BillingRepository,
    private val authRepository: AuthRepository
) : ViewModel(), BillingRepository.PurchaseUpdateListener {

    companion object {
        private const val TAG = "PremiumViewModel"
    }

    // Plan data
    private val _premiumPlans = MutableStateFlow<List<PremiumPlan>>(emptyList())
    val premiumPlans: StateFlow<List<PremiumPlan>> = _premiumPlans.asStateFlow()
    
    // PREMIUM SCREEN FIX: Add loading state for product details
    val isLoadingProductDetails: StateFlow<Boolean> = billingRepository.isLoadingProductDetails

    // Selected plan
    private val _selectedPlan = MutableStateFlow<PremiumPlan?>(null)
    val selectedPlan: StateFlow<PremiumPlan?> = _selectedPlan.asStateFlow()

    // Premium status
    val isPremium = billingRepository.isPremium
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    // Purchase state tracking
    private val _purchaseState = MutableStateFlow<PurchaseState>(PurchaseState.Idle)
    val purchaseState: StateFlow<PurchaseState> = _purchaseState.asStateFlow()

    // Features for premium
    val premiumFeatures = listOf(
        PremiumFeature(
            "Ad-Free Experience",
            "Enjoy an ad-free experience with no interruptions",
            iconDrawableRes = R.drawable.no_ads
        ),
        PremiumFeature(
            "Exclusive Wallpapers",
            "Access to all exclusive premium wallpapers",
            iconDrawableRes = R.drawable.exclusive_wallpapers
        ),
        PremiumFeature(
            "Premium Filters",
            "Access to all premium filters and effects",
            iconDrawableRes = R.drawable.premium_features
        ),
        PremiumFeature(
            "Advanced Editing",
            "Advanced editing features for your wallpapers",
            Icons.Default.EditNote
        )
    )

    // Success message for demonstration
    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()
    
    // Is connected to billing
    private val _isConnectedToBilling = billingRepository.isConnected
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    init {
        // React immediately when real product details arrive from Google Play
        viewModelScope.launch {
            billingRepository.productDetails.collectLatest { productDetails ->
                if (productDetails.isNotEmpty()) {
                    Log.d(TAG, "Product details available (${productDetails.size}), loading premium plans")
                    loadPremiumPlans()
                }
            }
        }

        // After billing has connected and its product query finishes (isLoading → false),
        // if we still have no products, show fallback plans so the screen is never blank.
        viewModelScope.launch {
            // Skip the initial false; wait until billing has gone true→false (query completed)
            var loadingStarted = false
            billingRepository.isLoadingProductDetails.collect { isLoading ->
                if (isLoading) {
                    loadingStarted = true
                } else if (loadingStarted) {
                    // Query has completed
                    if (billingRepository.productDetails.value.isEmpty()) {
                        Log.d(TAG, "Billing query finished with 0 products — using fallback plans")
                        addEmulatorFallbackPlans()
                    }
                }
            }
        }

        // Register as purchase listener
        billingRepository.addPurchaseListener(this)
    }
    
    override fun onCleared() {
        super.onCleared()
        // Remove purchase listener
        billingRepository.removePurchaseListener(this)
    }
    
    // Purchase listener implementation
    override fun onPurchaseSuccess() {
        viewModelScope.launch {
            _purchaseState.value = PurchaseState.Success
            _successMessage.value = "Purchase successful!"
            
            // Clear success message after a delay
            delay(3000)
            _successMessage.value = null
        }
    }
    
    override fun onPurchaseCanceled() {
        viewModelScope.launch {
            _purchaseState.value = PurchaseState.Failed("Purchase canceled")
            _successMessage.value = "Purchase canceled"
            
            // Clear message after a delay
            delay(3000)
            _successMessage.value = null
        }
    }
    
    override fun onPurchaseError(message: String) {
        viewModelScope.launch {
            _purchaseState.value = PurchaseState.Failed(message)
            _successMessage.value = "Purchase failed: $message"
            
            // Clear error message after a delay
            delay(3000)
            _successMessage.value = null
        }
    }

    private fun loadPremiumPlans() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "=== PREMIUM PLANS LOADING DEBUG ===")
                Log.d(TAG, "Starting to load premium plans from billing repository...")
                
                // Get plans from the billing repository
                val plans = billingRepository.getPremiumPlans()
                
                Log.d(TAG, "Received ${plans.size} plans from billing repository")
                
                if (plans.isEmpty()) {
                    Log.e(TAG, "No premium plans found from billing repository - Play Billing connection may not be established")
                    // Don't fall back to dummy data anymore - simply show an empty state
                    _premiumPlans.value = emptyList()
                } else {
                    // Log detailed plan information
                    plans.forEachIndexed { index, plan ->
                        Log.d(TAG, "Plan $index:")
                        Log.d(TAG, "  - Name: ${plan.name}")
                        Log.d(TAG, "  - Product ID: ${plan.productId}")
                        Log.d(TAG, "  - Type: ${plan.type}")
                        Log.d(TAG, "  - Price: ${plan.price}")
                        Log.d(TAG, "  - Formatted Price: ${plan.formattedPrice}")
                        Log.d(TAG, "  - Discounted Price: ${plan.discountedPrice}")
                        Log.d(TAG, "  - Price Amount Micros: ${plan.priceAmountMicros}")
                        Log.d(TAG, "  - Currency: ${plan.priceCurrencyCode}")
                        Log.d(TAG, "  - Offer Token: ${plan.offerToken}")
                        Log.d(TAG, "  - Features: ${plan.features}")
                    }
                    
                    // Use the plans directly from the billing repository
                    Log.d(TAG, "All plans loaded successfully, setting to state")
                    _premiumPlans.value = plans

                    // Select lifetime plan
                    _selectedPlan.value = plans.find { it.type == PlanType.LIFETIME }

                    Log.d(TAG, "Selected default plan: ${_selectedPlan.value?.name} (${_selectedPlan.value?.type})")
                }
                
                Log.d(TAG, "=== PREMIUM PLANS LOADING COMPLETE ===")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading premium plans", e)
            }
        }
    }
    
    // Fallback plans shown when Google Play returns no products
    // (app not yet published, unsigned build, or billing unavailable)
    private fun addEmulatorFallbackPlans() {
        val fallbackPlans = listOf(
            PremiumPlan(
                productId = AppConfig.BILLING_LIFETIME_ID,
                name = "Lifetime Premium",
                description = "Lifetime premium purchase",
                price = AppConfig.BILLING_LIFETIME_FALLBACK_PRICE,
                priceAmountMicros = 199_000_000L,
                priceCurrencyCode = "INR",
                type = PlanType.LIFETIME,
                formattedPrice = AppConfig.BILLING_LIFETIME_FALLBACK_PRICE,
                discountedPrice = null
            )
        )

        _premiumPlans.value = fallbackPlans
        _selectedPlan.value = fallbackPlans.firstOrNull()

        Log.d(TAG, "Added ${fallbackPlans.size} fallback plans (billing unavailable)")
    }

    // Restore purchases
    fun restorePurchases() {
        viewModelScope.launch {
            try {
                _purchaseState.value = PurchaseState.InProgress
                Log.d(TAG, "Starting to restore purchases...")
                
                // Check current premium status
                val currentIsPremium = authRepository.isPremiumUser.value
                
                if (currentIsPremium) {
                    // User already has premium, get user data to show details
                    val user = authRepository.getCurrentUser()
                    val premiumType = user?.premiumType ?: "premium"
                    
                    _purchaseState.value = PurchaseState.Success
                    _successMessage.value = "Your ${premiumType.lowercase()} subscription is active"
                    Log.d(TAG, "User already has premium, subscription type: $premiumType")
                } else {
                    // User is not premium, check if they should be
                    billingRepository.queryExistingPurchases()
                    
                    // Wait briefly to let the query complete
                    delay(1000)
                    
                    // Check again after query
                    val updatedIsPremium = billingRepository.isPremium.value
                    
                    if (updatedIsPremium) {
                        // Get the current user
                        val user = authRepository.getCurrentUser()
                        if (user != null) {
                            // Get the premium type from the user's data
                            val premiumType = user.premiumType ?: "monthly"
                            
                            // Update the user's premium status in Firestore
                            authRepository.updatePremiumStatus(
                                uid = user.id,
                                isPremium = true,
                                premiumType = premiumType
                            )
                            
                            // Force refresh user data to get updated subscription details
                            delay(500) // Brief delay to allow Firestore update to complete
                            authRepository.refreshPremiumStatus()
                            
                            _purchaseState.value = PurchaseState.Success
                            _successMessage.value = "Your premium subscription has been restored"
                            Log.d(TAG, "Successfully restored premium subscription")
                        } else {
                            _purchaseState.value = PurchaseState.Failed("User not found")
                            _successMessage.value = "Failed to restore: User not found"
                            Log.e(TAG, "Failed to restore subscription: User not found")
                        }
                    } else {
                        _purchaseState.value = PurchaseState.Failed("No active subscription found")
                        _successMessage.value = "No active subscription found for this account"
                        Log.d(TAG, "No active subscription found for this account")
                    }
                }
                
                // Clear message after a delay
                delay(3000)
                _successMessage.value = null
                
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring purchases", e)
                _purchaseState.value = PurchaseState.Failed("Error: ${e.message}")
                _successMessage.value = "Failed to restore: ${e.message}"
                
                // Clear error message after a delay
                delay(3000)
                _successMessage.value = null
            }
        }
    }

    // Purchase selected plan
    fun purchasePlan(activity: Activity) {
        val selectedPlan = _selectedPlan.value ?: return
        val productId = selectedPlan.productId
        
        // Update purchase state for UI feedback
        _purchaseState.value = PurchaseState.InProgress
        
        viewModelScope.launch {
            try {
                // Launch the billing flow with the selected product ID and offer token (for subscriptions)
                billingRepository.launchBillingFlow(activity, productId, selectedPlan.offerToken)
                
                // Add a fallback timeout to reset purchase state if no callback is received
                // (this is a backup in case onPurchaseCanceled is not called)
                delay(30000) // 30 second timeout
                if (_purchaseState.value == PurchaseState.InProgress) {
                    Log.d(TAG, "Purchase timed out, resetting state")
                    _purchaseState.value = PurchaseState.Failed("Purchase canceled or timed out")
                    _successMessage.value = "Purchase canceled"
                    
                    // Clear error message after a delay
                    delay(3000)
                    _successMessage.value = null
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error launching billing flow", e)
                _purchaseState.value = PurchaseState.Failed("Error: ${e.message}")
                _successMessage.value = "Failed to start purchase: ${e.message}"
                
                // Clear error message after a delay
                delay(3000)
                _successMessage.value = null
            }
        }
    }

    // For demonstration: set premium status to true
    fun purchaseForDemonstration(planType: PlanType) {
        Log.d(TAG, "Demonstration purchase of plan type: $planType")
        
        viewModelScope.launch {
            try {
                // Update premium status locally
                billingRepository.setPremiumForDemonstration(true, planType.name.lowercase())
                
                // Update purchase state for UI
                _purchaseState.value = PurchaseState.Success
                
                // Provide a toast message via the viewmodel state
                _successMessage.value = "Premium ${planType.name.lowercase()} subscription activated!"
                
                // Refresh user data to ensure all fields are up to date
                delay(1000) // Wait for Firestore update to complete
                authRepository.refreshPremiumStatus()
                
                // Clear success message after a delay
                delay(3000)
                _successMessage.value = null
            } catch (e: Exception) {
                Log.e(TAG, "Error activating premium demonstration", e)
                _purchaseState.value = PurchaseState.Failed("Failed to activate premium: ${e.message}")
            }
        }
    }

    // Sealed class for purchase state
    sealed class PurchaseState {
        object Idle : PurchaseState()
        object InProgress : PurchaseState()
        object Success : PurchaseState()
        data class Failed(val message: String) : PurchaseState()
    }
} 