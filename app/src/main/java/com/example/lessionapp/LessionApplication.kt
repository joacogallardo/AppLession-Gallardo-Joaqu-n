package com.example.lessionapp

import android.app.Application
import com.example.lessionapp.data.local.AppDatabase
import com.example.lessionapp.data.repository.EmgRepository

/**
 * Clase Application para inicializar la base de datos interna y el repositorio en el ciclo de vida global de la app.
 */
class LessionApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    val repository: EmgRepository by lazy { EmgRepository(database.emgDao()) }
}
