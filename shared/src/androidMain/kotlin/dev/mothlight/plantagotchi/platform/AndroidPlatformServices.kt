package dev.mothlight.plantagotchi.platform

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dev.mothlight.plantagotchi.data.GeoPosition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

fun androidPlatformServices(context: Context, widget: WidgetBridge = NoWidget) = PlatformServices(
    lightSensor = AndroidLightSensor(context.applicationContext),
    locationProvider = AndroidLocationProvider(context.applicationContext),
    notifications = AndroidNotificationScheduler(context.applicationContext),
    sound = AndroidSoundPlayer(context.applicationContext),
    widget = widget,
)

class AndroidLightSensor(context: Context) : LightSensor, SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
    private val _lux = MutableStateFlow<Float?>(null)
    override val lux: StateFlow<Float?> = _lux.asStateFlow()

    override fun start() {
        sensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    override fun stop() = sensorManager.unregisterListener(this)

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_LIGHT) _lux.value = event.values[0]
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
}

class AndroidLocationProvider(private val context: Context) : LocationProvider {
    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    @SuppressLint("MissingPermission")
    override suspend fun currentPosition(): GeoPosition? {
        if (!hasPermission()) return null
        val location = client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
            ?: client.lastLocation.await()
            ?: return null
        return GeoPosition(location.latitude, location.longitude, location.altitude)
    }

    private fun hasPermission() = listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ).any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
}
