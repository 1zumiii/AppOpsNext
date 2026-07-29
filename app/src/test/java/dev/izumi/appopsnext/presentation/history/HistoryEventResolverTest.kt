package dev.izumi.appopsnext.presentation.history

import dev.izumi.appopsnext.apps.model.InstalledApp
import dev.izumi.appopsnext.history.model.AppOpHistoryEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryEventResolverTest {
    private val userApp = app(
        packageName = "com.example.user",
        uid = 10_123,
        isSystemApp = false,
    )
    private val systemApp = app(
        packageName = "com.example.system",
        uid = 10_124,
        isSystemApp = true,
    )

    @Test
    fun `hides system app history when the shared setting is enabled`() {
        val result = HistoryEventResolver.resolve(
            events = listOf(
                event(userApp),
                event(systemApp),
            ),
            installedApps = listOf(userApp, systemApp),
            hideSystemApps = true,
        )

        assertEquals(
            listOf(userApp.packageName),
            result.map { it.app.packageName },
        )
    }

    @Test
    fun `keeps system app history when the shared setting is disabled`() {
        val result = HistoryEventResolver.resolve(
            events = listOf(
                event(userApp),
                event(systemApp),
            ),
            installedApps = listOf(userApp, systemApp),
            hideSystemApps = false,
        )

        assertEquals(2, result.size)
    }

    @Test
    fun `rejects stale events whose package uid no longer matches`() {
        val result = HistoryEventResolver.resolve(
            events = listOf(
                event(userApp).copy(uid = 10_999),
            ),
            installedApps = listOf(userApp),
            hideSystemApps = false,
        )

        assertEquals(emptyList<Any>(), result)
    }

    private fun app(
        packageName: String,
        uid: Int,
        isSystemApp: Boolean,
    ) = InstalledApp(
        label = packageName,
        packageName = packageName,
        uid = uid,
        isSystemApp = isSystemApp,
    )

    private fun event(app: InstalledApp) = AppOpHistoryEvent(
        uid = app.uid,
        packageName = app.packageName,
        operationName = "READ_CLIPBOARD",
        attributionTag = null,
        accessTimeMillis = 1L,
        durationMillis = null,
        uidState = "top",
        flags = "s",
    )
}
