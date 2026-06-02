package com.unibo.parkmate.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entità Room che modella la tabella "vehicles" all'interno del database relazionale.
 * Rappresenta il modello dati di base (Domain Model) per la gestione della flotta aziendale.
 */
@Entity(tableName = "vehicles")
data class Vehicle(
    // Identificatore univoco generato automaticamente dal DBMS (Primary Key)
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val type: String,
    // Flag di stato che permette di tracciare reattivamente se il veicolo ha una sessione attiva
    val isParked: Boolean = false
)