package com.unibo.parkmate.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entità Room che modella la tabella "saved_locations".
 * Utilizzata per la memorizzazione persistente dei perimetri di Geofencing (Spatial Data).
 * Oltre alle coordinate geodetiche, incapsula le regole di business di default
 * (es. policy di tariffazione) da applicare quando il dispositivo entra nell'area.
 */
@Entity(tableName = "saved_locations")
data class SavedLocation(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    // Raggio d'azione del geofence (in metri) per i calcoli di prossimità
    val radius: Double,
    val defaultParkType: String = "Free",
    val defaultCost: Double? = null
)