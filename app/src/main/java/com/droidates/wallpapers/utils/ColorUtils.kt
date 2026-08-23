package com.droidates.wallpapers.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log

/**
 * Data class to hold various extracted colors from an image
 */
data class ExtractedColors(
    val dominantColor: Color = Color.Black,
    val darkMutedColor: Color = Color.DarkGray,
    val lightMutedColor: Color = Color.LightGray,
    val darkVibrantColor: Color = Color.DarkGray,
    val lightVibrantColor: Color = Color.White,
    val mutedColor: Color = Color.Gray,
    val vibrantColor: Color = Color.Gray,
    val isLight: Boolean = false
)

/**
 * Utility class for extracting colors from images
 */
object ColorUtils {
    
    private val colorCache = mutableMapOf<String, ExtractedColors>()
    
    /**
     * Extract dominant colors from an image URL using Android's Palette API
     * @param context Application context
     * @param imageUrl URL of the image to analyze
     * @return ExtractedColors object containing various color schemes
     */
    suspend fun extractColorsFromUrl(
        context: Context, 
        imageUrl: String
    ): ExtractedColors = withContext(Dispatchers.IO) {
        try {
            // Check if colors are already cached
            colorCache[imageUrl]?.let { 
                Log.d("ColorUtils", "Using cached colors for $imageUrl")
                return@withContext it 
            }
            
            // Load the image using Coil
            val loader = ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .allowHardware(false) // Needed for Palette API access to bitmap pixels
                .build()
            
            val result = (loader.execute(request) as? SuccessResult)?.drawable
            val bitmap = (result as? BitmapDrawable)?.bitmap
            
            if (bitmap == null) {
                Log.e("ColorUtils", "Failed to load bitmap from $imageUrl")
                val defaultColors = ExtractedColors()
                colorCache[imageUrl] = defaultColors
                return@withContext defaultColors
            }
            
            // Extract colors using Palette API
            val palette = Palette.from(bitmap).generate()
            
            // Convert to Compose Color objects
            val extractedColors = ExtractedColors(
                dominantColor = palette.dominantSwatch?.rgb?.let { Color(it) } ?: Color.Black,
                darkMutedColor = palette.darkMutedSwatch?.rgb?.let { Color(it) } ?: Color.DarkGray,
                lightMutedColor = palette.lightMutedSwatch?.rgb?.let { Color(it) } ?: Color.LightGray,
                darkVibrantColor = palette.darkVibrantSwatch?.rgb?.let { Color(it) } ?: Color.DarkGray,
                lightVibrantColor = palette.lightVibrantSwatch?.rgb?.let { Color(it) } ?: Color.White,
                mutedColor = palette.mutedSwatch?.rgb?.let { Color(it) } ?: Color.Gray,
                vibrantColor = palette.vibrantSwatch?.rgb?.let { Color(it) } ?: Color.Gray,
                isLight = palette.dominantSwatch?.bodyTextColor == android.graphics.Color.BLACK
            )
            
            // Cache the extracted colors
            colorCache[imageUrl] = extractedColors
            
            Log.d("ColorUtils", "Extracted colors from $imageUrl: dominant=${extractedColors.dominantColor}")
            return@withContext extractedColors
        } catch (e: Exception) {
            Log.e("ColorUtils", "Error extracting colors: ${e.message}", e)
            val defaultColors = ExtractedColors()
            return@withContext defaultColors
        }
    }
    
    /**
     * Get a text color that ensures readability on the given background color
     * @param backgroundColor The background color
     * @return A text color (either white or black) that contrasts with the background
     */
    fun getTextColorForBackground(backgroundColor: Color): Color {
        val luminance = (0.299 * backgroundColor.red + 0.587 * backgroundColor.green + 0.114 * backgroundColor.blue)
        return if (luminance > 0.5f) Color.Black else Color.White
    }
} 