package com.droidates.wallpapers.core.utils

import java.util.Locale

/**
 * Utility class for formatting various statistics throughout the application.
 * Centralizes all statistics formatting operations for consistent presentation.
 */
object StatFormatter {
    /**
     * Format numerical statistics with K/M suffix for better readability.
     * Example: 1500 -> 1.5K, 1000000 -> 1.0M
     *
     * @param value The numerical value to format
     * @return Formatted string with appropriate suffix
     */
    fun formatStatValue(value: Int): String {
        return when {
            value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
            value >= 1_000 -> String.format(Locale.US, "%.1fK", value / 1_000.0)
            else -> value.toString()
        }
    }
    
    /**
     * Format percentage values for display.
     *
     * @param value The percentage value (0-100)
     * @param includeSymbol Whether to include the % symbol
     * @return Formatted percentage string
     */
    fun formatPercentage(value: Float, includeSymbol: Boolean = true): String {
        val formatted = String.format(Locale.US, "%.0f", value)
        return if (includeSymbol) "$formatted%" else formatted
    }
    
    /**
     * Format rating values (typically 0-5) for display.
     *
     * @param rating The rating value to format
     * @param maxValue The maximum possible rating value (default is 5)
     * @return Formatted rating string with up to one decimal place
     */
    fun formatRating(rating: Float, maxValue: Int = 5): String {
        return String.format(Locale.US, "%.1f", rating)
    }
} 
