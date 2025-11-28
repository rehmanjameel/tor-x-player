package com.torx.torxplayer.adapters

import android.content.Context
import android.icu.text.DecimalFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.torx.torxplayer.R
import com.torx.torxplayer.model.VideosModel
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.google.android.material.card.MaterialCardView
import com.torx.torxplayer.OptionsMenuClickListener

class VideosAdapter(
    val context: Context, var videos: MutableList<VideosModel>,
    private val onOptionsMenuClickListener: OptionsMenuClickListener
) :
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

        val video = videos[position]
        holder.title.text = video.title

        holder.duration.text = formatVideoDuration(video.duration)

        val convertedSize = convertBytesToMB(video.size.toLongOrNull() ?: 0L)
        holder.size.text = "$convertedSize MB | "

        Glide.with(context)
            .asBitmap()
            .load(video.contentUri)
            .frame(1000000)
            .transform(CenterCrop(), RoundedCorners(20))
            .placeholder(R.drawable.baseline_video_library_24)
            .error(R.drawable.baseline_video_library_24)
            .into(holder.thumbnail)


        holder.moreOptions.setOnClickListener {
            onOptionsMenuClickListener.onOptionsMenuClicked(position, it)
        }

        holder.cardItem.setOnClickListener {
            onOptionsMenuClickListener.onItemClick(position)
        }

        holder.cardItem.setOnLongClickListener {
            onOptionsMenuClickListener.onLongItemClick(position)
            true
        }

    }

    fun filterList(filterList: MutableList<VideosModel>) {
        videos = filterList
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return videos.size
    }

    class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        val title: TextView = itemView.findViewById(R.id.videoTitle)
        val thumbnail: ImageView = itemView.findViewById(R.id.thumbnail)

        val moreOptions: ImageView = itemView.findViewById(R.id.videoMoreOptionsIcon)

        val duration: TextView = itemView.findViewById(R.id.videoDuration)
        val size: TextView = itemView.findViewById(R.id.videoSize)
        val cardItem: MaterialCardView = itemView.findViewById(R.id.videoCardLayout)

    }

    private fun formatVideoDuration(durationMillis: Long): String {
        val totalSeconds = durationMillis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun convertBytesToMB(bytes: Long): Double {
        val mb = bytes / (1024.0 * 1024.0)
        val decimalFormat = DecimalFormat("#.##")
        return decimalFormat.format(mb).toDouble()
    }
}
