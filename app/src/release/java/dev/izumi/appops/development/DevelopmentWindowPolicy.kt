package dev.izumi.appops.development

import android.view.Window

/**
 * Release builds intentionally preserve the user's system screen-timeout policy.
 */
object DevelopmentWindowPolicy {
    @Suppress("UNUSED_PARAMETER")
    fun apply(window: Window) = Unit
}
