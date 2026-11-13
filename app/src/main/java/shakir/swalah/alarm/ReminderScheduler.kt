package shakir.swalah.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import shakir.swalah.data.SettingsStore
import shakir.swalah.prayer.PrayerCalculator
import shakir.swalah.reminder.Reminder
import shakir.swalah.reminder.ReminderStore
import shakir.swalah.ui.MainActivity

/**
 * Every enabled reminder is armed twice:
 *  1. setAlarmClock() - top-priority, exempt from Doze and app standby.
 *  2. a backup setExactAndAllowWhileIdle() shortly after, which only rings if the main one was
 *     never delivered (each occurrence is de-duplicated by its trigger time).
 * Prayer-based reminders are re-computed from the current settings every time they are armed.
 */
object ReminderScheduler {
    const val ACTION_FIRE = "shakir.swalah.FIRE"
    const val EXTRA_ID = "id"
    const val EXTRA_TRIGGER = "trigger"
    const val EXTRA_BACKUP = "backup"

    private const val BACKUP_DELAY_MS = 90_000L
    /** A wake-up alarm missed less than this long ago (phone off, app killed...) rings immediately. */
    private const val MISSED_WINDOW_WAKE_UP_MS = 2 * 60 * 60 * 1000L
    /** An adhan / iqama is only worth playing late if it is just a few minutes late. */
    private const val MISSED_WINDOW_SHORT_MS = 10 * 60 * 1000L

    fun calculator(context: Context): PrayerCalculator? = PrayerCalculator.from(SettingsStore.get(context))

    @Synchronized
    fun rescheduleAll(context: Context, reason: String) {
        val now = System.currentTimeMillis()
        val calc = calculator(context)
        EventLog.log(context, "rescheduleAll ($reason), exact=${canScheduleExact(context)}, location=${calc != null}")
        for (reminder in ReminderStore.all(context)) {
            if (!reminder.enabled) {
                cancel(context, reminder.id)
                continue
            }
            val window = if (reminder.isWakeUp) MISSED_WINDOW_WAKE_UP_MS else MISSED_WINDOW_SHORT_MS
            val missed = reminder.scheduledAt in (now - window) until now && reminder.lastFiredAt != reminder.scheduledAt
            if (missed) {
                EventLog.log(context, "MISSED reminder ${reminder.id} slot ${EventLog.time(reminder.scheduledAt)} - ringing now")
                arm(context, reminder.id, trigger = reminder.scheduledAt, fireAt = now + 3_000)
            } else {
                schedule(context, reminder, calc)
            }
        }
    }

    /** Arms the next occurrence of [reminder]; returns its trigger time (null = can't compute). */
    fun schedule(context: Context, reminder: Reminder, calc: PrayerCalculator? = calculator(context)): Long? {
        val trigger = reminder.nextTrigger(calc)
        if (trigger == null) {
            EventLog.log(context, "reminder ${reminder.id} has no trigger (no location?) - not armed")
            cancel(context, reminder.id)
            return null
        }
        if (trigger <= System.currentTimeMillis()) {
            EventLog.log(context, "reminder ${reminder.id} date ${reminder.date} has passed - switching off")
            cancel(context, reminder.id)
            ReminderStore.update(context, reminder.id) { it.copy(enabled = false) }
            return trigger
        }
        arm(context, reminder.id, trigger, trigger)
        if (trigger != reminder.scheduledAt) EventLog.log(context, "armed reminder ${reminder.id} for ${EventLog.time(trigger)}")
        ReminderStore.update(context, reminder.id) { it.copy(scheduledAt = trigger) }
        return trigger
    }

    fun cancel(context: Context, id: Int) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(firePendingIntent(context, id, 0, backup = false))
        am.cancel(firePendingIntent(context, id, 0, backup = true))
    }

    fun cancelBackup(context: Context, id: Int) {
        context.getSystemService(AlarmManager::class.java).cancel(firePendingIntent(context, id, 0, backup = true))
    }

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun arm(context: Context, id: Int, trigger: Long, fireAt: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        val main = firePendingIntent(context, id, trigger, backup = false)
        val backup = firePendingIntent(context, id, trigger, backup = true)
        val show = PendingIntent.getActivity(
            context, id, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            if (canScheduleExact(context)) am.setAlarmClock(AlarmManager.AlarmClockInfo(fireAt, show), main)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, main)
        } catch (e: SecurityException) {
            EventLog.log(context, "setAlarmClock REFUSED for reminder $id - inexact fallback", e)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, main)
        }
        try {
            if (canScheduleExact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt + BACKUP_DELAY_MS, backup)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt + BACKUP_DELAY_MS, backup)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt + BACKUP_DELAY_MS, backup)
        }
    }

    private fun firePendingIntent(context: Context, id: Int, trigger: Long, backup: Boolean): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(ACTION_FIRE)
            // Unique data per (reminder, main/backup) so the two PendingIntents never collide.
            .setData(Uri.parse("adhan://reminder/$id/${if (backup) "backup" else "main"}"))
            .putExtra(EXTRA_ID, id)
            .putExtra(EXTRA_TRIGGER, trigger)
            .putExtra(EXTRA_BACKUP, backup)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
