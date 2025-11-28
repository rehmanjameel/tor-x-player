package com.torx.torxplayer.fragments

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.torx.torxplayer.OptionsMenuClickListener
import com.torx.torxplayer.R
import com.torx.torxplayer.adapters.AudioAdapter
import com.torx.torxplayer.databinding.FragmentAudiosBinding
import com.torx.torxplayer.model.AudiosModel
import com.torx.torxplayer.viewmodel.FilesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AudiosFragment : Fragment() {

    private lateinit var binding: FragmentAudiosBinding

    private lateinit var audioAdapter: AudioAdapter
    private var audioList = mutableListOf<AudiosModel>()
    private lateinit var deleteRequestLauncher: ActivityResultLauncher<IntentSenderRequest>
    private lateinit var viewModel: FilesViewModel

    private var lastDeletedUri: Uri? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = FragmentAudiosBinding.inflate(inflater, container, false)

        binding.audioRV.layoutManager = GridLayoutManager(
            requireContext(), 1,
            LinearLayoutManager.VERTICAL, false
        )
        binding.audioRV.setHasFixedSize(true)

        // Register the launcher for delete request
        deleteRequestLauncher =
            registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    lastDeletedUri.let { deletedUri ->
                        viewModel.deleteAudiosByUri(deletedUri.toString()) // also remove from DB
                        audioList.removeAll { it.uri == deletedUri.toString() }
                        audioAdapter.notifyDataSetChanged()
                    }
                    Toast.makeText(
                        requireContext(),
                        "File deleted successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                    // You can refresh your list or remove the item here if not already done
                } else {
                    Toast.makeText(requireContext(), "File not deleted", Toast.LENGTH_SHORT).show()
                }
            }

        binding.donation.setOnClickListener {
            val url =
                "https://donate.wfp.org/1243/donation/regular?campaign=4574&_ga=2.233279257.602488721.1762603852-1327722293.1762603852&_gac=1.187330266.1762603852.CjwKCAiA8bvIBhBJEiwAu5ayrDyDEAP5YnuLh0lhI8kMRsprikeoVM9kdNvpFIRbTpmzfzSD6wKZ2RoCojcQAvD_BwE"
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(
                    requireActivity(),
                    "No browser found to open the link",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        // search audios
        binding.searchTIET.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {

            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                val query = p0.toString().trim()
                if (query.isNotEmpty()) {
                    searchAudios(query)
                } else {
                    binding.audioRV.visibility = View.GONE
                    binding.emptyView.visibility = View.GONE
                }
            }

        })

        binding.searchIcon.setOnClickListener {
            binding.searchLayout.visibility = View.VISIBLE
            binding.topLayout.visibility = View.GONE

            binding.searchTIET.requestFocus()
            binding.searchTIET.text.clear()

            binding.audioRV.visibility = View.GONE
            binding.emptyView.visibility = View.GONE

            val imm =
                requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.searchTIET, InputMethodManager.SHOW_IMPLICIT)
        }

        binding.backArrow.setOnClickListener {
            binding.searchLayout.visibility = View.GONE
            binding.topLayout.visibility = View.VISIBLE

            binding.audioRV.visibility = View.VISIBLE
            binding.emptyView.visibility = View.GONE
            audioAdapter.filterList(audioList) // restore original data

            binding.searchTIET.clearFocus()
            val imm =
                requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(binding.searchTIET.windowToken, 0) // Hide the keyboard
        }

        return binding.root
    }

    private fun searchAudios(query: String) {

        val filteredAudios = audioList.filter { audio ->
            audio.title.contains(query, ignoreCase = true)
        }.toMutableList()
        if (filteredAudios.isNotEmpty()) {
            audioAdapter.filterList(filteredAudios)
            binding.audioRV.visibility = View.VISIBLE
            binding.emptyView.visibility = View.GONE
        } else {
            binding.audioRV.visibility = View.GONE
            binding.emptyView.visibility = View.VISIBLE
            binding.emptyView.text = "No videos found"
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val app = requireActivity().application
        viewModel = ViewModelProvider(
            requireActivity(),
            ViewModelProvider.AndroidViewModelFactory.getInstance(app)
        )[FilesViewModel::class.java]
        Log.e("audio list", "$audioList")
        audioAdapter = AudioAdapter(requireContext(), audioList, object : OptionsMenuClickListener {
            override fun onOptionsMenuClicked(position: Int, anchorView: View) {
                performOptionsMenuClick(position, anchorView)
            }

            override fun onItemClick(position: Int) {
                val audio = audioList[position]
                val action = AudiosFragmentDirections.actionAudiosFragmentToAudioPlayerFragment(
                    audio.uri,
                    audioList.map { it.title }.toTypedArray(),
                    true,
                    audioList.map { it.uri }.toTypedArray(),
                    position
                )
                findNavController().navigate(action)
            }

            override fun onLongItemClick(position: Int) {

            }
        })
        binding.audioRV.adapter = audioAdapter

        // Observe database once here
        viewModel.allPublicAudios.observe(viewLifecycleOwner) { audios ->
            Log.e("audios list", "$audios")
            audioList.clear()
            audioList.addAll(audios)

            audioAdapter.notifyDataSetChanged()
            if (audios.isNotEmpty()) {
                binding.audioRV.visibility = View.VISIBLE
                binding.emptyView.visibility = View.GONE
            } else {
                binding.audioRV.visibility = View.GONE
                binding.emptyView.visibility = View.VISIBLE
            }
            binding.progressBar.visibility = View.GONE
        }
        checkMediaPermission()

        binding.mainMenu.setOnClickListener {
            showMainMenu(it)
        }

    }

    private fun showMainMenu(view: View) {
        val popupMenu = PopupMenu(requireContext(), view)
        popupMenu.inflate(R.menu.main_menu)

        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.startVersion -> {
                    Toast.makeText(requireContext(), "App version: ${getAppVersionName(requireContext())}", Toast.LENGTH_SHORT).show()
                    true
                }

//                R.id.rateUs -> {
//                    Toast.makeText(requireContext(), "Rate us coming soon", Toast.LENGTH_SHORT)
//                        .show()
//                    true
//                }
//
//                R.id.other -> {
//                    Toast.makeText(requireContext(), "Other coming soon", Toast.LENGTH_SHORT).show()
//                    true
//                }

                else -> false
            }
        }

        popupMenu.show()
    }

    fun getAppVersionName(context: Context): String? {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            return packageInfo.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        return null
    }

    // fetch audio files from the mobile
    private fun fetchAudioFiles(context: Context): MutableList<AudiosModel> {
        val audioList = mutableListOf<AudiosModel>()

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
            sortOrder
        )

        cursor?.use {
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val displayNameColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
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
                    AudiosModel(
                        id = id,
                        title = title ?: displayName,
                        artist = artist,
                        album = album,
                        duration = duration,
                        uri = contentUri.toString(),
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
                loadMediaFilesIntoDB()
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
            // Check DB content
            viewLifecycleOwner.lifecycleScope.launch {
                val currentAudios = withContext(Dispatchers.IO) {
                    viewModel.getAudioCount() // implement in ViewModel
                }

                if (currentAudios == 0) {
                    loadMediaFilesIntoDB()
                } else {
                    binding.progressBar.visibility = View.GONE
                }
            }
        } else {
            storagePermissionLauncher.launch(permission)
        }
    }

    private fun loadMediaFilesIntoDB() {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE

            val fetchedAudios = withContext(Dispatchers.IO) {
                fetchAudioFiles(requireContext())
            }

            if (fetchedAudios.isNotEmpty()) {
                viewModel.insertAllAudios(fetchedAudios)

                binding.progressBar.visibility = View.GONE
                binding.emptyView.visibility = View.GONE
                binding.audioRV.visibility = View.VISIBLE
            } else {
                binding.progressBar.visibility = View.GONE
                binding.emptyView.visibility = View.VISIBLE
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
        popupMenu.setOnMenuItemClickListener(object : PopupMenu.OnMenuItemClickListener {
            override fun onMenuItemClick(item: MenuItem?): Boolean {
                val audio = audioList[position]

                when (item?.itemId) {

//                    R.id.play -> {
//                        val action = AudiosFragmentDirections.actionAudiosFragmentToAudioPlayerFragment(
//                            audio.uri, audio.title, true)
//                        findNavController().navigate(action)
//
//                        // here are the logic to delete an item from the list
//
//                    }
                    // in the same way you can implement others
                    R.id.addToPrivate -> {
                        // define
                        viewModel.updateAudioIsPrivate(audio.id, true)
                        audioList.removeAt(position)
                        audioAdapter.notifyItemRemoved(position)
                        Toast.makeText(
                            requireContext(),
                            "Add to private clicked",
                            Toast.LENGTH_SHORT
                        ).show()
                        return true
                    }

                    R.id.delete -> {
                        // define

                        deleteFileFromStorage(audio)

                        return true
                    }
                }
                return false
            }
        })
        popupMenu.show()
    }

    private fun deleteFileFromStorage(audio: AudiosModel) {
        try {
            // For Android Q (API 29) and below — direct delete
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                val rowsDeleted =
                    requireContext().contentResolver.delete(audio.uri.toUri(), null, null)
                if (rowsDeleted > 0) {

                    viewModel.deleteAudiosByUri(audio.uri) // also remove from DB
                    audioList.remove(audio)
                    audioAdapter.notifyDataSetChanged()
                    Toast.makeText(context, "File deleted successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to delete file", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Android 11+ (Scoped Storage) — user confirmation required
                val collection = arrayListOf(audio.uri.toUri())
                val pendingIntent =
                    MediaStore.createDeleteRequest(requireContext().contentResolver, collection)
                val request = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                lastDeletedUri = audio.uri.toUri()

                deleteRequestLauncher.launch(request)
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
            Toast.makeText(context, "Permission denied to delete file", Toast.LENGTH_SHORT).show()
        }
    }

}
