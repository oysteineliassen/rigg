package no.oppdragsliste.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Det som sist ble skrevet for en kunde og et anlegg. Brukes til forslag og automatisk utfylling
 * i skjemaet. [anlegg] er tom for kunder uten anlegg. Uavhengig av oppdragene, så det huskes også
 * etter at et oppdrag er slettet.
 */
@Entity(tableName = "kjent_anlegg", primaryKeys = ["kunde", "anlegg"])
data class KjentAnlegg(
    val kunde: String,
    val anlegg: String,
    val kontaktperson: String = "",
    val telefon: String = "",
    val sted: String = "",
    val adresse: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val sistBrukt: Instant = Instant.now(),
    /** Notat som følger kunden/anlegget til neste oppdrag (f.eks. adgangskode, hvor sikringsskapet er). */
    val notat: String = "",
) {
    val stedTekst: String get() = listOf(adresse, sted).filter { it.isNotBlank() }.joinToString(", ")
}

@Dao
interface KjentAnleggDao {
    @Query("SELECT * FROM kjent_anlegg ORDER BY sistBrukt DESC")
    fun observerAlle(): Flow<List<KjentAnlegg>>

    @Query("SELECT * FROM kjent_anlegg ORDER BY sistBrukt DESC")
    suspend fun alle(): List<KjentAnlegg>

    @Query("SELECT * FROM kjent_anlegg WHERE kunde = :kunde COLLATE NOCASE AND anlegg = :anlegg COLLATE NOCASE")
    suspend fun hent(kunde: String, anlegg: String): List<KjentAnlegg>

    @Query("SELECT * FROM kjent_anlegg WHERE kunde = :kunde COLLATE NOCASE")
    suspend fun hentKunde(kunde: String): List<KjentAnlegg>

    @Query("DELETE FROM kjent_anlegg WHERE kunde = :kunde COLLATE NOCASE AND anlegg = :anlegg COLLATE NOCASE")
    suspend fun slett(kunde: String, anlegg: String)

    @Query("DELETE FROM kjent_anlegg WHERE kunde = :kunde COLLATE NOCASE")
    suspend fun slettKunde(kunde: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun settInn(k: KjentAnlegg)

    /** Erstatter eventuell rad med samme kunde og anlegg (uansett store/små bokstaver). */
    @Transaction
    suspend fun husk(k: KjentAnlegg) {
        slett(k.kunde, k.anlegg)
        settInn(k)
    }
}
