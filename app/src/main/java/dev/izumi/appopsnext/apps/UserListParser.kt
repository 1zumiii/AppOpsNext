package dev.izumi.appopsnext.apps

import dev.izumi.appopsnext.apps.model.AndroidUser
import dev.izumi.appopsnext.apps.model.UserPackage

/** Reads the output of `pm list users`. */
object UserListParser {
    // UserInfo{10:Work profile:1030} running. The name may itself hold colons,
    // so the flags are the last colon-separated field.
    private val userLine = Regex("""UserInfo\{(\d+):(.*):([0-9a-fA-F]+)\}""")

    fun parse(output: String): List<AndroidUser> =
        output.lineSequence()
            .mapNotNull { line ->
                val match = userLine.find(line) ?: return@mapNotNull null
                val (id, name, flags) = match.destructured
                AndroidUser(
                    id = id.toIntOrNull() ?: return@mapNotNull null,
                    name = name,
                    flags = flags.toIntOrNull(16) ?: 0,
                )
            }
            .distinctBy(AndroidUser::id)
            .sortedBy(AndroidUser::id)
            .toList()
}

/** Reads the output of `pm list packages --user <n> -U`. */
object UserPackageListParser {
    private val packageLine = Regex("""^package:(\S+)\s+uid:(\d+)\s*$""")

    fun parse(output: String): List<UserPackage> =
        output.lineSequence()
            .mapNotNull { line ->
                val match = packageLine.find(line.trim()) ?: return@mapNotNull null
                val (packageName, uid) = match.destructured
                UserPackage(packageName, uid.toIntOrNull() ?: return@mapNotNull null)
            }
            .distinctBy(UserPackage::packageName)
            .toList()
}
