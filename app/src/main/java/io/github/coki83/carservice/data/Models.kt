package io.github.coki83.carservice.data

import java.time.LocalDate

/** How the app keeps the odometer up to date. */
enum class TrackingMode { AUTO, OBD, GPS, MANUAL }

data class Vehicle(
    val name: String = "My car",
    val make: String = "",
    val model: String = "",
    val fuel: FuelType = FuelType.PETROL,
    /** Model year, used to leave out parts the car doesn't have (e.g. no DPF before 2010). */
    val year: Int? = null,
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
    /** Start and stop trips by themselves (car Bluetooth / Android Auto / driving detection). */
    val autoStart: Boolean = true,
    /** Bluetooth devices (the car's head unit, the OBD adapter) whose connection starts a trip. Address -> name. */
    val carBluetooth: Map<String, String> = emptyMap(),
    /** Use the phone's motion sensors to notice when you start and stop driving. */
    val detectDriving: Boolean = false,
)

data class AppState(
    val vehicle: Vehicle = Vehicle(),
    val items: List<ServiceItem> = emptyList(),
    val history: List<ServiceRecord> = emptyList(),
    val settings: Settings = Settings(),
)

/** Typical European intervals for the fuel type. The user edits these to match their service book. */
fun defaultItems(odometerKm: Int, today: LocalDate, fuel: FuelType = FuelType.PETROL, year: Int? = null): List<ServiceItem> =
    CarCatalog.templateFor(fuel, year).map { ServiceItem(it.id, it.name, it.km, it.months, odometerKm, today) }

private val templateIds: Set<String> by lazy { FuelType.entries.flatMap { f -> CarCatalog.template(f).map { it.id } }.toSet() }

/**
 * Rebuild the service list from the chosen template items: items that existed before keep their
 * "last done" values, template items not chosen are dropped, items the user added themselves are kept.
 */
fun retemplate(items: List<ServiceItem>, chosen: List<Template>, odometerKm: Int, today: LocalDate): List<ServiceItem> {
    val old = items.associateBy { it.id }
    val fromTemplate = chosen.map { t ->
        old[t.id]?.copy(name = t.name, intervalKm = t.km, intervalMonths = t.months)
            ?: ServiceItem(t.id, t.name, t.km, t.months, odometerKm, today)
    }
    val custom = items.filter { it.id !in templateIds }
    return fromTemplate + custom
}
