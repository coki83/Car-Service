package io.github.coki83.carservice.car

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.ForegroundCarColorSpan
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import android.text.SpannableString
import android.text.Spanned
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import io.github.coki83.carservice.data.DueCalculator
import io.github.coki83.carservice.data.DueStatus
import io.github.coki83.carservice.data.Store
import io.github.coki83.carservice.tracking.TrackingService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Read-only list of service items, most urgent first, plus the odometer and a trip toggle. */
class DueScreen(carContext: CarContext) : Screen(carContext) {

    private val store = Store.get(carContext)
    private var watch: Job? = null

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                // Refresh at most once per whole km so we stay well inside the host's template refresh limits.
                watch = lifecycleScope.launch {
                    combine(store.state, TrackingService.status) { s, t -> Triple(s.vehicle.odometerKm.toInt(), s.items, t.running) }
                        .distinctUntilChanged()
                        .collect { invalidate() }
                }
            }
            override fun onStop(owner: LifecycleOwner) { watch?.cancel() }
        })
    }

    override fun onGetTemplate(): Template {
        val state = store.state.value
        val due = DueCalculator.all(state)
        val list = ItemList.Builder()
        if (due.isEmpty()) {
            list.setNoItemsMessage("No service items. Add them in the phone app.")
        }
        // Hosts limit list length (usually 6); show the most urgent ones.
        for (d in due.take(6)) {
            val label = when (d.status) { DueStatus.OK -> "OK"; DueStatus.SOON -> "Soon"; DueStatus.OVERDUE -> "Overdue" }
            val color = when (d.status) { DueStatus.OK -> CarColor.GREEN; DueStatus.SOON -> CarColor.YELLOW; DueStatus.OVERDUE -> CarColor.RED }
            val span = SpannableString(label).apply { setSpan(ForegroundCarColorSpan.create(color), 0, label.length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE) }
            list.addItem(
                Row.Builder()
                    .setTitle(d.item.name)
                    .addText(span)
                    .addText(DueCalculator.describe(d))
                    .build()
            )
        }

        val running = TrackingService.status.value.running
        val tripAction = Action.Builder()
            .setTitle(if (running) "Stop trip" else "Start trip")
            .setOnClickListener {
                if (running) TrackingService.stop(carContext) else runCatching { TrackingService.start(carContext) }
                    .onFailure { CarToast.makeText(carContext, "Open the phone app to allow location", CarToast.LENGTH_LONG).show() }
                invalidate()
            }
            .build()

        return ListTemplate.Builder()
            .setTitle("${DueCalculator.formatKm(state.vehicle.odometerKm.toInt())} km · ${state.vehicle.name}")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(list.build())
            .setActionStrip(ActionStrip.Builder().addAction(tripAction).build())
            .build()
    }
}
