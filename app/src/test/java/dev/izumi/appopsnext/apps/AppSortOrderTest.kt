package dev.izumi.appopsnext.apps

import dev.izumi.appopsnext.apps.model.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Test

class AppSortOrderTest {
    private fun app(name: String, installed: Long?, updated: Long?) = InstalledApp(
        label = name,
        packageName = "dev.izumi.$name",
        uid = 10_000,
        isSystemApp = false,
        firstInstallTimeMillis = installed,
        lastUpdateTimeMillis = updated,
    )

    // Already in name order, as the repository returns them.
    private val apps = listOf(
        app("a", installed = 100, updated = 500),
        app("b", installed = null, updated = null),
        app("c", installed = 300, updated = 300),
        app("d", installed = 100, updated = 900),
    )

    private fun List<InstalledApp>.labels() = map(InstalledApp::label)

    @Test fun `name order keeps the list as it is`() {
        assertEquals(listOf("a", "b", "c", "d"), AppSortOrder.NAME.sort(apps).labels())
    }

    @Test fun `install time puts the newest first and keeps name order for ties and unknown times`() {
        assertEquals(listOf("c", "a", "d", "b"), AppSortOrder.INSTALL_TIME.sort(apps).labels())
    }

    @Test fun `update time puts the newest first and unknown times last`() {
        assertEquals(listOf("d", "a", "c", "b"), AppSortOrder.UPDATE_TIME.sort(apps).labels())
    }

    @Test fun `an unknown stored name reads as no choice`() {
        assertEquals(null, AppSortOrder.fromName("SIZE"))
        assertEquals(AppSortOrder.UPDATE_TIME, AppSortOrder.fromName("UPDATE_TIME"))
    }
}
