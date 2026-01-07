package com.torx.torxplayer.fragments

import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.appcompat.widget.PopupMenu
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.arconn.devicedesk.utils.AppGlobals
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.torx.torxplayer.OptionsMenuClickListener
import com.torx.torxplayer.R
import com.torx.torxplayer.adapters.AudioAdapter
import com.torx.torxplayer.adapters.VideosAdapter
import com.torx.torxplayer.databinding.FragmentPrivateFilesBinding
import com.torx.torxplayer.model.AudiosModel
import com.torx.torxplayer.model.VideosModel
import com.torx.torxplayer.viewmodel.FilesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

class PrivateFilesFragment : Fragment() {

    private lateinit var binding: FragmentPrivateFilesBinding

    private lateinit var dots: List<View>
    private var enteredPin = ""
    private var tempPin = ""
    private var isSettingPin = false
    private var isConfirmingPin = false
    private val appGlobals = AppGlobals()
    private var isVideoPrivate = false

    // private videos
    private lateinit var videoAdapter: VideosAdapter
    private var videoList = mutableListOf<VideosModel>()

    private lateinit var deleteRequestLauncher: ActivityResultLauncher<IntentSenderRequest>
    private var lastDeletedUri: Uri? = null
    private lateinit var viewModel: FilesViewModel

    // private audios
    private lateinit var audioAdapter: AudioAdapter
    private var audioList = mutableListOf<AudiosModel>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = FragmentPrivateFilesBinding.inflate(inflater, container, false)

        dots = listOf(binding.dot1, binding.dot2, binding.dot3, binding.dot4)

        val savedPin = appGlobals.getValueString("user_pin")
        isSettingPin = savedPin == null

        if (isSettingPin) {
            binding.pinTitle.text = "Create PIN"
        } else {
            binding.pinTitle.text = "Enter PIN"
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupKeypad(view)

        val app = requireActivity().application
        viewModel = ViewModelProvider(
            requireActivity(),
            ViewModelProvider.AndroidViewModelFactory.getInstance(app)
        )[FilesViewModel::class.java]

        binding.privateVideoCard.setOnClickListener {
            isVideoPrivate = true
            openPrivateVideos()
        }

        binding.privateAudioCard.setOnClickListener {
            isVideoPrivate = false
            openPrivateAudios()
        }

        setupBottomActions()

        binding.backArrow.setOnClickListener {
            setupBackPress()
            binding.cardsLayout.visibility = View.VISIBLE
            binding.privateFilesRV.visibility = View.GONE
            binding.title.text = getString(R.string.app_name)
            binding.backArrow.visibility = View.GONE
            binding.emptyView.visibility = View.GONE
        }
    }

    private fun setupKeypad(view: View) {
        val buttons = mapOf(
            R.id.btn0 to "0", R.id.btn1 to "1", R.id.btn2 to "2",
            R.id.btn3 to "3", R.id.btn4 to "4", R.id.btn5 to "5",
            R.id.btn6 to "6", R.id.btn7 to "7", R.id.btn8 to "8",
            R.id.btn9 to "9"
        )

        // Assign numbers to TextViews inside each CardView
        for ((id, number) in buttons) {
            val card = view.findViewById<CardView>(id)
            val text = card.findViewById<TextView>(R.id.tvKey)
            text.text = number
            card.setOnClickListener { onDigitPressed(number) }
        }

        val btnDel = view.findViewById<CardView>(R.id.btnDel)
        val tvDel = btnDel.findViewById<TextView>(R.id.tvKey)
        tvDel.text = "⌫"
        btnDel.setOnClickListener { onDeletePressed() }
    }

    private fun onDigitPressed(digit: String) {
        if (enteredPin.length < 4) {
            enteredPin += digit
            updateDots()
        }

        if (enteredPin.length == 4) {
            if (isSettingPin) {
                if (!isConfirmingPin) {
                    // Step 1: Enter new PIN
                    tempPin = enteredPin
                    enteredPin = ""
                    isConfirmingPin = true
                    binding.pinTitle.text = "Confirm PIN"
                    updateDots()
                } else {
                    // Step 2: Confirm PIN
                    if (enteredPin == tempPin) {
                        appGlobals.saveString("user_pin", enteredPin)
                        Toast.makeText(
                            requireContext(),
                            "PIN set successfully!",
                            Toast.LENGTH_SHORT
                        ).show()
                        binding.privateLayout.visibility = View.VISIBLE
                        binding.pinTitle.visibility = View.GONE
                        binding.pinDotsLayout.visibility = View.GONE
                        binding.buttonsLayout.visibility = View.GONE
                        binding.privateFragment.setBackgroundColor(
                            ContextCompat.getColor(
                                requireContext(),
                                R.color.black
                            )
                        )
                    } else {
                        Toast.makeText(requireContext(), "PINs do not match!", Toast.LENGTH_SHORT)
                            .show()
                        enteredPin = ""
                        tempPin = ""
                        isConfirmingPin = false
                        binding.pinTitle.text = "Create PIN"
                        updateDots()
                    }
                }
            } else {
                // Unlock mode
                val savedPin = appGlobals.getValueString("user_pin")
                if (enteredPin == savedPin) {
                    Toast.makeText(requireContext(), "Unlocked!", Toast.LENGTH_SHORT).show()
                    binding.privateLayout.visibility = View.VISIBLE
                    binding.pinTitle.visibility = View.GONE
                    binding.pinDotsLayout.visibility = View.GONE
                    binding.buttonsLayout.visibility = View.GONE
                    binding.privateFragment.setBackgroundColor(
                        ContextCompat.getColor(
                            requireContext(),
                            R.color.black
                        )
                    )

                } else {
                    Toast.makeText(requireContext(), "Wrong PIN!", Toast.LENGTH_SHORT).show()
                    enteredPin = ""
                    updateDots()
                }
            }
        }
    }

    private fun onDeletePressed() {
        if (enteredPin.isNotEmpty()) {
            enteredPin = enteredPin.dropLast(1)
            updateDots()
        }
    }

    private fun updateDots() {
        for (i in dots.indices) {
            if (i < enteredPin.length)
                dots[i].setBackgroundResource(R.drawable.pin_dot_active)
            else
                dots[i].setBackgroundResource(R.drawable.pin_dot_inactive)
        }
    }

    // videos
    private fun openPrivateVideos() {
        binding.privateFilesRV.visibility = View.VISIBLE
        binding.cardsLayout.visibility = View.GONE
        binding.title.text = "Private Videos"
        binding.backArrow.visibility = View.VISIBLE

        // Observe database once here
        viewModel.allPrivateVideos.observe(viewLifecycleOwner) { videos ->
            videoList.clear()
            videoList.addAll(videos)
            if (videos.isNotEmpty()) {
                binding.emptyView.visibility = View.GONE
                binding.progressBar.visibility = View.GONE

                videoAdapter =
                    VideosAdapter(requireContext(), videoList, object : OptionsMenuClickListener {
                        override fun onOptionsMenuClicked(position: Int, anchorView: View) {
                            performOptionsMenuClick(position, anchorView)
                        }

                        override fun onItemClick(position: Int) {
                            val video = videoList[position]
                            val action =
                                PrivateFilesFragmentDirections.actionPrivateFilesFragmentToVideoPlayerFragment(
                                    video.contentUri,
                                    video.privatePath?: "",
                                    videoList.map { it.privatePath }.toTypedArray(),
                                    videoList.map { it.title }.toTypedArray(),
                                    false,
                                    videoList.map { it.contentUri }.toTypedArray(),
                                    position
                                )
                            findNavController().navigate(action)
                        }

                        override fun onLongItemClick(position: Int) {

                            enterSelectionMode(position)
                        }

                        override fun onSelectionChanged(count: Int) {
                            binding.selectAllCheckbox.isChecked = count == videoAdapter.itemCount
                        }
                    })
                binding.privateFilesRV.apply {
                    layoutManager = GridLayoutManager(requireContext(), 1)
                    adapter = videoAdapter
                    setHasFixedSize(true)
                }
                binding.privateFilesRV.adapter = videoAdapter

            } else {
                binding.privateFilesRV.visibility = View.GONE
                binding.emptyView.visibility = View.VISIBLE
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun enterSelectionMode(position: Int) {

        if (isVideoPrivate) {

            videoAdapter.isSelectionMode = true
            videoAdapter.selectedItems.add(position)
            videoAdapter.notifyDataSetChanged()
        } else {
            audioAdapter.isSelectionMode = true
            audioAdapter.selectedItems.add(position)
            audioAdapter.notifyDataSetChanged()
        }
        binding.bottomActionBar.visibility = View.VISIBLE
        binding.selectAllCheckbox.visibility = View.VISIBLE
        requireActivity().findViewById<BottomNavigationView>(R.id.bottomNavView)?.visibility =
            View.GONE

    }

    private fun exitSelectionMode() {
        if (isVideoPrivate) {
            videoAdapter.isSelectionMode = false
            videoAdapter.selectedItems.clear()
            videoAdapter.notifyDataSetChanged()

            binding.selectAllCheckbox.visibility = View.GONE
            binding.bottomActionBar.visibility = View.GONE
            requireActivity().findViewById<BottomNavigationView>(R.id.bottomNavView)?.visibility =
                View.VISIBLE
        } else {
            audioAdapter.isSelectionMode = false
            audioAdapter.selectedItems.clear()
            audioAdapter.notifyDataSetChanged()
            binding.selectAllCheckbox.visibility = View.GONE
            binding.bottomActionBar.visibility = View.GONE
            requireActivity().findViewById<BottomNavigationView>(R.id.bottomNavView)?.visibility =
                View.VISIBLE
        }

    }

    private fun toggleSelectAll() {

        if (isVideoPrivate) {

            if (videoAdapter.selectedItems.size == videoAdapter.itemCount) {
                exitSelectionMode()
            } else {
                videoAdapter.selectedItems.clear()
                videoAdapter.selectedItems.addAll(videoAdapter.currentList.indices)
                videoAdapter.notifyDataSetChanged()
            }
        } else {
            if (audioAdapter.selectedItems.size == audioAdapter.itemCount) {
                exitSelectionMode()
            } else {
                audioAdapter.selectedItems.clear()
                audioAdapter.selectedItems.addAll(audioAdapter.currentList.indices)

                audioAdapter.notifyDataSetChanged()
            }
        }

    }

    private fun setupBottomActions() {
        binding.selectAllCheckbox.setOnClickListener { toggleSelectAll() }

        binding.actionDelete.visibility = View.GONE
        binding.actionPrivate.visibility = View.VISIBLE
        binding.actionPlay.visibility = View.GONE
        binding.actionShare.visibility = View.GONE
        binding.privateIcon.setImageResource(R.drawable.outline_visibility_24)
        binding.privateTxt.text = "Unlock"

        binding.actionPrivate.setOnClickListener {
            Log.e("is private", "selectedVideos.toString()")

            if (isVideoPrivate) {

                val selectedVideos = videoAdapter.selectedItems.map { videoAdapter.currentList[it] }

                for (video in selectedVideos) {
                    lifecycleScope.launch {
                        restoreVideo(video)
                    }

                    // Update local cache
                    videoList.find { it.id == video.id }?.isPrivate = false
                }
            } else {
                val selectedAudios = audioAdapter.selectedItems.map { audioAdapter.currentList[it] }

                for (audio in selectedAudios) {
                    lifecycleScope.launch {
                        restoreAudios(audio)
                    }

                    // Update local cache
                    audioList.find { it.id == audio.id }?.isPrivate = false
                }
            }

            exitSelectionMode()

        }

    }

    private fun addFilesToPublic(videoId: Long, isPrivate: Boolean, privatePath: String, videosAdapter: VideosAdapter) {
//        viewModel.updateVideoIsPrivate(videoId, isPrivate, privatePath)
        Log.e("is private1", isPrivate.toString())

        videosAdapter.notifyDataSetChanged()
        Toast.makeText(requireContext(), "Removed from private", Toast.LENGTH_SHORT).show()
    }

    suspend fun restoreVideo(video: VideosModel) =
        withContext(Dispatchers.IO) {

            val privateFile = File(video.privatePath ?: return@withContext)
            if (!privateFile.exists()) return@withContext

            val resolver = requireContext().contentResolver

            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, privateFile.name)
                put(MediaStore.Video.Media.MIME_TYPE, video.mimeType)
                put(
                    MediaStore.Video.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_MOVIES + "/" + video.folderName
                )
            }

            val uri = resolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                values
            ) ?: return@withContext

            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(privateFile).copyTo(out)
            }

            privateFile.delete()

            // THIS is the missing line
            viewModel.deleteVideosById(video.id)

        }

    suspend fun restoreAudios(audio: AudiosModel) =
        withContext(Dispatchers.IO) {

            val privateFile = File(audio.privatePath ?: return@withContext)
            if (!privateFile.exists()) return@withContext

            val resolver = requireContext().contentResolver

            val safeAlbum = sanitizeFolderName(audio.album)
            val safeName = ensureExtension(privateFile, audio.mimeType)
            Log.e("exten", safeName)

            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, safeName)
                put(MediaStore.Audio.Media.MIME_TYPE, audio.mimeType)
                put(
                    MediaStore.Audio.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_MUSIC + "/" + safeAlbum
                )
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }

            val uri = resolver.insert(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                values
            ) ?: return@withContext

            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(privateFile).copyTo(out)
                Log.e("output", out.toString())
            }

            // finalize
            resolver.update(
                uri,
                ContentValues().apply {
                    put(MediaStore.Audio.Media.IS_PENDING, 0)
                },
                null,
                null
            )

            Log.e("AUDIO_DEBUG", "private exists=${privateFile.exists()}")
            Log.e("AUDIO_DEBUG", "album=$safeAlbum")
            Log.e("AUDIO_DEBUG", "name=$safeName")
            Log.e("AUDIO_DEBUG", "mime=${audio.mimeType}")
            Log.e("AUDIO_DEBUG", "uri=$uri")

            // delete private copy ONLY after finalize
            privateFile.delete()

            // remove vault DB row
            viewModel.deleteAudiosById(audio.id)
        }

    fun sanitizeFolderName(input: String?): String {
        return input
            ?.replace(Regex("[^a-zA-Z0-9 _-]"), "")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "MyApp"
    }

    fun ensureExtension(file: File, mime: String): String {
        return if (file.name.contains(".")) {
            file.name
        } else {
            val ext = MimeTypeMap.getSingleton()
                .getExtensionFromMimeType(mime) ?: "mp3"
            "${file.name}.$ext"
        }
    }


    private fun addAudioFilesToPublic(
        audioId: Long,
        isPrivate: Boolean,
        audioAdapter: AudioAdapter
    ) {
        viewModel.updateAudioIsPrivate(audioId, isPrivate, null)
        Log.e("is private1", isPrivate.toString())

        audioAdapter.notifyDataSetChanged()
        Toast.makeText(requireContext(), "Removed from private", Toast.LENGTH_SHORT).show()
    }

    private fun setupBackPress() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (videoAdapter.isSelectionMode) {
                exitSelectionMode()
                return@addCallback
            }
        }
    }

    private fun performOptionsMenuClick(position: Int, anchorView: View) {
        val popupMenu = PopupMenu(requireContext(), anchorView)
        popupMenu.inflate(R.menu.options_menu)

        popupMenu.setOnMenuItemClickListener { item ->
            val video = videoList[position]

            when (item.itemId) {
//                R.id.play -> {
//                    Toast.makeText(requireContext(), video.title, Toast.LENGTH_SHORT).show()
//                    val action = PrivateFilesFragmentDirections.actionPrivateFilesFragmentToVideoPlayerFragment(
//                        video.contentUri,
//                        video.title,
//                        false
//                    )
//                    findNavController().navigate(action)
//                    true
//                }

                R.id.renameFile -> {
//                    viewModel.updateVideoIsPrivate(video.id, true)
                    videoList.removeAt(position)
                    videoAdapter.notifyItemRemoved(position)
                    Toast.makeText(requireContext(), "Add to private clicked", Toast.LENGTH_SHORT)
                        .show()
                    true
                }

                R.id.delete -> {
                    deleteFileFromStorage(video)
                    true
                }

                else -> false
            }
        }

        popupMenu.show()
    }

    private fun deleteFileFromStorage(video: VideosModel) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                val rowsDeleted =
                    requireContext().contentResolver.delete(video.contentUri.toUri(), null, null)
                if (rowsDeleted > 0) {
                    viewModel.deleteVideosByUri(video.contentUri) // also remove from DB
                    videoList.remove(video)
                    videoAdapter.notifyDataSetChanged()
                    Toast.makeText(context, "File deleted successfully", Toast.LENGTH_SHORT).show()
                }
            } else {
                val collection = arrayListOf(video.contentUri.toUri())
                val pendingIntent =
                    MediaStore.createDeleteRequest(requireContext().contentResolver, collection)
                val request = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                lastDeletedUri = video.contentUri.toUri()
                deleteRequestLauncher.launch(request)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // search videos
    private fun searchVideos(query: String) {

        val filteredVideos = videoList.filter { video ->
            video.title.contains(query, ignoreCase = true)
        }.toMutableList()
        videoAdapter.filterList(filteredVideos)
    }

    // audio

    private fun openPrivateAudios() {
        binding.title.text = "Private Audios"
        binding.cardsLayout.visibility = View.GONE
        binding.privateFilesRV.visibility = View.VISIBLE
        binding.backArrow.visibility = View.VISIBLE

        // Observe database once here
        viewModel.allPrivateAudios.observe(viewLifecycleOwner) { audios ->
            Log.e("audios list", "$audios")
            audioList.clear()
            audioList.addAll(audios)

//            audioAdapter.notifyDataSetChanged()
            if (audios.isNotEmpty()) {
                binding.privateFilesRV.visibility = View.VISIBLE
                binding.emptyView.visibility = View.GONE

                Log.e("audio list", "$audioList")
                audioAdapter =
                    AudioAdapter(requireContext(), audioList, object : OptionsMenuClickListener {
                        override fun onOptionsMenuClicked(position: Int, anchorView: View) {
                            performAudioOptionsMenuClick(position, anchorView)
                        }

                        override fun onItemClick(position: Int) {
                            val audio = audioList[position]
                            val action =
                                PrivateFilesFragmentDirections.actionPrivateFilesFragmentToAudioPlayerFragment(
                                    audio.uri,
                                    audio.privatePath?: "",
                                    audioList.map { it.privatePath }.toTypedArray(),
                                    audioList.map { it.title }.toTypedArray(),
                                    false,
                                    audioList.map { it.uri }.toTypedArray(),
                                    position
                                )
                            findNavController().navigate(action)
                        }

                        override fun onLongItemClick(position: Int) {

                            enterSelectionMode(position)
                        }

                        override fun onSelectionChanged(count: Int) {
                            binding.selectAllCheckbox.isChecked = count == audioAdapter.itemCount
                        }
                    })
                binding.privateFilesRV.apply {
                    layoutManager = GridLayoutManager(requireContext(), 1)
                    adapter = audioAdapter
                    setHasFixedSize(true)
                }
                binding.privateFilesRV.adapter = audioAdapter
            } else {
                binding.privateFilesRV.visibility = View.GONE
                binding.emptyView.visibility = View.VISIBLE
            }
            binding.progressBar.visibility = View.GONE
        }
    }

    // this method will handle the onclick options click
    private fun performAudioOptionsMenuClick(position: Int, anchorView: View) {
        // create object of PopupMenu and pass context and view where we want
        // to show the popup menu
        val popupMenu = PopupMenu(requireContext(), anchorView)
        // add the menu
        popupMenu.inflate(R.menu.options_menu)
        // implement on menu item click Listener
        popupMenu.setOnMenuItemClickListener(object :
            androidx.appcompat.widget.PopupMenu.OnMenuItemClickListener {
            override fun onMenuItemClick(item: MenuItem?): Boolean {
                val audio = audioList[position]

                when (item?.itemId) {

//                    R.id.play -> {
//                        val action = PrivateFilesFragmentDirections.actionPrivateFilesFragmentToAudioPlayerFragment(
//                            audio.uri, audio.title, false)
//                        findNavController().navigate(action)
//
//                        // here are the logic to delete an item from the list
//
//                    }
                    // in the same way you can implement others
                    R.id.renameFile -> {
                        // define
//                        viewModel.updateVideoIsPrivate(audio.id, true)
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

                        deleteAudioFileFromStorage(audio)

                        return true
                    }
                }
                return false
            }
        })
        popupMenu.show()
    }

    private fun deleteAudioFileFromStorage(audio: AudiosModel) {
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
