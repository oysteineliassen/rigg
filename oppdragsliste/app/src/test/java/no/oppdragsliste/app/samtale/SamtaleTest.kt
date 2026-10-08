package no.oppdragsliste.app.samtale

import android.provider.CallLog
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SamtaleTest {
    private val na = 1_000_000_000L
    private fun s(id: Long = 1, nummer: String = "90000000", sek: Long = 60, type: Int = CallLog.Calls.INCOMING_TYPE, slutt: Long = na - 5_000) =
        Samtale(id, nummer, sek, slutt - sek * 1000, type, null)

    @Test fun `vanlig samtale gir varsel`() = assertTrue(erAktuell(s(), 20, -1, na))
    @Test fun `for kort samtale`() = assertFalse(erAktuell(s(sek = 15), 20, -1, na))
    @Test fun `akkurat lang nok`() = assertTrue(erAktuell(s(sek = 20), 20, -1, na))
    @Test fun `allerede varslet`() = assertFalse(erAktuell(s(id = 7), 20, 7, na))
    @Test fun `tapt anrop`() = assertFalse(erAktuell(s(type = CallLog.Calls.MISSED_TYPE, sek = 0), 0, -1, na))
    @Test fun `utgaende samtale`() = assertTrue(erAktuell(s(type = CallLog.Calls.OUTGOING_TYPE), 20, -1, na))
    @Test fun `skjult nummer`() = assertFalse(erAktuell(s(nummer = ""), 20, -1, na))
    @Test fun `gammel samtale`() = assertFalse(erAktuell(s(slutt = na - 11 * 60_000), 20, -1, na))
    @Test fun `apne appen innen 15 minutter`() {
        assertTrue(erAktuell(s(slutt = na - 14 * 60_000), 20, -1, na, vinduMin = 15))
        assertFalse(erAktuell(s(slutt = na - 16 * 60_000), 20, -1, na, vinduMin = 15))
    }
}
