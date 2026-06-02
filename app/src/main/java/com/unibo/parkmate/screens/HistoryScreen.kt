package com.unibo.parkmate.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.unibo.parkmate.viewmodel.ParkMateViewModel
import com.unibo.parkmate.database.calculateTotalCost // <-- IMPORT FONDAMENTALE AGGIUNTO
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.platform.LocalLocale

/**
 * Schermata di visualizzazione dello storico (History Log).
 * Implementa un paradigma di interfaccia dichiarativa per la visualizzazione e il
 * filtraggio reattivo dei dati archiviati, attingendo alla Single Source of Truth del ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: ParkMateViewModel,
    onOpenDrawer: () -> Unit
) {
    // Sottoscrizione ai flussi reattivi (StateFlow). Qualsiasi mutamento nel layer dati
    // innesca automaticamente una ricomposizione (Recomposition) di questa funzione.
    val pastSessions by viewModel.pastSessions.collectAsState()
    val vehicles by viewModel.vehicles.collectAsState()
    val savedLocations by viewModel.savedLocations.collectAsState()

    var selectedFilter by remember { mutableStateOf("ALL") }
    val filterOptions = listOf("ALL", "HOURLY", "FIXED", "FREE")

    // MOTORE DI FILTRAGGIO IN-MEMORY:
    // Utilizza la memoizzazione (remember) per ricalcolare la lista solo quando
    // i dipendenti (pastSessions o selectedFilter) variano, abbattendo i costi di CPU.
    val filteredSessions = remember(pastSessions, selectedFilter) {
        pastSessions.filter { session ->
            if (selectedFilter == "ALL") {
                true
            } else {
                session.parkType.trim().equals(selectedFilter, ignoreCase = true)
            }
        }.sortedByDescending { it.endTime ?: it.startTime }
    }

    // OTTIMIZZAZIONE SPAZIALE O(1):
    // Pre-computazione (Spatial Join) delle coordinate storiche con le aree Geofence.
    // L'uso di un dizionario azzera la latenza di O(n * m) durante lo scorrimento della LazyColumn.
    val locationDictionary = remember(pastSessions, savedLocations) {
        pastSessions.associateWith { session ->
            savedLocations.find { loc ->
                val results = FloatArray(1)
                android.location.Location.distanceBetween(
                    session.latitude, session.longitude,
                    loc.latitude, loc.longitude,
                    results
                )
                results[0] <= loc.radius
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("HISTORY LOGS", style = MaterialTheme.typography.titleMedium) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.background
            ) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filterOptions) { option ->
                        val isSelected = selectedFilter == option
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedFilter = option },
                            label = {
                                Text(
                                    text = option,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            shape = RectangleShape,
                            leadingIcon = if (isSelected) {
                                { Icon(imageVector = Icons.Default.Check, contentDescription = "Selezionato") }
                            } else null
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

            // GESTIONE DELLE TRANSIZIONI DI STATO:
            // Crossfade fluidifica il cambio di contesto grafico generato dai filtri
            Crossfade(targetState = filteredSessions, label = "lista_filtrata") { sessions ->
                if (sessions.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (pastSessions.isEmpty()) "NO ARCHIVED SESSIONS FOUND" else "NO MATCHES FOR PROTOCOL: $selectedFilter",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    // LAZY RENDERING:
                    // Componente RecyclerView-like per il caricamento differito e ottimizzato degli elementi
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(sessions) { session ->

                            val vehicle = vehicles.find { it.id == session.vehicleId }
                            val vehicleName = vehicle?.name?.uppercase(Locale.getDefault()) ?: "VEICOLO ID: ${session.vehicleId}"

                            // Estrazione a costo computazionale nullo dal dizionario pre-computato
                            val matchedLocation = locationDictionary[session]
                            val locationDisplay = matchedLocation?.name?.uppercase(Locale.getDefault())
                                ?: String.format(Locale.US, "%.5f, %.5f", session.latitude, session.longitude)

                            val endTime = session.endTime ?: System.currentTimeMillis()
                            val durationMillis = endTime - session.startTime
                            val durationMinutes = (durationMillis % 3600000) / 60000

                            // 1. APPLICAZIONE DEL PRINCIPIO DRY
                            // Demanda il calcolo al Domain Model garantendo Single Responsibility
                            val type = session.parkType.trim().lowercase(LocalLocale.current.platformLocale)
                            val totalCost = session.calculateTotalCost()
                            val inputPrice = session.hourlyRate ?: 0.0

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RectangleShape,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = vehicleName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "€ ${String.format(Locale.US, "%.2f", totalCost)}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (totalCost > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                                    Spacer(modifier = Modifier.height(12.dp))

                                    Text(text = "LOC: $locationDisplay", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)

                                    val rateLabel = if (type == "fixed") "FLAT PRICE" else "RATE"
                                    val rateUnit = if (type == "fixed") "" else "/h"
                                    Text(
                                        text = "TYPE: ${type.uppercase(LocalLocale.current.platformLocale)} ($rateLabel: €${String.format(Locale.US, "%.2f", inputPrice)}$rateUnit)",
                                        style = MaterialTheme.typography.bodySmall
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    val formatter = SimpleDateFormat("dd/MM/yyyy - HH:mm:ss", LocalLocale.current.platformLocale)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(text = "IN:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                            Text(text = formatter.format(Date(session.startTime)), style = MaterialTheme.typography.bodySmall)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(text = "OUT:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                            Text(text = formatter.format(Date(endTime)), style = MaterialTheme.typography.bodySmall)
                                        }
                                    }

                                    val displayHours = durationMillis / 3600000
                                    Text(
                                        text = "TOTAL TIME: ${displayHours}H ${durationMinutes}M",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )

                                    if (!session.notes.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(text = "LOG NOTES:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 4.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            shape = RectangleShape
                                        ) {
                                            Text(
                                                text = session.notes,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(8.dp)
                                            )
                                        }
                                    }

                                    if (!session.photoPath.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(text = "EVIDENCE:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        AsyncImage(
                                            model = session.photoPath,
                                            contentDescription = "Foto della sosta",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(180.dp)
                                                .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), RectangleShape)
                                                .clip(RectangleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}