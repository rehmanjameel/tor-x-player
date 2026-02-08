package com.torx.torxplayer.services

import android.app.Service
import android.app.TaskStackBuilder
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.torx.torxplayer.MainActivity
import com.torx.torxplayer.R
import com.torx.torxplayer.VideoPlayerActivity

class FloatingPlayerService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var btnPlayPause: ImageView
    private lateinit var params: WindowManager.LayoutParams

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        floatingView = LayoutInflater.from(this)
            .inflate(R.layout.view_floating_player, null, false)

        params = WindowManager.LayoutParams(
            520,
            320,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val dm = resources.displayMetrics
        params.x = dm.widthPixels - params.width - 24
        params.y = dm.heightPixels - params.height - 24

        floatingView.isClickable = true
        floatingView.isFocusable = true

        val playerView =
            floatingView.findViewById<PlayerView>(R.id.floatingPlayerView)

        /// dragging on to the whole screen
        val controllerRoot =
            playerView.findViewById<View>(R.id.controllerRoot)

        controllerRoot.setOnTouchListener { _, event ->
            when (event.actionMasked) {

                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val newX = initialX + (event.rawX - initialTouchX).toInt()
                    val newY = initialY + (event.rawY - initialTouchY).toInt()

                    params.x = newX.coerceIn(0, screenWidth - params.width)
                    params.y = newY.coerceIn(0, screenHeight - params.height)

                    windowManager.updateViewLayout(floatingView, params)
                    true
                }

                else -> false
            }
        }

        playerView.player = OverlayPlaybackController.player
        OverlayPlaybackController.player?.play()

        // ❌ close
        floatingView.findViewById<ImageView>(R.id.btnClose).setOnClickListener {
            OverlayPlaybackController.player?.pause()
            OverlayPlaybackController.player = null
            stopSelf()
        }

        // ⛶ open fullscreen
        floatingView.findViewById<ImageView>(R.id.btnFullscreen).setOnClickListener {

            OverlayPipManager.isOpeningFullscreen = true
            OverlayPipManager.lastPosition =
                OverlayPlaybackController.player?.currentPosition ?: 0L

            floatingView.findViewById<PlayerView>(R.id.floatingPlayerView).player = null

            val stackBuilder = TaskStackBuilder.create(this)
            stackBuilder.addNextIntent(Intent(this, MainActivity::class.java))
            stackBuilder.addNextIntent(Intent(this, VideoPlayerActivity::class.java))

            stackBuilder.startActivities()
            stopSelf()
        }


        btnPlayPause = floatingView.findViewById(R.id.btnPlayPause)

        btnPlayPause.setOnClickListener {
            OverlayPlaybackController.togglePlayPause()
        }

        OverlayPlaybackController.player?.addListener(
            object : Player.Listener {

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updatePlayPauseIcon(isPlaying)
                }

                override fun onPlaybackStateChanged(state: Int) {
                    val isPlaying =
                        OverlayPlaybackController.player?.isPlaying == true
                    updatePlayPauseIcon(isPlaying)
                }
            }
        )


        OverlayPlaybackController.player?.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) {
                        OverlayPlaybackController.playNext()
                    }
                }
            }
        )

        // ▶ play / pause
        updatePlayPauseIcon(
            OverlayPlaybackController.player?.isPlaying == true
        )

        // ⏭ next
        floatingView.findViewById<ImageView>(R.id.btnNext).setOnClickListener {
            OverlayPlaybackController.playNext()
        }

        // ⏮ previous
        floatingView.findViewById<ImageView>(R.id.btnPrev).setOnClickListener {
            OverlayPlaybackController.playPrevious()
        }


        windowManager.addView(floatingView, params)
    }

    private fun updatePlayPauseIcon(isPlaying: Boolean) {
        btnPlayPause.setImageResource(
            if (isPlaying)
                R.drawable.baseline_pause_circle_filled_24
            else
                R.drawable.baseline_play_arrow_24
        )
    }

    private fun openFullPlayer() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("OPEN_PLAYER", true)
        }
        startActivity(intent)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()

        if (!OverlayPipManager.isOpeningFullscreen) {
            // ONLY when user closes overlay
            OverlayPlaybackController.player?.stop()
            OverlayPlaybackController.player?.clearMediaItems()
            OverlayPlaybackController.player = null
        }

        OverlayPipManager.isOpeningFullscreen = false
        windowManager.removeView(floatingView)
    }

    override fun onBind(intent: Intent?) = null
}


