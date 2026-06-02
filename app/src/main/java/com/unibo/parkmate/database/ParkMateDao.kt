package com.unibo.parkmate.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) per l'interazione con il database SQLite sottostante.
 * Fornisce un livello di astrazione (Abstraction Layer) isolando le query SQL dal resto dell'applicazione.
 */
@Dao
interface ParkMateDao {

    // ==========================================
    // TABELLA: VEHICLES
    // ==========================================
    // L'utilizzo di [Flow] implementa il paradigma Observer (Reactive Programming).
    // Ogni modifica alla tabella emette automaticamente un nuovo dato verso il Presentation Layer.
    @Query("SELECT * FROM vehicles")
    fun getAllVehicles(): Flow<List<Vehicle>>

    // L'identificatore [suspend] delega l'operazione di I/O asincrona alle Kotlin Coroutines,
    // garantendo che il Main Thread (UI Thread) non venga mai bloccato.
    @Query("SELECT * FROM vehicles WHERE id = :vehicleId LIMIT 1")
    suspend fun getVehicleById(vehicleId: Int): Vehicle?

    // Implementazione della strategia di Upsert (Update or Insert) per la risoluzione dei conflitti
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicle(vehicle: Vehicle)

    @Update
    suspend fun updateVehicle(vehicle: Vehicle)

    @Delete
    suspend fun deleteVehicle(vehicle: Vehicle)

    // ==========================================
    // TABELLA: PARKING SESSIONS
    // ==========================================
    // Interfaccia Reattiva (UI)
    @Query("SELECT * FROM parking_sessions WHERE endTime IS NULL")
    fun getActiveSessionsFlow(): Flow<List<ParkingSession>>

    @Query("SELECT * FROM parking_sessions WHERE endTime IS NOT NULL ORDER BY endTime DESC")
    fun getPastSessionsFlow(): Flow<List<ParkingSession>>

    // Operazioni Asincrone (Background / Worker)
    // Queste funzioni permettono operazioni transazionali isolate (One-Shot Queries)
    @Query("SELECT * FROM parking_sessions WHERE endTime IS NULL")
    suspend fun getAllActiveSessions(): List<ParkingSession>

    @Query("SELECT * FROM parking_sessions WHERE vehicleId = :vehicleId AND endTime IS NULL LIMIT 1")
    suspend fun getActiveSessionForVehicle(vehicleId: Int): ParkingSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParkingSession(session: ParkingSession)

    @Update
    suspend fun updateParkingSession(session: ParkingSession)

    // ==========================================
    // TABELLA: SAVED LOCATIONS (GEOFENCES)
    // ==========================================
    // Interfaccia Reattiva (UI)
    @Query("SELECT * FROM saved_locations")
    fun getAllSavedLocationsFlow(): Flow<List<SavedLocation>>

    // Operazioni Asincrone (Background / Worker)
    @Query("SELECT * FROM saved_locations")
    suspend fun getAllSavedLocations(): List<SavedLocation>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedLocation(location: SavedLocation)

    @Update
    suspend fun updateSavedLocation(location: SavedLocation)

    @Delete
    suspend fun deleteSavedLocation(location: SavedLocation)
}