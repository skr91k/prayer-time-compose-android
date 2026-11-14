package shakir.swalah.reminder

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import shakir.swalah.alarm.ReminderScheduler

/**
 * Reminders live in device-protected storage so they can ring right after a reboot,
 * before the user unlocks the phone for the first time.
 */
object ReminderStore {
    private const val PREFS = "reminders"
    private const val KEY_REMINDERS = "reminders_json"
    private const val KEY_NEXT_ID = "next_id"
    private const val KEY_SEEDED = "seeded_v1"
    private const val KEY_PRESETS_SEEN = "presets_seen"
    private const val KEY_RESET = "reset_v3"

    private val _version = MutableStateFlow(0)
    /** Bumped on every write so the UI can refresh. */
    val version: StateFlow<Int> = _version

    private fun prefs(context: Context): SharedPreferences =
        context.createDeviceProtectedStorageContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun all(context: Context): List<Reminder> {
        val raw = prefs(context).getString(KEY_REMINDERS, null) ?: return emptyList()
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { Reminder.fromJson(arr.getJSONObject(it)) }
    }

    fun get(context: Context, id: Int): Reminder? = all(context).firstOrNull { it.id == id }

    /**
     * Creates each built-in reminder once - also ones added in a later app version. A built-in
     * the user deleted is remembered as seen and not re-created.
     */
    @Synchronized
    fun seedIfNeeded(context: Context) {
        resetIfNeeded(context)
        val p = prefs(context)
        val seen = p.getStringSet(KEY_PRESETS_SEEN, null)
            // Installs from before this key existed had every preset of that time.
            ?: if (p.getBoolean(KEY_SEEDED, false)) all(context).mapNotNull { it.preset }.toSet() else emptySet()
        val fresh = Presets.all(context).filter { it.preset !in seen }
        if (fresh.isEmpty() && p.contains(KEY_PRESETS_SEEN)) return
        fresh.forEach { upsert(context, it) }
        p.edit().putStringSet(KEY_PRESETS_SEEN, seen + fresh.mapNotNull { it.preset }).putBoolean(KEY_SEEDED, true).commit()
    }

    /**
     * One-time wipe of every saved reminder (built-in and user-made) so all installs start again
     * from the defaults. Bump [KEY_RESET] (v4, ...) to wipe again in a later version.
     */
    private fun resetIfNeeded(context: Context) {
        val p = prefs(context)
        if (p.getBoolean(KEY_RESET, false)) return
        // Old alarms are keyed by reminder id; cancel them before the ids are forgotten.
        all(context).forEach { ReminderScheduler.cancel(context, it.id) }
        p.edit().clear().putBoolean(KEY_RESET, true).commit()
        _version.value++
    }

    /** Adds back any built-in reminder the user deleted. Returns how many were added. */
    @Synchronized
    fun restoreMissingPresets(context: Context): List<Reminder> = missingPresets(context).map { upsert(context, it) }

    /** Built-in reminders the user has deleted (what "Restore" would add back). */
    fun missingPresets(context: Context): List<Reminder> {
        val existing = all(context).mapNotNull { it.preset }.toSet()
        return Presets.all(context).filter { it.preset !in existing }
    }

    /** Inserts or replaces. A reminder with id 0 gets a fresh id. */
    @Synchronized
    fun upsert(context: Context, reminder: Reminder): Reminder {
        val p = prefs(context)
        val saved = if (reminder.id == 0) {
            val next = p.getInt(KEY_NEXT_ID, 1)
            p.edit().putInt(KEY_NEXT_ID, next + 1).commit()
            reminder.copy(id = next)
        } else reminder
        write(context, all(context).filter { it.id != saved.id } + saved)
        return saved
    }

    @Synchronized
    fun update(context: Context, id: Int, transform: (Reminder) -> Reminder): Reminder? {
        val current = get(context, id) ?: return null
        val updated = transform(current)
        if (updated != current) write(context, all(context).map { if (it.id == id) updated else it })
        return updated
    }

    @Synchronized
    fun delete(context: Context, id: Int) {
        write(context, all(context).filter { it.id != id })
    }

    private fun write(context: Context, reminders: List<Reminder>) {
        val arr = JSONArray()
        reminders.forEach { arr.put(it.toJson()) }
        // commit() on purpose: the process may be killed right after a reminder fires.
        prefs(context).edit().putString(KEY_REMINDERS, arr.toString()).commit()
        _version.value++
    }
}
