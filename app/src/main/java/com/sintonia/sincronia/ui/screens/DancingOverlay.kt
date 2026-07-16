package com.sintonia.sincronia.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sintonia.sincronia.components.DanceVideoPlayer
import com.sintonia.sincronia.domain.Dance
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
    onFinished: () -> Unit
) {
    val safeCountdown = if (countdownSeconds in setOf(3, 5, 10)) countdownSeconds else 10
    var count by remember(dance.id, safeCountdown) { mutableIntStateOf(safeCountdown) }
    var phase by remember(dance.id) { mutableStateOf(DancePhase.Countdown) }
    var cameraOffsetX by remember(dance.id) { mutableFloatStateOf(0f) }
    var cameraOffsetY by remember(dance.id) { mutableFloatStateOf(0f) }
    var cameraStreamReady by remember(dance.id) { mutableStateOf(false) }
    var goMinimumElapsed by remember(dance.id) { mutableStateOf(false) }
    var livePoseResult by remember(dance.id) { mutableStateOf<RealtimePoseResult?>(null) }
    val accent = Color(dance.accentColor)
    val context = LocalContext.current
    val view = LocalView.current
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
        if (compact && showSkeleton && poseResult != null) {
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
private const val LANDMARK_VISIBILITY_THRESHOLD = 0.35f
