package com.torx.torxplayer.filesdb

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.torx.torxplayer.model.AudiosModel
import com.torx.torxplayer.model.VideosModel

@Database(entities = [VideosModel::class, AudiosModel::class], version = 1, exportSchema = false)
abstract class AppDatabase: RoomDatabase() {

    // create abstract fun for each dao
    abstract fun fileDao() : FileDao

    companion object {

        // create the database instance
        private var INSTANCE: AppDatabase? = null

        // create fun to get the database
        fun getDatabase(context: Context): AppDatabase {
            // return the instance if it is not null else create a new instance
            return INSTANCE ?: synchronized(this) {
                // create the database instance if it is null else return the existing instance
                // databasebuilder will create the database if it does not exist
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "media_database"
                ).build()
                // return the instance
                INSTANCE = instance
                instance
            }
        }
    }
}