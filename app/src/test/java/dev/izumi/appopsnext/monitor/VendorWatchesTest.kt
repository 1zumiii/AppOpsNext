package dev.izumi.appopsnext.monitor

import dev.izumi.appopsnext.appops.parser.AccessWatchKind.ACTIVE
import dev.izumi.appopsnext.appops.parser.AccessWatchKind.NOTED
import dev.izumi.appopsnext.appops.parser.AccessWatchKind.STARTED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VendorWatchesTest {
    /** Stands in for the service: refuses any array that holds a code in [refused]. */
    private class FakeService(private val refused: Set<Int>) {
        val calls = mutableListOf<List<Int>>()
        fun attempt(codes: IntArray): String {
            calls += codes.toList()
            val bad = codes.filter { it in refused }
            require(bad.isEmpty()) { "Invalid op code in: ${codes.toList()}" }
            return "callback${calls.size}"
        }
    }

    @Test fun `accepted codes take one registration`() {
        val service = FakeService(emptySet())
        val result = VendorWatches.registerIsolated(listOf(10008, 10021), service::attempt)
        assertEquals(listOf(listOf(10008, 10021)), service.calls)
        assertNull(result.batchFailure)
        assertEquals(listOf(VendorWatchAttempt(listOf(10008, 10021), "callback1", null)), result.attempts)
    }

    @Test fun `a refused code costs only itself`() {
        val service = FakeService(setOf(10054))
        val result = VendorWatches.registerIsolated(listOf(10008, 10021, 10054), service::attempt)
        assertEquals(listOf(listOf(10008, 10021, 10054), listOf(10008), listOf(10021), listOf(10054)), service.calls)
        assertEquals("Invalid op code in: [10008, 10021, 10054]", result.batchFailure)
        assertEquals(listOf("callback2", "callback3", null), result.attempts.map { it.handle })
        assertEquals("Invalid op code in: [10054]", result.attempts.last().failure)
    }

    @Test fun `a single refused code is not tried twice`() {
        val service = FakeService(setOf(10008))
        val result = VendorWatches.registerIsolated(listOf(10008), service::attempt)
        assertEquals(1, service.calls.size)
        assertNull(result.batchFailure)
        assertEquals("Invalid op code in: [10008]", result.attempts.single().failure)
        assertTrue(VendorWatches.registerIsolated(emptyList(), service::attempt).attempts.isEmpty())
    }

    @Test fun `each code is registered, refused or unknown`() {
        val outcomes = listOf(
            VendorWatchOutcome(ACTIVE, listOf(10008), null),
            VendorWatchOutcome(ACTIVE, listOf(10021), null),
            VendorWatchOutcome(ACTIVE, listOf(10054), "bad"),
            VendorWatchOutcome(NOTED, listOf(10008, 10021), null),
            VendorWatchOutcome(STARTED, listOf(10054), "bad too"),
        )
        val registry = mapOf(ACTIVE to setOf(10008), NOTED to emptySet<Int>())
        val watches = VendorWatches.evaluate(outcomes, registry).associateBy(VendorOpWatch::code)
        assertEquals(VendorWatchState.REGISTERED, watches.getValue(10008).state)
        assertEquals(setOf(ACTIVE), watches.getValue(10008).confirmedKinds)
        assertEquals(setOf(ACTIVE, NOTED), watches.getValue(10008).acceptedKinds)
        assertEquals(VendorWatchState.UNKNOWN, watches.getValue(10021).state)
        assertEquals(VendorWatchState.REFUSED, watches.getValue(10054).state)
        assertEquals(listOf("active: bad", "started: bad too"), watches.getValue(10054).failures)
    }

    @Test fun `an unreadable registry leaves accepted codes unknown`() {
        val outcomes = listOf(VendorWatchOutcome(NOTED, listOf(10008), null))
        assertEquals(VendorWatchState.UNKNOWN, VendorWatches.evaluate(outcomes, null).single().state)
        // A watch the registry could not read is missing from the map.
        assertEquals(VendorWatchState.UNKNOWN, VendorWatches.evaluate(outcomes, mapOf(ACTIVE to setOf(10008))).single().state)
    }

    @Test fun `standard sessions are judged as without private operations`() {
        val refusedVendor = listOf(VendorWatchOutcome(NOTED, listOf(10008), "Invalid op code in: [10008]"))
        val status = MonitorSessionStatus.of(true, registration(true, true, true, refusedVendor), 1, 2)
        assertEquals(MonitorStatus(true, true, true, true, 1, 2), status.copy(vendorOps = emptyList()))
        assertEquals(VendorWatchState.REFUSED, status.vendorOps.single().state)
        assertFalse(status.partial)

        val plain = MonitorSessionStatus.of(true, registration(true, false, true, emptyList()), 1, 1)
        assertEquals(MonitorStatus(true, false, true, true, 1, 1), plain)

        val failed = assertThrows(IllegalStateException::class.java) {
            MonitorSessionStatus.of(
                true,
                registration(false, false, false, listOf(VendorWatchOutcome(NOTED, listOf(10008), null))),
                1, 2,
            )
        }
        assertTrue(failed.message!!.startsWith("No watch could be registered"))
    }

    @Test fun `a private only session runs on what was accepted`() {
        val outcomes = listOf(
            VendorWatchOutcome(ACTIVE, listOf(10008), "refused"),
            VendorWatchOutcome(NOTED, listOf(10008), null),
            VendorWatchOutcome(STARTED, listOf(10008), "refused"),
        )
        val status = MonitorSessionStatus.of(false, registration(false, false, false, outcomes), 1, 1)
        assertEquals(listOf(false, true, false), listOf(status.activeWatch, status.notedWatch, status.startedWatch))
        assertEquals(VendorWatchState.UNKNOWN, status.vendorOps.single().state)
    }

    @Test fun `a private only session fails with the system's reasons when all are refused`() {
        val outcomes = listOf(
            VendorWatchOutcome(ACTIVE, listOf(10008), "Invalid op code in: [10008]"),
            VendorWatchOutcome(NOTED, listOf(10008), "Invalid op code in: [10008]"),
            VendorWatchOutcome(STARTED, listOf(10008), "Invalid op code in: [10008]"),
        )
        val failed = assertThrows(IllegalStateException::class.java) {
            MonitorSessionStatus.of(false, registration(false, false, false, outcomes), 1, 1)
        }
        assertEquals(
            "The system refused every selected MIUI operation: 10008 (active: Invalid op code in: [10008], " +
                "noted: Invalid op code in: [10008], started: Invalid op code in: [10008])",
            failed.message,
        )
        assertNull(VendorWatches.startFailure(emptyList()))
    }

    @Test fun `diagnostic lines name the code, the watches and the reasons`() {
        val watch = VendorOpWatch(10021, VendorWatchState.REFUSED, emptySet(), emptySet(), listOf("noted: bad"))
        assertEquals("MIUI op 10021: REFUSED, accepted=[], confirmed=[], refused=[noted: bad]", VendorWatches.describe(watch))
    }

    private fun registration(
        active: Boolean,
        noted: Boolean,
        started: Boolean,
        vendor: List<VendorWatchOutcome>,
    ) = AppOpsMonitorClient.Registration(active, noted, started, true, emptyList(), vendor)
}
