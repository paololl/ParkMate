package com.unibo.parkmate.services

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.unibo.parkmate.MainActivity

/**
 * Componente [BroadcastReceiver] registrato a livello di sistema operativo.
 * Funge da entry point asincrono per gli eventi geospaziali (Geofence Transitions)
 * scatenati dai Google Play Services, anche quando l'applicazione è in background o terminata.
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Estrazione del payload dell'evento dall'Intent di sistema
        val geofencingEvent = GeofencingEvent.fromIntent(intent)

        // Validazione dell'integrità dei dati spaziali
        if (geofencingEvent == null || geofencingEvent.hasError()) {
            val errorMessage = GeofenceStatusCodes.getStatusCodeString(geofencingEvent?.errorCode ?: -1)
            Log.e("GeofenceReceiver", "Geofencing Error: $errorMessage")
            return
        }

        // Risoluzione della transizione (Entrata o Uscita)
        val geofenceTransition = geofencingEvent.geofenceTransition
        val triggeringGeofences = geofencingEvent.triggeringGeofences
        if (triggeringGeofences.isNullOrEmpty()) return

        val locationName = triggeringGeofences[0].requestId

        when (geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> sendGeofenceNotification(context, locationName, isEnter = true)
            Geofence.GEOFENCE_TRANSITION_EXIT -> sendGeofenceNotification(context, locationName, isEnter = false)
        }
    }

    /**
     * Costruisce e dispaccia una notifica interattiva di sistema.
     * Implementa il pattern del [Deep Linking] locale tramite [PendingIntent],
     * incanalando l'utente direttamente all'azione pertinente nella UI.
     */
    private fun sendGeofenceNotification(context: Context, locationName: String, isEnter: Boolean) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // --- ASSEGNAZIONE DEL COMANDO E DEEP LINKING ---
        val actionCommand = if (isEnter) "ACTION_START_PARK" else "ACTION_STOP_PARK"

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            action = actionCommand
            putExtra("LOCATION_NAME", locationName)
            // SINGLE_TOP riporta l'app in primo piano senza riavviarla da zero (Idempotenza di navigazione)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        // Incapsulamento dell'Intent in un PendingIntent delegabile al NotificationManager
        val pendingIntent = PendingIntent.getActivity(
            context,
            if (isEnter) 101 else 102,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isEnter) "📍 ENTRY: $locationName" else "🚗 EXIT: $locationName"
        val message = if (isEnter) {
            "You entered $locationName. Tap to start parking."
        } else {
            "You left $locationName. Tap to close active tickets here."
        }

        val notification = NotificationCompat.Builder(context, "PARKMATE_ALERTS")
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Sfondamento parziale delle restrizioni UI
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(locationName.hashCode(), notification)
    }
}