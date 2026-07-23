package dev.izumi.appops.model

data class DeviceSummary(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val apiLevel: Int,
)

