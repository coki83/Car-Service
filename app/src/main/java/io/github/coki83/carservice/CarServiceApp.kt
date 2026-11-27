package io.github.coki83.carservice

import android.app.Application
import androidx.car.app.connection.CarConnection
import io.github.coki83.carservice.notify.DueCheckWorker
import io.github.coki83.carservice.notify.Notifications
import io.github.coki83.carservice.tracking.AutoReason
import io.github.coki83.carservice.tracking.AutoStart
import io.github.coki83.carservice.tracking.DrivingDetector

class CarServiceApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        DueCheckWorker.schedule(this)
        DrivingDetector.sync(this)

        // While the app process is alive, react to Android Auto connecting and disconnecting.
        // (When it isn't, the car's Bluetooth connection wakes the app instead.)
        runCatching {
            CarConnection(this).type.observeForever { type ->
                when (type) {
                    CarConnection.CONNECTION_TYPE_PROJECTION -> AutoStart.start(this, AutoReason.ANDROID_AUTO)
                    CarConnection.CONNECTION_TYPE_NOT_CONNECTED -> AutoStart.stop(this, AutoReason.ANDROID_AUTO)
                }
            }
        }
    }
}
