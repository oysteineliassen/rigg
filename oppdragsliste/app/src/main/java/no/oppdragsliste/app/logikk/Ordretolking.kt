package no.oppdragsliste.app.logikk

import java.time.LocalDate

/** En sak lest ut av et skjermbilde av ordrelisten. */
data class TolketOrdre(
    val saksnr: String,
    val tittel: String,
    val adresse: String = "",
    val kundenr: String = "",
    val kunde: String = "",
    val dato: LocalDate? = null,
    val tid: String? = null,
    val status: String? = null,
)

private val maneder = listOf(
    "januar", "februar", "mars", "april", "mai", "juni",
    "juli", "august", "september", "oktober", "november", "desember",
)

/**
 * «SAK : 42154 - TITTEL». Tekstgjenkjenningen setter av og til mellomrom inne i tallet («421 54»), så sifre med
 * enkle mellomrom før bindestreken regnes som ett nummer.
 */
private val sakMedStrekRegex = Regex("""^\s*SAK\s*[:;.]?\s*((?:\d ?){3,8})\s*[-–—]\s*(.*)$""", RegexOption.IGNORE_CASE)
private val sakRegex = Regex("""^\s*SAK\s*[:;.]?\s*(\d{3,7})\s*[-–—]?\s*(.*)$""", RegexOption.IGNORE_CASE)
/** Ordre uten saksnummer har P-nummer: «P134940 KUNDE : PROSJEKT». */
private val pRegex = Regex("""^\s*(P\d{6})\b\s*[-–:]?\s*(.*)$""")

private val datoRegex = Regex("""^(man|tir|ons|tor|fre|lør|lor|søn|son)[a-zæøå]*\.?\s+(\d{1,2})\.?\s+([a-zæøå]+)\s+(\d{4})$""", RegexOption.IGNORE_CASE)
private val tidRegex = Regex("""(\d{1,2})[:.](\d{2})\s*[-–—]\s*(\d{1,2})[:.](\d{2})""")
private val statusRegex = Regex("""^(pågår|pagar|pågar|ny|ferdig|planlagt|avsluttet|utført|fullført|venter|ikke startet|ikke påbegynt)$""")

// Mønstrene under sammenlignes med små bokstaver: Javas IGNORE_CASE gjelder ikke Æ, Ø og Å.

/** Internt arbeid som ikke faktureres, og skal ikke med i oversikten. */
private val adminRegex = Regex("""\bADMIN\s*(OPPGAVE|TASK)\b""", RegexOption.IGNORE_CASE)
private val prioritetRegex = Regex("""^(lav|middels|høy|hoy|kritisk|akutt)$""")
private val kundeRegex = Regex("""Kunde\s*[:;]?\s*(K\d+)?\s*(.*)$""", RegexOption.IGNORE_CASE)

/**
 * Samme sak lest fra to bilder. En sak som ble lest helt (med status nederst) er til å stole på og vinner;
 * en som er kuttet av, kan inneholde feillest tekst fra kanten av bildet og brukes bare til å fylle hull.
 */
fun slaSammenTolket(a: TolketOrdre, b: TolketOrdre): TolketOrdre {
    if (a.status != null && b.status == null) return fyllHull(a, b)
    if (b.status != null && a.status == null) return fyllHull(b, a)
    fun lengst(x: String, y: String) = if (y.length > x.length) y else x
    return a.copy(
        tittel = lengst(a.tittel, b.tittel),
        adresse = lengst(a.adresse, b.adresse),
        kundenr = lengst(a.kundenr, b.kundenr),
        kunde = lengst(a.kunde, b.kunde),
        dato = a.dato ?: b.dato,
        tid = a.tid ?: b.tid,
        status = a.status ?: b.status,
    )
}

/** [hel] med tomme felt fylt fra [annen]. */
private fun fyllHull(hel: TolketOrdre, annen: TolketOrdre) = hel.copy(
    tittel = hel.tittel.ifBlank { annen.tittel },
    adresse = hel.adresse.ifBlank { annen.adresse },
    kundenr = hel.kundenr.ifBlank { annen.kundenr },
    kunde = hel.kunde.ifBlank { annen.kunde },
    dato = hel.dato ?: annen.dato,
    tid = hel.tid ?: annen.tid,
)

/**
 * Leser saker ut av teksten i et skjermbilde av ordrelisten (linje for linje, ovenfra og ned).
 * Formatet er: valgfri datooverskrift («TOR. 8. OKTOBER 2026»), valgfri prioritet og tid («Middels 07:00–15:00»),
 * «SAK : 45102 - TITTEL I STORE BOKSTAVER» (kan gå over flere linjer), adresse og «Kunde : K110771 Navn»,
 * og til slutt status («Pågår»). Saker som er kuttet av i toppen av bildet (uten «SAK»-linjen) hoppes over.
 */
fun tolkOrdreliste(linjer: List<String>): List<TolketOrdre> {
    val resultat = mutableListOf<TolketOrdre>()
    var dato: LocalDate? = null
    var ventendeTid: String? = null

    var saksnr: String? = null
    var sakDato: LocalDate? = null
    var sakTid: String? = null
    var status: String? = null
    val tittel = mutableListOf<String>()
    val resten = mutableListOf<String>()
    var iTittel = false

    fun avslutt() {
        val nr = saksnr ?: return
        if (adminRegex.containsMatchIn(tittel.joinToString(" "))) {
            // Admin oppgave / admin task: hopp over.
            saksnr = null
            tittel.clear()
            resten.clear()
            status = null
            iTittel = false
            return
        }
        // «Prosjektoppgave : Prosjekt» under P-nummer er ingen adresse.
        val rest = resten.joinToString(" ").replace(Regex("\\s+"), " ").trim()
            .let { if (it.startsWith("Prosjektoppgave", ignoreCase = true)) "" else it }
        val kundeTreff = kundeRegex.find(rest)
        val kundenr = kundeTreff?.groupValues?.get(1).orEmpty()
        val kunde = kundeTreff?.groupValues?.get(2).orEmpty().trim().trim(',', ':', ' ')
        var adresse = (if (kundeTreff != null) rest.substring(0, kundeTreff.range.first) else rest).trim().trim(',', ':', ' ')
        // «Dvergsnesbakken 22 : Dvergsnesbakken 22 4639 KRISTIANSAND S NORWAY»: den fullstendige adressen står sist.
        if (adresse.contains(" : ")) adresse = adresse.substringAfterLast(" : ").trim()
        adresse = adresse.replace(Regex("""[,\s]*(Norge|Norway)\s*$""", RegexOption.IGNORE_CASE), "").trim().trim(',')
        var heleTittel = tittel.joinToString(" ").replace(Regex("\\s+"), " ").trim()
        var kundeNavn = kunde
        // P-nummer: «ROGER AAMODT EIENDOM AS : BYDALSLIA 28-36» – kunden står foran « : ».
        if (nr.startsWith("P") && kundeNavn.isBlank() && heleTittel.contains(" : ")) {
            kundeNavn = heleTittel.substringBefore(" : ").trim()
            heleTittel = heleTittel.substringAfter(" : ").trim()
        }
        resultat += TolketOrdre(
            saksnr = nr,
            tittel = heleTittel,
            adresse = adresse,
            kundenr = kundenr,
            kunde = kundeNavn,
            dato = sakDato,
            tid = sakTid,
            status = status,
        )
        saksnr = null
        tittel.clear()
        resten.clear()
        status = null
        iTittel = false
    }

    for (rå in linjer) {
        val linje = rå.trim().replace('—', '–')
        if (linje.isEmpty()) continue
        // Løse tall på 1–3 sifre (f.eks. fanetelleren i nettleseren, «86») hører ikke til noen sak.
        if (linje.matches(Regex("""\d{1,3}"""))) continue

        val datoTreff = datoRegex.matchEntire(linje.lowercase())
        if (datoTreff != null) {
            avslutt()
            ventendeTid = null // tiden står under datooverskriften, så en tid fra før gjelder ikke
            val mnd = maneder.indexOf(datoTreff.groupValues[3].lowercase())
            dato = if (mnd >= 0) {
                runCatching { LocalDate.of(datoTreff.groupValues[4].toInt(), mnd + 1, datoTreff.groupValues[2].toInt()) }.getOrNull()
            } else {
                null
            }
            continue
        }

        val sak = sakMedStrekRegex.matchEntire(linje) ?: sakRegex.matchEntire(linje) ?: pRegex.matchEntire(linje)
        if (sak != null) {
            avslutt()
            saksnr = sak.groupValues[1].filter { !it.isWhitespace() }
            sakDato = dato
            sakTid = ventendeTid
            ventendeTid = null
            if (sak.groupValues[2].isNotBlank()) tittel += sak.groupValues[2]
            iTittel = true
            continue
        }

        // Prioritet og tid står over neste sak.
        val tid = tidRegex.find(linje)
        val utenTid = tid?.let { linje.removeRange(it.range).trim() } ?: linje
        if (tid != null && (utenTid.isEmpty() || prioritetRegex.matches(utenTid.lowercase()))) {
            avslutt()
            ventendeTid = "%02d:%s–%02d:%s".format(
                tid.groupValues[1].toInt(), tid.groupValues[2], tid.groupValues[3].toInt(), tid.groupValues[4],
            )
            continue
        }
        if (prioritetRegex.matches(linje.lowercase())) {
            avslutt()
            continue
        }
        if (statusRegex.matches(linje.lowercase())) {
            if (saksnr != null) {
                status = linje
                avslutt()
            }
            continue
        }
        // «ADMIN OPPGAVE» uten saksnummer: en egen oppføring som avslutter forrige sak og ignoreres,
        // sammen med tiden som sto over den.
        if (adminRegex.containsMatchIn(linje) && (saksnr == null || !iTittel)) {
            avslutt()
            ventendeTid = null
            continue
        }
        if (saksnr == null) continue // rester av en sak som er kuttet av i toppen, eller admin oppgave
        // Tittelen er i store bokstaver; første linje med små bokstaver (eller som starter med «:») er adressen.
        if (iTittel && !linje.startsWith(":") && linje.none { it.isLowerCase() }) {
            tittel += linje
        } else {
            iTittel = false
            resten += linje
        }
    }
    avslutt()
    return resultat
}
