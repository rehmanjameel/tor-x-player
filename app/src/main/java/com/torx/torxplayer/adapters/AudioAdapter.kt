package com.torx.torxplayer.adapters

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.core.net.toUri
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.google.android.material.card.MaterialCardView
import com.torx.torxplayer.R
import com.torx.torxplayer.model.AudiosModel
import com.torx.torxplayer.interfaces.OptionsMenuClickListener

class AudioAdapter(val context: Context, private var audioList: MutableList<AudiosModel>,
                   private var optionsMenuClickListener: OptionsMenuClickListener
) : RecyclerView.Adapter<AudioAdapter.AudioViewHolder>() {

    var isSelectionMode = false
    val selectedItems = mutableSetOf<Int>()
    val currentList: List<AudiosModel>
        get() = audioList

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

        val convertedSize = convertBytesToMB(audio.size)
        holder.size.text = "$convertedSize MB | "

        Log.e("audio uri", audio.uri.toString())

        // Try to load album art from MediaStore
//        val albumArtUri = ContentUris.withAppendedId(
//            "content://media/external/audio/albumart".toUri(),
//            audio.albumId.toLong()
//        )

        Glide.with(context)
            .asBitmap()
            .load(audio.uri)
            .transform(CenterCrop(), RoundedCorners(20))
            .error(R.drawable.audio_thumbnail)
            .into(holder.thumbnail)

        // If album art not available, fallback to embedded art
        holder.thumbnail.post {
            if (holder.thumbnail.drawable == null ||
                (holder.thumbnail.drawable as? BitmapDrawable)?.bitmap == null
            ) {
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(context, audio.uri.toUri())
                    val art = retriever.embeddedPicture
                    if (art != null) {
                        val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size)
                        holder.thumbnail.setImageBitmap(bitmap)
                    } else {
                        holder.thumbnail.setImageResource(R.drawable.audio_thumbnail)
                    }
                    retriever.release()
                } catch (e: Exception) {
                    holder.thumbnail.setImageResource(R.drawable.audio_thumbnail)
                }
            }
        }

        holder.moreOptions.setOnClickListener {
            optionsMenuClickListener.onOptionsMenuClicked(position, it)
        }

        holder.checkBox.visibility = if (isSelectionMode) View.VISIBLE else View.INVISIBLE
        holder.checkBox.setOnCheckedChangeListener(null)
        holder.checkBox.isChecked = selectedItems.contains(position)
        holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) selectedItems.add(position) else selectedItems.remove(position)
            optionsMenuClickListener.onSelectionChanged(selectedItems.size)
        }

        holder.cardItem.setOnClickListener {
            if (isSelectionMode) {
                toggleSelection(position)
            } else {
                optionsMenuClickListener.onItemClick(position)
            }
        }

        holder.cardItem.setOnLongClickListener {
            if (!isSelectionMode) optionsMenuClickListener.onLongItemClick(position)
            true
        }
    }

    fun filterList(filterList: MutableList<AudiosModel>) {
        audioList = filterList
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return audioList.size
    }

    private fun toggleSelection(position: Int) {
        if (selectedItems.contains(position)) selectedItems.remove(position)
        else selectedItems.add(position)
        notifyItemChanged(position)
        optionsMenuClickListener.onSelectionChanged(selectedItems.size)
    }

    class AudioViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.audioTitle)
        val thumbnail: ImageView = itemView.findViewById(R.id.audioThumbnail)
        val moreOptions: ImageView = itemView.findViewById(R.id.audioMoreOptionsIcon)
        val duration: TextView = itemView.findViewById(R.id.audioDuration)
        val size: TextView = itemView.findViewById(R.id.audioSize)
        val cardItem: MaterialCardView = itemView.findViewById(R.id.audioCardLayout)

        val checkBox: CheckBox = itemView.findViewById(R.id.audioCheckBox)
    }

    private fun formatVideoDuration(durationMillis: Long): String {
        val totalSeconds = durationMillis/1000
        val minutes = totalSeconds/60
        val seconds = totalSeconds%60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun convertBytesToMB(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return String.format("%.2f", mb)
    }

}
