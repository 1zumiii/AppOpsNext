package dev.izumi.appopsnext.history

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Coalesces refreshes; hidden/background/disconnected screens do no polling. */
internal class HistoryRefreshController(
    scope: CoroutineScope,
    private val intervalMillis: Long,
    private val refresh: suspend () -> Unit,
) {
    private var visible = false
    private var foreground = false
    private var connected = false
    private val active = MutableStateFlow(false)
    private val requests = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            active.collectLatest { enabled ->
                if (enabled) coroutineScope {
                    launch {
                        while (isActive) {
                            delay(intervalMillis)
                            requestRefresh()
                        }
                    }
                    requests.trySend(Unit)
                    for (request in requests) refresh()
                }
            }
        }
    }

    fun setVisible(value: Boolean) { visible = value; updateActive() }
    fun setForeground(value: Boolean) { foreground = value; updateActive() }
    fun setConnected(value: Boolean) { connected = value; updateActive() }
    fun requestRefresh() { if (active.value) requests.trySend(Unit) }
    private fun updateActive() { active.value = visible && foreground && connected }
}
