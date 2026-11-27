package io.github.coki83.carservice.car

import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import io.github.coki83.carservice.tracking.AutoReason
import io.github.coki83.carservice.tracking.AutoStart

/** Entry point for Android Auto (and Android Automotive hosts). */
class ServiceCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator =
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        else HostValidator.Builder(applicationContext)
            .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
            .build()

    override fun onCreateSession(): Session = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen {
            // Opening the app in the car starts a trip automatically, if automatic trips are on.
            AutoStart.start(carContext, AutoReason.ANDROID_AUTO)
            return DueScreen(carContext)
        }
    }
}
