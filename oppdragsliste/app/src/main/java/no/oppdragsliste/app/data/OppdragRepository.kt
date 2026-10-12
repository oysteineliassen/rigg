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
    private val paminnelseDao: PaminnelseDao,
    private val etterEndring: suspend () -> Unit = {},
) {
    val alle: Flow<List<Oppdrag>> = dao.observerAlle()
    val kjente: Flow<List<KjentAnlegg>> = kjenteDao.observerAlle()
    val paminnelser: Flow<List<Paminnelse>> = paminnelseDao.observerAlle()

    suspend fun allePaminnelser(): List<Paminnelse> = paminnelseDao.alle()
    suspend fun apnePaminnelser(): List<Paminnelse> = paminnelseDao.apne()
    suspend fun hentPaminnelse(id: Long): Paminnelse? = paminnelseDao.hent(id)

    suspend fun lagrePaminnelse(p: Paminnelse): Long {
        val id = if (p.id == 0L) paminnelseDao.settInn(p) else p.id.also { paminnelseDao.oppdater(p) }
        etterEndring()
        return id
    }

    suspend fun settPaminnelseFerdig(p: Paminnelse, ferdig: Boolean) {
        paminnelseDao.oppdater(p.copy(ferdig = ferdig, ferdigDato = if (ferdig) Instant.now() else null))
        etterEndring()
    }

    suspend fun slettPaminnelse(p: Paminnelse) {
        paminnelseDao.slett(p)
        etterEndring()
    }

    suspend fun settInnPaminnelser(liste: List<Paminnelse>) {
        liste.forEach { paminnelseDao.settInn(it) }
        etterEndring()
    }

    suspend fun alleNa(): List<Oppdrag> = dao.alle()
    suspend fun apne(): List<Oppdrag> = dao.apne()
    suspend fun hent(id: Long): Oppdrag? = dao.hent(id)

    /**
     * Lagrer nytt eller endret oppdrag og returnerer id-en. [notat] er notatet for kunden/anlegget fra
     * skjemaet; null = behold det som står i registeret.
     */
    suspend fun lagre(oppdrag: Oppdrag, notat: String? = null): Long {
        val id = if (oppdrag.id == 0L) dao.settInn(oppdrag) else oppdrag.id.also { dao.oppdater(oppdrag) }
        husk(oppdrag, Instant.now(), notat)
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

    /**
     * Fjerner et forslag: hele kunden (anlegg = null) eller ett anlegg. Rører ikke oppdragene.
     * Returnerer det som ble fjernet, så det kan legges tilbake med [gjenopprett].
     */
    suspend fun glem(kunde: String, anlegg: String?): List<KjentAnlegg> {
        val fjernet = if (anlegg == null) kjenteDao.hentKunde(kunde) else kjenteDao.hent(kunde, anlegg)
        if (anlegg == null) kjenteDao.slettKunde(kunde) else kjenteDao.slett(kunde, anlegg)
        return fjernet
    }

    suspend fun gjenopprett(rader: List<KjentAnlegg>) = rader.forEach { kjenteDao.settInn(it) }

    private suspend fun husk(o: Oppdrag, tidspunkt: Instant, notat: String? = null) {
        val kunde = o.kunde.trim()
        val anlegg = o.anlegg.trim()
        if (kunde.isEmpty() && anlegg.isEmpty()) return
        // Notatet beholdes når det ikke kommer fra skjemaet (f.eks. ved import eller oppdatert posisjon).
        val beholdt = notat ?: kjenteDao.hent(kunde, anlegg).firstOrNull()?.notat.orEmpty()
        kjenteDao.husk(
            KjentAnlegg(
                kunde = kunde, anlegg = anlegg, kontaktperson = o.kontaktperson.trim(), telefon = o.telefon.trim(), sted = o.sted.trim(),
                adresse = o.adresse.trim(), lat = o.lat, lng = o.lng, sistBrukt = tidspunkt, notat = beholdt.trim(),
            ),
        )
    }

    /** Legger inn notater fra en sikkerhetskopi (lager oppføringen hvis den mangler, beholder ellers alt annet). */
    suspend fun importerNotater(notater: List<Triple<String, String, String>>) {
        notater.forEach { (kunde, anlegg, notat) ->
            if (notat.isBlank()) return@forEach
            val eksisterende = kjenteDao.hent(kunde, anlegg).firstOrNull()
            if (eksisterende == null) {
                kjenteDao.husk(KjentAnlegg(kunde = kunde, anlegg = anlegg, notat = notat))
            } else if (eksisterende.notat.isBlank()) {
                kjenteDao.husk(eksisterende.copy(notat = notat))
            }
        }
    }

    suspend fun alleKjente(): List<KjentAnlegg> = kjenteDao.alle()
}
