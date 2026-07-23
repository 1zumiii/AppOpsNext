package dev.izumi.appopsnext.appops.command

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AppOpsCommandsTest {
    @Test
    fun `get package ops builds an argument list without shell concatenation`() {
        assertEquals(
            listOf("/system/bin/cmd", "appops", "get", "dev.izumi.appopsnext"),
            AppOpsCommands.getPackageOps("dev.izumi.appopsnext"),
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
    fun `get uid ops uses a validated numeric uid`() {
        assertEquals(
            listOf("/system/bin/cmd", "appops", "get", "10277"),
            AppOpsCommands.getUidOps(10277),
        )
        assertThrows(IllegalArgumentException::class.java) {
            AppOpsCommands.getUidOps(-1)
        }
    }

    @Test
    fun `get package ops rejects shell metacharacters`() {
        assertThrows(IllegalArgumentException::class.java) {
            AppOpsCommands.getPackageOps("dev.izumi.appopsnext;id")
        }
    }

    @Test
    fun `get one operation uses a validated argument list`() {
        assertEquals(
            listOf(
                "/system/bin/cmd",
                "appops",
                "get",
                "dev.izumi.appopsnext.testtarget",
                "RUN_IN_BACKGROUND",
            ),
            AppOpsCommands.getPackageOp(
                packageName = "dev.izumi.appopsnext.testtarget",
                operationName = "RUN_IN_BACKGROUND",
            ),
        )
    }

    @Test
    fun `set mode only accepts a typed mode`() {
        assertEquals(
            listOf(
                "/system/bin/cmd",
                "appops",
                "set",
                "dev.izumi.appopsnext.testtarget",
                "RUN_IN_BACKGROUND",
                "ignore",
            ),
            AppOpsCommands.setPackageOpMode(
                packageName = "dev.izumi.appopsnext.testtarget",
                operationName = "RUN_IN_BACKGROUND",
                mode = AppOpMode.IGNORE,
            ),
        )
    }

    @Test
    fun `set uid mode keeps uid scope as a separate validated argument`() {
        assertEquals(
            listOf(
                "/system/bin/cmd",
                "appops",
                "set",
                "--uid",
                "dev.izumi.appopsnext.testtarget",
                "CAMERA",
                "ignore",
            ),
            AppOpsCommands.setUidOpMode(
                packageName = "dev.izumi.appopsnext.testtarget",
                operationName = "CAMERA",
                mode = AppOpMode.IGNORE,
            ),
        )
    }

    @Test
    fun `operation rejects shell metacharacters`() {
        assertThrows(IllegalArgumentException::class.java) {
            AppOpsCommands.getPackageOp(
                packageName = "dev.izumi.appopsnext",
                operationName = "CAMERA;id",
            )
        }
    }
}
