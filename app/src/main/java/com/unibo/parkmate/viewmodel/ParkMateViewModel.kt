package com.unibo.parkmate.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.unibo.parkmate.database.ParkingSession
import com.unibo.parkmate.database.SavedLocation
import com.unibo.parkmate.database.Vehicle
import com.unibo.parkmate.repository.ParkMateRepository
import com.unibo.parkmate.services.OngoingParkingWorker
import com.unibo.parkmate.services.BASE_ID_HOURLY
import com.unibo.parkmate.ui.theme.ThemePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class ParkMateViewModel(
    application: Application,
    private val repository: ParkMateRepository
) : AndroidViewModel(application) {

    // ==========================================
    // FLUSSI REATTIVI (STATE FLOWS PER LA UI)
    // ==========================================
    val vehicles: StateFlow<List<Vehicle>> = repository.allVehicles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeSessions: StateFlow<List<ParkingSession>> = repository.activeSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pastSessions: StateFlow<List<ParkingSession>> = repository.pastSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedLocations: StateFlow<List<SavedLocation>> = repository.savedLocations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ==========================================
    // GESTIONE TEMA (CHIARO / SCURO)
    // ==========================================
    // ThemePreferences legge/scrive il DataStore "parkmate_settings".
    // Usiamo application come Context in modo da non dipendere da un Context
    // legato al ciclo di vita di una singola Activity (che potrebbe essere
    // ricreata per rotazione schermo o cambio configurazione).
    private val themePreferences = ThemePreferences(application)

    /**
     * StateFlow reattivo della preferenza tema. Partenza con false (tema chiaro)
     * per i nuovi utenti; viene immediatamente sovrascritto dal valore su disco
     * alla prima emissione del Flow del DataStore.
     *
     * WhileSubscribed(5000) mantiene il Flow attivo per 5 secondi dopo che
     * l'ultimo osservatore si disconnette, evitando riletture costose del DataStore
     * durante brevi interruzioni (es. rotazione schermo).
     */
    val isDarkMode: StateFlow<Boolean> = themePreferences.isDarkModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /**
     * Inverte la preferenza del tema e la persiste su DataStore.
     * La scrittura avviene su IO dispatcher tramite viewModelScope,
     * senza mai bloccare il thread principale.
     */
    fun toggleDarkMode() = viewModelScope.launch {
        themePreferences.setDarkMode(!isDarkMode.value)
    }
    fun insertVehicle(vehicle: Vehicle) = viewModelScope.launch { repository.insertVehicle(vehicle) }
    fun updateVehicle(vehicle: Vehicle) = viewModelScope.launch { repository.updateVehicle(vehicle) }

    /**
     * Elimina un veicolo dal database eseguendo prima la pulizia di tutte le risorse
     * correlate ancora attive in background.
     *
     * PROBLEMA SENZA QUESTA LOGICA: se un veicolo viene eliminato mentre ha una sosta
     * attiva, il [ForeignKey.CASCADE] rimuove correttamente la sessione dal DB, ma
     * i task WorkManager schedulati (HOURLY, FIXED, WARNING) continuano a girare in
     * background. L'[OngoingParkingWorker] si auto-terminerebbe al prossimo ciclo non
     * trovando la sessione, ma la notifica persistente rimarrebbe bloccata nella barra
     * fino a quel momento. Cancellandola qui, l'interfaccia è immediatamente coerente.
     */
    fun deleteVehicle(vehicle: Vehicle, context: Context) = viewModelScope.launch {
        val workManager = WorkManager.getInstance(context)

        // Interrompiamo immediatamente tutti i job in coda per questo veicolo
        workManager.cancelAllWorkByTag("HOURLY_${vehicle.id}")
        workManager.cancelAllWorkByTag("FIXED_${vehicle.id}")
        workManager.cancelAllWorkByTag("FIXED_WARN_${vehicle.id}")

        // Puliamo la notifica di monitoraggio ongoing che potrebbe essere ancora visibile
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.cancel(BASE_ID_HOURLY + vehicle.id)

        // Rimuoviamo il veicolo (il CASCADE eliminerà automaticamente le sessioni associate)
        repository.deleteVehicle(vehicle)

        // Se non ci sono più soste attive, fermiamo il servizio GPS per risparmiare batteria
        stopTrackingServiceIfIdle(context)
    }

    fun insertSavedLocation(location: SavedLocation) = viewModelScope.launch { repository.insertSavedLocation(location) }
    fun updateSavedLocation(location: SavedLocation) = viewModelScope.launch { repository.updateSavedLocation(location) }
    fun deleteSavedLocation(location: SavedLocation) = viewModelScope.launch { repository.deleteSavedLocation(location) }

    // ==========================================
    // GESTIONE TRANSAZIONALE DELLE SOSTE
    // ==========================================
    fun startSecureParkingSession(newSession: ParkingSession, vehicle: Vehicle, context: Context) = viewModelScope.launch {
        val workManager = WorkManager.getInstance(context)

        // 1. PULIZIA VECCHI TASK (Inclusa la nuova tag FIXED_WARN)
        val activeSession = repository.getActiveSessionForVehicle(vehicle.id)
        if (activeSession != null) {
            repository.updateParkingSession(activeSession.copy(endTime = System.currentTimeMillis()))
            workManager.cancelAllWorkByTag("HOURLY_${vehicle.id}")
            workManager.cancelAllWorkByTag("FIXED_${vehicle.id}")
            workManager.cancelAllWorkByTag("FIXED_WARN_${vehicle.id}") // Pulizia pre-avviso
        }

        // 2. SALVATAGGIO NEL DATABASE
        repository.insertParkingSession(newSession)
        if (!vehicle.isParked) {
            repository.updateVehicle(vehicle.copy(isParked = true))
        }

        // 3. SCHEDULAZIONE BACKGROUND SERVICES
        startTrackingService(context)
        if (newSession.parkType.trim().lowercase() == "hourly") {
            val inputData = Data.Builder().putInt("VEHICLE_ID", vehicle.id).build()

            // Il primo anello si attiva subito, i successivi si sposteranno di 5 in 5 minuti
            val firstHourlyRequest = OneTimeWorkRequestBuilder<OngoingParkingWorker>()
                .setInputData(inputData)
                .addTag("HOURLY_${vehicle.id}")
                .build()

            workManager.enqueueUniqueWork(
                "HOURLY_JOB_${vehicle.id}",
                ExistingWorkPolicy.REPLACE,
                firstHourlyRequest
            )
        } else if (newSession.parkType.trim().lowercase() == "fixed" && newSession.expiryTime != null) {
            val delayMillis = newSession.expiryTime - System.currentTimeMillis()

            if (delayMillis > 0) {
                val inputData = Data.Builder().putInt("VEHICLE_ID", vehicle.id).build()

                // ====================================================================
                // LOGICA PREDITTIVA PROPORZIONALE (WARNING AL 15% RIMANENTE)
                // ====================================================================
                // 1. Calcoliamo la durata totale prevista del ticket
                val totalDurationMillis = newSession.expiryTime - newSession.startTime

                // 2. Calcoliamo quanto tempo deve passare prima di far scattare l'allarme (l'85% del totale)
                val timeToWarningFromStart = (totalDurationMillis * 0.85).toLong()

                // 3. Troviamo il timestamp esatto (orologio di sistema) in cui dovrà suonare
                val warningTriggerTime = newSession.startTime + timeToWarningFromStart

                // 4. Calcoliamo il delay da passare al WorkManager partendo dal momento attuale
                val warningDelay = warningTriggerTime - System.currentTimeMillis()
                val finalWarningDelay = if (warningDelay > 0) warningDelay else 0L
                // ====================================================================

                // Schedulazione del Pre-Avviso (es. 15% prima della fine)
                val warningRequest = OneTimeWorkRequestBuilder<com.unibo.parkmate.services.WarningParkingWorker>()
                    .setInitialDelay(finalWarningDelay, TimeUnit.MILLISECONDS)
                    .setInputData(inputData)
                    .addTag("FIXED_WARN_${vehicle.id}")
                    .build()

                workManager.enqueueUniqueWork("WARNING_JOB_${vehicle.id}", ExistingWorkPolicy.REPLACE, warningRequest)

                // Schedulazione della Scadenza Fiscale (100% del tempo)
                val expiryRequest = OneTimeWorkRequestBuilder<com.unibo.parkmate.services.ExpiryParkingWorker>()
                    .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                    .setInputData(inputData)
                    .addTag("FIXED_${vehicle.id}")
                    .build()

                workManager.enqueueUniqueWork("EXPIRY_JOB_${vehicle.id}", ExistingWorkPolicy.REPLACE, expiryRequest)
            }
        }
    }

    fun stopParkingSession(vehicle: Vehicle, context: Context) = viewModelScope.launch {
        val activeSession = repository.getActiveSessionForVehicle(vehicle.id)
        if (activeSession != null) {
            repository.updateParkingSession(activeSession.copy(endTime = System.currentTimeMillis()))
        }
        repository.updateVehicle(vehicle.copy(isParked = false))

        // La cancellazione tramite tag interrompe istantaneamente la catena dei 5 minuti
        val workManager = WorkManager.getInstance(context)
        workManager.cancelAllWorkByTag("HOURLY_${vehicle.id}")
        workManager.cancelAllWorkByTag("FIXED_${vehicle.id}")
        workManager.cancelAllWorkByTag("FIXED_WARN_${vehicle.id}")

        // Stop GPS antenna if no sessions are left, saving battery
        stopTrackingServiceIfIdle(context)

        // Pulizia della barra delle notifiche
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.cancel(6000 + vehicle.id)
    }

    // ==========================================
    // GPS TRACKING SERVICE LIFECYCLE
    // ==========================================
    /**
     * Starts the foreground GPS tracking service. Called when a new parking
     * session begins so the service runs only while at least one vehicle is parked.
     */
    fun startTrackingService(context: Context) {
        val intent = android.content.Intent(context, com.unibo.parkmate.services.LocationTrackingService::class.java)
        context.startForegroundService(intent)
    }

    /**
     * Stops the foreground GPS service only if no active sessions remain.
     * Called after ending a session so the antenna is not kept on unnecessarily.
     */
    fun stopTrackingServiceIfIdle(context: Context) {
        if (activeSessions.value.isEmpty()) {
            val intent = android.content.Intent(context, com.unibo.parkmate.services.LocationTrackingService::class.java)
                .apply { action = "STOP" }
            context.startService(intent)
        }
    }

    // ==========================================
    // GEOFENCING HARDWARE
    // ==========================================
    @SuppressLint("MissingPermission")
    fun registerGeofences(context: Context) = viewModelScope.launch {
        val geofencingClient = LocationServices.getGeofencingClient(context)
        val locations = repository.getAllSavedLocations() // O la tua lista di locations

        if (locations.isEmpty()) {
            geofencingClient.removeGeofences(getGeofencePendingIntent(context))
            return@launch
        }

        // --- 1. IL "PRIMER" HARDWARE (RISOLVE IL PROBLEMA DEL GPS DORMIENTE) ---
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                // Il solo fatto di aver richiesto e ottenuto questa posizione
                // sblocca la cache del sistema e innesca immediatamente il Geofencing passivo!
                println("GPS Hardware Primed: ${location?.latitude}, ${location?.longitude}")
            }
        // ------------------------------------------------------------------------

        // 2. COSTRUZIONE DEI GEOFENCE
        val geofenceList = locations.map { loc ->
            com.google.android.gms.location.Geofence.Builder()
                .setRequestId(loc.name)
                .setCircularRegion(loc.latitude, loc.longitude, loc.radius.toFloat())
                .setExpirationDuration(com.google.android.gms.location.Geofence.NEVER_EXPIRE)
                .setTransitionTypes(com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_ENTER or com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_EXIT)
                .build()
        }

        // 3. REGISTRAZIONE CON TRIGGER INIZIALE
        val geofencingRequest = GeofencingRequest.Builder()
            // INITIAL_TRIGGER_ENTER è fondamentale: se l'app si avvia e sei GIA' dentro la zona, suona subito
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(geofenceList)
            .build()

        // 4. INVIO AL SISTEMA OPERATIVO
        geofencingClient.addGeofences(geofencingRequest, getGeofencePendingIntent(context))
    }

    // Assicurati di avere questo costruttore per il PendingIntent nello stesso file:
    private fun getGeofencePendingIntent(context: Context): android.app.PendingIntent {
        val intent = android.content.Intent(context, com.unibo.parkmate.services.GeofenceBroadcastReceiver::class.java)
        return android.app.PendingIntent.getBroadcast(
            context,
            0,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
        )
    }

    // Stato per richiedere alla UI di aprire la schermata "Nuova Sosta"
    private val _pendingGeofenceZone = MutableStateFlow<String?>(null)
    val pendingGeofenceZone: StateFlow<String?> = _pendingGeofenceZone.asStateFlow()
    fun setPendingGeofenceZone(zone: String?) {
        _pendingGeofenceZone.value = zone
    }
    /**
     * Interrompe tutte le soste attive che cadono geometricamente
     * all'interno del raggio della zona specificata.
     */
    fun stopParkingInZone(zoneName: String, context: Context) = viewModelScope.launch {
        // 1. Recuperiamo i dati della zona
        val location = repository.getAllSavedLocations().find { it.name == zoneName } ?: return@launch

        // 2. Iteriamo sulle soste attualmente attive
        val active = activeSessions.value
        active.forEach { session ->

            // 3. Calcolo geometrico della distanza tra l'auto e il centro del geofence
            val results = FloatArray(1)
            android.location.Location.distanceBetween(
                session.latitude, session.longitude,
                location.latitude, location.longitude,
                results
            )

            // 4. Se l'auto è dentro il raggio dell'area che stiamo lasciando, la spegniamo
            if (results[0] <= location.radius) {
                val vehicle = vehicles.value.find { it.id == session.vehicleId }
                if (vehicle != null) {
                    stopParkingSession(vehicle, context)
                }
            }
        }
    }
}

// ==========================================
// FACTORY PER IL VIEWMODEL
// ==========================================
class ParkMateViewModelFactory(
    private val application: Application,
    private val repository: ParkMateRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ParkMateViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ParkMateViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}