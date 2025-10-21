package com.torx.torxplayer.fragments

import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.torx.torxplayer.adapters.AudioAdapter
import com.torx.torxplayer.databinding.FragmentAudiosBinding
import com.torx.torxplayer.model.Audio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AudiosFragment : Fragment() {

    private lateinit var binding: FragmentAudiosBinding

    private lateinit var audioAdapter: AudioAdapter
    private lateinit var audioList: List<Audio>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = FragmentAudiosBinding.inflate(inflater, container, false)

        setupRecyclerView()

        checkMediaPermission()
        return binding.root
    }

    companion object {
        @JvmStatic
        fun newInstance() = AudiosFragment()
    }

    private fun setupRecyclerView() {
        binding.audioRV.layoutManager = GridLayoutManager(
            requireContext(), 1,
            LinearLayoutManager.VERTICAL, false
        )
        binding.audioRV.setHasFixedSize(true)
    }
    // fetch audio files from the mobile
    private fun fetchAudioFiles(context: Context): List<Audio> {
        val audioList = mutableListOf<Audio>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA, // Path to the file, use with caution on newer Android versions
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.ALBUM_ID
        )

        // Selection for audio only
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0" // Filter for music files
        val selectionArgs = arrayOf(
            MediaStore.Audio.Media.IS_MUSIC
        )
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        val queryUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val cursor = context.contentResolver.query(
            queryUri,
            projection,
            selection,
            null,
            null
        )

        cursor?.use {
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val albumId = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn)
                val displayName = cursor.getString(displayNameColumn)
                val artist = cursor.getString(artistColumn)
                val album = cursor.getString(albumColumn)
                val duration = cursor.getLong(durationColumn)

                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                audioList.add(
                    Audio(
                        id = id,
                        title = title ?: displayName,
                        artist,
                        album,
                        duration,
                        contentUri,
                        pathColumn,
                        sizeColumn,
                        albumId
                        ))

            }
        }

        return audioList
    }

    // this function will be called when the fragment is created when to check the permissions
    private val storagePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                audioList = fetchAudioFiles(requireContext())
            } else {
                Toast.makeText(requireContext(), "Permission Denied", Toast.LENGTH_SHORT).show()
            }
        }

    private fun checkMediaPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_AUDIO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(
                requireContext(),
                permission
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            loadMediaFiles()
        } else {
            storagePermissionLauncher.launch(permission)
        }
    }

    private fun loadMediaFiles() {
        lifecycleScope.launch {

            //show progress bar while loading
            binding.progressBar.visibility = View.VISIBLE

            // Switch to the IO dispatcher for background work
            val fetchedAudios = withContext(Dispatchers.IO) {
                fetchAudioFiles(requireContext())
            }

            binding.progressBar.visibility = View.GONE
            if (fetchedAudios.isEmpty()) {
                binding.emptyView.visibility = View.VISIBLE
            } else {
                binding.audioRV.visibility = View.VISIBLE
                audioList = fetchedAudios

                Log.d("audioList size", audioList.size.toString())
                // add video list in adapter
                audioAdapter = AudioAdapter(requireContext(), audioList) { video ->
                    // handle video click here
                    Toast.makeText(requireContext(), video.title, Toast.LENGTH_SHORT).show()
//                    val action = VideosFragmentDirections.actionVideosFragmentToVideoPlayerFragment(
//                        video.contentUri.toString(),
//                        video.title
//                    )
//                    findNavController().navigate(action)
                }
                binding.audioRV.adapter = audioAdapter
            }
        }
    }
}