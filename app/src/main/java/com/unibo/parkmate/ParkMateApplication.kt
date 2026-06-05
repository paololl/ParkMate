package com.unibo.parkmate

import android.app.Application
import com.unibo.parkmate.database.AppDatabase
import com.unibo.parkmate.repository.ParkMateRepository

class ParkMateApplication : Application() {

    // Usiamo "by lazy" in modo che vengano creati solo quando servono davvero,
    // risparmiando memoria all'avvio dell'app.
    val database   by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { ParkMateRepository(database.parkMateDao()) }
}