package io.github.coki83.carservice.notify

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.coki83.carservice.MainActivity
import io.github.coki83.carservice.R
import io.github.coki83.carservice.data.DueCalculator
import io.github.coki83.carservice.data.DueStatus
import io.github.coki83.carservice.data.Store
import java.util.concurrent.TimeUnit

/**
 * Runs once a day (and after each trip). Posts one reminder per item that is due soon or overdue,
 * at most once a week per item unless its status got worse.
 */
class DueCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        check(applicationContext)
        return Result.success()
    }

    companion object {
        private const val PERIODIC = "due-check"
        private const val WEEK_MS = 7L * 24 * 3600 * 1000

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<DueCheckWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<DueCheckWorker>().build())
        }

        fun check(context: Context) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            Notifications.ensureChannels(context)
            val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
            val state = Store.get(context).state.value
            val now = System.currentTimeMillis()
            val nm = NotificationManagerCompat.from(context)
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

            for (d in DueCalculator.all(state)) {
                val key = d.item.id
                if (d.status == DueStatus.OK) {
                    prefs.edit().remove("$key.status").remove("$key.time").apply()
                    nm.cancel(key.hashCode())
                    continue
                }
                val lastStatus = prefs.getString("$key.status", null)
                val lastTime = prefs.getLong("$key.time", 0L)
                val worse = lastStatus != d.status.name
                if (!worse && now - lastTime < WEEK_MS) continue

                val title = if (d.status == DueStatus.OVERDUE) "${d.item.name} is overdue" else "${d.item.name} due soon"
                val n = NotificationCompat.Builder(context, Notifications.CHANNEL_DUE)
                    .setSmallIcon(R.drawable.ic_car)
                    .setContentTitle(title)
                    .setContentText(DueCalculator.describe(d))
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build()
                try { nm.notify(key.hashCode(), n) } catch (_: SecurityException) { }
                prefs.edit().putString("$key.status", d.status.name).putLong("$key.time", now).apply()
            }
        }
    }
}
