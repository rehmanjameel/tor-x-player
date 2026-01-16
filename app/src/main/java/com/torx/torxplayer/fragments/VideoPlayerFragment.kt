package com.torx.torxplayer.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.hardware.SensorManager
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.OrientationEventListener
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.torx.torxplayer.R
import com.torx.torxplayer.databinding.FragmentVideoPlayerBinding
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.torx.torxplayer.MainActivity
import com.torx.torxplayer.viewmodel.FilesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class VideoPlayerFragment : Fragment() {

    private val args: VideoPlayerFragmentArgs by navArgs()
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
    private lateinit var binding: FragmentVideoPlayerBinding

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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = FragmentVideoPlayerBinding.inflate(inflater, container, false)
        requireActivity().findViewById<BottomNavigationView>(R.id.bottomNavView)?.visibility = View.GONE

        videoList = args.videoUriList.map { it.toUri() }
        videoPathList = args.videoPrivatePathList.toList()
        videoTitleList = args.videoTitle.toList()
        currentIndex = args.position

        val app = requireActivity().application

        viewModel = ViewModelProvider(
            requireActivity(),
            ViewModelProvider.AndroidViewModelFactory.getInstance(app)
        )[FilesViewModel::class.java]


        seekBar = binding.player.findViewById(R.id.seekBar)
        tvCurrentTime = binding.player.findViewById(R.id.tvCurrentTime)
        tvTotalTime = binding.player.findViewById(R.id.tvTotalTime)
        audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
        seekBarVolume = binding.player.findViewById(R.id.seekBarVolume)
        seekBarBrightness = binding.player.findViewById(R.id.seekBarBrightness)
        volumeLayout = binding.player.findViewById(R.id.volumeLayout)
        brightnessLayout = binding.player.findViewById(R.id.brightnessLayout)
        brightnessValue = binding.player.findViewById(R.id.brightnessValue)    // TextView showing "50%"
        soundValue = binding.player.findViewById(R.id.soundValue)

        setFullScreen()
        setLockScreen()
        preparePlayer()

        addBackForward()
        setOrientation()
        initRotationLockButton()

        setBrightness(50)
        seekBarBrightness.progress = 50
        binding.player.findViewById<ImageView>(R.id.custom_play).setOnClickListener {
            playPauseVideo()
        }

        // go back on system back press
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (isLock) return
                    if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        binding.player.findViewById<ImageView>(R.id.imageViewFullScreen).performClick()
                    }

                    (requireActivity() as MainActivity).blockNextPip()

                    // Stop player cleanly
                    releasePlayer()

                    // Navigate back
                    findNavController().navigateUp()
                    stopSeekbarUpdater()

                    //  Lock orientation at current state
                    val currentOrientation = resources.configuration.orientation

                    if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                        requireActivity().requestedOrientation =
                            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                }
            }
        )

        return binding.root
    }

    @OptIn(UnstableApi::class)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.player.findViewById<TextView>(R.id.setPlayBackSpeed).setOnClickListener {
            speedPlayBack(it)
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
            if (args.isPublic) {
                val prevIndex = if (currentIndex - 1 < 0) videoList.size - 1 else currentIndex - 1
                playVideoAt(prevIndex)
            } else {
                val prevIndex = if (currentIndex - 1 < 0) videoPathList.size - 1 else currentIndex - 1
                playVideoAt(prevIndex)
            }
        }

        binding.player.findViewById<ImageView>(R.id.imageViewForward).setOnClickListener {
            if (args.isPublic) {
                val nextIndex = (currentIndex + 1) % videoList.size
                playVideoAt(nextIndex)
            } else {
                val nextIndex = (currentIndex + 1) % videoPathList.size
                playVideoAt(nextIndex)
            }
        }

        binding.player.findViewById<ImageView>(R.id.imageViewVolume).setOnClickListener {
            val audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
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

        binding.player.findViewById<TextView>(R.id.titleText).text =
            videoTitleList.getOrNull(currentIndex) ?: "Untitled"

        // TrackSelector (NO resolution limits)
        val trackSelector = DefaultTrackSelector(requireContext()).apply {
            setParameters(
                buildUponParameters()
                    .setForceHighestSupportedBitrate(true)
            )
        }

        // Renderer with decoder fallback (VERY IMPORTANT)
        val renderersFactory = DefaultRenderersFactory(requireContext())
            .setEnableDecoderFallback(true)

        // Build ExoPlayer
        exoPlayer = ExoPlayer.Builder(requireContext())
            .setTrackSelector(trackSelector)
            .setRenderersFactory(renderersFactory)
            .setSeekBackIncrementMs(INCREMENT_MILLIS)
            .setSeekForwardIncrementMs(INCREMENT_MILLIS)
            .build()

        (requireActivity() as MainActivity).attachPlayer(exoPlayer)

        exoPlayer?.playWhenReady = true
        binding.player.player = exoPlayer

        // MediaItem handling (Public + Private)
        val mediaItem = if (args.isPublic) {
            // Public MediaStore video
            MediaItem.fromUri(args.videoUri.toUri())
        } else {
            // Private internal storage file
            val file = File(args.videoPrivate)
            require(file.exists()) { "Private video file missing" }
            MediaItem.fromUri(file.toUri())
        }

        exoPlayer?.apply {
            setMediaItem(mediaItem)
            seekTo(playbackPosition)
            prepare()
        }

        // Detect if video is actually 4K
        exoPlayer?.videoFormat?.let {
            if (it.width >= 3840 && it.height >= 2160) {
                Log.e("Player", "4K video playing")
            }
        }

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
                //new pip buttons
                val activity = requireActivity() as MainActivity
                if (activity.isInPictureInPictureMode) {
                }
                activity.updatePipActions(isPlaying)
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED && videoList.size > 1) {
                    playVideoAt((currentIndex + 1) % videoList.size)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("ExoPlayer", "Playback error", error)
            }
        })

        // Brightness & volume setup (unchanged)
        brightness =
            (requireActivity().window.attributes.screenBrightness * 100)
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

    private fun startSeekbarUpdater() {
        seekJob?.cancel()
        seekJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main.immediate) {
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

    private fun addBackForward() {
        binding.player.findViewById<ImageView>(R.id.custom_rew).setOnClickListener {
            exoPlayer?.let {
                val newPosition = (it.currentPosition - 10_000).coerceAtLeast(0L) // rewind 10 seconds
                it.seekTo(newPosition)
            }
        }

        binding.player.findViewById<ImageView>(R.id.custom_ffwd).setOnClickListener {
            exoPlayer?.let {
                val duration = it.duration
                val newPosition = (it.currentPosition + 10_000).coerceAtMost(duration)
                it.seekTo(newPosition)
            }
        }

    }
    private fun setOrientation() {
        mOrientationListener = object : OrientationEventListener(
            requireContext(),
            SensorManager.SENSOR_DELAY_NORMAL
        ) {
            override fun onOrientationChanged(orientation: Int) {

                if (isRotationLocked) return // Skip rotation

                when (orientation) {
                    in 1..89 -> {
                        requireActivity().requestedOrientation =
                            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                    in 180..360 -> {
                        requireActivity().requestedOrientation =
                            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    }
                    in 90..180 -> {
                        requireActivity().requestedOrientation =
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
                    requireActivity().requestedOrientation =
                        ActivityInfo.SCREEN_ORIENTATION_LOCKED
                } else {
                    requireActivity().requestedOrientation =
                        ActivityInfo.SCREEN_ORIENTATION_LOCKED
                }

                btnRotateLock.setImageResource(R.drawable.baseline_screen_lock_rotation_24)

            } else {
                //  Unlock orientation (follow sensors)
                requireActivity().requestedOrientation =
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR

                btnRotateLock.setImageResource(R.drawable.baseline_screen_rotation_24)
            }
        }
    }

    private fun lockScreen(lock: Boolean) {
        if (lock) {
            binding.player.findViewById<LinearLayout>(R.id.linearLayoutControlUp).visibility = View.INVISIBLE
            binding.player.findViewById<LinearLayout>(R.id.linearLayoutControlBottom).visibility = View.INVISIBLE
            binding.player.findViewById<LinearLayout>(R.id.ffbLayout).visibility = View.INVISIBLE
        } else {
            binding.player.findViewById<LinearLayout>(R.id.linearLayoutControlUp).visibility = View.VISIBLE
            binding.player.findViewById<LinearLayout>(R.id.linearLayoutControlBottom).visibility = View.VISIBLE
            binding.player.findViewById<LinearLayout>(R.id.ffbLayout).visibility = View.VISIBLE
        }
    }

    private fun setLockScreen() {
        binding.player.findViewById<ImageView>(R.id.imageViewLock).setOnClickListener {
            if (!isLock) {
                binding.player.findViewById<ImageView>(R.id.imageViewLock).setImageDrawable(
                    ContextCompat.getDrawable(
                        requireContext(),
                        R.drawable.baseline_lock_outline_24
                    )
                )
            } else {
                binding.player.findViewById<ImageView>(R.id.imageViewLock).setImageDrawable(
                    ContextCompat.getDrawable(
                        requireContext(),
                        R.drawable.baseline_lock_open_24
                    )
                )
            }
            isLock = !isLock
            lockScreen(isLock)
        }

        // go back with cancel button press
        binding.player.findViewById<ImageView>(R.id.closePlayer).setOnClickListener {
            findNavController().navigateUp()
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private fun setFullScreen() {
        binding.player.findViewById<ImageView>(R.id.imageViewFullScreen).setOnClickListener {

            if (!isFullScreen) {
                binding.player.findViewById<ImageView>(R.id.imageViewFullScreen).setImageDrawable(
                    ContextCompat.getDrawable(
                        requireContext(),
                        R.drawable.outline_fullscreen_exit_24
                    )
                )
                requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

            } else {
                binding.player.findViewById<ImageView>(R.id.imageViewFullScreen).setImageDrawable(
                    ContextCompat.getDrawable(requireContext(), R.drawable.baseline_fullscreen_24))
                requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

            }
            isFullScreen = !isFullScreen
        }
    }

    private fun playPauseVideo() {
        if (isVideoStopped) {
            exoPlayer?.seekTo(0)
            exoPlayer?.play()
            isVideoStopped = false
            binding.player.findViewById<ImageView>(R.id.custom_play).setImageResource(R.drawable.baseline_pause_circle_filled_24)

            return
        }

        if (exoPlayer?.isPlaying == true) {
            binding.player.findViewById<ImageView>(R.id.custom_play).setImageResource(R.drawable.baseline_play_arrow_24)
            exoPlayer?.pause()

        } else {
            binding.player.findViewById<ImageView>(R.id.custom_play).setImageResource(R.drawable.baseline_pause_circle_filled_24)
            exoPlayer?.play()
        }
    }

    private fun playVideoAt(index: Int) {

        if (index < 0) return

        currentIndex = index

        val mediaItem: MediaItem
        val videoTitle: String

        if (args.isPublic) {
            // ---------- PUBLIC VIDEO ----------
            if (index >= videoList.size) return

            val videoUri = videoList[currentIndex] // content:// uri
            viewModel.updateVideoIsHistory(videoUri.toString(), true)

            mediaItem = MediaItem.fromUri(videoUri)
            videoTitle = videoTitleList.getOrNull(currentIndex) ?: "Untitled"

        } else {
            // ---------- PRIVATE VIDEO ----------
            if (index >= videoPathList.size) return

            val path = videoPathList[currentIndex]
            val file = File(path)

            if (!file.exists()) {
                // Defensive: file missing → stop
                return
            }

            mediaItem = MediaItem.fromUri(file.toUri())
            videoTitle = videoTitleList.getOrNull(currentIndex) ?: "Untitled"
        }

        exoPlayer?.apply {
            setMediaItem(mediaItem)
            seekTo(0)
            playWhenReady = true
            prepare()
        }

        binding.player
            .findViewById<TextView>(R.id.titleText)
            .text = videoTitle
    }

    private fun setBrightness(value: Int) {
        val lp = requireActivity().window.attributes
        lp.screenBrightness = value / 100f // 0..1
        requireActivity().window.attributes = lp
    }

    private fun speedPlayBack(view: View) {
        val popupMenu = PopupMenu(requireContext(), view)
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

    // advance pip feature
    fun onPipModeChanged(isInPip: Boolean) {
        if (isInPip) {
            hideControls()
        } else {
            showControls()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        binding.player.useController = !isInPictureInPictureMode

        if (isInPictureInPictureMode) hideControls()
        else showControls()
    }

    private fun hideControls() {
        binding.player.findViewById<ImageView>(R.id.closePlayer).visibility = View.GONE
        binding.player.findViewById<TextView>(R.id.titleText).visibility = View.GONE

        binding.player.findViewById<LinearLayout>(R.id.linearLayoutControlUp).visibility = View.GONE
        binding.player.findViewById<LinearLayout>(R.id.linearLayoutControlBottom).visibility = View.GONE

        binding.player.findViewById<LinearLayout>(R.id.brightnessLayout).visibility = View.GONE
        binding.player.findViewById<LinearLayout>(R.id.volumeLayout).visibility = View.GONE
        binding.player.findViewById<LinearLayout>(R.id.ffbLayout).visibility = View.GONE
        binding.player.findViewById<ImageView>(R.id.imageViewLock).visibility = View.GONE
    }

    private fun showControls() {
        binding.player.findViewById<ImageView>(R.id.closePlayer).visibility = View.VISIBLE
        binding.player.findViewById<TextView>(R.id.titleText).visibility = View.VISIBLE

        binding.player.findViewById<LinearLayout>(R.id.linearLayoutControlUp).visibility = View.VISIBLE
        binding.player.findViewById<LinearLayout>(R.id.linearLayoutControlBottom).visibility = View.VISIBLE
        binding.player.findViewById<LinearLayout>(R.id.ffbLayout).visibility = View.VISIBLE
        binding.player.findViewById<ImageView>(R.id.imageViewLock).visibility = View.VISIBLE
    }

    override fun onStop() {
        super.onStop()

        stopSeekbarUpdater()
        exoPlayer?.stop()
    }

    // new buttons
    fun togglePlayPauseFromPip() {
        exoPlayer?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun playNextFromPip() {
        val nextIndex = (currentIndex + 1) % videoList.size
        playVideoAt(nextIndex)
    }

    fun playPreviousFromPip() {
        val prevIndex =
            if (currentIndex - 1 < 0) videoList.size -1 else currentIndex - 1
        playVideoAt(prevIndex)
    }

/////////////////

    override fun onDestroy() {
        super.onDestroy()
        stopSeekbarUpdater()
        exoPlayer?.release()
    }

    override fun onPause() {
        super.onPause()
        stopSeekbarUpdater()
    }

    private var playWhenReady = true

    private fun releasePlayer() {
        exoPlayer?.let { player ->

            // Save playback state
            playbackPosition = player.currentPosition
            playWhenReady = player.playWhenReady

            // Detach from PlayerView (IMPORTANT)
            binding.player.player = null

            // Release player
            player.release()
        }

        exoPlayer = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopSeekbarUpdater()
    }

    override fun onResume() {
        super.onResume()

        val activity = requireActivity() as MainActivity

        // Allow PiP again
        activity.setPipAllowed(true)

        // VERY IMPORTANT: reset back-press PiP block
        activity.resetBlockNextPip()

        // Re-attach player to Activity (PiP depends on this)
        exoPlayer?.let {
            activity.attachPlayer(it)      // <-- REQUIRED
            binding.player.player = it     // <-- keep this
        }
    }

    companion object {
        private var isFullScreen = false
        private var isLock = false
        private var isVideoStopped = false
        private const val INCREMENT_MILLIS = 5000L
    }

}