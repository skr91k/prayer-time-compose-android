package shakir.swalah.data

import org.json.JSONArray
import org.json.JSONObject
import shakir.swalah.prayer.Prayer

data class AppLocation(val name: String, val latitude: Double, val longitude: Double)

enum class ThirdOfNight(val title: String) { ISHA_BASED("Isha based"), MAGHRIB_BASED("Maghrib based"), HIDE("Hide") }

/**
 * Iqama time of one prayer, used for the home-screen countdown only
 * (iqama *alarms* are reminders with their own offset).
 * @param fixedMinuteOfDay used when [afterAdhan] is false; minutes since midnight.
 */
data class IqamaRule(val afterAdhan: Boolean = true, val minutes: Int = 20, val fixedMinuteOfDay: Int = -1)

data class AppSettings(
    val location: AppLocation? = null,
    val methodIndex: Int = 0,
    /** Minutes added to each calculated time, indexed by [Prayer.ordinal]. */
    val adjustments: List<Int> = List(Prayer.entries.size) { 0 },
    /** Null follows the phone's 12/24-hour setting. */
    val use24Hour: Boolean? = null,
    val showAmPm: Boolean = false,
    val showMidnight: Boolean = false,
    val thirdOfNight: ThirdOfNight = ThirdOfNight.ISHA_BASED,
    val iqamaCountdown: Boolean = true,
    /** One rule per [Prayer.WITH_IQAMA] entry. */
    val iqama: List<IqamaRule> = DEFAULT_IQAMA,
) {
    fun iqamaFor(prayer: Prayer): IqamaRule? =
        Prayer.WITH_IQAMA.indexOf(prayer).takeIf { it >= 0 }?.let { iqama.getOrNull(it) }

    fun toJson(): JSONObject = JSONObject().apply {
        location?.let {
            put("locName", it.name); put("lat", it.latitude); put("lng", it.longitude)
        }
        put("method", methodIndex)
        put("adjustments", JSONArray(adjustments))
        use24Hour?.let { put("use24Hour", it) }
        put("showAmPm", showAmPm)
        put("showMidnight", showMidnight)
        put("thirdOfNight", thirdOfNight.name)
        put("iqamaCountdown", iqamaCountdown)
        put("iqama", JSONArray().apply {
            iqama.forEach { r ->
                put(JSONObject().put("after", r.afterAdhan).put("minutes", r.minutes).put("fixed", r.fixedMinuteOfDay))
            }
        })
    }

    companion object {
        val DEFAULT_IQAMA = listOf(
            IqamaRule(minutes = 20), IqamaRule(minutes = 20), IqamaRule(minutes = 20),
            IqamaRule(minutes = 5), IqamaRule(minutes = 20),
        )

        fun fromJson(o: JSONObject): AppSettings {
            val adj = o.optJSONArray("adjustments")
            val iq = o.optJSONArray("iqama")
            return AppSettings(
                location = if (o.has("lat")) AppLocation(o.optString("locName"), o.getDouble("lat"), o.getDouble("lng")) else null,
                methodIndex = o.optInt("method"),
                adjustments = List(Prayer.entries.size) { adj?.optInt(it) ?: 0 },
                use24Hour = if (o.has("use24Hour")) o.getBoolean("use24Hour") else null,
                showAmPm = o.optBoolean("showAmPm"),
                showMidnight = o.optBoolean("showMidnight"),
                thirdOfNight = runCatching { ThirdOfNight.valueOf(o.optString("thirdOfNight")) }.getOrDefault(ThirdOfNight.ISHA_BASED),
                iqamaCountdown = o.optBoolean("iqamaCountdown", true),
                iqama = List(Prayer.WITH_IQAMA.size) { i ->
                    iq?.optJSONObject(i)?.let {
                        IqamaRule(it.optBoolean("after", true), it.optInt("minutes", 20), it.optInt("fixed", -1))
                    } ?: DEFAULT_IQAMA[i]
                },
            )
        }
    }
}
