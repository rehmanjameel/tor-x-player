package com.torx.torxplayer.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.torx.torxplayer.filesdb.AppDatabase
import com.torx.torxplayer.model.AudiosModel
import com.torx.torxplayer.model.VideosModel
import com.torx.torxplayer.repositories.FileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URI

class FilesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FileRepository
    val allVideos: LiveData<MutableList<VideosModel>>
    val allPublicVideos: LiveData<MutableList<VideosModel>>
    val allPrivateVideos: LiveData<MutableList<VideosModel>>
    val allPlaylistVideos: LiveData<MutableList<VideosModel>>
    val allHistoryVideos: LiveData<MutableList<VideosModel>>


    val allAudios: LiveData<MutableList<AudiosModel>>
    val allPublicAudios: LiveData<MutableList<AudiosModel>>
    val allPrivateAudios: LiveData<MutableList<AudiosModel>>
    val allPlaylistAudios: LiveData<MutableList<AudiosModel>>
    val allHistoryAudios: LiveData<MutableList<AudiosModel>>
    val filesDao = AppDatabase.getDatabase(application).fileDao()
    var allUrisLive: LiveData<List<String>>

    init {
        repository = FileRepository(filesDao)
        allVideos = repository.allVideos
        allAudios = repository.allAudios
        allPublicVideos = repository.allPublicVideos
        allPrivateVideos = repository.allPrivateVideos
        allPlaylistVideos = repository.allPlaylistVideos
        allHistoryVideos = repository.allHistoryVideos
        
        allPublicAudios = repository.allPublicAudios
        allPrivateAudios = repository.allPrivateAudios
        allPlaylistAudios = repository.allPlaylistAudios
        allHistoryAudios = repository.allHistoryAudios

        allUrisLive = repository.allUrisLive
    }

    fun insertVideo(video: VideosModel) = viewModelScope.launch(Dispatchers.IO) {
        repository.insertVideos(video)
    }

    suspend fun getVideoCount(): Int = filesDao.getVideoCount()
    suspend fun getAudioCount(): Int = filesDao.getAudioCount()

    fun insertAllVideos(videos: MutableList<VideosModel>) = viewModelScope.launch(Dispatchers.IO) {
        repository.insertAllVideos(videos)
    }

    fun deleteVideos(video: VideosModel) = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteVideo(video)
    }

    fun deleteVideosById(id: Long) = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteVideoById(id)
    }

    fun deleteVideosByUri(uri: String) = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteVideoByUri(uri)
    }

    fun updateVideoIsPrivate(videoId: Long, isPrivate: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        repository.updateVideoIsPrivate(videoId, isPrivate)
    }

    fun updateVideoIsPlaylist(videoId: Long, isPlaylist: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        repository.updateVideoIsPlaylist(videoId, isPlaylist)
    }

    fun updateVideoIsHistory(videoId: Long, isHistory: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        repository.updateVideoIsHistory(videoId, isHistory)
    }

    fun clearAllHistory() = viewModelScope.launch(Dispatchers.IO) {
        repository.clearAllHistory()
    }

    fun clearAll() = viewModelScope.launch(Dispatchers.IO) {
        repository.clearAllVideos()
    }

    ///////// Audios functions /////////

    fun insertAudio(audio: AudiosModel) = viewModelScope.launch(Dispatchers.IO) {
        repository.insertAudio(audio)
    }

    fun insertAllAudios(audios: MutableList<AudiosModel>) = viewModelScope.launch(Dispatchers.IO) {
        repository.insertAllAudios(audios)
    }

    fun deleteAudios(audio: AudiosModel) = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteAudio(audio)
    }

    fun deleteAudiosById(id: Long) = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteAudioById(id)
    }

    fun updateAudioIsPrivate(audioId: Long, isPrivate: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        repository.updateAudioIsPrivate(audioId, isPrivate)
    }

    fun deleteAudiosByUri(uri: String) = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteAudioByUri(uri)
    }

    fun clearAllAudios() = viewModelScope.launch(Dispatchers.IO) {
        repository.clearAllAudios()
    }

    //new update
    fun updateAudioIsPlaylist(audioId: Long, isPlaylist: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        repository.updateAudioIsPlaylist(audioId, isPlaylist)
    }

    fun updateAudioIsHistory(audioId: Long, isHistory: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        repository.updateAudioIsHistory(audioId, isHistory)
    }

    fun clearAllAudioHistory() = viewModelScope.launch(Dispatchers.IO) {
        repository.clearAllAudioHistory()
    }
}