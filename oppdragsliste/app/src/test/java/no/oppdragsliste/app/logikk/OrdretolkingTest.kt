package no.oppdragsliste.app.logikk

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class OrdretolkingTest {
    // Slik tekstgjenkjenningen leser skjermbildet (linje for linje).
    private val skjermbilde = """
        7083689.extforms.netsuite.com/app/s
        ØE
        Søk
        SAK : 45102 - SERVICEAVTALE: SAMEIET
        MØLLA, NØDLYS OG BRANNALARMANLEGG -
        2026
        : Venneslavegen 153 VENNESLA 4708 Norge , Kunde
        : K110771 Sameiet Mølla
        Pågår
        Middels
        07:00–15:00
        SAK : 48226 - SERVICEAVTALE: HMH WIRTH,
        DVERGSNES (DIVERSE BYGG), 2026
        Dvergsnesbakken 22 : Dvergsnesbakken 22 4639
        KRISTIANSAND S NORWAY , Kunde : K103846
        MHWirth AS
        Pågår
        TOR. 8. OKTOBER 2026
        Middels 08:00–12:00
        SAK : 49574 - REKLAMSJON 2 STK NØDLYS
        : Revia 3 4700 VENNESLA NORWAY , Kunde :
        K110556 Sameiet Vennesla Atrium
    """.trimIndent().lines()

    @Test
    fun `leser sakene i skjermbildet`() {
        val saker = tolkOrdreliste(skjermbilde)
        assertEquals(listOf("45102", "48226", "49574"), saker.map { it.saksnr })

        val molla = saker[0]
        assertEquals("SERVICEAVTALE: SAMEIET MØLLA, NØDLYS OG BRANNALARMANLEGG - 2026", molla.tittel)
        assertEquals("Venneslavegen 153 VENNESLA 4708", molla.adresse)
        assertEquals("K110771", molla.kundenr)
        assertEquals("Sameiet Mølla", molla.kunde)
        assertEquals("Pågår", molla.status)
        assertEquals(null, molla.tid)

        val wirth = saker[1]
        assertEquals("SERVICEAVTALE: HMH WIRTH, DVERGSNES (DIVERSE BYGG), 2026", wirth.tittel)
        assertEquals("Dvergsnesbakken 22 4639 KRISTIANSAND S", wirth.adresse)
        assertEquals("K103846", wirth.kundenr)
        assertEquals("MHWirth AS", wirth.kunde)
        assertEquals("07:00–15:00", wirth.tid)

        // Kuttet av nederst: ingen status, men resten er med; dato fra overskriften.
        val atrium = saker[2]
        assertEquals("REKLAMSJON 2 STK NØDLYS", atrium.tittel)
        assertEquals("Revia 3 4700 VENNESLA", atrium.adresse)
        assertEquals("Sameiet Vennesla Atrium", atrium.kunde)
        assertEquals(LocalDate.of(2026, 10, 8), atrium.dato)
        assertEquals("08:00–12:00", atrium.tid)
        assertEquals(null, atrium.status)
    }

    @Test
    fun `sak kuttet av i toppen hoppes over`() {
        val linjer = listOf("BRANNALARMANLEGG - 2026", ": Veien 1 Kunde : K1 Ola", "Pågår", "SAK : 12345 - TEST", "Pågår")
        assertEquals(listOf("12345"), tolkOrdreliste(linjer).map { it.saksnr })
    }

    @Test
    fun `tom tekst gir ingen saker`() = assertEquals(emptyList<TolketOrdre>(), tolkOrdreliste(listOf("Søk", "ØE")))

    @Test
    fun `admin oppgave ignoreres og ikke startet er status`() {
        val linjer = """
            SØN. 1. NOVEMBER 2026
            Middels
            08:00–12:00
            SAK : 51158 - SERVICEAVTALE: AVENYEN 14-
            24, 2026
            : Avenyen 14-24 4636 KRISTIANSAND S NORWAY ,
            Kunde : K103849 Mosvold Eiendomservice AS
            Ikke Startet
            MAN. 19. OKTOBER 2026
            Middels
            03:00–04:00
            ADMIN OPPGAVE
            Ikke Startet
            ONS. 14. OKTOBER 2026
            Middels 07:00–15:00
            SAK : 46257 - MES - MAGNUS BARFOTSVEI 9 -
            LUKKE AVVIK
            : Magnus Barfots vei 9 4633 KRISTIANSAND S
            NORWAY , Kunde : K103849 Mosvold
        """.trimIndent().lines()
        val saker = tolkOrdreliste(linjer)
        assertEquals(listOf("51158", "46257"), saker.map { it.saksnr })
        assertEquals("SERVICEAVTALE: AVENYEN 14- 24, 2026", saker[0].tittel)
        assertEquals("Ikke Startet", saker[0].status)
        assertEquals("Mosvold Eiendomservice AS", saker[0].kunde)
        assertEquals("Avenyen 14-24 4636 KRISTIANSAND S", saker[0].adresse)
        assertEquals(LocalDate.of(2026, 11, 1), saker[0].dato)
        assertEquals("MES - MAGNUS BARFOTSVEI 9 - LUKKE AVVIK", saker[1].tittel)
        assertEquals("Magnus Barfots vei 9 4633 KRISTIANSAND S", saker[1].adresse)
        assertEquals(LocalDate.of(2026, 10, 14), saker[1].dato)

        // Admin oppgave med saksnummer hoppes også over.
        assertEquals(emptyList<TolketOrdre>(), tolkOrdreliste(listOf("SAK : 999 - ADMIN TASK", "Pågår")))
    }
}
