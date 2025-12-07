package com.torx.torxplayer.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.torx.torxplayer.R
import com.torx.torxplayer.model.VideosModel

class VideoHistoryAdapter(val context: Context, var videos: MutableList<VideosModel>,
    val onItemClick: (position: Int) -> Unit)
    : RecyclerView.Adapter<VideoHistoryAdapter.VideoViewHolder>() {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): VideoViewHolder {

        val view = LayoutInflater.from(context).inflate(R.layout.history_layout, parent, false)
        return VideoViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: VideoViewHolder,
        position: Int
    ) {
        val video = videos[position]

        holder.duration.text = formatVideoDuration(video.duration)

        Glide.with(context)
            .asBitmap()
            .load(video.contentUri)
            .frame(1000000)
            .transform(CenterCrop(), RoundedCorners(20))
            .error(R.drawable.baseline_video_library_24)
            .into(holder.thumbnail)

        holder.itemView.setOnClickListener {
            onItemClick(position)
        }

    }

    override fun getItemCount(): Int {
        return videos.size
    }

    class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val thumbnail = itemView.findViewById<ImageView>(R.id.thumbnail)
        val duration = itemView.findViewById<TextView>(R.id.videoDuration)

    }

    private fun formatVideoDuration(durationMillis: Long): String {
        val totalSeconds = durationMillis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
}