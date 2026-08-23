package com.droidates.wallpapers.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.droidates.wallpapers.core.R

val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

val bodyFontFamily = FontFamily(
    Font(
        googleFont = GoogleFont("Nunito"),
        fontProvider = provider,
    )
)

val displayFontFamily = FontFamily(
    Font(
        googleFont = GoogleFont("ABeeZee"),
        fontProvider = provider,
    )
)

// Enhanced Material 3 Expressive typography values
val baseline = Typography()

val AppTypography = Typography(
    // Display styles with enhanced expressive characteristics
    displayLarge = baseline.displayLarge.copy(
        fontFamily = displayFontFamily,
        letterSpacing = (-0.25).sp,
        lineHeight = 64.sp,
        fontWeight = FontWeight.Normal
    ),
    displayMedium = baseline.displayMedium.copy(
        fontFamily = displayFontFamily,
        letterSpacing = 0.sp,
        lineHeight = 52.sp,
        fontWeight = FontWeight.Normal
    ),
    displaySmall = baseline.displaySmall.copy(
        fontFamily = displayFontFamily,
        letterSpacing = 0.sp,
        lineHeight = 44.sp,
        fontWeight = FontWeight.Normal
    ),
    
    // Headlines with improved readability
    headlineLarge = baseline.headlineLarge.copy(
        fontFamily = displayFontFamily,
        letterSpacing = 0.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.Medium
    ),
    headlineMedium = baseline.headlineMedium.copy(
        fontFamily = displayFontFamily,
        letterSpacing = 0.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Medium
    ),
    headlineSmall = baseline.headlineSmall.copy(
        fontFamily = displayFontFamily,
        letterSpacing = 0.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Medium
    ),
    
    // Titles with enhanced spacing for better hierarchy
    titleLarge = baseline.titleLarge.copy(
        fontFamily = displayFontFamily,
        letterSpacing = 0.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold
    ),
    titleMedium = baseline.titleMedium.copy(
        fontFamily = displayFontFamily,
        letterSpacing = 0.15.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold
    ),
    titleSmall = baseline.titleSmall.copy(
        fontFamily = displayFontFamily,
        letterSpacing = 0.1.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium
    ),
    
    // Body text optimized for readability
    bodyLarge = baseline.bodyLarge.copy(
        fontFamily = bodyFontFamily,
        letterSpacing = 0.5.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal
    ),
    bodyMedium = baseline.bodyMedium.copy(
        fontFamily = bodyFontFamily,
        letterSpacing = 0.25.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal
    ),
    bodySmall = baseline.bodySmall.copy(
        fontFamily = bodyFontFamily,
        letterSpacing = 0.4.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal
    ),
    
    // Labels with enhanced legibility
    labelLarge = baseline.labelLarge.copy(
        fontFamily = bodyFontFamily,
        letterSpacing = 0.1.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium
    ),
    labelMedium = baseline.labelMedium.copy(
        fontFamily = bodyFontFamily,
        letterSpacing = 0.5.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium
    ),
    labelSmall = baseline.labelSmall.copy(
        fontFamily = bodyFontFamily,
        letterSpacing = 0.5.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium
    ),
)