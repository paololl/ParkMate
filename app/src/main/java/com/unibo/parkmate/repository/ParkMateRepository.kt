package com.unibo.parkmate.repository

import com.unibo.parkmate.database.ParkMateDao
import com.unibo.parkmate.database.ParkingSession
import com.unibo.parkmate.database.SavedLocation
import com.unibo.parkmate.database.Vehicle
import kotlinx.coroutines.flow.Flow

/**
 * Implementazione del [Repository Pattern].
 * Questa classe funge da intermediario (Abstraction Layer) tra la sorgente dati locale (Room DAO)
 * e il Domain Layer (ViewModel).
 * * ARCHITETTURA: Garantisce il principio della "Single Source of Truth" (SSOT) e la
 * Separation of Concerns (SoC). Il ViewModel non deve conoscere l'implementazione
 * del database, ma interroga esclusivamente questo Repository.
 * * @property dao Il Data Access Object iniettato a costruttore. Questa iniezione
 * delle dipendenze (Dependency Injection) facilita enormemente il testing (es. mocking del DAO).
 */
class ParkMateRepository(private val dao: ParkMateDao) {

    // ==========================================
    // GESTIONE VEICOLI
    // ==========================================
    /**
     * Espone un flusso reattivo (Reactive Stream) di tutti i veicoli.
     * Sfruttando i [Flow] di Kotlin Coroutines, il Repository implementa il pattern Observer:
     * ogni mutamento nella tabella "vehicles" viene emesso a valle automaticamente.
     */
    val allVehicles: Flow<List<Vehicle>> = dao.getAllVehicles()

    suspend fun insertVehicle(vehicle: Vehicle) = dao.insertVehicle(vehicle)
    suspend fun updateVehicle(vehicle: Vehicle) = dao.updateVehicle(vehicle)
    suspend fun deleteVehicle(vehicle: Vehicle) = dao.deleteVehicle(vehicle)

    // ==========================================
    // GESTIONE SOSTE (PARKING SESSIONS)
    // ==========================================
    // Flussi reattivi per mantenere l'interfaccia utente (Live Map e History) costantemente
    // sincronizzata con lo stato del database senza necessità di polling manuale.
    val activeSessions: Flow<List<ParkingSession>> = dao.getActiveSessionsFlow()
    val pastSessions: Flow<List<ParkingSession>> = dao.getPastSessionsFlow()

    suspend fun getActiveSessionForVehicle(vehicleId: Int): ParkingSession? = dao.getActiveSessionForVehicle(vehicleId)
    suspend fun insertParkingSession(session: ParkingSession) = dao.insertParkingSession(session)
    suspend fun updateParkingSession(session: ParkingSession) = dao.updateParkingSession(session)

    // ==========================================
    // GESTIONE ZONE SALVATE (GEOFENCES)
    // ==========================================
    /**
     * Flusso reattivo delle coordinate geospaziali aziendali (Geofences).
     * Ottimizza i calcoli di intersezione geometrica mantenendo una cache sempre aggiornata in RAM.
     */
    val savedLocations: Flow<List<SavedLocation>> = dao.getAllSavedLocationsFlow()

    suspend fun getAllSavedLocations(): List<SavedLocation> = dao.getAllSavedLocations()
    suspend fun insertSavedLocation(location: SavedLocation) = dao.insertSavedLocation(location)
    suspend fun updateSavedLocation(location: SavedLocation) = dao.updateSavedLocation(location)
    suspend fun deleteSavedLocation(location: SavedLocation) = dao.deleteSavedLocation(location)
}