package no.oppdragsliste.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import no.oppdragsliste.app.app
import no.oppdragsliste.app.data.Oppdrag
import no.oppdragsliste.app.data.Oppsett
import no.oppdragsliste.app.data.Prioritet
import no.oppdragsliste.app.logikk.Feil
import no.oppdragsliste.app.logikk.Seksjon
import no.oppdragsliste.app.logikk.filtrer
import no.oppdragsliste.app.logikk.grupper
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
    val telefon: String = "",
    val sted: String = "",
    val adresse: String = "",
    val dato: LocalDate? = null,
    val tid: LocalTime? = null,
    val feil: Set<Feil> = emptySet(),
    val visFeil: Boolean = false,
) {
    companion object {
        fun fra(o: Oppdrag) = Skjema(
            original = o, prioritet = o.prioritet, beskrivelse = o.beskrivelse, kunde = o.kunde,
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

    fun endreOppsett(endring: (Oppsett) -> Oppsett) = innstillinger.endre(endring)

    fun settSok(tekst: String) { _sok.value = tekst }

    fun nyttOppdrag(telefon: String = "", kunde: String = "") {
        _skjema.value = Skjema(telefon = telefon, kunde = kunde)
    }

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
            telefon = s.telefon.trim(),
            sted = s.sted.trim(),
            adresse = s.adresse.trim(),
            dato = s.dato,
            tid = if (s.dato == null) null else s.tid,
        )
        _skjema.value = null
        viewModelScope.launch { repo.lagre(nytt) }
        return true
    }

    fun settFerdig(o: Oppdrag, ferdig: Boolean) = viewModelScope.launch { repo.settFerdig(o, ferdig) }

    fun slett(o: Oppdrag) = viewModelScope.launch { repo.slett(o) }
}
