package shakir.swalah.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fire broadcast from AlarmManager: hand over to the foreground service. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_FIRE) return
        // Keep the CPU awake until the service has taken over.
        WakeLocks.acquire(context, 60_000)
        val id = intent.getIntExtra(ReminderScheduler.EXTRA_ID, -1)
        val trigger = intent.getLongExtra(ReminderScheduler.EXTRA_TRIGGER, 0L)
        EventLog.log(
            context,
            "FIRE received reminder $id slot ${EventLog.time(trigger)} backup=${intent.getBooleanExtra(ReminderScheduler.EXTRA_BACKUP, false)}",
        )
        AlarmService.start(context, id, trigger)
    }
}

/** Snooze / Stop buttons in notifications. */
class AlarmActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, -1)
        when (intent.action) {
            ACTION_SNOOZE -> ReminderController.snooze(context, id)
            ACTION_DISMISS -> ReminderController.dismissFully(context, id)
            ACTION_STOP_PREVIEW -> AlarmService.stopPreview(context)
        }
    }

    companion object {
        const val ACTION_SNOOZE = "shakir.swalah.SNOOZE"
        const val ACTION_DISMISS = "shakir.swalah.DISMISS"
        const val ACTION_STOP_PREVIEW = "shakir.swalah.STOP_PREVIEW"
        const val EXTRA_ID = "id"
    }
}

/**
 * Reboot, app update, clock / time-zone change and exact-alarm permission changes can all
 * wipe or invalidate AlarmManager entries (and prayer times move with the time zone) - re-arm all.
 * LOCKED_BOOT_COMPLETED arrives before the first unlock, so reminders work after an overnight reboot.
 */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderScheduler.rescheduleAll(context, intent.action ?: "system event")
    }
}
