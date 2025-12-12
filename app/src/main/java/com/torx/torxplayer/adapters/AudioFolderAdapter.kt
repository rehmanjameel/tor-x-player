package com.torx.torxplayer.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.torx.torxplayer.R
import com.torx.torxplayer.model.AudioFolderModel

class AudioFolderAdapter(
    private var folderList: List<AudioFolderModel>,
    private val onClick: (AudioFolderModel) -> Unit
) : RecyclerView.Adapter<AudioFolderAdapter.FolderViewHolder>() {

    inner class FolderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
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
        holder.audioCount.text = "${folder.audioList.size} files"

        holder.itemView.setOnClickListener { onClick(folder) }
    }

    override fun getItemCount(): Int = folderList.size
}
