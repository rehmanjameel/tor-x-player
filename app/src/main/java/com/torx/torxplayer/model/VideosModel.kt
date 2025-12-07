package com.torx.torxplayer.model

import android.graphics.Bitmap
import android.net.Uri
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "video")
class VideosModel (
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "content_uri")
    val contentUri: String,
//    @ColumnInfo(name = "thumbnail")
//    val thumbnail: Bitmap? = null,
    @ColumnInfo(name = "date_added")
    val dateAdded: Long,
    @ColumnInfo(name = "mime_type")
    val mimeType: String,
    @ColumnInfo(name = "duration")
    val duration: Long,
    @ColumnInfo(name = "folder_name")
    val folderName: String,
    @ColumnInfo(name = "size")
    val size: String,
    @ColumnInfo(name = "path")
    val path: String,
    @ColumnInfo(name = "is_private")
    var isPrivate: Boolean = false,

    @ColumnInfo(name = "is_playlist")
    var isPlaylist: Boolean = false,

    @ColumnInfo(name = "is_history")
    var isHistory: Boolean = false,

)
