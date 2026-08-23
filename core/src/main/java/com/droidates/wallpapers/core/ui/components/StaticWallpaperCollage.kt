package com.droidates.wallpapers.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import kotlin.math.sqrt

private const val GRID_COLUMNS = 3
private const val GRID_ROWS = 3
private const val GRID_TILE_COUNT = GRID_COLUMNS * GRID_ROWS
/** Was 0.7 (−30%); +25% → 0.875. */
private const val TILE_SIZE_SCALE = 0.875f
private const val GRID_ROTATION_DEGREES = 14f
private val TILE_ROTATIONS = floatArrayOf(-8f, 5f, -6f, 4f, -7f, 6f, -5f, 7f, -4f)

@Composable
fun StaticWallpaperCollage(
    urls: List<String>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val tileUrls = remember(urls) {
        if (urls.isEmpty()) {
            List(GRID_TILE_COUNT) { null }
        } else {
            List(GRID_TILE_COUNT) { index -> urls[index % urls.size] }
        }
    }

    val coverScale = sqrt(2f) + 0.12f

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val spacing = 8.dp
        val cellWidth = (maxWidth - spacing * (GRID_COLUMNS - 1)) / GRID_COLUMNS
        val cellHeight = (maxHeight - spacing * (GRID_ROWS - 1)) / GRID_ROWS
        val tileWidth = cellWidth * TILE_SIZE_SCALE
        val tileHeight = cellHeight * TILE_SIZE_SCALE

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = GRID_ROTATION_DEGREES
                    scaleX = coverScale
                    scaleY = coverScale
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(spacing)
            ) {
                repeat(GRID_ROWS) { row ->
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(GRID_COLUMNS) { col ->
                            val index = row * GRID_COLUMNS + col
                            val url = tileUrls[index]
                            val decodeWidth = with(density) { (tileWidth * 1.35f).roundToPx() }
                            val decodeHeight = with(density) { (tileHeight * 1.35f).roundToPx() }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .graphicsLayer {
                                        rotationZ = TILE_ROTATIONS[index]
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                CollageTile(
                                    url = url,
                                    width = tileWidth,
                                    height = tileHeight,
                                    imageRequest = remember(url, decodeWidth, decodeHeight) {
                                        if (url.isNullOrBlank()) null
                                        else ImageRequest.Builder(context)
                                            .data(url)
                                            .size(decodeWidth, decodeHeight)
                                            .crossfade(160)
                                            .build()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollageTile(
    url: String?,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    imageRequest: ImageRequest?
) {
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(shape)
            .border(
                width = 1.dp,
                color = Color.Black.copy(alpha = 0.35f),
                shape = shape
            )
    ) {
        if (imageRequest != null) {
            SubcomposeAsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { ShimmerBox(modifier = Modifier.fillMaxSize()) },
                error = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            )
        } else {
            ShimmerBox(modifier = Modifier.fillMaxSize())
        }
    }
}
