package shakir.swalah.alarm

import android.content.Context
import shakir.swalah.reminder.Reminder
import shakir.swalah.reminder.ReminderStore
import shakir.swalah.reminder.TimeBase

/** All state transitions of a reminder: save, fire, snooze, dismiss. */
object ReminderController {
    /** How many times an unattended wake-up alarm re-rings before giving up. */
    const val MAX_AUTO_SNOOZES = 10

    fun save(context: Context, reminder: Reminder): Reminder {
        val saved = ReminderStore.upsert(context, reminder.copy(snoozedUntil = 0, autoSnoozes = 0))
        EventLog.log(context, "saved reminder ${saved.id} base=${saved.base} enabled=${saved.enabled}")
        if (saved.enabled) ReminderScheduler.schedule(context, saved) else ReminderScheduler.cancel(context, saved.id)
        return ReminderStore.get(context, saved.id) ?: saved
    }

    fun skipNext(context: Context, reminder: Reminder): Reminder {
        val skipped = reminder.regularTrigger(ReminderScheduler.calculator(context)) ?: return reminder
        EventLog.log(context, "skip next of reminder ${reminder.id}: ${EventLog.time(skipped)}")
        return save(context, reminder.copy(skippedTrigger = skipped))
    }

    fun undoSkip(context: Context, reminder: Reminder): Reminder = save(context, reminder.copy(skippedTrigger = 0))

    fun delete(context: Context, id: Int) {
        ReminderScheduler.cancel(context, id)
        ReminderStore.delete(context, id)
        Notifications.cancelSnoozed(context, id)
    }

    fun scheduleTest(context: Context, delayMs: Long = 20_000) {
        val test = Reminder(
            id = Reminder.TEST_ID,
            label = "Test reminder",
            base = TimeBase.FIXED,
            days = 0, // one-off: must not come back tomorrow
            snoozedUntil = System.currentTimeMillis() + delayMs,
        )
        ReminderStore.upsert(context, test)
        ReminderScheduler.schedule(context, test)
    }

    /**
     * Called when a fire broadcast arrives. Returns the reminder to ring, or null if this
     * occurrence was already handled (e.g. the backup firing after the main one rang).
     */
    fun onFire(context: Context, id: Int, trigger: Long, redelivered: Boolean): Reminder? {
        val reminder = ReminderStore.get(context, id) ?: return null
        if (!redelivered && (reminder.lastFiredAt == trigger || !reminder.enabled)) return null
        ReminderScheduler.cancelBackup(context, id)
        Notifications.cancelSnoozed(context, id)
        val updated = ReminderStore.update(context, id) {
            it.copy(
                lastFiredAt = trigger,
                snoozedUntil = 0,
                // One-time reminders switch off as soon as they ring; snooze turns them back on.
                enabled = it.isRepeating,
            )
        } ?: return null
        // Re-arm everything now, so tomorrow's reminders exist even if this process dies while ringing.
        ReminderScheduler.rescheduleAll(context, "reminder $id fired")
        return updated
    }

    fun snooze(context: Context, id: Int, auto: Boolean = false) {
        EventLog.log(context, "snooze reminder $id auto=$auto")
        val reminder = ReminderStore.get(context, id)
        if (reminder != null) {
            val until = System.currentTimeMillis() + reminder.snoozeMinutes * 60_000L
            val updated = ReminderStore.update(context, id) {
                it.copy(
                    enabled = true,
                    snoozedUntil = until,
                    autoSnoozes = if (auto) it.autoSnoozes + 1 else it.autoSnoozes,
                )
            }!!
            ReminderScheduler.schedule(context, updated)
            Notifications.showSnoozed(context, updated, until)
        }
        AlarmService.stop(context)
    }

    fun dismissFully(context: Context, id: Int) {
        EventLog.log(context, "dismiss reminder $id (done)")
        val updated = ReminderStore.update(context, id) {
            it.copy(snoozedUntil = 0, autoSnoozes = 0, enabled = it.isRepeating && it.enabled)
        }
        if (updated != null) {
            if (updated.enabled) ReminderScheduler.schedule(context, updated) else ReminderScheduler.cancel(context, id)
        }
        if (id == Reminder.TEST_ID) ReminderStore.delete(context, id)
        Notifications.cancelSnoozed(context, id)
        AlarmService.stop(context)
    }

    /** Nobody reacted within the auto-snooze time: ring again later rather than silently stopping. */
    fun onRingTimeout(context: Context, id: Int) {
        val reminder = ReminderStore.get(context, id)
        if (reminder != null && reminder.autoSnoozes < MAX_AUTO_SNOOZES) {
            snooze(context, id, auto = true)
        } else {
            dismissFully(context, id)
            if (reminder != null) Notifications.showMissed(context, reminder)
        }
    }
}
