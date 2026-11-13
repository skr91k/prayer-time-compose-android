package shakir.swalah.ui.qibla

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.hardware.GeomagneticField
import android.os.Build
import android.widget.ImageView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import shakir.swalah.R
import shakir.swalah.data.SettingsStore
import shakir.swalah.ui.common.AppScaffold
import shakir.swalah.ui.common.CenteredMessage
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val KAABA_LAT = 21.4225
private const val KAABA_LNG = 39.8262

/** Compass with the Kaaba needle, for the location chosen in the app. */
@Composable
fun QiblaScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val location = remember { SettingsStore.get(context).location }
    var showIntro by rememberSaveable { mutableStateOf(true) }

    AppScaffold(title = "Qibla | قِبْلَة", onBack = onBack) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                location == null -> CenteredMessage("Set your location first")
                !hasCompass(context) -> CenteredMessage("This phone has no compass sensor")
                else -> {
                    val bearing = remember(location) { qiblaBearing(location.latitude, location.longitude) }
                    val declination = remember(location) {
                        GeomagneticField(location.latitude.toFloat(), location.longitude.toFloat(), 0f, System.currentTimeMillis()).declination
                    }
                    val heading by rememberCompassHeading()
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(location.name.ifBlank { "My Location" }, color = Color.White, fontSize = 18.sp, modifier = Modifier.padding(8.dp))
                        Text("Qibla ${bearing.roundToInt()}° from North", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(16.dp))
                        val trueHeading = heading?.let { (it + declination + 360f) % 360f }
                        Compass(trueHeading, bearing)
                        if (heading == null) Text("Waiting for the compass…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (showIntro) CalibrationIntro { showIntro = false }
        }
    }
}

@Composable
private fun Compass(heading: Float?, qibla: Double) {
    val dialTarget = -(heading ?: 0f)
    val needleTarget = (qibla.toFloat() - (heading ?: 0f))
    val dialAngle = remember { ContinuousAngle() }
    val needleAngle = remember { ContinuousAngle() }
    val dial by animateFloatAsState(dialAngle.next(dialTarget), tween(200), label = "dial")
    val needle by animateFloatAsState(needleAngle.next(needleTarget), tween(200), label = "needle")
    Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.gold_round), null, Modifier.fillMaxSize().padding(32.dp), contentScale = ContentScale.Fit)
        // Dial: N/E/S/W letters and the gold rose, turned so N points north.
        Box(Modifier.fillMaxSize().padding(32.dp).rotate(dial)) {
            val letter = Modifier.padding(12.dp)
            Text("N", Modifier.align(Alignment.TopCenter).then(letter), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("E", Modifier.align(Alignment.CenterEnd).then(letter), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("S", Modifier.align(Alignment.BottomCenter).then(letter), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("W", Modifier.align(Alignment.CenterStart).then(letter), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Image(painterResource(R.drawable.arrow4gold), null, Modifier.fillMaxSize().padding(18.dp))
        }
        // Needle: the Kaaba arrow at the top, turned towards Makkah.
        if (heading != null) {
            Box(Modifier.fillMaxSize().padding(48.dp).rotate(needle)) {
                Image(
                    painterResource(R.drawable.kaba_arrow_gold), "Qibla direction",
                    Modifier.align(Alignment.TopCenter).padding(16.dp).fillMaxWidth().aspectRatio(2f / 1.42f),
                )
            }
        }
    }
}

/** Keeps an angle continuous across 0/360 so the animation takes the short way round. */
private class ContinuousAngle {
    private var last: Float? = null

    fun next(target: Float): Float {
        val prev = last ?: target
        var delta = (target - prev) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return (prev + delta).also { last = it }
    }
}

/** Initial great-circle bearing from the location to the Kaaba, degrees from true north. */
private fun qiblaBearing(lat: Double, lng: Double): Double {
    val phi1 = Math.toRadians(lat)
    val phi2 = Math.toRadians(KAABA_LAT)
    val dLambda = Math.toRadians(KAABA_LNG - lng)
    val y = sin(dLambda) * cos(phi2)
    val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
    return (Math.toDegrees(atan2(y, x)) + 360) % 360
}

@Composable
private fun CalibrationIntro(onOk: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Color(0xF2202020)).verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CalibrationGif()
        Text("Move the device in an 8-shape to calibrate the compass", color = Color.White, fontSize = 18.sp, textAlign = TextAlign.Center)
        Text("حرك الجهاز على شكل 8 لمعايرة البوصلة", color = Color.White, fontSize = 18.sp, textAlign = TextAlign.Center)
        Text(
            "Qibla Direction uses your device's compass. For an accurate result calibrate the compass first, and keep the " +
                "phone away from electronic devices or magnetic fields.",
            color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center,
        )
        Text(
            "يستخدم اتجاه القبلة بوصلة جهازك، وللحصول على نتيجة دقيقة ننصحك بمعايرة البوصلة قبل استخدام اتجاه القبلة. واحرص على أن يكون جهازك بعيداً عن أي أجهزة الكترونية أو مجال مغناطيسي",
            color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center,
        )
        Button(onClick = onOk, modifier = Modifier.fillMaxWidth()) { Text("OK") }
    }
}

@Composable
private fun CalibrationGif() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
    AndroidView(
        factory = { ctx ->
            ImageView(ctx).apply {
                val drawable = runCatching { ImageDecoder.decodeDrawable(ImageDecoder.createSource(ctx.resources, R.raw.compass_calibration)) }.getOrNull()
                setImageDrawable(drawable)
                (drawable as? AnimatedImageDrawable)?.start()
            }
        },
        modifier = Modifier.size(220.dp),
    )
}

