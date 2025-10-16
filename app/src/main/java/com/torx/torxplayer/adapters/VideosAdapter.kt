package com.torx.torxplayer.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.torx.torxplayer.R
import com.torx.torxplayer.model.Video

class VideosAdapter(val context: Context, val videos: List<Video>) :
    RecyclerView.Adapter<VideosAdapter.VideoViewHolder>() {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): VideoViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.video_layout, parent, false)
        return VideoViewHolder(view)

    }

    override fun onBindViewHolder(
        holder: VideoViewHolder,
        position: Int
    ) {

        val item = videos[position]
        holder.title.text = item.title
        holder.thumbnail.setImageBitmap(item.thumbnail)

    }

    override fun getItemCount(): Int {
        return videos.size
    }

    class VideoViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {

        val title: TextView = itemView.findViewById(R.id.title)
        val thumbnail: ImageView = itemView.findViewById(R.id.thumbnail)

    }
}