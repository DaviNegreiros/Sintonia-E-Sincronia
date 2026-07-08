package com.sintonia.sincronia.components

import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
@Composable
fun DanceVideoPlayer(
    uri: Uri,
    modifier: Modifier = Modifier,
    playWhenReady: Boolean = true,
    muted: Boolean = true,
    showControls: Boolean = false,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
    loop: Boolean = true,
    showFirstFrameUntilReady: Boolean = true,
    onEnded: () -> Unit = {}
) {
    val context = LocalContext.current
    var firstFrameRendered by remember(uri) { mutableStateOf(false) }
    var endedDispatched by remember(uri) { mutableStateOf(false) }
    val firstFrame = remember(uri) {
        if (!showFirstFrameUntilReady) {
            null
        } else {
            runCatching {
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(context, uri)
                    retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
            }.getOrNull()
        }
    }
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            volume = if (muted) 0f else 1f
            this.playWhenReady = playWhenReady
            prepare()
        }
    }

    LaunchedEffect(playWhenReady, muted, loop) {
        player.playWhenReady = playWhenReady
        player.volume = if (muted) 0f else 1f
        player.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                firstFrameRendered = true
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && !endedDispatched) {
                    endedDispatched = true
                    onEnded()
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    androidx.compose.foundation.layout.Box(modifier = modifier) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    useController = showControls
                    this.resizeMode = resizeMode
                    this.player = player
                }
            },
            update = { view ->
                view.useController = showControls
                view.resizeMode = resizeMode
                view.keepScreenOn = playWhenReady
                view.player = player
            },
            modifier = Modifier.fillMaxSize()
        )
        if (firstFrame != null && !firstFrameRendered) {
            Image(
                bitmap = firstFrame.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun DanceMediaPreview(
    videoUri: Uri,
    previewUri: Uri?,
    modifier: Modifier = Modifier,
    playVideo: Boolean = true,
    muted: Boolean = true
) {
    val context = LocalContext.current
    val previewBitmap = remember(previewUri) {
        previewUri?.let { uri ->
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }.getOrNull()
        }
    }
    val videoFrame = remember(videoUri, playVideo) {
        if (playVideo) {
            null
        } else {
            readFirstVideoFrame(context, videoUri)
        }
    }

    val staticBitmap = previewBitmap ?: videoFrame

    if (staticBitmap != null) {
        Image(
            bitmap = staticBitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize()
        )
    } else {
        DanceVideoPlayer(
            uri = videoUri,
            playWhenReady = playVideo,
            muted = muted,
            loop = true,
            modifier = modifier.fillMaxSize()
        )
    }
}

private fun readFirstVideoFrame(context: android.content.Context, uri: Uri): Bitmap? =
    runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, uri)
            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        }
    }.getOrNull()
