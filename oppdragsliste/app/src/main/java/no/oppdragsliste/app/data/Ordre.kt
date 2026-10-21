package no.oppdragsliste.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import no.oppdragsliste.app.logikk.TolketOrdre
import java.time.Instant
import java.time.LocalDate

/**
 * En sak fra ordrelisten (importert fra skjermbilde). Ligger i oversikten til den er både utført og fakturert.
 */
@Entity(tableName = "ordre", indices = [Index(value = ["saksnr"], unique = true)])
data class Ordre(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saksnr: String,
    val tittel: String = "",
    val adresse: String = "",
    val kundenr: String = "",
    val kunde: String = "",
    val dato: LocalDate? = null,
    val tid: String? = null,
    val utfort: Boolean = false,
    val utfortDato: Instant? = null,
    val fakturert: Boolean = false,
    val fakturertDato: Instant? = null,
    val opprettet: Instant = Instant.now(),
) {
    /** Ferdig behandlet: utført og fakturert (havner i arkivet). */
    val avsluttet: Boolean get() = utfort && fakturert
}

/**
 * Slår en sak fra et nytt skjermbilde sammen med det vi har: tomme felt fylles inn, og lengre tekst erstatter
 * kortere (en sak kan være kuttet av nederst i ett bilde og hel i det neste). Avkrysningene beholdes.
 */
fun slaSammen(eksisterende: Ordre?, ny: TolketOrdre): Ordre {
    fun velg(gammel: String, nyTekst: String) = if (nyTekst.length > gammel.length) nyTekst else gammel
    val base = eksisterende ?: Ordre(saksnr = ny.saksnr)
    return base.copy(
        tittel = velg(base.tittel, ny.tittel),
        adresse = velg(base.adresse, ny.adresse),
        kundenr = velg(base.kundenr, ny.kundenr),
        kunde = velg(base.kunde, ny.kunde),
        dato = ny.dato ?: base.dato,
        tid = ny.tid ?: base.tid,
    )
}

@Dao
interface OrdreDao {
    @Query("SELECT * FROM ordre")
    fun observerAlle(): Flow<List<Ordre>>

    @Query("SELECT * FROM ordre")
    suspend fun alle(): List<Ordre>

    @Insert
    suspend fun settInn(o: Ordre): Long

    @Update
    suspend fun oppdater(o: Ordre)

    @Delete
    suspend fun slett(o: Ordre)
}
