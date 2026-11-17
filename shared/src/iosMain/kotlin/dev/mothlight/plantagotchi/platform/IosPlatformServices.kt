package dev.mothlight.plantagotchi.platform

import dev.mothlight.plantagotchi.data.GeoPosition
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLLocationAccuracyKilometer
import platform.Foundation.NSError
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

fun iosPlatformServices() = PlatformServices(
    lightSensor = IosCameraLightSensor(),
    locationProvider = IosLocationProvider(),
    notifications = IosNotificationScheduler(),
    sound = IosSoundPlayer(),
    widget = IosWidgetBridge(),
)

@OptIn(ExperimentalForeignApi::class)
class IosLocationProvider : LocationProvider {
    private val delegate = LocationDelegate()
    private val manager = CLLocationManager().apply {
        delegate = this@IosLocationProvider.delegate
        desiredAccuracy = kCLLocationAccuracyKilometer
    }

    override suspend fun currentPosition(): GeoPosition? {
        when (manager.authorizationStatus) {
            kCLAuthorizationStatusNotDetermined -> {
                manager.requestWhenInUseAuthorization()
                return null
            }
            kCLAuthorizationStatusAuthorizedWhenInUse, kCLAuthorizationStatusAuthorizedAlways -> Unit
            else -> return null
        }
        val location = withTimeoutOrNull(10.seconds) {
            suspendCancellableCoroutine { cont ->
                delegate.pending += cont
                manager.requestLocation()
            }
        } ?: manager.location
        return location?.toGeoPosition()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun CLLocation.toGeoPosition(): GeoPosition = coordinate.useContents {
    GeoPosition(latitude = latitude, longitude = longitude, altitude = altitude)
}

private class LocationDelegate : NSObject(), CLLocationManagerDelegateProtocol {
    val pending = mutableListOf<CancellableContinuation<CLLocation?>>()

    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        complete(didUpdateLocations.lastOrNull() as? CLLocation)
    }

    override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
        complete(null)
    }

    private fun complete(location: CLLocation?) {
        val waiting = pending.toList()
        pending.clear()
        waiting.forEach { if (it.isActive) it.resume(location) }
    }
}
