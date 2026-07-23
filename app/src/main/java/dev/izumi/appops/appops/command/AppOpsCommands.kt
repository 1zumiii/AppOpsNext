package dev.izumi.appops.appops.command

object AppOpsCommands {
    fun getPackageOps(packageName: String): List<String> {
        require(PackageNameValidator.isValid(packageName)) {
            "Invalid Android package name"
        }

        return listOf(
            COMMAND_BINARY,
            APP_OPS_SERVICE,
            GET_COMMAND,
            packageName,
        )
    }

    private const val COMMAND_BINARY = "/system/bin/cmd"
    private const val APP_OPS_SERVICE = "appops"
    private const val GET_COMMAND = "get"
}

internal object PackageNameValidator {
    private val packageNamePattern =
        Regex("""[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)*""")

    fun isValid(value: String): Boolean =
        value.length in 1..MAX_PACKAGE_NAME_LENGTH &&
            packageNamePattern.matches(value)

    private const val MAX_PACKAGE_NAME_LENGTH = 255
}
