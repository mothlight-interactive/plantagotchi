package dev.mothlight.plantagotchi.platform

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.mothlight.plantagotchi.shared.R
import java.io.File
import java.util.concurrent.TimeUnit

class AndroidSoundPlayer(private val context: Context) : SoundPlayer {
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private val pool = SoundPool.Builder().setMaxStreams(8).setAudioAttributes(attributes).build()
    private val ids = mutableMapOf<String, Int>()
    private val loopFiles = mutableMapOf<String, File>()
    private val loops = mutableMapOf<String, MediaPlayer>()
    /** Loop volumes requested before the files were loaded. */
    private val pending = mutableMapOf<String, Float>()

    override suspend fun load(sounds: Map<String, ByteArray>) {
        val dir = File(context.cacheDir, "audio").apply { mkdirs() }
        sounds.forEach { (asset, bytes) ->
            val file = File(dir, asset.replace('/', '_') + ".wav").apply { writeBytes(bytes) }
            if (asset.isLoop()) loopFiles[asset] = file else ids[asset] = pool.load(file.absolutePath, 1)
        }
        pending.toMap().forEach { (asset, volume) -> setLoop(asset, volume) }
        pending.clear()
    }

    override fun play(asset: String, volume: Float) {
        ids[asset]?.let { pool.play(it, volume, volume, 1, 0, 1f) }
    }

    override fun setLoop(asset: String, volume: Float) {
        val file = loopFiles[asset]
        if (file == null) {
            pending[asset] = volume
            return
        }
        val player = loops.getOrPut(asset) {
            MediaPlayer().apply {
                setAudioAttributes(attributes)
                setDataSource(file.absolutePath)
                isLooping = true
                prepare()
            }
        }
        if (volume <= 0.001f) {
            if (player.isPlaying) player.pause()
        } else {
            player.setVolume(volume, volume)
            if (!player.isPlaying) player.start()
        }
    }

    override fun pauseAll() {
        loops.values.forEach { if (it.isPlaying) it.pause() }
    }

    private fun String.isLoop() = startsWith("music/") || startsWith("ambient/")
}

/** Schedules reminders with WorkManager so they survive the app being killed. */
class AndroidNotificationScheduler(private val context: Context) : NotificationScheduler {

    override suspend fun requestPermission(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    override fun schedule(reminders: List<Reminder>) {
        val work = WorkManager.getInstance(context)
        work.cancelAllWorkByTag(TAG)
        reminders.forEach { r ->
            work.enqueue(
                OneTimeWorkRequestBuilder<ReminderWorker>()
                    .setInitialDelay(r.delaySeconds, TimeUnit.SECONDS)
                    .setInputData(workDataOf("id" to r.id, "title" to r.title, "body" to r.body))
                    .addTag(TAG)
                    .build()
            )
        }
    }

    override fun cancelAll() {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
        NotificationManagerCompat.from(context).cancelAll()
    }

    companion object {
        const val TAG = "plantagotchi-reminder"
        const val CHANNEL = "reminders"
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }
        val manager = ctx.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(AndroidNotificationScheduler.CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(AndroidNotificationScheduler.CHANNEL, "Reminders", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
        val pending = launch?.let { PendingIntent.getActivity(ctx, 0, it, PendingIntent.FLAG_IMMUTABLE) }
        val notification = NotificationCompat.Builder(ctx, AndroidNotificationScheduler.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_plant)
            .setContentTitle(inputData.getString("title"))
            .setContentText(inputData.getString("body"))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        manager.notify(inputData.getInt("id", 0), notification)
        return Result.success()
    }
}
