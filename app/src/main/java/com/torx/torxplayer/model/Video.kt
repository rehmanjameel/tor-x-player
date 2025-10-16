package com.torx.torxplayer.model

import android.graphics.Bitmap
import android.net.Uri

class Video (
    val id: Long,
    val title: String,
    val contentUri: Uri,
    val thumbnail: Bitmap?,
    val dateAdded: Long,
    val mimeType: String,
    val duration: Long,
    val folderName: String,
    val size: String,
    val path: String
)