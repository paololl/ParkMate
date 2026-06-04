package com.unibo.parkmate.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entità Room che modella la tabella "parking_sessions", rappresentando lo storico dei log operativi.
 * L'architettura rispetta la Terza Forma Normale (3NF) applicando vincoli di integrità referenziale.
 * * @property foreignKeys Vincolo di Foreign Key verso l'entità [Vehicle].
 * L'opzione [ForeignKey.CASCADE] garantisce che l'eliminazione di un veicolo
 * propaghi la distruzione dei relativi record di sosta, prevenendo dati orfani.
 * @property indices Creazione di un indice B-Tree su "vehicleId" per ottimizzare
 * i tempi di esecuzione (O(log n)) delle query di join e di ricerca.
 */
@Entity(
    tableName = "parking_sessions",
    foreignKeys = [
        ForeignKey(
            entity = Vehicle::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["vehicleId"])]
)
data class ParkingSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val vehicleId: Int, // Chiave esterna (Foreign Key)
    val parkType: String,
    val startTime: Long, // Usiamo i timestamp (millisecondi) per le date
    val endTime: Long? = null,
    val expiryTime: Long? = null,
    val latitude: Double,
    val longitude: Double,
    val hourlyRate: Double? = null,
    val notes: String? = null,
    val photoPath: String? = null
)

/**
 * Calcola il costo totale della sosta.
 * Nota: per le soste "fixed", il prezzo fisso è memorizzato in [hourlyRate].
 * Questo è coerente con il comportamento di NewSessionScreen.
 * Restituisce 0.0 se la sosta è ancora attiva o se è gratuita.
 * * DESIGN PATTERN: Questa Extension Function rispetta il Single Responsibility Principle (SRP)
 * incapsulando la Business Logic finanziaria direttamente nel Data Model.
 * Ciò previene la violazione del principio DRY (Don't Repeat Yourself) nei layer di UI.
 */
fun ParkingSession.calculateTotalCost(): Double {
    if (endTime == null) return 0.0

    return when (parkType.lowercase()) {
        "free" -> 0.0
        "fixed" -> hourlyRate ?: 0.0   // hourlyRate funge da costo fisso per i ticket Fixed
        "hourly" -> {
            val rate = hourlyRate ?: 0.0
            val durationMillis = endTime - startTime
            val durationHours = durationMillis.toDouble() / (1000.0 * 60.0 * 60.0)
            durationHours * rate
        }
        else -> 0.0
    }
}