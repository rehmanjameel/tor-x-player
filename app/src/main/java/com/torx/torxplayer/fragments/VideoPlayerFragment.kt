package com.torx.torxplayer.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.Bundle
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
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.w3c.dom.Text
import kotlin.math.abs

class VideoPlayerFragment : Fragment() {

    private val args: VideoPlayerFragmentArgs by navArgs()
    private var playbackPosition = 0L
    private var exoPlayer: ExoPlayer? = null
    var mOrientationListener: OrientationEventListener? = null
    private var seekJob: Job? = null

    private lateinit var seekBar: SeekBar
    private lateinit var seekBarBrightness: SeekBar
    private lateinit var seekBarVolume: SeekBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var binding: FragmentVideoPlayerBinding


    private var initialY = 0f
    private var initialX = 0f
    private var brightness: Float = 50F
    private var volume: Float = 50f
    private lateinit var audioManager: AudioManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = FragmentVideoPlayerBinding.inflate(inflater, container, false)

        seekBar = binding.player.findViewById<SeekBar>(R.id.seekBar)
        tvCurrentTime = binding.player.findViewById<TextView>(R.id.tvCurrentTime)
        tvTotalTime = binding.player.findViewById<TextView>(R.id.tvTotalTime)
        audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
        seekBarVolume = binding.player.findViewById<SeekBar>(R.id.seekBarVolume)
        seekBarBrightness = binding.player.findViewById<SeekBar>(R.id.seekBarBrightness)

        setFullScreen()
        setLockScreen()
        preparePlayer()
        addBackForward()
        setOrientation()
        setupSwipeControls()

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

                    if (args.isPublic) {
                        findNavController().navigateUp()
                    } else {
                        findNavController().navigate(R.id.action_videoPlayerFragment_to_privateFilesFragment)
                    }
                    stopSeekbarUpdater()
                }
            }
        )

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.player.findViewById<TextView>(R.id.setPlayBackSpeed).setOnClickListener {
            speedPlayBack(it)
        }
    }

    private fun preparePlayer() {
        binding.player.findViewById<TextView>(R.id.titleText).text = args.videoTitle
        exoPlayer = ExoPlayer.Builder(requireContext()).setSeekBackIncrementMs(INCREMENT_MILLIS)
            .setSeekForwardIncrementMs(INCREMENT_MILLIS)
            .build()
        exoPlayer?.playWhenReady = true
        binding.player.player = exoPlayer

        exoPlayer?.apply {
//            binding.player.scaleX = -1f
            setMediaItem(MediaItem.fromUri(args.videoUri.toUri()))
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
                        isVideoStopped = true
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
                Log.e(
                    "DEBUG_TAG",
                    "Orientation changed to $orientation"
                )

                when (orientation) {
                    in 1..89 -> {
                        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

                    }
                    in 180 .. 360 -> {
                        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

                    }
                    in 90 .. 180 -> {
                        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                    }
                }
            }
        }
    }

    private fun lockScreen(lock: Boolean) {
        if (lock) {
            binding.player.findViewById<LinearLayout>(R.id.linearLayoutControlUp).visibility = View.INVISIBLE
            binding.player.findViewById<LinearLayout>(R.id.linearLayoutControlBottom).visibility = View.INVISIBLE
        } else {
            binding.player.findViewById<LinearLayout>(R.id.linearLayoutControlUp).visibility = View.VISIBLE
            binding.player.findViewById<LinearLayout>(R.id.linearLayoutControlBottom).visibility = View.VISIBLE
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


    private fun setupSwipeControls() {
        binding.player.setOnTouchListener { v, event ->
            val width = binding.player.width
            val height = binding.player.height

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = event.y
                    initialX = event.x
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaY = initialY - event.y

                    if (initialX < width / 2) {
                        // Left → Brightness
                        val deltaBrightness = (deltaY / height * 100)  // float
                        brightness = (brightness + deltaBrightness).coerceIn(0f, 100f)
                        setBrightness(brightness.toInt())
                        seekBarBrightness.progress = brightness.toInt()
                        seekBarBrightness.visibility = View.VISIBLE
                    } else {
                        // Right → Volume
                        val deltaVolume = (deltaY / height * 100)
                        volume = (volume + deltaVolume).coerceIn(0f, 100f)
                        setVolume(volume.toInt())
                        seekBarVolume.progress = volume.toInt()
                        seekBarVolume.visibility = View.VISIBLE
                    }

                    initialY = event.y
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    seekBarBrightness.visibility = View.GONE
                    seekBarVolume.visibility = View.GONE

                    // Tap detection
                    if (abs(event.y - initialY) < 10 && abs(event.x - initialX) < 10) {
                        v.performClick()
                    }
                    true
                }

                else -> false
            }
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