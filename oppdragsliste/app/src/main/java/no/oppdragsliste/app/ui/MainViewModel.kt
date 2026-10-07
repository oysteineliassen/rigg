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
import no.oppdragsliste.app.data.Oppsett
import no.oppdragsliste.app.data.Prioritet
import no.oppdragsliste.app.logikk.AnleggForslag
import no.oppdragsliste.app.logikk.Autofelt
import no.oppdragsliste.app.logikk.formaterTelefon
import no.oppdragsliste.app.logikk.Feil
import no.oppdragsliste.app.logikk.finnPaTelefon
import no.oppdragsliste.app.logikk.finnUtfylling
import no.oppdragsliste.app.logikk.Seksjon
import no.oppdragsliste.app.logikk.filtrer
import no.oppdragsliste.app.logikk.grupper
import no.oppdragsliste.app.logikk.sorterPaAvstand
import no.oppdragsliste.app.sikkerhetskopi.eksporter
import no.oppdragsliste.app.sikkerhetskopi.les
import no.oppdragsliste.app.sikkerhetskopi.nyeOppdrag
import no.oppdragsliste.app.sted.geokod
import no.oppdragsliste.app.sted.naverendePosisjon
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
)

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

    val liste: StateFlow<ListeTilstand> = combine(repo.alle, _sok, klokke) { alle, sok, na ->
        val treff = filtrer(alle, sok)
        ListeTilstand(
            seksjoner = grupper(treff),
            utforte = utforte(treff),
            na = na,
            antallApne = alle.count { !it.ferdig },
            lastet = true,
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
    fun nyttOppdrag(telefon: String = "", kontaktperson: String = "") {
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
        if (telefon.isBlank()) return
        viewModelScope.launch {
            val treff = finnPaTelefon(repo.kjente.first(), telefon) ?: return@launch
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
            finnPaTelefon(kjente.value, nummer)?.let { treff ->
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
    fun endre(o: Oppdrag) { sett(utkast.hentEndring(o.id)?.tilSkjema(o) ?: Skjema.fra(o)) }

    /** Lukker skjemaet. Det som er skrevet, ligger igjen som utkast. */
    fun lukkSkjema() { _skjema.value = null }

    /** Starter på nytt: tomt skjema for nye oppdrag, originalen for endringer. */
    fun tomSkjema() {
        val s = _skjema.value ?: return
        utkast.slett(s)
        _skjema.value = s.original?.let { Skjema.fra(it) } ?: Skjema(prioritet = s.prioritet)
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
            val id = repo.lagre(lagret)
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
            val tekst = eksporter(alle)
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
            "Importerte ${nye.size} oppdrag." + if (hoppet > 0) " $hoppet fantes fra før og ble hoppet over." else ""
        } catch (e: Exception) {
            "Kunne ikke lese filen. Er det en sikkerhetskopi fra Oppdragsliste?"
        }
    }

    fun settFerdig(o: Oppdrag, ferdig: Boolean) = viewModelScope.launch { repo.settFerdig(o, ferdig) }

    fun slett(o: Oppdrag) = viewModelScope.launch { repo.slett(o) }
}
