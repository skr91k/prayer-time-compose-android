package shakir.swalah.data

import android.app.NotificationManager
import android.content.Context
import shakir.swalah.alarm.Notifications

/**
 * The old (View-based) app is replaced in place. Nothing is imported from it - every setting
 * starts from its default. Clean-up only: its per-sound notification channels, and its audio
 * download folders (filesDir/adhanMp3s, filesDir/temp) - sounds now download to a new folder.
 */
object LegacyCleanup {
    private const val KEY_DONE = "legacy_channels_removed_v1"
    private const val KEY_DIRS_DONE = "legacy_dirs_removed_v1"

    /** Needs the user unlocked: the old folders are in credential-protected storage. */
    fun runIfNeeded(context: Context) {
        val marker = SettingsStore.prefs(context)
        if (!marker.getBoolean(KEY_DIRS_DONE, false)) {
            listOf("adhanMp3s", "temp").forEach { java.io.File(context.filesDir, it).deleteRecursively() }
            marker.edit().putBoolean(KEY_DIRS_DONE, true).apply()
        }
        if (marker.getBoolean(KEY_DONE, false)) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.notificationChannels
            .filter { it.id !in Notifications.ALL_CHANNELS }
            .forEach { nm.deleteNotificationChannel(it.id) }
        marker.edit().putBoolean(KEY_DONE, true).apply()
    }
}
