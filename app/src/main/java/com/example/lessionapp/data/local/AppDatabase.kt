package com.example.lessionapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.lessionapp.data.local.dao.EmgDao
import com.example.lessionapp.data.local.entity.EmgRecordEntity
import com.example.lessionapp.data.local.entity.EmgSessionEntity

/**
 * Clase principal de la base de datos interna Room que gestiona la persistencia de datos.
 */
@Database(
    entities = [EmgRecordEntity::class, EmgSessionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun emgDao(): EmgDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lessionapp_emg_db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
