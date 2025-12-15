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
import com.torx.torxplayer.model.AudioFolderModel
import java.io.File

class AudioFolderAdapter(
    private val context: Context,
    private var folderList: List<AudioFolderModel>,
    private val onClick: (AudioFolderModel) -> Unit
) : RecyclerView.Adapter<AudioFolderAdapter.FolderViewHolder>() {

    inner class FolderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
//        val folderThumbnail: ImageView = itemView.findViewById(R.id.folderThumbnail)
        val folderName = itemView.findViewById<TextView>(R.id.folderName)
        val audioCount = itemView.findViewById<TextView>(R.id.audioCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.row_audio_folder, parent, false)
        return FolderViewHolder(view)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        val folder = folderList[position]

        holder.folderName.text = folder.folderName
        holder.audioCount.text = "${folder.audioCount} files"

        // Load thumbnail using Glide (fast)
        val firstVideoFile = File(folder.folderPath).listFiles()?.firstOrNull {
            it.extension.lowercase() in listOf("mp4", "mkv", "mov", "avi")
        }

//        if (firstVideoFile != null) {
//            Glide.with(context)
//                .load(firstVideoFile.path)
//                .centerCrop()
//                .placeholder(R.drawable.baseline_video_library_24)
//                .into(holder)
//        }

        holder.itemView.setOnClickListener { onClick(folder) }
    }

    override fun getItemCount(): Int = folderList.size
}
