package shakir.swalah

import android.app.Application
import android.os.UserManager
import shakir.swalah.alarm.EventLog
import shakir.swalah.alarm.Notifications
import shakir.swalah.alarm.ReminderScheduler
import shakir.swalah.data.LegacyCleanup
import shakir.swalah.reminder.ReminderStore

class PrayerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            EventLog.log(this, "CRASH in ${t.name}: ${e.stackTraceToString().take(1500)}")
            previous?.uncaughtException(t, e)
        }
        Thread { prepareData() }.start()
    }

    /** Creates the built-in reminders. Skipped in direct boot (before first unlock); runs on the next start. */
    @Synchronized
    fun prepareData() {
        if (!getSystemService(UserManager::class.java).isUserUnlocked) return
        LegacyCleanup.runIfNeeded(this)
        ReminderStore.seedIfNeeded(this)
        ReminderScheduler.rescheduleAll(this, "app start")
    }
}
