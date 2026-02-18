package com.torx.torxplayer

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.hardware.SensorManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.OrientationEventListener
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import com.torx.torxplayer.databinding.ActivityVideoPlayerBinding
import com.torx.torxplayer.services.OverlayPipManager
import com.torx.torxplayer.services.OverlayPlaybackController
import com.torx.torxplayer.services.PlaybackQueue
import com.torx.torxplayer.services.VideoCache
import com.torx.torxplayer.utils.AppGlobals
import com.torx.torxplayer.viewmodel.FilesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class VideoPlayerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityVideoPlayerBinding

    private var playbackPosition = 0L
    private var exoPlayer: ExoPlayer? = null
    var mOrientationListener: OrientationEventListener? = null
    private var seekJob: Job? = null

    private var videoList: List<Uri> = emptyList()
    private var videoPathList: List<String> = emptyList()
    private var videoTitleList: List<String> = emptyList()
    private var currentIndex = 0
    private lateinit var seekBar: SeekBar
    private lateinit var seekBarBrightness: SeekBar
    private lateinit var volumeLayout: LinearLayout
    private lateinit var brightnessLayout: LinearLayout
    private lateinit var seekBarVolume: SeekBar
    private lateinit var brightnessValue: TextView
    private lateinit var soundValue: TextView
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var pipMode: ImageView

    private lateinit var viewModel: FilesViewModel
    private var brightness: Float = 50F
    private var volume: Float = 50f
    private lateinit var audioManager: AudioManager

    private var lastTapLeft = 0L
    private var lastTapRight = 0L
    private val skipMs = 10000L // 10 sec
    private var isRotationLocked = false

    private val hideBrightnessRunnable = Runnable {
        brightnessLayout.visibility = View.GONE
    }

    private val handler = Handler(Looper.getMainLooper())

    private val appGlobals = AppGlobals()

    private fun isRestoringFromOverlay(): Boolean {
        return OverlayPipManager.isFromOverlay && OverlayPipManager.sharedPlayer != null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        currentIndex = PlaybackQueue.currentIndex
        // Restore state after rotation (portrait <-> landscape)
        savedInstanceState?.let { bundle ->
            lastSavedPosition = bundle.getLong(KEY_SAVED_POSITION, 0L)
            currentIndex = bundle.getInt(KEY_CURRENT_INDEX, currentIndex)
            needsPrepare = bundle.getBoolean(KEY_NEEDS_PREPARE, false)
        }
        Log.e("position1", currentIndex.toString())
        Log.e("video list", videoList.toString())
        for (video in videoList) {
            Log.e("video uri", video.toString())
        }


        val app = application

        viewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(app)
        )[FilesViewModel::class.java]


        seekBar = binding.player.findViewById(R.id.seekBar)
        tvCurrentTime = binding.player.findViewById(R.id.tvCurrentTime)
        tvTotalTime = binding.player.findViewById(R.id.tvTotalTime)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        seekBarVolume = binding.player.findViewById(R.id.seekBarVolume)
        seekBarBrightness = binding.player.findViewById(R.id.seekBarBrightness)
        volumeLayout = binding.player.findViewById(R.id.volumeLayout)
        brightnessLayout = binding.player.findViewById(R.id.brightnessLayout)
        brightnessValue = binding.player.findViewById(R.id.brightnessValue)    // TextView showing "50%"
        soundValue = binding.player.findViewById(R.id.soundValue)
        pipMode = binding.player.findViewById(R.id.imageViewPIP)


        initializePlayerComponents()

        setFullScreen()
        setLockScreen()
        if (isRestoringFromOverlay()) {
            exoPlayer = OverlayPipManager.sharedPlayer
            binding.player.player = exoPlayer

            updateTitleFromQueue()
            startSeekbarUpdater()

            OverlayPipManager.isFromOverlay = false
        } else {
            preparePlayer()
            // After restore from rotation, player is ready (don't trigger needsPrepare path)
            if (savedInstanceState != null) needsPrepare = false
        }


//        addBackForward()
        setOrientation()
        initRotationLockButton()

        setBrightness(50)
        seekBarBrightness.progress = 50
        binding.player.findViewById<ImageView>(R.id.custom_play).setOnClickListener {
            playPauseVideo()
        }


        performBackPress()

        pipMode.setOnClickListener {

            if (!hasOverlayPermission()) {
                dialogToAskOverlayPermission()
                return@setOnClickListener
            } else {
                // Detach PlayerView
                attachPlayerSafely(exoPlayer!!)

                // Save overlay restore state
                OverlayPipManager.sharedPlayer = exoPlayer
                OverlayPipManager.lastPosition = exoPlayer!!.currentPosition
                OverlayPipManager.isFromOverlay = true

                binding.player.player = null
                binding.player.onPause()

                Log.e("what is here", "start")
                // Start overlay ONCE
                OverlayPipManager.start(applicationContext, exoPlayer!!)

                stopSeekbarUpdater()
                finish()
            }
        }


    }

    private fun attachPlayerSafely(player: ExoPlayer) {
        binding.player.player = null
        binding.player.onPause()
        binding.player.player = player
        binding.player.onResume()
    }

    private fun performBackPress() {

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    handleExit()
                }
            }
        )
    }

    private fun handleExit() {
        if (isLock) return

        exoPlayer?.apply {
            stop()
            clearMediaItems()
        }

        OverlayPipManager.sharedPlayer = null
        OverlayPipManager.isFromOverlay = false
        OverlayPipManager.lastPosition = 0L
        PlaybackQueue.currentIndex = -1

        if (isTaskRoot) {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
            startActivity(intent)
        }

        finish()
    }

    @OptIn(UnstableApi::class)
    private fun initializePlayerComponents(){
        binding.player.findViewById<TextView>(R.id.setPlayBackSpeed).setOnClickListener {
            speedPlayBack(it)
        }
//        OverlayPipManagerVideoCache.videoList = videoList

        if (OverlayPipManager.isFromOverlay &&
            OverlayPipManager.restoreIndex != -1
        ) {
            Log.e("what is here", "restore $currentIndex")
            // Overlay restore path
            videoList = VideoCache.videoList
            videoPathList = VideoCache.videoPathList
            videoTitleList = VideoCache.videoTitleList
            currentIndex = OverlayPipManager.restoreIndex
        } else {

            videoList = PlaybackQueue.videos.map { it.contentUri.toUri() }
            videoTitleList = PlaybackQueue.videos.map { it.title }
            videoPathList = PlaybackQueue.videos.map { it.privatePath ?: ""}
            require(PlaybackQueue.isValid()) {
                "PlaybackQueue is invalid"
            }

            // 🔥 NEVER read index from intent
            currentIndex = PlaybackQueue.currentIndex


//            appGlobals.getValueInt("video_position")
        }

        var ratioMode = 0

        val aspectButton = binding.player.findViewById<ImageView>(R.id.btnAspectRatio)

        // Icon list in order of mode
        val ratioIcons = listOf(
            R.drawable.baseline_fit_screen_24,        // 0
            R.drawable.baseline_open_in_full_24,       // 1
            R.drawable.baseline_zoom_in_map_24,       // 2
            R.drawable.baseline_aspect_ratio_24    // 3
        )

        aspectButton.setOnClickListener {
            ratioMode = (ratioMode + 1) % 4   // cycle through 4 modes

            when (ratioMode) {
                0 -> {
                    binding.player.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    showSkipAnimation("Fit to Screen")
                }
                1 -> {
                    binding.player.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                    showSkipAnimation("Fill (Crop)")
                }
                2 -> {
                    binding.player.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    showSkipAnimation("Zoom")
                }
                3 -> {
                    binding.player.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                    showSkipAnimation("Original Ratio")
                }
            }

            // update icon
            aspectButton.setImageResource(ratioIcons[ratioMode])
        }


        binding.player.findViewById<View>(R.id.tapLeft).setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastTapLeft < 300) {
                val newPos = (exoPlayer!!.currentPosition?.minus(skipMs))?.coerceAtLeast(0)
                exoPlayer?.seekTo(newPos ?: 0)
                showSkipAnimation("-10s")
            }
            lastTapLeft = now
        }

        binding.player.findViewById<View>(R.id.tapRight).setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastTapRight < 300) {
                val newPos = (exoPlayer?.currentPosition?.plus(skipMs))?.coerceAtMost(exoPlayer!!.duration)
                exoPlayer?.seekTo(newPos ?: 0)
                showSkipAnimation("+10s")
            }
            lastTapRight = now
        }

        binding.player.findViewById<ImageView>(R.id.btnBackward).setOnClickListener {
            if (appGlobals.getValueBoolean("is_public")) {
                val prevIndex = if (currentIndex - 1 < 0) videoList.size - 1 else currentIndex - 1
                playVideoAt(prevIndex)
            } else {
                val prevIndex = if (currentIndex - 1 < 0) videoPathList.size - 1 else currentIndex - 1
                playVideoAt(prevIndex)
            }
        }

        binding.player.findViewById<ImageView>(R.id.imageViewForward).setOnClickListener {
            if (appGlobals.getValueBoolean("is_public")) {
                val nextIndex = (currentIndex + 1) % videoList.size
                playVideoAt(nextIndex)
            } else {
                val nextIndex = (currentIndex + 1) % videoPathList.size
                playVideoAt(nextIndex)
            }
        }

        binding.player.findViewById<ImageView>(R.id.imageViewVolume).setOnClickListener {
            val audioManager = this.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.adjustVolume(AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
        }

        // Hide brightness slider when tapping anywhere
        binding.root.setOnClickListener {
            brightnessLayout.visibility = View.GONE
            handler.removeCallbacks(hideBrightnessRunnable)
        }

        seekBarBrightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                setBrightness(progress)
                brightnessValue.text = "${progress}%"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.player.findViewById<ImageView>(R.id.imageViewBrightness).setOnClickListener {
            brightnessLayout.visibility = View.VISIBLE

            // Reset hide timer
            handler.removeCallbacks(hideBrightnessRunnable)
            handler.postDelayed(hideBrightnessRunnable, 3500) // 3.5 seconds
        }
    }

    private fun showSkipAnimation(text: String) {
        val skipText = binding.player.findViewById<TextView>(R.id.skipText)
        skipText.text = text
        skipText.alpha = 1f
        skipText.animate().alpha(0f).setDuration(600).start()
    }

    @OptIn(UnstableApi::class)
    private fun preparePlayer() {

        Log.e("curent index", currentIndex.toString())
        binding.player.findViewById<TextView>(R.id.titleText).text =
            videoTitleList.getOrNull(currentIndex) ?: "Untitled"

        // TrackSelector (NO resolution limits)
        val trackSelector = DefaultTrackSelector(this).apply {
            setParameters(
                buildUponParameters()
                    .setForceHighestSupportedBitrate(true)
            )
        }

        // Renderer with decoder fallback (VERY IMPORTANT)
        val renderersFactory = DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)

        // Build ExoPlayer
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(this)
                .setTrackSelector(trackSelector)
                .setRenderersFactory(renderersFactory)
                .setSeekBackIncrementMs(INCREMENT_MILLIS)
                .setSeekForwardIncrementMs(INCREMENT_MILLIS)
                .build()
        }

//        (this as MainActivity).attachPlayer(exoPlayer)

        exoPlayer?.playWhenReady = true
        binding.player.player = exoPlayer


        val video = PlaybackQueue.current()

        val mediaItem = if (appGlobals.getValueBoolean("is_public")) {
            MediaItem.fromUri(video.contentUri.toUri())
        } else {
            binding.player.findViewById<ImageView>(R.id.imageViewPIP).visibility = View.GONE
            val file = File(video.privatePath!!)
            MediaItem.fromUri(file.toUri())
        }

        exoPlayer?.apply {
            stop()
            clearMediaItems()
            setMediaItem(mediaItem)
            prepare()
            if (lastSavedPosition > 0) {
                seekTo(lastSavedPosition)
                lastSavedPosition = 0L
            }
            play()
        }

        // Detect if video is actually 4K
        exoPlayer?.videoFormat?.let {
            if (it.width >= 3840 && it.height >= 2160) {
                Log.e("Player", "4K video playing")
            }
        }

        OverlayPlaybackController.attachPlayer(exoPlayer!!)

        // Player Listener
        exoPlayer?.addListener(object : Player.Listener {

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                binding.player.findViewById<ImageView>(R.id.custom_play)
                    .setImageResource(
                        if (isPlaying)
                            R.drawable.baseline_pause_circle_filled_24
                        else
                            R.drawable.baseline_play_arrow_24
                    )
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED && !OverlayPipManager.isFromOverlay) {
                    playVideoAt(PlaybackQueue.nextIndex())
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("ExoPlayer", "Playback error", error)
            }
        })

        // Brightness & volume setup (unchanged)
        brightness =
            (this.window.attributes.screenBrightness * 100)
                .toInt()
                .coerceIn(0, 100)
                .toFloat()
        seekBarBrightness.progress = brightness.toInt()

        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        volume =
            (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / maxVolume).toFloat()
        seekBarVolume.progress = volume.toInt()

        startSeekbarUpdater()

        // SeekBar listener
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) exoPlayer?.seekTo(progress.toLong())
                tvCurrentTime.text = formatTime(progress.toLong())
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    private fun updateTitleFromQueue() {
        val index = PlaybackQueue.currentIndex
        if (index in videoTitleList.indices) {
            binding.player.findViewById<TextView>(R.id.titleText).text =
                videoTitleList[index]
        }
    }

    private fun startSeekbarUpdater() {
        Log.e("what is here", "resume seekbar started 1")
        seekJob?.cancel()
        Log.e("what is here", "resume seekbar started 2")
        seekJob = lifecycleScope.launch(Dispatchers.Main.immediate) {
            while (isActive) {
                exoPlayer?.let { p ->
                    val dur = p.duration.takeIf { it > 0 } ?: 0
                    val pos = p.currentPosition.takeIf { it >= 0 } ?: 0
                    seekBar.max = dur.toInt()
                    seekBar.progress = pos.toInt()
                    tvCurrentTime.text = formatTime(pos)
                    tvTotalTime.text = formatTime(dur)
                }
                delay(300)
            }
        }
    }

    private fun stopSeekbarUpdater() {
        seekJob?.cancel()
        seekJob = null
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }


    private fun setOrientation() {
        mOrientationListener = object : OrientationEventListener(
            this,
            SensorManager.SENSOR_DELAY_NORMAL
        ) {
            override fun onOrientationChanged(orientation: Int) {

                if (isRotationLocked) return // Skip rotation

                when (orientation) {
                    in 1..89 -> {
                        requestedOrientation =
                            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                    in 180..360 -> {
                        requestedOrientation =
                            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    }
                    in 90..180 -> {
                        requestedOrientation =
                            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                    }
                }
            }
        }
    }

    private fun initRotationLockButton() {
        val btnRotateLock =
            binding.player.findViewById<ImageView>(R.id.imageViewRotateLock)

        btnRotateLock.setOnClickListener {

            isRotationLocked = !isRotationLocked

            if (isRotationLocked) {
                //  Lock orientation at current state
                val currentOrientation = resources.configuration.orientation

                if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                    requestedOrientation =
                        ActivityInfo.SCREEN_ORIENTATION_LOCKED
                } else {
                    requestedOrientation =
                        ActivityInfo.SCREEN_ORIENTATION_LOCKED
                }

                btnRotateLock.setImageResource(R.drawable.baseline_screen_lock_rotation_24)

            } else {
                //  Unlock orientation (follow sensors)
                requestedOrientation =
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR

                btnRotateLock.setImageResource(R.drawable.baseline_screen_rotation_24)
            }
        }
    }

    private fun lockScreen(lock: Boolean) {
        if (lock) {
//            binding.player.findViewById<LinearLayout>(R.id.linearLayoutControlUp).visibility = View.INVISIBLE
            binding.player.findViewById<LinearLayout>(R.id.linearLayoutControlBottom).visibility = View.INVISIBLE
            binding.player.findViewById<LinearLayout>(R.id.seekbarLayout).visibility = View.INVISIBLE
            binding.player.findViewById<LinearLayout>(R.id.ffbLayout).visibility = View.INVISIBLE
        } else {
//            binding.player.findViewById<LinearLayout>(R.id.linearLayoutControlUp).visibility = View.VISIBLE
            binding.player.findViewById<LinearLayout>(R.id.seekbarLayout).visibility = View.VISIBLE
            binding.player.findViewById<LinearLayout>(R.id.linearLayoutControlBottom).visibility = View.VISIBLE
            binding.player.findViewById<LinearLayout>(R.id.ffbLayout).visibility = View.VISIBLE
        }
    }

    private fun setLockScreen() {
        binding.player.findViewById<ImageView>(R.id.imageViewLock).setOnClickListener {
            if (!isLock) {
                binding.player.findViewById<ImageView>(R.id.imageViewLock).setImageDrawable(
                    ContextCompat.getDrawable(
                        this,
                        R.drawable.baseline_lock_outline_24
                    )
                )
            } else {
                binding.player.findViewById<ImageView>(R.id.imageViewLock).setImageDrawable(
                    ContextCompat.getDrawable(
                        this,
                        R.drawable.baseline_lock_open_24
                    )
                )
            }
            isLock = !isLock
            lockScreen(isLock)
        }

        // go back with cancel button press
        binding.player.findViewById<ImageView>(R.id.closePlayer).setOnClickListener {
//            findNavController().navigateUp()

            handleExit()
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private fun setFullScreen() {
        binding.player.findViewById<ImageView>(R.id.imageViewFullScreen).setOnClickListener {

            if (!isFullScreen) {
                binding.player.findViewById<ImageView>(R.id.imageViewFullScreen).setImageDrawable(
                    ContextCompat.getDrawable(
                        this,
                        R.drawable.outline_fullscreen_exit_24
                    )
                )
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

            } else {
                binding.player.findViewById<ImageView>(R.id.imageViewFullScreen).setImageDrawable(
                    ContextCompat.getDrawable(this, R.drawable.baseline_fullscreen_24))
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

            }
            isFullScreen = !isFullScreen
        }
    }

    private fun playPauseVideo() {

        if (needsPrepare) {
            val index = PlaybackQueue.currentIndex
            if (index in videoList.indices) {
                playVideoAt(index, lastSavedPosition)
                needsPrepare = false
                lastSavedPosition = 0L
            }
            return
        }

        if (exoPlayer?.isPlaying == true) {
            exoPlayer?.pause()
            binding.player.findViewById<ImageView>(R.id.custom_play)
                .setImageResource(R.drawable.baseline_play_arrow_24)
        } else {
            exoPlayer?.play()
            binding.player.findViewById<ImageView>(R.id.custom_play)
                .setImageResource(R.drawable.baseline_pause_circle_filled_24)
        }
    }


    private fun playVideoAt(index: Int, restorePosition: Long = 0L) {

        if (index !in videoList.indices) return

        PlaybackQueue.currentIndex = index
        currentIndex = index

        val mediaItem = if (appGlobals.getValueBoolean("is_public")) {
            MediaItem.fromUri(videoList[index])
        } else {
            MediaItem.fromUri(File(videoPathList[index]).toUri())
        }

        exoPlayer?.apply {
            stop()
            clearMediaItems()
            setMediaItem(mediaItem)
            prepare()

            if (restorePosition > 0) {
                seekTo(restorePosition)
            }

            play()
        }

        binding.player.findViewById<TextView>(R.id.titleText).text =
            videoTitleList.getOrNull(index) ?: "Untitled"
    }


    private fun setBrightness(value: Int) {
        val lp = window.attributes
        lp.screenBrightness = value / 100f // 0..1
        window.attributes = lp
    }

    private fun speedPlayBack(view: View) {
        val popupMenu = PopupMenu(this, view)
        popupMenu.inflate(R.menu.playback_speed)

        popupMenu.setOnMenuItemClickListener { item ->
            when(item.itemId) {
                R.id.x0_5 -> {
                    exoPlayer?.setPlaybackSpeed(0.5f)
                    binding.player.findViewById<TextView>(R.id.setPlayBackSpeed).text = "0.5x"
                    true
                }

                R.id.x1_0 -> {
                    exoPlayer?.setPlaybackSpeed(1.0f)
                    binding.player.findViewById<TextView>(R.id.setPlayBackSpeed).text = "1.0x"
                    true
                }

                R.id.x1_5 -> {
                    exoPlayer?.setPlaybackSpeed(1.5f)
                    binding.player.findViewById<TextView>(R.id.setPlayBackSpeed).text = "1.5x"
                    true
                }

                R.id.x2_0 -> {
                    exoPlayer?.setPlaybackSpeed(2.0f)
                    binding.player.findViewById<TextView>(R.id.setPlayBackSpeed).text = "2.0x"
                    true
                }

                else -> false
            }
        }

        popupMenu.show()
    }

    fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true
    }

    private fun dialogToAskOverlayPermission() {
        val dialog = AlertDialog.Builder(this)
        .setTitle("Enable PIP Mode")
    .setMessage("${getString(R.string.app_name)} needs Draw/Display over other apps permission to play the videos on the top of other apps.")
        .setPositiveButton("Grant Permission") { _, _ ->
            requestOverlayPermission()
        }
        .setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }
        .create()
        dialog.show()
    }

    private var needsPrepare = false
    private var lastSavedPosition: Long = 0L


    override fun onStop() {
        super.onStop()

        stopSeekbarUpdater()

        if (!OverlayPipManager.isFromOverlay) {
            lastSavedPosition = exoPlayer?.currentPosition ?: 0L

            exoPlayer?.apply {
                stop()
                clearMediaItems()
            }
            needsPrepare = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSeekbarUpdater()
    }

    override fun onPause() {
        super.onPause()
        stopSeekbarUpdater()
    }

    override fun onResume() {
        super.onResume()

        // Stop overlay UI only (player remains)
        OverlayPipManager.stop(this)

        val player = OverlayPipManager.sharedPlayer
        if (player != null) {
            exoPlayer = player   // 🔥 VERY IMPORTANT
            binding.player.player = player
            player.seekTo(OverlayPipManager.lastPosition)
            player.play()
            Log.e("what is here", "resume seekbar started")
            startSeekbarUpdater()
        }

        OverlayPipManager.isFromOverlay = false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save playback position for restoration on rotation (portrait <-> landscape)
        val position = exoPlayer?.currentPosition ?: lastSavedPosition
        outState.putLong(KEY_SAVED_POSITION, position)
        outState.putInt(KEY_CURRENT_INDEX, currentIndex)
        outState.putBoolean(KEY_NEEDS_PREPARE, needsPrepare)
    }

    companion object {
        private const val KEY_SAVED_POSITION = "saved_playback_position"
        private const val KEY_CURRENT_INDEX = "current_index"
        private const val KEY_NEEDS_PREPARE = "needs_prepare"
        private var isFullScreen = false
        private var isLock = false
        private var isVideoStopped = false
        private const val INCREMENT_MILLIS = 5000L
    }
}
