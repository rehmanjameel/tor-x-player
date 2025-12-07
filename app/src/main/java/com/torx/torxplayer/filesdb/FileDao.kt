package com.torx.torxplayer.filesdb

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.torx.torxplayer.model.AudiosModel
import com.torx.torxplayer.model.VideosModel
import java.net.URI

@Dao
interface FileDao {

    //////////// video queries ////////////

    // get videos from database
    @Query("Select * From video Order by date_added DESC")
    fun getAllVideos(): LiveData<MutableList<VideosModel>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVideo(video: VideosModel)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(videos: MutableList<VideosModel>)
    @Query("SELECT COUNT(*) FROM video")
    suspend fun getVideoCount(): Int

    @Query("SELECT COUNT(*) FROM audio")
    suspend fun getAudioCount(): Int
    @Delete
    suspend fun deleteVideo(video: VideosModel)

    @Query("DELETE FROM video WHERE id = :videoId")
    suspend fun deleteById(videoId: Long)

    @Query("DELETE FROM video WHERE content_uri = :uri")
    suspend fun deleteByUri(uri: String)

    @Query("Update video SET is_private = :isPrivate WHERE id = :videoId")
    suspend fun updateVideoIsPrivate(videoId: Long, isPrivate: Boolean)

    @Query("Update video SET is_playlist = :isPlaylist WHERE id = :videoId")
    suspend fun updateVideoIsPlaylist(videoId: Long, isPlaylist: Boolean)

    @Query("Update video SET is_history = :isHistory WHERE id = :videoId")
    suspend fun updateVideoIsHistory(videoId: Long, isHistory: Boolean)

    @Query("UPDATE video SET is_history = 0 WHERE is_history = 1")
    suspend fun clearAllHistory()

    @Query("SELECT * FROM video WHERE is_private = 1  Order by date_added DESC")
    fun getPrivateVideo(): LiveData<MutableList<VideosModel>>

    @Query("SELECT * FROM video WHERE is_playlist = 1  Order by date_added DESC")
    fun getPlaylistVideo(): LiveData<MutableList<VideosModel>>

    @Query("SELECT * FROM video WHERE is_history = 1  Order by date_added DESC")
    fun getHistoryVideo(): LiveData<MutableList<VideosModel>>

    @Query("SELECT * FROM video WHERE is_private = 0  Order by date_added DESC")
    fun getPublicVideo(): LiveData<MutableList<VideosModel>>

    @Query("SELECT * FROM video WHERE id = :videoId LIMIT 1")
    suspend fun getVideoById(videoId: Long): VideosModel?

    @Query("DELETE FROM video")
    suspend fun clearAll()


    //////////// Audio Queries ////////////

    // get audios from database
    @Query("Select * From audio")
    fun getAllAudios(): LiveData<MutableList<AudiosModel>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAudio(audio: AudiosModel)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllAudios(audios: MutableList<AudiosModel>)

    @Delete
    suspend fun deleteAudio(audio: AudiosModel)

    @Query("DELETE FROM audio WHERE id = :audioId")
    suspend fun deleteAudioById(audioId: Long)

    @Query("DELETE FROM audio WHERE uri = :audioUri")
    suspend fun deleteAudioByUri(audioUri: String)

    @Query("SELECT * FROM audio WHERE id = :audioId LIMIT 1")
    suspend fun getAudioById(audioId: Long): AudiosModel?

    @Query("SELECT uri FROM audio")
    fun getAllUrisLive(): LiveData<List<String>>

    @Query("Update audio SET is_private = :isPrivate WHERE id = :audioId")
    suspend fun updateAudioIsPrivate(audioId: Long, isPrivate: Boolean)

    @Query("SELECT * FROM audio WHERE is_private = 1  Order by id DESC")
    fun getPrivateAudio(): LiveData<MutableList<AudiosModel>>

    @Query("SELECT * FROM audio WHERE is_private = 0  Order by id DESC")
    fun getPublicAudio(): LiveData<MutableList<AudiosModel>>

    @Query("DELETE FROM audio")
    suspend fun clearAllAudios()

}
