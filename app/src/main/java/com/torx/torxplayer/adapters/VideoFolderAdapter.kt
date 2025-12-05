package com.torx.torxplayer.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.torx.torxplayer.R
import com.torx.torxplayer.model.VideoFolder
import java.io.File

class VideoFolderAdapter(private val context: Context,
                         private var folderList: List<VideoFolder>,
                         private val onFolderClick: (VideoFolder) -> Unit
) : RecyclerView.Adapter<VideoFolderAdapter.FolderViewHolder>() {

    inner class FolderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val folderThumbnail: ImageView = itemView.findViewById(R.id.folderThumbnail)
        val folderName: TextView = itemView.findViewById(R.id.folderName)
        val videoCount: TextView = itemView.findViewById(R.id.videoCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_folder, parent, false)
        return FolderViewHolder(view)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        val folder = folderList[position]

        holder.folderName.text = folder.folderName
        holder.videoCount.text = "${folder.videoCount} videos"

        // Load thumbnail using Glide (fast)
        val firstVideoFile = File(folder.folderPath).listFiles()?.firstOrNull {
            it.extension.lowercase() in listOf("mp4", "mkv", "mov", "avi")
        }

        if (firstVideoFile != null) {
            Glide.with(context)
                .load(firstVideoFile.path)
                .centerCrop()
                .placeholder(R.drawable.baseline_video_library_24)
                .into(holder.folderThumbnail)
        }

        holder.itemView.setOnClickListener {
            onFolderClick(folder)
        }
    }

    override fun getItemCount(): Int = folderList.size
}