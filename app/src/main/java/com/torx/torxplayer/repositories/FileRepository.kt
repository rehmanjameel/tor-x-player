package com.torx.torxplayer.repositories

import android.net.Uri
import androidx.lifecycle.LiveData
import com.torx.torxplayer.filesdb.FileDao
import com.torx.torxplayer.model.AudiosModel
import com.torx.torxplayer.model.VideosModel
import java.net.URI

class FileRepository(private val fileDao: FileDao) {
    // get videos
    val allVideos: LiveData<MutableList<VideosModel>> = fileDao.getAllVideos()

    val allPublicVideos: LiveData<MutableList<VideosModel>> = fileDao.getPublicVideo()

    val allPrivateVideos: LiveData<MutableList<VideosModel>> = fileDao.getPrivateVideo()

    suspend fun insertVideos(video: VideosModel) {
        fileDao.insertVideo(video)
    }

    suspend fun insertAllVideos(videos: MutableList<VideosModel>) {
        fileDao.insertAll(videos)
    }

    suspend fun deleteVideo(video: VideosModel) {
        fileDao.deleteVideo(video)
    }

    suspend fun deleteVideoById(id: Long) {
        fileDao.deleteById(id)
    }

    suspend fun deleteVideoByUri(uri: String) {
        fileDao.deleteByUri(uri)
    }

    suspend fun getVideoById(id: Long): VideosModel? {
        return fileDao.getVideoById(id)
    }

    suspend fun clearAllVideos() {
        fileDao.clearAll()
    }

    suspend fun updateVideoIsPrivate(videoId: Long, isPrivate: Boolean) {
        fileDao.updateVideoIsPrivate(videoId, isPrivate)
    }

    //////////// Audio Functions ////////////

    // get audios
    val allAudios: LiveData<MutableList<AudiosModel>> = fileDao.getAllAudios()

    suspend fun insertAudio(audio: AudiosModel) {
        fileDao.insertAudio(audio)
    }

    suspend fun insertAllAudios(audios: MutableList<AudiosModel>) {
        fileDao.insertAllAudios(audios)
    }

    suspend fun deleteAudio(audio: AudiosModel) {
        fileDao.deleteAudio(audio)
    }

    suspend fun deleteAudioById(id: Long) {
        fileDao.deleteAudioById(id)
    }

    suspend fun getAudioById(id: Long): AudiosModel? {
        return fileDao.getAudioById(id)
    }

    suspend fun clearAllAudios() {
        fileDao.clearAllAudios()
    }


}