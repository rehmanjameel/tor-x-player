package com.torx.torxplayer.fragments

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.torx.torxplayer.OptionsMenuClickListener
import com.torx.torxplayer.R
import com.torx.torxplayer.adapters.AudioAdapter
import com.torx.torxplayer.databinding.FragmentAudiosBinding
import com.torx.torxplayer.model.Audio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AudiosFragment : Fragment() {

    private lateinit var binding: FragmentAudiosBinding

    private lateinit var audioAdapter: AudioAdapter
    private lateinit var audioList: MutableList<Audio>
    private lateinit var deleteRequestLauncher: ActivityResultLauncher<IntentSenderRequest>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = FragmentAudiosBinding.inflate(inflater, container, false)

        setupRecyclerView()

        checkMediaPermission()

        // Register the launcher for delete request
        deleteRequestLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                audioAdapter.notifyDataSetChanged()
                Toast.makeText(requireContext(), "File deleted successfully", Toast.LENGTH_SHORT).show()
                // You can refresh your list or remove the item here if not already done
            } else {
                Toast.makeText(requireContext(), "File not deleted", Toast.LENGTH_SHORT).show()
            }
        }

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
    private fun fetchAudioFiles(context: Context): MutableList<Audio> {
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

                val path = cursor.getString(pathColumn)
                val size = cursor.getLong(sizeColumn)
                val albumIdValue = cursor.getLong(albumId)

                audioList.add(
                    Audio(
                        id = id,
                        title = title ?: displayName,
                        artist = artist,
                        album = album,
                        duration = duration,
                        uri = contentUri,
                        path = path,
                        size = size,
                        albumId = albumIdValue
                    )
                )


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
                audioAdapter = AudioAdapter(requireContext(), audioList,
                    object : OptionsMenuClickListener {
                    override fun onOptionsMenuClicked(position: Int, anchorView: View) {
                        performOptionsMenuClick(position, anchorView)
                    }
                })
                binding.audioRV.adapter = audioAdapter
            }
        }
    }

    // this method will handle the onclick options click
    private fun performOptionsMenuClick(position: Int, anchorView: View) {
        // create object of PopupMenu and pass context and view where we want
        // to show the popup menu
        val popupMenu = PopupMenu(requireContext(), anchorView)
        // add the menu
        popupMenu.inflate(R.menu.options_menu)
        // implement on menu item click Listener
        popupMenu.setOnMenuItemClickListener(object : PopupMenu.OnMenuItemClickListener{
            override fun onMenuItemClick(item: MenuItem?): Boolean {
                val tempLang = audioList[position]

                when(item?.itemId){

                    R.id.playVideo -> {
                        val action = AudiosFragmentDirections.actionAudiosFragmentToAudioPlayerFragment(
                            tempLang.uri.toString(), tempLang.title)
                        findNavController().navigate(action)

                        // here are the logic to delete an item from the list

                    }
                    // in the same way you can implement others
                    R.id.addToPrivate -> {
                        // define
                        Toast.makeText(requireContext() , "Add to private clicked" , Toast.LENGTH_SHORT).show()
                        return true
                    }
                    R.id.deleteVideo -> {
                        // define

                        deleteFileFromStorage(tempLang)

                        return true
                    }
                }
                return false
            }
        })
        popupMenu.show()
    }

    private fun deleteFileFromStorage(tempLang: Audio) {
        try {
            // For Android Q (API 29) and below — direct delete
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                val rowsDeleted = requireContext().contentResolver.delete(tempLang.uri, null, null)
                if (rowsDeleted > 0) {
                    Toast.makeText(context, "File deleted successfully", Toast.LENGTH_SHORT).show()
                    audioList.remove(tempLang)
                    audioAdapter.notifyDataSetChanged()
                } else {
                    Toast.makeText(context, "Failed to delete file", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Android 11+ (Scoped Storage) — user confirmation required
                val collection = arrayListOf(tempLang.uri)
                val pendingIntent = MediaStore.createDeleteRequest(requireContext().contentResolver, collection)
                val request = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                deleteRequestLauncher.launch(request)
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
            Toast.makeText(context, "Permission denied to delete file", Toast.LENGTH_SHORT).show()
        }
    }

}
