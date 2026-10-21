package no.oppdragsliste.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Oppdrag::class, KjentAnlegg::class, Paminnelse::class, Ordre::class], version = 7, exportSchema = false)
@TypeConverters(Konverterere::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun oppdragDao(): OppdragDao
    abstract fun kjentAnleggDao(): KjentAnleggDao
    abstract fun paminnelseDao(): PaminnelseDao
    abstract fun ordreDao(): OrdreDao

    companion object {
        fun bygg(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "oppdrag.db")
                .addMigrations(FRA_1_TIL_2, FRA_2_TIL_3, FRA_3_TIL_4, FRA_4_TIL_5, FRA_5_TIL_6, FRA_6_TIL_7)
                .build()

        /** Versjon 2: feltet «anlegg» og registeret over kjente kunder/anlegg (fylt fra eksisterende oppdrag). */
        val FRA_1_TIL_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE oppdrag ADD COLUMN anlegg TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS kjent_anlegg (kunde TEXT NOT NULL, anlegg TEXT NOT NULL, " +
                        "telefon TEXT NOT NULL, sted TEXT NOT NULL, adresse TEXT NOT NULL, lat REAL, lng REAL, " +
                        "sistBrukt INTEGER NOT NULL, PRIMARY KEY(kunde, anlegg))",
                )
                // Eldste først, så det nyeste oppdraget for hver kunde blir stående.
                db.execSQL(
                    "INSERT OR REPLACE INTO kjent_anlegg (kunde, anlegg, telefon, sted, adresse, lat, lng, sistBrukt) " +
                        "SELECT TRIM(kunde), '', telefon, sted, adresse, lat, lng, opprettet FROM oppdrag " +
                        "WHERE TRIM(kunde) != '' ORDER BY opprettet",
                )
            }
        }

        /** Versjon 3: kontaktperson på oppdrag og i registeret. */
        val FRA_2_TIL_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE oppdrag ADD COLUMN kontaktperson TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE kjent_anlegg ADD COLUMN kontaktperson TEXT NOT NULL DEFAULT ''")
            }
        }

        /** Versjon 4: sluttid på oppdrag. */
        val FRA_3_TIL_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE oppdrag ADD COLUMN sluttid INTEGER")
            }
        }

        /** Versjon 5: påminnelser, valgfritt koblet til et oppdrag. */
        val FRA_4_TIL_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `paminnelse` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`tekst` TEXT NOT NULL, `dato` INTEGER, `tid` INTEGER, `oppdragId` INTEGER, " +
                        "`ferdig` INTEGER NOT NULL, `ferdigDato` INTEGER, `opprettet` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`oppdragId`) REFERENCES `oppdrag`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_paminnelse_oppdragId` ON `paminnelse` (`oppdragId`)")
            }
        }

        /** Versjon 6: notat per kunde/anlegg i registeret. */
        val FRA_5_TIL_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE kjent_anlegg ADD COLUMN notat TEXT NOT NULL DEFAULT ''")
            }
        }

        /** Versjon 7: ordre importert fra skjermbilder av ordrelisten (utført/fakturert). */
        val FRA_6_TIL_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ordre` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`saksnr` TEXT NOT NULL, `tittel` TEXT NOT NULL, `adresse` TEXT NOT NULL, `kundenr` TEXT NOT NULL, " +
                        "`kunde` TEXT NOT NULL, `dato` INTEGER, `tid` TEXT, `utfort` INTEGER NOT NULL, `utfortDato` INTEGER, " +
                        "`fakturert` INTEGER NOT NULL, `fakturertDato` INTEGER, `opprettet` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_ordre_saksnr` ON `ordre` (`saksnr`)")
            }
        }
    }
}
