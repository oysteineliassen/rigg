package no.oppdragsliste.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Noe å huske, med valgfritt varsel (dato + klokkeslett) og valgfri kobling til et oppdrag.
 * Slettes oppdraget, blir påminnelsen stående uten kobling.
 */
@Entity(
    tableName = "paminnelse",
    foreignKeys = [
        ForeignKey(
            entity = Oppdrag::class,
            parentColumns = ["id"],
            childColumns = ["oppdragId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("oppdragId")],
)
data class Paminnelse(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tekst: String = "",
    val dato: LocalDate? = null,
    val tid: LocalTime? = null,
    val oppdragId: Long? = null,
    val ferdig: Boolean = false,
    val ferdigDato: Instant? = null,
    val opprettet: Instant = Instant.now(),
) {
    /** Når varselet skal komme. Uten klokkeslett: kl. 08:00 den dagen. */
    val tidspunkt: LocalDateTime? get() = dato?.let { LocalDateTime.of(it, tid ?: LocalTime.of(8, 0)) }
}

@Dao
interface PaminnelseDao {
    @Query("SELECT * FROM paminnelse")
    fun observerAlle(): Flow<List<Paminnelse>>

    @Query("SELECT * FROM paminnelse")
    suspend fun alle(): List<Paminnelse>

    @Query("SELECT * FROM paminnelse WHERE ferdig = 0")
    suspend fun apne(): List<Paminnelse>

    @Query("SELECT * FROM paminnelse WHERE id = :id")
    suspend fun hent(id: Long): Paminnelse?

    @Insert
    suspend fun settInn(p: Paminnelse): Long

    @Update
    suspend fun oppdater(p: Paminnelse)

    @Delete
    suspend fun slett(p: Paminnelse)
}
