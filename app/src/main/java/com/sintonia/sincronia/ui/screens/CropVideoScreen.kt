package com.sintonia.sincronia.ui.screens

import androidx.annotation.OptIn
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.sintonia.sincronia.components.BackButton
import com.sintonia.sincronia.components.GlowButton
import com.sintonia.sincronia.components.Hairline
import com.sintonia.sincronia.domain.CropSelection
import com.sintonia.sincronia.processing.ProcessingPerformanceConfig
import com.sintonia.sincronia.processing.ProcessingPerformanceReport
import com.sintonia.sincronia.ui.theme.SintoniaPrimary
import com.sintonia.sincronia.ui.theme.SintoniaText
import com.sintonia.sincronia.ui.theme.SintoniaTextMuted
import com.sintonia.sincronia.viewmodel.NewDanceViewModel
import kotlin.math.roundToInt

private const val CROP_ASPECT_RATIO = 9f / 16f

@OptIn(UnstableApi::class)
@Composable
fun CropVideoScreen(
    viewModel: NewDanceViewModel,
    onCancel: () -> Unit,
    onImported: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()
    val performanceReport by viewModel.processingPerformanceReport.collectAsStateWithLifecycle()
    val sourceUri = uiState.sourceUri
    val view = LocalView.current
    var showPerformanceReport by remember { mutableStateOf(false) }
    var handledImportId by remember { mutableStateOf<String?>(null) }

    fun finishImportFlow() {
        showPerformanceReport = false
        viewModel.reset()
        onImported()
    }

    LaunchedEffect(uiState.importedDanceId, performanceReport) {
        val importedDanceId = uiState.importedDanceId
        if (importedDanceId != null && handledImportId != importedDanceId) {
            if (ProcessingPerformanceConfig.SHOW_PROCESSING_REPORT && performanceReport == null) {
                return@LaunchedEffect
            }
            handledImportId = importedDanceId
            if (ProcessingPerformanceConfig.SHOW_PROCESSING_REPORT) {
                showPerformanceReport = true
                return@LaunchedEffect
            }
            viewModel.reset()
            onImported()
        }
    }

    if (sourceUri == null) {
        LaunchedEffect(Unit) { onCancel() }
        return
    }

    BackHandler {
        if (uiState.isImporting) {
            viewModel.cancelImport()
        } else {
            onCancel()
        }
    }

    DisposableEffect(uiState.isImporting) {
        if (uiState.isImporting) {
            view.keepScreenOn = true
        }
        onDispose {
            if (uiState.isImporting) {
                view.keepScreenOn = false
            }
        }
    }

    val context = LocalContext.current
    val player = remember(sourceUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(sourceUri))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    LaunchedEffect(player, uiState.isImporting) {
        if (uiState.isImporting) {
            player.pause()
            player.seekTo(0L)
        } else {
            player.play()
        }
    }

    var stageSize by remember { mutableStateOf(IntSize.Zero) }
    var cropOffset by remember(sourceUri) { mutableStateOf(Offset.Zero) }
    var cropInitialized by remember(sourceUri) { mutableStateOf(false) }
    val videoRect = remember(stageSize, uiState.videoInfo) {
        calculateFittedVideoRect(stageSize, uiState.videoInfo.width, uiState.videoInfo.height)
    }
    val cropSize = remember(videoRect) { calculateCropSize(videoRect) }

    LaunchedEffect(videoRect, cropSize, cropInitialized) {
        if (!cropInitialized && videoRect.width > 0f && videoRect.height > 0f) {
            cropOffset = Offset(
                x = (videoRect.width - cropSize.width) / 2f,
                y = (videoRect.height - cropSize.height) / 2f
            )
            cropInitialized = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF05000E))
    ) {
        CropHeader(onCancel = if (uiState.isImporting) viewModel::cancelImport else onCancel)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black)
                .onSizeChanged { stageSize = it }
        ) {
            AndroidView(
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        this.player = player
                    }
                },
                update = { it.player = player },
                modifier = Modifier.fillMaxSize()
            )

            if (videoRect.width > 0f && videoRect.height > 0f) {
                CropScrim(
                    videoRect = videoRect,
                    cropOffset = cropOffset,
                    cropSize = cropSize,
                    modifier = Modifier.fillMaxSize()
                )
                CropFrame(
                    videoRect = videoRect,
                    cropOffset = cropOffset,
                    cropSize = cropSize,
                    isLocked = uiState.isImporting,
                    onMove = { drag ->
                        cropOffset = Offset(
                            x = (cropOffset.x + drag.x).coerceIn(0f, videoRect.width - cropSize.width),
                            y = (cropOffset.y + drag.y).coerceIn(0f, videoRect.height - cropSize.height)
                        )
                    }
                )
            }
        }

        CropFooter(
            isImporting = uiState.isImporting,
            canConfirm = uiState.canConfirmCrop && videoRect.width > 0f,
            errorMessage = uiState.errorMessage,
            progress = importProgress,
            onCancelImport = viewModel::cancelImport,
            onConfirm = {
                viewModel.clearError()
                viewModel.importSelectedVideo(
                    CropSelection(
                        left = (cropOffset.x / videoRect.width).coerceIn(0f, 1f),
                        top = (cropOffset.y / videoRect.height).coerceIn(0f, 1f),
                        width = (cropSize.width / videoRect.width).coerceIn(0f, 1f),
                        height = (cropSize.height / videoRect.height).coerceIn(0f, 1f)
                    )
                )
            }
        )
    }

    val visiblePerformanceReport = performanceReport
    if (showPerformanceReport && visiblePerformanceReport != null) {
        PerformanceReportDialog(
            report = visiblePerformanceReport,
            onDismiss = ::finishImportFlow
        )
    }
}

@Composable
private fun PerformanceReportDialog(
    report: ProcessingPerformanceReport,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF160730),
        title = {
            Text("Relatório de Performance", color = SintoniaText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Text(
                text = report.toDisplayText(),
                color = SintoniaTextMuted,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar", color = SintoniaPrimary, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun CropHeader(onCancel: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BackButton(onClick = onCancel)
            Text(
                "Recortar Video",
                color = SintoniaText,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                "Arraste o quadro",
                color = SintoniaPrimary.copy(alpha = 0.78f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp
            )
        }
        Hairline()
    }
}

@Composable
private fun CropFooter(
    isImporting: Boolean,
    canConfirm: Boolean,
    errorMessage: String?,
    progress: com.sintonia.sincronia.processing.ImportProgress?,
    onCancelImport: () -> Unit,
    onConfirm: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF05000E))
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = Color(0xFFFF7A95),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
        if (isImporting) {
            Text(
                text = progress?.message ?: "Preparando dança...",
                color = SintoniaText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            LinearProgressIndicator(
                progress = { progress?.progress?.coerceIn(0f, 1f) ?: 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp),
                color = SintoniaPrimary,
                trackColor = Color(0x337C3AED)
            )
            Text(
                text = "${progress?.percent ?: 0}%",
                color = SintoniaTextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            GlowButton(
                label = "Cancelar",
                destructive = true,
                onClick = onCancelImport,
                modifier = Modifier.width(210.dp)
            )
        } else {
            GlowButton(
                label = "Confirmar",
                enabled = canConfirm,
                onClick = onConfirm,
                modifier = Modifier.width(210.dp)
            )
        }
    }
}

@Composable
private fun CropFrame(
    videoRect: RectPx,
    cropOffset: Offset,
    cropSize: Size,
    isLocked: Boolean,
    onMove: (Offset) -> Unit
) {
    val density = LocalDensity.current
    val widthDp = with(density) { cropSize.width.toDp() }
    val heightDp = with(density) { cropSize.height.toDp() }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (videoRect.left + cropOffset.x).roundToInt(),
                    (videoRect.top + cropOffset.y).roundToInt()
                )
            }
            .width(widthDp)
            .height(heightDp)
            .border(2.dp, Color(0xFFC4A6FF), RoundedCornerShape(3.dp))
            .pointerInput(isLocked) {
                if (!isLocked) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onMove(dragAmount)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridColor = Color.White.copy(alpha = 0.16f)
            drawLine(gridColor, Offset(size.width / 3f, 0f), Offset(size.width / 3f, size.height), strokeWidth = 1f)
            drawLine(gridColor, Offset(size.width * 2f / 3f, 0f), Offset(size.width * 2f / 3f, size.height), strokeWidth = 1f)
            drawLine(gridColor, Offset(0f, size.height / 3f), Offset(size.width, size.height / 3f), strokeWidth = 1f)
            drawLine(gridColor, Offset(0f, size.height * 2f / 3f), Offset(size.width, size.height * 2f / 3f), strokeWidth = 1f)
        }
        Text(
            text = "9:16",
            color = SintoniaText,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 11.sp,
            modifier = Modifier
                .background(Color(0xAA160730), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun CropScrim(
    videoRect: RectPx,
    cropOffset: Offset,
    cropSize: Size,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val scrim = Color.Black.copy(alpha = 0.56f)
        val cropLeft = videoRect.left + cropOffset.x
        val cropTop = videoRect.top + cropOffset.y
        val cropRight = cropLeft + cropSize.width
        val cropBottom = cropTop + cropSize.height

        drawRect(scrim, topLeft = Offset.Zero, size = Size(size.width, cropTop))
        drawRect(scrim, topLeft = Offset(0f, cropBottom), size = Size(size.width, size.height - cropBottom))
        drawRect(scrim, topLeft = Offset(0f, cropTop), size = Size(cropLeft, cropSize.height))
        drawRect(scrim, topLeft = Offset(cropRight, cropTop), size = Size(size.width - cropRight, cropSize.height))
    }
}

private data class RectPx(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
)

private fun calculateFittedVideoRect(stageSize: IntSize, videoWidth: Int, videoHeight: Int): RectPx {
    if (stageSize.width <= 0 || stageSize.height <= 0 || videoWidth <= 0 || videoHeight <= 0) {
        return RectPx(0f, 0f, 0f, 0f)
    }

    val videoAspect = videoWidth.toFloat() / videoHeight.toFloat()
    val stageAspect = stageSize.width.toFloat() / stageSize.height.toFloat()
    val fittedWidth: Float
    val fittedHeight: Float

    if (stageAspect > videoAspect) {
        fittedHeight = stageSize.height.toFloat()
        fittedWidth = fittedHeight * videoAspect
    } else {
        fittedWidth = stageSize.width.toFloat()
        fittedHeight = fittedWidth / videoAspect
    }

    return RectPx(
        left = (stageSize.width - fittedWidth) / 2f,
        top = (stageSize.height - fittedHeight) / 2f,
        width = fittedWidth,
        height = fittedHeight
    )
}

private fun calculateCropSize(videoRect: RectPx): Size {
    if (videoRect.width <= 0f || videoRect.height <= 0f) return Size.Zero

    val videoAspect = videoRect.width / videoRect.height
    return if (videoAspect > CROP_ASPECT_RATIO) {
        val height = videoRect.height
        Size(width = height * CROP_ASPECT_RATIO, height = height)
    } else {
        val width = videoRect.width
        Size(width = width, height = width / CROP_ASPECT_RATIO)
    }
}
