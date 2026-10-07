package no.oppdragsliste.app.logikk

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Koblingen mellom et oppdrag og hendelsen i kalenderen, slik den var ved forrige synk:
 * hendelsens id, kalenderen, signaturen til det appen skrev, og start/heldag slik kalenderen hadde det.
 * [start] er null for koblinger lagret før toveis synk.
 */
data class Kobling(
    val hendelse: Long,
    val kalender: Long,
    val signatur: String,
    val start: Long? = null,
    val heldag: Boolean? = null,
) {
    fun tilTekst() = "$hendelse:$kalender:$signatur:${start ?: ""}:${heldag ?: ""}"

    companion object {
        fun fraTekst(tekst: String): Kobling? {
            val d = tekst.split(":")
            if (d.size < 3) return null
            return Kobling(
                hendelse = d[0].toLongOrNull() ?: return null,
                kalender = d[1].toLongOrNull() ?: return null,
                signatur = d[2],
                start = d.getOrNull(3)?.toLongOrNull(),
                heldag = d.getOrNull(4)?.toBooleanStrictOrNull(),
            )
        }
    }
}

/** Hendelsen slik den ligger i kalenderen nå. */
data class Kalenderstand(val start: Long, val heldag: Boolean)

enum class Synkhandling {
    /** Lag ny hendelse. */
    OPPRETT,
    /** Flytt hendelsen til en annen kalender (slett og opprett). */
    FLYTT_KALENDER,
    /** Skriv appens versjon til kalenderen. */
    OPPDATER_KALENDER,
    /** Hendelsen er flyttet i kalenderen: ta med dato/klokkeslett inn i appen. */
    OPPDATER_APP,
    /** Bare lagre hvordan hendelsen ser ut nå (koblinger fra før toveis synk). */
    HUSK_STAND,
    INGEN,
}

/**
 * Hva som skal gjøres for ett oppdrag som skal ligge i kalenderen.
 * - Endret i appen siden sist: appen vinner, også om hendelsen samtidig er flyttet i kalenderen.
 * - Bare flyttet i kalenderen: dato og klokkeslett tas inn i appen.
 * - Hendelsen slettet i kalenderen: la den være slettet til avtalen endres i appen.
 */
fun velgSynkhandling(kobling: Kobling?, valgtKalender: Long, appSignatur: String, stand: Kalenderstand?): Synkhandling {
    if (kobling == null) return Synkhandling.OPPRETT
    if (kobling.kalender != valgtKalender) return Synkhandling.FLYTT_KALENDER
    val appEndret = kobling.signatur != appSignatur
    if (stand == null) return if (appEndret) Synkhandling.OPPRETT else Synkhandling.INGEN
    if (appEndret) return Synkhandling.OPPDATER_KALENDER
    if (kobling.start == null || kobling.heldag == null) return Synkhandling.HUSK_STAND
    val kalenderEndret = stand.start != kobling.start || stand.heldag != kobling.heldag
    return if (kalenderEndret) Synkhandling.OPPDATER_APP else Synkhandling.INGEN
}

/** Dato og klokkeslett fra en kalenderhendelse. Heldagshendelser lagres ved midnatt UTC. */
fun fraKalender(stand: Kalenderstand, sone: ZoneId = ZoneId.systemDefault()): Pair<LocalDate, LocalTime?> {
    val tidspunkt = Instant.ofEpochMilli(stand.start)
    return if (stand.heldag) {
        tidspunkt.atZone(ZoneOffset.UTC).toLocalDate() to null
    } else {
        val lokal = tidspunkt.atZone(sone)
        lokal.toLocalDate() to lokal.toLocalTime().withSecond(0).withNano(0)
    }
}
