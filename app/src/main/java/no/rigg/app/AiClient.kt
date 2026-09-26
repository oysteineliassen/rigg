package no.rigg.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

enum class Provider(val label: String, val defaultModel: String) {
    ANTHROPIC("Claude (Anthropic)", "claude-sonnet-5"),
    OPENAI("ChatGPT (OpenAI)", "gpt-4o"),
}

data class AiSettings(
    val provider: Provider = Provider.ANTHROPIC,
    val apiKey: String = "",
    val model: String = "",
) {
    val effectiveModel: String get() = model.ifBlank { provider.defaultModel }
}

object AiClient {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    /** Bygger prompten. Brukes både for API-kall og for "kopier til chat"-flyten. */
    fun buildPrompt(profile: Profile): String {
        val available = ExerciseLibrary.available(profile.equipment)
        val list = available.joinToString("\n") {
            "- ${it.id} | ${it.name} | ${it.pattern} | ${it.muscles.joinToString("/")} | nivå ${it.level}" +
                if (it.timed) " | tid" else ""
        }
        val equipmentText = (profile.equipment.map { it.label } + "Kroppsvekt").joinToString(", ")
        return """
Du er en erfaren styrketrener. Lag et 4-ukers treningsprogram for en person som trener hjemme med begrenset utstyr.

PROFIL
- Utstyr: $equipmentText
- Detaljer om utstyret: ${profile.equipmentDetails.ifBlank { "ingen oppgitt" }}
- Annet utstyr personen har utover det som er listet (du kan referere til dette i "tip" og progresjonsteksten, men velg fortsatt kun øvelser med id fra listen under): ${profile.customEquipment.ifBlank { "ingen oppgitt" }}
- Mål: ${profile.goal.label}
- Nivå: ${profile.level.label}
- Dager per uke: ${profile.daysPerWeek}
- Tid per økt: ${profile.minutesPerSession} minutter (inkludert 5 min oppvarming)
- Andre ønsker og skader: ${profile.notes.ifBlank { "ingen" }}

REGLER
- Bruk KUN øvelser fra listen under, og oppgi dem med nøyaktig id. Ikke finn på øvelser.
- Alle store muskelgrupper skal trenes jevnlig i uka. Balanser press og trekk.
- Tilpass antall øvelser, sett og pauser til tiden per økt.
- Med lite utstyr kan du ikke bare legge på vekt. Beskriv progresjon via reps, tempo, pauser, unilaterale varianter og vanskeligere øvelser, og legg til vekt der utstyret tillater det.
- Vær forsiktig med skader og nivå. Ikke velg øvelser som er urealistisk vanskelige.

TILGJENGELIGE ØVELSER (id | navn | mønster | muskler | nivå)
$list

SVARFORMAT
Svar med KUN gyldig JSON, uten forklaring og uten markdown-kodeblokk, i dette formatet:
{
  "name": "Kort navn på programmet",
  "weeks": 4,
  "progression": "Kort og konkret beskrivelse av hvordan man progredierer uke for uke",
  "days": [
    {
      "title": "Dag 1 - Helkropp",
      "exercises": [
        { "exerciseId": "goblet_squat", "sets": 3, "reps": "8-12", "restSec": 75, "tip": "Kort tips for akkurat denne personen" }
      ]
    }
  ]
}
For tidsbaserte øvelser skriver du reps som for eksempel "30-45 sek".
""".trimIndent()
    }

    /** Kaller valgt modell via API og returnerer rå tekst. */
    suspend fun ask(settings: AiSettings, prompt: String): String = withContext(Dispatchers.IO) {
        require(settings.apiKey.isNotBlank()) { "Mangler API-nøkkel. Legg den inn under Mer (nederst til høyre)." }
        when (settings.provider) {
            Provider.ANTHROPIC -> askAnthropic(settings, prompt)
            Provider.OPENAI -> askOpenAi(settings, prompt)
        }
    }

    private fun askAnthropic(s: AiSettings, prompt: String): String {
        val body = JSONObject()
            .put("model", s.effectiveModel)
            .put("max_tokens", 4096)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        val resp = post(
            "https://api.anthropic.com/v1/messages", body.toString(),
            mapOf("x-api-key" to s.apiKey, "anthropic-version" to "2023-06-01"),
        )
        val content = JSONObject(resp).getJSONArray("content")
        val sb = StringBuilder()
        for (i in 0 until content.length()) {
            val block = content.getJSONObject(i)
            if (block.optString("type") == "text") sb.append(block.getString("text"))
        }
        return sb.toString()
    }

    private fun askOpenAi(s: AiSettings, prompt: String): String {
        val body = JSONObject()
            .put("model", s.effectiveModel)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        val resp = post(
            "https://api.openai.com/v1/chat/completions", body.toString(),
            mapOf("Authorization" to "Bearer ${s.apiKey}"),
        )
        return JSONObject(resp).getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content")
    }

    private fun post(url: String, body: String, headers: Map<String, String>): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 120_000
            conn.doOutput = true
            conn.setRequestProperty("content-type", "application/json")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val hint = when (code) {
                    401, 403 -> " (sjekk API-nøkkelen)"
                    404 -> " (sjekk modellnavnet)"
                    429 -> " (for mange kall eller tom saldo)"
                    else -> ""
                }
                error("API-feil $code$hint: ${text.take(300)}")
            }
            return text
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Tolker svar fra en AI (API eller limt inn fra chat) og validerer mot øvelsesbiblioteket.
     * Ukjente øvelser fjernes, og du får advarsler.
     */
    fun parsePlan(raw: String, profile: Profile): Result<Generated> = runCatching {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        require(start >= 0 && end > start) { "Fant ingen JSON i svaret." }
        val plan = json.decodeFromString<Plan>(raw.substring(start, end + 1))

        val allowed = ExerciseLibrary.available(profile.equipment).map { it.id }.toSet()
        val warnings = mutableListOf<String>()

        val cleanedDays = plan.days.map { day ->
            val kept = day.exercises.mapNotNull { pe ->
                val lib = ExerciseLibrary.get(pe.exerciseId)
                when {
                    lib == null -> { warnings += "Fjernet ukjent øvelse «${pe.exerciseId}» i ${day.title}."; null }
                    pe.exerciseId !in allowed -> { warnings += "Fjernet «${lib.name}» i ${day.title} (krever utstyr du ikke har)."; null }
                    else -> pe.copy(
                        sets = pe.sets.coerceIn(1, 8),
                        restSec = pe.restSec.coerceIn(10, 300),
                        tip = pe.tip.ifBlank { lib.note },
                    )
                }
            }
            day.copy(exercises = kept)
        }.filter { it.exercises.isNotEmpty() }

        require(cleanedDays.isNotEmpty()) { "Programmet hadde ingen gyldige øvelser." }
        Generated(plan.copy(days = cleanedDays), warnings)
    }

    suspend fun generate(settings: AiSettings, profile: Profile): Result<Generated> =
        runCatching { ask(settings, buildPrompt(profile)) }.mapCatching { parsePlan(it, profile).getOrThrow() }
}
