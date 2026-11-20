package io.github.coki83.carservice.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.coki83.carservice.data.*
import io.github.coki83.carservice.tracking.TrackingService
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

private enum class Screen { HOME, SETTINGS, HISTORY }

@Composable
fun App() {
    val ctx = LocalContext.current
    val store = remember { Store.get(ctx) }
    val state by store.state.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf(Screen.HOME) }

    CarServiceTheme {
        BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }
        when (screen) {
            Screen.HOME -> HomeScreen(state, store, onSettings = { screen = Screen.SETTINGS }, onHistory = { screen = Screen.HISTORY })
            Screen.SETTINGS -> SettingsScreen(state, store, onBack = { screen = Screen.HOME })
            Screen.HISTORY -> HistoryScreen(state, onBack = { screen = Screen.HOME })
        }
        if (state.vehicle.odometerUpdated == 0L) CarSetupDialog(state, store, firstRun = true, onDismiss = {})
    }
}

// ---------------- Home ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(state: AppState, store: Store, onSettings: () -> Unit, onHistory: () -> Unit) {
    val due = remember(state) { DueCalculator.all(state) }
    var editOdo by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<DueInfo?>(null) }
    var editing by remember { mutableStateOf<ServiceItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.vehicle.name) },
                actions = {
                    IconButton(onClick = onHistory) { Icon(Icons.Default.History, "History") }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val km = state.vehicle.odometerKm.toInt()
                editing = ServiceItem(store.newId(), "", 15_000, 12, km, LocalDate.now())
            }) { Icon(Icons.Default.Add, "Add service item") }
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { OdometerCard(state, onEdit = { editOdo = true }) }
            item { TrackingCard(state) }
            if (due.isNotEmpty()) item {
                Text("Next service", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            }
            items(due, key = { it.item.id }) { d -> DueRow(d, onClick = { selected = d }) }
        }
    }

    if (editOdo) NumberDialog(
        title = "Odometer (km)",
        initial = state.vehicle.odometerKm.toInt().toString(),
        onDismiss = { editOdo = false },
        onConfirm = { store.setOdometer(it.toDouble()); editOdo = false },
    )
    selected?.let { d ->
        ItemActionsDialog(
            d, state,
            onDismiss = { selected = null },
            onDone = { km, date -> store.markDone(d.item.id, km, date); selected = null },
            onEdit = { editing = d.item; selected = null },
        )
    }
    editing?.let { item ->
        EditItemDialog(
            item,
            isNew = state.items.none { it.id == item.id },
            onDismiss = { editing = null },
            onSave = { store.upsertItem(it); editing = null },
            onDelete = { store.deleteItem(item.id); editing = null },
        )
    }
}

@Composable
private fun OdometerCard(state: AppState, onEdit: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Odometer", style = MaterialTheme.typography.labelLarge)
                Text(
                    "${DueCalculator.formatKm(state.vehicle.odometerKm.toInt())} km",
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                )
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit odometer") }
        }
    }
}

@Composable
private fun TrackingCard(state: AppState) {
    val ctx = LocalContext.current
    val status by TrackingService.status.collectAsStateWithLifecycle()
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        TrackingService.start(ctx)
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Kilometre tracking", style = MaterialTheme.typography.labelLarge)
            val modeText = when (state.settings.trackingMode) {
                TrackingMode.AUTO -> if (state.settings.obdAddress != null) "Auto: OBD (${state.settings.obdName ?: "adapter"}), GPS fallback" else "Auto: GPS (no OBD adapter chosen)"
                TrackingMode.OBD -> "OBD only (${state.settings.obdName ?: "choose adapter in settings"})"
                TrackingMode.GPS -> "GPS only"
                TrackingMode.MANUAL -> "Manual entry only"
            }
            Text(modeText, style = MaterialTheme.typography.bodyMedium)
            if (status.running || status.message.isNotEmpty()) {
                Text(
                    (if (status.running) "Trip ${"%.1f".format(status.tripKm)} km · " else "") + status.message,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (state.settings.trackingMode != TrackingMode.MANUAL) {
                Spacer(Modifier.height(8.dp))
                if (status.running) {
                    OutlinedButton(onClick = { TrackingService.stop(ctx) }) { Text("Stop trip") }
                } else {
                    Button(onClick = {
                        val needed = requiredPermissions(state.settings).filter {
                            ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED
                        }
                        if (needed.isEmpty()) TrackingService.start(ctx) else permLauncher.launch(needed.toTypedArray())
                    }) { Text("Start trip") }
                }
            }
        }
    }
}

fun requiredPermissions(s: Settings): List<String> {
    val list = mutableListOf<String>()
    if (s.trackingMode == TrackingMode.GPS || s.trackingMode == TrackingMode.AUTO) {
        list += Manifest.permission.ACCESS_FINE_LOCATION
        list += Manifest.permission.ACCESS_COARSE_LOCATION
    }
    if (Build.VERSION.SDK_INT >= 31 && s.obdAddress != null && s.trackingMode != TrackingMode.GPS) {
        list += Manifest.permission.BLUETOOTH_CONNECT
    }
    if (Build.VERSION.SDK_INT >= 33) list += Manifest.permission.POST_NOTIFICATIONS
    return list
}

@Composable
private fun DueRow(d: DueInfo, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(d.item.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    when (d.status) { DueStatus.OK -> "OK"; DueStatus.SOON -> "Soon"; DueStatus.OVERDUE -> "Overdue" },
                    color = statusColor(d.status), fontWeight = FontWeight.Bold,
                )
            }
            Text(DueCalculator.describe(d), style = MaterialTheme.typography.bodyMedium)
            val at = listOfNotNull(
                d.dueKm?.let { "${DueCalculator.formatKm(it)} km" },
                d.dueDate?.format(dateFmt),
            ).joinToString(" / ")
            if (at.isNotEmpty()) Text("Due at $at", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { d.progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = statusColor(d.status),
            )
        }
    }
}

// ---------------- Dialogs ----------------

@Composable
private fun NumberDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                text, { text = it.filter(Char::isDigit) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
            )
        },
        confirmButton = { TextButton(enabled = text.isNotEmpty(), onClick = { onConfirm(text.toInt()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun pickDate(ctx: Context, initial: LocalDate, onPicked: (LocalDate) -> Unit) {
    DatePickerDialog(ctx, { _, y, m, d -> onPicked(LocalDate.of(y, m + 1, d)) },
        initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
}

@Composable
private fun ItemActionsDialog(
    d: DueInfo, state: AppState,
    onDismiss: () -> Unit, onDone: (Int, LocalDate) -> Unit, onEdit: () -> Unit,
) {
    val ctx = LocalContext.current
    var km by remember { mutableStateOf(state.vehicle.odometerKm.toInt().toString()) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(d.item.name) },
        text = {
            Column {
                Text("Last done: ${DueCalculator.formatKm(d.item.lastDoneKm)} km on ${d.item.lastDoneDate.format(dateFmt)}")
                Spacer(Modifier.height(12.dp))
                Text("Mark as done", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    km, { km = it.filter(Char::isDigit) }, label = { Text("At km") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                )
                TextButton(onClick = { pickDate(ctx, date) { date = it } }) { Text("On ${date.format(dateFmt)}") }
            }
        },
        confirmButton = { TextButton(enabled = km.isNotEmpty(), onClick = { onDone(km.toInt(), date) }) { Text("Mark done") } },
        dismissButton = {
            Row {
                TextButton(onClick = onEdit) { Text("Edit item") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

@Composable
private fun EditItemDialog(
    item: ServiceItem, isNew: Boolean,
    onDismiss: () -> Unit, onSave: (ServiceItem) -> Unit, onDelete: () -> Unit,
) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(item.name) }
    var km by remember { mutableStateOf(item.intervalKm?.toString() ?: "") }
    var months by remember { mutableStateOf(item.intervalMonths?.toString() ?: "") }
    var lastKm by remember { mutableStateOf(item.lastDoneKm.toString()) }
    var lastDate by remember { mutableStateOf(item.lastDoneDate) }
    val valid = name.isNotBlank() && (km.isNotEmpty() || months.isNotEmpty()) && lastKm.isNotEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New service item" else "Edit service item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(
                    km, { km = it.filter(Char::isDigit) }, label = { Text("Every … km (empty = time only)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                )
                OutlinedTextField(
                    months, { months = it.filter(Char::isDigit) }, label = { Text("Every … months (empty = km only)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                )
                OutlinedTextField(
                    lastKm, { lastKm = it.filter(Char::isDigit) }, label = { Text("Last done at km") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                )
                TextButton(onClick = { pickDate(ctx, lastDate) { lastDate = it } }) { Text("Last done on ${lastDate.format(dateFmt)}") }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(item.copy(
                    name = name.trim(),
                    intervalKm = km.toIntOrNull()?.takeIf { it > 0 },
                    intervalMonths = months.toIntOrNull()?.takeIf { it > 0 },
                    lastDoneKm = lastKm.toInt(),
                    lastDoneDate = lastDate,
                ))
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (!isNew) TextButton(onClick = onDelete) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

// ---------------- Settings ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(state: AppState, store: Store, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(state.vehicle.name) }
    var warnKm by remember { mutableStateOf(state.settings.warnKm.toString()) }
    var warnDays by remember { mutableStateOf(state.settings.warnDays.toString()) }
    var pickObd by remember { mutableStateOf(false) }
    var changeCar by remember { mutableStateOf(false) }
    val btPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pickObd = true
    }

    fun saveFields() = store.update { s ->
        s.copy(
            vehicle = s.vehicle.copy(name = name.ifBlank { "My car" }),
            settings = s.settings.copy(
                warnKm = warnKm.toIntOrNull() ?: s.settings.warnKm,
                warnDays = warnDays.toIntOrNull() ?: s.settings.warnDays,
            ),
        )
    }
    BackHandler { saveFields(); onBack() }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = { IconButton(onClick = { saveFields(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                ListItem(
                    headlineContent = { Text("Car") },
                    supportingContent = {
                        Text(listOf(state.vehicle.make, state.vehicle.model).filter { it.isNotEmpty() }.joinToString(" ").ifEmpty { "Not set" } +
                            " · " + state.vehicle.fuel.label)
                    },
                    modifier = Modifier.clickable { changeCar = true },
                )
            }
            item { OutlinedTextField(name, { name = it }, label = { Text("Name shown in the app") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { Text("Kilometre source", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
            val modes = listOf(
                TrackingMode.AUTO to "Auto: OBD-II adapter when connected, otherwise GPS",
                TrackingMode.OBD to "OBD-II adapter only",
                TrackingMode.GPS to "GPS only",
                TrackingMode.MANUAL to "Manual entry only",
            )
            items(modes) { (mode, label) ->
                Row(
                    Modifier.fillMaxWidth().selectable(state.settings.trackingMode == mode) {
                        store.update { it.copy(settings = it.settings.copy(trackingMode = mode)) }
                    }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = state.settings.trackingMode == mode, onClick = null)
                    Spacer(Modifier.width(8.dp))
                    Text(label)
                }
            }
            item {
                ListItem(
                    headlineContent = { Text("OBD-II adapter") },
                    supportingContent = { Text(state.settings.obdName ?: "None chosen. Pair your ELM327 in Android Bluetooth settings first.") },
                    modifier = Modifier.clickable {
                        if (Build.VERSION.SDK_INT >= 31 &&
                            ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                        ) btPerm.launch(Manifest.permission.BLUETOOTH_CONNECT) else pickObd = true
                    },
                )
            }
            item { Text("Reminders", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
            item {
                OutlinedTextField(warnKm, { warnKm = it.filter(Char::isDigit) }, label = { Text("Warn this many km before due") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            item {
                OutlinedTextField(warnDays, { warnDays = it.filter(Char::isDigit) }, label = { Text("Warn this many days before due") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            item {
                Text(
                    "All data is stored only on this phone. No account and no internet needed.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }

    if (changeCar) CarSetupDialog(state, store, firstRun = false, onDismiss = { changeCar = false; name = store.state.value.vehicle.name })
    if (pickObd) ObdPickerDialog(
        onDismiss = { pickObd = false },
        onPick = { addr, n ->
            store.update { it.copy(settings = it.settings.copy(obdAddress = addr, obdName = n)) }
            pickObd = false
        },
    )
}

@SuppressLint("MissingPermission")
@Composable
private fun ObdPickerDialog(onDismiss: () -> Unit, onPick: (String?, String?) -> Unit) {
    val ctx = LocalContext.current
    val devices = remember {
        runCatching {
            ctx.getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices?.toList().orEmpty()
                .map { it.address to (it.name ?: it.address) }
                .sortedByDescending { (_, n) -> n.contains("OBD", true) || n.contains("ELM", true) || n.contains("V-LINK", true) }
        }.getOrDefault(emptyList())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose OBD-II adapter") },
        text = {
            if (devices.isEmpty()) Text("No paired Bluetooth devices. Pair the adapter in Android settings (PIN is usually 1234 or 0000), then come back.")
            else LazyColumn {
                items(devices) { (addr, n) ->
                    ListItem(headlineContent = { Text(n) }, supportingContent = { Text(addr) },
                        modifier = Modifier.clickable { onPick(addr, n) })
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        dismissButton = { TextButton(onClick = { onPick(null, null) }) { Text("Clear") } },
    )
}

// ---------------- History ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryScreen(state: AppState, onBack: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Service history") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
    }) { pad ->
        if (state.history.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { Text("Nothing recorded yet") }
        } else LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp)) {
            items(state.history) { r ->
                ListItem(
                    headlineContent = { Text(r.itemName) },
                    supportingContent = { Text("${DueCalculator.formatKm(r.km)} km · ${r.date.format(dateFmt)}") },
                )
                HorizontalDivider()
            }
        }
    }
}
