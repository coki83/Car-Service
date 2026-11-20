package io.github.coki83.carservice.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object Notifications {
    const val CHANNEL_TRACKING = "tracking"
    const val CHANNEL_DUE = "due"

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_TRACKING, "Kilometre tracking", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_DUE, "Service reminders", NotificationManager.IMPORTANCE_DEFAULT))
    }
}
