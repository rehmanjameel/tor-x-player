package com.torx.torxplayer.fragments

import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.fragment.navArgs
import com.torx.torxplayer.R
import com.torx.torxplayer.databinding.FragmentAudioPlayerBinding
import androidx.core.net.toUri
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.navigation.fragment.findNavController

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
        binding.titleText.text = audioTitle

        binding.backArrow.setOnClickListener {
            if (args.isPublic) {
                findNavController().navigateUp()
            } else {
                findNavController().navigate(R.id.action_audioPlayerFragment_to_privateFilesFragment)
            }
        }

        // go back on system back press
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {

                    if (args.isPublic) {
                        findNavController().navigateUp()
                    } else {
                        findNavController().navigate(R.id.action_audioPlayerFragment_to_privateFilesFragment)
                    }
                }
            }
        )
        return binding.root
    }


    private fun initializePlayer(uri: String) {
        player = ExoPlayer.Builder(requireContext()).build()
        binding.playerView.player = player

        val mediaItem = MediaItem.fromUri(uri.toUri())
        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.playWhenReady = true

//        player?.addListener(object : Player.Listener{
//            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
//                super.onMediaMetadataChanged(mediaMetadata)
//                val artworkData = mediaMetadata.artworkData
//                if (artworkData != null) {
//                    // Artwork exists — show it
//                    val bitmap = BitmapFactory.decodeByteArray(artworkData, 0, artworkData.size)
//                    binding.audioBackground.setImageBitmap(bitmap)
//                } else {
//                    // No artwork — use fallback image
//                    binding.audioBackground.setImageResource(R.drawable.audio_thumbnail)
//                }
//            }
//        })
    }

    override fun onStop() {
        super.onStop()
        player?.release()
        player = null
    }
}