package dev.izumi.appopsnext.appops

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpIdentifier
import dev.izumi.appopsnext.appops.model.AppOpModeChangePhase
import dev.izumi.appopsnext.appops.model.AppOpModeChangeResult
import dev.izumi.appopsnext.appops.model.AppOpsRestorationStatus
import dev.izumi.appopsnext.appops.model.ShellCommandResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RevokedCompatWriteTest {
    @Test
    fun `ignore sets the flag and verifies the derived uid mode`() = runBlocking {
        val gateway = MappedRuntimeOpGateway(granted = true, revokedCompat = false)

        val result = apply(gateway, AppOpMode.IGNORE)

        assertEquals(
            AppOpModeChangeResult.Success(AppOpMode.ALLOW, AppOpMode.IGNORE),
            result,
        )
        assertTrue(gateway.revokedCompat)
        assertEquals(listOf(Write(0, true)), gateway.flagWrites)
    }

    @Test
    fun `allow clears the flag and reports the mode android derives`() = runBlocking {
        val gateway = MappedRuntimeOpGateway(
            granted = true,
            revokedCompat = true,
            grantedMode = AppOpMode.FOREGROUND,
        )

        val result = apply(gateway, AppOpMode.ALLOW)

        assertEquals(
            AppOpModeChangeResult.Success(AppOpMode.IGNORE, AppOpMode.FOREGROUND),
            result,
        )
        assertFalse(gateway.revokedCompat)
    }

    @Test
    fun `the flag is never used to grant a denied permission`() = runBlocking {
        val gateway = MappedRuntimeOpGateway(granted = false, revokedCompat = true)

        assertNull(apply(gateway, AppOpMode.ALLOW))
        assertTrue(gateway.flagWrites.isEmpty())
    }

    @Test
    fun `a flag that already matches is left alone`() = runBlocking {
        val gateway = MappedRuntimeOpGateway(granted = true, revokedCompat = false)

        assertNull(apply(gateway, AppOpMode.ALLOW))
        assertTrue(gateway.flagWrites.isEmpty())
    }

    @Test
    fun `deny is not expressible through the flag`() = runBlocking {
        val gateway = MappedRuntimeOpGateway(granted = true, revokedCompat = false)

        assertNull(apply(gateway, AppOpMode.DENY))
        assertTrue(gateway.flagWrites.isEmpty())
    }

    @Test
    fun `an ignored flag is cleared again after failed verification`() = runBlocking {
        // A ROM that stores app ops separately does not derive them from the flag.
        val gateway = MappedRuntimeOpGateway(
            granted = true,
            revokedCompat = false,
            derivesFromFlag = false,
        )

        val result = apply(gateway, AppOpMode.IGNORE)

        assertEquals(
            AppOpModeChangeResult.Failure(
                phase = AppOpModeChangePhase.VERIFY_REQUESTED,
                originalMode = AppOpMode.ALLOW,
                observedMode = AppOpMode.ALLOW,
                restorationStatus = AppOpsRestorationStatus.SUCCEEDED,
            ),
            result,
        )
        assertFalse(gateway.revokedCompat)
        assertEquals(listOf(Write(0, true), Write(0, false)), gateway.flagWrites)
    }

    @Test
    fun `a failed flag write reports failed restoration when the reset also fails`() = runBlocking {
        val gateway = MappedRuntimeOpGateway(granted = true, revokedCompat = false)
        gateway.failFlagWrites = true

        val result = apply(gateway, AppOpMode.IGNORE)

        assertEquals(
            AppOpModeChangeResult.Failure(
                phase = AppOpModeChangePhase.RESTORE_ORIGINAL,
                originalMode = AppOpMode.ALLOW,
                observedMode = null,
                restorationStatus = AppOpsRestorationStatus.FAILED,
            ),
            result,
        )
    }

    @Test
    fun `the user comes from the target uid`() = runBlocking {
        val gateway = MappedRuntimeOpGateway(
            granted = true,
            revokedCompat = false,
            userId = 10,
        )

        val result = AppOpsRepository(gateway).withWriteTransaction {
            it.applyRevokedCompat(PACKAGE, 1_010_123, OPERATION, PERMISSION, AppOpMode.IGNORE)
        }

        assertTrue(result is AppOpModeChangeResult.Success)
        assertEquals(listOf(Write(10, true)), gateway.flagWrites)
    }

    private suspend fun apply(
        gateway: MappedRuntimeOpGateway,
        mode: AppOpMode,
    ): AppOpModeChangeResult? =
        AppOpsRepository(gateway).withWriteTransaction {
            it.applyRevokedCompat(PACKAGE, 10_123, OPERATION, PERMISSION, mode)
        }

    private data class Write(val userId: Int, val revoked: Boolean)

    /** Mirrors Android 16 with runtime permission app op mapping enabled. */
    private class MappedRuntimeOpGateway(
        var granted: Boolean,
        var revokedCompat: Boolean,
        private val grantedMode: AppOpMode = AppOpMode.ALLOW,
        private val derivesFromFlag: Boolean = true,
        private val userId: Int = 0,
    ) : PrivilegedAppOpsGateway {
        val flagWrites = mutableListOf<Write>()
        var failFlagWrites = false
        private val originalRevokedCompat = revokedCompat

        private val uidMode: AppOpMode
            get() {
                val revoked = if (derivesFromFlag) revokedCompat else originalRevokedCompat
                return if (granted && !revoked) grantedMode else AppOpMode.IGNORE
            }

        override suspend fun getPackageOps(packageName: String) = error("unused")

        override suspend fun getPackageOp(packageName: String, operationName: String) =
            ShellCommandResult(
                0,
                "Uid mode: COARSE_LOCATION: ${uidMode.shellValue}\nCOARSE_LOCATION: allow",
                "",
                false,
            )

        override suspend fun getPackagePermissions(packageName: String): ShellCommandResult {
            val flags = if (revokedCompat) "REVOKED_COMPAT|USER_SET" else "USER_SET"
            return ShellCommandResult(
                0,
                """
                Packages:
                  Package [$packageName] (1a2b3c):
                    User $userId: ceDataInode=0 installed=true
                      runtime permissions:
                        $PERMISSION: granted=$granted, flags=[ $flags]
                """.trimIndent(),
                "",
                false,
            )
        }

        override suspend fun setRevokedCompat(
            userId: Int,
            packageName: String,
            permissionName: String,
            revoked: Boolean,
        ): ShellCommandResult {
            flagWrites += Write(userId, revoked)
            if (failFlagWrites) return ShellCommandResult(1, "", "denied", false)
            revokedCompat = revoked
            return ShellCommandResult(0, "", "", false)
        }

        override suspend fun setPackageOpMode(packageName: String, operationName: String, mode: AppOpMode) =
            error("unused")

        override suspend fun setUidOpMode(packageName: String, operationName: String, mode: AppOpMode) =
            error("unused")
    }

    private companion object {
        const val PACKAGE = "com.example.target"
        const val PERMISSION = "android.permission.ACCESS_COARSE_LOCATION"
        val OPERATION = AppOpIdentifier("android:coarse_location", "COARSE_LOCATION")
    }
}
