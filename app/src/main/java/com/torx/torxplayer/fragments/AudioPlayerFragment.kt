package com.torx.torxplayer.fragments

import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.fragment.navArgs
import com.torx.torxplayer.R
import com.torx.torxplayer.databinding.FragmentAudioPlayerBinding
import androidx.core.net.toUri

class AudioPlayerFragment : Fragment() {

    private lateinit var binding: FragmentAudioPlayerBinding

    private val args: AudioPlayerFragmentArgs by navArgs()
    private var player: ExoPlayer? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = FragmentAudioPlayerBinding.inflate(inflater, container, false)

        val audioTitle = args.audioTitle

        val audioUri = args.audioUri
        initializePlayer(audioUri)

        return binding.root
    }


    private fun initializePlayer(uri: String) {
        player = ExoPlayer.Builder(requireContext()).build()
        binding.playerView.player = player

        val mediaItem = MediaItem.fromUri(uri.toUri())
        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.playWhenReady = true
    }

    override fun onStop() {
        super.onStop()
        player?.release()
        player = null
    }
}