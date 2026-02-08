package com.torx.torxplayer.services

import com.torx.torxplayer.model.VideosModel

object PlaybackQueue {

    // Ordered list (single source of truth)
    var videos: List<VideosModel> = emptyList()

    // 🔥 SINGLE source of current position
    var currentIndex: Int = -1

    fun isValid(): Boolean {
        return videos.isNotEmpty() && currentIndex in videos.indices
    }

    fun current(): VideosModel {
        return videos[currentIndex]
    }

    fun nextIndex(): Int {
        return (currentIndex + 1) % videos.size
    }

    fun previousIndex(): Int {
        return if (currentIndex - 1 < 0) videos.size - 1 else currentIndex - 1
    }
}
