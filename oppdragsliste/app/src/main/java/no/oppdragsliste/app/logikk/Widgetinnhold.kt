package no.oppdragsliste.app.logikk

import no.oppdragsliste.app.data.Oppdrag
import no.oppdragsliste.app.data.Paminnelse
import no.oppdragsliste.app.data.Prioritet
import java.time.LocalDateTime

/** Hva en rad i widgeten gjelder (bestemmer fargen på kanten). */
enum class Radtype { AKUTT, AVTALE, HASTER, PAMINNELSE }

data class WidgetRad(
    val tittel: String,
    /** Til høyre: klokkeslett, «i morgen», «Akutt» osv. */
    val nar: String,
    val type: Radtype,
    val forfalt: Boolean = false,
)

data class Widgetinnhold(val oppsummering: String, val rader: List<WidgetRad>)

/**
 * Det widgeten viser: det som haster i dag, i denne rekkefølgen: forfalte avtaler og påminnelser, akutte
 * (eldste først), dagens avtaler og påminnelser (etter klokkeslett), morgendagens avtaler, og til slutt
 * det som haster. «Når i området» telles bare i oppsummeringen.
 */
fun widgetinnhold(oppdrag: List<Oppdrag>, paminnelser: List<Paminnelse>, na: LocalDateTime, maks: Int = 20): Widgetinnhold {
    val idag = na.toLocalDate()
    val imorgen = idag.plusDays(1)
    val apne = oppdrag.filter { !it.ferdig }
    val apneP = paminnelser.filter { !it.ferdig }
    val avtaler = sorter(Prioritet.AVTALE, apne.filter { it.prioritet == Prioritet.AVTALE })

    fun avtaleRad(o: Oppdrag, nar: String) = WidgetRad(o.tittel, nar, Radtype.AVTALE, erForfalt(o, na))
    fun paminnelseRad(p: Paminnelse) = WidgetRad(
        p.tekst.lines().first(),
        p.tid?.let { formaterTid(it) } ?: "i dag",
        Radtype.PAMINNELSE,
        erForfalt(p, na),
    )

    val rader = buildList {
        // Forfalt (dato før i dag).
        avtaler.filter { it.dato?.isBefore(idag) == true }.forEach { o -> add(avtaleRad(o, formaterDato(o.dato!!, idag))) }
        apneP.filter { it.dato?.isBefore(idag) == true }.sortedBy { it.tidspunkt }.forEach { p ->
            add(WidgetRad(p.tekst.lines().first(), formaterDato(p.dato!!, idag), Radtype.PAMINNELSE, forfalt = true))
        }
        // Akutt.
        sorter(Prioritet.AKUTT, apne.filter { it.prioritet == Prioritet.AKUTT })
            .forEach { add(WidgetRad(it.tittel, "Akutt", Radtype.AKUTT)) }
        // I dag: avtaler og påminnelser flettet etter klokkeslett (uten klokkeslett sist).
        val idagAvtaler = avtaler.filter { it.dato == idag }.map { o ->
            (o.tid ?: java.time.LocalTime.MAX) to avtaleRad(o, o.tid?.let { formaterTidsrom(it, o.sluttid) } ?: "i dag")
        }
        val idagP = apneP.filter { it.dato == idag }.map { p -> (p.tid ?: java.time.LocalTime.of(8, 0)) to paminnelseRad(p) }
        (idagAvtaler + idagP).sortedBy { it.first }.forEach { add(it.second) }
        // I morgen.
        avtaler.filter { it.dato == imorgen }
            .forEach { o -> add(avtaleRad(o, "i morgen" + (o.tid?.let { " " + formaterTid(it) } ?: ""))) }
        // Haster.
        sorter(Prioritet.HASTER, apne.filter { it.prioritet == Prioritet.HASTER })
            .forEach { add(WidgetRad(it.tittel, "Haster", Radtype.HASTER)) }
    }

    val akutte = apne.count { it.prioritet == Prioritet.AKUTT }
    val idagN = avtaler.count { it.dato == idag } + apneP.count { it.dato == idag }
    val forfalte = avtaler.count { it.dato?.isBefore(idag) == true }
    val omrade = apne.count { it.prioritet == Prioritet.OMRADE }
    val deler = buildList {
        if (forfalte > 0) add("$forfalte forfalt")
        if (akutte > 0) add(if (akutte == 1) "1 akutt" else "$akutte akutte")
        if (idagN > 0) add("$idagN i dag")
        if (omrade > 0) add("$omrade i området")
    }
    return Widgetinnhold(
        oppsummering = deler.joinToString(" · ").ifBlank { if (apne.isEmpty()) "Ingen åpne oppdrag" else "Ingenting i dag" },
        rader = rader.take(maks),
    )
}
