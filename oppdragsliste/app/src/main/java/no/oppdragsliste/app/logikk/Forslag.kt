package no.oppdragsliste.app.logikk

import no.oppdragsliste.app.data.KjentAnlegg

private fun String.norm() = trim().lowercase()

/**
 * Rangerer [navn] mot det som er skrevet: starter med teksten → et ord starter med teksten → inneholder
 * teksten. Innenfor samme rang beholdes rekkefølgen (sist brukt først). Det som allerede står i feltet,
 * foreslås ikke.
 */
private fun rang(navn: String, t: String): Int? {
    val x = navn.norm()
    return when {
        t.isEmpty() -> 0
        x.startsWith(t) -> 0
        x.split(Regex("[\\s\\-/,.]+")).any { it.startsWith(t) } -> 1
        x.contains(t) -> 2
        else -> null
    }
}

/** Som [ranger], men for elementer med et navn ([navn]) og noe mer. Duplikater (samme nøkkel) fjernes. */
private fun <T> rangerAlle(elementer: List<T>, tekst: String, maks: Int, navn: (T) -> String, nokkel: (T) -> String): List<T> {
    val t = tekst.norm()
    return elementer.filter { navn(it).isNotBlank() && navn(it).norm() != t }
        .distinctBy(nokkel)
        .mapNotNull { e -> rang(navn(e), t)?.let { e to it } }
        .sortedBy { it.second } // stabil sortering: sist brukt først innenfor hver rang
        .map { it.first }
        .take(maks)
}

private fun ranger(navn: List<String>, tekst: String, maks: Int): List<String> =
    rangerAlle(navn, tekst, maks, { it }, { it.norm() })

/** Kundeforslag mens man skriver. [kjente] må være sortert sist brukt først. Tomt felt gir ingen forslag. */
fun kundeForslag(kjente: List<KjentAnlegg>, tekst: String, maks: Int = 5): List<String> =
    if (tekst.isBlank()) emptyList() else ranger(kjente.map { it.kunde.trim() }, tekst, maks)

/** Et anleggsforslag. [kunde] er satt når anlegget hører til en annen kunde enn den i kundefeltet. */
data class AnleggForslag(val anlegg: String, val kunde: String? = null) {
    val tekst: String get() = if (kunde.isNullOrBlank()) anlegg else "$anlegg · $kunde"
}

/**
 * Anleggsforslag.
 * - Med kunde: kundens anlegg først (alle når feltet er tomt). Når noe er skrevet, kommer også
 *   anlegg fra andre kunder etterpå, merket med kunden (flere kunder kan ha samme anlegg).
 * - Uten kunde: søk i alle anlegg, merket med kunden, så kunden kan fylles inn når et velges.
 */
fun anleggForslag(kjente: List<KjentAnlegg>, kunde: String, tekst: String, maks: Int = 6): List<AnleggForslag> {
    val k = kunde.norm()
    if (k.isEmpty() && tekst.isBlank()) return emptyList()
    val andre = if (tekst.isBlank()) emptyList() else rangerAlle(
        kjente.filter { it.kunde.norm() != k },
        tekst, maks, { it.anlegg.trim() }, { it.anlegg.norm() + "\u0000" + it.kunde.norm() },
    ).map { AnleggForslag(it.anlegg.trim(), it.kunde.trim().ifBlank { null }) }
    if (k.isEmpty()) return andre
    val egne = ranger(kjente.filter { it.kunde.norm() == k }.map { it.anlegg.trim() }, tekst, maks).map { AnleggForslag(it) }
    val egneNavn = egne.map { it.anlegg.norm() }.toSet()
    return (egne + andre.filter { it.anlegg.norm() !in egneNavn }).take(maks)
}

/**
 * Hva som skal fylles inn.
 * - Kjent kombinasjon av kunde og anlegg: alt derfra.
 * - Ellers: kontaktperson og telefon fra det som sist ble brukt for kunden, sted og adresse fra det som sist ble brukt
 *   for anlegget (uansett kunde, siden flere kunder kan ha samme anlegg). Uten anlegg: alt fra kunden.
 *   Ukjent kunde gir tom telefon, ukjent anlegg tomt sted, så det som ble fylt inn fra før forsvinner.
 * Null hvis verken kunden eller anlegget er kjent.
 */
fun finnUtfylling(kjente: List<KjentAnlegg>, kunde: String, anlegg: String): KjentAnlegg? {
    val k = kunde.norm()
    val a = anlegg.norm()
    if (k.isEmpty() && a.isEmpty()) return null
    kjente.firstOrNull { it.kunde.norm() == k && it.anlegg.norm() == a }?.let { return it }
    val kundeSist = if (k.isEmpty()) null else kjente.filter { it.kunde.norm() == k }.maxByOrNull { it.sistBrukt }
    val anleggSist = if (a.isEmpty()) null else kjente.filter { it.anlegg.norm() == a }.maxByOrNull { it.sistBrukt }
    if (kundeSist == null && anleggSist == null) return null
    // Kontaktperson og telefon hører sammen og følger kunden.
    val kontaktKilde = if (k.isNotEmpty()) kundeSist else anleggSist
    val stedKilde = if (a.isNotEmpty()) anleggSist else kundeSist
    return KjentAnlegg(
        kunde = kunde.trim(), anlegg = anlegg.trim(),
        kontaktperson = kontaktKilde?.kontaktperson.orEmpty(), telefon = kontaktKilde?.telefon.orEmpty(),
        sted = stedKilde?.sted.orEmpty(), adresse = stedKilde?.adresse.orEmpty(),
        lat = stedKilde?.lat, lng = stedKilde?.lng,
        // Notatet hører til stedet (adgangskoder o.l.), som sted og adresse.
        notat = stedKilde?.notat.orEmpty(),
        sistBrukt = listOfNotNull(kundeSist?.sistBrukt, anleggSist?.sistBrukt).max(),
    )
}

/** Finner kunde og anlegg for et telefonnummer (f.eks. etter en samtale), sist brukt først. */
fun finnPaTelefon(kjente: List<KjentAnlegg>, telefon: String): KjentAnlegg? {
    val siffer = telefon.filter { it.isDigit() }.takeLast(8)
    if (siffer.length < 8) return null
    return kjente.filter { it.telefon.filter { c -> c.isDigit() }.takeLast(8) == siffer }.maxByOrNull { it.sistBrukt }
}

private fun siffer8(t: String) = t.filter { it.isDigit() }.takeLast(8)

/**
 * Kunde/anlegg en som ringte er registrert på før, sist brukt først: treff på telefonnummer, og på
 * kontaktperson med samme navn (f.eks. når samme person ringer fra et annet nummer). Én per kunde+anlegg.
 */
fun kandidaterForSamtale(kjente: List<KjentAnlegg>, telefon: String, navn: String?, maks: Int = 3): List<KjentAnlegg> {
    val nr = siffer8(telefon)
    val n = navn?.trim()?.lowercase().orEmpty()
    return kjente
        .filter { k ->
            (nr.length == 8 && siffer8(k.telefon) == nr) ||
                (n.isNotEmpty() && k.kontaktperson.trim().lowercase() == n)
        }
        .filter { it.kunde.isNotBlank() || it.anlegg.isNotBlank() }
        .sortedByDescending { it.sistBrukt }
        .distinctBy { it.kunde.trim().lowercase() + "\u0000" + it.anlegg.trim().lowercase() }
        .take(maks)
}

/** Nøkkel for kunde + anlegg, uansett store/små bokstaver og mellomrom rundt. */
fun anleggNokkel(kunde: String, anlegg: String): String = kunde.trim().lowercase() + "\u0000" + anlegg.trim().lowercase()

/** Notatet for akkurat denne kunden og dette anlegget (tom tekst hvis ingen). */
fun notatFor(kjente: List<KjentAnlegg>, kunde: String, anlegg: String): String {
    if (kunde.isBlank() && anlegg.isBlank()) return ""
    val n = anleggNokkel(kunde, anlegg)
    return kjente.firstOrNull { anleggNokkel(it.kunde, it.anlegg) == n }?.notat.orEmpty()
}

/** Felt som kan fylles inn automatisk. */
enum class Autofelt { KONTAKTPERSON, TELEFON, STED, ADRESSE, NOTAT }

/**
 * Ny verdi for et felt: overskriv bare hvis feltet er tomt eller fortsatt har verdien vi selv fylte inn
 * sist (det brukeren har skrevet selv, eller nummeret fra en samtale, rører vi ikke).
 */
fun nyVerdi(naa: String, sistAutofylt: String?, forslag: String): String =
    if (naa.isBlank() || (sistAutofylt != null && naa == sistAutofylt)) forslag else naa
