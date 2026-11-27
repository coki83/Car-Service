package io.github.coki83.carservice.tracking

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import io.github.coki83.carservice.R
import io.github.coki83.carservice.data.Store
import io.github.coki83.carservice.data.TrackingMode
import io.github.coki83.carservice.notify.Notifications

/** Why a trip was started without the user pressing Start. */
enum class AutoReason(val label: String) {
    CAR("Connected to your car"),
    ANDROID_AUTO("Android Auto connected"),
    DRIVING("Driving detected"),
}

/**
 * Starts and stops trips by themselves. Android limits starting a foreground service from the
 * background; if it refuses, we post a notification the user can tap to start the trip instead.
 */
object AutoStart {
    private const val PROMPT_ID = 7

    fun enabled(context: Context): Boolean {
        val s = Store.get(context).state.value.settings
        return s.autoStart && s.trackingMode != TrackingMode.MANUAL
    }

    fun start(context: Context, reason: AutoReason) {
        if (!enabled(context)) return
        val status = TrackingService.status.value
        if (status.running) return
        val intent = TrackingService.intent(context, reason)
        try {
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
            cancelPrompt(context)
        } catch (e: Exception) {
            // Typically ForegroundServiceStartNotAllowedException (Android 12+) when battery optimisation is on.
            prompt(context, reason, intent)
        }
    }

    /** Stop a trip that was started automatically for [reason]; trips started by hand keep running. */
    fun stop(context: Context, reason: AutoReason) {
        cancelPrompt(context)
        val status = TrackingService.status.value
        if (status.running && status.autoReason == reason) TrackingService.stop(context)
    }

    private fun prompt(context: Context, reason: AutoReason, intent: Intent) {
        Notifications.ensureChannels(context)
        val pi = if (Build.VERSION.SDK_INT >= 26) {
            PendingIntent.getForegroundService(context, 3, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        } else {
            PendingIntent.getService(context, 3, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val n = NotificationCompat.Builder(context, Notifications.CHANNEL_TRACKING)
            .setSmallIcon(R.drawable.ic_car)
            .setContentTitle("${reason.label}. Track this trip?")
            .setContentText("Tap to count kilometres. Allow \"Unrestricted\" battery use in Car Service settings to skip this step.")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try { context.getSystemService(NotificationManager::class.java).notify(PROMPT_ID, n) } catch (_: SecurityException) { }
    }

    private fun cancelPrompt(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(PROMPT_ID)
    }
}
