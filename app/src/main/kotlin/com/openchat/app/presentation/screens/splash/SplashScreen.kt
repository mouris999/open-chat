package com.openchat.app.presentation.screens.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.openchat.app.presentation.theme.Motion
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToAuth: () -> Unit,
    viewModel: SplashViewModel = hiltViewModel()
) {
    // Drives both the logo/wordmark entrance and the minimum time spent on splash,
    // so navigation can never happen mid-animation.
    val enterProgress = remember { Animatable(0f) }

    LaunchedEffect(key1 = true) {
        // A fixed 2s delay ignored the animation entirely and could navigate away
        // before the entrance had even finished.
        enterProgress.animateTo(1f, tween(Motion.DELIBERATE, easing = Motion.Emphasized))
        delay(700)
        if (viewModel.isAuthenticated()) {
            onNavigateToHome()
        } else {
            onNavigateToAuth()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Chat,
                contentDescription = "OpenChat Logo",
                modifier = Modifier
                    .size(120.dp)
                    .graphicsLayer {
                        // Overshoot from the spring-like curve: scale past 1 then settle.
                        val eased = enterProgress.value
                        scaleX = 0.6f + 0.4f * eased + 0.04f * kotlin.math.sin(eased * Math.PI).toFloat()
                        scaleY = scaleX
                        alpha = eased
                    },
                tint = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "OpenChat",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                modifier = Modifier.graphicsLayer {
                    val eased = enterProgress.value
                    translationY = (1f - eased) * 24.dp.toPx()
                    alpha = eased
                }
            )
        }
    }
}
