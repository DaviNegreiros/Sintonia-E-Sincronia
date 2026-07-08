package com.sintonia.sincronia.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sintonia.sincronia.components.DancePreview
import com.sintonia.sincronia.domain.Dance
import com.sintonia.sincronia.ui.theme.SintoniaTextMuted
import kotlinx.coroutines.delay

private enum class DancePhase {
    Countdown,
    Go,
    Playing
}

@Composable
fun DancingOverlay(
    dance: Dance,
    onClose: () -> Unit
) {
    var count by remember(dance.id) { mutableIntStateOf(10) }
    var phase by remember(dance.id) { mutableStateOf(DancePhase.Countdown) }
    val accent = Color(dance.accentColor)

    LaunchedEffect(phase, count) {
        when {
            phase == DancePhase.Countdown && count > 0 -> {
                delay(1000)
                count -= 1
            }
            phase == DancePhase.Countdown -> phase = DancePhase.Go
            phase == DancePhase.Go -> {
                delay(700)
                phase = DancePhase.Playing
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFA04000E)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(18.dp)
                .size(34.dp)
                .background(Color(0x15FFFFFF), CircleShape)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            Text("×", color = Color.White.copy(alpha = 0.7f), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        when (phase) {
            DancePhase.Countdown -> CountdownPhase(count = count, accent = accent)
            DancePhase.Go -> Text(
                "VAI!",
                color = accent,
                fontWeight = FontWeight.Black,
                fontSize = 68.sp
            )
            DancePhase.Playing -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                DancePreview(dance = dance, playing = true, modifier = Modifier.width(220.dp))
                Text(
                    "Reproduzindo · ${dance.name}",
                    color = SintoniaTextMuted.copy(alpha = 0.65f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun CountdownPhase(count: Int, accent: Color) {
    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(156.dp)) {
            val radius = size.minDimension / 2f - 10f
            val stroke = Stroke(width = 6f, cap = StrokeCap.Round)
            drawCircle(
                color = Color(0x247C3AED),
                radius = radius,
                center = Offset(size.width / 2f, size.height / 2f),
                style = stroke
            )
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * (count / 10f),
                useCenter = false,
                style = stroke
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$count",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 86.sp
            )
            Text(
                "Prepare-se",
                color = SintoniaTextMuted.copy(alpha = 0.55f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
        }
    }
}
