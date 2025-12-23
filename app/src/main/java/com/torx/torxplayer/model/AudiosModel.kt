package com.torx.torxplayer.model

import android.net.Uri
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audio")
class AudiosModel(

    @PrimaryKey(autoGenerate = true)
    val id: Long,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "artist")
    val artist: String?,
    @ColumnInfo(name = "album")
    val album: String?,
    @ColumnInfo(name = "duration")
    val duration: Long,
    @ColumnInfo(name = "uri")
    val uri: String,
    @ColumnInfo(name = "path")
    val path: String?,
    @ColumnInfo(name = "size")
    val size: Long,
    @ColumnInfo(name = "album_id")
    val albumId: Long,
    @ColumnInfo(name = "is_private")
    var isPrivate: Boolean = false,

    @ColumnInfo(name = "is_playlist")
    var isPlaylist: Boolean = false,

    @ColumnInfo(name = "is_history")
    var isHistory: Boolean = false,

    @ColumnInfo(name = "private_path")
    var privatePath: String? = null,

    @ColumnInfo(name = "mime_type")
    val mimeType: String,

    @ColumnInfo(name = "folder_name")
    val folderName: String

)
