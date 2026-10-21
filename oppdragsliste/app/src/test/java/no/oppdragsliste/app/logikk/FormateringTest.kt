package no.oppdragsliste.app.logikk

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class FormateringTest {
    @Test
    fun telefonnummer() {
        assertEquals("900 00 000", formaterTelefon("90000000"))
        assertEquals("900 00 000", formaterTelefon("+4790000000"))
        assertEquals("412 34 567", formaterTelefon("0047 412 34 567"))
        assertEquals("38 12 34 56", formaterTelefon("38123456"))
        assertEquals("+46 70 123 45 67", formaterTelefon("+46 70 123 45 67"))
        assertEquals("113", formaterTelefon("113"))
    }

    @Test
    fun relativDato() {
        val idag = LocalDate.of(2026, 10, 7)
        assertEquals("I dag", formaterDato(idag, idag))
        assertEquals("I morgen", formaterDato(idag.plusDays(1), idag))
        assertEquals("I går", formaterDato(idag.minusDays(1), idag))
        assertEquals("I dag kl. 08:00–10:30", formaterDatoTid(idag, java.time.LocalTime.of(8, 0), idag, java.time.LocalTime.of(10, 30)))
        assertEquals("I dag kl. 08:00", formaterDatoTid(idag, java.time.LocalTime.of(8, 0), idag))
    }
}
