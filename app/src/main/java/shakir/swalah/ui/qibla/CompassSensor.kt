package shakir.swalah.ui.qibla

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Device heading in degrees from magnetic north (0..360), or null when the phone has no compass.
 * Uses the fused rotation-vector sensor when available, else accelerometer + magnetometer.
 */
@Composable
fun rememberCompassHeading(): State<Float?> {
    val context = LocalContext.current
    val heading = remember { mutableStateOf<Float?>(null) }
    DisposableEffect(Unit) {
        val sm = context.getSystemService(SensorManager::class.java)
        @Suppress("DEPRECATION")
        val display = context.getSystemService(WindowManager::class.java).defaultDisplay
        val rotationVector = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnet = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val raw = FloatArray(9)
        val remapped = FloatArray(9)
        val orientation = FloatArray(3)
        var gravity: FloatArray? = null
        var geomagnetic: FloatArray? = null
        var smoothed: Float? = null

        fun publish(matrix: FloatArray) {
            val (x, y) = when (display.rotation) {
                Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
            }
            SensorManager.remapCoordinateSystem(matrix, x, y, remapped)
            SensorManager.getOrientation(remapped, orientation)
            val deg = ((Math.toDegrees(orientation[0].toDouble()).toFloat()) + 360f) % 360f
            // Low-pass on the circle so the needle doesn't jitter.
            val prev = smoothed
            smoothed = if (prev == null) deg else {
                var delta = deg - prev
                if (delta > 180) delta -= 360
                if (delta < -180) delta += 360
                (prev + delta * 0.15f + 360f) % 360f
            }
            heading.value = smoothed
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(raw, event.values)
                        publish(raw)
                    }
                    Sensor.TYPE_ACCELEROMETER -> gravity = event.values.clone()
                    Sensor.TYPE_MAGNETIC_FIELD -> geomagnetic = event.values.clone()
                }
                val g = gravity
                val m = geomagnetic
                if (rotationVector == null && g != null && m != null && SensorManager.getRotationMatrix(raw, null, g, m)) publish(raw)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (rotationVector != null) {
            sm.registerListener(listener, rotationVector, SensorManager.SENSOR_DELAY_UI)
        } else if (accel != null && magnet != null) {
            sm.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
            sm.registerListener(listener, magnet, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { sm.unregisterListener(listener) }
    }
    return heading
}

fun hasCompass(context: Context): Boolean {
    val sm = context.getSystemService(SensorManager::class.java)
    return sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null || sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null
}
