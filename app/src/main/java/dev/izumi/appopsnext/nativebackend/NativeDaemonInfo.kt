package dev.izumi.appopsnext.nativebackend

data class NativeDaemonInfo(
    val protocolVersion: Int,
    val uid: Int,
    val pid: Int,
)
