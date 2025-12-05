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
import android.util.Log.v
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.PopupMenu
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
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.torx.torxplayer.OptionsMenuClickListener
import com.torx.torxplayer.R
import com.torx.torxplayer.adapters.VideoFolderAdapter
import com.torx.torxplayer.adapters.VideosAdapter
import com.torx.torxplayer.databinding.FragmentVideosBinding
import com.torx.torxplayer.model.VideoFolder
import com.torx.torxplayer.model.VideosModel
import com.torx.torxplayer.viewmodel.FilesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@RequiresApi(Build.VERSION_CODES.R)
class VideosFragment : Fragment() {

    private lateinit var binding: FragmentVideosBinding
    private lateinit var videoAdapter: VideosAdapter
    private lateinit var videoFolderAdapter: VideoFolderAdapter
    private var videoList = mutableListOf<VideosModel>()

    private lateinit var deleteRequestLauncher: ActivityResultLauncher<IntentSenderRequest>
    private var lastDeletedUri: Uri? = null
    private lateinit var viewModel: FilesViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentVideosBinding.inflate(inflater, container, false)

        setupRecyclerView()
        setupSearch()
        setupDonationClick()
        setupDeleteLauncher()

        //open the download screen
        binding.downloadIcon.setOnClickListener {
            val action = VideosFragmentDirections.actionVideosFragmentToDownloadFragment()
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

        videoAdapter =
            VideosAdapter(requireContext(), videoList, object : OptionsMenuClickListener {
                override fun onOptionsMenuClicked(position: Int, anchorView: View) {
                    performOptionsMenuClick(position, anchorView)
                }

                override fun onItemClick(position: Int) {
                    val video = videoList[position]
                    val action = VideosFragmentDirections.actionVideosFragmentToVideoPlayerFragment(
                        video.contentUri,
                        videoList.map { it.title }.toTypedArray(),
                        true,
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
        binding.videoRV.adapter = videoAdapter

        setupFolderAdapter()

        observeVideos()
        checkMediaPermission()
        setupMainMenu()
        setupBottomActions()

        binding.tabVideos.setOnClickListener { v ->
            highlightTab(binding.tabVideos)

        }

        binding.tabFolder.setOnClickListener { v ->
            highlightTab(binding.tabFolder)

        }

        binding.tabPlaylist.setOnClickListener { v ->
            highlightTab(binding.tabPlaylist)

        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (videoAdapter.isSelectionMode) {
                exitSelectionMode()
            } else {
                // Close the app
                requireActivity().finish()
            }
        }

        binding.folderBackArrow.setOnClickListener {
            binding.videoRV.visibility = View.GONE
            binding.videoFolderRV.visibility = View.VISIBLE
            binding.folderBackLayout.visibility = View.GONE
            binding.selectedVideosRV.visibility = View.GONE

        }

    }

    /** ------------------ SETUP FUNCTIONS ------------------ **/

    private fun highlightTab(selected: TextView) {

        // Reset underline visibility
        binding.lineVideos.visibility = View.GONE
        binding.lineFolder.visibility = View.GONE
        binding.linePlaylist.visibility = View.GONE

        // Reset colors
        binding.tabVideos.setTextColor(resources.getColor(R.color.white))
        binding.tabFolder.setTextColor(resources.getColor(R.color.white))
        binding.tabPlaylist.setTextColor(resources.getColor(R.color.white))

        // Apply selected underline + color
        when (selected) {
            binding.tabVideos -> {
                binding.lineVideos.visibility = View.VISIBLE
                binding.tabVideos.setTextColor(resources.getColor(R.color.green))
                binding.videoRV.visibility = View.VISIBLE
                binding.videoFolderRV.visibility = View.GONE
                binding.selectedVideosRV.visibility = View.GONE
                binding.folderBackLayout.visibility = View.GONE
            }

            binding.tabFolder -> {
                binding.lineFolder.visibility = View.VISIBLE
                binding.tabFolder.setTextColor(resources.getColor(R.color.green))
                binding.videoRV.visibility = View.GONE
                binding.videoFolderRV.visibility = View.VISIBLE
                binding.folderBackLayout.visibility = View.GONE
                binding.selectedVideosRV.visibility = View.GONE
            }

            binding.tabPlaylist -> {
                binding.linePlaylist.visibility = View.VISIBLE
                binding.tabPlaylist.setTextColor(resources.getColor(R.color.green))
                binding.videoRV.visibility = View.GONE
                binding.videoFolderRV.visibility = View.GONE
                binding.selectedVideosRV.visibility = View.VISIBLE
                binding.folderBackLayout.visibility = View.GONE
            }
        }
    }

    private fun setupRecyclerView() {
        binding.videoRV.layoutManager =
            GridLayoutManager(requireContext(), 1, LinearLayoutManager.VERTICAL, false)
        binding.videoRV.setHasFixedSize(true)

        binding.videoFolderRV.layoutManager =
            GridLayoutManager(requireContext(), 2, LinearLayoutManager.VERTICAL, false)
        binding.videoRV.setHasFixedSize(true)

        binding.selectedVideosRV.layoutManager =
            GridLayoutManager(requireContext(), 1, LinearLayoutManager.VERTICAL, false)
        binding.videoRV.setHasFixedSize(true)

    }

    private fun setupFolderAdapter() {
        val folderList = getVideoFolders(requireContext())

        videoFolderAdapter = VideoFolderAdapter(requireContext(), folderList) { folder ->
            // handle folder click
            // Example:
            openFolderVideos(folder)
//            val videosInFolder = videoList.filter {
//                it.path.startsWith(folder.folderPath)
//            }
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

        // Filter videos belonging to this folder
        val videosInFolder = videoList.filter { video ->
            video.path.startsWith(folder.folderPath)
        }.toMutableList()

        // Update your global videoList used in videoAdapter
        videoAdapter.filterList(videosInFolder)

        // Highlight the "Videos" tab & switch RRV
//        highlightTab(binding.tabVideos)
        binding.videoRV.visibility = View.VISIBLE
        binding.videoFolderRV.visibility = View.GONE
        binding.selectedVideosRV.visibility = View.GONE
        binding.folderBackLayout.visibility = View.VISIBLE
        binding.folderName.text = folder.folderName

        // Optional: scroll to top
        binding.videoRV.scrollToPosition(0)
    }


    private fun setupSearch() {
        binding.searchIcon.setOnClickListener {
            binding.searchLayout.visibility = View.VISIBLE
            binding.topLayout.visibility = View.GONE
            binding.videoRV.visibility = View.GONE
            binding.emptyView.visibility = View.GONE
            binding.searchTIET.text.clear()
            binding.searchTIET.requestFocus()
            val imm =
                requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.searchTIET, InputMethodManager.SHOW_IMPLICIT)
        }

        binding.backArrow.setOnClickListener {
            binding.searchLayout.visibility = View.GONE
            binding.topLayout.visibility = View.VISIBLE
            binding.videoRV.visibility = View.VISIBLE
            binding.emptyView.visibility = View.GONE
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
            registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    lastDeletedUri?.let { uri ->
                        viewModel.deleteVideosByUri(uri.toString())
                        videoList.removeAll { it.contentUri == uri.toString() }
                        videoAdapter.notifyDataSetChanged()
                    }
                    Toast.makeText(
                        requireContext(),
                        "File deleted successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(requireContext(), "File not deleted", Toast.LENGTH_SHORT).show()
                }
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
        binding.actionDelete.setOnClickListener { deleteSelectedVideos() }
        binding.selectAllCheckbox.setOnClickListener { toggleSelectAll() }
        binding.actionPlay.setOnClickListener { playSelectedVideos() }
        binding.actionShare.setOnClickListener { shareSelectedVideos() }
        binding.actionPrivate.setOnClickListener {
            val selectedVideos = videoAdapter.selectedItems.map { videoList[it] }
            for (video in selectedVideos) {
                addFilesToPrivate(video.id)
            }
            exitSelectionMode()
        }
    }

    /** ------------------ VIDEO LOGIC ------------------ **/

    private fun observeVideos() {
        viewModel.allPublicVideos.observe(viewLifecycleOwner) { videos ->
            videoList.clear()
            videoList.addAll(videos)
            videoAdapter.notifyDataSetChanged()

            binding.videoRV.visibility = if (videos.isNotEmpty()) View.VISIBLE else View.GONE
            binding.emptyView.visibility = if (videos.isEmpty()) View.VISIBLE else View.GONE
            binding.progressBar.visibility = View.GONE
        }
    }

    private fun enterSelectionMode(position: Int) {
        videoAdapter.isSelectionMode = true
        videoAdapter.selectedItems.add(position)
        videoAdapter.notifyDataSetChanged()
        binding.bottomActionBar.visibility = View.VISIBLE
        binding.selectAllCheckbox.visibility = View.VISIBLE
        requireActivity().findViewById<BottomNavigationView>(R.id.bottomNavView)?.visibility =
            View.GONE

    }

    private fun exitSelectionMode() {
        videoAdapter.isSelectionMode = false
        videoAdapter.selectedItems.clear()
        videoAdapter.notifyDataSetChanged()
        binding.selectAllCheckbox.visibility = View.GONE
        binding.bottomActionBar.visibility = View.GONE
        requireActivity().findViewById<BottomNavigationView>(R.id.bottomNavView)?.visibility =
            View.VISIBLE

    }

    private fun toggleSelectAll() {
        if (videoAdapter.selectedItems.size == videoAdapter.itemCount) {
            exitSelectionMode()
        } else {
            videoAdapter.selectedItems.clear()
            videoAdapter.selectedItems.addAll(videoList.indices)
            videoAdapter.notifyDataSetChanged()
        }
    }

    private fun deleteSelectedVideos() {
        val selectedVideos = videoAdapter.selectedItems.map { videoList[it] }
        for (video in selectedVideos) {
            deleteFileFromStorage(video)
        }
        exitSelectionMode()
    }

    private fun playSelectedVideos() {
        if (videoAdapter.selectedItems.isNotEmpty()) {
            val positions = videoAdapter.selectedItems
            val firstVideo = videoList[positions.first()]
            val uris = positions.map { videoList[it].contentUri }.toTypedArray()
            val titles = positions.map { videoList[it].title }.toTypedArray()

            val action = VideosFragmentDirections.actionVideosFragmentToVideoPlayerFragment(
                firstVideo.contentUri, titles, true, uris, 0
            )
            findNavController().navigate(action)
            exitSelectionMode()
        }
    }

    private fun shareSelectedVideos() {
        val uris = videoAdapter.selectedItems.map { Uri.parse(videoList[it].contentUri) }
        val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "video/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        startActivity(Intent.createChooser(shareIntent, "Share videos"))
        exitSelectionMode()
    }

    private fun performOptionsMenuClick(position: Int, anchorView: View) {
        val popupMenu = PopupMenu(requireContext(), anchorView)
        popupMenu.inflate(R.menu.options_menu)
        popupMenu.setOnMenuItemClickListener { item ->
            val video = videoList[position]
            when (item.itemId) {
                R.id.addToPrivate -> {
                    addFilesToPrivate(video.id)
                    videoList.removeAt(position)
                    videoAdapter.notifyItemRemoved(position)
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

    private fun addFilesToPrivate(videoId: Long) {
        viewModel.updateVideoIsPrivate(videoId, true)
        videoAdapter.notifyDataSetChanged()
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
            binding.videoRV.visibility = View.VISIBLE
            binding.emptyView.visibility = View.GONE
        } else {
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

}
