package com.unibo.parkmate.ui.theme

import androidx.compose.ui.graphics.Color

// ── TEMA SCURO (invariato) ────────────────────────────────────────────────────
// Palette originale dell'applicazione ottimizzata per schermi AMOLED.
val DarkPrimary       = Color(0xFF00ADB5)   // Teal/Cyan — bottoni e elementi attivi
val DarkSecondary     = Color(0xFF393E46)   // Grigio scuro — elementi secondari
val DarkBackground    = Color(0xFF121212)   // Nero profondo — riduce consumi AMOLED
val DarkSurface       = Color(0xFF1E1E1E)   // Grigio — card e container
val DarkOnPrimary     = Color.Black
val DarkOnSecondary   = Color.White
val DarkOnBackground  = Color(0xFFEEEEEE)  // Bianco opaco — testo principale
val DarkOnSurface     = Color(0xFFEEEEEE)
val DarkError         = Color(0xFFFF5722)   // Arancio/rosso — azioni distruttive

// ── TEMA CHIARO ───────────────────────────────────────────────────────────────
// La palette chiara mantiene l'identità visiva del brand (teal/cyan come colore
// primario) ma sostituisce i fondali scuri con bianchi e grigi chiari per
// garantire il contrasto WCAG AA su superfici luminose.
val LightPrimary      = Color(0xFF007B83)   // Teal più scuro per contrasto su bianco (WCAG AA)
val LightSecondary    = Color(0xFF546E7A)   // Blu-grigio — elementi secondari
val LightBackground   = Color(0xFFF4F6F8)  // Grigio chiarissimo — evita il bianco puro abbagliante
val LightSurface      = Color(0xFFFFFFFF)  // Bianco puro — card e container
val LightOnPrimary    = Color.White
val LightOnSecondary  = Color.White
val LightOnBackground = Color(0xFF1C1B1F)  // Quasi-nero — testo principale
val LightOnSurface    = Color(0xFF1C1B1F)
val LightError        = Color(0xFFD32F2F)   // Rosso Material — azioni distruttive su sfondo chiaro