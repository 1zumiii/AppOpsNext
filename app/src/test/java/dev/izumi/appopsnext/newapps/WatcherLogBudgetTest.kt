package dev.izumi.appopsnext.newapps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatcherLogBudgetTest {
    @Test fun `lines past the cap are left out and counted once the window turns`() {
        val budget = WatcherLogBudget(maxLines = 2, windowMillis = 1_000)
        val dropped = mutableListOf<Int>()

        assertTrue(budget.take(0, dropped::add))
        assertTrue(budget.take(10, dropped::add))
        assertFalse(budget.take(20, dropped::add))
        assertFalse(budget.take(30, dropped::add))
        assertTrue(dropped.isEmpty())

        assertTrue(budget.take(1_000, dropped::add))
        assertEquals(listOf(2), dropped)
    }

    @Test fun `a clock that goes back starts a new window`() {
        val budget = WatcherLogBudget(maxLines = 1, windowMillis = 1_000)
        assertTrue(budget.take(500) {})
        assertFalse(budget.take(600) {})
        assertTrue(budget.take(100) {})
    }
}
