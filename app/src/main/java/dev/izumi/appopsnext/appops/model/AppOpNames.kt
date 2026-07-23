package dev.izumi.appopsnext.appops.model

import java.util.Locale

object AppOpNames {
    fun shellName(operationName: String): String =
        operationName
            .substringAfterLast(':')
            .uppercase(Locale.ROOT)

    fun stableName(operationName: String): String =
        "android:${shellName(operationName).lowercase(Locale.ROOT)}"
}
