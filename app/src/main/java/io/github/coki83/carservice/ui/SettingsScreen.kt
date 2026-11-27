package io.github.coki83.carservice.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.coki83.carservice.data.*
import io.github.coki83.carservice.tracking.DrivingDetector

fun requiredPermissions(s: Settings): List<String> {
    val list = mutableListOf<String>()
    if (s.trackingMode == TrackingMode.GPS || s.trackingMode == TrackingMode.AUTO) {
        list += Manifest.permission.ACCESS_FINE_LOCATION
        list += Manifest.permission.ACCESS_COARSE_LOCATION
    }
    if (Build.VERSION.SDK_INT >= 31 && (s.obdAddress != null || s.carBluetooth.isNotEmpty()) && s.trackingMode != TrackingMode.GPS) {
        list += Manifest.permission.BLUETOOTH_CONNECT
    }
    if (Build.VERSION.SDK_INT >= 33) list += Manifest.permission.POST_NOTIFICATIONS
    return list
}

private fun granted(ctx: Context, p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

@Composable
fun SettingsScreen(state: AppState, store: Store, contentPadding: PaddingValues) {
    val ctx = LocalContext.current
    val s = state.settings
    var name by remember { mutableStateOf(state.vehicle.name) }
    var warnKm by remember { mutableStateOf(s.warnKm.toString()) }
    var warnDays by remember { mutableStateOf(s.warnDays.toString()) }
    var pickObd by remember { mutableStateOf(false) }
    var pickCarBt by remember { mutableStateOf(false) }
    var changeCar by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) } // re-read permission states when coming back from system screens
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }

    fun updateSettings(f: (Settings) -> Settings) {
        store.update { it.copy(settings = f(it.settings)) }
        DrivingDetector.sync(ctx)
    }

    var pendingBt by remember { mutableStateOf<String?>(null) }
    val btPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { if (pendingBt == "obd") pickObd = true else pickCarBt = true }
    }
    fun withBt(target: String) {
        if (Build.VERSION.SDK_INT >= 31 && !granted(ctx, Manifest.permission.BLUETOOTH_CONNECT)) {
            pendingBt = target; btPerm.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else if (target == "obd") pickObd = true else pickCarBt = true
    }
    val activityPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        updateSettings { it.copy(detectDriving = ok) }
    }
    val fgLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }
    val bgLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text("Settings", style = MaterialTheme.typography.headlineMedium) }

        item {
            Section("Car") {
                val v = state.vehicle
                SettingRow(
                    Icons.Default.DirectionsCar, "Car",
                    listOf(v.make, v.model, v.year?.toString().orEmpty()).filter { it.isNotEmpty() }.joinToString(" ").ifEmpty { "Not set" } +
                        " · " + v.fuel.label + " · ${state.items.size} service items",
                ) { changeCar = true }
                OutlinedTextField(
                    name, { name = it; store.update { st -> st.copy(vehicle = st.vehicle.copy(name = it.ifBlank { "My car" })) } },
                    label = { Text("Name shown in the app") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        item {
            Section("Automatic trips") {
                SwitchRow(
                    Icons.Default.AutoMode, "Start trips by themselves",
                    "Counts kilometres without pressing Start. Stops when you park.",
                    s.autoStart,
                ) { on -> updateSettings { it.copy(autoStart = on) } }
                if (s.autoStart) {
                    SettingRow(
                        Icons.Default.Bluetooth, "When the phone connects to",
                        s.carBluetooth.values.joinToString(", ").ifEmpty { "Choose your car's Bluetooth (also covers Android Auto)" },
                    ) { withBt("car") }
                    SwitchRow(
                        Icons.Default.Sensors, "Detect driving",
                        "Uses the phone's motion sensors, also in cars without Bluetooth.",
                        s.detectDriving,
                    ) { on ->
                        val p = DrivingDetector.permission()
                        if (on && p != null && !granted(ctx, p)) activityPerm.launch(p) else updateSettings { it.copy(detectDriving = on) }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    Text(
                        "For trips to start while the app is closed, Android needs these:",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    key(tick) {
                        val fine = granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
                        val bg = Build.VERSION.SDK_INT < 29 || granted(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        PermissionRow(Icons.Default.LocationOn, "Location: Allow all the time", fine && bg) {
                            if (!fine) fgLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                            else if (Build.VERSION.SDK_INT >= 29) bgLocation.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }
                        val pm = ctx.getSystemService(PowerManager::class.java)
                        val battery = pm.isIgnoringBatteryOptimizations(ctx.packageName)
                        PermissionRow(Icons.Default.BatteryChargingFull, "Battery: Unrestricted", battery) {
                            @SuppressLint("BatteryLife")
                            val i = Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.packageName))
                            runCatching { ctx.startActivity(i) }
                        }
                        if (Build.VERSION.SDK_INT >= 31 && s.carBluetooth.isNotEmpty()) {
                            PermissionRow(Icons.Default.Bluetooth, "Nearby devices (Bluetooth)", granted(ctx, Manifest.permission.BLUETOOTH_CONNECT)) {
                                btPerm.launch(Manifest.permission.BLUETOOTH_CONNECT)
                            }
                        }
                    }
                }
            }
        }

        item {
            Section("Kilometre source") {
                val modes = listOf(
                    TrackingMode.AUTO to "OBD-II adapter when connected, otherwise GPS",
                    TrackingMode.OBD to "OBD-II adapter only",
                    TrackingMode.GPS to "GPS only",
                    TrackingMode.MANUAL to "Manual entry only",
                )
                modes.forEach { (mode, label) ->
                    Row(
                        Modifier.fillMaxWidth().selectable(s.trackingMode == mode) { updateSettings { it.copy(trackingMode = mode) } }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = s.trackingMode == mode, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                SettingRow(
                    Icons.Default.SettingsInputComponent, "OBD-II adapter",
                    s.obdName ?: "None. Pair your ELM327 in Android Bluetooth settings first.",
                ) { withBt("obd") }
            }
        }

        item {
            Section("Reminders") {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        warnKm, { v -> warnKm = v.filter(Char::isDigit); warnKm.toIntOrNull()?.let { n -> updateSettings { it.copy(warnKm = n) } } },
                        label = { Text("Warn km before") }, singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        warnDays, { v -> warnDays = v.filter(Char::isDigit); warnDays.toIntOrNull()?.let { n -> updateSettings { it.copy(warnDays = n) } } },
                        label = { Text("Warn days before") }, singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }
        }

        item {
            Text(
                "All data stays on this phone. No account and no internet needed.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }

    if (changeCar) CarSetupDialog(state, store, firstRun = false, onDismiss = { changeCar = false; name = store.state.value.vehicle.name })
    if (pickObd) ObdPickerDialog(
        onDismiss = { pickObd = false },
        onPick = { addr, n -> updateSettings { it.copy(obdAddress = addr, obdName = n) }; pickObd = false },
    )
    if (pickCarBt) CarBluetoothDialog(
        selected = s.carBluetooth,
        onDismiss = { pickCarBt = false },
        onSave = { chosen -> updateSettings { it.copy(carBluetooth = chosen) }; pickCarBt = false },
    )
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(vertical = 6.dp), content = content)
        }
    }
}

@Composable
private fun RowIcon(icon: ImageVector) {
    Box(
        Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        leadingContent = { RowIcon(icon) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Icon(Icons.Default.ChevronRight, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        leadingContent = { RowIcon(icon) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onChange(!checked) },
    )
}

@Composable
private fun PermissionRow(icon: ImageVector, title: String, ok: Boolean, onFix: () -> Unit) {
    val okColor = statusColor(DueStatus.OK)
    val warn = statusColor(DueStatus.SOON)
    ListItem(
        leadingContent = { Icon(icon, null, tint = if (ok) okColor else warn) },
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyMedium) },
        trailingContent = {
            if (ok) Icon(Icons.Default.CheckCircle, "Allowed", tint = okColor)
            else TextButton(onClick = onFix) { Text("Allow") }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = if (ok) Modifier else Modifier.clickable(onClick = onFix),
    )
}

/** Pick one or more paired Bluetooth devices (the car's head unit) whose connection starts a trip. */
@SuppressLint("MissingPermission")
@Composable
private fun CarBluetoothDialog(selected: Map<String, String>, onDismiss: () -> Unit, onSave: (Map<String, String>) -> Unit) {
    val ctx = LocalContext.current
    val devices = remember {
        runCatching {
            ctx.getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices?.toList().orEmpty()
                .map { it.address to (it.name ?: it.address) }.sortedBy { it.second.lowercase() }
        }.getOrDefault(emptyList())
    }
    var chosen by remember { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your car's Bluetooth") },
        text = {
            if (devices.isEmpty()) Text("No paired Bluetooth devices. Connect the phone to your car once, then come back.")
            else Column {
                Text("A trip starts when the phone connects to any of these, and stops when it disconnects.",
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(devices) { (addr, n) ->
                        val on = addr in chosen
                        Row(
                            Modifier.fillMaxWidth().clickable { chosen = if (on) chosen - addr else chosen + (addr to n) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = on, onCheckedChange = null)
                            Spacer(Modifier.width(10.dp))
                            Text(n)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(chosen) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
