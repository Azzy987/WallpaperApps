package com.droidates.wallpapers.utils

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Utility class for handling date and time formatting throughout the application.
 * Centralizes all date/time related operations to ensure consistent formatting.
 */
object DateTimeFormatter {
    private const val TAG = "DateTimeFormatter"
    
    /**
     * Format date for display in various screens.
     * 
     * @param dateString The date string to format.
     * @param pattern The date pattern to use (default is yyyy-MM-dd).
     * @return Formatted date string for display or original string if parsing fails.
     */
    fun formatDisplayDate(dateString: String, pattern: String = "yyyy-MM-dd"): String {
        if (dateString.isEmpty()) return ""
        
        return try {
            val inputFormat = SimpleDateFormat(pattern, Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            val date = inputFormat.parse(dateString)
            date?.let { outputFormat.format(it) } ?: dateString
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing date string: $dateString", e)
            dateString
        }
    }
    
    /**
     * Format timestamp as a readable date string.
     * 
     * @param timestamp The timestamp to format.
     * @param pattern The output pattern to use.
     * @return Formatted date string.
     */
    fun formatTimestamp(timestamp: Date, pattern: String = "MMM dd, yyyy"): String {
        val dateFormat = SimpleDateFormat(pattern, Locale.getDefault())
        return dateFormat.format(timestamp)
    }
    
    /**
     * Format date for premium subscription display.
     * 
     * @param date The date to format.
     * @param pattern The output pattern to use.
     * @return Formatted date string.
     */
    fun formatSubscriptionDate(date: Date, pattern: String = "MMMM dd, yyyy"): String {
        val dateFormat = SimpleDateFormat(pattern, Locale.getDefault())
        return dateFormat.format(date)
    }
    
    /**
     * Check if the current time is night time (between 6 PM and 6 AM).
     * 
     * @return True if it's night time, false otherwise.
     */
    fun isNightTime(): Boolean {
        val calendar = Calendar.getInstance()
        val hourOfDay = calendar.get(Calendar.HOUR_OF_DAY)
        return hourOfDay < 6 || hourOfDay >= 18
    }
    
    /**
     * Format a relative time string (e.g., "2 hours ago", "Yesterday", etc.).
     * 
     * @param timestamp The timestamp to format.
     * @return A human-readable relative time string.
     */
    fun getRelativeTimeSpan(timestamp: Date): String {
        val now = Date()
        val diffInMillis = now.time - timestamp.time
        
        // Convert to appropriate time units
        val diffInSeconds = TimeUnit.MILLISECONDS.toSeconds(diffInMillis)
        val diffInMinutes = TimeUnit.MILLISECONDS.toMinutes(diffInMillis)
        val diffInHours = TimeUnit.MILLISECONDS.toHours(diffInMillis)
        val diffInDays = TimeUnit.MILLISECONDS.toDays(diffInMillis)
        
        return when {
            diffInSeconds < 60 -> "Just now"
            diffInMinutes < 60 -> "$diffInMinutes ${if (diffInMinutes == 1L) "minute" else "minutes"} ago"
            diffInHours < 24 -> "$diffInHours ${if (diffInHours == 1L) "hour" else "hours"} ago"
            diffInDays < 2 -> "Yesterday"
            diffInDays < 7 -> "$diffInDays days ago"
            diffInDays < 30 -> "${diffInDays / 7} ${if (diffInDays / 7 == 1L) "week" else "weeks"} ago"
            diffInDays < 365 -> "${diffInDays / 30} ${if (diffInDays / 30 == 1L) "month" else "months"} ago"
            else -> "${diffInDays / 365} ${if (diffInDays / 365 == 1L) "year" else "years"} ago"
        }
    }
    
    /**
     * Format file size for display.
     * 
     * @param bytes The size in bytes.
     * @return Formatted size string (e.g., "2.5 MB").
     */
    fun formatFileSize(bytes: Int): String {
        return when {
            bytes < 0 -> "Unknown size"
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }
} 