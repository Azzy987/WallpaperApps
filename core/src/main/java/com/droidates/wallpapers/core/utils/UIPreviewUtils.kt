package com.droidates.wallpapers.core.utils

import androidx.compose.ui.graphics.Color
import java.util.Calendar
import java.util.Locale

/**
 * Utility class for handling UI preview related functions across the application.
 * Centralizes functions related to rendering preview elements.
 */
object UIPreviewUtils {
    /**
     * Get gradient colors for weather widget based on time of day.
     * 
     * @return List of colors for the gradient
     */
    fun getWeatherGradientColors(): List<Color> {
        return if (DateTimeFormatter.isNightTime()) {
            // Night gradient (dark blue to lighter blue)
            listOf(
                Color(0xFF1A237E),
                Color(0xFF283593)
            )
        } else {
            // Day gradient (light blue to slightly darker blue)
            listOf(
                Color(0xFF2196F3),
                Color(0xFF1976D2)
            )
        }
    }
    
    /**
     * Get system UI colors for home screen preview.
     * 
     * @return Map of UI component colors
     */
    fun getSystemUIColors(): Map<String, Color> {
        val isNightTime = DateTimeFormatter.isNightTime()
        
        return if (isNightTime) {
            mapOf(
                "statusBar" to Color(0xFF000000),
                "navigationBar" to Color(0xFF000000),
                "systemIcons" to Color.White
            )
        } else {
            mapOf(
                "statusBar" to Color(0xFFFFFFFF),
                "navigationBar" to Color(0xFFFFFFFF),
                "systemIcons" to Color.Black
            )
        }
    }
    
    /**
     * Get current time formatted for displaying in a clock widget.
     * 
     * @param use24HourFormat Whether to use 24-hour format
     * @return Formatted time string
     */
    fun getCurrentTimeForDisplay(use24HourFormat: Boolean = false): String {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        
        return if (use24HourFormat) {
            String.format(Locale.US, "%02d:%02d", hour, minute)
        } else {
            val hourIn12 = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
            val amPm = if (hour < 12) "AM" else "PM"
            String.format(Locale.US, "%d:%02d %s", hourIn12, minute, amPm)
        }
    }
    
    /**
     * Get current day and date for displaying in a calendar widget.
     * 
     * @return Formatted day and date string
     */
    fun getCurrentDayAndDate(): String {
        val calendar = Calendar.getInstance()
        val dayNames = arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        val monthNames = arrayOf("January", "February", "March", "April", "May", "June", 
                                "July", "August", "September", "October", "November", "December")
        
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1
        val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
        val month = calendar.get(Calendar.MONTH)
        
        return "${dayNames[dayOfWeek]}, ${monthNames[month]} $dayOfMonth"
    }
} 
