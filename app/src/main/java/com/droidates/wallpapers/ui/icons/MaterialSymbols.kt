package com.droidates.wallpapers.ui.icons

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.droidates.wallpapers.R

/**
 * Material 3 Symbols (Rounded) - Modern icon set with consistent design language
 * 
 * This object provides access to Material Symbols Rounded icons with both
 * filled and unfilled (outlined) variants, following Material 3 design principles.
 * All icons use rounded corners for a softer, more modern appearance.
 */
object MaterialSymbols {
    
    /**
     * Navigation tab icons with filled and unfilled (rounded outline) variants
     */
    object Navigation {
        object Home {
            @Composable
            fun filled(): ImageVector = ImageVector.vectorResource(R.drawable.home_filled)
            
            @Composable
            fun outlined(): ImageVector = ImageVector.vectorResource(R.drawable.home)
        }
        
        object GridView {
            @Composable
            fun filled(): ImageVector = ImageVector.vectorResource(R.drawable.grid)
            
            @Composable
            fun outlined(): ImageVector = ImageVector.vectorResource(R.drawable.grid)
        }
        
        object TrendingUp {
            @Composable
            fun filled(): ImageVector = ImageVector.vectorResource(R.drawable.grid)
            
            @Composable
            fun outlined(): ImageVector = ImageVector.vectorResource(R.drawable.grid)
        }
        
        object Favorite {
            @Composable
            fun filled(): ImageVector = ImageVector.vectorResource(R.drawable.ic_favorite_rounded_filled)
            
            @Composable
            fun outlined(): ImageVector = ImageVector.vectorResource(R.drawable.ic_favorite_rounded_outlined)
        }
    }
    
    /**
     * Common action icons with rounded variants
     */
    object Actions {
        object Favorite {
            @Composable
            fun filled(): ImageVector = ImageVector.vectorResource(R.drawable.ic_favorite_rounded_filled)
            
            @Composable
            fun outlined(): ImageVector = ImageVector.vectorResource(R.drawable.ic_favorite_rounded_outlined)
        }
        
        object Close {
            @Composable
            fun rounded(): ImageVector = ImageVector.vectorResource(R.drawable.ic_close_rounded)
        }
        
        object Share {
            @Composable
            fun rounded(): ImageVector = ImageVector.vectorResource(R.drawable.ic_info_rounded)
        }
        
        object FormatPaint {
            @Composable
            fun rounded(): ImageVector = ImageVector.vectorResource(R.drawable.ic_format_paint_rounded)
        }
        
        object Info {
            @Composable
            fun rounded(): ImageVector = ImageVector.vectorResource(R.drawable.ic_info_rounded)
        }
        
        object Settings {
            @Composable
            fun rounded(): ImageVector = ImageVector.vectorResource(R.drawable.ic_settings_rounded)
        }
    }
}