package com.torx.torxplayer.fragments

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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.appcompat.widget.PopupMenu
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.arconn.devicedesk.utils.AppGlobals
import com.torx.torxplayer.OptionsMenuClickListener
import com.torx.torxplayer.R
import com.torx.torxplayer.adapters.AudioAdapter
import com.torx.torxplayer.adapters.VideosAdapter
import com.torx.torxplayer.databinding.FragmentPrivateFilesBinding
import com.torx.torxplayer.model.AudiosModel
import com.torx.torxplayer.model.VideosModel
import com.torx.torxplayer.viewmodel.FilesViewModel

class PrivateFilesFragment : Fragment() {

    private lateinit var binding: FragmentPrivateFilesBinding

    private lateinit var dots: List<View>
//    private lateinit var title: TextView
    private var enteredPin = ""
    private var tempPin = ""
    private var isSettingPin = false
    private var isConfirmingPin = false
    private val appGlobals = AppGlobals()
    private var isVideoPrivate = false

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

        binding.backArrow.setOnClickListener {
            binding.cardsLayout.visibility = View.VISIBLE
            binding.privateFilesRV.visibility = View.GONE
            binding.title.text = "Private Files"

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
                        Toast.makeText(requireContext(), "PIN set successfully!", Toast.LENGTH_SHORT).show()
                        binding.privateLayout.visibility = View.VISIBLE
                        binding.pinTitle.visibility = View.GONE
                        binding.pinDotsLayout.visibility = View.GONE
                        binding.buttonsLayout.visibility = View.GONE
                        binding.privateFragment.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.black))
//                        requireActivity().finish() // or open your private files activity
                    } else {
                        Toast.makeText(requireContext(), "PINs do not match!", Toast.LENGTH_SHORT).show()
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
                    binding.privateFragment.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.black))

//                    requireActivity().finish() // or navigate to your main/private files activity
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

    // private videos
    private lateinit var videoAdapter: VideosAdapter
    private var videoList = mutableListOf<VideosModel>()

    private lateinit var deleteRequestLauncher: ActivityResultLauncher<IntentSenderRequest>
    private var lastDeletedUri: Uri? = null
    private lateinit var viewModel : FilesViewModel

    private fun openPrivateVideos() {
        binding.privateFilesRV.visibility = View.VISIBLE
        binding.cardsLayout.visibility = View.GONE
        binding.title.text = "Private Videos"

        // Observe database once here
        viewModel.allPrivateVideos.observe(viewLifecycleOwner) { videos ->
            videoList.clear()
            videoList.addAll(videos)
//            videoAdapter.notifyDataSetChanged()
            if (videos.isNotEmpty()) {
                binding.emptyView.visibility = View.GONE
                binding.progressBar.visibility = View.GONE

                videoAdapter = VideosAdapter(requireContext(), videoList, object : OptionsMenuClickListener {
                    override fun onOptionsMenuClicked(position: Int, anchorView: View) {
                        performOptionsMenuClick(position, anchorView)
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

    private fun performOptionsMenuClick(position: Int, anchorView: View) {
        val popupMenu = PopupMenu(requireContext(), anchorView)
        popupMenu.inflate(R.menu.options_menu)

        popupMenu.setOnMenuItemClickListener { item ->
            val video = videoList[position]

            when (item.itemId) {
                R.id.playVideo -> {
                    Toast.makeText(requireContext(), video.title, Toast.LENGTH_SHORT).show()
                    val action = VideosFragmentDirections.actionVideosFragmentToVideoPlayerFragment(
                        video.contentUri,
                        video.title
                    )
                    findNavController().navigate(action)
                    true
                }

                R.id.addToPrivate -> {
                    viewModel.updateVideoIsPrivate(video.id, true)
                    videoList.removeAt(position)
                    videoAdapter.notifyItemRemoved(position)
                    Toast.makeText(requireContext(), "Add to private clicked", Toast.LENGTH_SHORT)
                        .show()
                    true
                }

                R.id.deleteVideo -> {
                    deleteFileFromStorage(video)
//                    Toast.makeText(requireContext(), "Deleted video", Toast.LENGTH_SHORT).show()
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
                val rowsDeleted = requireContext().contentResolver.delete(video.contentUri.toUri(), null, null)
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
    private lateinit var audioAdapter: AudioAdapter
    private var audioList = mutableListOf<AudiosModel>()

    private fun openPrivateAudios() {
        binding.title.text = "Private Audios"
        binding.cardsLayout.visibility = View.GONE
        binding.privateFilesRV.visibility = View.VISIBLE

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
                audioAdapter = AudioAdapter(requireContext(), audioList, object : OptionsMenuClickListener {
                    override fun onOptionsMenuClicked(position: Int, anchorView: View) {
                        performAudioOptionsMenuClick(position, anchorView)
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
        popupMenu.setOnMenuItemClickListener(object : androidx.appcompat.widget.PopupMenu.OnMenuItemClickListener{
            override fun onMenuItemClick(item: MenuItem?): Boolean {
                val audio = audioList[position]

                when(item?.itemId){

                    R.id.playVideo -> {
                        val action = AudiosFragmentDirections.actionAudiosFragmentToAudioPlayerFragment(
                            audio.uri, audio.title)
                        findNavController().navigate(action)

                        // here are the logic to delete an item from the list

                    }
                    // in the same way you can implement others
                    R.id.addToPrivate -> {
                        // define
                        viewModel.updateVideoIsPrivate(audio.id, true)
                        audioList.removeAt(position)
                        audioAdapter.notifyItemRemoved(position)
                        Toast.makeText(requireContext() , "Add to private clicked" , Toast.LENGTH_SHORT).show()
                        return true
                    }
                    R.id.deleteVideo -> {
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
                val rowsDeleted = requireContext().contentResolver.delete(audio.uri.toUri(), null, null)
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
                val pendingIntent = MediaStore.createDeleteRequest(requireContext().contentResolver, collection)
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
