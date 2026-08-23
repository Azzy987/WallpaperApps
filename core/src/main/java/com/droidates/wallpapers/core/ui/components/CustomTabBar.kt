package com.droidates.wallpapers.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import com.droidates.wallpapers.core.R
import com.droidates.wallpapers.core.ui.icons.MaterialSymbols

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CustomTabBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        TabItem(
            selectedIcon = MaterialSymbols.Navigation.Home.filled(),
            unselectedIcon = MaterialSymbols.Navigation.Home.outlined(),
            label = "HOME",
            isSelected = selectedTab == 0,
            showLabel = selectedTab == 0,
            onClick = { onTabSelected(0) }
        )
        TabItem(
            selectedIcon = Icons.Rounded.GridView,
            unselectedIcon = MaterialSymbols.Navigation.GridView.outlined(),
            label = "CATEGORIES",
            isSelected = selectedTab == 1,
            showLabel = selectedTab == 1,
            onClick = { onTabSelected(1) }
        )
        TabItem(
            selectedIcon = Icons.Rounded.LocalFireDepartment,
            unselectedIcon = Icons.Outlined.LocalFireDepartment,
            label = "TRENDING",
            isSelected = selectedTab == 2,
            showLabel = selectedTab == 2,
            onClick = { onTabSelected(2) }
        )
        FavoriteTabItem(
            label = "FAVORITES",
            isSelected = selectedTab == 3,
            showLabel = selectedTab == 3,
            onClick = { onTabSelected(3) }
        )
    }
}

@Composable
private fun TabItem(
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    label: String,
    isSelected: Boolean,
    showLabel: Boolean,
    onClick: () -> Unit
) {
    // Material 3 Morph Shapes - Shape morphs to express functional state
    val cornerRadius by animateDpAsState(
        targetValue = if (isSelected) 24.dp // Full capsule when selected (expresses completeness)
                     else 16.dp, // Less rounded when unselected (neutral state)
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "tab_corner_radius"
    )
    
    Surface(
        modifier = Modifier
            .height(48.dp)
            .widthIn(min = 48.dp), // Dynamic width based on content
        onClick = onClick,
        shape = RoundedCornerShape(cornerRadius),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (isSelected) 10.dp else 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isSelected) selectedIcon else unselectedIcon,
                contentDescription = label,
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.onSurface,
               // modifier = Modifier.size(18.dp)
            )
            if (showLabel) {
                Text(
                    text = label,
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                           else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun FavoriteTabItem(
    label: String,
    isSelected: Boolean,
    showLabel: Boolean,
    onClick: () -> Unit
) {
    // Material 3 Morph Shapes - Shape morphs to express favorite connection
    val cornerRadius by animateDpAsState(
        targetValue = if (isSelected) 24.dp // Heart-like roundness when selected (expresses love/connection)
                     else 16.dp, // Less rounded when unselected (neutral emotional state)
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "favorite_tab_corner_radius"
    )
    
    Surface(
        modifier = Modifier
            .height(48.dp)
            .widthIn(min = 48.dp), // Dynamic width based on content
        onClick = onClick,
        shape = RoundedCornerShape(cornerRadius),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (isSelected) 10.dp else 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(
                    id = if (isSelected) R.drawable.ic_favorite_rounded_filled 
                         else R.drawable.ic_favorite_rounded_outlined
                ),
                contentDescription = label,
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.onSurface
            )
            if (showLabel) {
                Text(
                    text = label,
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                           else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
        }
    }
} 