package com.unibo.parkmate.screens

import android.location.Location
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unibo.parkmate.viewmodel.ParkMateViewModel
import com.unibo.parkmate.database.calculateTotalCost // <-- IMPORT
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * Modulo di Business Intelligence (BI) e Data Visualization.
 * Esegue calcoli in-memory (Edge Computing) trasformando raw data in aggregazioni strategiche.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    viewModel: ParkMateViewModel,
    onOpenDrawer: () -> Unit
) {
    val pastSessions by viewModel.pastSessions.collectAsState()
    val savedLocations by viewModel.savedLocations.collectAsState()

    // Stato per il filtraggio multidimensionale (Data Pivot)
    var selectedCostFilter by remember { mutableStateOf("TYPE") }
    val costFilterOptions = listOf("TYPE", "DAY", "WEEK", "MONTH")

    var selectedBarKey by remember { mutableStateOf<String?>(null) }
    var selectedGeoCell by remember { mutableStateOf<Triple<String, String, Int>?>(null) }

    // Trigger per le animazioni al caricamento della view
    var startAnimation by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { startAnimation = true }

    // 2. APPLICAZIONE DEL PRINCIPIO DRY (Aggregazione finanziaria)
    // Sfrutta il Map-Reduce pattern in RAM per elaborare i costi evitando sub-query SQL
    val costData = remember(pastSessions, selectedCostFilter) {
        val dataMap = mutableMapOf<String, Double>()
        val calendar = Calendar.getInstance()

        pastSessions.forEach { session ->
            val type = session.parkType.trim().lowercase(Locale.getDefault())
            val cost = session.calculateTotalCost() // <-- CALCOLO PULITO

            calendar.timeInMillis = session.startTime
            val key = when (selectedCostFilter) {
                "TYPE" -> type.uppercase(Locale.getDefault())
                "DAY" -> SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(session.startTime))
                "WEEK" -> "WK ${calendar.get(Calendar.WEEK_OF_YEAR)}"
                "MONTH" -> SimpleDateFormat("MMM", Locale.getDefault()).format(Date(session.startTime)).uppercase(Locale.getDefault())
                else -> "UNKNOWN"
            }

            dataMap[key] = (dataMap[key] ?: 0.0) + cost
        }
        dataMap.toSortedMap()
    }
    val maxCost = remember(costData) { costData.values.maxOrNull()?.toFloat() ?: 1f }

    // Resetta lo stato di selezione quando cambia la dimensione analitica
    LaunchedEffect(selectedCostFilter) { selectedBarKey = null }

    // ALGORITMO DI MATRICE GEO-TEMPORALE (Spatial-Temporal Heatmap):
    // Incrocia i dati geometrici e i timestamp per valutare la saturazione delle zone.
    val geoHeatMapData = remember(pastSessions, savedLocations) {
        val locationMap = mutableMapOf<String, IntArray>()
        val calendar = Calendar.getInstance()

        pastSessions.forEach { session ->
            val matchedArea = savedLocations.find { loc ->
                val results = FloatArray(1)
                Location.distanceBetween(session.latitude, session.longitude, loc.latitude, loc.longitude, results)
                results[0] <= loc.radius
            }
            val areaName = matchedArea?.name?.uppercase(Locale.getDefault()) ?: "STREET (UNZONED)"

            calendar.timeInMillis = session.startTime
            val day = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7

            if (!locationMap.containsKey(areaName)) {
                locationMap[areaName] = IntArray(7)
            }
            locationMap[areaName]!![day]++
        }
        locationMap.entries.sortedByDescending { it.value.sum() }.associate { it.key to it.value }
    }

    val maxGeoHeat = remember(geoHeatMapData) {
        geoHeatMapData.values.maxOfOrNull { row -> row.maxOrNull() ?: 0 }?.coerceAtLeast(1) ?: 1
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DATA ANALYTICS", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = onOpenDrawer) { Icon(Icons.Default.Menu, "Menu") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { paddingValues ->
        if (pastSessions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text("NOT ENOUGH DATA FOR ANALYTICS", style = MaterialTheme.typography.labelMedium)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RectangleShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("COST EXPENDITURE ANALYSIS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Select an aggregation metric to compute total expenses", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(16.dp))

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            items(costFilterOptions) { option ->
                                val isSelected = selectedCostFilter == option
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedCostFilter = option },
                                    label = { Text(option, style = MaterialTheme.typography.labelSmall) },
                                    shape = RectangleShape
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        // Disegno nativo in memoria sfruttando le proprietà di box-model (Box weight)
                        Row(modifier = Modifier.fillMaxWidth().height(180.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                            if (costData.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("NO RECORDS", style = MaterialTheme.typography.labelSmall) }
                            } else {
                                costData.forEach { (key, cost) ->
                                    val animatedHeight by animateFloatAsState(targetValue = if (startAnimation) (cost.toFloat() / max(maxCost, 1f)) else 0f, animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing), label = "bar_height")
                                    val isSelected = selectedBarKey == key
                                    Box(
                                        modifier = Modifier.weight(1f).padding(horizontal = 6.dp).fillMaxHeight(animatedHeight.coerceAtLeast(0.03f))
                                            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                                            .clickable { selectedBarKey = if (isSelected) null else key }
                                    )
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            costData.keys.forEach { key ->
                                Text(text = key, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                            }
                        }

                        if (selectedBarKey != null) {
                            val totalCost = costData[selectedBarKey] ?: 0.0
                            Spacer(modifier = Modifier.height(16.dp))
                            Surface(color = MaterialTheme.colorScheme.surface, shape = RectangleShape, modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(text = "METRIC TARGET: $selectedBarKey", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    Text(text = "TOTAL EXPENDITURE: €${String.format(Locale.US, "%.2f", totalCost)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RectangleShape,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("GEO-TEMPORAL HEATMAP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Distribution of parks by Location and Time", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(24.dp))

                        val days = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

                        Row(modifier = Modifier.fillMaxWidth()) {
                            Spacer(modifier = Modifier.width(70.dp))
                            days.forEach { day ->
                                Text(
                                    text = day.take(1),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        geoHeatMapData.forEach { (areaName, daysArray) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = areaName,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.width(70.dp).padding(end = 8.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                daysArray.forEachIndexed { dayIndex, count ->
                                    // Risoluzione della densità (Color Interpolation) in base al massimo storico globale
                                    val intensity = count.toFloat() / maxGeoHeat.toFloat()
                                    val cellColor = if (count == 0) {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                                    } else {
                                        MaterialTheme.colorScheme.primary.copy(alpha = max(0.15f, intensity))
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .padding(horizontal = 2.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(cellColor)
                                            .clickable {
                                                selectedGeoCell = Triple(areaName, days[dayIndex], count)
                                            }
                                    )
                                }
                            }
                        }

                        if (selectedGeoCell != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Surface(color = MaterialTheme.colorScheme.surface, shape = RectangleShape, modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(text = "GEOGRAPHIC FOCUS: ${selectedGeoCell!!.first}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    Text(text = "TIMELINE: ${selectedGeoCell!!.second}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                    Text(text = "SESSIONS RECORDED: ${selectedGeoCell!!.third}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}