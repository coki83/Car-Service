package io.github.coki83.carservice

import android.app.Application
import io.github.coki83.carservice.notify.DueCheckWorker
import io.github.coki83.carservice.notify.Notifications

class CarServiceApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        DueCheckWorker.schedule(this)
    }
}
