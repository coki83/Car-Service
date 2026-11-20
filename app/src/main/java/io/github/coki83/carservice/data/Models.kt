package io.github.coki83.carservice.data

import java.time.LocalDate

/** How the app keeps the odometer up to date. */
enum class TrackingMode { AUTO, OBD, GPS, MANUAL }

data class Vehicle(
    val name: String = "My car",
    /** Current odometer in km (decimal so GPS/OBD can add fractions). */
    val odometerKm: Double = 0.0,
    val odometerUpdated: Long = 0L,
)

data class ServiceItem(
    val id: String,
    val name: String,
    /** Interval in km, or null if this item is time-only. */
    val intervalKm: Int? = null,
    /** Interval in months, or null if this item is km-only. */
    val intervalMonths: Int? = null,
    val lastDoneKm: Int = 0,
    val lastDoneDate: LocalDate = LocalDate.now(),
)

data class ServiceRecord(
    val itemId: String,
    val itemName: String,
    val km: Int,
    val date: LocalDate,
    val note: String = "",
)

data class Settings(
    val trackingMode: TrackingMode = TrackingMode.AUTO,
    /** Bluetooth MAC of the paired ELM327 adapter, if chosen. */
    val obdAddress: String? = null,
    val obdName: String? = null,
    /** Warn this many km before an item is due. */
    val warnKm: Int = 1000,
    /** Warn this many days before an item is due. */
    val warnDays: Int = 30,
)

data class AppState(
    val vehicle: Vehicle = Vehicle(),
    val items: List<ServiceItem> = emptyList(),
    val history: List<ServiceRecord> = emptyList(),
    val settings: Settings = Settings(),
)

/** Typical European intervals. The user edits these to match their service book. */
fun defaultItems(odometerKm: Int, today: LocalDate): List<ServiceItem> = listOf(
    ServiceItem("oil", "Engine oil & filter", 15_000, 12, odometerKm, today),
    ServiceItem("air", "Air filter", 30_000, 24, odometerKm, today),
    ServiceItem("cabin", "Cabin (pollen) filter", 15_000, 12, odometerKm, today),
    ServiceItem("brakefluid", "Brake fluid", null, 24, odometerKm, today),
    ServiceItem("plugs", "Spark plugs", 60_000, 48, odometerKm, today),
    ServiceItem("timing", "Timing belt", 120_000, 72, odometerKm, today),
    ServiceItem("inspection", "Technical inspection", null, 12, odometerKm, today),
)
