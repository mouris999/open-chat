package com.openchat.app.presentation.components

import android.Manifest
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.EntryPointAccessors
import com.openchat.app.core.util.LocationHelper
import java.util.concurrent.TimeUnit

/**
 * Live location sharing indicator (WhatsApp style)
 * Shows current location sharing status
 */
@Composable
fun LiveLocationSharingBar(
    remainingMinutes: Int,
    recipientName: String,
    onStopSharing: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF4CAF50).copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pulsing location icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4CAF50).copy(alpha = alpha)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Sharing live location",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
                Text(
                    text = "with $recipientName · $remainingMinutes min remaining",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            TextButton(onClick = onStopSharing) {
                Text("Stop")
            }
        }
    }
}

/**
 * Location message bubble (shows shared location)
 */
@Composable
fun LocationMessageBubble(
    locationName: String,
    address: String?,
    latitude: Double,
    longitude: Double,
    isLive: Boolean,
    remainingTime: Long?,
    onOpenMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(260.dp)
            .clickable(onClick = onOpenMap),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column {
            // Map preview placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(Color(0xFFE8F5E9)),
                contentAlignment = Alignment.Center
            ) {
                // Static map preview
                Icon(
                    imageVector = Icons.Default.Map,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                // Location pin
                if (isLive) {
                    Box(
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        PulsingLocationPin()
                    }
                }
            }
            
            // Location info
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isLive) Icons.Default.NearMe else Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (isLive) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = locationName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                address?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                if (isLive && remainingTime != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { remainingTime.toFloat() / (15 * 60 * 1000) },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF4CAF50),
                        trackColor = Color(0xFF4CAF50).copy(alpha = 0.2f)
                    )
                    Text(
                        text = formatRemainingTime(remainingTime),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF4CAF50),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PulsingLocationPin() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    
    val scale1 by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse1"
    )
    
    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha1"
    )
    
    Box(contentAlignment = Alignment.Center) {
        // Pulsing rings
        Box(
            modifier = Modifier
                .size(60.dp * scale1)
                .alpha(alpha1)
                .clip(CircleShape)
                .background(Color(0xFF4CAF50))
        )
        
        Box(
            modifier = Modifier
                .size(40.dp * scale1)
                .alpha(alpha1 * 1.2f)
                .clip(CircleShape)
                .background(Color(0xFF4CAF50))
        )
        
        // Center pin
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFF4CAF50)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

private fun formatRemainingTime(millis: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return String.format("Live for %d:%02d more", minutes, seconds)
}

/**
 * Location sharing duration selector
 */
@Composable
fun LocationDurationSelector(
    onDurationSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        Triple(15, "15 min", Icons.Default.Timer),
        Triple(60, "1 hour", Icons.Default.Schedule),
        Triple(480, "8 hours", Icons.Default.Today)
    )
    
    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = "Share live location for",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        options.forEach { (minutes, label, icon) ->
            ListItem(
                headlineContent = { Text(label) },
                leadingContent = {
                    Icon(imageVector = icon, contentDescription = null)
                },
                modifier = Modifier.clickable { onDurationSelected(minutes) }
            )
        }
    }
}

/**
 * Nearby places picker for sharing static location
 */
@Composable
fun NearbyPlacesPicker(
    onPlaceSelected: (String, Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }
    var currentLat by remember { mutableStateOf<Double?>(null) }
    var currentLng by remember { mutableStateOf<Double?>(null) }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    currentLat = it.latitude
                    currentLng = it.longitude
                }
            }
        }
    }

    val places = remember(currentLat, currentLng) {
        val lat = currentLat
        val lng = currentLng
        if (lat != null && lng != null) {
            listOf(
                Triple("Current Location", lat, lng),
                Triple("Home", lat + 0.001, lng - 0.001),
                Triple("Work", lat + 0.002, lng - 0.002),
                Triple("Coffee Shop", lat - 0.0005, lng + 0.0005)
            )
        } else {
            listOf(
                Triple("Current Location", 0.0, 0.0),
                Triple("Home", 0.001, -0.001),
                Triple("Work", 0.002, -0.002),
                Triple("Coffee Shop", -0.0005, 0.0005)
            )
        }
    }

    Column(modifier = modifier) {
        Text(
            text = "Share Location",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )
        places.forEach { (name, lat, lng) ->
            ListItem(
                headlineContent = { Text(name) },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                modifier = Modifier.clickable { onPlaceSelected(name, lat, lng) }
            )
        }
    }
}
