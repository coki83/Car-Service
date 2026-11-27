package io.github.coki83.carservice.ui

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.coki83.carservice.data.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

@Composable
internal fun NumberDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
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

internal fun pickDate(ctx: Context, initial: LocalDate, onPicked: (LocalDate) -> Unit) {
    DatePickerDialog(ctx, { _, y, m, d -> onPicked(LocalDate.of(y, m + 1, d)) },
        initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
}

@Composable
internal fun ItemActionsDialog(
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
internal fun EditItemDialog(
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

@SuppressLint("MissingPermission")
@Composable
internal fun ObdPickerDialog(onDismiss: () -> Unit, onPick: (String?, String?) -> Unit) {
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

