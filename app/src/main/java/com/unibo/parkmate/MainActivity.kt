package com.unibo.parkmate

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.unibo.parkmate.screens.MainNavigationScreen
import com.unibo.parkmate.viewmodel.ParkMateViewModel
import com.unibo.parkmate.viewmodel.ParkMateViewModelFactory
import com.unibo.parkmate.ui.theme.ParkMateTheme

class MainActivity : ComponentActivity() {

    // Il ViewModel viene creato usando i singleton già istanziati da ParkMateApplication.
    // Creare qui un nuovo database o repository produrrebbe una seconda istanza indipendente:
    // i due oggetti non condividerebbero lo stato in memoria e potrebbero divergere.
    private val viewModel: ParkMateViewModel by viewModels {
        val app = application as ParkMateApplication
        ParkMateViewModelFactory(application, app.repository)
    }

    // REGOLA ANDROID — registerForActivityResult() deve essere dichiarato come proprietà
    // della classe, non dentro onCreate(). Android registra il contratto prima di onStart():
    // se dichiarato dentro un blocco condizionale, il contratto non viene ripristinato
    // correttamente dopo una ricreazione dell'Activity (es. rotazione schermo con dialog
    // di sistema aperto), causando un IllegalStateException.

    // ACCESS_BACKGROUND_LOCATION viene richiesto in una chiamata separata rispetto a
    // ACCESS_FINE_LOCATION perché Android 10+ non accetta i due nella stessa richiesta:
    // la posizione in background verrebbe silenziosamente ignorata dal sistema.
    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* il geofencing funziona solo in foreground se il permesso viene negato */ }

    // POST_NOTIFICATIONS è obbligatorio da Android 13+ per mostrare qualsiasi notifica.
    // Se negato, i Worker continuano a girare ma l'utente non vede alcun avviso.
    private val notificationsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* l'app funziona in modalità degradata: soste attive ma nessuna notifica */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- CANALI DI NOTIFICA (obbligatori da Android 8+) ---
        val channel = android.app.NotificationChannel(
            "PARKMATE_ALERTS",
            "ParkMate Alerts",
            android.app.NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "Notifications for tariff expirations and active parking sessions" }
        val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.createNotificationChannel(channel)

        // --- PERMESSO POST_NOTIFICATIONS (Android 13+) ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // --- PERMESSO ACCESS_BACKGROUND_LOCATION (Android 10+) ---
        // Viene richiesto solo se ACCESS_FINE_LOCATION è già concesso, rispettando
        // il flusso in due fasi imposto da Android per la posizione in background.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }

        // Il LocationTrackingService viene avviato e fermato reattivamente da
        // MainNavigationScreen in base alle sessioni attive, non qui in modo statico.

        // --- ROUTING DEGLI INTENT DAL GEOFENCING ---
        handleGeofenceIntent(intent)

        setContent {
            // isDarkMode è uno StateFlow osservato come State di Compose.
            // Ad ogni cambio (es. tap sull'interruttore nel drawer) l'intero albero
            // dei composable si ricompone automaticamente con la nuova palette colori.
            val isDarkMode by viewModel.isDarkMode.collectAsState()

            ParkMateTheme(darkTheme = isDarkMode) {
                MainNavigationScreen(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleGeofenceIntent(intent)
    }

    /**
     * Traduce l'azione dell'Intent ricevuto dal [GeofenceBroadcastReceiver] in una
     * operazione sul ViewModel: chiude la sosta se il veicolo esce dall'area ([ACTION_STOP_PARK])
     * oppure segnala alla UI di aprire il form di nuova sosta ([ACTION_START_PARK]).
     */
    private fun handleGeofenceIntent(intent: Intent) {
        val action = intent.action
        val zoneName = intent.getStringExtra("LOCATION_NAME") ?: return
        when (action) {
            "ACTION_STOP_PARK"  -> viewModel.stopParkingInZone(zoneName, this)
            "ACTION_START_PARK" -> viewModel.setPendingGeofenceZone(zoneName)
        }
    }
}