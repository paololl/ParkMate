package com.unibo.parkmate.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// ── SCHEMA SCURO ─────────────────────────────────────────────────────────────
private val ParkMateDarkColorScheme = darkColorScheme(
    primary       = DarkPrimary,
    secondary     = DarkSecondary,
    background    = DarkBackground,
    surface       = DarkSurface,
    onPrimary     = DarkOnPrimary,
    onSecondary   = DarkOnSecondary,
    onBackground  = DarkOnBackground,
    onSurface     = DarkOnSurface,
    error         = DarkError
)

// ── SCHEMA CHIARO ─────────────────────────────────────────────────────────────
private val ParkMateLightColorScheme = lightColorScheme(
    primary       = LightPrimary,
    secondary     = LightSecondary,
    background    = LightBackground,
    surface       = LightSurface,
    onPrimary     = LightOnPrimary,
    onSecondary   = LightOnSecondary,
    onBackground  = LightOnBackground,
    onSurface     = LightOnSurface,
    error         = LightError
)

/**
 * Tema principale dell'applicazione.
 *
 * Il parametro [darkTheme] è controllato dall'utente tramite l'interruttore
 * nella sidebar, con la preferenza persistita su DataStore. Viene passato da
 * MainActivity che lo raccoglie come State dal ViewModel, garantendo che
 * l'intera albero dei composable si ricomponga immediatamente al cambio.
 *
 * NON usiamo isSystemInDarkTheme() come default perché vogliamo che la scelta
 * dell'utente dentro l'app abbia sempre la precedenza sul tema di sistema.
 */
@Composable
fun ParkMateTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) ParkMateDarkColorScheme else ParkMateLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content
    )
}