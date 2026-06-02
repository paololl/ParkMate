package com.unibo.parkmate.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.unibo.parkmate.database.Vehicle
import com.unibo.parkmate.viewmodel.ParkMateViewModel
import java.util.Locale
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight

/**
 * Schermata master (Master-Detail Pattern parziale) per le operazioni CRUD
 * riguardanti l'entità [Vehicle]. Implementa la Separation of Concerns separando
 * l'interfaccia a lista dal componente [VehicleCard].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleListScreen(
    viewModel: ParkMateViewModel,
    onOpenDrawer: () -> Unit,
    onNavigateToPark: () -> Unit
) {
    val vehicles by viewModel.vehicles.collectAsState()
    val activeSessions by viewModel.activeSessions.collectAsState()

    // --- 1. RECUPERO DEL CONTESTO ---
    val context = LocalContext.current

    // State Hoisting: Il controllo del Modale vive a livello schermo
    var showDialog by remember { mutableStateOf(false) }
    var editingVehicle by remember { mutableStateOf<Vehicle?>(null) }
    var vehicleName by remember { mutableStateOf("") }
    var vehicleType by remember { mutableStateOf("Car") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FLEET", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = "Open Menu")
                    }
                },
                // ---------------------------------------
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary // Colora il bottone di Ciano
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                shape = RectangleShape,
                onClick = {
                    editingVehicle = null
                    vehicleName = ""
                    vehicleType = "Car"
                    showDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Vehicle")
            }
        }
    ) { paddingValues ->
        if (vehicles.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text("NO VEHICLES REGISTERED.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(vehicles) { vehicle ->
                    val currentSession = activeSessions.find { it.vehicleId == vehicle.id }
                    VehicleCard(
                        vehicle = vehicle,
                        activeSession = currentSession,
                        onEdit = {
                            editingVehicle = vehicle
                            vehicleName = vehicle.name
                            vehicleType = vehicle.type
                            showDialog = true
                        },
                        onDelete = { viewModel.deleteVehicle(vehicle, context) },
                        onPark = { onNavigateToPark() }, // Innesca il cambio di schermata
                        onStop = { viewModel.stopParkingSession(vehicle, context) }
                    )
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            shape = RectangleShape,
            title = { Text(if (editingVehicle == null) "ADD VEHICLE" else "EDIT VEHICLE") },
            text = {
                // Variabile per controllare l'apertura del menù a tendina
                var expanded by remember { mutableStateOf(false) }
                val vehicleOptions = listOf("Car", "Bike", "Bicycle")

                Column {
                    OutlinedTextField(
                        value = vehicleName,
                        onValueChange = { vehicleName = it },
                        label = { Text("Vehicle Name") },
                        singleLine = true,
                        shape = RectangleShape,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // --- MENU A TENDINA ---
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        // Il campo di testo visibile (di sola lettura)
                        OutlinedTextField(
                            value = vehicleType.uppercase(Locale.ROOT),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Vehicle Type") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                            },
                            shape = RectangleShape,
                            modifier = Modifier
                                .menuAnchor(
                                    type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                    enabled = true
                                )
                                .fillMaxWidth(),
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                        )

                        // Il menù fluttuante con le opzioni
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            vehicleOptions.forEach { selectionOption ->
                                DropdownMenuItem(
                                    text = { Text(selectionOption.uppercase(Locale.ROOT)) },
                                    onClick = {
                                        vehicleType = selectionOption
                                        expanded = false // Chiude la tendina dopo il click
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(shape = RectangleShape, onClick = {
                    if (vehicleName.isNotBlank()) {
                        if (editingVehicle != null) {
                            viewModel.updateVehicle(editingVehicle!!.copy(name = vehicleName, type = vehicleType))
                        } else {
                            viewModel.insertVehicle(Vehicle(name = vehicleName, type = vehicleType))
                        }
                        showDialog = false
                    }
                }) { Text("SAVE", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("CANCEL", color = MaterialTheme.colorScheme.error) }
            }
        )
    }
}

/**
 * Componente UI autonomo (Stateless Composable) responsabile della visualizzazione del singolo veicolo.
 * I callback (es. onEdit, onDelete) promuovono lo State Hoisting verso il genitore.
 */
@Composable
fun VehicleCard(
    vehicle: Vehicle,
    activeSession: com.unibo.parkmate.database.ParkingSession?,
    onEdit: (Vehicle) -> Unit,
    onDelete: (Vehicle) -> Unit,
    onPark: () -> Unit,
    onStop: () -> Unit
) {
    val vehicleIcon = when (vehicle.type.lowercase()) {
        "bike" -> Icons.Filled.TwoWheeler
        "bicycle" -> Icons.AutoMirrored.Filled.DirectionsBike
        else -> Icons.Filled.DirectionsCar
    }

    // --- MOTORE DEL TEMPO IN TEMPO REALE (Timer Tick) ---
    // Sfrutta le Coroutines (suspend function) all'interno del LaunchedEffect
    // Il ciclo è ancorato al ciclo di vita (scoping) di questa Card e muta lo stato ogni 1000ms.
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(vehicle.isParked) {
        while (vehicle.isParked) {
            kotlinx.coroutines.delay(1000) // Aggiorna ogni secondo senza bloccare la UI
            currentTime = System.currentTimeMillis()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RectangleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = vehicleIcon, contentDescription = "Vehicle Type", modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(text = vehicle.name.uppercase(Locale.ROOT), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (vehicle.isParked) "STATUS: PARKED" else "STATUS: NOT PARKED",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (vehicle.isParked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }

                // Tasto Park SEMPRE VISIBILE per permettere il "Re-Park" (Auto-chiusura)
                IconButton(onClick = onPark) {
                    Icon(Icons.Filled.LocalParking, contentDescription = "Park", tint = MaterialTheme.colorScheme.primary)
                }

                if (vehicle.isParked) {
                    IconButton(onClick = onStop) {
                        Icon(Icons.Filled.StopCircle, contentDescription = "Stop", tint = MaterialTheme.colorScheme.error)
                    }
                }

                IconButton(onClick = { onEdit(vehicle) }) { Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton(onClick = { onDelete(vehicle) }) { Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error) }
            }

            // --- PANNELLO TELEMETRIA IN TEMPO REALE ---
            if (vehicle.isParked && activeSession != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.secondary, thickness = 0.5.dp)

                val elapsedMillis = currentTime - activeSession.startTime
                val elapsedHours = elapsedMillis / 3600000
                val elapsedMinutes = (elapsedMillis % 3600000) / 60000
                val elapsedSeconds = (elapsedMillis % 60000) / 1000

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("PROTOCOL: ${activeSession.parkType.uppercase()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "ELAPSED: ${String.format(Locale.US, "%02d:%02d:%02d", elapsedHours, elapsedMinutes, elapsedSeconds)}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        if (activeSession.parkType == "hourly") {
                            val currentCost = (elapsedMillis.toDouble() / 3600000.0) * (activeSession.hourlyRate ?: 0.0)
                            Text("CURRENT COST", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "€ ${String.format(Locale.US, "%.2f", currentCost)}",
                                style = MaterialTheme.typography.titleSmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), color = MaterialTheme.colorScheme.error
                            )
                        } else if (activeSession.parkType == "fixed" && activeSession.expiryTime != null) {
                            val remainingMillis = activeSession.expiryTime - currentTime
                            if (remainingMillis > 0) {
                                val remHours = remainingMillis / 3600000
                                val remMinutes = (remainingMillis % 3600000) / 60000
                                val remSeconds = (remainingMillis % 60000) / 1000
                                Text("EXPIRES IN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = String.format(Locale.US, "%02d:%02d:%02d", remHours, remMinutes, remSeconds),
                                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Text("TICKET EXPIRED!", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}