package com.example.lessionapp.data.repository

import com.example.lessionapp.data.local.dao.EmgDao
import com.example.lessionapp.data.local.entity.EmgRecordEntity
import com.example.lessionapp.data.local.entity.EmgSessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repositorio de datos que provee una interfaz limpia para interactuar con la base de datos interna.
 */
class EmgRepository(private val emgDao: EmgDao) {

    // Flujos de datos en tiempo real
    val allSessions: Flow<List<EmgSessionEntity>> = emgDao.getAllSessions()
    val recentRecords: Flow<List<EmgRecordEntity>> = emgDao.getRecentRecords()
    val latestSession: Flow<EmgSessionEntity?> = emgDao.getLatestSession()

    fun getRecordsForSession(sessionId: Long): Flow<List<EmgRecordEntity>> {
        return emgDao.getRecordsForSession(sessionId)
    }

    fun getSessionById(sessionId: Long): Flow<EmgSessionEntity?> {
        return emgDao.getSessionById(sessionId)
    }

    // Operaciones de escritura (suspending)
    suspend fun insertSession(session: EmgSessionEntity): Long {
        return emgDao.insertSession(session)
    }

    suspend fun updateSession(session: EmgSessionEntity) {
        emgDao.updateSession(session)
    }

    suspend fun deleteSession(session: EmgSessionEntity) {
        emgDao.deleteSessionWithRecords(session)
    }

    suspend fun insertRecord(record: EmgRecordEntity): Long {
        return emgDao.insertRecord(record)
    }

    suspend fun insertRecords(records: List<EmgRecordEntity>) {
        emgDao.insertRecords(records)
    }

    suspend fun clearAllData() {
        emgDao.deleteAllRecords()
        emgDao.deleteAllSessions()
    }
}
