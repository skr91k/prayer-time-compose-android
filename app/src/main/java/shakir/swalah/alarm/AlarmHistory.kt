package shakir.swalah.alarm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import shakir.swalah.reminder.Reminder

/**
 * What happened to each alarm occurrence, for the server's alarm report (see NetSync):
 *  - "ok": rang within [ON_TIME_MS] of its slot
 *  - "late": rang later (the main alarm wasn't delivered; the backup or the app-open catch-up rang it)
 *  - "missed": never rang (found when re-arming: its slot passed outside the catch-up window)
 * Kept in device-protected storage (alarms fire before the first unlock); entries are dropped once
 * the server has them, and the oldest go when there are more than [MAX].
 */
object AlarmHistory {
    private const val PREFS = "alarm_history"
    private const val KEY_ENTRIES = "entries"
    private const val KEY_SEQ = "seq"
    /** Missed alarms the user hasn't been told about yet (the entries themselves go to the server). */
    private const val KEY_UNSEEN_MISSED = "unseen_missed"
    private const val KEY_NO_DIALOG = "no_missed_dialog"
    private const val MAX = 300
    const val ON_TIME_MS = 60_000L

    const val OK = "ok"
    const val LATE = "late"
    const val MISSED = "missed"

    private fun prefs(context: Context) =
        context.createDeviceProtectedStorageContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** An occurrence that rang. [snooze] = it was a snoozed re-ring, not the regular slot. */
    @Synchronized
    fun recordFired(context: Context, reminder: Reminder, slot: Long, snooze: Boolean) {
        val now = System.currentTimeMillis()
        val delay = (now - slot).coerceAtLeast(0)
        add(
            context,
            entry(reminder, slot, if (delay <= ON_TIME_MS) OK else LATE)
                .put("firedAt", now)
                .put("delaySec", delay / 1000)
                .put("snooze", snooze),
        )
    }

    /** An occurrence that never rang. Recorded once per (reminder, slot). */
    @Synchronized
    fun recordMissed(context: Context, reminder: Reminder, slot: Long) {
        val known = read(context).any {
            it.optInt("reminderId") == reminder.id && it.optLong("slot") == slot && it.optString("status") == MISSED
        }
        if (known) return
        if (reminder.id != Reminder.TEST_ID) bumpUnseenMissed(context)
        add(context, entry(reminder, slot, MISSED))
        EventLog.log(context, "MISSED (never rang) reminder ${reminder.id} slot ${EventLog.time(slot)}")
    }

    /** Missed alarms not yet shown in the home dialog; 0 when there are none or it's turned off. */
    fun unseenMissed(context: Context): Int {
        val p = prefs(context)
        return if (p.getBoolean(KEY_NO_DIALOG, false)) 0 else p.getInt(KEY_UNSEEN_MISSED, 0)
    }

    /** The dialog was shown; [never] = "Don't show again". */
    fun markSeen(context: Context, never: Boolean) {
        prefs(context).edit().putInt(KEY_UNSEEN_MISSED, 0)
            .apply { if (never) putBoolean(KEY_NO_DIALOG, true) }.apply()
    }

    private fun bumpUnseenMissed(context: Context) {
        val p = prefs(context)
        p.edit().putInt(KEY_UNSEEN_MISSED, p.getInt(KEY_UNSEEN_MISSED, 0) + 1).apply()
    }

    /** Entries the server doesn't have yet, oldest first. */
    @Synchronized
    fun pending(context: Context): List<JSONObject> = read(context)

    /** Drops the entries the server accepted (by their seq). */
    @Synchronized
    fun markSent(context: Context, sent: List<JSONObject>) {
        if (sent.isEmpty()) return
        val seqs = sent.map { it.optLong("seq") }.toSet()
        write(context, read(context).filter { it.optLong("seq") !in seqs })
    }

    private fun entry(reminder: Reminder, slot: Long, status: String) = JSONObject()
        .put("reminderId", reminder.id)
        .put("label", reminder.label)
        .put("preset", reminder.preset ?: "")
        .put("wakeUp", reminder.isWakeUp)
        .put("slot", slot)
        .put("status", status)

    private fun add(context: Context, entry: JSONObject) {
        val p = prefs(context)
        val seq = p.getLong(KEY_SEQ, 0) + 1
        p.edit().putLong(KEY_SEQ, seq).commit()
        write(context, (read(context) + entry.put("seq", seq)).takeLast(MAX))
    }

    private fun read(context: Context): List<JSONObject> {
        val raw = prefs(context).getString(KEY_ENTRIES, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getJSONObject(it) }
        }.getOrDefault(emptyList())
    }

    private fun write(context: Context, entries: List<JSONObject>) {
        // commit(): written from the fire path, where the process may die right after.
        prefs(context).edit().putString(KEY_ENTRIES, JSONArray(entries).toString()).commit()
    }
}
