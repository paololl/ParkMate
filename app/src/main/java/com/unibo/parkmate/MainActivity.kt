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

    // Usiamo i singleton già creati da ParkMateApplication per evitare di istanziare
    // un secondo repository/database ad ogni avvio dell'Activity.
    private val viewModel: ParkMateViewModel by viewModels {
        val app = application as ParkMateApplication
        ParkMateViewModelFactory(application, app.repository)
    }

    // REGOLA LIFECYCLE DI ANDROID: registerForActivityResult() DEVE essere chiamato
    // prima di onStart(), quindi va dichiarato come proprietà della classe (non dentro onCreate).
    // Se creato dentro un blocco condizionale in onCreate, il sistema non riuscirebbe a
    // ripristinare correttamente il contratto dopo una ricreazione dell'Activity (es. rotazione
    // dello schermo mentre il dialog di sistema è aperto), causando un IllegalStateException.

    // Launcher per ACCESS_BACKGROUND_LOCATION — richiesto separatamente dalla
    // posizione in foreground su Android 10+ (vincolo imposto dal sistema operativo).
    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* concesso/negato — il geofencing degrada gracefully se negato */ }

    // Launcher per POST_NOTIFICATIONS — richiesto separatamente su Android 13+.
    // Il callback è vuoto perché l'app funziona anche senza notifiche (funzionalità degradata).
    private val notificationsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* concesso/negato — l'app continua a funzionare anche senza permesso notifiche */ }

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
        // Usiamo il launcher dichiarato a livello di classe per garantire la corretta gestione
        // del risultato anche in caso di ricreazione dell'Activity durante il dialog di sistema.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // --- PERMESSO ACCESS_BACKGROUND_LOCATION (Android 10+) ---
        // Android impone che questo permesso sia richiesto DOPO aver ottenuto ACCESS_FINE_LOCATION
        // e in una richiesta separata. Tentare di chiederli insieme nella stessa chiamata
        // causerebbe il silenziamento automatico della richiesta da parte del sistema.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }

        // NOTA: il LocationTrackingService viene ora avviato/fermato reattivamente da
        // MainNavigationScreen in base alla presenza di sessioni attive.
        // Non viene più avviato incondizionatamente qui per risparmiare batteria.

        // --- ROUTING DEGLI INTENT DAL GEOFENCING ---
        // Gestiamo il deep link proveniente dalle notifiche del BroadcastReceiver.
        handleGeofenceIntent(intent)

        setContent {
            // Raccogliamo isDarkMode come State di Compose: ogni volta che l'utente
            // preme l'interruttore nel drawer, questo valore cambia e l'intera
            // ParkMateTheme (e tutto l'albero dei composable sotto di essa) si
            // ricompone con la nuova palette in modo automatico e istantaneo.
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

    // Traduce l'azione dell'Intent (proveniente dal GeofenceBroadcastReceiver)
    // in una chiamata al ViewModel oppure in una navigazione verso la schermata di nuova sosta.
    private fun handleGeofenceIntent(intent: Intent) {
        val action = intent.action
        val zoneName = intent.getStringExtra("LOCATION_NAME") ?: return
        when (action) {
            "ACTION_STOP_PARK" -> viewModel.stopParkingInZone(zoneName, this)
            "ACTION_START_PARK" -> viewModel.setPendingGeofenceZone(zoneName)
        }
    }
}