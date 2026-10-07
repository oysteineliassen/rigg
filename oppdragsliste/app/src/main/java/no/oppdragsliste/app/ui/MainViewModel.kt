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
import no.oppdragsliste.app.logikk.Autofelt
import no.oppdragsliste.app.logikk.Feil
import no.oppdragsliste.app.logikk.finnPaTelefon
import no.oppdragsliste.app.logikk.finnUtfylling
import no.oppdragsliste.app.logikk.nyVerdi
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

/** Skjemaet for nytt eller endret oppdrag. [original] er null for nye. */
data class Skjema(
    val original: Oppdrag? = null,
    val prioritet: Prioritet = Prioritet.OMRADE,
    val beskrivelse: String = "",
    val kunde: String = "",
    val anlegg: String = "",
    val telefon: String = "",
    val sted: String = "",
    val adresse: String = "",
    val dato: LocalDate? = null,
    val tid: LocalTime? = null,
    val feil: Set<Feil> = emptySet(),
    val visFeil: Boolean = false,
    /** Verdiene vi selv fylte inn sist, så de kan byttes ut hvis kunde eller anlegg endres. */
    val autofylt: Map<Autofelt, String> = emptyMap(),
    /** Registeroppføringen sist utfylt fra; koordinatene gjenbrukes hvis stedet ikke er endret. */
    val kilde: KjentAnlegg? = null,
) {
    /** Fyller telefon, sted og adresse fra registeret, uten å røre det brukeren har skrevet selv. */
    fun fyllFra(k: KjentAnlegg): Skjema {
        val auto = autofylt.toMutableMap()
        fun felt(f: Autofelt, naa: String, forslag: String): String {
            val ny = nyVerdi(naa, autofylt[f], forslag)
            if (ny != naa || naa == autofylt[f]) auto[f] = ny else auto.remove(f)
            return ny
        }
        return copy(
            telefon = felt(Autofelt.TELEFON, telefon, k.telefon),
            sted = felt(Autofelt.STED, sted, k.sted),
            adresse = felt(Autofelt.ADRESSE, adresse, k.adresse),
            autofylt = auto,
            kilde = k,
        )
    }

    companion object {
        fun fra(o: Oppdrag) = Skjema(
            original = o, prioritet = o.prioritet, beskrivelse = o.beskrivelse, kunde = o.kunde, anlegg = o.anlegg,
            telefon = o.telefon, sted = o.sted, adresse = o.adresse, dato = o.dato, tid = o.tid,
        )
    }
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

    /**
     * Nytt skjema. Fra en samtale: er navnet kjent, fylles sted og adresse fra sist; er bare nummeret
     * kjent, fylles også kunde og anlegg.
     */
    fun nyttOppdrag(telefon: String = "", kunde: String = "") {
        _skjema.value = Skjema(telefon = telefon, kunde = kunde)
        if (telefon.isBlank() && kunde.isBlank()) return
        viewModelScope.launch {
            val kjente = repo.kjente.first()
            val treff = if (kunde.isNotBlank()) finnUtfylling(kjente, kunde, "") else finnPaTelefon(kjente, telefon)
            treff ?: return@launch
            _skjema.update { s ->
                s?.let { if (kunde.isBlank()) it.copy(kunde = treff.kunde, anlegg = treff.anlegg) else it }?.fyllFra(treff)
            }
        }
    }

    /** Kunde valgt fra forslag: fyll inn fra det som sist ble brukt for kunden. */
    fun velgKunde(navn: String) {
        oppdaterSkjema { it.copy(kunde = navn) }
        fyllFraKjente()
    }

    /** Anlegg valgt fra forslag: fyll inn fra det som sist ble brukt for kunden og anlegget. */
    fun velgAnlegg(navn: String) {
        oppdaterSkjema { it.copy(anlegg = navn) }
        fyllFraKjente()
    }

    /** Fyller inn hvis kunde (og eventuelt anlegg) finnes i registeret. Kalles også når feltene forlates. */
    fun fyllFraKjente() {
        val s = _skjema.value ?: return
        val treff = finnUtfylling(kjente.value, s.kunde, s.anlegg) ?: return
        oppdaterSkjema { it.fyllFra(treff) }
    }

    /** Fjerner et forslag fra registeret (hele kunden når [anlegg] er null). */
    fun glemForslag(kunde: String, anlegg: String?) = viewModelScope.launch { repo.glem(kunde, anlegg) }

    fun endre(o: Oppdrag) { _skjema.value = Skjema.fra(o) }

    fun lukkSkjema() { _skjema.value = null }

    fun oppdaterSkjema(endring: (Skjema) -> Skjema) {
        _skjema.update { s ->
            s?.let(endring)?.let { ny ->
                ny.copy(feil = valider(ny.prioritet, ny.beskrivelse, ny.kunde, ny.dato, ny.tid))
            }
        }
    }

    /** Returnerer false (og viser feilene) hvis skjemaet ikke er gyldig. */
    fun lagreSkjema(): Boolean {
        val s = _skjema.value ?: return false
        val feil = valider(s.prioritet, s.beskrivelse, s.kunde, s.dato, s.tid)
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
            telefon = s.telefon.trim(),
            sted = s.sted.trim(),
            adresse = s.adresse.trim(),
            dato = s.dato,
            tid = if (s.dato == null) null else s.tid,
        )
        _skjema.value = null
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
