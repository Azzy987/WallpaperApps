package com.droidates.wallpapers.core.model

import androidx.compose.runtime.Immutable

@Immutable
data class PremiumPlan(
    val productId: String,
    val name: String,
    val description: String,
    val price: String,
    val priceAmountMicros: Long,
    val priceCurrencyCode: String,
    val type: PlanType,
    val billingPeriod: String? = null,
    val formattedPrice: String = price,
    val isSelected: Boolean = false,
    val features: List<String> = emptyList(),
    val offerToken: String? = null,
    val discountedPrice: Double? = null
) {
    val id: String
        get() = when (type) {
            PlanType.LIFETIME -> "lifetime"
        }
} 