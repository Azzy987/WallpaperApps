package com.droidates.wallpapers.utils

/**
 * Animation Safety Utilities
 *
 * Provides safe fallback values for animations to prevent NaN crashes on older Android versions.
 *
 * Critical for Android 9 (SDK 28) compatibility where animation calculations can produce NaN values
 * due to precision issues or timing edge cases in Compose animations.
 */

/**
 * Ensures a Float value is safe for use in graphicsLayer animations.
 * Returns a fallback value if the input is NaN or Infinite.
 *
 * @param fallback The value to return if this Float is invalid (default: 1.0f for scale, 0.0f for translation)
 * @return A safe Float value that won't crash graphicsLayer
 */
fun Float.orSafe(fallback: Float = 1f): Float {
    return if (this.isNaN() || this.isInfinite()) fallback else this
}

/**
 * Ensures a scale value is safe and within reasonable bounds.
 *
 * @param min Minimum allowed scale (default: 0.1f)
 * @param max Maximum allowed scale (default: 3.0f)
 * @return A safe scale value between min and max
 */
fun Float.toSafeScale(min: Float = 0.1f, max: Float = 3.0f): Float {
    return this.orSafe(1f).coerceIn(min, max)
}

/**
 * Ensures an alpha value is safe and within 0..1 range.
 *
 * @return A safe alpha value between 0 and 1
 */
fun Float.toSafeAlpha(): Float {
    return this.orSafe(1f).coerceIn(0f, 1f)
}

/**
 * Ensures a translation value is safe for graphicsLayer.
 *
 * @return A safe translation value (0f if NaN/Infinite)
 */
fun Float.toSafeTranslation(): Float {
    return this.orSafe(0f)
}
