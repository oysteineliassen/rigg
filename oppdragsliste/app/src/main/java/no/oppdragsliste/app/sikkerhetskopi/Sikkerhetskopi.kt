package no.oppdragsliste.app.sikkerhetskopi

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.oppdragsliste.app.data.Oppdrag
import no.oppdragsliste.app.data.Prioritet
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Formatet i JSON-filen. Datoer og tider som ISO-tekst, så filen er lesbar. */
@Serializable
data class OppdragJson(
    val beskrivelse: String = "",
    val kunde: String = "",
    val telefon: String = "",
    val sted: String = "",
    val adresse: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val prioritet: String = Prioritet.OMRADE.name,
    val dato: String? = null,
    val tid: String? = null,
    val opprettet: String,
    val ferdig: Boolean = false,
    val ferdigDato: String? = null,
)

@Serializable
data class SikkerhetskopiFil(
    val app: String = "Oppdragsliste",
    val versjon: Int = 1,
    val eksportert: String,
    val oppdrag: List<OppdragJson>,
)

private val json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun Oppdrag.tilJson() = OppdragJson(
    beskrivelse = beskrivelse, kunde = kunde, telefon = telefon, sted = sted, adresse = adresse,
    lat = lat, lng = lng, prioritet = prioritet.name, dato = dato?.toString(), tid = tid?.toString(),
    opprettet = opprettet.toString(), ferdig = ferdig, ferdigDato = ferdigDato?.toString(),
)

fun OppdragJson.tilOppdrag() = Oppdrag(
    beskrivelse = beskrivelse, kunde = kunde, telefon = telefon, sted = sted, adresse = adresse,
    lat = lat, lng = lng,
    prioritet = runCatching { Prioritet.valueOf(prioritet) }.getOrDefault(Prioritet.OMRADE),
    dato = dato?.let(LocalDate::parse),
    tid = tid?.let(LocalTime::parse),
    opprettet = Instant.parse(opprettet),
    ferdig = ferdig,
    ferdigDato = ferdigDato?.let(Instant::parse),
)

fun eksporter(oppdrag: List<Oppdrag>, na: Instant = Instant.now()): String =
    json.encodeToString(SikkerhetskopiFil(eksportert = na.toString(), oppdrag = oppdrag.map { it.tilJson() }))

/** Leser en sikkerhetskopi. Kaster unntak hvis filen ikke er gyldig. */
fun les(tekst: String): List<Oppdrag> =
    json.decodeFromString<SikkerhetskopiFil>(tekst).oppdrag.map { it.tilOppdrag() }

/** Samme oppdrag: lagt inn samtidig og med samme innhold. Brukes for å ikke importere duplikater. */
private fun Oppdrag.nokkel() = listOf(opprettet.toEpochMilli(), beskrivelse, kunde, telefon, sted, adresse).joinToString("\u0000")

/** Oppdragene i [importerte] som ikke allerede finnes, klare til å settes inn (med ny id). */
fun nyeOppdrag(eksisterende: List<Oppdrag>, importerte: List<Oppdrag>): List<Oppdrag> {
    val finnes = eksisterende.map { it.nokkel() }.toMutableSet()
    return importerte.filter { finnes.add(it.nokkel()) }.map { it.copy(id = 0) }
}
