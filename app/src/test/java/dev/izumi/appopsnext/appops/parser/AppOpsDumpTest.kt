package dev.izumi.appopsnext.appops.parser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppOpsDumpTest {
    @Test fun `header with or without the Current prefix`() {
        assertTrue(AppOpsDump.isHeader("Current AppOps Service state:"))
        assertTrue(AppOpsDump.isHeader("AppOps Service state:  "))
        for (line in listOf("", "Permission Denial", "AppOps Service state", "Current AppOps Service")) {
            assertFalse(AppOpsDump.isHeader(line))
        }
    }
}
