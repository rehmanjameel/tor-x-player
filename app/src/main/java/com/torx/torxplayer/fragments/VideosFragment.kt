package com.torx.torxplayer.fragments

import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
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
import com.torx.torxplayer.R
import com.torx.torxplayer.adapters.VideosAdapter
import com.torx.torxplayer.databinding.FragmentVideosBinding
import com.torx.torxplayer.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class VideosFragment : Fragment() {

    private lateinit var binding: FragmentVideosBinding
    private lateinit var videoAdapter: VideosAdapter
    private lateinit var videoList: List<Video>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = FragmentVideosBinding.inflate(inflater, container, false)

        // initialize the recyclerview
        setupRecyclerView()

        // check storage permission
        checkMediaPermission()

        return binding.root
    }

    companion object {
        @JvmStatic
        fun newInstance() = VideosFragment()
    }

    private fun setupRecyclerView() {
        binding.videoRV.layoutManager = GridLayoutManager(requireContext(), 2,
            LinearLayoutManager.VERTICAL, false)
        binding.videoRV.setHasFixedSize(true)
//        videoAdapter = VideosAdapter(requireContext(), videoList)
//        binding.videoRV.adapter = videoAdapter
    }

    // this function will be called when the fragment is created when to check the permissions
    private val storagePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            videoList = fetchMediaFiles(requireContext())
        } else {
            Toast.makeText(requireContext(), "Permission Denied", Toast.LENGTH_SHORT).show()
        }
    }

    fun fetchMediaFiles(context: Context): List<Video> {
        val mediaList = mutableListOf<Video>()

        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.DURATION,
            MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATA,
        )

        // Selection for images and videos only
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?"
        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )

        val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

        val queryUri = MediaStore.Files.getContentUri("external")

        val cursor = context.contentResolver.query(
            queryUri,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val dateAddedColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
            val mimeTypeColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)

            val durationColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DURATION)
            val folderNameColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.BUCKET_DISPLAY_NAME)
            val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val pathColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)

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

                // **CRITICAL CHANGE**: Generate thumbnail in the background
                val thumbnail = getVideoThumbnail(requireContext(), contentUri)

                mediaList.add(Video(id = id, title = name, contentUri = contentUri, thumbnail = thumbnail,
                    dateAdded = dateAdded, mimeType = mimeType, duration = duration, folderName = folderName,
                    size = size.toString(), path = path))
            }
        }
        return mediaList
    }

    // get the video thumbnail using MediaMetadataRetriever and video uri
    fun getVideoThumbnail(context: Context, videoUri: Uri): Bitmap? {
        val mediaMetadataRetriever = MediaMetadataRetriever()
        try {
            mediaMetadataRetriever.setDataSource(context, videoUri)
            // You can specify a time in microseconds to get a frame at a specific point.
            // If you want the first frame, you can typically use 0L.
            // You can also specify the option to get the closest sync frame or a representative frame.
            return mediaMetadataRetriever.getFrameAtTime(-1
//                0L, // Time in microseconds
//                MediaMetadataRetriever.OPTION_CLOSEST_SYNC // Option for frame retrieval
            )
        } catch (e: Exception) {
            Log.e("VideoThumbnail", "Error getting video thumbnail: ${e.message}")
            return null
        } finally {
            mediaMetadataRetriever.release() // Release resources
        }
    }

    // check the storage permission
    private fun checkMediaPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_VIDEO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED) {
            loadMediaFiles()
        } else {
            storagePermissionLauncher.launch(permission)
        }
    }

    private fun loadMediaFiles() {
        lifecycleScope.launch(Dispatchers.IO) {
            //show progress bar while loading
            binding.emptyView.visibility = View.GONE
            binding.videoRV.visibility = View.GONE

            binding.progressBar.visibility = View.VISIBLE

            // Switch to the IO dispatcher for background work
            val fetchedVideos = withContext(Dispatchers.Main) {
                fetchMediaFiles(requireContext())
            }

            if (fetchedVideos.isEmpty()) {
                binding.progressBar.visibility = View.GONE
                binding.videoRV.visibility = View.GONE
                binding.emptyView.visibility = View.VISIBLE
            } else {
                binding.progressBar.visibility = View.GONE

                binding.videoRV.visibility = View.VISIBLE
                binding.emptyView.visibility = View.GONE
                videoList = fetchedVideos
                addDataToAdapter()
            }
        }
    }

    private fun addDataToAdapter() {
        if (videoList.isEmpty()) {
            binding.videoRV.visibility = View.GONE
            binding.emptyView.visibility = View.VISIBLE
        } else {
            binding.videoRV.visibility = View.VISIBLE
            binding.emptyView.visibility = View.GONE
            videoAdapter = VideosAdapter(requireContext(), videoList)
            binding.videoRV.adapter = videoAdapter
        }
    }
}
