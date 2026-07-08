package com.sintonia.sincronia.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sintonia.sincronia.domain.Dance

@Composable
fun DancePreview(
    dance: Dance,
    modifier: Modifier = Modifier,
    playing: Boolean = false,
    centerContent: @Composable (() -> Unit)? = null
) {
    val accent = Color(dance.accentColor)
    Box(
        modifier = modifier
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(dance.gradientStart), Color(dance.gradientEnd))
                )
            )
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
    ) {
        DanceMediaPreview(
            videoUri = dance.videoUri,
            previewUri = dance.previewUri,
            playVideo = playing,
            muted = false,
            modifier = Modifier.fillMaxSize()
        )
        if (playing) {
            AudioBars(
                color = accent,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp)
            )
        }
        centerContent?.let {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                it()
            }
        }
    }
}

@Composable
fun AudioBars(color: Color, modifier: Modifier = Modifier, count: Int = 7) {
    val transition = rememberInfiniteTransition(label = "audioBars")
    val bars = List(count) { index ->
        transition.animateFloat(
            initialValue = 8f + index,
            targetValue = 22f - (index % 3) * 3f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 560 + index * 40),
                repeatMode = RepeatMode.Reverse
            ),
            label = "audioBar$index"
        )
    }

    Row(modifier = modifier.height(26.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        bars.forEach { height ->
            val animatedHeight by height
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(animatedHeight.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
    }
}
