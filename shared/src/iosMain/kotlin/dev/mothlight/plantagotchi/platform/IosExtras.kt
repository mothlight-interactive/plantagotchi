package dev.mothlight.plantagotchi.platform

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.AVFAudio.setActive
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetLow
import platform.AVFoundation.AVCaptureVideoDataOutput
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.ISO
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.exposureDuration
import platform.AVFoundation.lensAperture
import platform.AVFoundation.requestAccessForMediaType
import platform.Foundation.NSData
import platform.Foundation.NSUserDefaults
import platform.Foundation.dataWithBytes
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import kotlin.coroutines.resume
import kotlin.math.log2
import kotlin.math.pow

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosSoundPlayer : SoundPlayer {
    private val players = mutableMapOf<String, AVAudioPlayer>()
    /** Loop volumes requested before the files were loaded. */
    private val pending = mutableMapOf<String, Float>()

    override suspend fun load(sounds: Map<String, ByteArray>) {
        runCatching {
            AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryAmbient, null)
            AVAudioSession.sharedInstance().setActive(true, null)
        }
        sounds.forEach { (asset, bytes) ->
            val data = bytes.usePinned { NSData.dataWithBytes(it.addressOf(0), bytes.size.toULong()) }
            AVAudioPlayer(data = data, error = null).let { player ->
                if (asset.startsWith("music/") || asset.startsWith("ambient/")) player.numberOfLoops = -1
                player.prepareToPlay()
                players[asset] = player
            }
        }
        pending.toMap().forEach { (asset, volume) -> setLoop(asset, volume) }
        pending.clear()
    }

    override fun play(asset: String, volume: Float) {
        players[asset]?.let {
            it.volume = volume
            it.currentTime = 0.0
            it.play()
        }
    }

    override fun setLoop(asset: String, volume: Float) {
        val player = players[asset]
        if (player == null) {
            pending[asset] = volume
            return
        }
        player.volume = volume
        if (volume <= 0.001f) {
            if (player.playing) player.pause()
        } else if (!player.playing) {
            player.play()
        }
    }

    override fun pauseAll() {
        players.forEach { (asset, player) ->
            if ((asset.startsWith("music/") || asset.startsWith("ambient/")) && player.playing) player.pause()
        }
    }
}

class IosNotificationScheduler : NotificationScheduler {
    private val center get() = UNUserNotificationCenter.currentNotificationCenter()

    override suspend fun requestPermission(): Boolean = suspendCancellableCoroutine { cont ->
        center.requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
        ) { granted, _ -> if (cont.isActive) cont.resume(granted) }
    }

    override fun schedule(reminders: List<Reminder>) {
        center.removeAllPendingNotificationRequests()
        reminders.forEach { r ->
            val content = UNMutableNotificationContent().apply {
                setTitle(r.title)
                setBody(r.body)
                setSound(UNNotificationSound.defaultSound)
            }
            val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(r.delaySeconds.toDouble(), repeats = false)
            center.addNotificationRequest(
                UNNotificationRequest.requestWithIdentifier("plantagotchi-${r.id}", content, trigger),
                withCompletionHandler = null,
            )
        }
    }

    override fun cancelAll() {
        center.removeAllPendingNotificationRequests()
        center.removeAllDeliveredNotifications()
    }
}

/** Shares the plant state with the WidgetKit extension through the app group. */
class IosWidgetBridge(private val suiteName: String = APP_GROUP) : WidgetBridge {
    override fun update(snapshot: WidgetSnapshot) {
        val defaults = NSUserDefaults(suiteName = suiteName)
        defaults.setObject(snapshot.toJson(), forKey = "widget_snapshot")
    }

    companion object {
        const val APP_GROUP = "group.dev.mothlight.plantagotchi"
    }
}

/**
 * iOS has no public ambient light sensor API. If the player opts in, the back camera's auto
 * exposure is used as a light meter: lux ≈ 2.5 · 2^EV100. Otherwise [lux] stays null and the game
 * estimates light from daylight and weather.
 */
@OptIn(ExperimentalForeignApi::class)
class IosCameraLightSensor : LightSensor {
    private val _lux = MutableStateFlow<Float?>(null)
    override val lux: StateFlow<Float?> = _lux.asStateFlow()
    override val supportsCamera: Boolean = true

    private var enabled = false
    private var started = false
    private var session: AVCaptureSession? = null
    private var device: AVCaptureDevice? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var poll: Job? = null

    override fun setCameraEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (enabled && started) startCamera() else stopCamera()
    }

    override fun start() {
        started = true
        if (enabled) startCamera()
    }

    override fun stop() {
        started = false
        stopCamera()
    }

    private fun startCamera() {
        if (session != null) return
        when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
            AVAuthorizationStatusAuthorized -> Unit
            AVAuthorizationStatusNotDetermined -> {
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                    if (granted) scope.launch { if (enabled && started) startCamera() }
                }
                return
            }
            else -> return
        }
        val camera = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) ?: return
        val input = AVCaptureDeviceInput.deviceInputWithDevice(camera, null) ?: return
        val capture = AVCaptureSession().apply {
            sessionPreset = AVCaptureSessionPresetLow
            if (canAddInput(input)) addInput(input)
            val output = AVCaptureVideoDataOutput()
            if (canAddOutput(output)) addOutput(output)
        }
        session = capture
        device = camera
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) { capture.startRunning() }
        poll = scope.launch {
            while (isActive) {
                delay(1_000)
                _lux.value = measure(camera)
            }
        }
    }

    private fun stopCamera() {
        poll?.cancel()
        poll = null
        session?.let { s -> dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) { s.stopRunning() } }
        session = null
        device = null
        _lux.value = null
    }

    private fun measure(camera: AVCaptureDevice): Float? {
        val seconds = camera.exposureDuration.useContents { if (timescale == 0) 0.0 else value.toDouble() / timescale }
        val iso = camera.ISO.toDouble()
        val aperture = camera.lensAperture.toDouble()
        if (seconds <= 0.0 || iso <= 0.0 || aperture <= 0.0) return null
        val ev100 = log2(aperture * aperture / seconds) - log2(iso / 100.0)
        return (2.5 * 2.0.pow(ev100)).toFloat()
    }
}
