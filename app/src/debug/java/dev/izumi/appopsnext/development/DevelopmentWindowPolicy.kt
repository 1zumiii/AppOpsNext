package dev.izumi.appopsnext.development

import android.view.Window
import android.view.WindowManager

/**
 * Window behavior used only while exercising debug builds on a physical device.
 */
object DevelopmentWindowPolicy {
    fun apply(window: Window) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
