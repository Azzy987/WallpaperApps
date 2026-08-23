package com.droidates.wallpapers.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A button that appears when the user scrolls down in a list or grid, allowing them to
 * quickly scroll back to the top.
 *
 * @param visible Whether the button should be visible, can be a Boolean or State<Boolean>
 * @param onClick Callback to be invoked when the button is clicked
 * @param modifier Modifier for the button
 */
@Composable
fun ScrollToTopButton(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
        modifier = modifier.padding(16.dp)
    ) {
        FloatingActionButton(
            onClick = onClick
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Scroll to top"
            )
        }
    }
}

/**
 * Overload for ScrollToTopButton that accepts a State<Boolean>
 */
@Composable
fun ScrollToTopButton(
    visible: State<Boolean>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ScrollToTopButton(
        visible = visible.value,
        onClick = onClick,
        modifier = modifier
    )
}

/**
 * Utility function to determine if the scroll to top button should be shown based on scroll position
 * 
 * @param listState The LazyListState to monitor for scroll position
 * @param threshold The number of items that must be scrolled before showing the button
 * @return Boolean indicating whether the button should be shown
 */
@Composable
fun rememberScrollToTopState(
    listState: LazyListState,
    threshold: Int = 2
): State<Boolean> {
    return remember {
        derivedStateOf {
            listState.firstVisibleItemIndex >= threshold
        }
    }
}

/**
 * Utility function to determine if the scroll to top button should be shown for grid layouts
 * 
 * @param gridState The LazyGridState to monitor for scroll position
 * @param threshold The number of items that must be scrolled before showing the button
 * @return Boolean indicating whether the button should be shown
 */
@Composable
fun rememberScrollToTopGridState(
    gridState: LazyGridState,
    threshold: Int = 2
): State<Boolean> {
    return remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex >= threshold
        }
    }
}

/**
 * Smoothly scrolls to the top of a LazyListState
 * 
 * @param scope The coroutine scope to launch the animation in
 * @param state The LazyListState to scroll
 */
fun scrollToTop(scope: CoroutineScope, state: LazyListState) {
    scope.launch {
        state.animateScrollToItem(0)
    }
}

/**
 * Smoothly scrolls to the top of a LazyGridState
 * 
 * @param scope The coroutine scope to launch the animation in
 * @param state The LazyGridState to scroll
 */
fun scrollToGridTop(scope: CoroutineScope, state: LazyGridState) {
    scope.launch {
        state.animateScrollToItem(0)
    }
} 