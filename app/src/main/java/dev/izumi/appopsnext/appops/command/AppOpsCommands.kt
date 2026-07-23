package dev.izumi.appopsnext.appops.command

object AppOpsCommands {
    fun getPackageOps(packageName: String): List<String> {
        validatePackageName(packageName)

        return listOf(
            COMMAND_BINARY,
            APP_OPS_SERVICE,
            GET_COMMAND,
            packageName,
        )
    }

    fun getPackageOp(
        packageName: String,
        operationName: String,
    ): List<String> {
        validatePackageName(packageName)
        validateOperationName(operationName)

        return listOf(
            COMMAND_BINARY,
            APP_OPS_SERVICE,
            GET_COMMAND,
            packageName,
            operationName,
        )
    }

    fun getUidOps(uid: Int): List<String> {
        require(uid >= 0) { "Invalid Android UID" }

        return listOf(
            COMMAND_BINARY,
            APP_OPS_SERVICE,
            GET_COMMAND,
            uid.toString(),
        )
    }

    fun setPackageOpMode(
        packageName: String,
        operationName: String,
        mode: AppOpMode,
    ): List<String> {
        validatePackageName(packageName)
        validateOperationName(operationName)

        return listOf(
            COMMAND_BINARY,
            APP_OPS_SERVICE,
            SET_COMMAND,
            packageName,
            operationName,
            mode.shellValue,
        )
    }

    fun setUidOpMode(
        packageName: String,
        operationName: String,
        mode: AppOpMode,
    ): List<String> {
        validatePackageName(packageName)
        validateOperationName(operationName)

        return listOf(
            COMMAND_BINARY,
            APP_OPS_SERVICE,
            SET_COMMAND,
            UID_OPTION,
            packageName,
            operationName,
            mode.shellValue,
        )
    }

    private fun validatePackageName(packageName: String) {
        require(PackageNameValidator.isValid(packageName)) {
            "Invalid Android package name"
        }
    }

    private fun validateOperationName(operationName: String) {
        require(OperationNameValidator.isValid(operationName)) {
            "Invalid AppOps operation name"
        }
    }

    private const val COMMAND_BINARY = "/system/bin/cmd"
    private const val APP_OPS_SERVICE = "appops"
    private const val GET_COMMAND = "get"
    private const val SET_COMMAND = "set"
    private const val UID_OPTION = "--uid"
}

enum class AppOpMode(
    val shellValue: String,
) {
    ALLOW("allow"),
    IGNORE("ignore"),
    DENY("deny"),
    DEFAULT("default"),
    FOREGROUND("foreground"),
    ;

    companion object {
        fun fromShellValue(value: String): AppOpMode? =
            entries.firstOrNull { it.shellValue == value }
    }
}

internal object PackageNameValidator {
    private val packageNamePattern =
        Regex("""[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)*""")

    fun isValid(value: String): Boolean =
        value.length in 1..MAX_PACKAGE_NAME_LENGTH &&
            packageNamePattern.matches(value)

    private const val MAX_PACKAGE_NAME_LENGTH = 255
}

internal object OperationNameValidator {
    private val operationNamePattern = Regex("""[A-Za-z0-9_.:-]+""")

    fun isValid(value: String): Boolean =
        value.length in 1..MAX_OPERATION_NAME_LENGTH &&
            operationNamePattern.matches(value)

    private const val MAX_OPERATION_NAME_LENGTH = 128
}
