package no.oppdragsliste.app.sikkerhetskopi

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.oppdragsliste.app.data.Oppdrag
import no.oppdragsliste.app.data.Paminnelse
import no.oppdragsliste.app.data.Prioritet
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Formatet i JSON-filen. Datoer og tider som ISO-tekst, så filen er lesbar. */
@Serializable
data class OppdragJson(
    val beskrivelse: String = "",
    val kunde: String = "",
    val anlegg: String = "",
    val kontaktperson: String = "",
    val telefon: String = "",
    val sted: String = "",
    val adresse: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val prioritet: String = Prioritet.OMRADE.name,
    val dato: String? = null,
    val tid: String? = null,
    val sluttid: String? = null,
    val opprettet: String,
    val ferdig: Boolean = false,
    val ferdigDato: String? = null,
)

/** En påminnelse. Koblingen til et oppdrag lagres som oppdragets «opprettet», siden id-er endres ved import. */
@Serializable
data class PaminnelseJson(
    val tekst: String = "",
    val dato: String? = null,
    val tid: String? = null,
    val oppdragOpprettet: String? = null,
    val ferdig: Boolean = false,
    val ferdigDato: String? = null,
    val opprettet: String,
)

@Serializable
data class SikkerhetskopiFil(
    val app: String = "Oppdragsliste",
    val versjon: Int = 2,
    val eksportert: String,
    val oppdrag: List<OppdragJson>,
    val paminnelser: List<PaminnelseJson> = emptyList(),
)

/** En påminnelse fra en sikkerhetskopi, før den er koblet til oppdragets nye id. */
data class PaminnelseImport(val paminnelse: Paminnelse, val oppdragOpprettet: Instant?)

private val json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun Oppdrag.tilJson() = OppdragJson(
    beskrivelse = beskrivelse, kunde = kunde, anlegg = anlegg, kontaktperson = kontaktperson, telefon = telefon, sted = sted, adresse = adresse,
    lat = lat, lng = lng, prioritet = prioritet.name, dato = dato?.toString(), tid = tid?.toString(), sluttid = sluttid?.toString(),
    opprettet = opprettet.toString(), ferdig = ferdig, ferdigDato = ferdigDato?.toString(),
)

fun OppdragJson.tilOppdrag() = Oppdrag(
    beskrivelse = beskrivelse, kunde = kunde, anlegg = anlegg, kontaktperson = kontaktperson, telefon = telefon, sted = sted, adresse = adresse,
    lat = lat, lng = lng,
    prioritet = runCatching { Prioritet.valueOf(prioritet) }.getOrDefault(Prioritet.OMRADE),
    dato = dato?.let(LocalDate::parse),
    tid = tid?.let(LocalTime::parse),
    sluttid = sluttid?.let(LocalTime::parse),
    opprettet = Instant.parse(opprettet),
    ferdig = ferdig,
    ferdigDato = ferdigDato?.let(Instant::parse),
)

fun eksporter(oppdrag: List<Oppdrag>, paminnelser: List<Paminnelse> = emptyList(), na: Instant = Instant.now()): String {
    val opprettet = oppdrag.associate { it.id to it.opprettet.toString() }
    return json.encodeToString(
        SikkerhetskopiFil(
            eksportert = na.toString(),
            oppdrag = oppdrag.map { it.tilJson() },
            paminnelser = paminnelser.map { p ->
                PaminnelseJson(
                    tekst = p.tekst, dato = p.dato?.toString(), tid = p.tid?.toString(),
                    oppdragOpprettet = p.oppdragId?.let { opprettet[it] }, ferdig = p.ferdig,
                    ferdigDato = p.ferdigDato?.toString(), opprettet = p.opprettet.toString(),
                )
            },
        ),
    )
}

/** Påminnelsene i en sikkerhetskopi (tom liste for eldre filer uten påminnelser). */
fun lesPaminnelser(tekst: String): List<PaminnelseImport> =
    json.decodeFromString<SikkerhetskopiFil>(tekst).paminnelser.map { p ->
        PaminnelseImport(
            Paminnelse(
                tekst = p.tekst, dato = p.dato?.let(LocalDate::parse), tid = p.tid?.let(LocalTime::parse),
                ferdig = p.ferdig, ferdigDato = p.ferdigDato?.let(Instant::parse), opprettet = Instant.parse(p.opprettet),
            ),
            p.oppdragOpprettet?.let(Instant::parse),
        )
    }

/**
 * Påminnelser som ikke finnes fra før (samme tekst og opprettet), koblet til oppdragene slik de nå ligger
 * i databasen ([alleOppdrag], etter at oppdragene fra filen er satt inn).
 */
fun nyePaminnelser(eksisterende: List<Paminnelse>, importerte: List<PaminnelseImport>, alleOppdrag: List<Oppdrag>): List<Paminnelse> {
    val finnes = eksisterende.map { it.opprettet.toEpochMilli() to it.tekst }.toMutableSet()
    val oppdragId = alleOppdrag.associate { it.opprettet.toEpochMilli() to it.id }
    return importerte.filter { finnes.add(it.paminnelse.opprettet.toEpochMilli() to it.paminnelse.tekst) }
        .map { i -> i.paminnelse.copy(id = 0, oppdragId = i.oppdragOpprettet?.let { oppdragId[it.toEpochMilli()] }) }
}

/** Leser en sikkerhetskopi. Kaster unntak hvis filen ikke er gyldig. */
fun les(tekst: String): List<Oppdrag> =
    json.decodeFromString<SikkerhetskopiFil>(tekst).oppdrag.map { it.tilOppdrag() }

/** Samme oppdrag: lagt inn samtidig og med samme innhold. Brukes for å ikke importere duplikater. */
private fun Oppdrag.nokkel() = listOf(opprettet.toEpochMilli(), beskrivelse, kunde, anlegg, telefon, sted, adresse).joinToString("\u0000")

/** Oppdragene i [importerte] som ikke allerede finnes, klare til å settes inn (med ny id). */
fun nyeOppdrag(eksisterende: List<Oppdrag>, importerte: List<Oppdrag>): List<Oppdrag> {
    val finnes = eksisterende.map { it.nokkel() }.toMutableSet()
    return importerte.filter { finnes.add(it.nokkel()) }.map { it.copy(id = 0) }
}
