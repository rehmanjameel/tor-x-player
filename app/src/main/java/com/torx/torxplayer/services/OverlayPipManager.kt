package com.torx.torxplayer.services

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.media3.exoplayer.ExoPlayer

object OverlayPipManager {

    var sharedPlayer: ExoPlayer? = null
    var lastPosition: Long = 0L
    var restoreIndex: Int = -1
    var isFromOverlay: Boolean = false

    var isOpeningFullscreen = false

    fun start(context: Context, player: ExoPlayer) {
        sharedPlayer = player
        lastPosition = player.currentPosition
        Log.e("what is here", "start $lastPosition")
        context.startService(
            Intent(context, FloatingPlayerService::class.java)
        )
    }

    fun stop(context: Context) {
        context.stopService(
            Intent(context, FloatingPlayerService::class.java)
        )
    }
}
