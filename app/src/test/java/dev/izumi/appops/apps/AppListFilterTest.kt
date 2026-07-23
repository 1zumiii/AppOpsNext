package dev.izumi.appops.apps

import dev.izumi.appops.apps.model.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Test

class AppListFilterTest {
    private val apps = listOf(
        installedApp(
            label = "AppOps Next",
            packageName = "dev.izumi.appops",
        ),
        installedApp(
            label = "测试目标",
            packageName = "dev.izumi.appops.testtarget",
        ),
        installedApp(
            label = "Camera",
            packageName = "com.android.camera",
        ),
    )

    @Test
    fun `blank query preserves the loaded ordering`() {
        assertEquals(apps, AppListFilter.apply(apps, "  "))
    }

    @Test
    fun `filters labels case insensitively`() {
        assertEquals(
            listOf(apps[0]),
            AppListFilter.apply(apps, "appops next"),
        )
    }

    @Test
    fun `filters package names and trims the query`() {
        assertEquals(
            apps.take(2),
            AppListFilter.apply(apps, " izumi.appops "),
        )
    }

    private fun installedApp(
        label: String,
        packageName: String,
    ) = InstalledApp(
        label = label,
        packageName = packageName,
        uid = 10_000,
        isSystemApp = false,
    )
}
