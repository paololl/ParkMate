package com.unibo.parkmate.services

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await

/**
 * Wrapper architetturale per le Location API di Google Play Services.
 * Incapsula l'accesso all'hardware GPS fornendo una singola operazione (One-Shot Request)
 * sicura per le Coroutine.
 */
class LocationService(context: Context) {

    // Inizializziamo il client ufficiale di Google per l'astrazione hardware
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    // Sopprimiamo il warning di Android Studio perché il controllo
    // dei permessi lo faremo visivamente nella UI (Jetpack Compose) prima di chiamare questo metodo
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location? {
        return try {
            val cancellationToken = CancellationTokenSource()

            // Richiediamo la posizione attuale con massima priorità (GPS vero e proprio)
            // L'uso di .await() (Kotlin Coroutines) trasforma un'operazione asincrona basata su
            // Callback/Task in un'operazione sequenziale, senza bloccare il Main Thread.
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationToken.token
            ).await()

        } catch (e: Exception) {
            // Fault Tolerance: Se l'utente ha il GPS spento o nega i permessi,
            // restituiamo null in sicurezza prevenendo crash a cascata (Null Pointer).
            null
        }
    }
}