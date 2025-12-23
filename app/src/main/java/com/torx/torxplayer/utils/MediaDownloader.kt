package com.torx.torxplayer.utils

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast

object MediaDownloader {

    fun download(
        context: Context,
        url: String,
        fileName: String
    ) {
        if (!url.startsWith("http")) {
            Toast.makeText(context, "Invalid URL", Toast.LENGTH_SHORT).show()
            return
        }

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(fileName)
            .setDescription("Downloading media")
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                fileName
            )

        val downloadManager =
            context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        downloadManager.enqueue(request)

        Toast.makeText(context, "Download started", Toast.LENGTH_SHORT).show()
    }
}
