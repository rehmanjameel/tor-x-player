package com.torx.torxplayer.fragments

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.PopupMenu
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.torx.torxplayer.OptionsMenuClickListener
import com.torx.torxplayer.R
import com.torx.torxplayer.adapters.VideoFolderAdapter
import com.torx.torxplayer.adapters.VideoHistoryAdapter
import com.torx.torxplayer.adapters.VideosAdapter
import com.torx.torxplayer.databinding.FragmentVideosBinding
import com.torx.torxplayer.model.VideoFolder
import com.torx.torxplayer.model.VideosModel
import com.torx.torxplayer.viewmodel.FilesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class VideosFragment : Fragment() {

    private lateinit var binding: FragmentVideosBinding
    private lateinit var videoAdapter: VideosAdapter
    private lateinit var playlistVideoAdapter: VideosAdapter
    private lateinit var videoHistoryAdapter: VideoHistoryAdapter
    private lateinit var videoFolderAdapter: VideoFolderAdapter
    private var videoList = mutableListOf<VideosModel>()
    private var videoHistoryList = mutableListOf<VideosModel>()
    private var videoPlaylistList = mutableListOf<VideosModel>()

    private lateinit var deleteRequestLauncher: ActivityResultLauncher<IntentSenderRequest>
    private var lastDeletedUri: Uri? = null
    private lateinit var viewModel: FilesViewModel
    private var isAscending = false
    private var isPlaylistView = false
    private var isFolder = false
    private val pendingDeleteUris = mutableListOf<Uri>()
    private lateinit var privateDeleteRequestLauncher: ActivityResultLauncher<IntentSenderRequest>
    private val pendingPrivateDeletes = mutableListOf<VideosModel>()

    private var isSelectionMode = false

    enum class ActiveTab {
        VIDEOS,
        PLAYLIST,
        FOLDER
    }

    private var activeTab = ActiveTab.VIDEOS


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentVideosBinding.inflate(inflater, container, false)

        setupRecyclerView()
        setupSearch()
        setupDonationClick()
        setupDeleteLauncher()
//        setupPrivateDeleteLauncher()

        //open the download screen
        binding.downloadIcon.setOnClickListener {
            val action = VideosFragmentDirections.actionVideosFragmentToDownloadFragment(true)
            findNavController().navigate(action)
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val app = requireActivity().application
        viewModel = ViewModelProvider(
            requireActivity(),
            ViewModelProvider.AndroidViewModelFactory.getInstance(app)
        )[FilesViewModel::class.java]

        setupVideoAdapter()
        setupHistoryAdapter()
        setupFolderAdapter()
        setupRecyclerView()
        setupTabClicks()
        setupBackPress()
        observeVideos()
//        checkMediaPermission()
        requestMediaPermissions()
        setupMainMenu()
        setupBottomActions()
        observeHistoryVideos()

        binding.folderBackArrow.setOnClickListener {
            showSection(video = false, folder = true, selected = false, showBack = false)

            setupFolderBack()

        }

        binding.swapIcon.setOnClickListener {
            isAscending = !isAscending
            applySorting()
        }

        binding.delHistoryIcon.setOnClickListener {
            if (videoHistoryList.isNotEmpty()) {
                viewModel.clearAllHistory()
                videoHistoryList.clear()
                videoHistoryAdapter.notifyDataSetChanged()
                binding.historyRV.visibility = View.GONE
                binding.historyEmptyView.visibility = View.VISIBLE
                binding.delHistoryIcon.visibility = View.GONE
            }
        }

    }

    private fun setupHistoryAdapter() {
        videoHistoryAdapter = VideoHistoryAdapter(
            requireContext(), videoHistoryList,
            onItemClick = { position ->
                val video = videoHistoryList[position]
                val action = VideosFragmentDirections.actionVideosFragmentToVideoPlayerFragment(
                    video.contentUri,
                    video.privatePath?: "",
                    videoList.map { it.privatePath }.toTypedArray(),
                    videoHistoryList.map { it.title }.toTypedArray(),
                    true,
                    videoHistoryList.map { it.contentUri }.toTypedArray(),
                    position
                )
                findNavController().navigate(action)
            })
        binding.historyRV.adapter = videoHistoryAdapter
    }

    private fun setupVideoAdapter() {
        videoAdapter =
            VideosAdapter(requireContext(), videoList, object : OptionsMenuClickListener {
                override fun onOptionsMenuClicked(position: Int, anchorView: View) {
                    performOptionsMenuClick(position, anchorView)
                }

                override fun onItemClick(position: Int) {
                    val videos = videoAdapter.currentList
                    if (position !in videos.indices) return  // prevents crash
                    val video = videos[position]
                    val action = VideosFragmentDirections.actionVideosFragmentToVideoPlayerFragment(
                        video.contentUri,
                        video.privatePath?: "",
                        videoList.map { it.privatePath }.toTypedArray(),
                        videos.map { it.title }.toTypedArray(),
                        true,
                        videos.map { it.contentUri }.toTypedArray(),
                        position
                    )
                    findNavController().navigate(action)
                    viewModel.updateVideoIsHistory(video.contentUri, true)
                }

                override fun onLongItemClick(position: Int) {
                    enterSelectionMode(position)
                }

                override fun onSelectionChanged(count: Int) {
                    binding.selectAllCheckbox.isChecked = count == videoAdapter.itemCount
                }
            })

        binding.videoRV.adapter = videoAdapter
    }

    private fun applySorting() {
        val sortedList = if (isAscending) {
            videoList.sortedBy { it.dateAdded }
        } else {
            videoList.sortedByDescending { it.dateAdded }
        }

        videoAdapter.filterList(sortedList.toMutableList())
    }

    private fun setupPlaylistVideoAdapter() {
        playlistVideoAdapter =
            VideosAdapter(requireContext(), videoPlaylistList, object : OptionsMenuClickListener {
                override fun onOptionsMenuClicked(position: Int, anchorView: View) {
                    performOptionsMenuClick(position, anchorView)
                }

                override fun onItemClick(position: Int) {
                    val videos = playlistVideoAdapter.currentList
                    if (position !in videos.indices) return  // prevents crash
                    val video = videos[position]
                    val action = VideosFragmentDirections.actionVideosFragmentToVideoPlayerFragment(
                        video.contentUri,
                        video.privatePath?: "",
                        videoList.map { it.privatePath }.toTypedArray(),
                        videos.map { it.title }.toTypedArray(),
                        true,
                        videos.map { it.contentUri }.toTypedArray(),
                        position
                    )
                    findNavController().navigate(action)
                    viewModel.updateVideoIsHistory(video.contentUri, true)
                }

                override fun onLongItemClick(position: Int) {
                    enterSelectionMode(position)
                }

                override fun onSelectionChanged(count: Int) {
                    binding.selectAllCheckbox.isChecked = count == playlistVideoAdapter.itemCount
                }
            })

        binding.selectedVideosRV.adapter = playlistVideoAdapter
    }

    private fun setupRecyclerView() {
        binding.videoRV.apply {
            layoutManager = GridLayoutManager(requireContext(), 1)
            setHasFixedSize(true)
        }

        binding.videoFolderRV.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            setHasFixedSize(true)
        }

        binding.selectedVideosRV.apply {
            layoutManager = GridLayoutManager(requireContext(), 1)
            setHasFixedSize(true)
        }
    }

    /** ------------------ TAB HANDLER ------------------ **/
    private fun setupTabClicks() {
        binding.tabVideos.setOnClickListener {
            activeTab = ActiveTab.VIDEOS
            highlightTab(binding.tabVideos)
            setupFolderBack()
        }
        binding.tabFolder.setOnClickListener {
           activeTab = ActiveTab.FOLDER
            highlightTab(binding.tabFolder) }
        binding.tabPlaylist.setOnClickListener {
            activeTab = ActiveTab.PLAYLIST
            highlightTab(binding.tabPlaylist)
            setupFolderBack()
        }
    }

    private fun highlightTab(selected: TextView) {

        // reset underlines
        listOf(binding.lineVideos, binding.lineFolder, binding.linePlaylist)
            .forEach { it.visibility = View.GONE }

        // reset colors
        listOf(binding.tabVideos, binding.tabFolder, binding.tabPlaylist)
            .forEach { it.setTextColor(resources.getColor(R.color.white)) }

        when (selected) {
            binding.tabVideos -> {
                showSection(video = true, folder = false, selected = false, showBack = false)
                binding.lineVideos.visibility = View.VISIBLE
                selected.setTextColor(resources.getColor(R.color.green))
                setVideoRvTop(R.id.customTabs)
                observeVideos()
                isPlaylistView = false
                isFolder = false
                setupBottomActions()
            }

            binding.tabFolder -> {
                binding.lineFolder.visibility = View.VISIBLE
                selected.setTextColor(resources.getColor(R.color.green))
                showSection(video = false, folder = true, selected = false, showBack = false)
                setVideoRvTop(R.id.customTabs)
                binding.emptyView.visibility = View.GONE
                isPlaylistView = false
                isFolder = true
                setupBottomActions()
            }

            binding.tabPlaylist -> {
                binding.linePlaylist.visibility = View.VISIBLE
                selected.setTextColor(resources.getColor(R.color.green))
                showSection(video = false, folder = false, selected = true, showBack = false)
                setVideoRvTop(R.id.customTabs)
                setupPlaylistVideoAdapter()
                observePlaylistVideos()
                isPlaylistView = true
                isFolder = false
                setupBottomActions()
            }
        }
    }

    /** Show/hide layout groups together */
    private fun showSection(video: Boolean, folder: Boolean, selected: Boolean, showBack: Boolean) {
        binding.videoRV.visibility = if (video) View.VISIBLE else View.GONE
        binding.videoFolderRV.visibility = if (folder) View.VISIBLE else View.GONE
        binding.selectedVideosRV.visibility = if (selected) View.VISIBLE else View.GONE
        binding.folderBackLayout.visibility = if (showBack) View.VISIBLE else View.GONE
    }


    /** ---------- CONSTRAINT CLEAN VERSION ---------- **/
    private fun setVideoRvTop(anchorId: Int) {
        val videoRV = binding.videoRV
        val params = videoRV.layoutParams as RelativeLayout.LayoutParams

        // Remove any TOP/BOTTOM rules first
        params.removeRule(RelativeLayout.ALIGN_PARENT_TOP)
        params.removeRule(RelativeLayout.BELOW)

        // Apply new rule: videoRV is below the given anchor view
        params.addRule(RelativeLayout.BELOW, anchorId)

        videoRV.layoutParams = params
    }

    /** ---------- FOLDER ADAPTER ---------- **/
    private fun setupFolderAdapter() {
        val folderList = getVideoFolders(requireContext())

        videoFolderAdapter = VideoFolderAdapter(requireContext(), folderList) { folder ->
            selectedFolder = folder
            openFolderVideos(folder)
        }

        if (folderList.isNotEmpty()) {
            binding.videoFolderRV.adapter = videoFolderAdapter
        } else {
            binding.videoFolderRV.visibility = View.GONE
            binding.emptyView.visibility = View.VISIBLE
            binding.emptyView.text = "No folders found"
        }
    }

    private fun openFolderVideos(folder: VideoFolder) {

        val videosInFolder = videoList.filter {
            it.path.startsWith(folder.folderPath) && !it.isPrivate
        }.toMutableList()

        videoAdapter.filterList(videosInFolder)

        binding.folderName.text = folder.folderName

        showSection(video = true, folder = false, selected = false, showBack = true)

        setVideoRvTop(R.id.folderBackLayout)

        binding.videoRV.scrollToPosition(0)
    }

    /** ---------- BACK BUTTON ---------- **/
    private fun setupFolderBack() {
        selectedFolder = null

        // Restore full list
        videoAdapter.filterList(videoList.toMutableList())
        setVideoRvTop(R.id.customTabs) // move RV back under original layout
    }

    /** ---------- BACK PRESS ---------- **/
    private fun setupBackPress() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (videoAdapter.isSelectionMode) {
                if (isSelectionMode)
                    exitSelectionMode()
                if (binding.selectAllCheckbox.isChecked) {
                    binding.selectAllCheckbox.isChecked = false
                }
                refreshVisibleList()
            } else {
                requireActivity().finish()
            }
        }
    }

    private fun setupSearch() {
        binding.searchIcon.setOnClickListener {

            binding.searchTIET.text.clear()
            binding.searchTIET.requestFocus()
            if (isPlaylistView) {
                binding.selectedVideosRV.visibility = View.GONE
            } else if (isFolder) {
                binding.videoFolderRV.visibility = View.GONE

            } else {
                binding.videoRV.visibility = View.GONE
            }
            binding.topLayout.visibility = View.GONE
            binding.searchLayout.visibility = View.VISIBLE
            binding.emptyView.visibility = View.GONE
            binding.historyLayout.visibility = View.GONE
            binding.customTabs.visibility = View.GONE

            val imm =
                requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.searchTIET, InputMethodManager.SHOW_IMPLICIT)
        }

        binding.backArrow.setOnClickListener {

            if (isPlaylistView) {
                binding.selectedVideosRV.visibility = View.VISIBLE

            } else if (isFolder) {
                binding.videoFolderRV.visibility = View.VISIBLE

            } else {

                binding.videoRV.visibility = View.VISIBLE
            }
            binding.searchLayout.visibility = View.GONE
            binding.topLayout.visibility = View.VISIBLE
            binding.emptyView.visibility = View.GONE
            binding.historyLayout.visibility = View.VISIBLE
            binding.customTabs.visibility = View.VISIBLE

            setVideoRvTop(R.id.customTabs)
            videoAdapter.filterList(videoList)
            val imm =
                requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(binding.searchTIET.windowToken, 0)
            binding.searchTIET.clearFocus()
        }

        binding.searchTIET.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()
                if (query.isNotEmpty()) searchVideos(query) else {
                    binding.videoRV.visibility = View.GONE
                    binding.emptyView.visibility = View.GONE
                }
            }
        })
    }

    private fun setupDonationClick() {
        binding.donation.setOnClickListener {
            val url = "https://donate.wfp.org/1243/donation/regular?campaign=4574"
            try {
                val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(
                    requireActivity(),
                    "No browser found to open the link",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun setupDeleteLauncher() {
        deleteRequestLauncher =
            registerForActivityResult(
                ActivityResultContracts.StartIntentSenderForResult()
            ) { result ->

                if (result.resultCode == Activity.RESULT_OK) {

                    pendingDeleteUris.forEach { uri ->
                        viewModel.deleteVideosByUri(uri.toString())
                        videoList.removeAll { it.contentUri == uri.toString() }
                    }

                    pendingDeleteUris.clear()
                    refreshVisibleList()

                    Toast.makeText(
                        requireContext(),
                        "Videos deleted successfully",
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

    private fun deletePreAndroid11(video: VideosModel) {
        val rowsDeleted = requireContext().contentResolver.delete(
            video.contentUri.toUri(),
            null,
            null
        )

        if (rowsDeleted > 0) {
            viewModel.deleteVideosByUri(video.contentUri)
            videoList.remove(video)
        }
    }

    private fun setupMainMenu() {
//        binding.mainMenu.setOnClickListener { view ->
//            val popupMenu = PopupMenu(requireContext(), view)
//            popupMenu.inflate(R.menu.main_menu)
//            popupMenu.setOnMenuItemClickListener { item ->
//                when (item.itemId) {
//                    R.id.startVersion -> {
//                        Toast.makeText(requireContext(), "App version: ${getAppVersionName(requireContext())}", Toast.LENGTH_SHORT).show()
//                        true
//                    }
//                    else -> false
//                }
//            }
//            popupMenu.show()
//        }
    }

    private fun setupBottomActions() {
        binding.selectAllCheckbox.setOnClickListener { toggleSelectAll() }
        binding.actionDelete.setOnClickListener { deleteSelectedVideos() }
        binding.actionPlay.setOnClickListener { playSelectedVideos() }
        binding.actionShare.setOnClickListener { shareSelectedVideos() }

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

            val selectedVideos =
                videoAdapter.selectedItems.map { videoAdapter.currentList[it] }

            if (selectedVideos.isEmpty()) return@setOnClickListener

            onMakePrivateClicked(selectedVideos)

            if (isSelectionMode) exitSelectionMode()
        }

    }

    private val pendingPrivateVideos = mutableListOf<VideosModel>()

    private val movePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                lifecycleScope.launch {
                    movePendingVideosToPrivate()
                }
            } else {
                pendingPrivateVideos.clear()
            }
        }

    fun onMakePrivateClicked(videos: List<VideosModel>) {

        pendingPrivateVideos.clear()
        pendingPrivateVideos.addAll(videos)

        val uris = videos.map { it.contentUri.toUri() }

        val pendingIntent = MediaStore.createWriteRequest(
            requireContext().contentResolver,
            uris
        )

        movePermissionLauncher.launch(
            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
        )
    }


    private suspend fun movePendingVideosToPrivate() =
        withContext(Dispatchers.IO) {

            val resolver = requireContext().contentResolver
            val privateDir = requireContext()
                .getExternalFilesDir("private_videos")!!
            if (!privateDir.exists()) privateDir.mkdirs()

            for (video in pendingPrivateVideos) {

                val sourceUri = video.contentUri.toUri()
                val destFile = File(privateDir, video.title)

                // Copy
                resolver.openInputStream(sourceUri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // Remove from MediaStore (permission already granted)
                resolver.delete(sourceUri, null, null)

                // Update DB (ONLY private info)
                viewModel.updateVideoIsPrivate(
                    videoId = video.id,
                    isPrivate = true,
                    privatePath = destFile.absolutePath
                )
            }

            pendingPrivateVideos.clear()

            withContext(Dispatchers.Main) {
                refreshVisibleList()
            }
        }


//    private suspend fun makeVideoPrivate(video: VideosModel) {
//        withContext(Dispatchers.IO) {
//
//            try {
//                // Copy to private storage
//                val privatePath = savePrivateVideo(
//                    context = requireContext(),
//                    sourceUri = video.contentUri.toUri(),
//                    fileName = "${video.id}.mp4"
//                )
//
//                // store path temporarily
//                video.privatePath = privatePath
//
//                // Delete original
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//
//                    pendingPrivateDeletes.add(video)
//
//                    val pendingIntent = MediaStore.createDeleteRequest(
//                        requireContext().contentResolver,
//                        listOf(video.contentUri.toUri())
//                    )
//
//                    withContext(Dispatchers.Main) {
//                        privateDeleteRequestLauncher.launch(
//                            IntentSenderRequest.Builder(
//                                pendingIntent.intentSender
//                            ).build()
//                        )
//                    }
//
//                } else {
//                    // Pre-Android 11
//                    val rows = requireContext().contentResolver.delete(
//                        video.contentUri.toUri(),
//                        null,
//                        null
//                    )
//
//                    if (rows > 0) {
//                        viewModel.updateVideoIsPrivate(
//                            videoId = video.id,
//                            isPrivate = true,
//                            privatePath = privatePath
//                        )
//
//                        withContext(Dispatchers.Main) {
//                            videoList.removeAll { it.id == video.id }
//                            videoAdapter.notifyDataSetChanged()
//                        }
//                    }
//                }
//
//            } catch (e: Exception) {
//                e.printStackTrace()
//            }
//        }
//    }
//
//
//    suspend fun savePrivateVideo(
//        context: Context,
//        sourceUri: Uri,
//        fileName: String
//    ): String = withContext(Dispatchers.IO) {
//
//        val privateDir = File(context.filesDir, "private_videos")
//        if (!privateDir.exists()) privateDir.mkdirs()
//
//        val privateFile = File(privateDir, fileName)
//
//        context.contentResolver.openInputStream(sourceUri)?.use { input ->
//            privateFile.outputStream().use { output ->
//                input.copyTo(output)
//            }
//        } ?: throw IllegalStateException("Unable to open input stream")
//
//        privateFile.absolutePath
//    }



    /** ------------------ VIDEO LOGIC ------------------ **/

    private fun observeVideos() {
        viewModel.allPublicVideos.observe(viewLifecycleOwner) { videos ->

            if (activeTab != ActiveTab.VIDEOS) return@observe

            videoList.clear()
            videoList.addAll(videos)
            applySorting()

            binding.videoRV.visibility =
                if (videos.isNotEmpty()) View.VISIBLE else View.GONE
            binding.emptyView.visibility =
                if (videos.isEmpty()) View.VISIBLE else View.GONE

            binding.progressBar.visibility = View.GONE
        }
    }


    private fun observeHistoryVideos() {
        viewModel.allHistoryVideos.observe(viewLifecycleOwner) { historyVideos ->
            videoHistoryList.clear()
            videoHistoryList.addAll(historyVideos)
            videoHistoryAdapter.notifyDataSetChanged()
            binding.historyRV.visibility =
                if (historyVideos.isNotEmpty()) View.VISIBLE else View.GONE
            binding.historyEmptyView.visibility =
                if (historyVideos.isEmpty()) View.VISIBLE else View.GONE
            binding.delHistoryIcon.visibility =
                if (historyVideos.isNotEmpty()) View.VISIBLE else View.GONE

            binding.progressBar.visibility = View.GONE
        }
    }

    private fun observePlaylistVideos() {
        viewModel.allPlaylistVideos.observe(viewLifecycleOwner) { playlistVideos ->

            if (activeTab != ActiveTab.PLAYLIST) return@observe

            videoPlaylistList.clear()
            videoPlaylistList.addAll(playlistVideos)
            playlistVideoAdapter.notifyDataSetChanged()

            binding.selectedVideosRV.visibility =
                if (playlistVideos.isNotEmpty()) View.VISIBLE else View.GONE
            binding.emptyView.visibility =
                if (playlistVideos.isEmpty()) View.VISIBLE else View.GONE

            binding.progressBar.visibility = View.GONE
        }
    }

    private fun enterSelectionMode(position: Int) {
        if (isPlaylistView) {
            playlistVideoAdapter.isSelectionMode = true
            playlistVideoAdapter.selectedItems.add(position)

            playlistVideoAdapter.notifyDataSetChanged()

        } else {
            videoAdapter.isSelectionMode = true
            videoAdapter.selectedItems.add(position)
            videoAdapter.notifyDataSetChanged()
        }
        isSelectionMode = true
        binding.bottomActionBar.visibility = View.VISIBLE
        binding.selectAllCheckbox.visibility = View.VISIBLE
        requireActivity().findViewById<BottomNavigationView>(R.id.bottomNavView)?.visibility =
            View.GONE

    }

    private fun exitSelectionMode() {
        if (isPlaylistView) {
            playlistVideoAdapter.isSelectionMode = false
            playlistVideoAdapter.selectedItems.clear()
            playlistVideoAdapter.notifyDataSetChanged()
        } else {
            videoAdapter.isSelectionMode = false
            videoAdapter.selectedItems.clear()
            videoAdapter.notifyDataSetChanged()
        }
        isSelectionMode = false
        binding.selectAllCheckbox.visibility = View.GONE
        binding.bottomActionBar.visibility = View.GONE
        requireActivity().findViewById<BottomNavigationView>(R.id.bottomNavView)?.visibility =
            View.VISIBLE

    }

    private var selectedFolder: VideoFolder? = null

    private fun refreshVisibleList() {
        if (selectedFolder != null) {
            openFolderVideos(selectedFolder!!)
        } else {
            videoAdapter.filterList(videoList.toMutableList())
        }
    }

    private fun toggleSelectAll() {
        if (isPlaylistView) {
            if (playlistVideoAdapter.selectedItems.size == playlistVideoAdapter.itemCount) {
                if (isSelectionMode)
                    exitSelectionMode()

            } else {

                playlistVideoAdapter.selectedItems.clear()
                playlistVideoAdapter.selectedItems.addAll(playlistVideoAdapter.currentList.indices)
                playlistVideoAdapter.notifyDataSetChanged()
            }
        } else {

            if (videoAdapter.selectedItems.size == videoAdapter.itemCount) {
                if (isSelectionMode)
                    exitSelectionMode()
            } else {
                videoAdapter.selectedItems.clear()
                videoAdapter.selectedItems.addAll(videoAdapter.currentList.indices)
                videoAdapter.notifyDataSetChanged()
            }
        }
    }

    private fun deleteSelectedVideos() {
        val selectedVideos = videoAdapter.selectedItems
            .map { videoAdapter.currentList[it] }

        if (selectedVideos.isEmpty()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

            pendingDeleteUris.clear()
            pendingDeleteUris.addAll(
                selectedVideos.map { it.contentUri.toUri() }
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

        if (isSelectionMode)
            exitSelectionMode()
    }

    private fun playSelectedVideos() {
        if (!isPlaylistView) {
            if (videoAdapter.selectedItems.isNotEmpty()) {
                val positions = videoAdapter.selectedItems.toList()  // ensure fixed order
                val source = videoAdapter.currentList

//                val firstVideo = source[positions.first()]
//                val uris = positions.map { source[it].contentUri }.toTypedArray()
//                val titles = positions.map { source[it].title }.toTypedArray()

//                val action = VideosFragmentDirections.actionVideosFragmentToVideoPlayerFragment(
//                    firstVideo.contentUri, titles, true, uris, 0
//                )
//                findNavController().navigate(action)

                // FIXED – no more crash
                for (pos in positions) {
                    val videoId = source[pos].id
                    Log.e("video ids pos", pos.toString())
                    Log.e("video ids", videoId.toString())
                    viewModel.updateVideoIsPlaylist(videoId, true)
                }
                if (isSelectionMode)
                    exitSelectionMode()

            }
        } else {
            if (playlistVideoAdapter.selectedItems.isNotEmpty()) {
                val positions = playlistVideoAdapter.selectedItems.toList()
                val source = playlistVideoAdapter.currentList

                for (pos in positions) {
                    val videoId = source[pos].id
                    viewModel.updateVideoIsPlaylist(videoId, false)
                }

                if (isSelectionMode) exitSelectionMode()

                // Stay on playlist tab
//                observePlaylistVideos()
            }
        }

    }

    private fun shareSelectedVideos() {
        if (!isPlaylistView) {
            val uris = videoAdapter.selectedItems.map {
                videoAdapter.currentList[it].contentUri.toUri()
            }
            val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "video/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
            startActivity(Intent.createChooser(shareIntent, "Share videos"))
            if (isSelectionMode)
                exitSelectionMode()
        }
    }

    private fun performOptionsMenuClick(position: Int, anchorView: View) {
        val popupMenu = PopupMenu(requireContext(), anchorView)
        popupMenu.inflate(R.menu.options_menu)
        popupMenu.setOnMenuItemClickListener { item ->
            val video = videoAdapter.currentList[position]
            when (item.itemId) {
                R.id.addToPrivate -> {
//                    addFilesToPrivate(video.id)
                    val originalIndex = videoList.indexOfFirst { it.id == video.id }
                    if (originalIndex != -1) videoList.removeAt(originalIndex)

                    val updatedList = videoAdapter.currentList.toMutableList()
                    updatedList.removeAt(position)
                    videoAdapter.filterList(updatedList)

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

    private fun addFilesToPrivate(videoId: Long, isPrivate: Boolean, privatePath: String, videosAdapter: VideosAdapter) {
        viewModel.updateVideoIsPrivate(videoId, isPrivate, privatePath)
        Log.e("is private1", isPrivate.toString())

        videosAdapter.notifyDataSetChanged()
        Toast.makeText(requireContext(), "Added to private", Toast.LENGTH_SHORT).show()
    }

    private fun deleteFileFromStorage(video: VideosModel) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                val rowsDeleted =
                    requireContext().contentResolver.delete(video.contentUri.toUri(), null, null)
                if (rowsDeleted > 0) {
                    viewModel.deleteVideosByUri(video.contentUri)
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

    /** ------------------ SEARCH ------------------ **/

    private fun searchVideos(query: String) {
        val filtered = videoList.filter { it.title.contains(query, ignoreCase = true) }
        if (filtered.isNotEmpty()) {
            videoAdapter.filterList(filtered.toMutableList())
            setVideoRvTop(R.id.searchLayout)
            binding.videoRV.visibility = View.VISIBLE
            binding.emptyView.visibility = View.GONE
        } else {
            setVideoRvTop(R.id.customTabs)
            binding.videoRV.visibility = View.GONE
            binding.emptyView.visibility = View.VISIBLE
            binding.emptyView.text = "No videos found"
        }
    }

    /** ------------------ PERMISSIONS ------------------ **/

    private val storagePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) loadMediaFilesIntoDB() else
                Toast.makeText(requireContext(), "Permission Denied", Toast.LENGTH_SHORT).show()
        }

    private fun checkMediaPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            android.Manifest.permission.READ_MEDIA_VIDEO else
            android.Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(
                requireContext(),
                permission
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            lifecycleScope.launch {
                val currentVideos = withContext(Dispatchers.IO) { viewModel.getVideoCount() }
                if (currentVideos == 0) loadMediaFilesIntoDB() else binding.progressBar.visibility =
                    View.GONE
            }
        } else storagePermissionLauncher.launch(permission)
    }

    private fun loadMediaFilesIntoDB() {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            val fetched = withContext(Dispatchers.IO) { fetchMediaFiles(requireContext()) }
            if (fetched.isNotEmpty()) {
                viewModel.insertAllVideos(fetched)
                binding.videoRV.visibility = View.VISIBLE
                binding.emptyView.visibility = View.GONE
            } else binding.emptyView.visibility = View.VISIBLE
            binding.progressBar.visibility = View.GONE
        }
    }

    /** ------------------ UTILS ------------------ **/

    private fun fetchMediaFiles(context: Context): MutableList<VideosModel> {
        val mediaList = mutableListOf<VideosModel>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATA,
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"
        val queryUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val cursor = context.contentResolver.query(queryUri, projection, null, null, sortOrder)

        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dateCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val mimeCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val durCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val folderCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val sizeCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val pathCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)

            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                val name = it.getString(nameCol)
                val date = it.getLong(dateCol)
                val mime = it.getString(mimeCol)
                val dur = it.getLong(durCol)
                val folder = it.getString(folderCol)
                val size = it.getLong(sizeCol)
                val path = it.getString(pathCol)
                val contentUri = ContentUris.withAppendedId(queryUri, id)

                mediaList.add(
                    VideosModel(
                        id,
                        name,
                        contentUri.toString(),
                        date,
                        mime,
                        dur,
                        folder,
                        size.toString(),
                        path
                    )
                )
            }
        }
        return mediaList
    }

    fun getVideoFolders(context: Context): List<VideoFolder> {
        val folderMap = HashMap<String, MutableList<String>>() // folderPath -> list of videos

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATA
        )

        val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val sortOrder = MediaStore.Video.Media.DATE_ADDED + " DESC"

        val cursor = context.contentResolver.query(
            uri, projection, null, null, sortOrder
        )

        cursor?.use {
            val dataCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)

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
        val folderList = ArrayList<VideoFolder>()
        for ((folderPath, videos) in folderMap) {
            folderList.add(
                VideoFolder(
                    folderName = File(folderPath).name,
                    folderPath = folderPath,
                    videoCount = videos.size
                )
            )
        }

        return folderList
    }

    private fun getAppVersionName(context: Context): String? {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Exception) {
            null
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasVideoPermission(requireContext())) {
            loadMediaFilesIntoDB()
        }
    }
    private val mediaPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->

            val videoGranted =
                permissions[getVideoPermission()] == true

            val audioGranted =
                permissions[getAudioPermission()] == true

            if (!videoGranted || !audioGranted) {
                openAppSettings()
                Toast.makeText(requireContext(), "Storage permission required", Toast.LENGTH_SHORT)
                    .show()
            } else {
                lifecycleScope.launch {
                    val currentVideos = withContext(Dispatchers.IO) { viewModel.getVideoCount() }
                    if (currentVideos == 0) loadMediaFilesIntoDB() else binding.progressBar.visibility =
                        View.GONE
                }
                requestNotificationPermissionIfNeeded()
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(requireContext(), "Notifications disabled", Toast.LENGTH_SHORT)
                    .show()
            }
        }

    private fun getVideoPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_VIDEO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

    private fun getAudioPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasVideoPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            getVideoPermission()
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestMediaPermissions() {
        val permissions = arrayOf(
            getVideoPermission(),
            getAudioPermission()
        )

        mediaPermissionLauncher.launch(permissions)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }


    private fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", requireContext().packageName, null)
        )
        startActivity(intent)
    }

}
