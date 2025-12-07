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
    val allPlaylistVideos: LiveData<MutableList<VideosModel>> = fileDao.getPlaylistVideo()
    val allHistoryVideos: LiveData<MutableList<VideosModel>> = fileDao.getHistoryVideo()

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

    suspend fun updateVideoIsPlaylist(videoId: Long, isPlaylist: Boolean) {
        fileDao.updateVideoIsPlaylist(videoId, isPlaylist)
    }

    suspend fun updateVideoIsHistory(videoId: Long, isHistory: Boolean) {
        fileDao.updateVideoIsHistory(videoId, isHistory)
    }

    suspend fun clearAllHistory() {
        fileDao.clearAllHistory()
    }

    //////////// Audio Functions ////////////

    // get audios
    val allAudios: LiveData<MutableList<AudiosModel>> = fileDao.getAllAudios()
    val allPublicAudios: LiveData<MutableList<AudiosModel>> = fileDao.getPublicAudio()
    val allPrivateAudios: LiveData<MutableList<AudiosModel>> = fileDao.getPrivateAudio()

    val allUrisLive: LiveData<List<String>> = fileDao.getAllUrisLive()

    suspend fun insertAudio(audio: AudiosModel) {
        fileDao.insertAudio(audio)
    }

    suspend fun insertAllAudios(audios: MutableList<AudiosModel>) {
        fileDao.insertAllAudios(audios)
    }

    suspend fun updateAudioIsPrivate(audioId: Long, isPrivate: Boolean) {
        fileDao.updateAudioIsPrivate(audioId, isPrivate)
    }

    suspend fun deleteAudio(audio: AudiosModel) {
        fileDao.deleteAudio(audio)
    }

    suspend fun deleteAudioById(id: Long) {
        fileDao.deleteAudioById(id)
    }

    suspend fun deleteAudioByUri(uri: String) {
        fileDao.deleteAudioByUri(uri)
    }


    suspend fun getAudioById(id: Long): AudiosModel? {
        return fileDao.getAudioById(id)
    }

    suspend fun clearAllAudios() {
        fileDao.clearAllAudios()
    }


}