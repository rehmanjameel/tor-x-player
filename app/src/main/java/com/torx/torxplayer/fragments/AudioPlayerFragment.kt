package com.torx.torxplayer.fragments

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.torx.torxplayer.R
import com.torx.torxplayer.databinding.FragmentAudioPlayerBinding
import kotlinx.coroutines.*

class AudioPlayerFragment : Fragment() {

    private lateinit var binding: FragmentAudioPlayerBinding
    private val args: AudioPlayerFragmentArgs by navArgs()

    private var player: ExoPlayer? = null
    private var audioList: List<Uri> = emptyList()
    private var audioTitleList: List<String> = emptyList()
    private var currentIndex = 0
    private var seekJob: Job? = null
    private var currentSpeedIndex = 2 // default = 1x
    private val speedOptions = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentAudioPlayerBinding.inflate(inflater, container, false)
        audioTitleList = args.audioTitle.toList()
        audioList = args.audioUriList.map { it.toUri() }
        currentIndex = args.position

        initializePlayer()
        setupUiListeners()
        setupBackPress()
        return binding.root
    }

    private fun initializePlayer() {
        player = ExoPlayer.Builder(requireContext()).build()

        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) nextTrack()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                lifecycleScope.launch(Dispatchers.Main) {
                    binding.btnPlayPause.setImageResource(
                        if (isPlaying) R.drawable.baseline_pause_circle_filled_24
                        else R.drawable.baseline_play_arrow_24
                    )
                    if (isPlaying) binding.heartbeatAnim.playAnimation()
                    else binding.heartbeatAnim.pauseAnimation()
                }
            }
        })

        lifecycleScope.launch(Dispatchers.Main) {
            playAudio(currentIndex)
            startSeekbarUpdater()
        }
    }

    private suspend fun loadAlbumArt(uri: Uri?) = withContext(Dispatchers.IO) {
        uri ?: return@withContext null
        try {
            requireContext().contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun playAudio(index: Int) = withContext(Dispatchers.Main) {
        val uri = audioList[index]
        val audioTitle = audioTitleList.getOrNull(index) ?: "Untitled"

        // Load album art in background
        val bitmap = withContext(Dispatchers.IO) { loadAlbumArt(uri) }
        bitmap?.let {
            binding.albumArt.setImageBitmap(it)
        } ?: run {
            binding.albumArt.setImageResource(R.drawable.audio_thumbnail)
        }

        val item = MediaItem.fromUri(uri)
        player?.apply {
            stop()
            clearMediaItems()
            setMediaItem(item)
            prepare()
            playWhenReady = true
            setPlaybackSpeed(speedOptions[currentSpeedIndex])
        }

        binding.tvTitle.text = audioTitle
    }

    private fun setupUiListeners() {
        binding.btnPlayPause.setOnClickListener {
            player?.let { p ->
                if (p.isPlaying) p.pause() else p.play()
            }
        }

        binding.btnNext.setOnClickListener { nextTrack() }
        binding.btnPrev.setOnClickListener { previousTrack() }

        // Speed control TextView
        binding.tvSpeed.apply {
            visibility = View.VISIBLE
            text = "${speedOptions[currentSpeedIndex]}x"
            setOnClickListener {
                currentSpeedIndex = (currentSpeedIndex + 1) % speedOptions.size
                val newSpeed = speedOptions[currentSpeedIndex]
                player?.setPlaybackSpeed(newSpeed)
                text = "${newSpeed}x"
                Toast.makeText(requireContext(), "Speed: ${newSpeed}x", Toast.LENGTH_SHORT).show()
            }
        }

        // SeekBar interaction
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) player?.seekTo(progress.toLong())
                binding.tvCurrentTime.text = formatTime(progress.toLong())
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        binding.closePlayer.setOnClickListener { navigateBack() }
    }

    private fun startSeekbarUpdater() {
        seekJob?.cancel()
        seekJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main.immediate) {
            while (isActive) {
                player?.let { p ->
                    val dur = p.duration.takeIf { it > 0 } ?: 0
                    val pos = p.currentPosition.takeIf { it >= 0 } ?: 0
                    binding.seekBar.max = dur.toInt()
                    binding.seekBar.progress = pos.toInt()
                    binding.tvCurrentTime.text = formatTime(pos)
                    binding.tvTotalTime.text = formatTime(dur)
                }
                delay(300)
            }
        }
    }

    private fun stopSeekbarUpdater() {
        seekJob?.cancel()
        seekJob = null
    }

    private fun nextTrack() {
        if (currentIndex < audioList.size - 1) {
            currentIndex++
            lifecycleScope.launch { playAudio(currentIndex) }
        }
    }

    private fun previousTrack() {
        if (currentIndex > 0) {
            currentIndex--
            lifecycleScope.launch { playAudio(currentIndex) }
        }
    }

    private fun setupBackPress() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = navigateBack()
            }
        )
    }

    private fun navigateBack() {
        stopSeekbarUpdater()
        player?.stop()
        player?.release()
        player = null
        if (args.isPublic) findNavController().navigateUp()
        else findNavController().navigate(R.id.action_audioPlayerFragment_to_privateFilesFragment)
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
        stopSeekbarUpdater()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopSeekbarUpdater()
        player?.release()
        player = null
    }

    private fun ExoPlayer.setPlaybackSpeed(speed: Float) {
        this.playbackParameters = PlaybackParameters(speed)
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
}
