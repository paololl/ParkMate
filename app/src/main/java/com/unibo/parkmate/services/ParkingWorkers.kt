package com.unibo.parkmate.services

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.unibo.parkmate.MainActivity
import com.unibo.parkmate.database.AppDatabase
import java.util.Locale
import java.util.concurrent.TimeUnit

// Costanti di base per l'ID Spacing (prevenzione collisioni notifiche)
const val BASE_ID_HOURLY = 1000
const val BASE_ID_FIXED = 2000
const val BASE_ID_WARNING = 3000

class OngoingParkingWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val vehicleId = inputData.getInt("VEHICLE_ID", -1)
        if (vehicleId == -1) return Result.failure()

        val db = AppDatabase.getDatabase(context)
        val dao = db.parkMateDao()

        // 1. VERIFICA STATO DELLA SOSTA
        val activeSession = dao.getActiveSessionForVehicle(vehicleId)
        val vehicle = dao.getVehicleById(vehicleId)

        // Se la sosta è stata interrotta, spegniamo la catena
        if (activeSession == null || vehicle == null || !vehicle.isParked) {
            return Result.success()
        }

        val vehicleName = vehicle.name.uppercase(Locale.getDefault())
        val rate = activeSession.hourlyRate ?: 0.0
        val startTime = activeSession.startTime

        // 2. CALCOLO STATISTICHE CORRENTI
        val elapsedMillis = System.currentTimeMillis() - startTime
        val hours = (elapsedMillis / 3600000).toInt()
        val minutes = ((elapsedMillis % 3600000) / 60000).toInt()
        val currentCost = (elapsedMillis.toDouble() / 3600000.0) * rate

        // 3. COSTRUZIONE E INVIO NOTIFICA
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val textDisplay = if (rate > 0.0) {
            "⏱️ Time: ${hours}h ${minutes}m | Cost: € ${String.format(Locale.US, "%.2f", currentCost)}"
        } else {
            "⏱️ Time: ${hours}h ${minutes}m | Free Area "
        }

        val notification = NotificationCompat.Builder(context, "PARKMATE_ALERTS")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("🛰️ PARKING MONITOR: $vehicleName")
            .setContentText(textDisplay)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(BASE_ID_HOURLY + vehicleId, notification)

        // 4. INNESCO AUTOMATICO DEL PROSSIMO CONTROLLO (FISSO A 5 MINUTI)
        val workManager = WorkManager.getInstance(context)
        val nextInputData = Data.Builder().putInt("VEHICLE_ID", vehicleId).build()

        val nextWorkRequest = OneTimeWorkRequestBuilder<OngoingParkingWorker>()
            .setInitialDelay(5, TimeUnit.MINUTES)
            .setInputData(nextInputData)
            .addTag("HOURLY_${vehicleId}")
            .build()

        workManager.enqueueUniqueWork(
            "HOURLY_JOB_${vehicleId}",
            ExistingWorkPolicy.REPLACE,
            nextWorkRequest
        )

        return Result.success()
    }
}

class ExpiryParkingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val vehicleId = inputData.getInt("VEHICLE_ID", -1)
        if (vehicleId == -1) return Result.failure()

        val db = AppDatabase.getDatabase(applicationContext)
        val dao = db.parkMateDao()

        // 1. CHIUSURA TRANSAZIONALE DELLA SOSTA
        // Aggiorniamo il database segnando la fine della sessione con il timestamp corrente.
        val activeSession = dao.getActiveSessionForVehicle(vehicleId)
        if (activeSession != null) {
            dao.updateParkingSession(activeSession.copy(endTime = System.currentTimeMillis()))
        }

        val vehicle = dao.getVehicleById(vehicleId)
        val vehicleName = vehicle?.name?.uppercase() ?: "VEHICLE $vehicleId"
        if (vehicle != null) {
            dao.updateVehicle(vehicle.copy(isParked = false))
        }

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 2. PULIZIA NOTIFICA ONGOING
        // Il worker di monitoraggio orario (OngoingParkingWorker) pubblica una notifica persistente
        // con ID = BASE_ID_HOURLY + vehicleId. Poiché la sosta è ora terminata, dobbiamo
        // rimuoverla esplicitamente: senza questa riga rimarrebbe bloccata nella barra delle notifiche.
        notificationManager.cancel(BASE_ID_HOURLY + vehicleId)

        // 3. SPEGNIMENTO SELETTIVO DEL SERVIZIO GPS
        // Verifichiamo se esistono ancora soste attive per altri veicoli prima di fermare il
        // servizio in foreground. In questo modo il sensore GPS rimane acceso solo se necessario,
        // garantendo un uso energeticamente consapevole della batteria.
        val sessioniRimanenti = dao.getAllActiveSessions()
        if (sessioniRimanenti.isEmpty()) {
            val stopIntent = Intent(applicationContext, LocationTrackingService::class.java)
                .apply { action = "STOP" }
            applicationContext.startService(stopIntent)
        }

        // 4. INVIO ALERT DI SCADENZA AD ALTA PRIORITÀ
        // Utilizziamo PRIORITY_HIGH per sfondare il Doze Mode e mostrare il banner
        // anche quando il dispositivo è in modalità risparmio energetico.
        val notification = NotificationCompat.Builder(applicationContext, "PARKMATE_ALERTS")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ TICKET SCADUTO!")
            .setContentText("La sosta per $vehicleName è stata chiusa automaticamente.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(BASE_ID_FIXED + vehicleId, notification)

        return Result.success()
    }
}

class WarningParkingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val vehicleId = inputData.getInt("VEHICLE_ID", -1)
        if (vehicleId == -1) return Result.failure()

        val db = AppDatabase.getDatabase(applicationContext)
        val dao = db.parkMateDao()

        // Recuperiamo la sessione attiva per calcolare il tempo rimanente esatto.
        // È fondamentale leggere expiryTime dal DB (non dal payload del Worker) perché
        // la sosta potrebbe essere stata modificata dopo la schedulazione del job.
        val activeSession = dao.getActiveSessionForVehicle(vehicleId)
        val vehicle = dao.getVehicleById(vehicleId)
        val vehicleName = vehicle?.name?.uppercase() ?: "VEHICLE $vehicleId"

        // CALCOLO DEL TEMPO RIMANENTE REALE
        // Sottraiamo l'istante attuale dall'orario di scadenza per ottenere
        // i minuti effettivi rimasti, così l'utente può agire in modo informato.
        val minutiRimanenti = if (activeSession?.expiryTime != null) {
            ((activeSession.expiryTime - System.currentTimeMillis()) / 60000).coerceAtLeast(0)
        } else {
            0L
        }

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(applicationContext, "PARKMATE_ALERTS")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⏳ TICKET IN SCADENZA: $vehicleName")
            // Mostriamo i minuti rimanenti reali invece di un generico "pochi minuti"
            .setContentText("Scadenza tra circa $minutiRimanenti min. Sposta il veicolo!")
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Priorità alta per far comparire il banner immediatamente
            .setAutoCancel(true)
            .build()

        notificationManager.notify(BASE_ID_WARNING + vehicleId, notification)

        return Result.success()
    }
}