package com.unibo.parkmate.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.unibo.parkmate.database.ParkingSession
import com.unibo.parkmate.database.SavedLocation
import com.unibo.parkmate.database.Vehicle
import com.unibo.parkmate.services.LocationService
import com.unibo.parkmate.viewmodel.ParkMateViewModel
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import java.io.File

/**
 * Modulo form di registrazione sosta aziendale.
 * Gestisce l'interfacciamento asincrono con l'Hardware di bordo (GPS Hardware, Camera API)
 * richiedendo permessi tramite i "Contract" di [rememberLauncherForActivityResult].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewSessionScreen(viewModel: ParkMateViewModel, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val locationService = remember { LocationService(context) }

    val vehicles by viewModel.vehicles.collectAsState()
    val savedLocations by viewModel.savedLocations.collectAsState()
    val availableVehicles = vehicles

    LaunchedEffect(Unit) {
        val mapPrefs = context.getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE)
        Configuration.getInstance().load(context, mapPrefs)
        Configuration.getInstance().userAgentValue = context.packageName
    }

    // --- UI STATE ---
    var selectedVehicle by remember { mutableStateOf<Vehicle?>(null) }

    // --- LOCATION STATE ---
    var locationMode by remember { mutableStateOf("GPS") }
    var selectedSavedLocation by remember { mutableStateOf<SavedLocation?>(null) }
    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var isFetchingLocation by remember { mutableStateOf(false) }
    var detectedGeofence by remember { mutableStateOf<SavedLocation?>(null) }

    // --- SESSION DETAIL STATE ---
    var parkType by remember { mutableStateOf("Free") }
    var priceInput by remember { mutableStateOf("") }
    var durationInput by remember { mutableStateOf("") }
    var notesInput by remember { mutableStateOf("") }

    // --- CAMERA STATE (Persistent Storage Pattern) ---
    // Implementazione del FileProvider proxy per garantire persistenza.
    // L'istanza File punta all'External Files Dir anziché alla volatile Cache.
    var photoFile by remember { mutableStateOf<File?>(null) }
    var photoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var photoPath by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(availableVehicles) {
        if (selectedVehicle == null && availableVehicles.isNotEmpty()) {
            selectedVehicle = availableVehicles.first()
        }
    }

    // --- GPS PERMISSION LAUNCHER & ALGORITMO DI PROSSIMITA' SPAZIALE ---
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            coroutineScope.launch {
                isFetchingLocation = true
                val loc = locationService.getCurrentLocation()
                if (loc != null) {
                    latitude = loc.latitude
                    longitude = loc.longitude

                    // ALGORITMO GEOFENCING E DATA QUALITY:
                    // Risolve la sovrapposizione tra coordinate GPS e zone salvate usando la trigonometria
                    detectedGeofence = savedLocations.find { location ->
                        val results = FloatArray(1)
                        android.location.Location.distanceBetween(
                            loc.latitude, loc.longitude,
                            location.latitude, location.longitude,
                            results
                        )
                        results[0] <= location.radius
                    }
                    // Se intersecata, la logica autocompila l'interfaccia
                    if (detectedGeofence != null) {
                        parkType = detectedGeofence!!.defaultParkType
                        priceInput = detectedGeofence!!.defaultCost?.toString() ?: ""
                    }
                } else {
                    Toast.makeText(context, "Impossibile ottenere il fix GPS", Toast.LENGTH_SHORT).show()
                }
                isFetchingLocation = false
            }
        }
    }

    // --- CAMERA LAUNCHER (TakePicture = full-resolution, saved to filesDir) ---
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            // The camera wrote the full-resolution photo to photoFile.
            // Decode a preview bitmap for the thumbnail display in the UI.
            photoPath = photoFile?.absolutePath
            photoPath?.let { path -> photoBitmap = BitmapFactory.decodeFile(path) }
        }
    }

    // Helper: create a new file in filesDir and launch the camera pointing at it.
    fun launchCamera() {
        // 1. Point to the correct authorized directory
        val storageDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)

        // 2. Create the physical file
        val file = File(storageDir, "park_photo_${System.currentTimeMillis()}.jpg")
        photoFile = file

        // 3. Generate the secure URI
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        cameraLauncher.launch(uri)
    }

    // --- CAMERA PERMISSION LAUNCHER ---
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCamera()
        } else {
            Toast.makeText(context, "Camera permission is required to attach photos", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("INITIATE PARKING PROTOCOL") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (availableVehicles.isEmpty()) {
                Text("NESSUN VEICOLO NEL DATABASE.", color = MaterialTheme.colorScheme.error)
                return@Scaffold
            }

            // --- 1. VEHICLE SELECTION ---
            Text("TARGET VEHICLE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableVehicles.forEach { vehicle ->
                    FilterChip(
                        selected = selectedVehicle == vehicle,
                        onClick = { selectedVehicle = vehicle },
                        label = {
                            Text(
                                text = if (vehicle.isParked) "${vehicle.name.uppercase()} (RE-PARK)" else vehicle.name.uppercase(),
                                color = if (vehicle.isParked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        shape = RectangleShape
                    )
                }
            }
            if (selectedVehicle?.isParked == true) {
                Text(
                    "Warning: Starting a session will auto-close the current active session.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.secondary)

            // --- 2. LOCATION SOURCE ---
            Text("LOCATION SOURCE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("GPS", "SAVED", "MANUAL").forEach { mode ->
                    FilterChip(
                        selected = locationMode == mode,
                        onClick = {
                            locationMode = mode
                            latitude = null; longitude = null; detectedGeofence = null
                        },
                        label = { Text(mode) },
                        shape = RectangleShape
                    )
                }
            }

            when (locationMode) {
                "GPS" -> {
                    Button(
                        onClick = {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        shape = RectangleShape,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.GpsFixed, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isFetchingLocation) "ACQUIRING..." else "ENGAGE GPS HARDWARE")
                    }
                }
                "SAVED" -> {
                    if (savedLocations.isEmpty()) {
                        Text("No saved locations available.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                            OutlinedTextField(
                                value = selectedSavedLocation?.name ?: "Select a Geofence",
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier
                                    .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                                    .fillMaxWidth(),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                shape = RectangleShape
                            )
                            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                savedLocations.forEach { loc ->
                                    DropdownMenuItem(
                                        text = { Text(loc.name.uppercase()) },
                                        onClick = {
                                            selectedSavedLocation = loc
                                            latitude = loc.latitude
                                            longitude = loc.longitude
                                            parkType = loc.defaultParkType
                                            priceInput = loc.defaultCost?.toString() ?: ""
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                "MANUAL" -> {
                    Text(
                        "TAP ON THE MAP TO SET COORDINATES",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RectangleShape)
                            .border(1.dp, MaterialTheme.colorScheme.secondary, RectangleShape)
                    ) {
                        androidx.compose.ui.viewinterop.AndroidView(
                            factory = { ctx ->
                                org.osmdroid.views.MapView(ctx).apply {
                                    setMultiTouchControls(true)
                                    controller.setZoom(15.0)
                                    controller.setCenter(org.osmdroid.util.GeoPoint(44.4949, 11.3426))

                                    setOnTouchListener { view, event ->
                                        when (event.action) {
                                            android.view.MotionEvent.ACTION_DOWN ->
                                                view.parent.requestDisallowInterceptTouchEvent(true)
                                            android.view.MotionEvent.ACTION_UP -> {
                                                view.parent.requestDisallowInterceptTouchEvent(false)
                                                view.performClick()
                                            }
                                            android.view.MotionEvent.ACTION_CANCEL ->
                                                view.parent.requestDisallowInterceptTouchEvent(false)
                                        }
                                        false
                                    }

                                    val overlay = org.osmdroid.views.overlay.MapEventsOverlay(
                                        object : org.osmdroid.events.MapEventsReceiver {
                                            override fun singleTapConfirmedHelper(p: org.osmdroid.util.GeoPoint): Boolean {
                                                latitude = p.latitude
                                                longitude = p.longitude
                                                overlays.removeAll { it is org.osmdroid.views.overlay.Marker }
                                                val marker = org.osmdroid.views.overlay.Marker(this@apply).apply {
                                                    position = p
                                                    setAnchor(
                                                        org.osmdroid.views.overlay.Marker.ANCHOR_CENTER,
                                                        org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM
                                                    )
                                                }
                                                overlays.add(marker)
                                                invalidate()
                                                return true
                                            }
                                            override fun longPressHelper(p: org.osmdroid.util.GeoPoint) = false
                                        }
                                    )
                                    overlays.add(overlay)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            if (latitude != null && longitude != null) {
                Text(
                    "LAT: $latitude | LNG: $longitude",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.secondary)

            // --- 3. SESSION RULES ---
            Text("SESSION RULES", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            // Park type chips are always interactive — even when a saved location pre-fills
            // them, the user may want to override the default for this specific session.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Free", "Hourly", "Fixed").forEach { type ->
                    FilterChip(
                        selected = parkType == type,
                        onClick = { parkType = type; priceInput = ""; durationInput = "" },
                        label = { Text(type.uppercase()) },
                        shape = RectangleShape
                    )
                }
            }

            if (parkType != "Free") {
                OutlinedTextField(
                    value = priceInput,
                    onValueChange = { if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d*$"))) priceInput = it },
                    label = { Text(if (parkType == "Hourly") "Hourly Rate (€/h)" else "Fixed Cost (€)") },
                    shape = RectangleShape,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            if (parkType == "Fixed") {
                OutlinedTextField(
                    value = durationInput,
                    onValueChange = { if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d*$"))) durationInput = it },
                    label = { Text("Duration (Hours)") },
                    shape = RectangleShape,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.secondary)

            // --- 4. ATTACHMENTS ---
            Text("ATTACHMENTS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                value = notesInput,
                onValueChange = { notesInput = it },
                label = { Text("Operational Notes") },
                shape = RectangleShape,
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        // Check CAMERA permission before launching the camera app.
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                            == PackageManager.PERMISSION_GRANTED
                        ) {
                            launchCamera()
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    shape = RectangleShape
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (photoBitmap == null) "ATTACH PHOTO" else "RETAKE PHOTO")
                }
                Spacer(modifier = Modifier.width(16.dp))
                if (photoBitmap != null) {
                    Image(
                        bitmap = photoBitmap!!.asImageBitmap(),
                        contentDescription = "Proof",
                        modifier = Modifier
                            .size(64.dp)
                            .border(1.dp, MaterialTheme.colorScheme.primary)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- 5. SUBMIT ---
            val isFormValid = latitude != null && longitude != null &&
                    (parkType == "Free" || priceInput.isNotBlank()) &&
                    (parkType != "Fixed" || durationInput.isNotBlank())

            Button(
                onClick = {
                    if (selectedVehicle != null && isFormValid) {
                        val parsedPrice = priceInput.toDoubleOrNull()
                        val parsedDuration = durationInput.toDoubleOrNull() ?: 0.0
                        val calculatedExpiry = if (parkType == "Fixed")
                            System.currentTimeMillis() + (parsedDuration * 3600000).toLong()
                        else null

                        val session = ParkingSession(
                            vehicleId = selectedVehicle!!.id,
                            parkType = parkType.lowercase(),
                            startTime = System.currentTimeMillis(),
                            latitude = latitude!!,
                            longitude = longitude!!,
                            hourlyRate = if (parkType.lowercase() == "hourly" || parkType.lowercase() == "fixed") parsedPrice else null,
                            expiryTime = calculatedExpiry,
                            notes = notesInput.ifBlank { null },
                            photoPath = photoPath
                        )

                        viewModel.startSecureParkingSession(session, selectedVehicle!!, context)
                        onNavigateBack()
                    }
                },
                enabled = isFormValid,
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(Icons.Filled.LocalParking, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("START PARKING SESSION")
            }
        }
    }
}