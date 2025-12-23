package com.torx.torxplayer.fragments

import android.annotation.SuppressLint
import android.app.Dialog
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
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.torx.torxplayer.R
import com.torx.torxplayer.databinding.FragmentVideoPlayerBinding
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.torx.torxplayer.viewmodel.FilesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.w3c.dom.Text
import java.io.File
import kotlin.math.abs

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

    // gesture tracking
    private var initialX = 0f
    private var initialY = 0f
    private var lastX = 0f
    private var lastY = 0f
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

        Log.e("videoList", videoList.toString())
        Log.e("videoPathList", videoPathList.toString())
        Log.e("videoTitleList", videoTitleList.toString())


        seekBar = binding.player.findViewById<SeekBar>(R.id.seekBar)
        tvCurrentTime = binding.player.findViewById<TextView>(R.id.tvCurrentTime)
        tvTotalTime = binding.player.findViewById<TextView>(R.id.tvTotalTime)
        audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
        seekBarVolume = binding.player.findViewById<SeekBar>(R.id.seekBarVolume)
        seekBarBrightness = binding.player.findViewById<SeekBar>(R.id.seekBarBrightness)
        volumeLayout = binding.player.findViewById<LinearLayout>(R.id.volumeLayout)
        brightnessLayout = binding.player.findViewById<LinearLayout>(R.id.brightnessLayout)
        brightnessValue = binding.player.findViewById(R.id.brightnessValue)    // TextView showing "50%"
        soundValue = binding.player.findViewById(R.id.soundValue)

        setFullScreen()
        setLockScreen()
        preparePlayer()
        addBackForward()
        setOrientation()
        initRotationLockButton()
//        setupSwipeControls()

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

                    findNavController().navigateUp()
//                    if (args.isPublic) {
//                    } else {
//                        findNavController().navigate(R.id.action_videoPlayerFragment_to_privateFilesFragment)
//                    }
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
//                val layoutParams = requireActivity().window.attributes
//                layoutParams.screenBrightness = progress / 100f
//                requireActivity().window.attributes = layoutParams
                setBrightness(progress)
                brightnessValue.text = "${progress.toInt()}%"
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

    private fun preparePlayer() {
        binding.player.findViewById<TextView>(R.id.titleText).text = videoTitleList.getOrNull(currentIndex) ?: "Untitled"

        exoPlayer = ExoPlayer.Builder(requireContext()).setSeekBackIncrementMs(INCREMENT_MILLIS)
            .setSeekForwardIncrementMs(INCREMENT_MILLIS)
            .build()
        exoPlayer?.playWhenReady = true
        binding.player.player = exoPlayer

        exoPlayer?.apply {
//            binding.player.scaleX = -1f
            val mediaItem = if (args.isPublic) {
                // Public video → MediaStore / content uri
                MediaItem.fromUri(args.videoUri.toUri())

            } else {
                // Private video → internal storage file path
                val file = File(args.videoPrivate)
                Log.e("exists", file.exists().toString())
                Log.e("length", file.length().toString())
                Log.e("files", file.path)
                MediaItem.fromUri(Uri.fromFile(file))
            }

            setMediaItem(mediaItem)
//            setMediaSource(buildMediaSource(getPath))
            seekTo(playbackPosition)
            playWhenReady = playWhenReady
            prepare()
        }

        exoPlayer?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)
                if (isPlaying) {
                    binding.player.findViewById<ImageView>(R.id.custom_play).setImageResource(R.drawable.baseline_pause_circle_filled_24)
                } else {
                    binding.player.findViewById<ImageView>(R.id.custom_play).setImageResource(R.drawable.baseline_play_arrow_24)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                super.onPlaybackStateChanged(playbackState)
                when (playbackState) {
                    Player.STATE_ENDED -> {
//                        isVideoStopped = true
                        // Play next video automatically
                        val nextIndex = (currentIndex + 1) % videoList.size
                        if (nextIndex != 0 || args.isPublic) { // optional: check if only list should play
                            playVideoAt(nextIndex)
                        } else {
                            isVideoStopped = true
                        }
//                        exoPlayer!!.seekTo(0)
//                        exoPlayer!!.play()
//                        binding.player.findViewById<ImageView>(R.id.exo_play).setImageResource(R.drawable.baseline_pause_circle_filled_24)
                    }

                    Player.STATE_READY -> {
                        isVideoStopped = false
                    }
                    Player.STATE_BUFFERING -> {

                    }
                    Player.STATE_IDLE -> {

                    }
                }

            }
        })

        brightness =
            (requireActivity().window.attributes.screenBrightness * 100).toInt().coerceIn(0,100).toFloat()
        seekBarBrightness.progress = brightness.toInt()

        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        volume = (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / maxVolume).toFloat()
        seekBarVolume.progress = volume.toInt()


        startSeekbarUpdater()

        // SeekBar interaction
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

    //creating mediaSource
    @OptIn(UnstableApi::class)
    private fun buildMediaSource(url: String?): MediaSource {
        // Create a data source factory.
        val dataSourceFactory: DefaultHttpDataSource.Factory = DefaultHttpDataSource.Factory()

        // Create a progressive media source pointing to a stream uri.

        return ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(url!!))
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
//                Toast.makeText(requireContext(), "Rotation Locked", Toast.LENGTH_SHORT).show()

            } else {
                //  Unlock orientation (follow sensors)
                requireActivity().requestedOrientation =
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR

                btnRotateLock.setImageResource(R.drawable.baseline_screen_rotation_24)
//                Toast.makeText(requireContext(), "Rotation Unlocked", Toast.LENGTH_SHORT).show()
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



    @SuppressLint("ClickableViewAccessibility")
    private fun setupSwipeControls() {
        binding.player.setOnTouchListener { v, event ->
            val width = binding.player.width
            val height = binding.player.height

            when (event.action) {

                MotionEvent.ACTION_DOWN -> {
                    initialX = event.x
                    initialY = event.y
                    lastX = event.x
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - lastX   // horizontal movement
                    val absDX = abs(dx)

                    val horizontalThreshold = 10f

                    if (absDX > horizontalThreshold) {

                        // 🟦 TOP HALF → VOLUME
                        if (initialY < height / 2f) {

                            val deltaVolume = (dx / width * 100f)
                            volume = (volume + deltaVolume).coerceIn(0f, 100f)

                            setVolume(volume.toInt())
                            seekBarVolume.progress = volume.toInt()

                            soundValue.text = "${volume.toInt()}%"
                            volumeLayout.visibility = View.VISIBLE

                        } else {
                            // 🟨 BOTTOM HALF → BRIGHTNESS

                            val deltaBrightness = (dx / width * 100f)
                            brightness = (brightness + deltaBrightness).coerceIn(0f, 100f)

                            setBrightness(brightness.toInt())
                            seekBarBrightness.progress = brightness.toInt()

                            brightnessValue.text = "${brightness.toInt()}%"
                            brightnessLayout.visibility = View.VISIBLE
                        }
                    }

                    lastX = event.x
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    brightnessLayout.visibility = View.GONE
                    volumeLayout.visibility = View.GONE

                    // Tap detection
                    if (abs(event.x - initialX) < 10f && abs(event.y - initialY) < 10f) {
                        v.performClick()
                    }
                    true
                }

                else -> false
            }
        }
    }



    private fun seekBy(millis: Long) {
        exoPlayer?.let { player ->
            val newPos = (player.currentPosition + millis)
                .coerceIn(0L, player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE)
            player.seekTo(newPos)
        }
    }


    private fun setBrightness(value: Int) {
        val lp = requireActivity().window.attributes
        lp.screenBrightness = value / 100f // 0..1
        requireActivity().window.attributes = lp
    }

    private fun setVolume(value: Int) {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val newVolume = (value / 100f * maxVolume).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
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

    override fun onStop() {
        super.onStop()

        stopSeekbarUpdater()
        exoPlayer?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSeekbarUpdater()
        exoPlayer?.release()
    }

    override fun onPause() {
        super.onPause()
        stopSeekbarUpdater()
        exoPlayer?.pause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopSeekbarUpdater()
    }

    companion object {
        private var isFullScreen = false
        private var isLock = false
        private var isVideoStopped = false
        private const val INCREMENT_MILLIS = 5000L
    }

}