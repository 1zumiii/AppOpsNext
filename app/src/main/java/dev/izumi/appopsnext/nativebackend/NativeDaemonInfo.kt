package dev.izumi.appopsnext.nativebackend

internal data class NativeDaemonInfo(
    val protocolVersion: Int,
    val uid: Int,
    val pid: Int,
)
