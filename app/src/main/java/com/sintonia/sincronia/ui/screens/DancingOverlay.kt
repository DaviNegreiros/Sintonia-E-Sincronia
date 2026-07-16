package com.sintonia.sincronia.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sintonia.sincronia.R
import com.sintonia.sincronia.components.DanceVideoPlayer
import com.sintonia.sincronia.domain.Dance
import com.sintonia.sincronia.domain.DanceResult
import com.sintonia.sincronia.domain.ScoreFeedback
import com.sintonia.sincronia.processing.DanceScoringEngine
import com.sintonia.sincronia.processing.PoseConnections
import com.sintonia.sincronia.processing.PoseLandmark
import com.sintonia.sincronia.processing.RealtimePoseLandmarker
import com.sintonia.sincronia.processing.RealtimePoseResult
import com.sintonia.sincronia.ui.theme.SintoniaTextMuted
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import kotlin.math.max
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
    showSkeleton: Boolean,
    onClose: () -> Unit,
    onFinished: (DanceResult) -> Unit
) {
    val safeCountdown = if (countdownSeconds in setOf(3, 5, 10)) countdownSeconds else 10
    var count by remember(dance.id, safeCountdown) { mutableIntStateOf(safeCountdown) }
    var phase by remember(dance.id) { mutableStateOf(DancePhase.Countdown) }
    var cameraOffsetX by remember(dance.id) { mutableFloatStateOf(0f) }
    var cameraOffsetY by remember(dance.id) { mutableFloatStateOf(0f) }
    var cameraStreamReady by remember(dance.id) { mutableStateOf(false) }
    var goMinimumElapsed by remember(dance.id) { mutableStateOf(false) }
    var livePoseResult by remember(dance.id) { mutableStateOf<RealtimePoseResult?>(null) }
    var gameplayPoseStartTimestampMs by remember(dance.id) { mutableStateOf<Long?>(null) }
    var feedbackEvent by remember(dance.id) { mutableStateOf<FeedbackImageEvent?>(null) }
    var feedbackSequence by remember(dance.id) { mutableIntStateOf(0) }
    var finishedDispatched by remember(dance.id) { mutableStateOf(false) }
    val scoringEngine = remember(dance.id) { DanceScoringEngine.forDance(dance) }
    val accent = Color(dance.accentColor)
    val context = LocalContext.current
    val feedbackHeight = with(LocalDensity.current) { FEEDBACK_IMAGE_HEIGHT_PX.toDp() }
    val view = LocalView.current
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    var feedbackImageSize by remember { mutableStateOf(IntSize.Zero) }
    var feedbackPosition by remember(context) { mutableStateOf(context.readFeedbackPosition()) }
    var hasCameraPermission by remember { mutableStateOf(context.hasCameraPermission()) }
    var lensFacing by remember { mutableIntStateOf(context.readPreferredCameraLens()) }
    var availableLensFacings by remember { mutableStateOf(emptySet<Int>()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    BackHandler(onBack = onClose)

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    CameraAvailabilityEffect(
        enabled = hasCameraPermission,
        onAvailableLenses = { lenses ->
            availableLensFacings = lenses
            if (lenses.isNotEmpty() && lensFacing !in lenses) {
                val fallback = preferredCameraFallback(lenses)
                lensFacing = fallback
                context.savePreferredCameraLens(fallback)
            }
        }
    )

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
        }
    }

    LaunchedEffect(dance.id, lensFacing, hasCameraPermission) {
        cameraStreamReady = false
        livePoseResult = null
        gameplayPoseStartTimestampMs = null
        feedbackEvent = null
        feedbackSequence = 0
        finishedDispatched = false
    }

    LaunchedEffect(showSkeleton) {
        if (!showSkeleton) {
            livePoseResult = null
        }
    }

    LaunchedEffect(phase) {
        goMinimumElapsed = false
        if (phase == DancePhase.Go) {
            delay(700)
            goMinimumElapsed = true
        }
    }

    LaunchedEffect(phase, goMinimumElapsed, hasCameraPermission, cameraStreamReady) {
        if (phase == DancePhase.Go && goMinimumElapsed && (!hasCameraPermission || cameraStreamReady)) {
            phase = DancePhase.Playing
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { rootSize = it }
            .background(Color(0xFA04000E)),
        contentAlignment = Alignment.Center
    ) {
        if (phase == DancePhase.Playing) {
            DanceVideoPlayer(
                uri = dance.videoUri,
                playWhenReady = true,
                muted = false,
                loop = false,
                onEnded = {
                    if (!finishedDispatched) {
                        finishedDispatched = true
                        onFinished(scoringEngine.finalResult())
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        AdaptiveCameraPreview(
            lensFacing = lensFacing,
            hasCameraPermission = hasCameraPermission,
            compact = phase == DancePhase.Playing,
            poseResult = livePoseResult,
            showSkeleton = showSkeleton,
            mirrorLandmarksHorizontally = lensFacing == CameraSelector.LENS_FACING_FRONT,
            onStreamingChanged = { cameraStreamReady = it },
            onPoseResult = { result ->
                if (showSkeleton) {
                    livePoseResult = result
                }
                if (phase == DancePhase.Playing && !finishedDispatched) {
                    val startTimestamp = gameplayPoseStartTimestampMs ?: result.timestampMs.also {
                        gameplayPoseStartTimestampMs = it
                    }
                    val playbackTimestampMs = (result.timestampMs - startTimestamp).coerceAtLeast(0L).toDouble()
                    val scoreUpdate = scoringEngine.compare(
                        playbackTimestampMs = playbackTimestampMs,
                        landmarks = result.landmarks
                    )
                    val emittedFeedback = scoreUpdate.emittedFeedback
                    if (emittedFeedback != null) {
                        feedbackEvent = FeedbackImageEvent(
                            id = feedbackSequence,
                            feedback = emittedFeedback
                        )
                        feedbackSequence += 1
                    }
                }
            },
            modifier = if (phase == DancePhase.Playing) {
                Modifier
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
            } else {
                Modifier.fillMaxSize()
            }
        )

        if (phase == DancePhase.Countdown) {
            FeedbackPlacementPlaceholder(
                position = feedbackPosition,
                containerSize = rootSize,
                imageSize = feedbackImageSize,
                imageHeight = feedbackHeight,
                onImageSizeChanged = { feedbackImageSize = it },
                onPositionChanged = { feedbackPosition = it },
                onPositionSelected = { context.saveFeedbackPosition(it) }
            )
        }

        CameraFlipButton(
            onClick = {
                val nextLens = nextCameraLens(lensFacing, availableLensFacings)
                if (nextLens != lensFacing) {
                    lensFacing = nextLens
                    context.savePreferredCameraLens(nextLens)
                }
            },
            enabled = availableLensFacings.size > 1,
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

        feedbackEvent?.let { event ->
            FeedbackImage(
                event = event,
                position = feedbackPosition,
                containerSize = rootSize,
                imageSize = feedbackImageSize,
                imageHeight = feedbackHeight,
                onImageSizeChanged = { feedbackImageSize = it },
                onFinished = {
                    if (feedbackEvent?.id == event.id) {
                        feedbackEvent = null
                    }
                }
            )
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
private fun AdaptiveCameraPreview(
    lensFacing: Int,
    hasCameraPermission: Boolean,
    compact: Boolean,
    poseResult: RealtimePoseResult?,
    showSkeleton: Boolean,
    mirrorLandmarksHorizontally: Boolean,
    onStreamingChanged: (Boolean) -> Unit,
    onPoseResult: (RealtimePoseResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    val containerModifier = if (compact) {
        modifier
            .width(112.dp)
            .aspectRatio(9f / 16f)
            .clip(shape)
            .background(Color(0xDD120624), shape)
    } else {
        modifier.background(Color(0xDD120624))
    }

    Box(
        modifier = containerModifier,
        contentAlignment = Alignment.Center
    ) {
        CameraPreviewSurface(
            lensFacing = lensFacing,
            hasCameraPermission = hasCameraPermission,
            onStreamingChanged = onStreamingChanged,
            onPoseResult = onPoseResult,
            modifier = Modifier.fillMaxSize()
        )
        if (showSkeleton && poseResult != null) {
            PoseLandmarksOverlay(
                poseResult = poseResult,
                mirrorHorizontally = mirrorLandmarksHorizontally,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (compact) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(1.dp, Color(0x80A78BFA), shape)
            )
        }
    }
}

@Composable
private fun CameraPreviewSurface(
    lensFacing: Int,
    hasCameraPermission: Boolean,
    onStreamingChanged: (Boolean) -> Unit,
    onPoseResult: (RealtimePoseResult) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(Color(0xDD120624)),
        contentAlignment = Alignment.Center
    ) {
        if (hasCameraPermission) {
            CameraPreview(
                lensFacing = lensFacing,
                onStreamingChanged = onStreamingChanged,
                onPoseResult = onPoseResult,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Câmera", color = SintoniaTextMuted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("permissão", color = SintoniaTextMuted.copy(alpha = 0.55f), fontSize = 10.sp)
            }
        }
    }
}

private data class FeedbackImageEvent(
    val id: Int,
    val feedback: ScoreFeedback
)

private data class FeedbackPosition(
    val xFraction: Float,
    val yFraction: Float
)

@Composable
private fun FeedbackImage(
    event: FeedbackImageEvent,
    position: FeedbackPosition,
    containerSize: IntSize,
    imageSize: IntSize,
    imageHeight: Dp,
    onImageSizeChanged: (IntSize) -> Unit,
    onFinished: () -> Unit
) {
    var visible by remember(event.id) { mutableStateOf(true) }

    LaunchedEffect(event.id) {
        delay(FEEDBACK_VISIBLE_MS)
        visible = false
        delay(FEEDBACK_FADE_OUT_MS)
        onFinished()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopStart
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(90)) + scaleIn(
                initialScale = 0.62f,
                animationSpec = tween(170)
            ),
            exit = fadeOut(animationSpec = tween(FEEDBACK_FADE_OUT_MS.toInt())) + scaleOut(
                targetScale = 0.94f,
                animationSpec = tween(FEEDBACK_FADE_OUT_MS.toInt())
            ),
            modifier = Modifier.offset { position.toIntOffset(containerSize, imageSize) }
        ) {
            Image(
                painter = painterResource(id = event.feedback.drawableResId()),
                contentDescription = null,
                modifier = Modifier
                    .height(imageHeight)
                    .onSizeChanged(onImageSizeChanged)
            )
        }
    }
}

@Composable
private fun FeedbackPlacementPlaceholder(
    position: FeedbackPosition,
    containerSize: IntSize,
    imageSize: IntSize,
    imageHeight: Dp,
    onImageSizeChanged: (IntSize) -> Unit,
    onPositionChanged: (FeedbackPosition) -> Unit,
    onPositionSelected: (FeedbackPosition) -> Unit
) {
    val currentPosition by rememberUpdatedState(position)
    val currentOnPositionChanged by rememberUpdatedState(onPositionChanged)
    val currentOnPositionSelected by rememberUpdatedState(onPositionSelected)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopStart
    ) {
        Image(
            painter = painterResource(id = ScoreFeedback.SS.drawableResId()),
            contentDescription = null,
            modifier = Modifier
                .offset { position.toIntOffset(containerSize, imageSize) }
                .height(imageHeight)
                .onSizeChanged(onImageSizeChanged)
                .pointerInput(containerSize, imageSize) {
                    detectDragGestures(
                        onDragEnd = { currentOnPositionSelected(currentPosition) },
                        onDragCancel = { currentOnPositionSelected(currentPosition) }
                    ) { change, dragAmount ->
                        change.consume()
                        currentOnPositionChanged(
                            currentPosition.moveBy(
                                dragAmount = dragAmount,
                                containerSize = containerSize,
                                imageSize = imageSize
                            )
                        )
                    }
                }
        )
    }
}

private fun ScoreFeedback.drawableResId(): Int =
    when (this) {
        ScoreFeedback.X -> R.drawable.ss_x
        ScoreFeedback.OK -> R.drawable.ss_ok
        ScoreFeedback.OTIMO -> R.drawable.ss_otimo
        ScoreFeedback.SS -> R.drawable.ss_ss
    }

private fun FeedbackPosition.toIntOffset(containerSize: IntSize, imageSize: IntSize): IntOffset {
    val maxX = max(containerSize.width - imageSize.width, 0)
    val maxY = max(containerSize.height - imageSize.height, 0)
    return IntOffset(
        x = (xFraction.coerceIn(0f, 1f) * maxX).roundToInt(),
        y = (yFraction.coerceIn(0f, 1f) * maxY).roundToInt()
    )
}

private fun FeedbackPosition.moveBy(
    dragAmount: Offset,
    containerSize: IntSize,
    imageSize: IntSize
): FeedbackPosition {
    val maxX = max(containerSize.width - imageSize.width, 0)
    val maxY = max(containerSize.height - imageSize.height, 0)
    val current = toIntOffset(containerSize, imageSize)
    val nextX = (current.x + dragAmount.x).coerceIn(0f, maxX.toFloat())
    val nextY = (current.y + dragAmount.y).coerceIn(0f, maxY.toFloat())
    return FeedbackPosition(
        xFraction = if (maxX > 0) nextX / maxX else 0f,
        yFraction = if (maxY > 0) nextY / maxY else 0f
    )
}

private fun Context.readFeedbackPosition(): FeedbackPosition {
    val preferences = feedbackPreferences()
    return FeedbackPosition(
        xFraction = preferences.getFloat(KEY_FEEDBACK_X_FRACTION, DEFAULT_FEEDBACK_X_FRACTION)
            .coerceIn(0f, 1f),
        yFraction = preferences.getFloat(KEY_FEEDBACK_Y_FRACTION, DEFAULT_FEEDBACK_Y_FRACTION)
            .coerceIn(0f, 1f)
    )
}

private fun Context.saveFeedbackPosition(position: FeedbackPosition) {
    feedbackPreferences()
        .edit()
        .putFloat(KEY_FEEDBACK_X_FRACTION, position.xFraction.coerceIn(0f, 1f))
        .putFloat(KEY_FEEDBACK_Y_FRACTION, position.yFraction.coerceIn(0f, 1f))
        .apply()
}

private fun Context.feedbackPreferences() =
    applicationContext.getSharedPreferences(FEEDBACK_PREFERENCES_NAME, Context.MODE_PRIVATE)

@Composable
private fun CameraFlipButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(Color(0x1FFFFFFF), RoundedCornerShape(18.dp))
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val alpha = if (enabled) 0.78f else 0.34f
        Text("⇄", color = Color.White.copy(alpha = alpha), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("Câmera", color = Color.White.copy(alpha = if (enabled) 0.62f else 0.34f), fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
    }
}

@Composable
private fun CameraPreview(
    lensFacing: Int,
    onStreamingChanged: (Boolean) -> Unit,
    onPoseResult: (RealtimePoseResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnStreamingChanged by rememberUpdatedState(onStreamingChanged)
    val currentOnPoseResult = rememberUpdatedState(onPoseResult)
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val poseLandmarker = remember(context) {
        RealtimePoseLandmarker(
            context = context,
            onResult = { result -> currentOnPoseResult.value(result) }
        )
    }

    DisposableEffect(previewView, lifecycleOwner) {
        val observer = Observer<PreviewView.StreamState> { streamState ->
            currentOnStreamingChanged(streamState == PreviewView.StreamState.STREAMING)
        }
        previewView.previewStreamState.observe(lifecycleOwner, observer)
        onDispose {
            previewView.previewStreamState.removeObserver(observer)
            currentOnStreamingChanged(false)
        }
    }

    DisposableEffect(poseLandmarker, analysisExecutor) {
        onDispose {
            poseLandmarker.close()
            analysisExecutor.shutdown()
        }
    }

    DisposableEffect(context, lifecycleOwner, lensFacing, previewView, poseLandmarker, analysisExecutor) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        val listener = Runnable {
            val cameraProvider = cameraProviderFuture.get()
            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()
            val targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
            val preview = Preview.Builder()
                .setTargetRotation(targetRotation)
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .setTargetRotation(targetRotation)
                .build()
                .also { imageAnalysis ->
                    imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                        poseLandmarker.detect(imageProxy)
                    }
                }

            runCatching {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, analysis)
            }
        }

        cameraProviderFuture.addListener(listener, executor)
        onDispose {
            if (cameraProviderFuture.isDone) {
                runCatching { cameraProviderFuture.get().unbindAll() }
            }
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}

@Composable
private fun PoseLandmarksOverlay(
    poseResult: RealtimePoseResult,
    mirrorHorizontally: Boolean,
    modifier: Modifier = Modifier
) {
    val landmarks = poseResult.landmarks
    if (landmarks.isEmpty()) return

    Canvas(modifier = modifier) {
        val imageWidth = poseResult.imageWidth.takeIf { it > 0 }?.toFloat() ?: size.width
        val imageHeight = poseResult.imageHeight.takeIf { it > 0 }?.toFloat() ?: size.height
        val scale = max(size.width / imageWidth, size.height / imageHeight)
        val displayedWidth = imageWidth * scale
        val displayedHeight = imageHeight * scale
        val offsetX = (size.width - displayedWidth) / 2f
        val offsetY = (size.height - displayedHeight) / 2f

        fun landmarkOffset(landmark: PoseLandmark): Offset {
            val x = if (mirrorHorizontally) 1f - landmark.x else landmark.x
            return Offset(
                x = offsetX + x.coerceIn(0f, 1f) * displayedWidth,
                y = offsetY + landmark.y.coerceIn(0f, 1f) * displayedHeight
            )
        }

        for ((startIndex, endIndex) in PoseConnections.connections) {
            val start = landmarks.getOrNull(startIndex)
            val end = landmarks.getOrNull(endIndex)
            if (start != null &&
                end != null &&
                start.visibility >= LANDMARK_VISIBILITY_THRESHOLD &&
                end.visibility >= LANDMARK_VISIBILITY_THRESHOLD
            ) {
                drawLine(
                    color = Color(0xFF4ADE80),
                    start = landmarkOffset(start),
                    end = landmarkOffset(end),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
            }
        }

        for (landmark in landmarks) {
            if (landmark.visibility >= LANDMARK_VISIBILITY_THRESHOLD) {
                drawCircle(
                    color = Color(0xFFFFA24A),
                    radius = 4f,
                    center = landmarkOffset(landmark)
                )
            }
        }

    }
}

@Composable
private fun CameraAvailabilityEffect(
    enabled: Boolean,
    onAvailableLenses: (Set<Int>) -> Unit
) {
    val context = LocalContext.current

    DisposableEffect(context, enabled) {
        if (!enabled) {
            onAvailableLenses(emptySet())
            return@DisposableEffect onDispose { }
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        val listener = Runnable {
            runCatching {
                val cameraProvider = cameraProviderFuture.get()
                val lenses = buildSet {
                    if (runCatching { cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) }.getOrDefault(false)) {
                        add(CameraSelector.LENS_FACING_FRONT)
                    }
                    if (runCatching { cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) }.getOrDefault(false)) {
                        add(CameraSelector.LENS_FACING_BACK)
                    }
                }
                onAvailableLenses(lenses)
            }
        }

        cameraProviderFuture.addListener(listener, executor)
        onDispose { }
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun Context.readPreferredCameraLens(): Int {
    val storedLens = cameraPreferences().getInt(KEY_CAMERA_LENS_FACING, CameraSelector.LENS_FACING_FRONT)
    return if (storedLens == CameraSelector.LENS_FACING_BACK) {
        CameraSelector.LENS_FACING_BACK
    } else {
        CameraSelector.LENS_FACING_FRONT
    }
}

private fun Context.savePreferredCameraLens(lensFacing: Int) {
    cameraPreferences()
        .edit()
        .putInt(KEY_CAMERA_LENS_FACING, lensFacing)
        .apply()
}

private fun Context.cameraPreferences() =
    applicationContext.getSharedPreferences(CAMERA_PREFERENCES_NAME, Context.MODE_PRIVATE)

private fun nextCameraLens(current: Int, availableLensFacings: Set<Int>): Int {
    val target = if (current == CameraSelector.LENS_FACING_FRONT) {
        CameraSelector.LENS_FACING_BACK
    } else {
        CameraSelector.LENS_FACING_FRONT
    }
    return when {
        target in availableLensFacings -> target
        current in availableLensFacings -> current
        else -> preferredCameraFallback(availableLensFacings)
    }
}

private fun preferredCameraFallback(availableLensFacings: Set<Int>): Int =
    when {
        CameraSelector.LENS_FACING_FRONT in availableLensFacings -> CameraSelector.LENS_FACING_FRONT
        CameraSelector.LENS_FACING_BACK in availableLensFacings -> CameraSelector.LENS_FACING_BACK
        else -> CameraSelector.LENS_FACING_FRONT
    }

private const val CAMERA_PREFERENCES_NAME = "sintonia_camera"
private const val KEY_CAMERA_LENS_FACING = "camera_lens_facing"
private const val FEEDBACK_PREFERENCES_NAME = "sintonia_feedback"
private const val KEY_FEEDBACK_X_FRACTION = "feedback_x_fraction"
private const val KEY_FEEDBACK_Y_FRACTION = "feedback_y_fraction"
private const val DEFAULT_FEEDBACK_X_FRACTION = 0.5f
private const val DEFAULT_FEEDBACK_Y_FRACTION = 0.72f
private const val FEEDBACK_IMAGE_HEIGHT_PX = 350f
private const val LANDMARK_VISIBILITY_THRESHOLD = 0.35f
private const val FEEDBACK_VISIBLE_MS = 900L
private const val FEEDBACK_FADE_OUT_MS = 650L
