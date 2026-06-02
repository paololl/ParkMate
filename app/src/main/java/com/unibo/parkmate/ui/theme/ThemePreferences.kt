package com.unibo.parkmate.ui.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// La property delegate "preferencesDataStore" DEVE essere dichiarata a livello
// di file (top-level), non dentro una classe. Android impone questa regola per
// garantire che esista una sola istanza del DataStore per il file "parkmate_settings"
// durante l'intero ciclo di vita dell'applicazione (Singleton pattern).
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "parkmate_settings")

/**
 * Oggetto di accesso alla preferenza del tema utente, persistita tramite DataStore.
 *
 * PERCHÉ DATASTORE E NON SHAREDPREFERENCES?
 * DataStore è la sostituzione moderna di SharedPreferences. Opera interamente
 * su coroutine e Flow (non blocca mai il thread UI), gestisce automaticamente
 * le eccezioni di I/O e garantisce la consistenza dei dati anche in caso di
 * crash durante la scrittura. SharedPreferences.apply() invece può causare
 * perdita di dati se il processo viene terminato prima del flush su disco.
 */
class ThemePreferences(private val context: Context) {

    companion object {
        // La chiave tipizzata garantisce la type-safety a compile time:
        // impossibile leggere un valore booleano come stringa o viceversa.
        private val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
    }

    /**
     * Flusso reattivo che emette il valore aggiornato ogni volta che
     * la preferenza cambia su disco. Il valore di default è FALSE (tema chiaro)
     * per i nuovi utenti che non hanno ancora espresso una preferenza.
     */
    val isDarkModeFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[DARK_MODE_KEY] ?: false }

    /**
     * Scrive la preferenza su disco in modo asincrono tramite coroutine.
     * Chiamare questa funzione da un viewModelScope garantisce che la scrittura
     * non blocchi mai il thread principale dell'interfaccia utente.
     */
    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DARK_MODE_KEY] = enabled
        }
    }
}