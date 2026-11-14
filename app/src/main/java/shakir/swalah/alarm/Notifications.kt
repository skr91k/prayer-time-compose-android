package shakir.swalah.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import shakir.swalah.R
import shakir.swalah.data.SettingsStore
import shakir.swalah.prayer.TimeFormat
import shakir.swalah.reminder.Reminder
import shakir.swalah.ui.MainActivity
import shakir.swalah.ui.RingingActivity

object Notifications {
    const val CH_RINGING = "ringing"
    const val CH_STATUS = "status"
    const val CH_INFO = "prayer_info"
    /** Used only if the foreground service can't start: the notification itself plays the sound. */
    const val CH_FALLBACK = "fallback_alarm"
    val ALL_CHANNELS = setOf(CH_RINGING, CH_STATUS, CH_INFO, CH_FALLBACK)

    const val RINGING_ID = 1
    private const val INFO_ID = 2
    private const val SNOOZED_BASE = 10_000
    private const val MISSED_BASE = 20_000

    private const val ACCENT = 0xFF0C2752.toInt()

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_RINGING, "Ringing adhan & reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Shown while an adhan, iqama or reminder is playing"
                setSound(null, null) // the service plays the sound itself
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_STATUS, "Snoozed & missed reminders", NotificationManager.IMPORTANCE_DEFAULT),
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_INFO, "Prayer time notices", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Stays after an adhan has played"
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_FALLBACK, "Backup alarm", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Emergency alarm used if the normal one can't start"
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
                )
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            },
        )
    }

    /** "الفجر 05:12" in the user's chosen time format. */
    fun title(context: Context, reminder: Reminder, at: Long = System.currentTimeMillis()): String =
        reminder.label.ifBlank { "Reminder" } + "  " + formatTime(context, at)

    fun formatTime(context: Context, millis: Long): String =
        "‎" + TimeFormat(context, SettingsStore.get(context)).format(millis)

    fun buildRinging(context: Context, reminder: Reminder, preview: Boolean = false): Notification {
        val open = PendingIntent.getActivity(
            context, reminder.id, RingingActivity.intent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CH_RINGING)
            .setSmallIcon(R.drawable.ic_stat_notifications)
            .setColor(ACCENT)
            .setContentTitle((if (preview) "Preview: " else "") + title(context, reminder))
            .setContentText(if (reminder.isWakeUp) "Tap to open" else "Tap or swipe to stop")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (reminder.fullScreen) builder.setFullScreenIntent(open, true)
        when {
            preview -> {
                val stop = action(context, AlarmActionReceiver.ACTION_STOP_PREVIEW, reminder.id)
                builder.addAction(0, "Stop preview", stop)
                // Swiping a preview away stops it too, like the real heads-up adhan.
                builder.setDeleteIntent(stop)
            }
            reminder.isWakeUp -> {
                builder.setOngoing(true)
                builder.addAction(0, "Snooze ${reminder.snoozeMinutes} min", action(context, AlarmActionReceiver.ACTION_SNOOZE, reminder.id))
            }
            else -> {
                // Adhan / short reminder: one tap (or a swipe) stops it.
                val stop = action(context, AlarmActionReceiver.ACTION_DISMISS, reminder.id)
                builder.addAction(0, "Stop", stop)
                builder.setDeleteIntent(stop)
            }
        }
        return builder.build()
    }

    fun buildPlaceholder(context: Context): Notification =
        NotificationCompat.Builder(context, CH_RINGING)
            .setSmallIcon(R.drawable.ic_stat_notifications)
            .setContentTitle(context.getString(R.string.app_name))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    /** Left behind after an adhan / iqama played, like the old app's notification. */
    fun showRang(context: Context, reminder: Reminder, at: Long) {
        val n = NotificationCompat.Builder(context, CH_INFO)
            .setSmallIcon(R.drawable.ic_stat_notifications)
            .setColor(ACCENT)
            .setContentTitle(title(context, reminder, at))
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .setSilent(true)
            .build()
        notify(context, INFO_ID, n)
    }

    /** Last line of defence: a notification that loops the alarm sound on its own. */
    fun postFallback(context: Context, reminder: Reminder) {
        val n = NotificationCompat.Builder(context, CH_FALLBACK)
            .setSmallIcon(R.drawable.ic_stat_notifications)
            .setContentTitle(title(context, reminder))
            .setContentText("Tap to stop")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(openApp(context), true)
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        if (reminder.isWakeUp) n.flags = n.flags or Notification.FLAG_INSISTENT
        notify(context, MISSED_BASE + reminder.id, n)
    }

    fun showSnoozed(context: Context, reminder: Reminder, until: Long) {
        val n = NotificationCompat.Builder(context, CH_STATUS)
            .setSmallIcon(R.drawable.ic_stat_notifications)
            .setContentTitle("Snoozed until ${formatTime(context, until)}")
            .setContentText(reminder.label.ifBlank { "Reminder" })
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .build()
        notify(context, SNOOZED_BASE + reminder.id, n)
    }

    fun cancelSnoozed(context: Context, id: Int) {
        context.getSystemService(NotificationManager::class.java).cancel(SNOOZED_BASE + id)
    }

    fun showMissed(context: Context, reminder: Reminder) {
        val n = NotificationCompat.Builder(context, CH_STATUS)
            .setSmallIcon(R.drawable.ic_stat_notifications)
            .setContentTitle("Reminder rang without response")
            .setContentText("${reminder.label.ifBlank { "Reminder" }} rang ${ReminderController.MAX_AUTO_SNOOZES + 1} times and was stopped")
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        notify(context, MISSED_BASE + reminder.id, n)
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun notify(context: Context, id: Int, n: Notification) {
        try {
            context.getSystemService(NotificationManager::class.java).notify(id, n)
        } catch (_: SecurityException) {
            // Notification permission denied; the reminder itself still rings.
        }
    }

    private fun action(context: Context, action: String, id: Int): PendingIntent {
        val intent = Intent(context, AlarmActionReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse("adhan://action/$id/$action"))
            .putExtra(AlarmActionReceiver.EXTRA_ID, id)
        return PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
