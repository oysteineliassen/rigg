package no.oppdragsliste.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OppdragDao {
    @Query("SELECT * FROM oppdrag")
    fun observerAlle(): Flow<List<Oppdrag>>

    @Query("SELECT * FROM oppdrag")
    suspend fun alle(): List<Oppdrag>

    @Query("SELECT * FROM oppdrag WHERE ferdig = 0")
    suspend fun apne(): List<Oppdrag>

    @Query("SELECT * FROM oppdrag WHERE id = :id")
    suspend fun hent(id: Long): Oppdrag?

    @Insert
    suspend fun settInn(oppdrag: Oppdrag): Long

    @Insert
    suspend fun settInnAlle(oppdrag: List<Oppdrag>)

    @Update
    suspend fun oppdater(oppdrag: Oppdrag)

    @Delete
    suspend fun slett(oppdrag: Oppdrag)
}
