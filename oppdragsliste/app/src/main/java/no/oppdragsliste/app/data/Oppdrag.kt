package no.oppdragsliste.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Rekkefølgen her er rekkefølgen seksjonene vises i. */
enum class Prioritet(val tittel: String, val kort: String) {
    AKUTT("Akutt", "Akutt"),
    AVTALE("Avtalt tid", "Avtale"),
    HASTER("Haster", "Haster"),
    OMRADE("Når i området", "I området"),
}

@Entity(tableName = "oppdrag")
data class Oppdrag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val beskrivelse: String = "",
    val kunde: String = "",
    val anlegg: String = "",
    val kontaktperson: String = "",
    val telefon: String = "",
    val sted: String = "",
    val adresse: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val prioritet: Prioritet = Prioritet.OMRADE,
    val dato: LocalDate? = null,
    val tid: LocalTime? = null,
    val opprettet: Instant = Instant.now(),
    val ferdig: Boolean = false,
    val ferdigDato: Instant? = null,
    /** Siste dag det ble varslet «i nærheten» for dette oppdraget (maks én gang per dag). */
    val naerVarslet: LocalDate? = null,
) {
    /** Det som vises som overskrift på kortet. */
    val tittel: String get() = beskrivelse.ifBlank { kunde }

    val harKoordinater: Boolean get() = lat != null && lng != null

    /** Sted og adresse slått sammen, uten tomme deler. */
    val stedTekst: String get() = listOf(adresse, sted).filter { it.isNotBlank() }.joinToString(", ")
}
