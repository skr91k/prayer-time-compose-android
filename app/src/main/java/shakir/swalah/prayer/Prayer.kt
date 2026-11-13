package shakir.swalah.prayer

/** The six daily times shown on the home screen, in calculation order. */
enum class Prayer(val arabic: String, val english: String) {
    FAJR("الفجر", "Fajr"),
    SUNRISE("الشروق", "Sunrise"),
    DHUHR("الظهر", "Dhuhr"),
    ASR("العصر", "Asr"),
    MAGHRIB("المغرب", "Maghrib"),
    ISHA("العشاء", "Isha");

    /** Sunrise has no iqama. */
    val hasIqama: Boolean get() = this != SUNRISE

    companion object {
        /** The five prayers that have an iqama, in order. */
        val WITH_IQAMA: List<Prayer> = entries.filter { it.hasIqama }
    }
}
