package io.github.coki83.carservice.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.coki83.carservice.MainActivity
import io.github.coki83.carservice.R
import io.github.coki83.carservice.data.DueCalculator
import io.github.coki83.carservice.data.Store
import io.github.coki83.carservice.data.TrackingMode
import io.github.coki83.carservice.notify.Notifications
import io.github.coki83.carservice.notify.DueCheckWorker
import io.github.coki83.carservice.obd.Elm327
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground service that adds driven distance to the odometer while a trip is running.
 * Source priority in AUTO mode: OBD-II adapter (if one is chosen and reachable), else GPS.
 */
class TrackingService : Service() {

    enum class Source { NONE, OBD, GPS }

    data class Status(
        val running: Boolean = false,
        val source: Source = Source.NONE,
        val tripKm: Double = 0.0,
        val message: String = "",
        /** Set when the trip started by itself; null when the user pressed Start. */
        val autoReason: AutoReason? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private lateinit var store: Store
    private var gpsListener: LocationListener? = null
    private var elm: Elm327? = null

    /** Distance not yet written to the store. */
    private var pendingKm = 0.0
    private var tripKm = 0.0
    private var autoReason: AutoReason? = null
    /** Last time the car was seen moving; used to end automatic trips after parking. */
    @Volatile private var lastMove = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        store = Store.get(this)
        Notifications.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTracking()
            return START_NOT_STICKY
        }
        if (!startInForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (job == null) {
            tripKm = 0.0
            lastMove = System.currentTimeMillis()
            autoReason = intent?.getStringExtra(EXTRA_REASON)?.let { r -> runCatching { AutoReason.valueOf(r) }.getOrNull() }
            job = scope.launch { run() }
            scope.launch { parkedWatchdog() }
        } else if (intent?.getStringExtra(EXTRA_REASON) == null) {
            // The user pressed Start during an automatic trip: keep it running until they stop it.
            autoReason = null
        }
        return START_STICKY
    }

    private fun startInForeground(): Boolean {
        var types = 0
        if (hasPerm(Manifest.permission.ACCESS_FINE_LOCATION)) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        if (Build.VERSION.SDK_INT < 31 || hasPerm(Manifest.permission.BLUETOOTH_CONNECT)) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        }
        if (types == 0) {
            _status.value = Status(message = "Location or Bluetooth permission needed")
            return false
        }
        return try {
            ServiceCompat.startForeground(this, NOTIF_ID, buildNotification("Starting…"), types)
            true
        } catch (e: Exception) {
            // Started from the background without "Allow all the time" location: Android refuses the
            // location type. Fall back to OBD-only so a connected adapter still counts kilometres.
            val btOnly = types and ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION.inv()
            if (btOnly != 0 && btOnly != types) {
                try {
                    ServiceCompat.startForeground(this, NOTIF_ID, buildNotification("Starting…"), btOnly)
                    return true
                } catch (_: Exception) { }
            }
            _status.value = Status(message = "Could not start tracking: ${e.message}")
            false
        }
    }

    private suspend fun run() {
        _status.value = Status(running = true, message = "Starting…", autoReason = autoReason)
        while (scope.isActive) {
            val settings = store.state.value.settings
            val wantObd = settings.trackingMode == TrackingMode.OBD ||
                (settings.trackingMode == TrackingMode.AUTO && settings.obdAddress != null)
            val wantGps = settings.trackingMode == TrackingMode.GPS || settings.trackingMode == TrackingMode.AUTO

            if (wantObd && settings.obdAddress != null && runObd(settings.obdAddress)) {
                // OBD session ended (adapter disconnected). Loop around and pick a source again.
                continue
            }
            if (wantGps) {
                runGpsUntilObdAvailable(settings.obdAddress.takeIf { wantObd })
            } else {
                update(Source.NONE, "OBD adapter not reachable, retrying…")
                delay(15_000)
            }
        }
    }

    /** Returns true if it connected at least once (so the caller re-evaluates), false if it never connected. */
    @SuppressLint("MissingPermission")
    private suspend fun runObd(address: String): Boolean = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= 31 && !hasPerm(Manifest.permission.BLUETOOTH_CONNECT)) return@withContext false
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter ?: return@withContext false
        if (!adapter.isEnabled) return@withContext false
        val client = Elm327(address)
        try {
            update(Source.OBD, "Connecting to OBD adapter…")
            client.connect(adapter)
            elm = client
        } catch (e: Exception) {
            client.close()
            return@withContext false
        }

        var odometerSupported = true
        var lastOdoCheck = 0L
        var lastSample = System.currentTimeMillis()
        var failures = 0
        try {
            while (isActive && failures < 5) {
                val now = System.currentTimeMillis()

                if (odometerSupported && now - lastOdoCheck > 30_000) {
                    lastOdoCheck = now
                    val odo = runCatching { client.odometerKm() }.getOrNull()
                    if (odo == null) {
                        odometerSupported = false
                    } else if (odo > 0) {
                        // The car knows its real odometer: trust it over anything we accumulated.
                        flush()
                        val before = store.state.value.vehicle.odometerKm
                        tripKm += (odo - before).coerceAtLeast(0.0)
                        store.setOdometer(odo)
                        update(Source.OBD, "OBD odometer")
                    }
                }

                val speed = try { client.speedKmh() } catch (e: Exception) { null }
                val t = System.currentTimeMillis()
                if (speed == null) {
                    failures++
                } else {
                    failures = 0
                    if (speed > 2) lastMove = System.currentTimeMillis()
                    if (!odometerSupported) {
                        val hours = (t - lastSample) / 3_600_000.0
                        // Ignore gaps (e.g. adapter stalled) so we don't invent distance.
                        if (hours < 10.0 / 3600) addKm(speed * hours)
                    }
                    update(Source.OBD, if (odometerSupported) "OBD odometer · $speed km/h" else "OBD speed · $speed km/h")
                }
                lastSample = t
                delay(1000)
            }
        } finally {
            flush()
            client.close()
            elm = null
        }
        true
    }

    @SuppressLint("MissingPermission")
    private suspend fun runGpsUntilObdAvailable(obdAddress: String?) {
        if (!hasPerm(Manifest.permission.ACCESS_FINE_LOCATION)) {
            update(Source.NONE, "Location permission needed for GPS tracking")
            delay(30_000)
            return
        }
        val lm = getSystemService(LocationManager::class.java)
        var last: Location? = null
        val listener = LocationListener { loc ->
            if (!loc.hasAccuracy() || loc.accuracy > 25f) return@LocationListener
            val prev = last
            if (prev == null) { last = loc; return@LocationListener }
            val meters = prev.distanceTo(loc)
            val seconds = (loc.time - prev.time) / 1000.0
            if (seconds <= 0) return@LocationListener
            val kmh = meters / seconds * 3.6
            // Skip jitter while parked and impossible jumps.
            if (meters < 10f && kmh < 5) return@LocationListener
            if (kmh > 250) { last = loc; return@LocationListener }
            addKm(meters / 1000.0)
            last = loc
            update(Source.GPS, "GPS · ${kmh.toInt()} km/h")
        }
        withContext(Dispatchers.Main) {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 5f, listener, Looper.getMainLooper())
        }
        gpsListener = listener
        update(Source.GPS, "GPS · waiting for fix")
        try {
            // In AUTO mode with an adapter chosen, try OBD again every minute.
            while (scope.isActive) {
                delay(60_000)
                flush()
                if (obdAddress != null && obdReachable()) break
            }
        } finally {
            withContext(Dispatchers.Main) { lm.removeUpdates(listener) }
            gpsListener = null
            flush()
        }
    }

    @SuppressLint("MissingPermission")
    private fun obdReachable(): Boolean {
        if (Build.VERSION.SDK_INT >= 31 && !hasPerm(Manifest.permission.BLUETOOTH_CONNECT)) return false
        return getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
    }

    @Synchronized
    private fun addKm(km: Double) {
        pendingKm += km
        tripKm += km
        lastMove = System.currentTimeMillis()
        if (pendingKm >= 0.1) flush()
    }

    @Synchronized
    private fun flush() {
        if (pendingKm > 0) {
            store.addDistance(pendingKm)
            pendingKm = 0.0
        }
    }

    private fun update(source: Source, message: String) {
        _status.value = Status(true, source, tripKm, message, autoReason)
        val odo = DueCalculator.formatKm(store.state.value.vehicle.odometerKm.toInt())
        val nm = getSystemService(android.app.NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification("$odo km · trip ${"%.1f".format(tripKm)} km · $message"))
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, TrackingService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, Notifications.CHANNEL_TRACKING)
            .setSmallIcon(R.drawable.ic_car)
            .setContentTitle("Tracking kilometres")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .build()
    }

    /** Automatic trips end on their own once the car has been parked for a while. */
    private suspend fun parkedWatchdog() {
        while (scope.isActive) {
            delay(60_000)
            val reason = autoReason ?: continue
            val limit = if (reason == AutoReason.CAR) 30 * 60_000L else 10 * 60_000L
            if (System.currentTimeMillis() - lastMove > limit) {
                withContext(Dispatchers.Main) { stopTracking() }
                return
            }
        }
    }

    private fun stopTracking() {
        job?.cancel()
        job = null
        flush()
        _status.value = Status(running = false, tripKm = tripKm, message = "Stopped")
        DueCheckWorker.runNow(this)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        flush()
        gpsListener?.let { getSystemService(LocationManager::class.java).removeUpdates(it) }
        elm?.close()
        scope.cancel()
        if (_status.value.running) _status.value = _status.value.copy(running = false, message = "Stopped")
        super.onDestroy()
    }

    private fun hasPerm(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val ACTION_STOP = "stop"
        private const val EXTRA_REASON = "reason"
        private const val NOTIF_ID = 1

        private val _status = MutableStateFlow(Status())
        val status: StateFlow<Status> = _status

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, TrackingService::class.java))
        }

        /** Intent for a trip that starts by itself. */
        fun intent(context: Context, reason: AutoReason): Intent =
            Intent(context, TrackingService::class.java).putExtra(EXTRA_REASON, reason.name)

        fun stop(context: Context) {
            context.startService(Intent(context, TrackingService::class.java).setAction(ACTION_STOP))
        }
    }
}
