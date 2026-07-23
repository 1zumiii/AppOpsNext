package dev.izumi.appops.appops.command

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AppOpsCommandsTest {
    @Test
    fun `get package ops builds an argument list without shell concatenation`() {
        assertEquals(
            listOf("/system/bin/cmd", "appops", "get", "dev.izumi.appops"),
            AppOpsCommands.getPackageOps("dev.izumi.appops"),
        )
    }

    @Test
    fun `get package ops accepts the android framework package`() {
        assertEquals(
            listOf("/system/bin/cmd", "appops", "get", "android"),
            AppOpsCommands.getPackageOps("android"),
        )
    }

    @Test
    fun `get package ops rejects shell metacharacters`() {
        assertThrows(IllegalArgumentException::class.java) {
            AppOpsCommands.getPackageOps("dev.izumi.appops;id")
        }
    }
}
