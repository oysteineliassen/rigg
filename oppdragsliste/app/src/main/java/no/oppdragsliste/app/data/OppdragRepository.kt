package no.oppdragsliste.app.data

import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * All lesing og skriving av oppdrag går hit. [etterEndring] kalles etter hver endring, slik at
 * påminnelser og geofencer kan planlegges på nytt.
 */
class OppdragRepository(
    private val dao: OppdragDao,
    private val etterEndring: suspend () -> Unit = {},
) {
    val alle: Flow<List<Oppdrag>> = dao.observerAlle()

    suspend fun alleNa(): List<Oppdrag> = dao.alle()
    suspend fun apne(): List<Oppdrag> = dao.apne()
    suspend fun hent(id: Long): Oppdrag? = dao.hent(id)

    /** Lagrer nytt eller endret oppdrag og returnerer id-en. */
    suspend fun lagre(oppdrag: Oppdrag): Long {
        val id = if (oppdrag.id == 0L) dao.settInn(oppdrag) else oppdrag.id.also { dao.oppdater(oppdrag) }
        etterEndring()
        return id
    }

    /** Oppdaterer uten å planlegge varsler på nytt (f.eks. «varslet i dag»-merket). */
    suspend fun oppdaterStille(oppdrag: Oppdrag) = dao.oppdater(oppdrag)

    suspend fun settFerdig(oppdrag: Oppdrag, ferdig: Boolean) {
        dao.oppdater(oppdrag.copy(ferdig = ferdig, ferdigDato = if (ferdig) Instant.now() else null))
        etterEndring()
    }

    suspend fun slett(oppdrag: Oppdrag) {
        dao.slett(oppdrag)
        etterEndring()
    }

    suspend fun settInnAlle(liste: List<Oppdrag>) {
        dao.settInnAlle(liste)
        etterEndring()
    }
}
