package no.oppdragsliste.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [Oppdrag::class], version = 1, exportSchema = false)
@TypeConverters(Konverterere::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun oppdragDao(): OppdragDao

    companion object {
        fun bygg(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "oppdrag.db").build()
    }
}
