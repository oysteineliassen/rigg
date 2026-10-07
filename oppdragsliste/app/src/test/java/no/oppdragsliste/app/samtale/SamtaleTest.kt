package no.oppdragsliste.app.samtale

import android.provider.CallLog
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SamtaleTest {
    private val na = 1_000_000_000L
    private fun s(id: Long = 1, nummer: String = "90000000", sek: Long = 60, type: Int = CallLog.Calls.INCOMING_TYPE, slutt: Long = na - 5_000) =
        Samtale(id, nummer, sek, slutt - sek * 1000, type, null)

    @Test fun `vanlig samtale gir varsel`() = assertTrue(skalVarsle(s(), 20, -1, na))
    @Test fun `for kort samtale`() = assertFalse(skalVarsle(s(sek = 15), 20, -1, na))
    @Test fun `akkurat lang nok`() = assertTrue(skalVarsle(s(sek = 20), 20, -1, na))
    @Test fun `allerede varslet`() = assertFalse(skalVarsle(s(id = 7), 20, 7, na))
    @Test fun `tapt anrop`() = assertFalse(skalVarsle(s(type = CallLog.Calls.MISSED_TYPE, sek = 0), 0, -1, na))
    @Test fun `utgaende samtale`() = assertTrue(skalVarsle(s(type = CallLog.Calls.OUTGOING_TYPE), 20, -1, na))
    @Test fun `skjult nummer`() = assertFalse(skalVarsle(s(nummer = ""), 20, -1, na))
    @Test fun `gammel samtale`() = assertFalse(skalVarsle(s(slutt = na - 11 * 60_000), 20, -1, na))
}
