package com.droidates.wallpapers.core.ui.components.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidates.wallpapers.core.R
import com.droidates.wallpapers.core.ui.components.AppIconPreview
import com.droidates.wallpapers.core.utils.DateTimeFormatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * A preview of a home screen to display on wallpaper detail screen.
 */
@Composable
fun HomeScreenPreview() {
    // Get current time and date for display
    val currentTime = remember {
        val formatter = SimpleDateFormat("h:mm a", Locale.getDefault())
        formatter.format(Calendar.getInstance().time)
    }
    
    val isNightTime = remember { DateTimeFormatter.isNightTime() }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.weight(1f)) // Space between elements
        
        // First row with weather and info cards
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(175.dp), // Reduced height for the row
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Weather card (left half)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        brush = Brush.linearGradient(
                            colors = if (isNightTime) {
                                listOf(
                                    Color(0xFF0D0D0D),  // Dark black
                                    Color(0xFF333333)   // Dark grey
                                )
                            } else {
                                listOf(
                                    Color(0xFF2196F3),  // Blue
                                    Color(0xFF64B5F6)   // Light blue
                                )
                            }
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Content layout
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 16.dp, bottom = 8.dp)
                ) {
                    // Sun/Moon icon at top right
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(),
                        contentAlignment = Alignment.TopEnd
                    ) {
                        Image(
                            painter = painterResource(
                                id = if (isNightTime) R.drawable.moon else R.drawable.sun
                            ),
                            contentDescription = if (isNightTime) "Moon" else "Sun",
                            modifier = Modifier.size(70.dp)
                        )
                    }
                    
                    Text(
                        text = "11°",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp
                    )
                    
                    // Weather condition
                    Text(
                        text = if (isNightTime) "Clear" else "Sunny",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    
                    // High/Low temperature
                    Text(
                        text = "↑19° / ↓13°",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    // Location
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LocationOn,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "San Jose",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                    }
                }
            }
            
            // Right side cards (stacked vertically)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(24.dp) // Increased spacing between cards
            ) {
                // Time & Brief card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(60.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF64B5F6), // Light blue
                                    Color(0xFFE8EAED)  // Light grey
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = currentTime,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isNightTime) "Night brief" else "Morning brief",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                // Health data card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(60.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFE0E0E0), // Light grey
                                    Color(0xFFC0C0C0)  // Darker grey
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Health icon on the left
                        Image(
                            painter = painterResource(id = R.drawable.health),
                            contentDescription = "Health",
                            modifier = Modifier.size(36.dp)
                        )
                        
                        Spacer(modifier = Modifier.width(10.dp))
                        
                        // Health data indicators in a column
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            CircleIndicator(Color(0xFF4CAF50), "150 steps")
                            CircleIndicator(Color(0xFF2196F3), "1 mins")
                            CircleIndicator(Color(0xFFE91E63), "5 cal")
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        
        // Google search bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.9f)),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.google),
                        contentDescription = "Google",
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Search",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.google_mic),
                        contentDescription = "Google Mic",
                        modifier = Modifier.size(26.dp)
                    )
                    Image(
                        painter = painterResource(id = R.drawable.google_lens),
                        contentDescription = "Google Lens",
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // App icons row (2nd row from top)
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            AppIconPreview(
                drawableResId = R.drawable.store,
                contentDescription = "Store"
            )
            AppIconPreview(
                drawableResId = R.drawable.gallery,
                contentDescription = "Gallery"
            )
            AppIconPreview(
                drawableResId = R.drawable.playstore,
                contentDescription = "Play Store"
            )
            AppIconPreview(
                drawableResId = R.drawable.youtube,
                contentDescription = "YouTube"
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Middle indicators (dots and lines)
        Row(
            modifier = Modifier
                .align(Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Lines
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .width(8.dp)
                        .height(1.5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.8f))

                )
                Box(
                    modifier = Modifier
                        .width(8.dp)
                        .height(1.5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.8f))

                )
            }
            
            // Dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)

                ){
                    Image(
                        painter = painterResource(id = R.drawable.home_preview),
                        contentDescription = "Home Icon",
                    )
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color.White.copy(alpha = 0.5f), CircleShape)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Bottom row app icons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            AppIconPreview(
                drawableResId = R.drawable.phone,
                contentDescription = "Phone"
            )
            AppIconPreview(
                drawableResId = R.drawable.message,
                contentDescription = "Messages"
            )
            AppIconPreview(
                drawableResId = R.drawable.browser,
                contentDescription = "Browser"
            )
            AppIconPreview(
                drawableResId = R.drawable.camera,
                contentDescription = "Camera"
            )
        }
    }
}

/**
 * Circle indicator with number for the morning brief card
 */
@Composable
private fun CircleIndicator(color: Color, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.Black,
            fontWeight = FontWeight.Bold
        )
    }
} 