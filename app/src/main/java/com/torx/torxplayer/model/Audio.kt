package com.torx.torxplayer.model

import android.net.Uri

class Audio(

    val id: Long,
    val title: String,
    val artist: String?,
    val album: String?,
    val duration: Long,
    val uri: Uri,
    val path: Int,
    val size: Int,
    val albumId: Int
)
