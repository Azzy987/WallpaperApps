package com.droidates.wallpapers.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest

private const val GRID_COLUMNS = 3
private val TILE_CORNER = 14.dp
private val GRID_SPACING = 6.dp
private const val COLUMN_STAGGER_FRACTION = 0.28f
private const val TILE_FADE_MS = 420

private val TILE_HEIGHT_PATTERN = floatArrayOf(1.0f, 1.38f, 0.9f, 1.28f, 0.95f, 1.2f)

@Composable
fun PaywallWallpaperGrid(
    urls: List<String>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primaryContainer)
    ) {
        val cellWidth = (maxWidth - GRID_SPACING * (GRID_COLUMNS - 1)) / GRID_COLUMNS
        val gridMaxHeight = maxHeight

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(GRID_SPACING)
        ) {
            repeat(GRID_COLUMNS) { col ->
                val staggerOffset =
                    if (col % 2 == 1) cellWidth * COLUMN_STAGGER_FRACTION else 0.dp

                val columnTiles = remember(urls, gridMaxHeight, cellWidth, col) {
                    buildColumnTiles(
                        urls = urls,
                        column = col,
                        cellWidth = cellWidth,
                        maxHeight = gridMaxHeight,
                        staggerOffset = staggerOffset
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .offset(y = staggerOffset),
                    verticalArrangement = Arrangement.spacedBy(GRID_SPACING)
                ) {
                    columnTiles.forEach { tile ->
                        val decodeWidth = with(density) { cellWidth.roundToPx() }
                        val decodeHeight = with(density) { tile.height.roundToPx() }

                        PaywallGridTile(
                            url = tile.url,
                            width = cellWidth,
                            height = tile.height,
                            imageRequest = remember(tile.url, decodeWidth, decodeHeight) {
                                if (tile.url.isNullOrBlank()) null
                                else ImageRequest.Builder(context)
                                    .data(tile.url)
                                    .size(decodeWidth, decodeHeight)
                                    .build()
                            }
                        )
                    }
                }
            }
        }
    }
}

private data class ColumnTile(val url: String?, val height: Dp)

private fun buildColumnTiles(
    urls: List<String>,
    column: Int,
    cellWidth: Dp,
    maxHeight: Dp,
    staggerOffset: Dp
): List<ColumnTile> {
    val tiles = mutableListOf<ColumnTile>()
    var usedHeight = staggerOffset
    var row = 0

    while (row < 14) {
        val multiplier = TILE_HEIGHT_PATTERN[(row + column) % TILE_HEIGHT_PATTERN.size]
        val tileHeight = cellWidth * multiplier
        val nextHeight = usedHeight + tileHeight
        if (tiles.isNotEmpty() && nextHeight > maxHeight) break

        val urlIndex = row * GRID_COLUMNS + column
        val url = if (urls.isEmpty()) null else urls[urlIndex % urls.size]
        tiles.add(ColumnTile(url = url, height = tileHeight))
        usedHeight = nextHeight + GRID_SPACING
        row++
    }

    if (tiles.isEmpty()) {
        tiles.add(ColumnTile(url = urls.firstOrNull(), height = cellWidth))
    }
    return tiles
}

@Composable
private fun PaywallGridTile(
    url: String?,
    width: Dp,
    height: Dp,
    imageRequest: ImageRequest?
) {
    val shape = RoundedCornerShape(TILE_CORNER)
    val blankColor = MaterialTheme.colorScheme.surface

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(shape)
            .background(blankColor)
    ) {
        if (imageRequest != null) {
            SubcomposeAsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {},
                error = {},
                success = { state ->
                    val fadeAlpha = remember { Animatable(0f) }
                    LaunchedEffect(state.painter) {
                        fadeAlpha.snapTo(0f)
                        fadeAlpha.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(TILE_FADE_MS, easing = FastOutSlowInEasing)
                        )
                    }
                    Image(
                        painter = state.painter,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = fadeAlpha.value }
                    )
                }
            )
        }
    }
}
