package io.github.coki83.carservice.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import io.github.coki83.carservice.data.Store

/**
 * Uses the phone's motion sensors (Google Play services activity recognition, works offline)
 * to start a trip when you begin driving and stop it when you get out of the car.
 */
object DrivingDetector {

    fun permission(): String? = when {
        Build.VERSION.SDK_INT >= 29 -> Manifest.permission.ACTIVITY_RECOGNITION
        else -> null // granted at install time on older Android
    }

    fun hasPermission(context: Context): Boolean =
        permission()?.let { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED } ?: true

    /** Register or unregister according to the current settings. Safe to call often. */
    @SuppressLint("MissingPermission")
    fun sync(context: Context) {
        val s = Store.get(context).state.value.settings
        val client = runCatching { ActivityRecognition.getClient(context) }.getOrNull() ?: return
        val pi = pendingIntent(context)
        if (s.autoStart && s.detectDriving && hasPermission(context)) {
            val transitions = listOf(ActivityTransition.ACTIVITY_TRANSITION_ENTER, ActivityTransition.ACTIVITY_TRANSITION_EXIT).map {
                ActivityTransition.Builder().setActivityType(DetectedActivity.IN_VEHICLE).setActivityTransition(it).build()
            }
            runCatching { client.requestActivityTransitionUpdates(ActivityTransitionRequest(transitions), pi) }
        } else {
            runCatching { client.removeActivityTransitionUpdates(pi) }
        }
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        return PendingIntent.getBroadcast(context, 5, Intent(context, Receiver::class.java), flags)
    }

    class Receiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!ActivityTransitionResult.hasResult(intent)) return
            val last = ActivityTransitionResult.extractResult(intent)?.transitionEvents?.lastOrNull() ?: return
            if (last.activityType != DetectedActivity.IN_VEHICLE) return
            when (last.transitionType) {
                ActivityTransition.ACTIVITY_TRANSITION_ENTER -> AutoStart.start(context, AutoReason.DRIVING)
                ActivityTransition.ACTIVITY_TRANSITION_EXIT -> AutoStart.stop(context, AutoReason.DRIVING)
            }
        }
    }
}

/** Activity recognition registrations are lost on reboot and app updates; restore them. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        DrivingDetector.sync(context)
    }
}
