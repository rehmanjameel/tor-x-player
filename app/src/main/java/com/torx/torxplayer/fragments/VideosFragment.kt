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
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.torx.torxplayer.OptionsMenuClickListener
import com.torx.torxplayer.R
import com.torx.torxplayer.adapters.VideosAdapter
import com.torx.torxplayer.databinding.FragmentVideosBinding
import com.torx.torxplayer.model.VideosModel
import com.torx.torxplayer.viewmodel.FilesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@RequiresApi(Build.VERSION_CODES.R)
class VideosFragment : Fragment() {

    private lateinit var binding: FragmentVideosBinding
    private lateinit var videoAdapter: VideosAdapter
    private var videoList = mutableListOf<VideosModel>()

    private lateinit var deleteRequestLauncher: ActivityResultLauncher<IntentSenderRequest>
    private var lastDeletedUri: Uri? = null
    private lateinit var viewModel: FilesViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = FragmentVideosBinding.inflate(inflater, container, false)

        // initialize the recyclerview
//        setupRecyclerView()

        binding.videoRV.layoutManager = GridLayoutManager(
            requireContext(), 1,
            LinearLayoutManager.VERTICAL, false
        )
        binding.videoRV.setHasFixedSize(true)


        binding.donation.setOnClickListener {
            val url = "https://donate.wfp.org/1243/donation/regular?campaign=4574&_ga=2.233279257.602488721.1762603852-1327722293.1762603852&_gac=1.187330266.1762603852.CjwKCAiA8bvIBhBJEiwAu5ayrDyDEAP5YnuLh0lhI8kMRsprikeoVM9kdNvpFIRbTpmzfzSD6wKZ2RoCojcQAvD_BwE"
            try {
                val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(requireActivity(), "No browser found to open the link", Toast.LENGTH_SHORT).show()
            }
        }

        // Register the launcher for delete request
        deleteRequestLauncher =
            registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    videoAdapter.notifyDataSetChanged()
                    Toast.makeText(
                        requireContext(),
                        "File deleted successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                    lastDeletedUri.let { deletedUri ->
                        viewModel.deleteVideosByUri(deletedUri.toString()) // also remove from DB
                        videoList.removeAll { it.contentUri == deletedUri.toString() }
                        videoAdapter.notifyDataSetChanged()
                    }
                    // You can refresh your list or remove the item here if not already done
                } else {
                    Toast.makeText(requireContext(), "File not deleted", Toast.LENGTH_SHORT).show()
                }
            }

        // When search icon clicked
        binding.searchIcon.setOnClickListener {
            binding.searchLayout.visibility = View.VISIBLE
            binding.topLayout.visibility = View.GONE

            // Hide main list until search text typed
            binding.videoRV.visibility = View.GONE
            binding.emptyView.visibility = View.GONE

            binding.searchTIET.text.clear()
            binding.searchTIET.requestFocus()

            val imm = requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.searchTIET, InputMethodManager.SHOW_IMPLICIT)
        }

        // When back arrow clicked
        binding.backArrow.setOnClickListener {
            binding.searchLayout.visibility = View.GONE
            binding.topLayout.visibility = View.VISIBLE

            // Restore full list view
            binding.videoRV.visibility = View.VISIBLE
            binding.emptyView.visibility = View.GONE
            videoAdapter.filterList(videoList) // restore original data

            val imm = requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(binding.searchTIET.windowToken, 0)
            binding.searchTIET.clearFocus()
        }

        // Listen for typing
        binding.searchTIET.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()

                if (query.isNotEmpty()) {
                    searchVideos(query)
                } else {
                    // blank screen if no text
                    binding.videoRV.visibility = View.GONE
                    binding.emptyView.visibility = View.GONE
                }
            }
        })

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val app = requireActivity().application
        viewModel = ViewModelProvider(
            requireActivity(),
            ViewModelProvider.AndroidViewModelFactory.getInstance(app)
        )[FilesViewModel::class.java]

        Log.e("video list", "$videoList")
        videoAdapter = VideosAdapter(requireContext(), videoList, object : OptionsMenuClickListener {
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
                val video = videoList[position]
                Toast.makeText(requireContext(), "Long press clicked", Toast.LENGTH_SHORT).show()
                Log.e("long press", "long press clicked")
//                deleteFileFromStorage(video)
            }
        })
        binding.videoRV.adapter = videoAdapter
//        setupRecyclerView()

        // Observe database once here
        viewModel.allPublicVideos.observe(viewLifecycleOwner) { videos ->
            videoList.clear()
            videoList.addAll(videos)
            videoAdapter.notifyDataSetChanged()
            if (videos.isNotEmpty()) {
                binding.videoRV.visibility = View.VISIBLE
                binding.emptyView.visibility = View.GONE
                binding.progressBar.visibility = View.GONE
            } else {
                binding.videoRV.visibility = View.GONE
                binding.emptyView.visibility = View.VISIBLE
                binding.progressBar.visibility = View.GONE
            }
        }
        checkMediaPermission()

        binding.mainMenu.setOnClickListener { it ->
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

    // this function will be called when the fragment is created when to check the permissions
    private val storagePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                loadMediaFilesIntoDB()
            } else {
                Toast.makeText(requireContext(), "Permission Denied", Toast.LENGTH_SHORT).show()
            }
        }

    fun fetchMediaFiles(context: Context): MutableList<VideosModel> {
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

        val cursor = context.contentResolver.query(
            queryUri,
            projection,
            null,
            null,
            sortOrder
        )

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dateAddedColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val mimeTypeColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)

            val durationColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val folderNameColumn =
                it.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val pathColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)

            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                val name = it.getString(nameColumn)
                val dateAdded = it.getLong(dateAddedColumn)
                val mimeType = it.getString(mimeTypeColumn)
                val duration = it.getLong(durationColumn)
                val folderName = it.getString(folderNameColumn)
                val size = it.getLong(sizeColumn)
                val path = it.getString(pathColumn)

                val contentUri: Uri = ContentUris.withAppendedId(queryUri, id)

                mediaList.add(
                    VideosModel(
                        id = id,
                        title = name,
                        contentUri = contentUri.toString(),
                        dateAdded = dateAdded,
                        mimeType = mimeType,
                        duration = duration,
                        folderName = folderName,
                        size = size.toString(),
                        path = path
                    )
                )
            }
        }
        return mediaList
    }

    // check the storage permission
    private fun checkMediaPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_VIDEO
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
                val currentVideos = withContext(Dispatchers.IO) {
                    viewModel.getVideoCount() // implement in ViewModel
                }

                if (currentVideos == 0) {
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

            val fetchedVideos = withContext(Dispatchers.IO) {
                fetchMediaFiles(requireContext())
            }

            if (fetchedVideos.isNotEmpty()) {
                viewModel.insertAllVideos(fetchedVideos)
                binding.progressBar.visibility = View.GONE
                binding.emptyView.visibility = View.GONE
                binding.videoRV.visibility = View.VISIBLE
                Log.e("video list1", "${fetchedVideos.isNotEmpty()}")
            } else {
                Log.e("video list2", "${fetchedVideos}")
                binding.progressBar.visibility = View.GONE
                binding.emptyView.visibility = View.VISIBLE
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
//                    val action = VideosFragmentDirections.actionVideosFragmentToVideoPlayerFragment(
//                        video.contentUri,
//                        video.title,
//                        true
//                    )
//                    findNavController().navigate(action)
//                    true
//                }

                R.id.addToPrivate -> {
                    viewModel.updateVideoIsPrivate(video.id, true)
                    videoList.removeAt(position)
                    videoAdapter.notifyItemRemoved(position)
                    Toast.makeText(requireContext(), "Add to private clicked", Toast.LENGTH_SHORT)
                        .show()
                    true
                }

                R.id.delete -> {
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
        }

        if (filteredVideos.isNotEmpty()) {
            videoAdapter.filterList(filteredVideos.toMutableList())
            binding.videoRV.visibility = View.VISIBLE
            binding.emptyView.visibility = View.GONE
        } else {
            binding.videoRV.visibility = View.GONE
            binding.emptyView.visibility = View.VISIBLE
            binding.emptyView.text = "No videos found"
        }
    }

}
