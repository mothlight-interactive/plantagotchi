package dev.mothlight.plantagotchi.platform

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
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
    private val pool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val ids = mutableMapOf<Sfx, Int>()

    override suspend fun load(sounds: Map<Sfx, ByteArray>) {
        val dir = File(context.cacheDir, "sfx").apply { mkdirs() }
        sounds.forEach { (sfx, bytes) ->
            val file = File(dir, "${sfx.file}.wav").apply { writeBytes(bytes) }
            ids[sfx] = pool.load(file.absolutePath, 1)
        }
    }

    override fun play(sfx: Sfx) {
        ids[sfx]?.let { pool.play(it, 0.8f, 0.8f, 0, 0, 1f) }
    }
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
