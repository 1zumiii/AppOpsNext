package dev.izumi.appopsnext.model

data class DeviceSummary(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val apiLevel: Int,
)

