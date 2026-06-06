package com.unibo.parkmate.services


import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*

/**
 * [Foreground Service] dedicato all'Active Location Polling.
 * ARCHITETTURA: Supera i limiti imposti dal Doze Mode di Android per le query geospaziali
 * in background. Mantenendo il servizio in foreground, l'hardware GPS viene forzato a restare
 * sveglio, abbattendo la latenza dei trigger Geofence e garantendo la correttezza
 * finanziaria delle transazioni di ingresso/uscita.
 */
class LocationTrackingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Creiamo il canale di notifica (obbligatorio da Android 8+ per policy di trasparenza utente)
        val channel = NotificationChannel(
            "TRACKING_CHANNEL",
            "Active GPS Tracking",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Intercetta la richiesta esplicita di distruzione del servizio
        if (intent?.action == "STOP") {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        // Avviamo la notifica persistente che mantiene vivo il servizio e ottempera ai vincoli OS
        startForeground(NOTIFICATION_ID, buildNotification())

        // Configurazione delle richieste GPS continue e aggressive (High Frequency Polling)
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L) // Ping ogni 5 secondi
            .setMinUpdateIntervalMillis(2000L) // Minimo 2 secondi se l'hardware è veloce
            .setWaitForAccurateLocation(true)
            .build()

        // MOTIVO DELLA CHIAMATA: requestLocationUpdates() richiede obbligatoriamente un
        // LocationCallback come parametro — non è possibile avviare il polling GPS senza di esso.
        // Il corpo del callback è intenzionalmente vuoto perché questo servizio NON elabora
        // le coordinate: il suo unico scopo è mantenere l'antenna GPS accesa per tutta la durata
        // delle sessioni di sosta attive.
        //
        // CICLO DI VITA: il servizio viene avviato alla prima sosta e fermato all'ultima
        // (gestito da MainNavigationScreen tramite LaunchedEffect(activeSessions.isEmpty())).
        // Tenere il GPS attivo durante le sessioni ha anche un effetto collaterale utile:
        // riduce la latenza dei trigger Geofence del sistema operativo, che rispondono
        // più lentamente quando il sensore è in modalità risparmio energetico.
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) { /* intenzionalmente vuoto */ }
        }

        // Accendiamo l'antenna GPS in modo vincolato delegando il callback al Main Looper
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )

        // START_STICKY assicura che il servizio venga riavviato dal SO se abbattuto per scarsa memoria
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, "TRACKING_CHANNEL")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("📡 Fleet Sensors Active")
            .setContentText("High-precision background localization")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Graceful teardown: Spegniamo l'antenna quando il servizio viene distrutto per evitare memory leaks
        if (::fusedLocationClient.isInitialized && ::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 8888
    }
}