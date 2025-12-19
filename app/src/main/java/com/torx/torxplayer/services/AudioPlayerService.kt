package com.torx.torxplayer.services


import android.app.*
import android.content.Intent
import android.os.*
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.*
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerNotificationManager
import com.torx.torxplayer.R

@UnstableApi
class AudioPlayerService : Service() {

    companion object {
        const val CHANNEL_ID = "audio_playback"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PLAY = "play"
        const val ACTION_PAUSE = "pause"
        const val ACTION_NEXT = "next"
        const val ACTION_PREV = "prev"
    }
    private var currentTitle: String = ""

    lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private lateinit var notificationManager: PlayerNotificationManager
    private var playlist: List<MediaItem> = emptyList()
    private var currentIndex = 0

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        player = ExoPlayer.Builder(this).build()

        mediaSession = MediaSession.Builder(this, player).build()

        notificationManager =
            PlayerNotificationManager.Builder(
                this,
                NOTIFICATION_ID,
                CHANNEL_ID
            )
                .setMediaDescriptionAdapter(descriptionAdapter)
                .setNotificationListener(notificationListener)
                .build()

        notificationManager.setPlayer(player)
        notificationManager.setMediaSessionToken(mediaSession.platformToken)
        notificationManager.setUseNextAction(true)
        notificationManager.setUsePreviousAction(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        when (intent?.action) {
            ACTION_PLAY -> player.play()
            ACTION_PAUSE -> player.pause()
            ACTION_NEXT -> playNext()
            ACTION_PREV -> playPrevious()
        }
        return START_STICKY
    }


    fun playPlaylist(
        uris: List<String>,
        titles: List<String>,
        startIndex: Int
    ) {
        playlist = uris.mapIndexed { index, uri ->
            MediaItem.Builder()
                .setUri(uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(titles.getOrNull(index) ?: "Audio")
                        .build()
                )
                .build()
        }

        currentIndex = startIndex
        currentTitle = titles.getOrNull(startIndex) ?: ""

        player.setMediaItems(playlist, startIndex, 0)
        player.prepare()
        player.play()
    }



    override fun onDestroy() {
        notificationManager.setPlayer(null)
        mediaSession.release()
        player.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    inner class LocalBinder : Binder() {
        fun getService(): AudioPlayerService = this@AudioPlayerService
    }

    private val binder = LocalBinder()

    private val descriptionAdapter =
        object : PlayerNotificationManager.MediaDescriptionAdapter {

            override fun getCurrentContentTitle(player: Player): CharSequence =
                currentTitle.ifEmpty { getString(R.string.app_name) }

            override fun getCurrentContentText(player: Player): CharSequence =
                getString(R.string.app_name) // OR return ""

            override fun createCurrentContentIntent(player: Player): PendingIntent? =
                null

            override fun getCurrentLargeIcon(
                player: Player,
                callback: PlayerNotificationManager.BitmapCallback
            ) = null
        }


    private val notificationListener =
        object : PlayerNotificationManager.NotificationListener {

            override fun onNotificationPosted(
                notificationId: Int,
                notification: Notification,
                ongoing: Boolean
            ) {
                startForeground(notificationId, notification)
            }

            override fun onNotificationCancelled(
                notificationId: Int,
                dismissedByUser: Boolean
            ) {
                stopSelf()
            }
        }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Audio Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun playNext() {
        if (currentIndex < playlist.size - 1) {
            currentIndex++
            player.seekTo(currentIndex, 0)
            player.play()
            updateTitleFromPlayer()
        }
    }

    private fun playPrevious() {
        if (currentIndex > 0) {
            currentIndex--
            player.seekTo(currentIndex, 0)
            player.play()
            updateTitleFromPlayer()
        }
    }

    private fun updateTitleFromPlayer() {
        val title = player.currentMediaItem
            ?.mediaMetadata
            ?.title
            ?.toString()

        currentTitle = title ?: ""
    }

}
