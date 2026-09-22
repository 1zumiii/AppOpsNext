package dev.izumi.appopsnext.presentation.experimental

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.izumi.appopsnext.AppOpsNextApplication
import dev.izumi.appopsnext.appops.AppOpsWatchersRepository
import dev.izumi.appopsnext.appops.parser.ModeWatcher
import dev.izumi.appopsnext.apps.model.InstalledApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WatcherOwner(
    val uid: Int,
    val pid: Int,
    /** Shared UIDs cannot identify a single owning package. Keep every candidate. */
    val apps: List<InstalledApp>,
    val registrations: List<ModeWatcher>,
)

data class WatchersUiState(
    val loading: Boolean = false,
    val unavailable: Boolean = false,
    val incomplete: Boolean = false,
    val capturedAtMillis: Long? = null,
    val owners: List<WatcherOwner> = emptyList(),
)

class WatchersViewModel(application: Application) : AndroidViewModel(application) {
    private val app = getApplication<AppOpsNextApplication>()
    private val repository = AppOpsWatchersRepository(app.privilegedServiceClient)
    private val mutableState = MutableStateFlow(WatchersUiState())
    val uiState = mutableState.asStateFlow()

    fun refresh() {
        if (mutableState.value.loading) return
        // A failed refresh must not keep an old list labelled as current.
        mutableState.value = WatchersUiState(loading = true)
        viewModelScope.launch {
            try {
                val snapshot = repository.read()
                check(snapshot.recognized) { "Unrecognized watcher format" }
                val capturedAt = System.currentTimeMillis()
                val apps = app.installedAppsRepository.loadInstalledApps().groupBy { it.uid }
                mutableState.value = WatchersUiState(
                    capturedAtMillis = capturedAt,
                    incomplete = snapshot.malformedModeWatchers,
                    owners = snapshot.modeWatchers.groupBy { it.callerUid to it.callerPid }
                        .map { (owner, rows) ->
                            WatcherOwner(owner.first, owner.second, apps[owner.first].orEmpty(), rows)
                        }.sortedWith(compareBy<WatcherOwner> { it.uid < 10_000 }.thenBy { it.uid }.thenBy { it.pid }),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.value = WatchersUiState(unavailable = true)
                app.diagnosticLogRepository.warning("Watchers", "Unable to read mode watchers: ${error.message}")
            }
        }
    }
}
