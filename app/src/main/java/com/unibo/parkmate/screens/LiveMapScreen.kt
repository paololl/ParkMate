package com.unibo.parkmate.screens

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.unibo.parkmate.database.ParkingSession
import com.unibo.parkmate.viewmodel.ParkMateViewModel
import com.unibo.parkmate.database.calculateTotalCost
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.platform.LocalLocale

/**
 * Mappa in tempo reale (Live Tracking).
 * Dimostra l'utilizzo del paradigma di Interoperabilità tra View legacy (OSMDroid)
 * e Jetpack Compose tramite [AndroidView].
 */
@SuppressLint("UseKtx")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveMapScreen(
    viewModel: ParkMateViewModel,
    onOpenDrawer: () -> Unit
) {
    val context = LocalContext.current
    val savedLocations by viewModel.savedLocations.collectAsState()
    val activeSessions by viewModel.activeSessions.collectAsState()
    val vehicles by viewModel.vehicles.collectAsState()

    // State Bridge: Un ponte di comunicazione che trasmette l'ID del veicolo
    // toccato (mondo OSM) all'interfaccia dichiarativa Compose (Pannello ispezione)
    var selectedSession by remember { mutableStateOf<ParkingSession?>(null) }

    // TICKER PER IL PANNELLO DI ISPEZIONE
    // VehicleCard ha il suo proprio ticker, ma il pannello della mappa è un componente
    // separato che richiede il suo stato temporale. Senza questo, i valori di tempo
    // trascorso e costo corrente nel pannello sarebbero congelati al momento del tap
    // sul marker e non si aggiornerebbero mai.
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(selectedSession?.id) {
        // Il ciclo è attivo solo quando il pannello è aperto (selectedSession != null)
        // e si ferma automaticamente quando viene chiuso o cambia sessione.
        while (selectedSession != null) {
            kotlinx.coroutines.delay(1000)
            currentTime = System.currentTimeMillis()
        }
    }

    // AUTO-DISMISS DELLA SESSIONE TERMINATA IN BACKGROUND
    // Scenario: il pannello è aperto e mostra la sosta del Veicolo A.
    // L'ExpiryParkingWorker chiude quella sosta mentre l'utente sta guardando la mappa.
    // Senza questo effetto, il pannello rimarrebbe aperto mostrando dati ormai obsoleti
    // (la sessione non esiste più in activeSessions). Lo chiudiamo automaticamente.
    LaunchedEffect(activeSessions) {
        if (selectedSession != null && activeSessions.none { it.id == selectedSession!!.id }) {
            selectedSession = null
        }
    }

    remember {
        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        true
    }

    // NOTA: la registrazione reattiva dei Geofence (LaunchedEffect su savedLocations) è stata
    // spostata in MainNavigationScreen per evitare registrazioni doppie. Qui rimane solo
    // il caricamento della configurazione di OSMDroid.

    val userIconDrawable = remember {
        val size = 50
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val fill = Paint().apply { color = "#FFFF2222".toColorInt(); isAntiAlias = true }
        val stroke = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 5f; isAntiAlias = true }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4f, fill)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4f, stroke)
        // Usiamo BitmapDrawable(null, bitmap) invece di bitmap.toDrawable(context.resources)
        // perché questa icona è disegnata interamente a codice (pixel fissi, colori hardcoded)
        // e non dipende da alcuna risorsa di sistema. Passare `null` come Resources elimina
        // la lettura di context.resources dentro il composable, risolvendo il warning Compose:
        // "Reading Resources using LocalContext.current.resources".
        BitmapDrawable(null, bitmap)
    }

    val vehicleIconDrawable = remember {
        val size = 44
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val fill = Paint().apply { color = "#FFFF9100".toColorInt(); isAntiAlias = true }
        val stroke = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 5f; isAntiAlias = true }
        canvas.drawRect(4f, 4f, size - 4f, size - 4f, fill)
        canvas.drawRect(4f, 4f, size - 4f, size - 4f, stroke)
        // Stesso motivo dell'icona utente sopra: icona puramente programmatica,
        // nessuna dipendenza da risorse di sistema → BitmapDrawable(null, bitmap).
        BitmapDrawable(null, bitmap)
    }

    var locationOverlay by remember { mutableStateOf<MyLocationNewOverlay?>(null) }

    val mapView = remember {
        MapView(context).apply {
            setMultiTouchControls(true)
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
            controller.setZoom(16.5)
            controller.setCenter(GeoPoint(44.4949, 11.3426))

            val overlay = MyLocationNewOverlay(GpsMyLocationProvider(context), this)
            overlay.enableMyLocation()
            overlay.setPersonIcon(userIconDrawable.bitmap)
            overlay.setDirectionIcon(userIconDrawable.bitmap)
            overlay.setPersonAnchor(0.5f, 0.5f)

            overlay.runOnFirstFix {
                post { controller.animateTo(overlay.myLocation) }
            }

            overlays.add(overlay)
            locationOverlay = overlay
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LIVE MAP", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(imageVector = Icons.Default.Menu, contentDescription = "Apri menu")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {

            // AndroidView: Incapsulamento di Views Tradizionali.
            // Il blocco 'update' sincronizza lo stato reattivo di Compose (savedLocations, activeSessions)
            // convertendolo in comandi imperativi sul canvas della mappa.
            AndroidView(
                factory = { mapView },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    view.overlays.retainAll { it is MyLocationNewOverlay }

                    savedLocations.forEach { location ->
                        val geoPoint = GeoPoint(location.latitude, location.longitude)
                        val circle = Polygon().apply {
                            points = Polygon.pointsAsCircle(geoPoint, location.radius)
                            fillPaint.color = "#2600E5FF".toColorInt()
                            outlinePaint.color = "#FF00E5FF".toColorInt()
                            outlinePaint.strokeWidth = 4f
                        }
                        val labelMarker = Marker(view).apply {
                            position = geoPoint
                            title = "AREA: ${location.name.uppercase(Locale.getDefault())}"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                        view.overlays.add(circle)
                        view.overlays.add(labelMarker)
                    }

                    activeSessions.forEach { session ->
                        val vehiclePoint = GeoPoint(session.latitude, session.longitude)

                        val vehicleMarker = Marker(view).apply {
                            position = vehiclePoint
                            icon = vehicleIconDrawable
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            infoWindow = null

                            setOnMarkerClickListener { _, _ ->
                                selectedSession = session // Attivazione dello State Bridge
                                true
                            }
                        }
                        view.overlays.add(vehicleMarker)
                    }

                    view.invalidate() // Forza il render engine
                }
            )

            AnimatedVisibility(
                visible = selectedSession != null,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .padding(bottom = 80.dp)
            ) {
                selectedSession?.let { session ->
                    val inspectedVehicle = vehicles.find { it.id == session.vehicleId }
                    val vehicleName = inspectedVehicle?.name?.uppercase(Locale.getDefault()) ?: "VEHICLE ID: ${session.vehicleId}"
                    val type = session.parkType.trim().lowercase(LocalLocale.current.platformLocale)

                    // Usiamo `currentTime` (aggiornato ogni secondo dal ticker LaunchedEffect)
                    // invece di System.currentTimeMillis() chiamato una volta sola al momento
                    // della composizione. In questo modo tempo e costo si aggiornano in real-time.
                    val elapsedMillis = currentTime - session.startTime
                    val hours = elapsedMillis / 3600000
                    val minutes = (elapsedMillis % 3600000) / 60000

                    // APPLICAZIONE DEL PRINCIPIO DRY IN REAL-TIME (Kotlin Copy Trick)
                    // Copiamo la sessione chiudendola virtualmente all'istante corrente per
                    // delegare il calcolo del costo all'extension function del Domain Model,
                    // evitando di duplicare la logica tariffaria nel layer di presentazione.
                    val currentCost = session.copy(endTime = currentTime).calculateTotalCost()

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(2.dp, MaterialTheme.colorScheme.primary, RectangleShape),
                        shape = RectangleShape,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📡 INSPECTION PANEL // $vehicleName",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                IconButton(onClick = { selectedSession = null }) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Chiudi pannello")
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                            Spacer(modifier = Modifier.height(12.dp))

                            Text(text = "PROTOCOL: ${type.uppercase(LocalLocale.current.platformLocale)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)

                            val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", LocalLocale.current.platformLocale)
                            Text(text = "START TIME: ${formatter.format(Date(session.startTime))}", style = MaterialTheme.typography.bodySmall)

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "ELAPSED TIME: ${hours}H ${minutes}M",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = "CURRENT COST: € ${String.format(Locale.US, "%.2f", currentCost)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (currentCost > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = {
                    locationOverlay?.myLocation?.let { geoPoint ->
                        mapView.controller.animateTo(geoPoint)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
                shape = RectangleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(imageVector = Icons.Default.MyLocation, contentDescription = "Centra GPS")
            }
        }
    }
}