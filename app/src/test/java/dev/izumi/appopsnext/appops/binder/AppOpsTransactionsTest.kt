package dev.izumi.appopsnext.appops.binder

import org.junit.Assert.assertEquals
import org.junit.Test

class AppOpsTransactionsTest {
    @Test fun `API 35 and 36 use the original positions`() {
        for (sdk in listOf(35, 36)) {
            assertEquals(18, AppOpsTransactions.fallbackCode("getPackagesForOps", sdk))
            assertEquals(19, AppOpsTransactions.fallbackCode("getOpsForPackage", sdk))
            assertEquals(29, AppOpsTransactions.fallbackCode("getUidOps", sdk))
            assertEquals(37, AppOpsTransactions.fallbackCode("startWatchingActive", sdk))
            assertEquals(44, AppOpsTransactions.fallbackCode("stopWatchingNoted", sdk))
        }
    }

    @Test fun `API 37 shifts every position from 12 to 54 by one`() {
        assertEquals(19, AppOpsTransactions.fallbackCode("getPackagesForOps", 37))
        assertEquals(20, AppOpsTransactions.fallbackCode("getOpsForPackage", 37))
        assertEquals(30, AppOpsTransactions.fallbackCode("getUidOps", 37))
        assertEquals(38, AppOpsTransactions.fallbackCode("startWatchingActive", 37))
        assertEquals(39, AppOpsTransactions.fallbackCode("stopWatchingActive", 37))
        assertEquals(43, AppOpsTransactions.fallbackCode("stopWatchingStarted", 37))
        assertEquals(45, AppOpsTransactions.fallbackCode("stopWatchingNoted", 37))
    }

    @Test fun `mode watch methods sit before the shifted range`() {
        for (sdk in listOf(35, 36, 37)) {
            assertEquals(5, AppOpsTransactions.fallbackCode("startWatchingMode", sdk))
            assertEquals(6, AppOpsTransactions.fallbackCode("stopWatchingMode", sdk))
        }
    }
}
