package dev.izumi.appopsnext.appops.parser

data class RuntimePermissionState(
    val granted: Boolean,
    val revokedCompat: Boolean,
)

/**
 * Reads one user's runtime permission row from `dumpsys package <package>`.
 *
 * ROMs place the `runtime permissions:` block either inside the per-user
 * package state or in a later per-user section, so a row belongs to the most
 * recent `User N:` header. Install permissions are not per user and are
 * skipped.
 */
class RuntimePermissionStateParser {
    fun parse(
        output: String,
        userId: Int,
        permissionName: String,
    ): RuntimePermissionState? {
        var currentUser: Int? = null
        output.lineSequence().forEach { line ->
            UserHeader.matchEntire(line)?.let { match ->
                currentUser = match.groupValues[1].toInt()
                return@forEach
            }
            if (line.trim() == INSTALL_PERMISSIONS_HEADER) {
                currentUser = null
                return@forEach
            }
            if (currentUser != userId) return@forEach

            val match = PermissionRow.matchEntire(line) ?: return@forEach
            if (match.groupValues[1] != permissionName) return@forEach
            val flags = match.groupValues[3]
                .split('|')
                .map { it.trim() }
            return RuntimePermissionState(
                granted = match.groupValues[2] == "true",
                revokedCompat = REVOKED_COMPAT_FLAG in flags,
            )
        }
        return null
    }

    private companion object {
        const val INSTALL_PERMISSIONS_HEADER = "install permissions:"
        const val REVOKED_COMPAT_FLAG = "REVOKED_COMPAT"
        val UserHeader = Regex("""\s*User (\d+):.*""")
        val PermissionRow = Regex(
            """\s*(\S+): granted=(true|false)(?:, flags=\[\s*([^\]]*)])?.*""",
        )
    }
}
