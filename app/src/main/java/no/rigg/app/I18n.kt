package no.rigg.app

import java.util.Locale

/**
 * Språk: appen er engelsk i bunn, og norsk når telefonens systemspråk er norsk (bokmål, nynorsk eller
 * det eldre "no"). Alle andre språk får engelsk.
 *
 * Tekster skrives rett i koden som tr("English", "Norsk"), så de to språkene står ved siden av hverandre.
 * Endres systemspråket, starter Android aktiviteten på nytt og tekstene hentes på nytt.
 */
object Lang {
    val isNorwegian: Boolean
        get() = Locale.getDefault().language in setOf("nb", "nn", "no")
}

fun tr(en: String, no: String): String = if (Lang.isNorwegian) no else en

/** En tekst på begge språk, for data som lagres i lister (øvelser, utstyr osv.). */
data class Txt(val en: String, val no: String) {
    fun get(): String = tr(en, no)
}
