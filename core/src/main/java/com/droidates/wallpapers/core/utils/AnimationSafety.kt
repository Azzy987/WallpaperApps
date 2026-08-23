package com.droidates.wallpapers.core.utils

fun Float.orSafe(fallback: Float = 1f): Float {
    return if (this.isNaN() || this.isInfinite()) fallback else this
}

fun Float.toSafeScale(min: Float = 0.1f, max: Float = 3.0f): Float {
    return this.orSafe(1f).coerceIn(min, max)
}

fun Float.toSafeAlpha(): Float {
    return this.orSafe(1f).coerceIn(0f, 1f)
}

fun Float.toSafeTranslation(): Float {
    return this.orSafe(0f)
}
