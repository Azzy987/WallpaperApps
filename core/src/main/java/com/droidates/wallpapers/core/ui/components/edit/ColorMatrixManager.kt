package com.droidates.wallpapers.core.ui.components.edit

import androidx.compose.ui.graphics.ColorMatrix
import com.droidates.wallpapers.core.ui.components.ImageFilter
import kotlin.math.PI

/**
 * A utility class to manage color matrix operations for image editing.
 * This class handles various image adjustments like brightness, contrast, saturation, hue, opacity,
 * and applies predefined filters.
 */
class ColorMatrixManager {
    companion object {
        /**
         * Creates a color matrix with all the specified adjustments and filter applied.
         *
         * @param brightness The brightness adjustment value (-1f to 1f)
         * @param contrast The contrast adjustment value (-1f to 1f)
         * @param saturation The saturation adjustment value (-1f to 1f)
         * @param hue The hue adjustment value (-1f to 1f)
         * @param opacity The opacity value (0.1f to 1f)
         * @param selectedFilter The selected filter to apply (null for no filter)
         * @return A ColorMatrix with all adjustments applied
         */
        fun createColorMatrix(
            brightness: Float = 0f,
            contrast: Float = 0f,
            saturation: Float = 0f,
            hue: Float = 0f,
            opacity: Float = 1f,
            selectedFilter: ImageFilter? = null
        ): ColorMatrix {
            val colorMatrix = ColorMatrix()

            // Apply brightness adjustment
            if (brightness != 0f) {
                val brightnessMatrix = ColorMatrix().apply {
                    val brightnessValue = brightness * 255f
                    values[4] = brightnessValue  // Red offset
                    values[9] = brightnessValue  // Green offset
                    values[14] = brightnessValue // Blue offset
                }
                colorMatrix.timesAssign(brightnessMatrix)
            }

            // Apply contrast adjustment
            if (contrast != 0f) {
                val contrastMatrix = ColorMatrix().apply {
                    val scale = contrast + 1f
                    val translate = (-.5f * scale + .5f) * 255f

                    values[0] = scale
                    values[6] = scale
                    values[12] = scale

                    values[4] = translate  // Red offset
                    values[9] = translate  // Green offset
                    values[14] = translate // Blue offset
                }
                colorMatrix.timesAssign(contrastMatrix)
            }

            // Apply saturation adjustment
            if (saturation != 0f) {
                val saturationMatrix = ColorMatrix().apply {
                    val saturationValue = saturation + 1f

                    // Formula for saturation
                    val r = 0.213f
                    val g = 0.715f
                    val b = 0.072f

                    val sr = (1 - saturationValue) * r
                    val sg = (1 - saturationValue) * g
                    val sb = (1 - saturationValue) * b

                    values[0] = sr + saturationValue
                    values[1] = sr
                    values[2] = sr

                    values[5] = sg
                    values[6] = sg + saturationValue
                    values[7] = sg

                    values[10] = sb
                    values[11] = sb
                    values[12] = sb + saturationValue
                }
                colorMatrix.timesAssign(saturationMatrix)
            }

            // Apply hue adjustment
            if (hue != 0f) {
                val hueMatrix = ColorMatrix().apply {
                    val cosVal = kotlin.math.cos(hue * PI.toFloat())
                    val sinVal = kotlin.math.sin(hue * PI.toFloat())

                    val lumR = 0.213f
                    val lumG = 0.715f
                    val lumB = 0.072f

                    values[0] = lumR + cosVal * (1 - lumR) + sinVal * (-lumR)
                    values[1] = lumG + cosVal * (-lumG) + sinVal * (-lumG)
                    values[2] = lumB + cosVal * (-lumB) + sinVal * (1 - lumB)

                    values[5] = lumR + cosVal * (-lumR) + sinVal * (0.143f)
                    values[6] = lumG + cosVal * (1 - lumG) + sinVal * (0.140f)
                    values[7] = lumB + cosVal * (-lumB) + sinVal * (-0.283f)

                    values[10] = lumR + cosVal * (-lumR) + sinVal * (-(1 - lumR))
                    values[11] = lumG + cosVal * (-lumG) + sinVal * (lumG)
                    values[12] = lumB + cosVal * (1 - lumB) + sinVal * (lumB)
                }
                colorMatrix.timesAssign(hueMatrix)
            }

            // Apply opacity adjustment
            if (opacity < 1f) {
                val opacityMatrix = ColorMatrix().apply {
                    values[18] = opacity // Alpha scale
                }
                colorMatrix.timesAssign(opacityMatrix)
            }

            // Apply selected filter if any
            selectedFilter?.let { filter ->
                val filterMatrix = ColorMatrix()

                when (filter) {
                    ImageFilter.NONE -> {
                        // No filter - use identity matrix
                        filterMatrix.values[0] = 1f
                        filterMatrix.values[6] = 1f
                        filterMatrix.values[12] = 1f
                    }
                    ImageFilter.INVERT -> {
                        // Invert colors
                        filterMatrix.values[0] = -1f
                        filterMatrix.values[6] = -1f
                        filterMatrix.values[12] = -1f
                        filterMatrix.values[4] = 255f
                        filterMatrix.values[9] = 255f
                        filterMatrix.values[14] = 255f
                    }
                    ImageFilter.GRAYSCALE -> {
                        // Black and white filter
                        filterMatrix.values[0] = 0.33f
                        filterMatrix.values[1] = 0.33f
                        filterMatrix.values[2] = 0.33f
                        filterMatrix.values[5] = 0.33f
                        filterMatrix.values[6] = 0.33f
                        filterMatrix.values[7] = 0.33f
                        filterMatrix.values[10] = 0.33f
                        filterMatrix.values[11] = 0.33f
                        filterMatrix.values[12] = 0.33f
                    }
                    ImageFilter.SEPIA -> {
                        // Sepia tone filter
                        filterMatrix.values[0] = 0.393f
                        filterMatrix.values[1] = 0.769f
                        filterMatrix.values[2] = 0.189f
                        filterMatrix.values[5] = 0.349f
                        filterMatrix.values[6] = 0.686f
                        filterMatrix.values[7] = 0.168f
                        filterMatrix.values[10] = 0.272f
                        filterMatrix.values[11] = 0.534f
                        filterMatrix.values[12] = 0.131f
                    }
                    ImageFilter.VINTAGE -> {
                        // Vintage filter
                        filterMatrix.values[0] = 0.9f
                        filterMatrix.values[6] = 0.7f
                        filterMatrix.values[12] = 0.5f
                        filterMatrix.values[4] = 40f / 255f
                        filterMatrix.values[9] = 20f / 255f
                        filterMatrix.values[14] = 10f / 255f
                    }
                    ImageFilter.COOL -> {
                        // Cool filter
                        filterMatrix.values[0] = 0.8f
                        filterMatrix.values[6] = 0.9f
                        filterMatrix.values[12] = 1.2f
                    }
                    ImageFilter.WARM -> {
                        // Warm filter
                        filterMatrix.values[0] = 1.2f
                        filterMatrix.values[6] = 0.9f
                        filterMatrix.values[12] = 0.8f
                    }
                    ImageFilter.CYBERPUNK -> {
                        // CyberPunk filter (blue/pink neon glow)
                        filterMatrix.values[0] = 0.8f
                        filterMatrix.values[6] = 0.9f
                        filterMatrix.values[12] = 1.5f
                        filterMatrix.values[4] = 30f / 255f
                        filterMatrix.values[9] = 10f / 255f
                        filterMatrix.values[14] = 70f / 255f
                    }
                    ImageFilter.NEON -> {
                        // Neon filter (purple glow)
                        filterMatrix.values[0] = 1.3f
                        filterMatrix.values[6] = 0.5f
                        filterMatrix.values[12] = 1.3f
                        filterMatrix.values[4] = 20f / 255f
                        filterMatrix.values[14] = 30f / 255f
                    }
                    ImageFilter.RETROWAVE -> {
                        // Retrowave filter (80s style pink/blue)
                        filterMatrix.values[0] = 1.2f
                        filterMatrix.values[6] = 0.8f
                        filterMatrix.values[12] = 1.3f
                        filterMatrix.values[4] = 40f / 255f
                        filterMatrix.values[14] = 50f / 255f
                    }
                    ImageFilter.MIDNIGHT -> {
                        // Midnight filter (dark blue tint)
                        filterMatrix.values[0] = 0.7f
                        filterMatrix.values[6] = 0.8f
                        filterMatrix.values[12] = 1.3f
                        filterMatrix.values[14] = 20f / 255f
                    }
                    ImageFilter.SYNTHWAVE -> {
                        // Synthwave filter (futuristic look)
                        filterMatrix.values[0] = 1.3f
                        filterMatrix.values[6] = 0.7f
                        filterMatrix.values[12] = 1.4f
                        filterMatrix.values[4] = 25f / 255f
                        filterMatrix.values[14] = 40f / 255f
                    }
                    ImageFilter.LOFI -> {
                        // Lo-Fi filter (high saturation)
                        filterMatrix.values[0] = 1.2f
                        filterMatrix.values[6] = 1.2f
                        filterMatrix.values[12] = 1.2f
                    }
                    ImageFilter.MOON -> {
                        // Moon filter (blue-tinted B&W)
                        filterMatrix.values[0] = 0.3f
                        filterMatrix.values[1] = 0.3f
                        filterMatrix.values[2] = 0.3f
                        filterMatrix.values[5] = 0.3f
                        filterMatrix.values[6] = 0.3f
                        filterMatrix.values[7] = 0.3f
                        filterMatrix.values[10] = 0.3f
                        filterMatrix.values[11] = 0.3f
                        filterMatrix.values[12] = 0.4f
                        filterMatrix.values[14] = 10f / 255f
                    }
                    ImageFilter.SUNSET -> {
                        // Sunset filter (orange/red glow)
                        filterMatrix.values[0] = 1.4f
                        filterMatrix.values[6] = 0.8f
                        filterMatrix.values[12] = 0.6f
                        filterMatrix.values[4] = 40f / 255f
                        filterMatrix.values[9] = 10f / 255f
                    }
                    ImageFilter.APOCALYPSE -> {
                        // Apocalypse filter (dark orange/red)
                        filterMatrix.values[0] = 1.5f
                        filterMatrix.values[6] = 0.7f
                        filterMatrix.values[12] = 0.4f
                        filterMatrix.values[4] = 30f / 255f
                        filterMatrix.values[9] = 5f / 255f
                    }
                    ImageFilter.DYSTOPIA -> {
                        // Dystopia filter (faded blue/gray)
                        filterMatrix.values[0] = 0.6f
                        filterMatrix.values[6] = 0.7f
                        filterMatrix.values[12] = 0.8f
                        filterMatrix.values[14] = 15f / 255f
                    }
                    ImageFilter.MATRIX -> {
                        // Matrix filter (green digital)
                        filterMatrix.values[0] = 0.5f
                        filterMatrix.values[6] = 1.4f
                        filterMatrix.values[12] = 0.5f
                        filterMatrix.values[9] = 30f / 255f
                    }
                    ImageFilter.ARCTIC -> {
                        // Arctic filter (cold blue/white)
                        filterMatrix.values[0] = 0.8f
                        filterMatrix.values[6] = 1.0f
                        filterMatrix.values[12] = 1.4f
                        filterMatrix.values[14] = 25f / 255f
                    }
                    ImageFilter.DESERT -> {
                        // Desert filter (warm orange/yellow)
                        filterMatrix.values[0] = 1.6f
                        filterMatrix.values[6] = 1.2f
                        filterMatrix.values[12] = 0.8f
                        filterMatrix.values[4] = 35f / 255f
                        filterMatrix.values[9] = 20f / 255f
                    }
                    ImageFilter.VAPORWAVE -> {
                        // Vaporwave filter (pink/blue retro)
                        filterMatrix.values[0] = 1.1f
                        filterMatrix.values[6] = 0.9f
                        filterMatrix.values[12] = 1.4f
                        filterMatrix.values[4] = 30f / 255f
                        filterMatrix.values[14] = 40f / 255f
                    }
                }

                colorMatrix.timesAssign(filterMatrix)
            }

            return colorMatrix
        }
    }
} 