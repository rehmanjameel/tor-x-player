package com.torx.torxplayer.fragments

import android.annotation.SuppressLint
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
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
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
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.torx.torxplayer.OptionsMenuClickListener
import com.torx.torxplayer.R
import com.torx.torxplayer.adapters.AudioAdapter
import com.torx.torxplayer.adapters.AudioFolderAdapter
import com.torx.torxplayer.adapters.AudioHistoryAdapter
import com.torx.torxplayer.adapters.VideoFolderAdapter
import com.torx.torxplayer.adapters.VideoHistoryAdapter
import com.torx.torxplayer.adapters.VideosAdapter
import com.torx.torxplayer.databinding.FragmentAudiosBinding
import com.torx.torxplayer.model.AudioFolderModel
import com.torx.torxplayer.model.AudiosModel
import com.torx.torxplayer.model.VideoFolder
import com.torx.torxplayer.model.VideosModel
import com.torx.torxplayer.viewmodel.FilesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AudiosFragment : Fragment() {

    private lateinit var binding: FragmentAudiosBinding

    private lateinit var audioAdapter: AudioAdapter
    private lateinit var playlistAudioAdapter: AudioAdapter
    private lateinit var audioHistoryAdapter: AudioHistoryAdapter

    private lateinit var folderAdapter: AudioFolderAdapter
    private var selectedFolder: AudioFolderModel? = null
    private var AudioHistoryList = mutableListOf<AudiosModel>()
    private var videoPlaylistList = mutableListOf<AudiosModel>()
    private var audioList = mutableListOf<AudiosModel>()
    private lateinit var deleteRequestLauncher: ActivityResultLauncher<IntentSenderRequest>
    private lateinit var viewModel: FilesViewModel

    private var lastDeletedUri: Uri? = null

    private var isAscending = false
    private var isPlaylistView = false
    private var isFolder = false

    private val pendingDeleteUris = mutableListOf<Uri>()


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = FragmentAudiosBinding.inflate(inflater, container, false)

        setupRecyclerView()

        // Register the launcher for delete request
        setupDeleteLauncher()

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
            binding.historyLayout.visibility = View.GONE
            binding.historyEmptyView.visibility = View.GONE
            binding.customTabs.visibility = View.GONE
            binding.folderBackLayout.visibility = View.GONE
            binding.selectedAudiosRV.visibility = View.GONE
            binding.audioFolderRV.visibility = View.GONE

            val imm =
                requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.searchTIET, InputMethodManager.SHOW_IMPLICIT)
        }

        binding.backArrow.setOnClickListener {
            binding.searchLayout.visibility = View.GONE
            binding.topLayout.visibility = View.VISIBLE

            binding.audioRV.visibility = View.VISIBLE
            binding.emptyView.visibility = View.GONE

            binding.historyLayout.visibility = View.VISIBLE
            binding.historyEmptyView.visibility = View.VISIBLE
            binding.customTabs.visibility = View.VISIBLE

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

        checkMediaPermission()

//        setupRecyclerView()
        observeAudios()
        setupAudioAdapter()
        setupHistoryAdapter()
        setupTabClicks()
        observeHistoryAudios()
        setupBottomActions()
        setupFolderAdapter()
        setupBackPress()

        binding.swapIcon.setOnClickListener {
            isAscending = !isAscending
            applySorting()
        }

        binding.mainMenu.setOnClickListener {
            showMainMenu(it)
        }

        binding.tabAudios.setOnClickListener { v ->
            highlightTab(binding.tabAudios)

        }

        binding.tabFolder.setOnClickListener { v ->
            highlightTab(binding.tabFolder)

        }

        binding.tabPlaylist.setOnClickListener { v ->
            highlightTab(binding.tabPlaylist)

        }

        binding.folderBackArrow.setOnClickListener {
            showSection(audio = false, folder = true, selected = false, showBack = false)

            setupFolderBack()
        }

        binding.delHistoryIcon.setOnClickListener {
            if (AudioHistoryList.isNotEmpty()) {
                viewModel.clearAllAudioHistory()
                AudioHistoryList.clear()
                audioHistoryAdapter.notifyDataSetChanged()
                binding.historyRV.visibility = View.GONE
                binding.historyEmptyView.visibility = View.VISIBLE
                binding.delHistoryIcon.visibility = View.GONE
            }
        }
    }

    private fun setupRecyclerView() {
        binding.audioRV.apply {
            layoutManager = GridLayoutManager(requireContext(), 1)
            setHasFixedSize(true)
        }

        binding.audioFolderRV.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            setHasFixedSize(true)
        }

        binding.selectedAudiosRV.apply {
            layoutManager = GridLayoutManager(requireContext(), 1)
            setHasFixedSize(true)
        }
    }

    private fun setupAudioAdapter() {
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
                viewModel.updateAudioIsHistory(audio.id, true)
            }

            override fun onLongItemClick(position: Int) {

                enterSelectionMode(position)

            }

            override fun onSelectionChanged(count: Int) {
                binding.selectAllCheckbox.isChecked = count == audioAdapter.itemCount
            }
        })
        binding.audioRV.adapter = audioAdapter
    }

    private fun setupPlaylistAudioAdapter() {
        playlistAudioAdapter =
            AudioAdapter(requireContext(), videoPlaylistList, object : OptionsMenuClickListener {
                override fun onOptionsMenuClicked(position: Int, anchorView: View) {
                    performOptionsMenuClick(position, anchorView)
                }

                override fun onItemClick(position: Int) {
                    val audios = playlistAudioAdapter.currentList
                    if (position !in audios.indices) return  // prevents crash
                    val video = audios[position]
                    val action = AudiosFragmentDirections.actionAudiosFragmentToAudioPlayerFragment(
                        video.uri,
                        audios.map { it.title }.toTypedArray(),
                        true,
                        audios.map { it.uri }.toTypedArray(),
                        position
                    )
                    findNavController().navigate(action)
                    viewModel.updateVideoIsHistory(video.id, true)
                }

                override fun onLongItemClick(position: Int) {
                    enterSelectionMode(position)
                }

                override fun onSelectionChanged(count: Int) {
                    binding.selectAllCheckbox.isChecked = count == playlistAudioAdapter.itemCount
                }
            })

        binding.selectedAudiosRV.adapter = playlistAudioAdapter
    }

    private fun setupHistoryAdapter() {
        audioHistoryAdapter = AudioHistoryAdapter(
            requireContext(), AudioHistoryList,
            onItemClick = { position ->
                val video = AudioHistoryList[position]
                val action = AudiosFragmentDirections.actionAudiosFragmentToAudioPlayerFragment(
                    video.uri,
                    AudioHistoryList.map { it.title }.toTypedArray(),
                    true,
                    AudioHistoryList.map { it.uri }.toTypedArray(),
                    position
                )
                findNavController().navigate(action)
            })
        binding.historyRV.adapter = audioHistoryAdapter
    }

    private fun applySorting() {
        val sortedList = if (isAscending) {
            audioList.sortedBy { it.id }
        } else {
            audioList.sortedByDescending { it.id }
        }

        audioAdapter.filterList(sortedList.toMutableList())
    }

    /** ------------------ TAB HANDLER ------------------ **/
    private fun setupTabClicks() {
        binding.tabAudios.setOnClickListener {
            highlightTab(binding.tabAudios)
            setupFolderBack()
        }
        binding.tabFolder.setOnClickListener { highlightTab(binding.tabFolder) }
        binding.tabPlaylist.setOnClickListener {
            highlightTab(binding.tabPlaylist)
            setupFolderBack()
        }
    }

    private fun highlightTab(selected: TextView) {

        // reset underlines
        listOf(binding.lineAudios, binding.lineFolder, binding.linePlaylist)
            .forEach { it.visibility = View.GONE }

        // reset colors
        listOf(binding.tabAudios, binding.tabFolder, binding.tabPlaylist)
            .forEach { it.setTextColor(resources.getColor(R.color.white)) }

        when (selected) {
            binding.tabAudios -> {
                binding.lineAudios.visibility = View.VISIBLE
                selected.setTextColor(resources.getColor(R.color.green))
                showSection(audio = true, folder = false, selected = false, showBack = false)
                setAudioRvTop(R.id.customTabs)
                isPlaylistView = false
                isFolder = false
                observeAudios()
                setupBottomActions()
            }

            binding.tabFolder -> {
                binding.lineFolder.visibility = View.VISIBLE
                selected.setTextColor(resources.getColor(R.color.green))
                showSection(audio = false, folder = true, selected = false, showBack = false)
                setAudioRvTop(R.id.customTabs)
                isPlaylistView = false
                isFolder = true
                binding.emptyView.visibility = View.GONE
                setupBottomActions()
            }

            binding.tabPlaylist -> {
                binding.linePlaylist.visibility = View.VISIBLE
                selected.setTextColor(resources.getColor(R.color.green))
                showSection(audio = false, folder = false, selected = true, showBack = false)
                setAudioRvTop(R.id.customTabs)
                setupPlaylistAudioAdapter()
                observePlaylistAudios()
                isPlaylistView = true
                isFolder = false
                setupBottomActions()
            }
        }
    }

    private fun observeAudios() {
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
    }

    /** ---------- FOLDER ADAPTER ---------- **/
    private fun setupFolderAdapter() {
        val folderList = getAudioFolders(requireContext())

        folderAdapter = AudioFolderAdapter(requireContext(), folderList) { folder ->
            selectedFolder = folder
            openFolderVideos(folder)
        }

        if (folderList.isNotEmpty()) {
            binding.audioFolderRV.adapter = folderAdapter
        } else {
            binding.audioFolderRV.visibility = View.GONE
            binding.emptyView.visibility = View.VISIBLE
            binding.emptyView.text = "No folders found"
        }
    }

    private fun openFolderVideos(folder: AudioFolderModel) {

        val audiosInFolder = audioList.filter {
            it.path!!.startsWith(folder.folderPath) && !it.isPrivate
        }.toMutableList()

        audioAdapter.filterList(audiosInFolder)

        binding.folderName.text = folder.folderName

        showSection(audio = true, folder = false, selected = false, showBack = true)

        setAudioRvTop(R.id.folderBackLayout)

        binding.audioRV.scrollToPosition(0)
    }

    /** ---------- BACK BUTTON ---------- **/
    private fun setupFolderBack() {
        selectedFolder = null

        // Restore full list
        audioAdapter.filterList(audioList.toMutableList())
        setAudioRvTop(R.id.customTabs) // move RV back under original layout
    }

    /** ---------- BACK PRESS ---------- **/
    private fun setupBackPress() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (audioAdapter.isSelectionMode) {
                exitSelectionMode()
                refreshVisibleList()
            } else {
                findNavController().popBackStack()
            }
        }
    }

    private fun setupBottomActions() {
        binding.selectAllCheckbox.setOnClickListener { toggleSelectAll() }
        binding.actionDelete.setOnClickListener { deleteSelectedAudios() }
        binding.actionPlay.setOnClickListener { playSelectedAudios() }
        binding.actionShare.setOnClickListener { shareSelectedAudios() }

        if (isPlaylistView) {
            binding.actionDelete.visibility = View.GONE
            binding.actionPrivate.visibility = View.GONE
            binding.actionShare.visibility = View.GONE
            binding.addPlaylistIcon.setImageResource(R.drawable.baseline_playlist_remove_24)
            binding.playListTxt.text = "Remove List"
        } else{
            binding.actionDelete.visibility = View.VISIBLE
            binding.actionPrivate.visibility = View.VISIBLE
            binding.actionShare.visibility = View.VISIBLE
            binding.addPlaylistIcon.setImageResource(R.drawable.baseline_playlist_play_24)
            binding.playListTxt.text = "Add To"
        }

        binding.actionPrivate.setOnClickListener {


            Log.e("not playlist", isPlaylistView.toString())
            val selectedVideos = audioAdapter.selectedItems.map { audioAdapter.currentList[it] }

            for (video in selectedVideos) {
                addFilesToPrivate(video.id, true, audioAdapter)

                // Update local cache
                audioList.find { it.id == video.id }?.isPrivate = true
            }

            exitSelectionMode()

            // Refresh folder view if inside folder
            if (selectedFolder != null) {
                openFolderVideos(selectedFolder!!)
            }


        }

    }

    private fun observeHistoryAudios() {
        viewModel.allHistoryAudios.observe(viewLifecycleOwner) { historyAudios ->
            AudioHistoryList.clear()
            AudioHistoryList.addAll(historyAudios)
            audioHistoryAdapter.notifyDataSetChanged()
            binding.historyRV.visibility =
                if (historyAudios.isNotEmpty()) View.VISIBLE else View.GONE
            binding.historyEmptyView.visibility =
                if (historyAudios.isEmpty()) View.VISIBLE else View.GONE
            binding.delHistoryIcon.visibility =
                if (historyAudios.isNotEmpty()) View.VISIBLE else View.GONE

            binding.progressBar.visibility = View.GONE
        }
    }

    private fun observePlaylistAudios() {
        viewModel.allPlaylistAudios.observe(viewLifecycleOwner) { playlistVideos ->
            videoPlaylistList.clear()
            videoPlaylistList.addAll(playlistVideos)
            playlistAudioAdapter.notifyDataSetChanged()
            binding.selectedAudiosRV.visibility =
                if (playlistVideos.isNotEmpty()) View.VISIBLE else View.GONE
            binding.emptyView.visibility = if (playlistVideos.isEmpty()) View.VISIBLE else View.GONE

            binding.progressBar.visibility = View.GONE
        }
    }

    private fun enterSelectionMode(position: Int) {
        if (isPlaylistView) {
            playlistAudioAdapter.isSelectionMode = true
            playlistAudioAdapter.selectedItems.add(position)

            playlistAudioAdapter.notifyDataSetChanged()

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
        if (isPlaylistView) {
            playlistAudioAdapter.isSelectionMode = false
            playlistAudioAdapter.selectedItems.clear()
            playlistAudioAdapter.notifyDataSetChanged()
        } else {
            audioAdapter.isSelectionMode = false
            audioAdapter.selectedItems.clear()
            audioAdapter.notifyDataSetChanged()
        }
        binding.selectAllCheckbox.visibility = View.GONE
        binding.bottomActionBar.visibility = View.GONE
        requireActivity().findViewById<BottomNavigationView>(R.id.bottomNavView)?.visibility =
            View.VISIBLE

    }

    private fun refreshVisibleList() {
        if (selectedFolder != null) {
            openFolderVideos(selectedFolder!!)
        } else {
            audioAdapter.filterList(audioList.toMutableList())
        }
    }

    private fun toggleSelectAll() {
        if (isPlaylistView) {
            if (playlistAudioAdapter.selectedItems.size == playlistAudioAdapter.itemCount) {
                exitSelectionMode()

            } else {

                playlistAudioAdapter.selectedItems.clear()
                playlistAudioAdapter.selectedItems.addAll(playlistAudioAdapter.currentList.indices)
                playlistAudioAdapter.notifyDataSetChanged()
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

    private fun deleteSelectedAudios() {
        val selectedVideos = audioAdapter.selectedItems
            .map { audioAdapter.currentList[it] }

        if (selectedVideos.isEmpty()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

            pendingDeleteUris.clear()
            pendingDeleteUris.addAll(
                selectedVideos.map { it.uri.toUri() }
            )

            val pendingIntent = MediaStore.createDeleteRequest(
                requireContext().contentResolver,
                pendingDeleteUris
            )

            val request = IntentSenderRequest.Builder(
                pendingIntent.intentSender
            ).build()

            deleteRequestLauncher.launch(request)

        } else {
            // Pre-Android 11
            selectedVideos.forEach { video ->
                deletePreAndroid11(video)
            }
            refreshVisibleList()
        }

        exitSelectionMode()

    }

    private fun setupDeleteLauncher() {
        deleteRequestLauncher =
            registerForActivityResult(
                ActivityResultContracts.StartIntentSenderForResult()
            ) { result ->

                if (result.resultCode == Activity.RESULT_OK) {

                    pendingDeleteUris.forEach { uri ->
                        viewModel.deleteAudiosByUri(uri.toString())
                        audioList.removeAll { it.uri == uri.toString() }
                    }

                    pendingDeleteUris.clear()
                    refreshVisibleList()

                    Toast.makeText(
                        requireContext(),
                        "Audios deleted successfully",
                        Toast.LENGTH_SHORT
                    ).show()

                } else {
                    Toast.makeText(
                        requireContext(),
                        "Delete cancelled",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun deletePreAndroid11(audio: AudiosModel) {
        val rowsDeleted = requireContext().contentResolver.delete(
            audio.uri.toUri(),
            null,
            null
        )

        if (rowsDeleted > 0) {
            viewModel.deleteVideosByUri(audio.uri)
            audioList.remove(audio)
        }
    }

    private fun playSelectedAudios() {
        if (!isPlaylistView) {
            if (audioAdapter.selectedItems.isNotEmpty()) {
                val positions = audioAdapter.selectedItems.toList()  // ensure fixed order
                val source = audioAdapter.currentList

//                val firstAudio = source[positions.first()]
//                val uris = positions.map { source[it].uri }.toTypedArray()
//                val titles = positions.map { source[it].title }.toTypedArray()
//
//                val action = AudiosFragmentDirections.actionAudiosFragmentToAudioPlayerFragment(
//                    firstAudio.uri, titles, true, uris, 0
//                )
//                findNavController().navigate(action)

                // FIXED – no more crash
                for (pos in positions) {
                    val audioId = source[pos].id
                    Log.e("video ids pos", pos.toString())
                    Log.e("video ids", audioId.toString())
                    viewModel.updateAudioIsPlaylist(audioId, true)
                }

                exitSelectionMode()
            }
        } else {
            if (playlistAudioAdapter.selectedItems.isNotEmpty()) {
                val positions = playlistAudioAdapter.selectedItems.toList()  // ensure fixed order
                val source = playlistAudioAdapter.currentList

                for (pos in positions) {
                    val audioId = source[pos].id
                    Log.e("audio ids pos", pos.toString())
                    Log.e("audio ids", audioId.toString())
                    viewModel.updateAudioIsPlaylist(audioId, false)
                }

                highlightTab(binding.tabAudios)
                exitSelectionMode()
            }

        }
    }


    private fun shareSelectedAudios() {
        if (!isPlaylistView) {
            val uris = audioAdapter.selectedItems.map {
                audioAdapter.currentList[it].uri.toUri()
            }
            val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "audio/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
            startActivity(Intent.createChooser(shareIntent, "Share audios"))
            exitSelectionMode()
        }
    }

    /** Show/hide layout groups together */
    private fun showSection(audio: Boolean, folder: Boolean, selected: Boolean, showBack: Boolean) {
        binding.audioRV.visibility = if (audio) View.VISIBLE else View.GONE
        binding.audioFolderRV.visibility = if (folder) View.VISIBLE else View.GONE
        binding.selectedAudiosRV.visibility = if (selected) View.VISIBLE else View.GONE
        binding.folderBackLayout.visibility = if (showBack) View.VISIBLE else View.GONE
    }

    /** ---------- CONSTRAINT CLEAN VERSION ---------- **/
    private fun setAudioRvTop(anchorId: Int) {
        val audioRv = binding.audioRV
        val params = audioRv.layoutParams as RelativeLayout.LayoutParams

        // Remove any TOP/BOTTOM rules first
        params.removeRule(RelativeLayout.ALIGN_PARENT_TOP)
        params.removeRule(RelativeLayout.BELOW)

        // Apply new rule: videoRV is below the given anchor view
        params.addRule(RelativeLayout.BELOW, anchorId)

        audioRv.layoutParams = params
    }

    private fun addFilesToPrivate(audioId: Long, isPrivate: Boolean, audiosAdapter: AudioAdapter) {
        viewModel.updateAudioIsPrivate(audioId, isPrivate)
        Log.e("is private1", isPrivate.toString())

        audiosAdapter.notifyDataSetChanged()
        Toast.makeText(requireContext(), "Added to private", Toast.LENGTH_SHORT).show()
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

    fun getAudioFolders(context: Context): List<AudioFolderModel> {
        val folderMap = HashMap<String, MutableList<String>>() // folderPath -> list of videos

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DATA
        )

        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val sortOrder = MediaStore.Audio.Media.DATE_ADDED + " DESC"

        val cursor = context.contentResolver.query(
            uri, projection, null, null, sortOrder
        )

        cursor?.use {
            val dataCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (it.moveToNext()) {
                val fullPath = it.getString(dataCol)

                // Extract folder path
                val file = File(fullPath)
                val folder = file.parent ?: continue

                if (!folderMap.containsKey(folder)) {
                    folderMap[folder] = mutableListOf()
                }
                folderMap[folder]?.add(fullPath)
            }
        }

        // Convert folderMap to ArrayList of VideoFolder
        val folderList = ArrayList<AudioFolderModel>()
        for ((folderPath, audios) in folderMap) {
            folderList.add(
                AudioFolderModel(
                    folderName = File(folderPath).name,
                    folderPath = folderPath,
                    audioCount = audios.size
                )
            )
        }

        return folderList
    }
    @SuppressLint("Range")
//    fun getAudioFoldersFromList(audioList: List<AudiosModel>): List<AudioFolderModel> {
//
//        val folderMap = HashMap<String, MutableList<AudiosModel>>()
//
//        for (audio in audioList) {
//            val file = File(audio.path)
//            val parent = file.parentFile ?: continue
//            val folderName = parent.name
//
//            folderMap.getOrPut(folderName) { mutableListOf() }.add(audio)
//        }
//
//        return folderMap.map { (folderName, list) ->
//            AudioFolderModel(folderName, list)
//        }
//    }


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
