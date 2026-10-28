package dev.izumi.appopsnext.appops.parser

object AppOpsDump {
    /** Some ROMs (seen on ColorOS with Android 17) print the header without the `Current ` prefix. */
    fun isHeader(line: String): Boolean =
        line.trim().removePrefix("Current ") == "AppOps Service state:"
}
