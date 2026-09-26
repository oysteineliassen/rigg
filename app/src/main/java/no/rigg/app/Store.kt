package no.rigg.app

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Enkel lokal lagring i SharedPreferences. Alt er lagret som JSON. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("rigg", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; encodeDefaults = true }

    fun loadProfile(): Profile = decode("profile") ?: Profile()
    fun saveProfile(p: Profile) = put("profile", json.encodeToString(p))

    fun loadPlan(): Plan? = decode("plan")
    fun savePlan(p: Plan?) = if (p == null) prefs.edit().remove("plan").apply() else put("plan", json.encodeToString(p))

    fun loadLogs(): List<SessionLog> = decode("logs") ?: emptyList()
    fun saveLogs(l: List<SessionLog>) = put("logs", json.encodeToString(l))

    fun loadAi(): AiSettings = AiSettings(
        provider = runCatching { Provider.valueOf(prefs.getString("ai_provider", "") ?: "") }.getOrDefault(Provider.ANTHROPIC),
        apiKey = prefs.getString("ai_key", "") ?: "",
        model = prefs.getString("ai_model", "") ?: "",
    )

    fun saveAi(s: AiSettings) {
        prefs.edit()
            .putString("ai_provider", s.provider.name)
            .putString("ai_key", s.apiKey)
            .putString("ai_model", s.model)
            .apply()
    }

    fun loadReminders(): ReminderSettings = decode("reminders") ?: ReminderSettings()
    fun saveReminders(r: ReminderSettings) = put("reminders", json.encodeToString(r))

    /** Pågående økt, lagret slik at øktklokka overlever at Android lukker appen i bakgrunnen. */
    fun loadActiveSession(): ActiveSession? = decode("active_session")
    fun saveActiveSession(s: ActiveSession?) =
        if (s == null) prefs.edit().remove("active_session").apply() else put("active_session", json.encodeToString(s))

    private fun put(key: String, value: String) = prefs.edit().putString(key, value).apply()

    private inline fun <reified T> decode(key: String): T? =
        prefs.getString(key, null)?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() }
}
