package io.github.coki83.carservice

import io.github.coki83.carservice.data.DueCalculator
import io.github.coki83.carservice.data.DueStatus
import io.github.coki83.carservice.data.CarCatalog
import io.github.coki83.carservice.data.FuelType
import io.github.coki83.carservice.data.ServiceItem
import io.github.coki83.carservice.data.defaultItems
import io.github.coki83.carservice.data.retemplate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import io.github.coki83.carservice.obd.Elm327
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DueCalculatorTest {
    private val today = LocalDate.of(2026, 11, 20)
    private val oil = ServiceItem("oil", "Oil", 15_000, 12, 100_000, LocalDate.of(2026, 6, 1))

    @Test fun okWhenFarAway() {
        val d = DueCalculator.compute(oil, 105_000.0, today, 1000, 30)
        assertEquals(DueStatus.OK, d.status)
        assertEquals(10_000, d.kmLeft)
        assertEquals(115_000, d.dueKm)
        assertEquals(LocalDate.of(2027, 6, 1), d.dueDate)
    }

    @Test fun soonByKm() {
        assertEquals(DueStatus.SOON, DueCalculator.compute(oil, 114_500.0, today, 1000, 30).status)
    }

    @Test fun overdueByTimeEvenWithKmLeft() {
        val d = DueCalculator.compute(oil, 101_000.0, LocalDate.of(2027, 6, 2), 1000, 30)
        assertEquals(DueStatus.OVERDUE, d.status)
        assertEquals("Overdue by 1 day", DueCalculator.describe(d))
    }

    @Test fun timeOnlyItem() {
        val fluid = ServiceItem("bf", "Brake fluid", null, 24, 0, LocalDate.of(2025, 1, 1))
        val d = DueCalculator.compute(fluid, 50_000.0, today, 1000, 30)
        assertNull(d.kmLeft)
        assertEquals(DueStatus.OK, d.status)
    }

    @Test fun describeUpcoming() {
        val d = DueCalculator.compute(oil, 113_760.0, today, 1000, 30)
        assertEquals("Due in 1 240 km or 6 months", DueCalculator.describe(d))
    }

    @Test fun parsesElmResponses() {
        assertEquals(0x3CL, Elm327.parse("410D3C\r", "410D", 1))
        assertEquals(1234567L, Elm327.parse("SEARCHING...\r41A60012D687", "41A6", 4))
        assertNull(Elm327.parse("NO DATA", "41A6", 4))
    }

    @Test fun dieselHasNoSparkPlugs() {
        val ids = defaultItems(0, today, FuelType.DIESEL).map { it.id }
        assertFalse("plugs" in ids)
        assertTrue("glowplugs" in ids && "fuelfilter" in ids && "dpf" in ids)
    }

    @Test fun switchingFuelKeepsHistoryAndCustomItems() {
        val petrol = defaultItems(50_000, today, FuelType.PETROL)
            .map { if (it.id == "oil") it.copy(lastDoneKm = 48_000) else it } +
            ServiceItem("my", "Wiper blades", null, 12, 50_000, today)
        val diesel = retemplate(petrol, CarCatalog.templateFor(FuelType.DIESEL, null), 50_000, today)
        assertEquals(48_000, diesel.first { it.id == "oil" }.lastDoneKm)
        assertTrue(diesel.any { it.id == "my" })
        assertFalse(diesel.any { it.id == "plugs" })
    }

    @Test fun olderDieselHasNoDpfOrAdBlue() {
        val ids = defaultItems(0, today, FuelType.DIESEL, 2007).map { it.id }
        assertFalse("dpf" in ids || "adblue" in ids)
        assertTrue("glowplugs" in ids)
        val newer = defaultItems(0, today, FuelType.DIESEL, 2019).map { it.id }
        assertTrue("dpf" in newer && "adblue" in newer)
    }
}
