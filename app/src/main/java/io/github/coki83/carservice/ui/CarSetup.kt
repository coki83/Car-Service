package io.github.coki83.carservice.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.coki83.carservice.data.*
import java.time.LocalDate

private const val OTHER = "Other (type it)"

/**
 * Pick make, model and fuel type. On first run it also asks for the odometer.
 * The service list is built from the fuel type's template, filtered by model year and the user's ticks.
 */
@Composable
fun CarSetupDialog(state: AppState, store: Store, firstRun: Boolean, onDismiss: () -> Unit) {
    val v = state.vehicle
    var make by remember { mutableStateOf(v.make) }
    var model by remember { mutableStateOf(v.model) }
    var fuel by remember { mutableStateOf(v.fuel) }
    var year by remember { mutableStateOf(v.year?.toString() ?: "") }
    // Template items the user unticked. When editing, items they deleted earlier start unticked.
    var unticked by remember {
        mutableStateOf(if (firstRun) emptySet() else CarCatalog.templateFor(v.fuel, v.year).map { it.id }.toSet() - state.items.map { it.id }.toSet())
    }
    val yearNum = year.toIntOrNull()?.takeIf { it in 1950..2100 }
    val candidates = remember(fuel, yearNum) { CarCatalog.templateFor(fuel, yearNum) }
    var odo by remember { mutableStateOf(if (firstRun) "" else v.odometerKm.toInt().toString()) }
    var picking by remember { mutableStateOf<String?>(null) } // "make" | "model"
    var typingMake by remember { mutableStateOf(make.isNotEmpty() && make !in CarCatalog.makes) }
    var typingModel by remember { mutableStateOf(model.isNotEmpty() && CarCatalog.makes[make]?.contains(model) != true) }

    val valid = make.isNotBlank() && (!firstRun || odo.isNotEmpty())

    AlertDialog(
        onDismissRequest = { if (!firstRun) onDismiss() },
        title = { Text(if (firstRun) "Your car" else "Change car") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (typingMake) {
                    OutlinedTextField(make, { make = it }, label = { Text("Make") }, singleLine = true)
                } else {
                    PickerField("Make", make.ifEmpty { "Choose…" }) { picking = "make" }
                }
                if (typingModel || typingMake) {
                    OutlinedTextField(model, { model = it }, label = { Text("Model") }, singleLine = true)
                } else {
                    PickerField("Model", model.ifEmpty { "Choose…" }, enabled = make.isNotEmpty()) { picking = "model" }
                }
                OutlinedTextField(
                    year, { year = it.filter(Char::isDigit).take(4) }, label = { Text("Model year (e.g. 2007)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                )
                Text("Fuel", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                FuelType.entries.forEach { f ->
                    Row(
                        Modifier.fillMaxWidth().selectable(fuel == f) { fuel = f },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = fuel == f, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(f.label)
                    }
                }
                Text("Service items", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                Text(
                    if (yearNum == null) "Enter the year to leave out parts older cars don't have (DPF, AdBlue). Untick anything else your car doesn't have."
                    else "Untick anything your car doesn't have.",
                    style = MaterialTheme.typography.bodySmall,
                )
                candidates.forEach { t ->
                    val on = t.id !in unticked
                    Row(
                        Modifier.fillMaxWidth().clickable { unticked = if (on) unticked + t.id else unticked - t.id },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = on, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Text(t.name)
                    }
                }
                if (!firstRun) {
                    Text("Past service dates and items you added yourself are kept.", style = MaterialTheme.typography.bodySmall)
                }
                if (firstRun) {
                    OutlinedTextField(
                        odo, { odo = it.filter(Char::isDigit) }, label = { Text("Odometer now (km)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                    )
                    Text(
                        "Service items start counting from today. Tap each one afterwards to set when it was really last done.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                val today = LocalDate.now()
                store.update { s ->
                    val km = if (firstRun) odo.toInt() else s.vehicle.odometerKm.toInt()
                    val name = listOf(make.trim(), model.trim()).filter { it.isNotEmpty() }.joinToString(" ").ifEmpty { "My car" }
                    val chosen = candidates.filter { it.id !in unticked }
                    val items = retemplate(if (firstRun) emptyList() else s.items, chosen, km, today)
                    s.copy(
                        vehicle = s.vehicle.copy(
                            name = name, make = make.trim(), model = model.trim(), fuel = fuel, year = yearNum,
                            odometerKm = if (firstRun) km.toDouble() else s.vehicle.odometerKm,
                            odometerUpdated = if (firstRun) System.currentTimeMillis() else s.vehicle.odometerUpdated,
                        ),
                        items = items,
                    )
                }
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { if (!firstRun) TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    when (picking) {
        "make" -> ListPicker("Make", CarCatalog.makes.keys.toList() + OTHER, onDismiss = { picking = null }) {
            if (it == OTHER) { typingMake = true; make = ""; model = "" }
            else { if (it != make) model = ""; make = it; typingMake = false; typingModel = false }
            picking = null
        }
        "model" -> ListPicker("Model", CarCatalog.makes[make].orEmpty() + OTHER, onDismiss = { picking = null }) {
            if (it == OTHER) { typingModel = true; model = "" } else { model = it; typingModel = false }
            picking = null
        }
    }
}

@Composable
private fun PickerField(label: String, value: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun ListPicker(title: String, options: List<String>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = options.filter { it == OTHER || it.contains(query, ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, label = { Text("Search") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(shown) { o -> ListItem(headlineContent = { Text(o) }, modifier = Modifier.clickable { onPick(o) }) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
