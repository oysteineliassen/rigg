package no.oppdragsliste.app.logikk

import no.oppdragsliste.app.data.KjentAnlegg

private fun String.norm() = trim().lowercase()

/**
 * Rangerer [navn] mot det som er skrevet: starter med teksten → et ord starter med teksten → inneholder
 * teksten. Innenfor samme rang beholdes rekkefølgen (sist brukt først). Det som allerede står i feltet,
 * foreslås ikke.
 */
private fun ranger(navn: List<String>, tekst: String, maks: Int): List<String> {
    val t = tekst.norm()
    val unike = navn.filter { it.isNotBlank() }.distinctBy { it.norm() }.filter { it.norm() != t }
    if (t.isEmpty()) return unike.take(maks)
    fun rang(n: String): Int? {
        val x = n.norm()
        return when {
            x.startsWith(t) -> 0
            x.split(Regex("[\\s\\-/,.]+")).any { it.startsWith(t) } -> 1
            x.contains(t) -> 2
            else -> null
        }
    }
    return unike.mapNotNull { n -> rang(n)?.let { n to it } }
        .sortedBy { it.second } // stabil sortering: sist brukt først innenfor hver rang
        .map { it.first }
        .take(maks)
}

/** Kundeforslag mens man skriver. [kjente] må være sortert sist brukt først. Tomt felt gir ingen forslag. */
fun kundeForslag(kjente: List<KjentAnlegg>, tekst: String, maks: Int = 5): List<String> =
    if (tekst.isBlank()) emptyList() else ranger(kjente.map { it.kunde.trim() }, tekst, maks)

/**
 * Anleggsforslag for kunden. Er feltet tomt, vises alle anleggene til kunden (sist brukt først).
 * Uten kunde foreslås anlegg fra alle kunder, men bare når noe er skrevet.
 */
fun anleggForslag(kjente: List<KjentAnlegg>, kunde: String, tekst: String, maks: Int = 6): List<String> {
    val k = kunde.norm()
    if (k.isEmpty() && tekst.isBlank()) return emptyList()
    val aktuelle = if (k.isEmpty()) kjente else kjente.filter { it.kunde.norm() == k }
    return ranger(aktuelle.map { it.anlegg.trim() }, tekst, maks)
}

/**
 * Hva som skal fylles inn. Med anlegg: akkurat den kunden og det anlegget. Uten anlegg: det som sist ble
 * brukt for kunden (uansett anlegg). Null hvis ingenting passer.
 */
fun finnUtfylling(kjente: List<KjentAnlegg>, kunde: String, anlegg: String): KjentAnlegg? {
    val k = kunde.norm()
    val a = anlegg.norm()
    if (k.isEmpty() && a.isEmpty()) return null
    return if (a.isNotEmpty()) {
        kjente.firstOrNull { it.kunde.norm() == k && it.anlegg.norm() == a }
    } else {
        kjente.filter { it.kunde.norm() == k }.maxByOrNull { it.sistBrukt }
    }
}

/** Finner kunde og anlegg for et telefonnummer (f.eks. etter en samtale), sist brukt først. */
fun finnPaTelefon(kjente: List<KjentAnlegg>, telefon: String): KjentAnlegg? {
    val siffer = telefon.filter { it.isDigit() }.takeLast(8)
    if (siffer.length < 8) return null
    return kjente.filter { it.telefon.filter { c -> c.isDigit() }.takeLast(8) == siffer }.maxByOrNull { it.sistBrukt }
}

/** Felt som kan fylles inn automatisk. */
enum class Autofelt { TELEFON, STED, ADRESSE }

/**
 * Ny verdi for et felt: overskriv bare hvis feltet er tomt eller fortsatt har verdien vi selv fylte inn
 * sist (det brukeren har skrevet selv, eller nummeret fra en samtale, rører vi ikke).
 */
fun nyVerdi(naa: String, sistAutofylt: String?, forslag: String): String =
    if (naa.isBlank() || (sistAutofylt != null && naa == sistAutofylt)) forslag else naa
