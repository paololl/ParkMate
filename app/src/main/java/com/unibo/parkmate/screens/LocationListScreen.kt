package com.unibo.parkmate.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.unibo.parkmate.database.SavedLocation
import com.unibo.parkmate.viewmodel.ParkMateViewModel
import java.util.Locale
import androidx.compose.ui.platform.LocalLocale

/**
 * Gestione dei profili spaziali (Geofences). 
 * Utilizza Form Validation a livello UI per impedire incoerenze logiche o sintattiche (Dirty Data) 
 * prima di commutare verso il Domain Layer (ViewModel).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationListScreen(
    viewModel: ParkMateViewModel,
    onOpenDrawer: () -> Unit
) {
    val savedLocations by viewModel.savedLocations.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    // State pre-compilato. Quando non nullo, abilita il modale in modalità "Edit" (Aggiornamento CRUD)
    var editingLocation by remember { mutableStateOf<SavedLocation?>(null) }

    // Campi di Input controllati (Controlled Components)
    var name by remember { mutableStateOf("") }
    var latInput by remember { mutableStateOf("") }
    var lonInput by remember { mutableStateOf("") }
    var radInput by remember { mutableStateOf("") }
    var costInput by remember { mutableStateOf("") }

    fun openAddDialog() {
        editingLocation = null
        name = ""; latInput = ""; lonInput = ""; radInput = ""; costInput = ""
        showAddDialog = true
    }

    fun openEditDialog(location: SavedLocation) {
        editingLocation = location
        name = location.name
        latInput = location.latitude.toString()
        lonInput = location.longitude.toString()
        radInput = location.radius.toString()
        costInput = location.defaultCost?.toString() ?: ""
        showAddDialog = true
    }

    fun closeAndResetDialog() {
        showAddDialog = false
        editingLocation = null
        name = ""; latInput = ""; lonInput = ""; radInput = ""; costInput = ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SAVED LOCATIONS", style = MaterialTheme.typography.titleMedium) },
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
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { openAddDialog() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RectangleShape
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Aggiungi nuova zona")
            }
        }
    ) { paddingValues ->

        if (savedLocations.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("NO GEOFENCE PROFILES FOUND", style = MaterialTheme.typography.labelMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(savedLocations) { location ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RectangleShape,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = location.name.uppercase(LocalLocale.current.platformLocale),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                // Edit button
                                IconButton(onClick = { openEditDialog(location) }) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Edit location",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                // Delete button
                                IconButton(onClick = { viewModel.deleteSavedLocation(location) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete location",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.3f))
                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = String.format(Locale.US, "LAT: %.5f | LON: %.5f", location.latitude, location.longitude),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(text = "SCOPE: ${location.radius}m", style = MaterialTheme.typography.bodySmall)

                            val rateDisplay = if (location.defaultCost != null)
                                "€${String.format(Locale.US, "%.2f", location.defaultCost)}/h"
                            else "FREE"
                            Text(
                                text = "PROTOCOL: ${location.defaultParkType.uppercase(LocalLocale.current.platformLocale)} ($rateDisplay)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // --- ADD / EDIT DIALOG ---
        if (showAddDialog) {
            val dialogTitle = if (editingLocation == null) "NEW GEOFENCE" else "EDIT GEOFENCE"

            AlertDialog(
                onDismissRequest = { closeAndResetDialog() },
                shape = RectangleShape,
                title = {
                    Text(dialogTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Zone Identifier (Name)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RectangleShape,
                            singleLine = true
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = latInput,
                                onValueChange = { latInput = it },
                                label = { Text("Latitude") },
                                modifier = Modifier.weight(1f),
                                shape = RectangleShape,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            OutlinedTextField(
                                value = lonInput,
                                onValueChange = { lonInput = it },
                                label = { Text("Longitude") },
                                modifier = Modifier.weight(1f),
                                shape = RectangleShape,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }
                        OutlinedTextField(
                            value = radInput,
                            onValueChange = { radInput = it },
                            label = { Text("Radius (meters)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RectangleShape,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = costInput,
                            onValueChange = { costInput = it },
                            label = { Text("Hourly Rate (Optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RectangleShape,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        shape = RectangleShape,
                        onClick = {
                            val lat = latInput.toDoubleOrNull() ?: 0.0
                            val lon = lonInput.toDoubleOrNull() ?: 0.0
                            val rad = radInput.toDoubleOrNull() ?: 0.0
                            val cost = costInput.toDoubleOrNull()
                            if (name.isNotBlank()) {
                                val updated = SavedLocation(
                                    id = editingLocation?.id ?: 0,
                                    name = name,
                                    latitude = lat,
                                    longitude = lon,
                                    radius = rad,
                                    defaultParkType = if (cost != null) "hourly" else "Free",
                                    defaultCost = cost
                                )
                                if (editingLocation == null) {
                                    viewModel.insertSavedLocation(updated)
                                } else {
                                    viewModel.updateSavedLocation(updated)
                                }
                                closeAndResetDialog()
                            }
                        }
                    ) {
                        Text("COMMIT", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { closeAndResetDialog() }) {
                        Text("CANCEL", color = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    }
}