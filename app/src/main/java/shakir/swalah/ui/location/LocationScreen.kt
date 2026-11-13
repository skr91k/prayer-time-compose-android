package shakir.swalah.ui.location

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import shakir.swalah.data.AppLocation
import shakir.swalah.data.Cities
import shakir.swalah.data.SettingsStore
import shakir.swalah.ui.common.AppIcons
import shakir.swalah.ui.common.AppScaffold
import kotlin.math.cos

/** Pick a city from the bundled list, or use the phone's current position. */
@Composable
fun LocationScreen(firstRun: Boolean, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cities by remember { mutableStateOf<List<AppLocation>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var current by remember { mutableStateOf<AppLocation?>(null) }
    var locating by remember { mutableStateOf(false) }
    var gpsMessage by remember { mutableStateOf<String?>(null) }
    val saved = remember { SettingsStore.get(context).location }

    LaunchedEffect(Unit) { cities = withContext(Dispatchers.IO) { Cities.all(context) } }

    fun choose(location: AppLocation) {
        SettingsStore.update(context) { it.copy(location = location) }
        onDone()
    }

    fun locate() {
        locating = true
        gpsMessage = null
        scope.launch {
            val fix = CurrentLocation.fetch(context)
            if (fix == null) {
                gpsMessage = "Couldn't get your position. Is location turned on?"
            } else {
                val name = CurrentLocation.locality(context, fix)
                current = AppLocation(name.ifBlank { "My Location" }, fix.latitude, fix.longitude)
            }
            locating = false
        }
    }

    val settingsResolution = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { locate() }

    /** Asks to turn location on if needed, then gets a fix. */
    fun ensureLocationOnThenLocate() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 60_000).build()
        LocationServices.getSettingsClient(context)
            .checkLocationSettings(LocationSettingsRequest.Builder().addLocationRequest(request).build())
            .addOnSuccessListener { locate() }
            .addOnFailureListener { e ->
                if (e is ResolvableApiException) {
                    settingsResolution.launch(IntentSenderRequest.Builder(e.resolution).build())
                } else locate()
            }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) ensureLocationOnThenLocate() else gpsMessage = "Location permission denied - pick your city from the list."
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (granted) ensureLocationOnThenLocate()
        else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    val reference = current ?: saved
    val shown = remember(cities, query, reference) {
        val q = query.trim()
        when {
            q.isNotEmpty() -> (cities.filter { it.name.startsWith(q, ignoreCase = true) } + cities.filter { it.name.contains(q, ignoreCase = true) })
                .distinct().take(200)
            reference != null -> cities.sortedBy { distanceScore(reference, it) }.take(200)
            else -> cities.take(200)
        }
    }

    AppScaffold(title = if (firstRun) "Choose your location" else "Location", onBack = if (firstRun) null else onDone) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clickable(enabled = current != null) { current?.let(::choose) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.MyLocation, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Select current location", fontWeight = FontWeight.SemiBold)
                        Text(
                            when {
                                locating -> "Finding your position…"
                                current != null -> current!!.name
                                else -> gpsMessage ?: "Tap the button to use GPS"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (locating) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    else IconButton(onClick = {
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (granted) ensureLocationOnThenLocate()
                        else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }) { Icon(AppIcons.MyLocation, "Use GPS") }
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, "Clear") } },
                placeholder = { Text("Search city") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { "${it.name}${it.latitude}${it.longitude}" }) { city ->
                    Text(
                        city.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().clickable { choose(city) }.padding(horizontal = 24.dp, vertical = 14.dp),
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }
}

/** Squared equirectangular distance - only used for ordering. */
private fun distanceScore(a: AppLocation, b: AppLocation): Double {
    val x = (b.longitude - a.longitude) * cos(Math.toRadians((a.latitude + b.latitude) / 2))
    val y = b.latitude - a.latitude
    return x * x + y * y
}
