package shakir.swalah.alarm

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import shakir.swalah.reminder.Reminder
import shakir.swalah.reminder.ReminderStore
import shakir.swalah.ui.RingingActivity

/**
 * Foreground service that owns the ringing reminder. The sound is independent of the UI:
 * closing the ringing screen never silences a wake-up alarm.
 */
class AlarmService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var sound: AlarmSound? = null
    private var current: Reminder? = null
    private var startedAt = 0L
    private val timeout = Runnable {
        val id = current?.id ?: return@Runnable
        if (_ringing.value?.preview == true) stopSelf() else ReminderController.onRingTimeout(this, id)
    }
    private val autoStop = Runnable { finishRing() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getIntExtra(EXTRA_ID, -1) ?: -1
        val trigger = intent?.getLongExtra(EXTRA_TRIGGER, 0L) ?: 0L
        val redelivered = flags and START_FLAG_REDELIVERY != 0
        if (intent?.getBooleanExtra(EXTRA_PREVIEW, false) == true) return startPreview(id)
        val previewing = _ringing.value?.preview == true

        // Decide first so the very first notification already carries the full-screen intent.
        val fired = if (id == current?.id && _ringing.value != null && !previewing) null
        else ReminderController.onFire(this, id, trigger, redelivered)
        val reminder = fired

        EventLog.log(this, "service start id=$id redelivered=$redelivered -> ${if (reminder != null) "RING" else "ignored"}")
        val notification = (reminder ?: current?.takeIf { _ringing.value != null })
            ?.let { Notifications.buildRinging(this, it, preview = reminder == null && previewing) }
            ?: Notifications.buildPlaceholder(this)
        try {
            ServiceCompat.startForeground(this, Notifications.RINGING_ID, notification, fgsType())
        } catch (e: Exception) {
            EventLog.log(this, "startForeground FAILED - fallback notification", e)
            reminder?.let { Notifications.postFallback(this, it) }
            stopSelf()
            return START_NOT_STICKY
        }

        if (reminder == null) {
            if (_ringing.value == null) stopSelf()
            return START_NOT_STICKY
        }

        // A second reminder while a wake-up alarm is ringing: snooze the old one so it isn't lost.
        val previous = _ringing.value
        val previousReminder = current
        if (previous != null && previous.id != reminder.id && !previous.preview && previousReminder?.isWakeUp == true) {
            ReminderStore.update(this, previous.id) {
                it.copy(enabled = true, snoozedUntil = System.currentTimeMillis() + it.snoozeMinutes * 60_000L)
            }?.let { ReminderScheduler.schedule(this, it) }
        } else if (previous != null && !previous.preview && previousReminder != null) {
            Notifications.showRang(this, previousReminder, startedAt)
        }
        ring(reminder)
        // A killed wake-up alarm comes back; a killed adhan should not start over.
        return if (reminder.isWakeUp) START_REDELIVER_INTENT else START_NOT_STICKY
    }

    private fun fgsType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0

    /** Rings [id] now with all its settings, without touching its schedule or state. */
    private fun startPreview(id: Int): Int {
        val reminder = ReminderStore.get(this, id)
        val busy = _ringing.value?.let { !it.preview } == true
        if (reminder == null || busy) {
            if (_ringing.value == null) stopSelf()
            return START_NOT_STICKY
        }
        try {
            ServiceCompat.startForeground(this, Notifications.RINGING_ID, Notifications.buildRinging(this, reminder, preview = true), fgsType())
        } catch (e: Exception) {
            EventLog.log(this, "preview startForeground failed", e)
            stopSelf()
            return START_NOT_STICKY
        }
        EventLog.log(this, "preview reminder $id")
        ring(reminder, preview = true)
        return START_NOT_STICKY
    }

    private fun ring(reminder: Reminder, preview: Boolean = false) {
        current = reminder
        startedAt = System.currentTimeMillis()
        val playOnce = reminder.autoStopSeconds == Reminder.PLAY_ONCE
        WakeLocks.acquire(this, if (playOnce) MAX_PLAY_ONCE_MS + 60_000 else reminder.autoSnoozeSeconds * 1_000L + 60_000)
        sound?.stop()
        sound = AlarmSound(this).also {
            it.start(reminder.ringtoneUri, reminder.vibrate, loop = !playOnce, maxVolume = reminder.isWakeUp, onFinished = ::finishRing)
        }
        EventLog.log(this, "ringing reminder ${reminder.id}")
        _ringing.value = Ringing(
            reminder.id, reminder.label, reminder.snoozeMinutes, reminder.mathChallenge,
            reminder.autoStopSeconds, preview = preview,
        )
        getSystemService(NotificationManager::class.java)
            .notify(Notifications.RINGING_ID, Notifications.buildRinging(this, reminder, preview))
        handler.removeCallbacks(timeout)
        handler.removeCallbacks(autoStop)
        when {
            playOnce -> handler.postDelayed(autoStop, MAX_PLAY_ONCE_MS)
            reminder.autoStopSeconds > 0 -> handler.postDelayed(autoStop, reminder.autoStopSeconds * 1000L)
            else -> handler.postDelayed(timeout, reminder.autoSnoozeSeconds * 1_000L)
        }
        // Adhan / iqama stay a heads-up notification; wake-up style reminders open the ringing screen.
        // A preview behaves exactly like the real thing.
        if (reminder.fullScreen) {
            try {
                startActivity(RingingActivity.intent(this))
            } catch (_: Exception) {
            }
        }
    }

    /** The sound finished (play once) or its auto-stop time is up. */
    private fun finishRing() {
        val id = current?.id ?: return stopSelf()
        EventLog.log(this, "finished reminder $id")
        if (_ringing.value?.preview == true) stopSelf() else ReminderController.dismissFully(this, id)
    }

    override fun onDestroy() {
        EventLog.log(this, "service stopped (reminder ${current?.id})")
        val ended = current?.takeIf { _ringing.value?.preview == false && !it.isWakeUp }
        handler.removeCallbacks(timeout)
        handler.removeCallbacks(autoStop)
        sound?.stop()
        sound = null
        _ringing.value = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        ended?.let { Notifications.showRang(this, it, startedAt) }
        WakeLocks.release()
        super.onDestroy()
    }

    data class Ringing(
        val id: Int,
        val label: String,
        val snoozeMinutes: Int,
        val mathChallenge: Boolean,
        val autoStopSeconds: Int,
        /** Started from the list's "Preview": every button only ends the preview. */
        val preview: Boolean = false,
    ) {
        val isWakeUp: Boolean get() = autoStopSeconds == 0
    }

    companion object {
        private const val EXTRA_ID = "id"
        private const val EXTRA_TRIGGER = "trigger"
        private const val EXTRA_PREVIEW = "preview"
        /** Safety stop for a play-once sound that never reports completion. */
        private const val MAX_PLAY_ONCE_MS = 10 * 60_000L

        private val _ringing = MutableStateFlow<Ringing?>(null)
        val ringing: StateFlow<Ringing?> = _ringing

        fun start(context: Context, id: Int, trigger: Long) {
            val intent = Intent(context, AlarmService::class.java).putExtra(EXTRA_ID, id).putExtra(EXTRA_TRIGGER, trigger)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                EventLog.log(context, "startForegroundService FAILED - fallback notification", e)
                ReminderController.onFire(context, id, trigger, redelivered = false)
                    ?.let { Notifications.postFallback(context, it) }
            }
        }

        /** Rings this reminder now so it can be heard; its real schedule is untouched. */
        fun preview(context: Context, id: Int): Boolean {
            if (_ringing.value?.preview == false) return false
            ContextCompat.startForegroundService(
                context,
                Intent(context, AlarmService::class.java).putExtra(EXTRA_ID, id).putExtra(EXTRA_PREVIEW, true),
            )
            return true
        }

        fun stopPreview(context: Context) {
            if (_ringing.value?.preview == true) stop(context)
        }

        /** Volume key / Stop: ends whatever is playing unless it's a wake-up alarm that needs dismissing. */
        fun stopIfNotWakeUp(context: Context): Boolean {
            val r = _ringing.value ?: return false
            if (r.isWakeUp && !r.preview) return false
            if (r.preview) stop(context) else ReminderController.dismissFully(context, r.id)
            return true
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AlarmService::class.java))
        }
    }
}
