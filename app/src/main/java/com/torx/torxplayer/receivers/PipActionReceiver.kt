package com.torx.torxplayer.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.torx.torxplayer.MainActivity

class PipActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val activity = context as? MainActivity ?: return

        when (intent.action) {
            MainActivity.ACTION_PIP_PLAY_PAUSE -> activity.onPipPlayPause()
            MainActivity.ACTION_PIP_NEXT -> activity.onPipNext()
            MainActivity.ACTION_PIP_PREVIOUS -> activity.onPipPrevious()
        }
    }
}
