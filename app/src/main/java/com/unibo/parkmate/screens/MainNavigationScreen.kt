package com.unibo.parkmate.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import com.unibo.parkmate.services.LocationTrackingService
import android.content.Intent
import androidx.navigation.compose.rememberNavController
import com.unibo.parkmate.viewmodel.ParkMateViewModel
import kotlinx.coroutines.launch

/**
 * Router e Navigation Graph centralizzato (Single-Activity Architecture).
 * Coordina gli Intent asincroni (Deep Linking) e il ciclo di vita del Background Service.
 */
sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Vehicles : Screen("vehicles", "FLEET", Icons.Filled.DirectionsCar)
    object Locations : Screen("locations", "SAVED LOCATIONS", Icons.Filled.Place)
    object Map : Screen("map", "LIVE MAP", Icons.Filled.Map)
    object History : Screen("history", "HISTORY LOGS", Icons.AutoMirrored.Filled.ReceiptLong)
    object NewSession : Screen("new_session", "START PARKING", Icons.Filled.AddCircle)
    object Analytics : Screen("analytics", "DATA ANALYTICS", Icons.Filled.BarChart)
}

@Composable
fun MainNavigationScreen(viewModel: ParkMateViewModel) {
    val navController = rememberNavController()
    val items = listOf(
        Screen.Vehicles,
        Screen.Locations,
        Screen.Map,
        Screen.History,
        Screen.Analytics
    )

    // --- SERVICE LIFECYCLE CONTROLLER ---
    // Controller Reattivo: Osserva i flussi dati e decide lo stato hardware.
    // L'antenna GPS (ForegroundService) viene ri-allocata in maniera energeticamente consapevole,
    // evitandone il run-in continuo (24/7) qualora non ci siano sessioni attive in memoria.
    val context = LocalContext.current
    val activeSessions by viewModel.activeSessions.collectAsState()
    LaunchedEffect(activeSessions.isEmpty()) {
        val serviceIntent = Intent(context, LocationTrackingService::class.java)
        if (activeSessions.isNotEmpty()) {
            context.startForegroundService(serviceIntent)
        } else {
            serviceIntent.action = "STOP"
            context.startService(serviceIntent)
        }
    }

    // --- REGISTRAZIONE CENTRALIZZATA DEI GEOFENCE ---
    // Precedentemente, i geofence venivano registrati sia in VehicleListScreen (una volta all'avvio)
    // sia in LiveMapScreen (ad ogni cambio di savedLocations). Questa duplicazione era ridondante:
    // se l'utente non navigava mai sulla mappa, i geofence non venivano mai aggiornati.
    // Spostiamo qui la registrazione perché MainNavigationScreen è sempre attiva per tutta la
    // vita dell'app: garantiamo che i geofence siano sempre sincronizzati con il DB
    // indipendentemente dalla schermata corrente, eliminando la duplicazione.
    val savedLocations by viewModel.savedLocations.collectAsState()
    LaunchedEffect(savedLocations) {
        viewModel.registerGeofences(context)
    }

    // --- STATE BRIDGE PER LA NOTIFICA GPS E INTENT ROUTING ---
    // Disaccoppia la Broadcast Receiver originata dal OS (Intent Mapping)
    // traducendola in una navigazione reattiva dichiarativa all'interno di Compose.
    val pendingZone = viewModel.pendingGeofenceZone
    LaunchedEffect(pendingZone) {
        if (pendingZone != null) {
            // Quando scatta la notifica, il router sposta l'utente sulla nuova sosta
            navController.navigate(Screen.NewSession.route)

            // Disinneschiamo il comando per evitare che ripeta il salto se ruoti lo schermo (Idempotenza)
            viewModel.pendingGeofenceZone = null
        }
    }

    // Gestori dello stato del menù a tendina
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Funzione da passare alle singole schermate per fargli aprire il menù (Callback propagation)
    val openDrawer: () -> Unit = {
        scope.launch { drawerState.open() }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // Controlliamo se siamo sulla mappa per inibire le gesture laterali e prevenire i "Gesture Conflicts"
    val isMapScreen = currentDestination?.route == Screen.Map.route

    ModalNavigationDrawer(
        drawerState = drawerState,
        // DrawerSheet = Il pannello visivo che scorre da sinistra
        gesturesEnabled = !isMapScreen,
        drawerContent = {
            ModalDrawerSheet(
                drawerShape = RectangleShape, // Design squadrato hi-tech
                drawerContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.width(300.dp) // Larghezza del menù
            ) {


                Spacer(modifier = Modifier.height(8.dp))

                items.forEach { screen ->
                    NavigationDrawerItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title, fontFamily = FontFamily.Monospace) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        shape = RectangleShape,
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        onClick = {
                            // Chiudiamo il menù dopo aver cliccato
                            scope.launch { drawerState.close() }
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // ── INTERRUTTORE TEMA CHIARO / SCURO ──────────────────────────
                // Posizionato in fondo al drawer, separato dalle voci di navigazione
                // da un divisore per chiarezza visiva. Lo Switch è il componente
                // Material 3 standard per preferenze binarie: più immediato e
                // accessibile di un pulsante o di un menu a tendina.
                val isDarkMode by viewModel.isDarkMode.collectAsState()

                Spacer(modifier = Modifier.weight(1f))

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (isDarkMode) "Dark Theme" else "Light Theme",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        // checked=true = tema scuro attivo
                        checked = isDarkMode,
                        onCheckedChange = { viewModel.toggleDarkMode() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor  = MaterialTheme.colorScheme.primary,
                            checkedTrackColor  = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    ) {
        // --- IL CORPO PRINCIPALE DELL'APP ---
        // Sostituiamo lo Scaffold precedente con il solo NavHost,
        // e passiamo la funzione `openDrawer` alle schermate
        NavHost(
            navController = navController,
            startDestination = Screen.Vehicles.route,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(Screen.Vehicles.route) {
                VehicleListScreen(
                    viewModel = viewModel,
                    onOpenDrawer = openDrawer, // Passiamo il trigger del menù
                    onNavigateToPark = { navController.navigate("new_session") }
                )
            }
            composable(Screen.Locations.route) { LocationListScreen(viewModel, openDrawer) }
            composable(Screen.Map.route) { LiveMapScreen(viewModel, openDrawer) }
            composable(Screen.History.route) { HistoryScreen(viewModel, openDrawer) }

            composable(Screen.NewSession.route) {
                // Sostituisci con il vero nome del tuo form di inserimento
                NewSessionScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() } // Torna indietro alla fine
                )
            }

            composable(Screen.Analytics.route) { AnalyticsScreen(viewModel, openDrawer) }
        }
    }
}