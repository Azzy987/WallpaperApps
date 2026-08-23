package com.droidates.wallpapers.data.repository

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.consumePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.droidates.wallpapers.model.PremiumPlan
import com.droidates.wallpapers.model.PlanType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow
import kotlin.math.min
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.logEvent

@Singleton
class BillingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authRepository: AuthRepository
) : PurchasesUpdatedListener {

    private val analytics: FirebaseAnalytics by lazy { FirebaseAnalytics.getInstance(context) }

    companion object {
        private const val TAG = "BillingRepository"
        const val PREMIUM_LIFETIME_PRODUCT_ID = com.droidates.wallpapers.config.AppConfig.BILLING_LIFETIME_ID
    }

    private val coroutineScopeJob = SupervisorJob()
    private val coroutineScope = CoroutineScope(coroutineScopeJob + Dispatchers.IO)
    private lateinit var billingClient: BillingClient
    
    // Emulator detection
    fun isRunningInEmulator(): Boolean {
        return Build.FINGERPRINT.contains("generic") ||
                Build.FINGERPRINT.contains("unknown") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK built for x86") ||
                Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic") ||
                "google_sdk" == Build.PRODUCT ||
                Build.HARDWARE.contains("goldfish") ||
                Build.HARDWARE.contains("ranchu")
    }

    // Product details state
    private val _productDetails = MutableStateFlow<Map<String, ProductDetails>>(emptyMap())
    val productDetails: StateFlow<Map<String, ProductDetails>> = _productDetails.asStateFlow()
    
    // Product details loading state - PREMIUM SCREEN FIX
    private val _isLoadingProductDetails = MutableStateFlow(false)
    val isLoadingProductDetails: StateFlow<Boolean> = _isLoadingProductDetails.asStateFlow()

    // Premium status state
    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    // Connection state
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    // Retry management for connection failures
    private var retryCount = 0
    private val maxRetries = 5
    private var reconnectionJob: Job? = null

    // Add a listener interface for purchase updates
    interface PurchaseUpdateListener {
        fun onPurchaseSuccess()
        fun onPurchaseCanceled()
        fun onPurchaseError(message: String)
    }
    
    // MEMORY LEAK FIX: Use WeakReference for listeners to prevent memory leaks
    private val purchaseListeners = mutableListOf<WeakReference<PurchaseUpdateListener>>()
    
    // Add and remove listeners
    fun addPurchaseListener(listener: PurchaseUpdateListener) {
        // Remove any dead references first
        cleanupListeners()
        // Check for duplicates
        val exists = purchaseListeners.any { ref -> ref.get() === listener }
        if (!exists) {
            purchaseListeners.add(WeakReference(listener))
        }
    }
    
    fun removePurchaseListener(listener: PurchaseUpdateListener) {
        purchaseListeners.removeAll { ref -> ref.get() === null || ref.get() === listener }
    }
    
    // Clean up dead references
    private fun cleanupListeners() {
        purchaseListeners.removeAll { ref -> ref.get() == null }
    }
    
    // Helper methods to notify listeners safely
    private fun notifyPurchaseSuccess() {
        cleanupListeners()
        purchaseListeners.forEach { ref -> ref.get()?.onPurchaseSuccess() }
    }
    
    private fun notifyPurchaseCanceled() {
        cleanupListeners()
        purchaseListeners.forEach { ref -> ref.get()?.onPurchaseCanceled() }
    }
    
    private fun notifyPurchaseError(message: String) {
        cleanupListeners()
        purchaseListeners.forEach { ref -> ref.get()?.onPurchaseError(message) }
    }

    init {
        setupBillingClient()
        // Monitor premium status from auth repository
        coroutineScope.launch {
            authRepository.isPremiumUser.collect { isPremium ->
                _isPremium.value = isPremium
            }
        }
    }

    private fun setupBillingClient() {
        billingClient = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases() // Required for purchases support
            .build()

        connectToPlayBilling()
    }

    private fun connectToPlayBilling() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing client connected")
                    _isConnected.value = true
                    
                    // PREMIUM SCREEN FIX: Set loading state when starting to query product details
                    _isLoadingProductDetails.value = true
                    
                    // Query available products and existing purchases
                    coroutineScope.launch {
                        queryAvailableProducts()
                        queryExistingPurchases()
                    }
                } else {
                    Log.e(TAG, "Failed to connect to billing service: ${billingResult.responseCode}")
                    _isConnected.value = false
                    
                    // PREMIUM SCREEN FIX: Reset loading state on connection failure
                    _isLoadingProductDetails.value = false
                    
                    // Just log emulator detection - fallback will be handled in PremiumViewModel
                    if (isRunningInEmulator()) {
                        Log.d(TAG, "Running in emulator - billing service unavailable")
                    }
                    
                    // Schedule reconnection with exponential backoff if we haven't exceeded retries
                    if (retryCount < maxRetries) {
                        scheduleReconnection()
                    } else {
                        Log.e(TAG, "Max retries exceeded, giving up on billing connection")
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected")
                _isConnected.value = false
                scheduleReconnection() // Proper retry logic with backoff
            }
        })
    }

    private fun scheduleReconnection() {
        reconnectionJob?.cancel()

        if (retryCount >= maxRetries) {
            Log.e(TAG, "Maximum retry attempts ($maxRetries) reached. Stopping reconnection attempts.")
            return
        }

        val delayMs = min(
            (2.0.pow(retryCount.toDouble()) * 1000).toLong(),
            30000L // Max 30 seconds
        )

        reconnectionJob = coroutineScope.launch {
            try {
                kotlinx.coroutines.delay(delayMs)
                retryCount++
                connectToPlayBilling()
            } catch (e: Exception) {
                Log.e(TAG, "Error during scheduled reconnection", e)
            }
        }
    }

    private fun queryAvailableProducts() {
        coroutineScope.launch {
            try {
                Log.d(TAG, "Starting to query available products from Google Play...")

                // Query one-time purchase products
                val inAppParams = QueryProductDetailsParams.newBuilder()
                    .setProductList(
                        listOf(
                            QueryProductDetailsParams.Product.newBuilder()
                                .setProductId(PREMIUM_LIFETIME_PRODUCT_ID)
                                .setProductType(BillingClient.ProductType.INAPP)
                                .build()
                        )
                    )
                    .build()

                Log.d(TAG, "Querying in-app products: $PREMIUM_LIFETIME_PRODUCT_ID")
                val inAppResult = withContext(Dispatchers.IO) {
                    billingClient.queryProductDetails(inAppParams)
                }

                val detailsMap = mutableMapOf<String, ProductDetails>()

                if (inAppResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    inAppResult.productDetailsList?.forEach { productDetails ->
                        detailsMap[productDetails.productId] = productDetails
                        
                        // Log in-app purchase details for debugging
                        productDetails.oneTimePurchaseOfferDetails?.let { offer ->
                            Log.d(TAG, "Product ${productDetails.productId} one-time purchase: " +
                                  "price=${offer.formattedPrice}, micros=${offer.priceAmountMicros}")
                        }
                    }
                    Log.d(TAG, "In-app product details loaded: ${inAppResult.productDetailsList?.map { it.productId } ?: "none"}")
                } else {
                    Log.e(TAG, "Failed to query in-app product details. Code: ${inAppResult.billingResult.responseCode}, Message: ${inAppResult.billingResult.debugMessage}")
                }
                
                _productDetails.value = detailsMap
                Log.d(TAG, "All product details loaded: ${detailsMap.keys}")
                
                // PREMIUM SCREEN FIX: Set loading state to false when product details are loaded
                _isLoadingProductDetails.value = false
                
                if (detailsMap.isEmpty()) {
                    Log.e(TAG, "No product details were loaded. Make sure products are properly configured in Play Console and the app is signed with the correct key.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error querying product details", e)
                // PREMIUM SCREEN FIX: Reset loading state on error
                _isLoadingProductDetails.value = false
            }
        }
    }

    // Make queryExistingPurchases public for purchase restoration
    fun queryExistingPurchases() {
        coroutineScope.launch {
            try {
                Log.d(TAG, "Querying existing purchases...")
                
                // Query in-app purchases
                val inAppParams = QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()

                val inAppPurchasesResult = billingClient.queryPurchasesAsync(inAppParams)
                
                // Query subscriptions
                val subsParams = QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
                    
                val subsPurchasesResult = billingClient.queryPurchasesAsync(subsParams)
                
                // Process both purchase types
                val allPurchases = mutableListOf<Purchase>()
                
                if (inAppPurchasesResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Found ${inAppPurchasesResult.purchasesList.size} in-app purchases")
                    inAppPurchasesResult.purchasesList.forEach { purchase ->
                        Log.d(TAG, "In-app purchase: ${purchase.products}, state: ${purchase.purchaseState}")
                    }
                    allPurchases.addAll(inAppPurchasesResult.purchasesList)
                } else {
                    Log.e(TAG, "Failed to query in-app purchases. Code: ${inAppPurchasesResult.billingResult.responseCode}, Message: ${inAppPurchasesResult.billingResult.debugMessage}")
                }
                
                if (subsPurchasesResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Found ${subsPurchasesResult.purchasesList.size} subscription purchases")
                    subsPurchasesResult.purchasesList.forEach { purchase ->
                        Log.d(TAG, "Subscription purchase: ${purchase.products}, state: ${purchase.purchaseState}")
                    }
                    allPurchases.addAll(subsPurchasesResult.purchasesList)
                } else {
                    Log.e(TAG, "Failed to query subscription purchases. Code: ${subsPurchasesResult.billingResult.responseCode}, Message: ${subsPurchasesResult.billingResult.debugMessage}")
                }
                
                Log.d(TAG, "Processing ${allPurchases.size} total purchases")
                processPurchases(allPurchases)
            } catch (e: Exception) {
                Log.e(TAG, "Error querying purchases", e)
            }
        }
    }

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        Log.d(TAG, "onPurchasesUpdated: ${billingResult.responseCode}, ${billingResult.debugMessage}")
        
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases != null) {
                    Log.d(TAG, "Purchase successful, processing ${purchases.size} purchases")
                    coroutineScope.launch {
                        processPurchases(purchases)
                        // Notify listeners of success
                        notifyPurchaseSuccess()
                    }
                } else {
                    Log.w(TAG, "Purchase successful but no purchases returned")
                    // Still consider this a success
                    notifyPurchaseSuccess()
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.d(TAG, "User canceled the purchase")
                // Notify listeners of cancellation
                notifyPurchaseCanceled()
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                Log.d(TAG, "Item already owned, querying existing purchases")
                coroutineScope.launch {
                    queryExistingPurchases()
                    // Notify listeners of success (item is already owned)
                    notifyPurchaseSuccess()
                }
            }
            BillingClient.BillingResponseCode.DEVELOPER_ERROR -> {
                Log.e(TAG, "Developer error in billing: ${billingResult.debugMessage}")
                // Notify listeners of error
                notifyPurchaseError("Developer error: ${billingResult.debugMessage}")
            }
            else -> {
                Log.e(TAG, "Purchase failed. Code: ${billingResult.responseCode}, Debug: ${billingResult.debugMessage}")
                // Notify listeners of error
                notifyPurchaseError("Purchase failed: ${billingResult.debugMessage}")
            }
        }
    }

    private suspend fun processPurchases(purchases: List<Purchase>) {
        for (purchase in purchases) {
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                if (!purchase.isAcknowledged) {
                    // Acknowledge the purchase to prevent refund window from starting
                    acknowledgePurchase(purchase)
                }

                // Get current user
                val user = authRepository.getCurrentUser() ?: continue
                
                // Determine premium type based on product ID
                when {
                    purchase.products.contains(PREMIUM_LIFETIME_PRODUCT_ID) -> {
                        Log.d(TAG, "Lifetime purchase found")
                        authRepository.updatePremiumStatus(
                            uid = user.id,
                            isPremium = true,
                            premiumType = "lifetime"
                        )
                        _isPremium.value = true

                        // Firebase Analytics: Track lifetime purchase
                        try {
                            analytics.logEvent("premium_purchase") {
                                param("plan", "lifetime")
                                param("product_id", PREMIUM_LIFETIME_PRODUCT_ID)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error logging analytics: ${e.message}")
                        }
                    }
                }
            } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
                Log.d(TAG, "Purchase is pending. Handle accordingly.")
                // Handle pending purchases if needed
            }
        }
    }

    // For demonstration purposes only
    fun setPremiumForDemonstration(isPremium: Boolean, premiumType: String) {
        Log.d(TAG, "Setting premium status for demonstration: isPremium=$isPremium, type=$premiumType")
        
        // Update local state first
        _isPremium.value = isPremium
        
        // Update user premium status in Firestore if signed in
        coroutineScope.launch {
            val user = authRepository.getCurrentUser()
            if (user != null) {
                Log.d(TAG, "Updating premium status for user ${user.id}")
                authRepository.updatePremiumStatus(
                    uid = user.id,
                    isPremium = isPremium,
                    premiumType = premiumType
                )
            } else {
                Log.d(TAG, "User not signed in, only updating local state")
            }
        }
    }

    private suspend fun acknowledgePurchase(purchase: Purchase) {
        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        try {
            val result = billingClient.acknowledgePurchase(acknowledgePurchaseParams)
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "Purchase acknowledged successfully")
            } else {
                Log.e(TAG, "Failed to acknowledge purchase. Code: ${result.responseCode}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error acknowledging purchase", e)
        }
    }

    // Launch billing flow to purchase a product
    fun launchBillingFlow(activity: Activity, productId: String, offerToken: String? = null) {
        if (!_isConnected.value) {
            Log.e(TAG, "Cannot launch billing flow - not connected to Google Play")
            connectToPlayBilling()
            notifyPurchaseError("Not connected to Google Play. Please try again.")
            return
        }

        val productDetail = _productDetails.value[productId]
        if (productDetail == null) {
            Log.e(TAG, "Product details not found for $productId")
            queryAvailableProducts()
            notifyPurchaseError("Product details not available. Please try again in a moment.")
            return
        }

        try {
            Log.d(TAG, "Launching billing flow for $productId")
            
            val productDetailsParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetail)

            // Apply offer token if provided (reserved for future subscription products if ever re-added)
            if (offerToken != null) {
                productDetailsParamsBuilder.setOfferToken(offerToken)
            }
            
            val billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productDetailsParamsBuilder.build()))
                .build()

            val billingResult = billingClient.launchBillingFlow(activity, billingFlowParams)
            
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.e(TAG, "Failed to launch billing flow. Code: ${billingResult.responseCode}, Debug: ${billingResult.debugMessage}")
            } else {
                Log.d(TAG, "Billing flow launched successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception launching billing flow", e)
            throw e  // Rethrow to notify caller
        }
    }

    // Get premium plans with pricing from product details
    fun getPremiumPlans(): List<PremiumPlan> {
        val plans = mutableListOf<PremiumPlan>()
        
        Log.d(TAG, "getPremiumPlans: Starting to build premium plans from product details")
        Log.d(TAG, "Available product IDs: ${_productDetails.value.keys}")

        // Add lifetime plan if available
        _productDetails.value[PREMIUM_LIFETIME_PRODUCT_ID]?.let { details ->
            Log.d(TAG, "Lifetime plan found: ${details.title}")
            val oneTimePurchaseOfferDetails = details.oneTimePurchaseOfferDetails
            if (oneTimePurchaseOfferDetails != null) {
                // PRICING LOGIC: Show Play Console price. Fallback used only when billing isn't ready.
                val basePrice = com.droidates.wallpapers.config.AppConfig.BILLING_LIFETIME_FALLBACK_PRICE
                val basePriceMicros = 199_000_000L // micros for ₹199
                val actualPriceInStandardUnits = oneTimePurchaseOfferDetails.priceAmountMicros / 1_000_000.0
                
                // Lifetime pricing configuration (logs removed)
                
                plans.add(
                    PremiumPlan(
                        productId = PREMIUM_LIFETIME_PRODUCT_ID,
                        name = "Lifetime Access",
                        description = "One-time purchase, forever access",
                        price = basePrice, // App price (for strikethrough)
                        priceAmountMicros = basePriceMicros, // App price in micros
                        priceCurrencyCode = oneTimePurchaseOfferDetails.priceCurrencyCode,
                        type = PlanType.LIFETIME,
                        billingPeriod = "lifetime",
                        formattedPrice = basePrice, // App price (for strikethrough)
                        discountedPrice = actualPriceInStandardUnits, // Play Console price (discounted)
                        features = listOf(
                            "Ad-Free Experience",
                            "Exclusive Wallpapers",
                            "Premium Filters",
                            "Advanced Editing",
                            "Priority Updates",
                            "Lifetime Updates"
                        )
                    )
                )
            }
        } ?: Log.e(TAG, "Lifetime plan not found in product details")
        
        if (plans.isEmpty()) {
            Log.e(TAG, "No premium plans could be created from product details")
        } else {
            Log.d(TAG, "Created ${plans.size} premium plans successfully")
            plans.forEach { plan ->
                Log.d(TAG, "Plan: ${plan.name}, Price: ${plan.formattedPrice}, ID: ${plan.productId}, Discounted: ${plan.discountedPrice}")
            }
        }
        
        return plans
    }

    fun close() {
        reconnectionJob?.cancel()
        coroutineScopeJob.cancel()
        if (::billingClient.isInitialized && billingClient.isReady) {
            billingClient.endConnection()
        }
    }
}