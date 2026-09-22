package dev.izumi.appopsnext.appops

import dev.izumi.appopsnext.appops.parser.AppOpsWatcherSnapshot
import dev.izumi.appopsnext.appops.parser.AppOpsWatchersParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads on demand: neither an audit log nor a permanent background poller. */
class AppOpsWatchersRepository(private val gateway: PrivilegedAppOpsGateway) {
    suspend fun read(): AppOpsWatcherSnapshot = withContext(Dispatchers.IO) {
        val result = gateway.getWatchers()
        check(!result.timedOut) { "Watcher query timed out" }
        check(result.exitCode == 0 && result.stderr.isBlank()) { "Watcher query failed (${result.exitCode})" }
        // Never treat a clipped or unexpectedly large response as a complete registry.
        check(result.stdout.length < 512 * 1024) { "Watcher response exceeds the supported size" }
        AppOpsWatchersParser.parse(result.stdout)
    }
}
