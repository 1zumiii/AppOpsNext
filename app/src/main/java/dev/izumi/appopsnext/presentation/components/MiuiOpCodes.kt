package dev.izumi.appopsnext.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.izumi.appopsnext.AppOpsNextApplication
import dev.izumi.appopsnext.monitor.VendorMonitorOps
import kotlinx.coroutines.CancellationException

/**
 * The MIUI private operation codes this device offers, empty until it has
 * shown that it has them. The device is probed only while nothing is known,
 * so a device without these operations is read once per process.
 */
@Composable
fun rememberMiuiOpCodes(): Set<Int> {
    val context = LocalContext.current
    val codes by VendorMonitorOps.codes.collectAsStateWithLifecycle()
    LaunchedEffect(codes == null) {
        if (codes != null) return@LaunchedEffect
        try {
            (context.applicationContext as AppOpsNextApplication).probeVendorOps()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // The probe runs again the next time a screen asks, since nothing was learned.
        }
    }
    return codes.orEmpty()
}
