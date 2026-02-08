package com.torx.torxplayer.services

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

object OverlayPlaybackController {

    var player: ExoPlayer? = null

    fun attachPlayer(exoPlayer: ExoPlayer) {
        player = exoPlayer
    }

    fun togglePlayPause() {
        player?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun playNext() {
        playAt(PlaybackQueue.nextIndex())
    }

    fun playPrevious() {
        playAt(PlaybackQueue.previousIndex())
    }

    private fun playAt(index: Int) {
        PlaybackQueue.currentIndex = index
        val video = PlaybackQueue.current()

        player?.apply {
            stop()
            clearMediaItems()
            setMediaItem(MediaItem.fromUri(video.contentUri.toUri()))
            prepare()
            play()
        }
    }
}

