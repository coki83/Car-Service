package io.github.coki83.carservice.ui

import android.content.pm.PackageManager
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.coki83.carservice.data.*
import io.github.coki83.carservice.tracking.TrackingService
import java.time.LocalDate

@Composable
fun HomeScreen(state: AppState, store: Store, contentPadding: PaddingValues) {
    val due = remember(state) { DueCalculator.all(state) }
    var editOdo by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<DueInfo?>(null) }
    var editing by remember { mutableStateOf<ServiceItem?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = contentPadding.calculateTopPadding() + 12.dp,
                bottom = contentPadding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { HeroCard(state, onEditOdo = { editOdo = true }) }
            due.firstOrNull()?.let { next -> item { NextUpCard(next, onDone = { selected = next }) } }
            item { TripCard(state) }
            item { SectionHeader(due) }
            items(due, key = { it.item.id }) { d -> ServiceRow(d, onClick = { selected = d }) }
        }
        ExtendedFloatingActionButton(
            onClick = {
                editing = ServiceItem(store.newId(), "", 15_000, 12, state.vehicle.odometerKm.toInt(), LocalDate.now())
            },
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text("Add item") },
            modifier = Modifier.align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
        )
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

// ---------------- Hero: car + odometer ----------------

@Composable
private fun HeroCard(state: AppState, onEditOdo: () -> Unit) {
    val v = state.vehicle
    val cs = MaterialTheme.colorScheme
    val gradient = Brush.linearGradient(listOf(Color(0xFF123E73), Color(0xFF1B5FAA), Color(0xFF2C7BC9)))
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(gradient).padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.DirectionsCar, null, tint = Color.White) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(v.name, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val sub = listOfNotNull(v.year?.toString(), v.fuel.label).joinToString(" · ")
                    Text(sub, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.75f))
                }
                FilledIconButton(
                    onClick = onEditOdo,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = 0.18f), contentColor = Color.White),
                ) { Icon(Icons.Default.Edit, "Edit odometer") }
            }
            Column {
                Text("ODOMETER", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    OdometerDigits(v.odometerKm.toInt(), Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    Text("km", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 6.dp))
                }
                Spacer(Modifier.height(6.dp))
                val updated = if (v.odometerUpdated == 0L) "Not set yet" else
                    "Updated " + DateUtils.getRelativeTimeSpanString(v.odometerUpdated, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
                Text(updated, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

/** Digits in little drums, like a mechanical odometer. */
@Composable
private fun OdometerDigits(km: Int, modifier: Modifier = Modifier) {
    val digits = km.coerceAtLeast(0).toString().padStart(6, '0')
    val size = if (digits.length > 6) 26.sp else 30.sp
    Row(modifier.widthIn(max = 300.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        digits.forEach { c ->
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(7.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF0B1016), Color(0xFF1E2731), Color(0xFF0B1016))))
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(c.toString(), style = OdometerDigitStyle, fontSize = size, fontFamily = FontFamily.Monospace,
                    color = Color.White, maxLines = 1, softWrap = false)
            }
        }
    }
}

// ---------------- Next up ----------------

@Composable
private fun NextUpCard(d: DueInfo, onDone: () -> Unit) {
    val color = statusColor(d.status)
    ElevatedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { d.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxSize(),
                    color = color,
                    strokeWidth = 7.dp,
                    trackColor = color.copy(alpha = 0.15f),
                )
                Icon(itemIcon(d.item.id), null, tint = color, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("NEXT SERVICE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(d.item.name, style = MaterialTheme.typography.titleMedium)
                Text(DueCalculator.describe(d), style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.SemiBold)
                dueAt(d)?.let { Text("At $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Spacer(Modifier.height(6.dp))
                FilledTonalButton(onClick = onDone, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                    Icon(Icons.Default.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Mark done")
                }
            }
        }
    }
}

private fun dueAt(d: DueInfo): String? = listOfNotNull(
    d.dueKm?.let { "${DueCalculator.formatKm(it)} km" },
    d.dueDate?.format(dateFmt),
).joinToString(" / ").ifEmpty { null }

// ---------------- Trip ----------------

@Composable
private fun TripCard(state: AppState) {
    val ctx = LocalContext.current
    val status by TrackingService.status.collectAsStateWithLifecycle()
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        TrackingService.start(ctx)
    }
    val s = state.settings
    val running = status.running

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            PulseDot(active = running)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (running) "Trip · ${"%.1f".format(status.tripKm)} km" else "Not tracking",
                    style = MaterialTheme.typography.titleMedium,
                )
                val line = when {
                    running && status.autoReason != null -> "${status.autoReason!!.label} · ${status.message}"
                    running -> status.message
                    s.trackingMode == TrackingMode.MANUAL -> "Manual entry only"
                    s.autoStart && (s.carBluetooth.isNotEmpty() || s.obdAddress != null || s.detectDriving) -> "Starts by itself when you drive"
                    s.autoStart -> "Starts with Android Auto. Pick your car's Bluetooth in Settings for every drive."
                    else -> "Press Start before you drive"
                }
                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (s.trackingMode != TrackingMode.MANUAL) {
                Spacer(Modifier.width(8.dp))
                if (running) {
                    OutlinedButton(onClick = { TrackingService.stop(ctx) }) {
                        Icon(Icons.Default.Stop, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Stop")
                    }
                } else {
                    Button(onClick = {
                        val needed = requiredPermissions(s).filter {
                            ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED
                        }
                        if (needed.isEmpty()) TrackingService.start(ctx) else permLauncher.launch(needed.toTypedArray())
                    }) {
                        Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Start")
                    }
                }
            }
        }
    }
}

@Composable
private fun PulseDot(active: Boolean) {
    val color = if (active) Color(0xFF2E9D4F) else MaterialTheme.colorScheme.outline
    val alpha = if (active) {
        val t = rememberInfiniteTransition(label = "pulse")
        t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a").value
    } else 1f
    Box(Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
        Box(Modifier.size(14.dp).alpha(alpha).clip(CircleShape).background(color))
    }
}

// ---------------- Service list ----------------

@Composable
private fun SectionHeader(due: List<DueInfo>) {
    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("All services", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(DueStatus.OVERDUE, DueStatus.SOON, DueStatus.OK).forEach { st ->
                val n = due.count { it.status == st }
                if (n > 0) StatusPill("$n ${statusLabel(st).lowercase()}", statusColor(st))
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ServiceRow(d: DueInfo, onClick: () -> Unit) {
    val color = statusColor(d.status)
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Icon(itemIcon(d.item.id), null, tint = color) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(d.item.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(8.dp))
                    StatusPill(statusLabel(d.status), color)
                }
                Text(DueCalculator.describe(d), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LinearProgressIndicator(
                    progress = { d.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = color,
                    trackColor = color.copy(alpha = 0.14f),
                    drawStopIndicator = {},
                )
            }
        }
    }
}
