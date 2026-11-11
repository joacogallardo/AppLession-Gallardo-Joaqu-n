package com.example.lessionapp.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.lessionapp.data.local.entity.EmgRecordEntity
import com.example.lessionapp.data.local.entity.EmgSessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Objeto de Acceso a Datos (DAO) para realizar operaciones CRUD en la base de datos interna.
 */
@Dao
interface EmgDao {

    // --- OPERACIONES SOBRE REGISTROS INDIVIDUALES (EmgRecordEntity) ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: EmgRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecords(records: List<EmgRecordEntity>)

    @Query("SELECT * FROM emg_records WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getRecordsForSession(sessionId: Long): Flow<List<EmgRecordEntity>>

    @Query("SELECT * FROM emg_records ORDER BY timestamp DESC LIMIT 100")
    fun getRecentRecords(): Flow<List<EmgRecordEntity>>

    @Query("DELETE FROM emg_records WHERE sessionId = :sessionId")
    suspend fun deleteRecordsForSession(sessionId: Long)

    // --- OPERACIONES SOBRE SESIONES DE MEDICIÓN (EmgSessionEntity) ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: EmgSessionEntity): Long

    @Update
    suspend fun updateSession(session: EmgSessionEntity)

    @Delete
    suspend fun deleteSession(session: EmgSessionEntity)

    @Transaction
    suspend fun deleteSessionWithRecords(session: EmgSessionEntity) {
        deleteRecordsForSession(session.sessionId)
        deleteSession(session)
    }

    @Query("SELECT * FROM emg_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<EmgSessionEntity>>

    @Query("SELECT * FROM emg_sessions WHERE sessionId = :sessionId LIMIT 1")
    fun getSessionById(sessionId: Long): Flow<EmgSessionEntity?>

    @Query("SELECT * FROM emg_sessions ORDER BY startTime DESC LIMIT 1")
    fun getLatestSession(): Flow<EmgSessionEntity?>

    @Query("DELETE FROM emg_sessions")
    suspend fun deleteAllSessions()

    @Query("DELETE FROM emg_records")
    suspend fun deleteAllRecords()
}
