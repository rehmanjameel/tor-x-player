package com.torx.torxplayer.fragments

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
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
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.ActionOnlyNavDirections
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.torx.torxplayer.OptionsMenuClickListener
import com.torx.torxplayer.R
import com.torx.torxplayer.adapters.VideosAdapter
import com.torx.torxplayer.databinding.FragmentVideosBinding
import com.torx.torxplayer.model.Audio
import com.torx.torxplayer.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


@RequiresApi(Build.VERSION_CODES.R)
class VideosFragment : Fragment() {

    private lateinit var binding: FragmentVideosBinding
    private lateinit var videoAdapter: VideosAdapter
    private lateinit var videoList: MutableList<Video>
    private lateinit var deleteRequestLauncher: ActivityResultLauncher<IntentSenderRequest>
    private var lastDeletedUri: Uri? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = FragmentVideosBinding.inflate(inflater, container, false)

        // initialize the recyclerview
        setupRecyclerView()

        binding.searchIcon.setOnClickListener {
            binding.searchTIL.visibility = View.VISIBLE
            binding.backArrow.visibility = View.VISIBLE
            binding.title.visibility = View.GONE
            binding.searchIcon.visibility = View.GONE

            binding.searchTIET.requestFocus()
        }

        binding.backArrow.setOnClickListener {
            binding.searchTIL.visibility = View.GONE
            binding.backArrow.visibility = View.GONE
            binding.title.visibility = View.VISIBLE
            binding.searchIcon.visibility = View.VISIBLE

            binding.searchTIET.clearFocus()
            binding.searchTIET.text?.clear()
        }

        // search videos
        binding.searchTIET.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {

            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                val query = p0.toString().trim()
                if (query.isNotEmpty()) {
                    searchVideos(query)
                } else {
                    videoAdapter.filterList(videoList)
                }
            }

        })

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
                        videoList.removeAll { it.contentUri ==  deletedUri}
                        videoAdapter.notifyDataSetChanged()
                    }
                    // You can refresh your list or remove the item here if not already done
                } else {
                    Toast.makeText(requireContext(), "File not deleted", Toast.LENGTH_SHORT).show()
                }
            }

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        // check storage permission
        checkMediaPermission()
    }

    companion object {
        @JvmStatic
        fun newInstance() = VideosFragment()
    }

    private fun setupRecyclerView() {
        binding.videoRV.layoutManager = GridLayoutManager(
            requireContext(), 1,
            LinearLayoutManager.VERTICAL, false
        )
        binding.videoRV.setHasFixedSize(true)
    }

    // this function will be called when the fragment is created when to check the permissions
    private val storagePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                videoList = fetchMediaFiles(requireContext())
            } else {
                Toast.makeText(requireContext(), "Permission Denied", Toast.LENGTH_SHORT).show()
            }
        }

    fun fetchMediaFiles(context: Context): MutableList<Video> {
        val mediaList = mutableListOf<Video>()

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

        // Selection for images and videos only
//        val selection =
//            "${MediaStore.Video.Media.MEDIA_TYPE}=? OR ${MediaStore.Video.Media.MEDIA_TYPE}=?"
//        val selectionArgs = arrayOf(
//            MediaStore.Video.Media.MEDIA_TYPE_VIDEO.toString()
//        )

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
                    Video(
                        id = id,
                        title = name,
                        contentUri = contentUri,
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
            val fetchedVideos = withContext(Dispatchers.IO) {
                fetchMediaFiles(requireContext())
            }

            binding.progressBar.visibility = View.GONE
            if (fetchedVideos.isEmpty()) {
                binding.emptyView.visibility = View.VISIBLE
            } else {
                binding.videoRV.visibility = View.VISIBLE
                videoList = fetchedVideos

                Log.d("VideoList size", videoList.size.toString())
                // add video list in adapter
                videoAdapter = VideosAdapter(requireContext(), videoList, object :
                    OptionsMenuClickListener {
                    override fun onOptionsMenuClicked(position: Int, anchorView: View) {
                        // handle video click here
                        performOptionsMenuClick(position, anchorView)
                    }
                })
                binding.videoRV.adapter = videoAdapter
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
                        video.contentUri.toString(),
                        video.title
                    )
                    findNavController().navigate(action)
                    true
                }

                R.id.addToPrivate -> {
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

    private fun deleteFileFromStorage(tempLang: Video) {
        try {
            // For Android Q (API 29) and below — direct delete
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                val rowsDeleted = requireContext().contentResolver.delete(tempLang.contentUri, null, null)
                if (rowsDeleted > 0) {
                    Toast.makeText(context, "File deleted successfully", Toast.LENGTH_SHORT).show()
                    videoList.remove(tempLang)
                    videoAdapter.notifyDataSetChanged()
                } else {
                    Toast.makeText(context, "Failed to delete file", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Android 11+ (Scoped Storage) — user confirmation required
                val collection = arrayListOf(tempLang.contentUri)
                val pendingIntent = MediaStore.createDeleteRequest(requireContext().contentResolver, collection)
                val request = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                lastDeletedUri = tempLang.contentUri
                deleteRequestLauncher.launch(request)
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
            Toast.makeText(context, "Permission denied to delete file", Toast.LENGTH_SHORT).show()
        }
    }


//    private fun deleteVideoFromStorage(video: Video) {
//        val resolver = requireContext().contentResolver
//        try {
//            val rowsDeleted = resolver.delete(video.contentUri, null, null)
//            if (rowsDeleted > 0) {
//                Toast.makeText(requireContext(), "Video deleted successfully", Toast.LENGTH_SHORT).show()
//                // remove from list and update adapter if needed
//                videoList.removeAll { it.contentUri == video.contentUri }
//                videoAdapter.notifyDataSetChanged()
//            } else {
//                Toast.makeText(requireContext(), "Failed to delete video", Toast.LENGTH_SHORT).show()
//            }
//
//        } catch (e: SecurityException) {
//            // Handle scoped storage security (Android 10+)
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                val collection = arrayListOf(video.contentUri)
//                try {
//                    val pendingIntent = MediaStore.createDeleteRequest(resolver, collection)
//                    val request = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
//                    lastDeletedUri = video.contentUri
//                    deleteRequestLauncher.launch(request)
//                } catch (ex: IllegalArgumentException) {
//                    // For non-media files, fallback manual delete
//                    val file = File(getRealPathFromURI(video.contentUri))
//                    if (file.exists() && file.delete()) {
//                        videoList.removeAll { it.contentUri == video.contentUri }
//                        videoAdapter.notifyDataSetChanged()
//                        Toast.makeText(requireContext(), "File deleted manually", Toast.LENGTH_SHORT).show()
//                    } else {
//                        Toast.makeText(requireContext(), "Manual delete failed", Toast.LENGTH_SHORT).show()
//                    }
//                }
//            }
//        }
//    }

//    private fun getRealPathFromURI(uri: Uri): String? {
//        val projection = arrayOf(MediaStore.Video.Media.DATA)
//        requireContext().contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
//            val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
//            if (cursor.moveToFirst()) {
//                return cursor.getString(columnIndex)
//            }
//        }
//        return null
//    }

    // search videos
    private fun searchVideos(query: String) {

        val filteredVideos = videoList.filter { video ->
            video.title.contains(query, ignoreCase = true)
        }.toMutableList()
        videoAdapter.filterList(filteredVideos)
    }

}
