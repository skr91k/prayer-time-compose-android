package shakir.swalah.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import shakir.swalah.data.AppSettings
import shakir.swalah.data.SettingsStore
import shakir.swalah.prayer.CountdownPhase
import shakir.swalah.prayer.Hijri
import shakir.swalah.prayer.HomeTimes
import shakir.swalah.prayer.Prayer
import shakir.swalah.prayer.PrayerCalculator
import shakir.swalah.prayer.TimeFormat
import kotlin.math.abs

data class PrayerRowUi(val prayer: Prayer, val time: String)

data class HomeUiState(
    val locationName: String = "",
    val hijriDate: String = "",
    val rows: List<PrayerRowUi> = emptyList(),
    val midnight: String? = null,
    val lastThird: String? = null,
    val countdown: String = "",
    val countdownTitle: String = "",
    val phase: CountdownPhase = CountdownPhase.NEXT_ADHAN,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    val state: StateFlow<HomeUiState> = SettingsStore.flow(app).filterNotNull()
        .flatMapLatest { settings -> ticker(settings) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Re-builds the screen every second for the countdown; the calculator caches each day. */
    private fun ticker(settings: AppSettings) = flow {
        val calc = PrayerCalculator.from(settings) ?: return@flow emit(HomeUiState(locationName = "Set location"))
        val format = TimeFormat(getApplication(), settings)
        while (true) {
            val now = System.currentTimeMillis()
            emit(build(now, settings, calc, format))
            delay(1_000 - now % 1_000)
        }
    }

    private fun build(now: Long, settings: AppSettings, calc: PrayerCalculator, format: TimeFormat): HomeUiState {
        val today = calc.today(now)
        val night = HomeTimes.night(now, calc, settings.thirdOfNight)
        val countdown = HomeTimes.countdown(now, calc, settings)
        return HomeUiState(
            locationName = settings.location?.name?.ifBlank { "My Location" } ?: "",
            hijriDate = Hijri.arabic(today.date),
            rows = Prayer.entries.map { PrayerRowUi(it, format.format(today[it])) },
            midnight = if (settings.showMidnight) format.format(night.midnight) else null,
            lastThird = night.lastThird?.let(format::format),
            countdown = hms(countdown.target - now),
            countdownTitle = countdown.title,
            phase = countdown.phase,
        )
    }

    private fun hms(ms: Long): String {
        val total = abs(ms) / 1000
        return "%02d:%02d:%02d".format(total / 3600, (total % 3600) / 60, total % 60)
    }
}
