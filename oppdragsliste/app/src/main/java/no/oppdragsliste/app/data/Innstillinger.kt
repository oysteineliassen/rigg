package no.oppdragsliste.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalTime

enum class Tema(val tittel: String) { SYSTEM("Følg telefonen"), LYS("Lys"), MORK("Mørk") }

data class Oppsett(
    val tema: Tema = Tema.SYSTEM,
    /** Prioriteten et nytt oppdrag starter med. */
    val standardPrioritet: Prioritet = Prioritet.AVTALE,
    /** Fase 2: varsel etter telefonsamtaler. */
    val samtaleVarsel: Boolean = false,
    val minSamtaleSekunder: Int = 20,
    /** Fase 3: påminnelse før avtaler. */
    val avtaleVarsel: Boolean = true,
    val avtaleMinutterFor: Int = 60,
    val morgenOversikt: Boolean = false,
    val morgenTid: LocalTime = LocalTime.of(7, 0),
    /** Fase 4: «Du er i området». */
    val naerVarsel: Boolean = false,
    val naerRadiusMeter: Int = 2000,
    /** Avtaler legges i denne kalenderen på telefonen (f.eks. Google-kalenderen), -1 = ingen valgt. */
    val kalenderSynk: Boolean = false,
    val kalenderId: Long = -1,
    val kalenderVarighetMin: Int = 60,
)

/** Innstillingene lagres i SharedPreferences, så de kan leses synkront også fra mottakere. */
class Innstillinger(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("innstillinger", Context.MODE_PRIVATE)
    private val _oppsett = MutableStateFlow(les())
    val oppsett: StateFlow<Oppsett> = _oppsett.asStateFlow()

    val na: Oppsett get() = _oppsett.value

    private fun les() = Oppsett(
        tema = runCatching { Tema.valueOf(prefs.getString("tema", null) ?: "SYSTEM") }.getOrDefault(Tema.SYSTEM),
        standardPrioritet = runCatching { Prioritet.valueOf(prefs.getString("standardPrioritet", null) ?: "AVTALE") }
            .getOrDefault(Prioritet.AVTALE),
        samtaleVarsel = prefs.getBoolean("samtaleVarsel", false),
        minSamtaleSekunder = prefs.getInt("minSamtaleSekunder", 20),
        avtaleVarsel = prefs.getBoolean("avtaleVarsel", true),
        avtaleMinutterFor = prefs.getInt("avtaleMinutterFor", 60),
        morgenOversikt = prefs.getBoolean("morgenOversikt", false),
        morgenTid = LocalTime.ofSecondOfDay(prefs.getInt("morgenTid", 7 * 3600).toLong()),
        naerVarsel = prefs.getBoolean("naerVarsel", false),
        naerRadiusMeter = prefs.getInt("naerRadiusMeter", 2000),
        kalenderSynk = prefs.getBoolean("kalenderSynk", false),
        kalenderId = prefs.getLong("kalenderId", -1),
        kalenderVarighetMin = prefs.getInt("kalenderVarighetMin", 60),
    )

    fun endre(endring: (Oppsett) -> Oppsett) {
        val ny = endring(_oppsett.value)
        prefs.edit()
            .putString("tema", ny.tema.name)
            .putString("standardPrioritet", ny.standardPrioritet.name)
            .putBoolean("samtaleVarsel", ny.samtaleVarsel)
            .putInt("minSamtaleSekunder", ny.minSamtaleSekunder)
            .putBoolean("avtaleVarsel", ny.avtaleVarsel)
            .putInt("avtaleMinutterFor", ny.avtaleMinutterFor)
            .putBoolean("morgenOversikt", ny.morgenOversikt)
            .putInt("morgenTid", ny.morgenTid.toSecondOfDay())
            .putBoolean("naerVarsel", ny.naerVarsel)
            .putInt("naerRadiusMeter", ny.naerRadiusMeter)
            .putBoolean("kalenderSynk", ny.kalenderSynk)
            .putLong("kalenderId", ny.kalenderId)
            .putInt("kalenderVarighetMin", ny.kalenderVarighetMin)
            .apply()
        _oppsett.value = ny
    }

    /** Id-en til siste samtale i anropsloggen det er varslet om, så samme samtale ikke varsles to ganger. */
    /** Id-en til siste samtale appen har spurt om (spørsmålet når appen åpnes), så det bare spørres én gang. */
    var sisteSpurteSamtale: Long
        get() = prefs.getLong("sisteSpurteSamtale", -1)
        set(v) = prefs.edit().putLong("sisteSpurteSamtale", v).apply()

    var sisteVarsledeSamtale: Long
        get() = prefs.getLong("sisteVarsledeSamtale", -1)
        set(v) = prefs.edit().putLong("sisteVarsledeSamtale", v).apply()
}
