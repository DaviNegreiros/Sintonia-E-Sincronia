package com.sintonia.sincronia.data

import android.content.Context
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.Presentation
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.sintonia.sincronia.domain.CropSelection
import com.sintonia.sincronia.domain.Dance
import com.sintonia.sincronia.domain.DanceMetadata
import com.sintonia.sincronia.domain.Rank
import com.sintonia.sincronia.domain.VideoInfo
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject

class LocalDanceRepository(context: Context) : DanceRepository {
    private val appContext = context.applicationContext
    private val dancesDir = File(appContext.filesDir, "dances")
    private val _dances = MutableStateFlow<List<Dance>>(emptyList())

    override val dances: StateFlow<List<Dance>> = _dances.asStateFlow()

    init {
        refresh()
    }

    override suspend fun importDance(
        title: String,
        sourceUri: Uri,
        cropSelection: CropSelection
    ): Result<Dance> = runCatching {
        val cleanTitle = title.trim().ifEmpty { "Nova dança" }
        ensureTitleIsAvailable(cleanTitle)

        val danceId = nextDanceId()
        val danceFolder = File(dancesDir, danceId)
        val videoFile = File(danceFolder, VIDEO_FILE_NAME)
        val metadataFile = File(danceFolder, METADATA_FILE_NAME)

        withContext(Dispatchers.IO) {
            danceFolder.mkdirs()
            if (videoFile.exists()) videoFile.delete()
        }

        try {
            transformVideo(sourceUri, videoFile, cropSelection)
            val metadata = DanceMetadata(
                id = danceId,
                title = cleanTitle,
                video = VIDEO_FILE_NAME,
                preview = null,
                moveset = null,
                bestRank = null
            )
            withContext(Dispatchers.IO) {
                metadataFile.writeText(metadata.toJson().toString(2))
            }
            refresh()
            requireNotNull(_dances.value.firstOrNull { it.id == danceId })
        } catch (error: Throwable) {
            withContext(Dispatchers.IO) {
                danceFolder.deleteRecursively()
            }
            throw error
        }
    }

    override fun refresh() {
        dancesDir.mkdirs()
        _dances.value = dancesDir
            .listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { folder -> folder.toDanceOrNull() }
            ?.sortedByDescending { it.folder.lastModified() }
            .orEmpty()
    }

    override fun deleteDance(id: String) {
        val target = File(dancesDir, id)
        if (target.exists()) {
            target.deleteRecursively()
        }
        refresh()
    }

    private fun File.toDanceOrNull(): Dance? {
        val metadataFile = File(this, METADATA_FILE_NAME)
        if (!metadataFile.exists()) return null

        val metadata = runCatching {
            JSONObject(metadataFile.readText()).toDanceMetadata()
        }.getOrNull() ?: return null

        val videoFile = File(this, metadata.video)
        if (!videoFile.exists()) return null
        val previewFile = metadata.preview?.let { File(this, it) }?.takeIf { it.exists() }

        val colors = colorsFor(metadata.id)
        return Dance(
            metadata = metadata,
            folder = this,
            videoFile = videoFile,
            videoUri = Uri.fromFile(videoFile),
            previewUri = previewFile?.let { Uri.fromFile(it) },
            accentColor = colors.accent,
            gradientStart = colors.start,
            gradientEnd = colors.end
        )
    }

    private fun nextDanceId(): String {
        dancesDir.mkdirs()
        val nextNumber = dancesDir
            .listFiles()
            ?.mapNotNull { folder ->
                folder.name.removePrefix("dance_").toIntOrNull()
            }
            ?.maxOrNull()
            ?.plus(1)
            ?: 1
        return "dance_${nextNumber.toString().padStart(3, '0')}"
    }

    @OptIn(UnstableApi::class)
    private suspend fun transformVideo(
        sourceUri: Uri,
        outputFile: File,
        cropSelection: CropSelection
    ) = withContext(Dispatchers.Main) {
        val cropEffect = SelectionCropTransformation(cropSelection)
        val effects = Effects(
            emptyList(),
            listOf(
                cropEffect,
                Presentation.createForWidthAndHeight(
                    OUTPUT_WIDTH,
                    OUTPUT_HEIGHT,
                    Presentation.LAYOUT_SCALE_TO_FIT
                )
            )
        )
        val editedMediaItem = EditedMediaItem.Builder(MediaItem.fromUri(sourceUri))
            .setEffects(effects)
            .build()

        suspendCancellableCoroutine { continuation ->
            val transformer = Transformer.Builder(appContext)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: ExportResult) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }

                    override fun onError(
                        composition: androidx.media3.transformer.Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        if (continuation.isActive) continuation.resumeWith(Result.failure(exportException))
                    }
                })
                .build()

            continuation.invokeOnCancellation {
                transformer.cancel()
                if (it !is CancellationException) outputFile.delete()
            }

            transformer.start(editedMediaItem, outputFile.absolutePath)
        }
    }

    override fun readVideoInfo(uri: Uri): VideoInfo = runCatching {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(appContext, uri)
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: DEFAULT_VIDEO_WIDTH
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: DEFAULT_VIDEO_HEIGHT
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        retriever.release()
        if (rotation == 90 || rotation == 270) VideoInfo(height, width) else VideoInfo(width, height)
    }.getOrDefault(VideoInfo(DEFAULT_VIDEO_WIDTH, DEFAULT_VIDEO_HEIGHT))

    override fun isDanceTitleAvailable(title: String): Boolean {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return false
        return _dances.value.none { it.name == cleanTitle }
    }

    private fun ensureTitleIsAvailable(title: String) {
        refresh()
        if (!isDanceTitleAvailable(title)) {
            throw DuplicateDanceNameException(title)
        }
    }

    private fun DanceMetadata.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("video", video)
        .put("preview", preview ?: JSONObject.NULL)
        .put("moveset", moveset ?: JSONObject.NULL)
        .put("bestRank", bestRank?.name ?: JSONObject.NULL)

    private fun JSONObject.toDanceMetadata(): DanceMetadata = DanceMetadata(
        id = getString("id"),
        title = getString("title"),
        video = getString("video"),
        preview = optNullableString("preview"),
        moveset = optNullableString("moveset"),
        bestRank = optNullableString("bestRank")?.let { value ->
            runCatching { Rank.valueOf(value) }.getOrNull()
        }
    )

    private fun JSONObject.optNullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private data class DanceColors(val accent: Long, val start: Long, val end: Long)

    @OptIn(UnstableApi::class)
    private class SelectionCropTransformation(
        cropSelection: CropSelection
    ) : MatrixTransformation, Effect {
        private val left = cropSelection.left.coerceIn(0f, 1f)
        private val top = cropSelection.top.coerceIn(0f, 1f)
        private val right = cropSelection.right.coerceIn(left + MIN_CROP_SIZE, 1f)
        private val bottom = cropSelection.bottom.coerceIn(top + MIN_CROP_SIZE, 1f)
        private var matrix = Matrix()

        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            val ndcLeft = left * 2f - 1f
            val ndcRight = right * 2f - 1f
            val ndcTop = 1f - top * 2f
            val ndcBottom = 1f - bottom * 2f
            val xScale = (ndcRight - ndcLeft) / 2f
            val yScale = (ndcTop - ndcBottom) / 2f
            val centerX = (ndcLeft + ndcRight) / 2f
            val centerY = (ndcBottom + ndcTop) / 2f

            matrix = Matrix().apply {
                postTranslate(-centerX, -centerY)
                postScale(1f / xScale, 1f / yScale)
            }

            return Size(
                (inputWidth * xScale).toInt().coerceAtLeast(1),
                (inputHeight * yScale).toInt().coerceAtLeast(1)
            )
        }

        override fun getMatrix(presentationTimeUs: Long): Matrix = matrix

        private companion object {
            const val MIN_CROP_SIZE = 0.01f
        }
    }

    private fun colorsFor(seed: String): DanceColors {
        val palettes = listOf(
            DanceColors(0xFF8B5CF6, 0xFF2D1B69, 0xFF140030),
            DanceColors(0xFF9747FF, 0xFF4B0082, 0xFF1A0040),
            DanceColors(0xFFA855F7, 0xFF5B0E91, 0xFF240040),
            DanceColors(0xFF7C3AED, 0xFF180D4F, 0xFF09001F)
        )
        return palettes[kotlin.math.abs(seed.hashCode()) % palettes.size]
    }

    private companion object {
        const val METADATA_FILE_NAME = "metadata.json"
        const val VIDEO_FILE_NAME = "dance.mp4"
        const val DEFAULT_VIDEO_WIDTH = 1080
        const val DEFAULT_VIDEO_HEIGHT = 1920
        const val OUTPUT_WIDTH = 720
        const val OUTPUT_HEIGHT = 1280
    }
}
