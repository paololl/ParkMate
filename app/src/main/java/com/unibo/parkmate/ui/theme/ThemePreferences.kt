package com.unibo.parkmate.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Wrapper per la persistenza della preferenza del tema utente.
 *
 * SCELTA ARCHITETTURALE: SharedPreferences
 * Per una preferenza binaria (dark/light mode) letta una sola volta all'avvio
 * e aggiornata raramente, SharedPreferences è la soluzione più diretta e leggibile:
 * è già inclusa nel framework Android senza dipendenze esterne, la lettura del
 * boolean è sincrona e trascurabile in termini di tempo, e la scrittura tramite
 * .apply() avviene in modo asincrono su un thread di I/O interno al framework,
 * senza mai bloccare il thread principale dell'interfaccia utente.
 */
class ThemePreferences(context: Context) {

    companion object {
        private const val PREFS_NAME  = "parkmate_settings"
        private const val KEY_DARK_MODE = "dark_mode"
    }

    // Accesso al file di preferenze dedicato all'applicazione.
    // MODE_PRIVATE garantisce che solo questa app possa leggere/scrivere il file.
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Restituisce la preferenza di tema attualmente salvata.
     * Il valore di default è FALSE (tema chiaro) per i nuovi utenti
     * che non hanno ancora espresso una preferenza esplicita.
     */
    fun isDarkMode(): Boolean = prefs.getBoolean(KEY_DARK_MODE, false)

    /**
     * Persiste la scelta del tema su disco.
     * .apply() è preferito a .commit() perché scrive in modo asincrono
     * su un thread di background, rendendo l'operazione non bloccante.
     */
    fun setDarkMode(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_DARK_MODE, enabled) }
    }
}