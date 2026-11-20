package io.github.coki83.carservice.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class DueStatus { OK, SOON, OVERDUE }

data class DueInfo(
    val item: ServiceItem,
    val dueKm: Int?,
    val dueDate: LocalDate?,
    /** km left until due (negative = overdue), null if not km-based. */
    val kmLeft: Int?,
    /** days left until due (negative = overdue), null if not time-based. */
    val daysLeft: Long?,
    val status: DueStatus,
    /** 0..1+ share of the interval already used (worst of km and time). */
    val progress: Float,
)

object DueCalculator {

    fun compute(item: ServiceItem, odometerKm: Double, today: LocalDate, warnKm: Int, warnDays: Int): DueInfo {
        val km = odometerKm.toInt()
        val dueKm = item.intervalKm?.let { item.lastDoneKm + it }
        val dueDate = item.intervalMonths?.let { item.lastDoneDate.plusMonths(it.toLong()) }
        val kmLeft = dueKm?.let { it - km }
        val daysLeft = dueDate?.let { ChronoUnit.DAYS.between(today, it) }

        val overdue = (kmLeft != null && kmLeft <= 0) || (daysLeft != null && daysLeft <= 0)
        val soon = (kmLeft != null && kmLeft <= warnKm) || (daysLeft != null && daysLeft <= warnDays)
        val status = when {
            overdue -> DueStatus.OVERDUE
            soon -> DueStatus.SOON
            else -> DueStatus.OK
        }

        val kmProgress = item.intervalKm?.takeIf { it > 0 }?.let { (km - item.lastDoneKm).toFloat() / it }
        val timeProgress = dueDate?.let {
            val total = ChronoUnit.DAYS.between(item.lastDoneDate, it).coerceAtLeast(1)
            ChronoUnit.DAYS.between(item.lastDoneDate, today).toFloat() / total
        }
        val progress = listOfNotNull(kmProgress, timeProgress).maxOrNull()?.coerceAtLeast(0f) ?: 0f

        return DueInfo(item, dueKm, dueDate, kmLeft, daysLeft, status, progress)
    }

    /** All items, most urgent first. */
    fun all(state: AppState, today: LocalDate = LocalDate.now()): List<DueInfo> =
        state.items
            .map { compute(it, state.vehicle.odometerKm, today, state.settings.warnKm, state.settings.warnDays) }
            .sortedByDescending { it.progress }

    /** Short human text like "in 1 240 km or 3 months" / "overdue by 300 km". */
    fun describe(d: DueInfo): String {
        val kmLeft = d.kmLeft
        val daysLeft = d.daysLeft
        if (kmLeft == null && daysLeft == null) return "No interval set"
        if (d.status == DueStatus.OVERDUE) {
            val over = mutableListOf<String>()
            if (kmLeft != null && kmLeft <= 0) over += "${formatKm(-kmLeft)} km"
            if (daysLeft != null && daysLeft <= 0) over += formatDays(-daysLeft)
            return "Overdue by " + over.joinToString(" and ")
        }
        val parts = mutableListOf<String>()
        if (kmLeft != null) parts += "${formatKm(kmLeft)} km"
        if (daysLeft != null) parts += formatDays(daysLeft)
        return "Due in " + parts.joinToString(" or ")
    }

    fun formatKm(km: Int): String = String.format(java.util.Locale.ROOT, "%,d", km).replace(',', ' ')

    private fun formatDays(days: Long): String = when {
        days >= 60 -> "${days / 30} months"
        days == 1L -> "1 day"
        else -> "$days days"
    }
}
