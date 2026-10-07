package no.oppdragsliste.app.data

import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * All lesing og skriving av oppdrag går hit. [etterEndring] kalles etter hver endring, slik at
 * påminnelser og geofencer kan planlegges på nytt. Hver lagring oppdaterer også registeret over
 * kjente kunder og anlegg.
 */
class OppdragRepository(
    private val dao: OppdragDao,
    private val kjenteDao: KjentAnleggDao,
    private val etterEndring: suspend () -> Unit = {},
) {
    val alle: Flow<List<Oppdrag>> = dao.observerAlle()
    val kjente: Flow<List<KjentAnlegg>> = kjenteDao.observerAlle()

    suspend fun alleNa(): List<Oppdrag> = dao.alle()
    suspend fun apne(): List<Oppdrag> = dao.apne()
    suspend fun hent(id: Long): Oppdrag? = dao.hent(id)

    /** Lagrer nytt eller endret oppdrag og returnerer id-en. */
    suspend fun lagre(oppdrag: Oppdrag): Long {
        val id = if (oppdrag.id == 0L) dao.settInn(oppdrag) else oppdrag.id.also { dao.oppdater(oppdrag) }
        husk(oppdrag, Instant.now())
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
        liste.sortedBy { it.opprettet }.forEach { husk(it, it.opprettet) }
        etterEndring()
    }

    /** Fjerner et forslag: hele kunden (anlegg = null) eller ett anlegg. */
    suspend fun glem(kunde: String, anlegg: String?) {
        if (anlegg == null) kjenteDao.slettKunde(kunde) else kjenteDao.slett(kunde, anlegg)
    }

    private suspend fun husk(o: Oppdrag, tidspunkt: Instant) {
        val kunde = o.kunde.trim()
        val anlegg = o.anlegg.trim()
        if (kunde.isEmpty() && anlegg.isEmpty()) return
        kjenteDao.husk(
            KjentAnlegg(
                kunde = kunde, anlegg = anlegg, kontaktperson = o.kontaktperson.trim(), telefon = o.telefon.trim(), sted = o.sted.trim(),
                adresse = o.adresse.trim(), lat = o.lat, lng = o.lng, sistBrukt = tidspunkt,
            ),
        )
    }
}
