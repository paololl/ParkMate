package com.unibo.parkmate.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Classe astratta centrale che funge da database holder.
 * Gestisce la configurazione del database Room e fornisce l'accesso ai DAO sottostanti.
 */
@Database(
    entities = [Vehicle::class, SavedLocation::class, ParkingSession::class],
    version = 2, // <-- Fondamentale per attivare la migrazione
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun parkMateDao(): ParkMateDao

    companion object {
        // L'annotazione @Volatile assicura che il valore di INSTANCE sia scritto e letto
        // direttamente dalla memoria principale, garantendo la visibilità cross-thread
        // ed evitando problemi di caching locale nei registri del processore.
        @Volatile // Garantisce che le modifiche a INSTANCE siano visibili subito a tutti i thread
        private var INSTANCE: AppDatabase? = null

        // --- DEFINIZIONE DELLE MIGRAZIONI ---
        // Questo oggetto dice ad Android come passare in modo sicuro dalla versione 1 alla 2
        // senza perdere i dati esistenti.
        // ARCHITETTURA: Implementazione del pattern di Schema Evolution. Permette di
        // applicare modifiche incrementali (Continuous Delivery) garantendo la retrocompatibilità
        // ed evitando l'uso del metodo distruttivo "fallbackToDestructiveMigration()".
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // In uno scenario reale, qui si inserirebbe le query SQL per modificare le tabelle.
                // Lasciandolo vuoto (o con commenti), stiamo dicendo al database che la
                // versione 2 è strutturalmente pronta, mettendo in sicurezza i dati correnti.
            }
        }

        /**
         * Espone l'istanza del database seguendo il Singleton Pattern.
         * Il blocco [synchronized] previene "race conditions", assicurando che
         * richieste concorrenti non istanzino accidentalmente più database simultaneamente.
         */
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "parkmate_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}