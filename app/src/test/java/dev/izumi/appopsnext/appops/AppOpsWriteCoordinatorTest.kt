package dev.izumi.appopsnext.appops

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppOpsWriteCoordinatorTest {
    @Test
    fun `the time since the last write counts writes only`(): Unit = runBlocking {
        var now = 1_000L
        val coordinator = AppOpsWriteCoordinator(clock = { now })

        assertNull(coordinator.betweenWrites { it })
        coordinator.serialize { now = 1_500L }
        now = 1_700L
        assertEquals(200L, coordinator.betweenWrites { it })
        now = 2_000L
        // A read between writes is not a write.
        assertEquals(500L, coordinator.betweenWrites { it })
        runCatching { coordinator.serialize { error("Rejected") } }
        assertEquals(0L, coordinator.betweenWrites { it })
    }
}
