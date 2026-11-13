package shakir.swalah.ui.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.os.Build
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/** GPS fix via Play Services and a city name via the Android Geocoder. */
object CurrentLocation {

    @SuppressLint("MissingPermission")
    suspend fun fetch(context: Context): Location? = suspendCancellableCoroutine { cont ->
        val token = CancellationTokenSource()
        cont.invokeOnCancellation { token.cancel() }
        try {
            LocationServices.getFusedLocationProviderClient(context)
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token)
                .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        } catch (_: Exception) {
            if (cont.isActive) cont.resume(null)
        }
    }

    /** Locality name, or "" when the Geocoder has nothing (offline, no backend). */
    suspend fun locality(context: Context, location: Location): String {
        if (!Geocoder.isPresent()) return ""
        val geocoder = Geocoder(context, Locale.getDefault())
        return try {
            if (Build.VERSION.SDK_INT >= 33) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(location.latitude, location.longitude, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<android.location.Address>) {
                            if (cont.isActive) cont.resume(addresses.firstOrNull()?.let { it.locality ?: it.subAdminArea ?: it.adminArea }.orEmpty())
                        }
                        override fun onError(errorMessage: String?) {
                            if (cont.isActive) cont.resume("")
                        }
                    })
                }
            } else withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    ?.firstOrNull()?.let { it.locality ?: it.subAdminArea ?: it.adminArea }.orEmpty()
            }
        } catch (_: Exception) {
            ""
        }
    }
}
