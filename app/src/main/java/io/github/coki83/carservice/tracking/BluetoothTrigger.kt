package io.github.coki83.carservice.tracking

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.IntentCompat
import io.github.coki83.carservice.data.Store

/**
 * Wakes the app when the phone connects to (or disconnects from) the car's Bluetooth or the OBD adapter.
 * Android Auto, wired or wireless, also brings up the car's Bluetooth link, so this covers it too.
 */
class BluetoothTrigger : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
        val settings = Store.get(context).state.value.settings
        val watched = device.address in settings.carBluetooth || device.address == settings.obdAddress
        if (!watched) return
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> AutoStart.start(context, AutoReason.CAR)
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> AutoStart.stop(context, AutoReason.CAR)
        }
    }
}
