package no.oppdragsliste.app.logikk

import no.oppdragsliste.app.data.Oppdrag
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields

/** En gruppe avtaler under «Avtalt tid», f.eks. «I dag» eller «Uke 43». */
data class Avtalegruppe(
    /** Stabil nøkkel, brukt til å huske om gruppen er åpen. */
    val nokkel: String,
    val tittel: String,
    /** Tilleggstekst i overskriften, f.eks. «19.–25. okt.». */
    val undertittel: String?,
    val oppdrag: List<Oppdrag>,
    /** Åpen når ingenting annet er valgt: forfalt, i dag, i morgen og resten av denne uka. */
    val apenSomStandard: Boolean,
    val forfalt: Boolean = false,
)

private val kortDato = DateTimeFormatter.ofPattern("d. MMM", norsk)

private fun ukeNokkel(d: LocalDate) = d.get(IsoFields.WEEK_BASED_YEAR) * 100 + d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

/** «19.–25. okt.» eller «29. sep.–5. okt.» for uka [d] ligger i (mandag–søndag). */
fun ukeSpenn(d: LocalDate): String {
    val man = d.minusDays((d.dayOfWeek.value - 1).toLong())
    val son = man.plusDays(6)
    return if (man.month == son.month) "${man.dayOfMonth}.–${son.format(kortDato)}" else "${man.format(kortDato)}–${son.format(kortDato)}"
}

/**
 * Deler avtalene (allerede sortert på dato og tid) i grupper: Forfalt, I dag, I morgen, resten av denne
 * uka, neste uke og deretter én gruppe per uke (ISO-uker, mandag–søndag). Tomme grupper tas ikke med.
 */
fun grupperAvtaler(avtaler: List<Oppdrag>, idag: LocalDate): List<Avtalegruppe> {
    val denneUka = ukeNokkel(idag)
    val nesteUka = ukeNokkel(idag.plusWeeks(1))
    val grupper = linkedMapOf<String, Avtalegruppe>()
    fun legg(nokkel: String, lag: () -> Avtalegruppe, o: Oppdrag) {
        val g = grupper[nokkel] ?: lag()
        grupper[nokkel] = g.copy(oppdrag = g.oppdrag + o)
    }
    for (o in avtaler) {
        val d = o.dato
        when {
            d == null -> legg("uten", { Avtalegruppe("uten", "Uten dato", null, emptyList(), true) }, o)
            d.isBefore(idag) -> legg("forfalt", { Avtalegruppe("forfalt", "Forfalt", null, emptyList(), true, forfalt = true) }, o)
            d == idag -> legg("idag", { Avtalegruppe("idag", "I dag", null, emptyList(), true) }, o)
            d == idag.plusDays(1) -> legg("imorgen", { Avtalegruppe("imorgen", "I morgen", null, emptyList(), true) }, o)
            else -> {
                val uke = ukeNokkel(d)
                val nr = d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
                val (tittel, apen) = when (uke) {
                    denneUka -> "Resten av uka" to true
                    nesteUka -> "Neste uke" to false
                    else -> "Uke $nr" to false
                }
                val under = if (uke == denneUka || uke == nesteUka) "uke $nr · ${ukeSpenn(d)}" else ukeSpenn(d)
                legg("uke-$uke", { Avtalegruppe("uke-$uke", tittel, under, emptyList(), apen) }, o)
            }
        }
    }
    // Rekkefølge: forfalt først, så i dag, i morgen og ukene i tidsrekkefølge, uten dato til slutt.
    val rang = listOf("forfalt", "idag", "imorgen")
    return grupper.values.sortedWith(
        compareBy<Avtalegruppe> { g ->
            when {
                g.nokkel in rang -> rang.indexOf(g.nokkel)
                g.nokkel == "uten" -> Int.MAX_VALUE
                else -> 10
            }
        }.thenBy { it.nokkel.removePrefix("uke-").toIntOrNull() ?: 0 },
    )
}
