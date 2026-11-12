package dev.izumi.appopsnext.newapps

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpModeChangeResult
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.batch.model.BatchOperationItemResult
import dev.izumi.appopsnext.batch.model.BatchOperationTarget
import dev.izumi.appopsnext.newapps.model.InstalledPackageFingerprint
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NewAppPolicyStateRepositoryTest {
    private lateinit var directory: File
    private lateinit var scope: CoroutineScope
    private lateinit var repository: NewAppPolicyStateRepository

    private val done = InstalledPackageFingerprint("example.done", 1_000)
    private val waiting = InstalledPackageFingerprint("example.waiting", 2_000)
    private val existing = InstalledPackageFingerprint("example.existing", 500)

    @Before fun setUp() {
        directory = Files.createTempDirectory("new-app-policy").toFile()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        repository = NewAppPolicyStateRepository(
            PreferenceDataStoreFactory.create(scope = scope) {
                File(directory, "new_app_policy.preferences_pb")
            },
        )
    }

    @After fun tearDown() {
        scope.cancel()
        directory.deleteRecursively()
    }

    @Test fun `clearing forgets finished results and keeps what is still waiting`() = runBlocking {
        installBoth()
        repository.recordProgress(progress(done, "android:camera"))
        repository.recordProgress(progress(waiting, "android:camera"))
        repository.markProcessed(done)
        assertEquals(listOf(waiting, done), repository.results.first().map { it.installation })

        val cleared = repository.clearResults()

        assertEquals(setOf(done), cleared)
        assertEquals(listOf(waiting), repository.results.first().map { it.installation })
        assertEquals(1, repository.progress(waiting).size)
        assertTrue(repository.progress(done).isEmpty())
    }

    @Test fun `clearing leaves no result once every installation is done`() = runBlocking {
        installBoth()
        repository.recordProgress(progress(done, "android:camera"))
        repository.recordProgress(progress(waiting, "android:record_audio"))
        repository.markProcessed(done)
        repository.markProcessed(waiting)

        repository.clearResults()

        assertTrue(repository.results.first().isEmpty())
    }

    @Test fun `cleared apps are not taken for new again`() = runBlocking {
        installBoth()
        repository.recordProgress(progress(done, "android:camera"))
        repository.markProcessed(done)

        repository.clearResults()

        assertEquals(listOf(waiting), repository.pending())
        val again = repository.reconcile(listOf(existing, done, waiting))
        assertTrue(again.detected.isEmpty())
        assertEquals(listOf(waiting), repository.pending())
    }

    /** The first scan only records what is there, so the two apps arrive after it. */
    private suspend fun installBoth() {
        repository.reconcile(listOf(existing))
        val detected = repository.reconcile(listOf(existing, done, waiting)).detected
        assertEquals(listOf(done, waiting), detected)
    }

    private fun progress(installation: InstalledPackageFingerprint, operation: String) =
        NewAppRuleProgress(
            installation = installation,
            item = BatchOperationItemResult(
                target = BatchOperationTarget(
                    installation.packageName,
                    "Example",
                    10_123,
                    operation,
                    AppOpScope.PACKAGE,
                    AppOpMode.IGNORE,
                ),
                result = AppOpModeChangeResult.Success(AppOpMode.DEFAULT, AppOpMode.IGNORE),
            ),
        )
}
