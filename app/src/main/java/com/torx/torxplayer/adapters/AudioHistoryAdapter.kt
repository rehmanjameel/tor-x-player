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
import com.torx.torxplayer.model.AudiosModel

class AudioHistoryAdapter(val context: Context, var audios: MutableList<AudiosModel>,
                          val onItemClick: (position: Int) -> Unit)
    : RecyclerView.Adapter<AudioHistoryAdapter.AudioViewHolder>(){
    class AudioViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val thumbnail = itemView.findViewById<ImageView>(R.id.thumbnail)
        val duration = itemView.findViewById<TextView>(R.id.videoDuration)
        val title = itemView.findViewById<TextView>(R.id.historyFileTitle)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): AudioViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.history_layout, parent, false)
        return AudioViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: AudioViewHolder,
        position: Int
    ) {
        val audio = audios[position]

        holder.duration.text = formatVideoDuration(audio.duration)
        holder.title.text = audio.title

        Glide.with(context)
            .asBitmap()
            .load(audio.uri)
            .frame(1000000)
            .transform(CenterCrop(), RoundedCorners(20))
            .error(R.drawable.audio_thumbnail)
            .into(holder.thumbnail)

        holder.itemView.setOnClickListener {
            onItemClick(position)
        }
    }

    override fun getItemCount(): Int {
        return audios.size
    }

    private fun formatVideoDuration(durationMillis: Long): String {
        val totalSeconds = durationMillis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
}