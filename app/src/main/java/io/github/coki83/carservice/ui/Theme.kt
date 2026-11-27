package io.github.coki83.carservice.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.coki83.carservice.data.DueStatus

// A calm "instrument cluster" palette: deep blue for the brand, amber like a dashboard warning lamp.
private val Light = lightColorScheme(
    primary = Color(0xFF1B5FAA), onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4FA), onPrimaryContainer = Color(0xFF0B2A4D),
    secondary = Color(0xFF4A6178), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE6F0), onSecondaryContainer = Color(0xFF1A2633),
    tertiary = Color(0xFFC77700), onTertiary = Color.White,
    background = Color(0xFFF2F4F7), onBackground = Color(0xFF151B22),
    surface = Color(0xFFF2F4F7), onSurface = Color(0xFF151B22),
    surfaceVariant = Color(0xFFE1E6EC), onSurfaceVariant = Color(0xFF55606C),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF8F9FB),
    surfaceContainer = Color.White, surfaceContainerHigh = Color.White, surfaceContainerHighest = Color.White,
    outline = Color(0xFFC3CBD4), outlineVariant = Color(0xFFDCE2E8),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8EC1FF), onPrimary = Color(0xFF00315F),
    primaryContainer = Color(0xFF1C3D63), onPrimaryContainer = Color(0xFFD6E4FA),
    secondary = Color(0xFFB2C6DA), onSecondary = Color(0xFF1C3144),
    secondaryContainer = Color(0xFF26323F), onSecondaryContainer = Color(0xFFDDE6F0),
    tertiary = Color(0xFFFFB74D), onTertiary = Color(0xFF462A00),
    background = Color(0xFF0D1217), onBackground = Color(0xFFE3E8EE),
    surface = Color(0xFF0D1217), onSurface = Color(0xFFE3E8EE),
    surfaceVariant = Color(0xFF26303A), onSurfaceVariant = Color(0xFF9EABB8),
    surfaceContainerLowest = Color(0xFF0A0E12), surfaceContainerLow = Color(0xFF121920),
    surfaceContainer = Color(0xFF161E26), surfaceContainerHigh = Color(0xFF1B242D), surfaceContainerHighest = Color(0xFF212B35),
    outline = Color(0xFF3A4652), outlineVariant = Color(0xFF2A343E),
)

private val AppTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp),
    )
}

@Composable
fun CarServiceTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, typography = AppTypography, content = content)
}

@Composable
@ReadOnlyComposable
fun statusColor(s: DueStatus): Color {
    val dark = isSystemInDarkTheme()
    return when (s) {
        DueStatus.OK -> if (dark) Color(0xFF66BB6A) else Color(0xFF2E7D32)
        DueStatus.SOON -> if (dark) Color(0xFFFFB74D) else Color(0xFFC77700)
        DueStatus.OVERDUE -> if (dark) Color(0xFFFF6E61) else Color(0xFFC62828)
    }
}

fun statusLabel(s: DueStatus): String = when (s) {
    DueStatus.OK -> "OK"
    DueStatus.SOON -> "Soon"
    DueStatus.OVERDUE -> "Overdue"
}

/** An icon per service item, so the list can be scanned at a glance. */
fun itemIcon(id: String): ImageVector = when (id) {
    "oil" -> Icons.Default.OilBarrel
    "air", "cabin" -> Icons.Default.Air
    "fuelfilter", "lpgfilter" -> Icons.Default.LocalGasStation
    "lpgcheck" -> Icons.Default.LocalGasStation
    "brakefluid", "coolant", "evcoolant" -> Icons.Default.WaterDrop
    "brakes" -> Icons.Default.DiscFull
    "plugs", "glowplugs" -> Icons.Default.Bolt
    "dpf", "adblue" -> Icons.Default.FilterAlt
    "timing" -> Icons.Default.Settings
    "inspection" -> Icons.Default.FactCheck
    "tyres" -> Icons.Default.TireRepair
    "battery12v" -> Icons.Default.BatteryChargingFull
    "evcheck", "hybridcheck" -> Icons.Default.ElectricBolt
    else -> Icons.Default.Build
}

val OdometerDigitStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = 0.sp)
