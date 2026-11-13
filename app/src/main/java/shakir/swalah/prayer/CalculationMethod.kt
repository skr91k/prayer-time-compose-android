package shakir.swalah.prayer

import com.azan.Method

/** Calculation methods offered in Adhan time settings. The index is what gets stored. */
enum class CalculationMethod(val method: Method, val title: String, val details: String) {
    KARACHI_SHAFI(
        Method.KARACHI_SHAF, "University of Islamic Sciences, Karachi (Shafi'i)",
        "Fajr angle 18°, Isha angle 18°\nUsed in: Iran, Kuwait, parts of Europe",
    ),
    KARACHI_HANAFI(
        Method.KARACHI_HANAF, "University of Islamic Sciences, Karachi (Hanafi)",
        "Fajr angle 18°, Isha angle 18°\nUsed in: Afghanistan, Bangladesh, India",
    ),
    EGYPT(
        Method.EGYPT_SURVEY, "Egyptian General Authority of Survey",
        "Fajr angle 20°, Isha angle 18°\nUsed in: Indonesia, Iraq, Jordan, Lebanon, Malaysia, Singapore, Syria, parts of Africa and the United States",
    ),
    ISNA(
        Method.NORTH_AMERICA, "Islamic Society of North America",
        "Fajr angle 15°, Isha angle 15°\nUsed in: Canada, parts of the UK and the United States",
    ),
    MWL(
        Method.MUSLIM_LEAGUE, "Muslim World League (MWL)",
        "Fajr angle 18°, Isha angle 17°\nUsed in: parts of Europe, Far East, parts of the United States",
    ),
    UMM_AL_QURA(
        Method.UMM_ALQURRA, "Umm Al-Qura University",
        "Fajr angle 19°, Isha 90 minutes after Maghrib\nUsed in: Saudi Arabia",
    ),
    FIXED_ISHA(
        Method.FIXED_ISHAA, "Fixed Isha interval (always 90 min)",
        "Fajr angle 19.5°, Isha 90 minutes after Maghrib\nUsed in: Bahrain, Oman, Qatar, United Arab Emirates",
    );

    companion object {
        fun at(index: Int): CalculationMethod = entries.getOrElse(index) { KARACHI_SHAFI }
    }
}
