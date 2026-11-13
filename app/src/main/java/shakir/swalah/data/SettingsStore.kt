package shakir.swalah.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import shakir.swalah.alarm.ReminderScheduler

/**
 * App settings, kept in device-protected storage so prayer reminders can be computed and
 * re-armed right after a reboot, before the phone is unlocked.
 */
object SettingsStore {
    private const val PREFS = "app_settings"
    private const val KEY = "settings_v1"

    private val _settings = MutableStateFlow<AppSettings?>(null)

    fun prefs(context: Context): SharedPreferences =
        context.createDeviceProtectedStorageContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun get(context: Context): AppSettings {
        _settings.value?.let { return it }
        val loaded = prefs(context).getString(KEY, null)
            ?.let { runCatching { AppSettings.fromJson(JSONObject(it)) }.getOrNull() }
            ?: AppSettings()
        _settings.value = loaded
        return loaded
    }

    fun flow(context: Context): StateFlow<AppSettings?> {
        get(context)
        return _settings
    }

    /** Saves and, when prayer times are affected, re-arms every reminder. */
    fun update(context: Context, transform: (AppSettings) -> AppSettings) {
        val (old, new) = synchronized(this) {
            val old = get(context)
            val new = transform(old)
            if (new != old) write(context, new)
            old to new
        }
        val timesChanged = old.location != new.location || old.methodIndex != new.methodIndex || old.adjustments != new.adjustments
        if (timesChanged) ReminderScheduler.rescheduleAll(context, "prayer settings changed")
    }

    @Synchronized
    internal fun write(context: Context, settings: AppSettings) {
        // commit(): the process may be killed right after (e.g. from a receiver).
        prefs(context).edit().putString(KEY, settings.toJson().toString()).commit()
        _settings.value = settings
    }
}
