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
            "- ${it.id} | ${it.nameEn} | ${it.pattern} | ${it.muscles.joinToString("/")} | level ${it.level}" +
                if (it.timed) " | timed" else ""
        }
        val equipmentText = (profile.equipment.map { englishLabel(it) } + "Bodyweight").joinToString(", ")
        val norwegian = Lang.isNorwegian
        val languageRule = if (norwegian) {
            "Write all text values (name, progression, day titles, tips) in Norwegian (bokmål)."
        } else {
            "Write all text values (name, progression, day titles, tips) in English."
        }
        val exampleTitle = if (norwegian) "Dag 1 - Helkropp" else "Day 1 - Full body"
        val exampleTimed = if (norwegian) "30-45 sek" else "30-45 sec"
        return """
You are an experienced strength coach. Create a 4-week training program for a person who trains at home with limited equipment.

PROFILE
- Equipment: $equipmentText
- Equipment details: ${profile.equipmentDetails.ifBlank { "none given" }}
- Other equipment the person has beyond what is listed (you may refer to it in "tip" and the progression text, but still only choose exercises by id from the list below): ${profile.customEquipment.ifBlank { "none given" }}
- Goal: ${englishLabel(profile.goal)}
- Level: ${englishLabel(profile.level)}
- Days per week: ${profile.daysPerWeek}
- Time per session: ${profile.minutesPerSession} minutes (including a 5 min warm-up)
- Other wishes and injuries: ${profile.notes.ifBlank { "none" }}

RULES
- Use ONLY exercises from the list below, referenced by their exact id. Do not invent exercises.
- All major muscle groups should be trained regularly through the week. Balance pushing and pulling.
- Fit the number of exercises, sets and rest periods to the time per session.
- With little equipment you can't just add weight. Describe progression through reps, tempo, rest, unilateral variations and harder exercises, and add weight where the equipment allows.
- Be careful with injuries and level. Don't pick exercises that are unrealistically hard.
- If the time per session is 30 minutes or less, pair exercises into supersets: give both exercises in a pair the same "group" letter ("A", "B", ...) and list them next to each other; the rest applies after the second one. Pair exercises that don't tire the same muscles (e.g. push + pull, legs + upper body). Otherwise leave "group" empty.
- $languageRule

AVAILABLE EXERCISES (id | name | pattern | muscles | level)
$list

RESPONSE FORMAT
Respond with ONLY valid JSON, no explanation and no markdown code block, in this format:
{
  "name": "Short name for the program",
  "weeks": 4,
  "progression": "Short, concrete description of how to progress week by week",
  "days": [
    {
      "title": "$exampleTitle",
      "exercises": [
        { "exerciseId": "goblet_squat", "sets": 3, "reps": "8-12", "restSec": 75, "tip": "Short tip for this particular person", "group": "" }
      ]
    }
  ]
}
For timed exercises, write reps as for example "$exampleTimed".
""".trimIndent()
    }

    // Faste engelske navn til prompten, uavhengig av systemspråket.
    private fun englishLabel(e: Equipment): String = when (e) {
        Equipment.BODYWEIGHT -> "Bodyweight"
        Equipment.DUMBBELLS -> "Dumbbells"
        Equipment.KETTLEBELL -> "Kettlebell"
        Equipment.RESISTANCE_BANDS -> "Resistance bands"
        Equipment.PULL_UP_BAR -> "Pull-up bar"
        Equipment.BENCH -> "Bench"
        Equipment.PLYO_BOX -> "Plyo box"
        Equipment.BARBELL -> "Barbell and plates"
        Equipment.CHAIR -> "Sturdy chair/sofa"
        Equipment.TABLE -> "Sturdy table"
        Equipment.TRX -> "Suspension trainer (TRX)"
        Equipment.JUMP_ROPE -> "Jump rope"
    }

    private fun englishLabel(g: Goal): String = when (g) {
        Goal.STRENGTH -> "Strength"
        Goal.HYPERTROPHY -> "Muscle growth"
        Goal.FITNESS -> "General fitness"
    }

    private fun englishLabel(l: Level): String = when (l) {
        Level.BEGINNER -> "Beginner"
        Level.INTERMEDIATE -> "Intermediate"
        Level.ADVANCED -> "Advanced"
    }

    /** Kaller valgt modell via API og returnerer rå tekst. */
    suspend fun ask(settings: AiSettings, prompt: String): String = withContext(Dispatchers.IO) {
        require(settings.apiKey.isNotBlank()) {
            tr("Missing API key. Add it under More (bottom right).", "Mangler API-nøkkel. Legg den inn under Mer (nederst til høyre).")
        }
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
                    401, 403 -> tr(" (check the API key)", " (sjekk API-nøkkelen)")
                    404 -> tr(" (check the model name)", " (sjekk modellnavnet)")
                    429 -> tr(" (too many requests or no credit left)", " (for mange kall eller tom saldo)")
                    else -> ""
                }
                error(tr("API error $code$hint: ${text.take(300)}", "API-feil $code$hint: ${text.take(300)}"))
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
        require(start >= 0 && end > start) { tr("No JSON found in the response.", "Fant ingen JSON i svaret.") }
        val plan = json.decodeFromString<Plan>(raw.substring(start, end + 1))

        val allowed = ExerciseLibrary.available(profile.equipment).map { it.id }.toSet()
        val warnings = mutableListOf<String>()

        val cleanedDays = plan.days.map { day ->
            val kept = day.exercises.mapNotNull { pe ->
                val lib = ExerciseLibrary.get(pe.exerciseId)
                when {
                    lib == null -> {
                        warnings += tr(
                            "Removed unknown exercise \"${pe.exerciseId}\" in ${day.title}.",
                            "Fjernet ukjent øvelse «${pe.exerciseId}» i ${day.title}.",
                        )
                        null
                    }
                    pe.exerciseId !in allowed -> {
                        warnings += tr(
                            "Removed \"${lib.name}\" in ${day.title} (requires equipment you don't have).",
                            "Fjernet «${lib.name}» i ${day.title} (krever utstyr du ikke har).",
                        )
                        null
                    }
                    else -> pe.copy(
                        sets = pe.sets.coerceIn(1, 8),
                        restSec = pe.restSec.coerceIn(10, 300),
                        tip = pe.tip.ifBlank { lib.note },
                    )
                }
            }
            day.copy(exercises = kept)
        }.filter { it.exercises.isNotEmpty() }

        require(cleanedDays.isNotEmpty()) { tr("The program had no valid exercises.", "Programmet hadde ingen gyldige øvelser.") }
        Generated(plan.copy(days = cleanedDays), warnings)
    }

    suspend fun generate(settings: AiSettings, profile: Profile): Result<Generated> =
        runCatching { ask(settings, buildPrompt(profile)) }.mapCatching { parsePlan(it, profile).getOrThrow() }
}
