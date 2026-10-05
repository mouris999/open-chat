package com.openchat.app.presentation.components
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.openchat.app.presentation.theme.Accent
@Composable fun ConversationPulse(modifier: Modifier = Modifier){
    val inf = rememberInfiniteTransition(label="pulse")
    val a1 by inf.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900, easing=FastOutSlowInEasing), RepeatMode.Reverse), label="a1")
    val a2 by inf.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900, delayMillis=180, easing=FastOutSlowInEasing), RepeatMode.Reverse), label="a2")
    val a3 by inf.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900, delayMillis=360, easing=FastOutSlowInEasing), RepeatMode.Reverse), label="a3")
    Row(modifier, verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(5.dp)){
        Box(Modifier.size(6.dp).clip(CircleShape).background(Accent).alpha(a1))
        Box(Modifier.size(6.dp).clip(CircleShape).background(Accent).alpha(a2))
        Box(Modifier.size(6.dp).clip(CircleShape).background(Accent).alpha(a3))
    }
}
@Composable fun PulseDot(modifier: Modifier = Modifier){
    val inf = rememberInfiniteTransition(label="dot")
    val s by inf.animateFloat(0.85f, 1.15f, infiniteRepeatable(tween(1100, easing=FastOutSlowInEasing), RepeatMode.Reverse), label="s")
    val a by inf.animateFloat(0.6f, 1f, infiniteRepeatable(tween(1100, easing=FastOutSlowInEasing), RepeatMode.Reverse), label="a")
    Box(modifier.size(8.dp).clip(CircleShape).background(Accent.copy(alpha=a))){
        Box(Modifier.fillMaxSize().alpha(0.35f).background(Accent))
    }
}
