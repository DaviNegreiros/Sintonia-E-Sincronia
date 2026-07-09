package com.sintonia.sincronia.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import com.sintonia.sincronia.components.DanceVideoPlayer
import com.sintonia.sincronia.domain.Dance
import com.sintonia.sincronia.ui.theme.SintoniaTextMuted
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private enum class DancePhase {
    Countdown,
    Go,
    Playing
}

@Composable
fun DancingOverlay(
    dance: Dance,
    countdownSeconds: Int,
    onClose: () -> Unit,
    onFinished: () -> Unit
) {
    val safeCountdown = if (countdownSeconds in setOf(3, 5, 10)) countdownSeconds else 10
    var count by remember(dance.id, safeCountdown) { mutableIntStateOf(safeCountdown) }
    var phase by remember(dance.id) { mutableStateOf(DancePhase.Countdown) }
    var cameraOffsetX by remember(dance.id) { mutableFloatStateOf(0f) }
    var cameraOffsetY by remember(dance.id) { mutableFloatStateOf(0f) }
    val accent = Color(dance.accentColor)
    val view = LocalView.current

    BackHandler(onBack = onClose)

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

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
        if (phase == DancePhase.Playing) {
            DanceVideoPlayer(
                uri = dance.videoUri,
                playWhenReady = true,
                muted = false,
                loop = false,
                onEnded = onFinished,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            DanceVideoPlayer(
                uri = dance.videoUri,
                playWhenReady = false,
                muted = true,
                loop = false,
                modifier = Modifier.fillMaxSize()
            )
        }

        CameraFlipButton(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 18.dp, top = 18.dp)
        )

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
            DancePhase.Countdown -> CountdownPhase(count = count, total = safeCountdown, accent = accent)
            DancePhase.Go -> Text(
                "VAI!",
                color = accent,
                fontWeight = FontWeight.Black,
                fontSize = 68.sp
            )
            DancePhase.Playing -> {
                DraggableCameraPlaceholder(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 78.dp, end = 18.dp)
                        .offset { IntOffset(cameraOffsetX.roundToInt(), cameraOffsetY.roundToInt()) }
                        .pointerInput(dance.id) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                cameraOffsetX += dragAmount.x
                                cameraOffsetY += dragAmount.y
                            }
                        }
                )
                Text(
                    "Reproduzindo · ${dance.name}",
                    color = SintoniaTextMuted.copy(alpha = 0.82f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 30.dp)
                )
            }
        }
    }
}

@Composable
private fun CountdownPhase(count: Int, total: Int, accent: Color) {
    Box(modifier = Modifier.padding(top = 56.dp), contentAlignment = Alignment.Center) {
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
                sweepAngle = 360f * (count / total.toFloat()),
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

@Composable
private fun DraggableCameraPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(112.dp)
            .height(199.dp)
            .background(Color(0xDD120624), RoundedCornerShape(14.dp))
            .border(1.dp, Color(0x80A78BFA), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Câmera", color = SintoniaTextMuted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("arraste", color = SintoniaTextMuted.copy(alpha = 0.55f), fontSize = 10.sp)
        }
    }
}

@Composable
private fun CameraFlipButton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color(0x1FFFFFFF), RoundedCornerShape(18.dp))
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("⇄", color = Color.White.copy(alpha = 0.78f), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("Câmera", color = Color.White.copy(alpha = 0.62f), fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
    }
}
