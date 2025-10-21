package com.torx.torxplayer.adapters

import android.content.ContentUris
import android.content.Context
import android.graphics.BitmapFactory
import android.icu.text.DecimalFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
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
import com.torx.torxplayer.model.Audio
import androidx.core.net.toUri

class AudioAdapter(val context: Context, private var audioList: List<Audio>,
    private val onItemClick: ((Audio) -> Unit)? = null) : RecyclerView.Adapter<AudioAdapter.AudioViewHolder>() {
        
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): AudioViewHolder {

        val view = LayoutInflater.from(context).inflate(R.layout.audio_layout, parent, false)
        return AudioViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: AudioViewHolder,
        position: Int
    ) {
        val audio = audioList[position]

        holder.title.text = audio.title

        holder.duration.text = formatVideoDuration(audio.duration)

        val convertedSize = convertBytesToMB(audio.size.toLong())
        holder.size.text = "$convertedSize MB | "

        // Construct the URI for the album art
        val artworkUri = audio.uri
        Log.e("audio uri", audio.uri.toString())
        Log.e("audio size", audio.size.toString())
        val albumArtUri = ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            audio.albumId.toLong()
        )

//        Glide.with(context)
////            .asBitmap()
//            .load(albumArtUri)
//            .frame(1000000)
//            .transform(CenterCrop(), RoundedCorners(20))
//            .placeholder(R.drawable.outline_video_library_24)
//            .error(R.drawable.outline_video_library_24)
//            .into(holder.thumbnail)

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.parse(audio.uri.toString()))
            val art = retriever.embeddedPicture
            if (art != null) {
                val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size)
                holder.thumbnail.setImageBitmap(bitmap)
            } else {
                // fallback if no embedded art
                holder.thumbnail.setImageResource(R.drawable.outline_library_music_24)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            holder.thumbnail.setImageResource(R.drawable.outline_library_music_24)
        } finally {
            retriever.release()
        }



        holder.itemView.setOnClickListener {
            onItemClick?.invoke(audio)
        }
    }

    fun filterList(filterList: List<Audio>) {
        audioList = filterList
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return audioList.size
    }

    class AudioViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.audioTitle)
        val thumbnail: ImageView = itemView.findViewById(R.id.thumbnail)
        val duration: TextView = itemView.findViewById(R.id.videoDuration)
        val size: TextView = itemView.findViewById(R.id.videoSize)
    }

    private fun formatVideoDuration(durationMillis: Long): String {
        val totalSeconds = durationMillis/1000
        val minutes = totalSeconds/60
        val seconds = totalSeconds%60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun convertBytesToMB(bytes: Long): Double {
        val mb = bytes /(1024.0 * 1024.0)
        val decimalFormat = DecimalFormat("#.##")
        return decimalFormat.format(mb).toDouble()
    }
    
}
