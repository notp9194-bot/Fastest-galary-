package com.fastgallery.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.fastgallery.app.data.MediaItem as GalleryMediaItem

@Composable
fun VideoPlayer(item: GalleryMediaItem) {
    val context = LocalContext.current
    val player = remember(item.key) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(item.uri))
            prepare()
            playWhenReady = false
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                this.player = player
                useController = true
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            }
        },
        update = { it.player = player },
        modifier = Modifier.fillMaxSize(),
    )
}