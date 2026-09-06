package dev.izumi.appopsnext.presentation.history

import org.junit.Assert.*
import org.junit.Test

class HistorySnapshotFreshnessTest {
    @Test fun `returning within interval reuses snapshot but missing expired and future data refresh`() {
        assertTrue(HistorySnapshotFreshness.isFresh(1_000, 1_001, 300_000))
        assertFalse(HistorySnapshotFreshness.isFresh(null, 1_001, 300_000))
        assertFalse(HistorySnapshotFreshness.isFresh(1_000, 301_000, 300_000))
        assertFalse(HistorySnapshotFreshness.isFresh(2_000, 1_000, 300_000))
    }
}
