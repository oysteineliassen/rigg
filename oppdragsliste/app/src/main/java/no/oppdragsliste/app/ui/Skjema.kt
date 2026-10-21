package no.oppdragsliste.app.ui

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.oppdragsliste.app.data.KjentAnlegg
import no.oppdragsliste.app.data.Oppdrag
import no.oppdragsliste.app.data.Prioritet
import no.oppdragsliste.app.logikk.Autofelt
import no.oppdragsliste.app.logikk.Feil
import no.oppdragsliste.app.logikk.nyVerdi
import java.time.LocalDate
import java.time.LocalTime

/** Skjemaet for nytt eller endret oppdrag. [original] er null for nye. */
data class Skjema(
    val original: Oppdrag? = null,
    val prioritet: Prioritet = Prioritet.AVTALE,
    val beskrivelse: String = "",
    val kunde: String = "",
    val anlegg: String = "",
    val kontaktperson: String = "",
    val telefon: String = "",
    val sted: String = "",
    val adresse: String = "",
    val dato: LocalDate? = null,
    val tid: LocalTime? = null,
    val sluttid: LocalTime? = null,
    /** Notat for kunden/anlegget (lagres i registeret og følger med til neste oppdrag der). */
    val notat: String = "",
    /** Notatet slik det var da skjemaet ble åpnet, så vi vet om det er endret. */
    val originalNotat: String = "",
    val feil: Set<Feil> = emptySet(),
    val visFeil: Boolean = false,
    /** Verdiene vi selv fylte inn sist, så de kan byttes ut hvis kunde eller anlegg endres. */
    val autofylt: Map<Autofelt, String> = emptyMap(),
    /** Registeroppføringen sist utfylt fra; koordinatene gjenbrukes hvis stedet ikke er endret. */
    val kilde: KjentAnlegg? = null,
    /** Skjemaet er hentet fra et lagret utkast. */
    val fraUtkast: Boolean = false,
) {
    /** Fyller kontaktperson, telefon, sted og adresse fra registeret, uten å røre det brukeren har skrevet selv. */
    fun fyllFra(k: KjentAnlegg): Skjema {
        val auto = autofylt.toMutableMap()
        fun felt(f: Autofelt, naa: String, forslag: String): String {
            val ny = nyVerdi(naa, autofylt[f], forslag)
            if (ny != naa || naa == autofylt[f]) auto[f] = ny else auto.remove(f)
            return ny
        }
        return copy(
            kontaktperson = felt(Autofelt.KONTAKTPERSON, kontaktperson, k.kontaktperson),
            telefon = felt(Autofelt.TELEFON, telefon, k.telefon),
            sted = felt(Autofelt.STED, sted, k.sted),
            adresse = felt(Autofelt.ADRESSE, adresse, k.adresse),
            notat = felt(Autofelt.NOTAT, notat, k.notat),
            autofylt = auto,
            kilde = k,
        )
    }

    /** Ingenting skrevet inn (prioritet teller ikke). */
    val erTomt: Boolean
        get() = listOf(beskrivelse, kunde, anlegg, kontaktperson, telefon, sted, adresse, notat).all { it.isBlank() } &&
            dato == null && tid == null && sluttid == null

    /** Har det skjedd noe som er verdt å ta vare på? For endringer: noe er forskjellig fra originalen. */
    val harInnhold: Boolean
        get() = original?.let { innhold != fra(it, originalNotat).innhold } ?: !erTomt

    private val innhold get() = listOf(prioritet, beskrivelse, kunde, anlegg, kontaktperson, telefon, sted, adresse, dato, tid, sluttid, notat)

    companion object {
        fun fra(o: Oppdrag, notat: String = "") = Skjema(
            original = o, prioritet = o.prioritet, beskrivelse = o.beskrivelse, kunde = o.kunde, anlegg = o.anlegg,
            kontaktperson = o.kontaktperson, telefon = o.telefon, sted = o.sted, adresse = o.adresse,
            dato = o.dato, tid = o.tid, sluttid = o.sluttid,
            notat = notat, originalNotat = notat,
        )
    }
}

/** Utkastet slik det lagres (JSON i SharedPreferences). */
@Serializable
data class Utkast(
    val originalId: Long? = null,
    val prioritet: String = Prioritet.OMRADE.name,
    val beskrivelse: String = "",
    val kunde: String = "",
    val anlegg: String = "",
    val kontaktperson: String = "",
    val telefon: String = "",
    val sted: String = "",
    val adresse: String = "",
    val dato: String? = null,
    val tid: String? = null,
    val sluttid: String? = null,
    val notat: String = "",
    val autofylt: Map<String, String> = emptyMap(),
) {
    fun tilSkjema(original: Oppdrag?) = Skjema(
        original = original,
        prioritet = runCatching { Prioritet.valueOf(prioritet) }.getOrDefault(Prioritet.OMRADE),
        beskrivelse = beskrivelse, kunde = kunde, anlegg = anlegg, kontaktperson = kontaktperson,
        telefon = telefon, sted = sted, adresse = adresse,
        dato = dato?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        tid = tid?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
        sluttid = sluttid?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
        notat = notat,
        autofylt = autofylt.mapNotNull { (k, v) -> runCatching { Autofelt.valueOf(k) }.getOrNull()?.let { it to v } }.toMap(),
        fraUtkast = true,
    )

    companion object {
        fun fra(s: Skjema) = Utkast(
            originalId = s.original?.id, prioritet = s.prioritet.name, beskrivelse = s.beskrivelse, kunde = s.kunde,
            anlegg = s.anlegg, kontaktperson = s.kontaktperson, telefon = s.telefon, sted = s.sted,
            adresse = s.adresse, dato = s.dato?.toString(), tid = s.tid?.toString(), sluttid = s.sluttid?.toString(), notat = s.notat,
            autofylt = s.autofylt.mapKeys { it.key.name },
        )
    }
}

/**
 * Tar vare på det som er skrevet i skjemaet, så det ikke går tapt om skjemaet lukkes eller appen avsluttes.
 * Ett utkast for nytt oppdrag og ett for endring av et eksisterende.
 */
class Utkastlager(context: Context) {
    private val prefs = context.getSharedPreferences("utkast", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val _harNytt = MutableStateFlow(hentNytt() != null)
    /** Finnes det et påbegynt nytt oppdrag? */
    val harNytt: StateFlow<Boolean> = _harNytt.asStateFlow()

    private fun les(nokkel: String): Utkast? =
        prefs.getString(nokkel, null)?.let { runCatching { json.decodeFromString<Utkast>(it) }.getOrNull() }

    fun hentNytt(): Utkast? = les(NYTT)
    fun hentEndring(id: Long): Utkast? = les(ENDRING)?.takeIf { it.originalId == id }

    fun lagre(s: Skjema) {
        val nokkel = if (s.original == null) NYTT else ENDRING
        if (s.harInnhold) {
            prefs.edit().putString(nokkel, json.encodeToString(Utkast.fra(s))).apply()
        } else {
            prefs.edit().remove(nokkel).apply()
        }
        if (nokkel == NYTT) _harNytt.value = s.harInnhold
    }

    fun slett(s: Skjema) {
        if (s.original == null) {
            prefs.edit().remove(NYTT).apply()
            _harNytt.value = false
        } else {
            prefs.edit().remove(ENDRING).apply()
        }
    }

    private companion object {
        const val NYTT = "nytt"
        const val ENDRING = "endring"
    }
}
