package dev.izumi.appopsnext.appops.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuntimePermissionStateParserTest {
    private val parser = RuntimePermissionStateParser()

    @Test
    fun `reads runtime rows nested in the per-user package state`() {
        // realme Android 16 layout, from the reporter's dump.
        val output = """
            Packages:
              Package [com.example.target] (1a2b3c):
                User 0: ceDataInode=0 installed=true hidden=false
                  runtime permissions:
                    android.permission.ACCESS_COARSE_LOCATION: granted=true, flags=[ REVOKED_COMPAT|REVOKE_WHEN_REQUESTED|USER_SENSITIVE_WHEN_GRANTED]
                User 10: ceDataInode=0 installed=false hidden=false
                  runtime permissions:
                    android.permission.ACCESS_COARSE_LOCATION: granted=false, flags=[ REVOKE_WHEN_REQUESTED]
        """.trimIndent()

        assertEquals(
            RuntimePermissionState(granted = true, revokedCompat = true),
            parser.parse(output, 0, COARSE_LOCATION),
        )
        assertEquals(
            RuntimePermissionState(granted = false, revokedCompat = false),
            parser.parse(output, 10, COARSE_LOCATION),
        )
    }

    @Test
    fun `reads runtime rows from a later per-user section`() {
        // ASUS Android 15 layout: package state first, permissions later.
        val output = """
                install permissions:
                  android.permission.INTERNET: granted=true
                User 0: ceDataInode=953979 installed=true
                User 10: ceDataInode=0 installed=false
                install permissions:
                  android.permission.CAMERA: granted=true
                User 0: 
                  gids=[3003]
                  runtime permissions:
                    android.permission.CAMERA: granted=true, flags=[ USER_SET|USER_SENSITIVE_WHEN_GRANTED]
                    android.permission.RECORD_AUDIO: granted=false, flags=[ USER_SENSITIVE_WHEN_DENIED]
                User 10: 
                  runtime permissions:
        """.trimIndent()

        assertEquals(
            RuntimePermissionState(granted = true, revokedCompat = false),
            parser.parse(output, 0, "android.permission.CAMERA"),
        )
        assertEquals(
            RuntimePermissionState(granted = false, revokedCompat = false),
            parser.parse(output, 0, "android.permission.RECORD_AUDIO"),
        )
        assertNull(parser.parse(output, 10, "android.permission.CAMERA"))
    }

    @Test
    fun `accepts rows without flags`() {
        val output = """
            User 0:
              runtime permissions:
                android.permission.ACCESS_COARSE_LOCATION: granted=true
        """.trimIndent()

        assertEquals(
            RuntimePermissionState(granted = true, revokedCompat = false),
            parser.parse(output, 0, COARSE_LOCATION),
        )
    }

    @Test
    fun `does not match a flag by prefix`() {
        val output = """
            User 0:
              runtime permissions:
                android.permission.ACCESS_COARSE_LOCATION: granted=true, flags=[ REVOKED_COMPAT_EXTRA]
        """.trimIndent()

        assertEquals(
            RuntimePermissionState(granted = true, revokedCompat = false),
            parser.parse(output, 0, COARSE_LOCATION),
        )
    }

    @Test
    fun `returns null when the permission is absent`() {
        assertNull(parser.parse("User 0:\n  runtime permissions:", 0, COARSE_LOCATION))
    }

    private companion object {
        const val COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION"
    }
}
