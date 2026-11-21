package shakir.swalah.alarm

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/** Everything on the phone that can stop an adhan or reminder from ringing, and how to fix it. */
object Reliability {

    data class Check(
        val title: String,
        val detail: String,
        val ok: Boolean,
        val fixLabel: String? = null,
        val fixIntent: Intent? = null,
        /** Manual checks can't be detected; the user confirms them. */
        val manualKey: String? = null,
        /** Nice to have: not counted as a problem, shown only in the expanded list. */
        val optional: Boolean = false,
    )

    private const val KEY_OEM_DONE = "oem_autostart_done"
    private const val KEY_HIDE_HOME_WARNING = "hide_home_warning"

    private val aggressiveOems = setOf(
        "xiaomi", "redmi", "poco", "huawei", "honor", "oppo", "realme", "oneplus",
        "vivo", "iqoo", "samsung", "asus", "meizu", "tecno", "infinix", "itel", "lenovo", "motorola", "nothing",
    )

    private fun prefs(context: Context) =
        context.createDeviceProtectedStorageContext().getSharedPreferences("reliability", Context.MODE_PRIVATE)

    @SuppressLint("BatteryLife")
    fun checks(context: Context): List<Check> {
        val pkg = context.packageName
        val pkgUri = Uri.parse("package:$pkg")
        val list = mutableListOf<Check>()

        list += Check(
            "Exact alarms allowed",
            "Lets the adhan play at the exact minute.",
            ReminderScheduler.canScheduleExact(context),
            "Allow",
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkgUri) else null,
        )
        list += Check(
            "Notifications allowed",
            "Needed for the adhan notification and the Stop / Snooze buttons.",
            NotificationManagerCompat.from(context).areNotificationsEnabled(),
            "Allow",
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg),
        )
        val nm = context.getSystemService(NotificationManager::class.java)
        val channel = nm.getNotificationChannel(Notifications.CH_RINGING)
        list += Check(
            "Adhan notifications set to urgent",
            "The 'Ringing adhan & reminders' category must not be silenced.",
            channel != null && channel.importance >= NotificationManager.IMPORTANCE_HIGH,
            "Fix",
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
                .putExtra(Settings.EXTRA_CHANNEL_ID, Notifications.CH_RINGING),
        )
        if (Build.VERSION.SDK_INT >= 34) {
            list += Check(
                "Full-screen alarms allowed",
                "Shows wake-up reminders (Suhoor, Tahajjud) over the lock screen.",
                nm.canUseFullScreenIntent(),
                "Allow",
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkgUri),
            )
        }
        list += Check(
            "Ringing screen over other apps",
            "Opens the ringing screen even while you use another app, instead of a notification.",
            Settings.canDrawOverlays(context),
            "Allow",
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri),
        )
        list += Check(
            "Battery optimization off",
            "Stops Android from putting the app to sleep.",
            context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(pkg),
            "Turn off",
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri),
        )
        val maker = Build.MANUFACTURER.lowercase()
        if (maker in aggressiveOems) {
            val brand = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
            val steps = if (maker == "samsung") {
                "Samsung puts apps to sleep. 1) App info > Battery > Unrestricted. " +
                    "2) Settings > Battery > Background usage limits > Never sleeping apps > add this app."
            } else {
                "$brand phones kill apps in the background. In App info, allow 'Autostart' / " +
                    "'Run in background' and set Battery to 'Unrestricted' / 'No restrictions'."
            }
            list += Check(
                "$brand background restrictions", steps,
                prefs(context).getBoolean(KEY_OEM_DONE, false),
                "Open app info",
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri),
                manualKey = KEY_OEM_DONE,
            )
        }
        return list
    }

    /** The user chose not to see the "may not play on time" banner on the home screen. */
    fun isHomeWarningHidden(context: Context): Boolean = prefs(context).getBoolean(KEY_HIDE_HOME_WARNING, false)

    fun setHomeWarningHidden(context: Context, hidden: Boolean) {
        prefs(context).edit().putBoolean(KEY_HIDE_HOME_WARNING, hidden).apply()
    }

    fun markManualDone(context: Context, key: String) {
        prefs(context).edit().putBoolean(key, true).apply()
    }
}
