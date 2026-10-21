package no.oppdragsliste.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import no.oppdragsliste.app.app
import no.oppdragsliste.app.data.KjentAnlegg
import no.oppdragsliste.app.data.Oppdrag
import no.oppdragsliste.app.data.Paminnelse
import no.oppdragsliste.app.data.Ordre
import no.oppdragsliste.app.data.nummerTekst
import no.oppdragsliste.app.logikk.TolketOrdre
import no.oppdragsliste.app.logikk.slaSammenTolket
import no.oppdragsliste.app.logikk.tolkOrdreliste
import no.oppdragsliste.app.ordre.lesTekst
import no.oppdragsliste.app.logikk.filtrerPaminnelser
import no.oppdragsliste.app.logikk.sorterPaminnelser
import no.oppdragsliste.app.data.Oppsett
import no.oppdragsliste.app.data.Prioritet
import no.oppdragsliste.app.logikk.AnleggForslag
import no.oppdragsliste.app.logikk.Autofelt
import no.oppdragsliste.app.logikk.formaterTelefon
import no.oppdragsliste.app.logikk.Feil
import no.oppdragsliste.app.logikk.kandidaterForSamtale
import no.oppdragsliste.app.logikk.notatFor
import no.oppdragsliste.app.logikk.anleggNokkel
import no.oppdragsliste.app.samtale.erAktuell
import no.oppdragsliste.app.samtale.navnForNummer
import no.oppdragsliste.app.samtale.sisteSamtale
import no.oppdragsliste.app.logikk.finnUtfylling
import no.oppdragsliste.app.logikk.Seksjon
import no.oppdragsliste.app.logikk.filtrer
import no.oppdragsliste.app.logikk.grupper
import no.oppdragsliste.app.logikk.sorterPaAvstand
import no.oppdragsliste.app.sikkerhetskopi.eksporter
import no.oppdragsliste.app.sikkerhetskopi.les
import no.oppdragsliste.app.sikkerhetskopi.nyeOppdrag
import no.oppdragsliste.app.sikkerhetskopi.lesPaminnelser
import no.oppdragsliste.app.sikkerhetskopi.lesNotater
import no.oppdragsliste.app.sikkerhetskopi.lesOrdre
import no.oppdragsliste.app.sikkerhetskopi.nyePaminnelser
import no.oppdragsliste.app.sted.geokod
import no.oppdragsliste.app.sted.naverendePosisjon
import no.oppdragsliste.app.varsler.Varsler
import no.oppdragsliste.app.varsler.mottakerOmfang
import no.oppdragsliste.app.logikk.utforte
import no.oppdragsliste.app.logikk.valider
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class ListeTilstand(
    val seksjoner: List<Seksjon> = emptyList(),
    val utforte: List<Oppdrag> = emptyList(),
    val na: LocalDateTime = LocalDateTime.now(),
    val antallApne: Int = 0,
    val lastet: Boolean = false,
    /** Åpne påminnelser (etter søk), tidligst først. */
    val paminnelser: List<Paminnelse> = emptyList(),
    /** Påminnelser krysset av i dag, så det kan angres. */
    val ferdigePaminnelser: List<Paminnelse> = emptyList(),
    /** Åpne påminnelser per oppdrag, til visning på kortene. */
    val paminnelserPerOppdrag: Map<Long, List<Paminnelse>> = emptyMap(),
    /** Alle oppdrag etter id (for å vise hvilket oppdrag en påminnelse hører til). */
    val oppdragEtterId: Map<Long, Oppdrag> = emptyMap(),
    /** Notater per kunde/anlegg ([anleggNokkel]), vist på kortene. */
    val notater: Map<String, String> = emptyMap(),
) {
    fun notat(o: Oppdrag): String? = notater[anleggNokkel(o.kunde, o.anlegg)]
}

enum class Fane { OPPDRAG, ORDRE }

/** Resultatet av å lese skjermbilder av ordrelisten, før det lagres. */
data class OrdreImport(
    val laster: Boolean = false,
    /** Sakene som ble funnet, og om de finnes fra før. */
    val funnet: List<Pair<TolketOrdre, Boolean>> = emptyList(),
    val feil: String? = null,
)

/** «Samtale med …: vil du registrere et oppdrag?» – vises når appen åpnes like etter en samtale. */
data class SamtaleSporsmal(
    val samtaleId: Long,
    val telefon: String,
    val navn: String?,
    val minutterSiden: Long,
    /** Kunde/anlegg kontakten er registrert på før, sist brukt først. */
    val kandidater: List<KjentAnlegg>,
)

/** Skjemaet for en påminnelse. [original] er null for nye. */
data class PaminnelseSkjema(
    val original: Paminnelse? = null,
    val tekst: String = "",
    val dato: LocalDate? = null,
    val tid: LocalTime? = null,
    val oppdragId: Long? = null,
    val visFeil: Boolean = false,
) {
    /** Noe som er verdt å spørre om før det forkastes. */
    val harInnhold: Boolean get() = tekst.isNotBlank() && tekst.trim() != original?.tekst
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = application.app.repository
    private val innstillinger = application.app.innstillinger

    val oppsett: StateFlow<Oppsett> = innstillinger.oppsett

    private val _sok = MutableStateFlow("")
    val sok: StateFlow<String> = _sok.asStateFlow()

    private val _skjema = MutableStateFlow<Skjema?>(null)
    val skjema: StateFlow<Skjema?> = _skjema.asStateFlow()

    /** Oppdaterer «nå» hvert minutt, så forfalte avtaler blir røde uten at noe annet endres. */
    private val klokke = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(60_000)
        }
    }

    val liste: StateFlow<ListeTilstand> = combine(repo.alle, repo.paminnelser, repo.kjente, _sok, klokke) { alle, pam, kjente, sok, na ->
        val treff = filtrer(alle, sok)
        val etterId = alle.associateBy { it.id }
        val pTreff = filtrerPaminnelser(pam, sok) { etterId[it]?.tittel }
        val idag = na.toLocalDate()
        ListeTilstand(
            seksjoner = grupper(treff),
            utforte = utforte(treff),
            na = na,
            antallApne = alle.count { !it.ferdig },
            lastet = true,
            paminnelser = sorterPaminnelser(pTreff),
            ferdigePaminnelser = pTreff.filter { p ->
                p.ferdig && p.ferdigDato?.atZone(java.time.ZoneId.systemDefault())?.toLocalDate() == idag
            },
            paminnelserPerOppdrag = sorterPaminnelser(pam).filter { it.oppdragId != null }.groupBy { it.oppdragId!! },
            oppdragEtterId = etterId,
            notater = kjente.filter { it.notat.isNotBlank() }.associate { anleggNokkel(it.kunde, it.anlegg) to it.notat },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListeTilstand())

    fun endreOppsett(endring: (Oppsett) -> Oppsett) {
        val forrige = innstillinger.na
        innstillinger.endre(endring)
        if (innstillinger.na.copy(tema = forrige.tema) != forrige) getApplication<Application>().app.planleggAltSenere()
    }

    fun settSok(tekst: String) { _sok.value = tekst }

    /** Kjente kunder og anlegg, sist brukt først. */
    val kjente: StateFlow<List<KjentAnlegg>> = repo.kjente.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val utkast = Utkastlager(application)
    /** Finnes det et påbegynt nytt oppdrag? (Knappen viser da «Fortsett oppdrag».) */
    val harUtkast: StateFlow<Boolean> = utkast.harNytt

    /** Viser skjemaet og tar vare på det som utkast. */
    private fun sett(s: Skjema?) {
        _skjema.value = s
        if (s != null) utkast.lagre(s)
    }

    /**
     * Nytt skjema, eller utkastet hvis et nytt oppdrag er påbegynt. Fra en samtale: navnet fra kontaktene
     * blir kontaktperson, og er nummeret kjent fra før, fylles kunde, anlegg, sted og adresse inn.
     */
    fun nyttOppdrag(
        telefon: String = "",
        kontaktperson: String = "",
        /** Kunde/anlegg valgt i samtalespørsmålet; null = finn selv ut fra nummer og navn (hvis [slaOpp]). */
        kandidat: KjentAnlegg? = null,
        slaOpp: Boolean = true,
    ) {
        val lagret = utkast.hentNytt()?.tilSkjema(null)
        if (lagret != null) {
            // Påbegynt oppdrag: fortsett der, og legg bare inn nummeret/navnet hvis feltene er tomme.
            sett(
                lagret.copy(
                    telefon = lagret.telefon.ifBlank { telefon },
                    kontaktperson = lagret.kontaktperson.ifBlank { kontaktperson },
                ),
            )
            return
        }
        sett(Skjema(prioritet = innstillinger.na.standardPrioritet, telefon = telefon, kontaktperson = kontaktperson))
        if (kandidat != null) {
            _skjema.value?.let { s -> sett(s.copy(kunde = kandidat.kunde, anlegg = kandidat.anlegg).fyllFra(kandidat)) }
            return
        }
        if (!slaOpp || (telefon.isBlank() && kontaktperson.isBlank())) return
        viewModelScope.launch {
            val treff = kandidaterForSamtale(repo.kjente.first(), telefon, kontaktperson).firstOrNull() ?: return@launch
            val s = _skjema.value ?: return@launch
            if (s.original != null || s.kunde.isNotBlank()) return@launch
            sett(s.copy(kunde = treff.kunde, anlegg = treff.anlegg).fyllFra(treff))
        }
    }

    /**
     * Kontakt valgt fra telefonens kontakter: navn og nummer fylles inn (det brukeren uttrykkelig valgte,
     * så det erstatter det som stod der). Er nummeret brukt før og kunden tom, fylles kunde og anlegg inn.
     */
    fun velgKontakt(navn: String, nummer: String) {
        val s = _skjema.value ?: return
        var ny = s.copy(
            kontaktperson = navn.ifBlank { s.kontaktperson },
            telefon = formaterTelefon(nummer).ifBlank { s.telefon },
            autofylt = s.autofylt - Autofelt.KONTAKTPERSON - Autofelt.TELEFON,
        )
        if (ny.kunde.isBlank()) {
            kandidaterForSamtale(kjente.value, nummer, navn).firstOrNull()?.let { treff ->
                ny = ny.copy(kunde = treff.kunde, anlegg = ny.anlegg.ifBlank { treff.anlegg }).fyllFra(treff)
            }
        }
        oppdaterSkjema { ny }
    }

    /** Kunde valgt fra forslag: fyll inn fra det som sist ble brukt for kunden (og anlegget, hvis valgt). */
    fun velgKunde(navn: String) {
        oppdaterSkjema { it.copy(kunde = navn) }
        fyllFraKjente()
    }

    /** Anlegg valgt fra forslag. Er kundefeltet tomt, fylles kunden fra forslaget inn også. */
    fun velgAnlegg(forslag: AnleggForslag) {
        oppdaterSkjema { s ->
            s.copy(anlegg = forslag.anlegg, kunde = if (s.kunde.isBlank()) forslag.kunde.orEmpty() else s.kunde)
        }
        fyllFraKjente()
    }

    /** Fyller inn hvis kunde (og eventuelt anlegg) finnes i registeret. Kalles også når feltene forlates. */
    fun fyllFraKjente() {
        val s = _skjema.value ?: return
        val treff = finnUtfylling(kjente.value, s.kunde, s.anlegg) ?: return
        oppdaterSkjema { it.fyllFra(treff) }
    }

    /** Sist fjernede forslag, så det kan angres. */
    data class Fjernet(val melding: String, val rader: List<KjentAnlegg>)
    private val _fjernet = MutableStateFlow<Fjernet?>(null)
    val fjernet: StateFlow<Fjernet?> = _fjernet.asStateFlow()

    /** Fjerner et forslag fra registeret (hele kunden når [anlegg] er null). Kan angres. */
    fun glemForslag(kunde: String, anlegg: String?) = viewModelScope.launch {
        val rader = repo.glem(kunde, anlegg)
        if (rader.isEmpty()) return@launch
        val hva = if (anlegg == null) "«$kunde»" + (if (rader.any { it.anlegg.isNotBlank() }) " med anlegg" else "") else "«$anlegg»"
        val ny = Fjernet("Fjernet $hva fra forslagene", rader)
        _fjernet.value = ny
        delay(8_000)
        if (_fjernet.value === ny) _fjernet.value = null
    }

    fun angreFjerning() {
        val f = _fjernet.value ?: return
        _fjernet.value = null
        viewModelScope.launch { repo.gjenopprett(f.rader) }
    }

    /** Endre et oppdrag; fortsetter på et ulagret utkast for samme oppdrag hvis det finnes. */
    fun endre(o: Oppdrag) {
        val notat = notatFor(kjente.value, o.kunde, o.anlegg)
        sett(utkast.hentEndring(o.id)?.tilSkjema(o)?.copy(originalNotat = notat) ?: Skjema.fra(o, notat))
    }

    /** Lukker skjemaet. Det som er skrevet, ligger igjen som utkast. */
    fun lukkSkjema() { _skjema.value = null }

    /** Starter på nytt: tomt skjema for nye oppdrag, originalen for endringer. */
    fun tomSkjema() {
        val s = _skjema.value ?: return
        utkast.slett(s)
        _skjema.value = s.original?.let { Skjema.fra(it, s.originalNotat) } ?: Skjema(prioritet = s.prioritet)
    }

    fun oppdaterSkjema(endring: (Skjema) -> Skjema) {
        val ny = _skjema.value?.let(endring) ?: return
        sett(ny.copy(feil = valider(ny.prioritet, ny.beskrivelse, ny.kunde, ny.dato, ny.tid, ny.sluttid)))
    }

    /** Returnerer false (og viser feilene) hvis skjemaet ikke er gyldig. */
    fun lagreSkjema(): Boolean {
        val s = _skjema.value ?: return false
        val feil = valider(s.prioritet, s.beskrivelse, s.kunde, s.dato, s.tid, s.sluttid)
        if (feil.isNotEmpty()) {
            _skjema.value = s.copy(feil = feil, visFeil = true)
            return false
        }
        val grunnlag = s.original ?: Oppdrag()
        val nytt = grunnlag.copy(
            prioritet = s.prioritet,
            beskrivelse = s.beskrivelse.trim(),
            kunde = s.kunde.trim(),
            anlegg = s.anlegg.trim(),
            kontaktperson = s.kontaktperson.trim(),
            telefon = s.telefon.trim(),
            sted = s.sted.trim(),
            adresse = s.adresse.trim(),
            dato = s.dato,
            tid = if (s.dato == null) null else s.tid,
            sluttid = if (s.dato == null || s.tid == null) null else s.sluttid,
        )
        _skjema.value = null
        utkast.slett(s)
        val stedEndret = s.original == null || s.original.stedTekst != nytt.stedTekst
        // Lagres med en gang. Koordinatene hentes etterpå, i appens omfang så det fullføres selv om skjermen lukkes.
        mottakerOmfang.launch {
            // Samme sted som sist for kunden/anlegget: bruk koordinatene derfra i stedet for å geokode på nytt.
            val kjentPos = s.kilde?.takeIf { it.lat != null && it.lng != null && it.stedTekst == nytt.stedTekst }
            val lagret = when {
                kjentPos != null -> nytt.copy(lat = kjentPos.lat, lng = kjentPos.lng)
                stedEndret -> nytt.copy(lat = null, lng = null)
                else -> nytt
            }
            // Notatet lagres på kunden/anlegget bare når det er endret i skjemaet, så et nyere notat
            // ikke overskrives når et gammelt oppdrag endres.
            val notat = s.notat.trim().takeIf { it != s.originalNotat.trim() }
            val id = repo.lagre(lagret, notat)
            if (!lagret.harKoordinater && lagret.stedTekst.isNotBlank()) {
                val koordinater = geokod(getApplication(), lagret) ?: return@launch
                val na = repo.hent(id) ?: return@launch
                // Bare hvis stedet ikke er endret på nytt i mellomtiden.
                if (na.stedTekst == lagret.stedTekst) repo.lagre(na.copy(lat = koordinater.first, lng = koordinater.second))
            }
        }
        return true
    }

    private val _geokoder = MutableStateFlow<String?>(null)
    /** Status for «finn koordinater for oppdrag som mangler». */
    val geokoder: StateFlow<String?> = _geokoder.asStateFlow()

    fun geokodManglende() = mottakerOmfang.launch {
        val mangler = repo.apne().filter { !it.harKoordinater && it.stedTekst.isNotBlank() }
        var funnet = 0
        mangler.forEachIndexed { i, o ->
            _geokoder.value = "Søker ${i + 1} av ${mangler.size} …"
            geokod(getApplication(), o)?.let { (lat, lng) ->
                repo.hent(o.id)?.let { repo.oppdaterStille(it.copy(lat = lat, lng = lng)) }
                funnet++
            }
        }
        if (funnet > 0) getApplication<Application>().app.planleggAlt()
        _geokoder.value = if (mangler.isEmpty()) "Alle åpne oppdrag med sted har koordinater."
        else "Fant koordinater for $funnet av ${mangler.size}."
    }

    // «I nærheten»: null = av. Ellers posisjonen (eller at vi venter på den / ikke fant den).
    private sealed interface Posisjon {
        data object Laster : Posisjon
        data class Feil(val melding: String) : Posisjon
        data class Funnet(val lat: Double, val lng: Double) : Posisjon
    }
    private val _posisjon = MutableStateFlow<Posisjon?>(null)

    val naer: StateFlow<NaerVisning?> = combine(repo.alle, _posisjon) { alle, pos ->
        when (pos) {
            null -> null
            Posisjon.Laster -> NaerVisning(emptyList(), laster = true)
            is Posisjon.Feil -> NaerVisning(emptyList(), feil = pos.melding)
            is Posisjon.Funnet -> NaerVisning(sorterPaAvstand(alle, pos.lat, pos.lng))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun visNaer(pa: Boolean) {
        if (!pa) {
            _posisjon.value = null
            return
        }
        _posisjon.value = Posisjon.Laster
        viewModelScope.launch {
            val p = naverendePosisjon(getApplication())
            if (_posisjon.value == null) return@launch // slått av mens vi ventet
            _posisjon.value = if (p != null) Posisjon.Funnet(p.first, p.second)
            else Posisjon.Feil("Fant ikke posisjonen din. Sjekk at posisjon er slått på og at appen har tilgang.")
        }
    }

    private val _kopiStatus = MutableStateFlow<String?>(null)
    val kopiStatus: StateFlow<String?> = _kopiStatus.asStateFlow()

    fun eksporterTil(uri: Uri) = viewModelScope.launch {
        _kopiStatus.value = try {
            val alle = repo.alleNa()
            val tekst = eksporter(alle, repo.allePaminnelser(), repo.alleKjente(), repo.alleOrdre())
            withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use {
                    it.write(tekst.toByteArray(Charsets.UTF_8))
                }
            }
            "Eksporterte ${alle.size} oppdrag."
        } catch (e: Exception) {
            "Eksporten feilet: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    fun importerFra(uri: Uri) = viewModelScope.launch {
        _kopiStatus.value = try {
            val tekst = withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
            }
            val importerte = les(tekst)
            val nye = nyeOppdrag(repo.alleNa(), importerte)
            if (nye.isNotEmpty()) repo.settInnAlle(nye)
            val hoppet = importerte.size - nye.size
            val nyeP = nyePaminnelser(repo.allePaminnelser(), lesPaminnelser(tekst), repo.alleNa())
            if (nyeP.isNotEmpty()) repo.settInnPaminnelser(nyeP)
            // Notater for kunder/anlegg (eksisterende notater beholdes).
            repo.importerNotater(lesNotater(tekst))
            // Ordre: saker som ikke finnes fra før (samme saksnummer).
            val finnesOrdre = repo.alleOrdre().map { it.saksnr }.toSet()
            repo.settInnOrdre(lesOrdre(tekst).filter { it.saksnr !in finnesOrdre }.map { it.copy(id = 0) })
            "Importerte ${nye.size} oppdrag" + (if (nyeP.isNotEmpty()) " og ${nyeP.size} påminnelser." else ".") +
                if (hoppet > 0) " $hoppet oppdrag fantes fra før og ble hoppet over." else ""
        } catch (e: Exception) {
            "Kunne ikke lese filen. Er det en sikkerhetskopi fra Oppdragsliste?"
        }
    }

    // ---- Faner og ordre ----

    private val _fane = MutableStateFlow(Fane.OPPDRAG)
    val fane: StateFlow<Fane> = _fane.asStateFlow()
    fun visFane(f: Fane) { _fane.value = f }

    val ordre: StateFlow<List<Ordre>> = repo.ordre.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _ordreImport = MutableStateFlow<OrdreImport?>(null)
    val ordreImport: StateFlow<OrdreImport?> = _ordreImport.asStateFlow()

    private val _ordreMelding = MutableStateFlow<String?>(null)
    val ordreMelding: StateFlow<String?> = _ordreMelding.asStateFlow()
    fun ordreMeldingVist() { _ordreMelding.value = null }

    /** Leser sakene i ett eller flere skjermbilder og viser hva som ble funnet før det lagres. */
    fun importerOrdreBilder(bilder: List<Uri>) {
        if (bilder.isEmpty()) return
        _fane.value = Fane.ORDRE
        _ordreImport.value = OrdreImport(laster = true)
        viewModelScope.launch {
            val app = getApplication<Application>()
            val funnet = mutableListOf<TolketOrdre>()
            var feilet = 0
            bilder.forEach { uri ->
                try {
                    funnet += tolkOrdreliste(lesTekst(app, uri))
                } catch (e: Exception) {
                    feilet++
                }
            }
            val samlet = funnet.groupBy { it.saksnr }.values.map { it.reduce(::slaSammenTolket) }
            val finnes = ordre.value.associateBy { it.saksnr }
            _ordreImport.value = OrdreImport(
                funnet = samlet.map { it to (finnes[it.saksnr] != null) },
                feil = when {
                    samlet.isNotEmpty() -> null
                    feilet == bilder.size -> "Kunne ikke lese bildet."
                    else -> "Fant ingen saker («SAK : …» eller P-nummer) i bildet."
                },
            )
        }
    }

    fun bekreftOrdreImport() {
        val imp = _ordreImport.value ?: return
        _ordreImport.value = null
        viewModelScope.launch {
            val (nye, oppdaterte) = repo.importerOrdre(imp.funnet.map { it.first })
            _ordreMelding.value = listOfNotNull(
                if (nye > 0) "La til $nye" else null,
                if (oppdaterte > 0) "oppdaterte $oppdaterte" else null,
            ).joinToString(", ").replaceFirstChar { it.uppercase() }.ifBlank { "Ingen endringer" }
        }
    }

    fun avbrytOrdreImport() { _ordreImport.value = null }

    fun settOrdreUtfort(o: Ordre, pa: Boolean) = viewModelScope.launch {
        repo.lagreOrdre(o.copy(utfort = pa, utfortDato = if (pa) java.time.Instant.now() else null))
    }

    fun settOrdreFakturert(o: Ordre, pa: Boolean) = viewModelScope.launch {
        repo.lagreOrdre(o.copy(fakturert = pa, fakturertDato = if (pa) java.time.Instant.now() else null))
        if (pa && o.utfort) _ordreMelding.value = "${o.nummerTekst} er utført og fakturert – flyttet til arkivet"
    }

    fun slettOrdre(o: Ordre) = viewModelScope.launch { repo.slettOrdre(o) }

    /** Lagrer en sak som er endret for hånd (eller lagt inn manuelt, id 0). Saksnummeret må være unikt. */
    fun lagreOrdreManuelt(o: Ordre) = viewModelScope.launch {
        try {
            if (o.id == 0L) repo.settInnOrdre(listOf(o)) else repo.lagreOrdre(o)
            _ordreMelding.value = "${o.nummerTekst} er lagret"
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            _ordreMelding.value = "${o.nummerTekst} finnes allerede – ikke lagret"
        }
    }

    /** Lager et oppdrag fra en sak (beskrivelse, kunde, adresse, dato og tid fylt inn). */
    fun lagOppdragFraOrdre(o: Ordre) {
        _fane.value = Fane.OPPDRAG
        val lagret = utkast.hentNytt()?.tilSkjema(null)
        if (lagret != null) {
            // Et påbegynt oppdrag skal ikke gå tapt: vis det, og si fra.
            sett(lagret)
            _ordreMelding.value = "Du har et påbegynt oppdrag. Lagre eller tøm det først."
            return
        }
        sett(
            Skjema(
                prioritet = innstillinger.na.standardPrioritet,
                beskrivelse = "${o.nummerTekst} – ${o.tittel}".trim(),
                kunde = o.kunde,
                adresse = o.adresse,
            ),
        )
        fyllFraKjente()
    }

    // ---- Spørsmål etter samtale ----

    private val _samtaleSporsmal = MutableStateFlow<SamtaleSporsmal?>(null)
    val samtaleSporsmal: StateFlow<SamtaleSporsmal?> = _samtaleSporsmal.asStateFlow()

    /**
     * Kalles når appen kommer i forgrunnen. Er det en samtale de siste 15 minuttene som ikke er spurt om
     * og ikke allerede registrert, spørres det om et nytt oppdrag.
     */
    fun sjekkSisteSamtale() = viewModelScope.launch {
        val app = getApplication<Application>()
        val oppsett = innstillinger.na
        if (!oppsett.samtaleVarsel || !oppsett.samtaleModus.sporIApp) return@launch
        if (_skjema.value != null || _samtaleSporsmal.value != null) return@launch
        val s = withContext(Dispatchers.IO) { sisteSamtale(app) } ?: return@launch
        val na = System.currentTimeMillis()
        if (!erAktuell(s, oppsett.minSamtaleSekunder, innstillinger.sisteSpurteSamtale, na, vinduMin = 15, simKort = oppsett.samtaleSim)) return@launch
        val alle = repo.alleNa()
        val nr = s.nummer.filter { it.isDigit() }.takeLast(8)
        // Allerede lagt inn et oppdrag med nummeret etter at samtalen startet: ikke spør.
        if (alle.any { it.opprettet.toEpochMilli() >= s.tidspunkt && it.telefon.filter { c -> c.isDigit() }.takeLast(8) == nr }) {
            innstillinger.sisteSpurteSamtale = s.id
            return@launch
        }
        val navn = withContext(Dispatchers.IO) { navnForNummer(app, s.nummer) } ?: s.lagretNavn
        // Registeret og oppdragene (også de som er fjernet fra forslagene).
        val fraOppdrag = alle.map {
            KjentAnlegg(
                kunde = it.kunde.trim(), anlegg = it.anlegg.trim(), kontaktperson = it.kontaktperson, telefon = it.telefon,
                sted = it.sted, adresse = it.adresse, lat = it.lat, lng = it.lng, sistBrukt = it.opprettet,
            )
        }
        if (_skjema.value != null) return@launch
        _samtaleSporsmal.value = SamtaleSporsmal(
            samtaleId = s.id,
            telefon = formaterTelefon(s.nummer),
            navn = navn,
            minutterSiden = ((na - s.slutt) / 60_000).coerceAtLeast(0),
            kandidater = kandidaterForSamtale(repo.kjente.first() + fraOppdrag, s.nummer, navn),
        )
    }

    /** Svar på samtalespørsmålet. [kandidat] er valgt kunde/anlegg, null = fyll inn selv. */
    fun svarSamtale(registrer: Boolean, kandidat: KjentAnlegg?) {
        val sp = _samtaleSporsmal.value ?: return
        _samtaleSporsmal.value = null
        innstillinger.sisteSpurteSamtale = sp.samtaleId
        // Varselet om samme samtale trengs ikke lenger.
        androidx.core.app.NotificationManagerCompat.from(getApplication()).cancel(Varsler.samtaleId(sp.samtaleId))
        if (registrer) nyttOppdrag(sp.telefon, sp.navn.orEmpty(), kandidat = kandidat, slaOpp = false)
    }

    /** Samtalen er håndtert på annen måte (f.eks. via varselet): ikke spør om den. */
    fun merkSamtaleSpurt(id: Long) {
        innstillinger.sisteSpurteSamtale = id
        if (_samtaleSporsmal.value?.samtaleId == id) _samtaleSporsmal.value = null
    }

    // ---- Påminnelser ----

    private val _paminnelse = MutableStateFlow<PaminnelseSkjema?>(null)
    val paminnelse: StateFlow<PaminnelseSkjema?> = _paminnelse.asStateFlow()

    /** Ny påminnelse, eventuelt koblet til et oppdrag. */
    fun nyPaminnelse(oppdragId: Long? = null) { _paminnelse.value = PaminnelseSkjema(oppdragId = oppdragId) }

    fun endrePaminnelse(p: Paminnelse) {
        _paminnelse.value = PaminnelseSkjema(original = p, tekst = p.tekst, dato = p.dato, tid = p.tid, oppdragId = p.oppdragId)
    }

    fun oppdaterPaminnelse(endring: (PaminnelseSkjema) -> PaminnelseSkjema) { _paminnelse.update { it?.let(endring) } }

    fun lukkPaminnelse() { _paminnelse.value = null }

    /** Lagrer; false (og feilmelding) hvis teksten mangler. */
    fun lagrePaminnelse(): Boolean {
        val s = _paminnelse.value ?: return false
        if (s.tekst.isBlank()) {
            _paminnelse.value = s.copy(visFeil = true)
            return false
        }
        val p = (s.original ?: Paminnelse()).copy(
            tekst = s.tekst.trim(), dato = s.dato, tid = if (s.dato == null) null else s.tid, oppdragId = s.oppdragId,
        )
        _paminnelse.value = null
        viewModelScope.launch { repo.lagrePaminnelse(p) }
        return true
    }

    fun slettPaminnelse(p: Paminnelse) {
        _paminnelse.value = null
        viewModelScope.launch { repo.slettPaminnelse(p) }
    }

    fun settPaminnelseFerdig(p: Paminnelse, ferdig: Boolean) = viewModelScope.launch { repo.settPaminnelseFerdig(p, ferdig) }

    fun settFerdig(o: Oppdrag, ferdig: Boolean) = viewModelScope.launch { repo.settFerdig(o, ferdig) }

    fun slett(o: Oppdrag) = viewModelScope.launch { repo.slett(o) }
}
