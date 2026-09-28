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
    fun `get history uses a validated dumpsys argument list`() {
        assertEquals(
            listOf(
                "/system/bin/dumpsys",
                "appops",
                "--history",
                "--include-discrete",
                "0",
                "--op",
                "CAMERA",
            ),
            AppOpsCommands.getHistory("CAMERA"),
        )
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
                "com.example.target",
                "RUN_IN_BACKGROUND",
            ),
            AppOpsCommands.getPackageOp(
                packageName = "com.example.target",
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
                "com.example.target",
                "RUN_IN_BACKGROUND",
                "ignore",
            ),
            AppOpsCommands.setPackageOpMode(
                packageName = "com.example.target",
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
                "com.example.target",
                "CAMERA",
                "ignore",
            ),
            AppOpsCommands.setUidOpMode(
                packageName = "com.example.target",
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

    @Test
    fun `package permissions use a validated dumpsys argument list`() {
        assertEquals(
            listOf("/system/bin/dumpsys", "package", "dev.izumi.appopsnext"),
            AppOpsCommands.getPackagePermissions("dev.izumi.appopsnext"),
        )
    }

    @Test
    fun `revoked compat commands name the user and only that flag`() {
        assertEquals(
            listOf(
                "/system/bin/cmd",
                "package",
                "set-permission-flags",
                "--user",
                "10",
                "dev.izumi.appopsnext",
                "android.permission.CAMERA",
                "revoked-compat",
            ),
            AppOpsCommands.setRevokedCompat(
                userId = 10,
                packageName = "dev.izumi.appopsnext",
                permissionName = "android.permission.CAMERA",
                revoked = true,
            ),
        )
        assertEquals(
            "clear-permission-flags",
            AppOpsCommands.setRevokedCompat(
                userId = 0,
                packageName = "dev.izumi.appopsnext",
                permissionName = "android.permission.CAMERA",
                revoked = false,
            )[2],
        )
    }

    @Test
    fun `revoked compat commands reject other permissions and users`() {
        for ((userId, permission) in listOf(
            -1 to "android.permission.CAMERA",
            0 to "com.example.permission.CAMERA",
            0 to "android.permission.CAMERA;id",
            0 to "android.permission.camera",
        )) {
            assertThrows(IllegalArgumentException::class.java) {
                AppOpsCommands.setRevokedCompat(
                    userId = userId,
                    packageName = "dev.izumi.appopsnext",
                    permissionName = permission,
                    revoked = true,
                )
            }
        }
    }
}
