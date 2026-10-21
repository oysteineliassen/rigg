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
 * Slår en sak fra et nytt skjermbilde sammen med det vi har. Ble saken lest helt (med status nederst), erstatter
 * den det som var lagret, så feillest tekst fra et tidligere bilde rettes opp. En sak som er kuttet av i bildet,
 * fyller bare tomme felt. Avkrysningene beholdes.
 */
fun slaSammen(eksisterende: Ordre?, ny: TolketOrdre): Ordre {
    val hel = ny.status != null
    fun velg(gammel: String, nyTekst: String) = when {
        nyTekst.isBlank() -> gammel
        hel || gammel.isBlank() -> nyTekst
        else -> gammel
    }
    val base = eksisterende ?: Ordre(saksnr = ny.saksnr)
    return base.copy(
        tittel = velg(base.tittel, ny.tittel),
        adresse = velg(base.adresse, ny.adresse),
        kundenr = velg(base.kundenr, ny.kundenr),
        kunde = velg(base.kunde, ny.kunde),
        // Dato og tid fra ordrelisten brukes ikke (sakene sorteres på saksnummer).
        dato = null,
        tid = null,
    )
}

/** Saksnummer som tall, for sortering (ukjent format sist). */
val Ordre.saksnrTall: Long get() = saksnr.filter { it.isDigit() }.toLongOrNull() ?: Long.MAX_VALUE

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
