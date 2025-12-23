package com.torx.torxplayer.fragments

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
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
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.torx.torxplayer.R
import com.torx.torxplayer.databinding.FragmentAudioPlayerBinding
import com.torx.torxplayer.services.AudioPlayerService
import kotlinx.coroutines.*

@UnstableApi
class AudioPlayerFragment : Fragment() {

    private lateinit var binding: FragmentAudioPlayerBinding
    private val args: AudioPlayerFragmentArgs by navArgs()

    private var audioList: List<Uri> = emptyList()
    private var audioTitleList: List<String> = emptyList()
    private var audioPrivatePathList: List<String> = emptyList()
    private var currentIndex = 0

    private var seekJob: Job? = null
    private var currentSpeedIndex = 2
    private val speedOptions = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    private var audioService: AudioPlayerService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AudioPlayerService.LocalBinder
            audioService = binder.getService()
            isBound = true

            // Start playback ONLY ONCE
            audioService?.playPlaylist(
                audioList.map { it.toString() },
                audioTitleList,
                audioPrivatePathList,
                currentIndex
            )

            observePlayer()
            startSeekbarUpdater()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            audioService = null
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentAudioPlayerBinding.inflate(inflater, container, false)

        audioTitleList = args.audioTitle.toList()
        audioList = args.audioUriList.map { it.toUri() }
        audioPrivatePathList = args.audioPrivatePathList.toList()
        currentIndex = args.position

        setupUiListeners()
        setupBackPress()
        startAudioService()

        updateTitle()
        loadAlbumArt(audioList[currentIndex])

        return binding.root
    }

    private fun startAudioService() {
        val intent = Intent(requireContext(), AudioPlayerService::class.java)
        requireContext().startForegroundService(intent)

        requireContext().bindService(
            intent,
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    // 🔁 Observe service player for UI + animation
    private fun observePlayer() {
        audioService?.player?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                binding.btnPlayPause.setImageResource(
                    if (isPlaying)
                        R.drawable.baseline_pause_circle_filled_24
                    else
                        R.drawable.baseline_play_arrow_24
                )

                if (isPlaying) binding.heartbeatAnim.playAnimation()
                else binding.heartbeatAnim.pauseAnimation()
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) nextTrack()
            }
        })
    }

    private fun setupUiListeners() {

        binding.btnPlayPause.setOnClickListener {
            audioService?.player?.let { p ->
                if (p.isPlaying) p.pause() else p.play()
            }
        }

        binding.btnNext.setOnClickListener { nextTrack() }
        binding.btnPrev.setOnClickListener { previousTrack() }

        binding.tvSpeed.apply {
            visibility = View.VISIBLE
            text = "${speedOptions[currentSpeedIndex]}x"
            setOnClickListener {
                currentSpeedIndex = (currentSpeedIndex + 1) % speedOptions.size
                val speed = speedOptions[currentSpeedIndex]
                audioService?.player?.playbackParameters =
                    PlaybackParameters(speed)
                text = "${speed}x"
            }
        }

        binding.seekBar.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                if (fromUser) {
                    audioService?.player?.seekTo(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        binding.closePlayer.setOnClickListener { navigateBack() }
    }

    private fun startSeekbarUpdater() {
        seekJob?.cancel()
        seekJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                audioService?.player?.let { p ->
                    val pos = p.currentPosition
                    val dur = p.duration.takeIf { it > 0 } ?: 0
                    binding.seekBar.max = dur.toInt()
                    binding.seekBar.progress = pos.toInt()
                    binding.tvCurrentTime.text = formatTime(pos)
                    binding.tvTotalTime.text = formatTime(dur)
                }
                delay(300)
            }
        }
    }

    private fun nextTrack() {
        if (currentIndex < audioList.size - 1) {
            currentIndex++
            audioService?.playPlaylist(
                audioList.map { it.toString() },
                audioTitleList,
                audioPrivatePathList,
                currentIndex
            )
            updateTitle()
            loadAlbumArt(audioList[currentIndex])
        }
    }

    private fun previousTrack() {
        if (currentIndex > 0) {
            currentIndex--
            audioService?.playPlaylist(
                audioList.map { it.toString() },
                audioTitleList,
                audioPrivatePathList,
                currentIndex
            )
            updateTitle()
            loadAlbumArt(audioList[currentIndex])
        }
    }

    private fun updateTitle() {
        binding.tvTitle.text =
            audioTitleList.getOrNull(currentIndex) ?: "Untitled"
    }

    private fun loadAlbumArt(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {

            val bitmap = try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(requireContext(), uri)

                val art = retriever.embeddedPicture
                retriever.release()

                art?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            } catch (e: Exception) {
                null
            }

            withContext(Dispatchers.Main) {
                if (bitmap != null) {
                    binding.albumArt.setImageBitmap(bitmap)
                } else {
                    binding.albumArt.setImageResource(R.drawable.audio_thumbnail)
                }
            }
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
        if (args.isPublic) findNavController().navigateUp()
        else findNavController().navigate(
            R.id.action_audioPlayerFragment_to_privateFilesFragment
        )
    }

    private fun stopSeekbarUpdater() {
        seekJob?.cancel()
        seekJob = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopSeekbarUpdater()
        if (isBound) {
            requireContext().unbindService(serviceConnection)
            isBound = false
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        return String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60)
    }
}

