package io.github.coki83.carservice.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.UUID

/**
 * Everything lives in one JSON file in the app's private storage. No account, no network.
 * Small enough that rewriting it on every change is fine.
 */
class Store private constructor(private val file: File) {

    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppState> = _state.asStateFlow()

    @Synchronized
    fun update(transform: (AppState) -> AppState) {
        val next = transform(_state.value)
        _state.value = next
        save(next)
    }

    fun setOdometer(km: Double) = update {
        it.copy(vehicle = it.vehicle.copy(odometerKm = km, odometerUpdated = System.currentTimeMillis()))
    }

    fun addDistance(km: Double) {
        if (km <= 0) return
        update { it.copy(vehicle = it.vehicle.copy(odometerKm = it.vehicle.odometerKm + km, odometerUpdated = System.currentTimeMillis())) }
    }

    fun upsertItem(item: ServiceItem) = update { s ->
        val exists = s.items.any { it.id == item.id }
        s.copy(items = if (exists) s.items.map { if (it.id == item.id) item else it } else s.items + item)
    }

    fun deleteItem(id: String) = update { s -> s.copy(items = s.items.filterNot { it.id == id }) }

    fun markDone(id: String, km: Int, date: LocalDate, note: String = "") = update { s ->
        val item = s.items.firstOrNull { it.id == id } ?: return@update s
        s.copy(
            items = s.items.map { if (it.id == id) it.copy(lastDoneKm = km, lastDoneDate = date) else it },
            history = listOf(ServiceRecord(id, item.name, km, date, note)) + s.history,
        )
    }

    fun newId(): String = UUID.randomUUID().toString()

    // ---- persistence ----

    private fun load(): AppState {
        if (!file.exists()) {
            val today = LocalDate.now()
            return AppState(items = defaultItems(0, today))
        }
        return try { fromJson(JSONObject(file.readText())) } catch (e: Exception) { AppState() }
    }

    private fun save(s: AppState) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(toJson(s).toString())
        tmp.renameTo(file)
    }

    companion object {
        @Volatile private var instance: Store? = null

        fun get(context: Context): Store = instance ?: synchronized(this) {
            instance ?: Store(File(context.applicationContext.filesDir, "car_service.json")).also { instance = it }
        }

        fun toJson(s: AppState): JSONObject = JSONObject().apply {
            put("vehicle", JSONObject().apply {
                put("name", s.vehicle.name)
                put("odometerKm", s.vehicle.odometerKm)
                put("odometerUpdated", s.vehicle.odometerUpdated)
            })
            put("items", JSONArray().apply {
                s.items.forEach { i ->
                    put(JSONObject().apply {
                        put("id", i.id); put("name", i.name)
                        i.intervalKm?.let { put("intervalKm", it) }
                        i.intervalMonths?.let { put("intervalMonths", it) }
                        put("lastDoneKm", i.lastDoneKm); put("lastDoneDate", i.lastDoneDate.toString())
                    })
                }
            })
            put("history", JSONArray().apply {
                s.history.forEach { r ->
                    put(JSONObject().apply {
                        put("itemId", r.itemId); put("itemName", r.itemName)
                        put("km", r.km); put("date", r.date.toString()); put("note", r.note)
                    })
                }
            })
            put("settings", JSONObject().apply {
                put("trackingMode", s.settings.trackingMode.name)
                s.settings.obdAddress?.let { put("obdAddress", it) }
                s.settings.obdName?.let { put("obdName", it) }
                put("warnKm", s.settings.warnKm); put("warnDays", s.settings.warnDays)
            })
        }

        fun fromJson(o: JSONObject): AppState {
            val v = o.optJSONObject("vehicle") ?: JSONObject()
            val items = o.optJSONArray("items") ?: JSONArray()
            val hist = o.optJSONArray("history") ?: JSONArray()
            val st = o.optJSONObject("settings") ?: JSONObject()
            return AppState(
                vehicle = Vehicle(
                    name = v.optString("name", "My car"),
                    odometerKm = v.optDouble("odometerKm", 0.0),
                    odometerUpdated = v.optLong("odometerUpdated", 0L),
                ),
                items = (0 until items.length()).map { idx ->
                    val i = items.getJSONObject(idx)
                    ServiceItem(
                        id = i.getString("id"),
                        name = i.getString("name"),
                        intervalKm = if (i.has("intervalKm")) i.getInt("intervalKm") else null,
                        intervalMonths = if (i.has("intervalMonths")) i.getInt("intervalMonths") else null,
                        lastDoneKm = i.optInt("lastDoneKm", 0),
                        lastDoneDate = LocalDate.parse(i.getString("lastDoneDate")),
                    )
                },
                history = (0 until hist.length()).map { idx ->
                    val r = hist.getJSONObject(idx)
                    ServiceRecord(r.getString("itemId"), r.getString("itemName"), r.getInt("km"),
                        LocalDate.parse(r.getString("date")), r.optString("note", ""))
                },
                settings = Settings(
                    trackingMode = runCatching { TrackingMode.valueOf(st.optString("trackingMode", "AUTO")) }.getOrDefault(TrackingMode.AUTO),
                    obdAddress = st.optString("obdAddress").ifEmpty { null },
                    obdName = st.optString("obdName").ifEmpty { null },
                    warnKm = st.optInt("warnKm", 1000),
                    warnDays = st.optInt("warnDays", 30),
                ),
            )
        }
    }
}
