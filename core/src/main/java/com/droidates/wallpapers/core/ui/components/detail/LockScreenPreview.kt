package com.droidates.wallpapers.core.ui.components.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * A preview of a lock screen to display on wallpaper detail screen.
 */
@Composable
fun LockScreenPreview() {
    val currentHour = remember {
        SimpleDateFormat("HH", Locale.getDefault()).format(Calendar.getInstance().time)
    }

    val currentMinute = remember {
        SimpleDateFormat("mm", Locale.getDefault()).format(Calendar.getInstance().time)
    }

    val currentDate = remember {
        SimpleDateFormat("EEE, MMMM dd", Locale.getDefault()).format(Calendar.getInstance().time)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        // Lock icon at top
        Icon(
            imageVector = Icons.Rounded.Lock,
            contentDescription = "Lock",
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.weight(0.1f))

        // Clock with split hour / minute lines
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(200.dp)
        ) {
            // Hours
            Text(
                text = currentHour,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Light,
                color = Color.White,
                fontSize = 80.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            // Minutes
            Text(
                text = currentMinute,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Light,
                color = Color.White,
                fontSize = 80.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Day and date
            Text(
                text = currentDate,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Fingerprint icon (centre)
        Icon(
            imageVector = Icons.Rounded.Fingerprint,
            contentDescription = "Fingerprint",
            tint = Color.White,
            modifier = Modifier.size(60.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Bottom row with phone and camera icons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Phone icon (left)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Call,
                    contentDescription = "Phone",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Camera icon (right)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Camera",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
