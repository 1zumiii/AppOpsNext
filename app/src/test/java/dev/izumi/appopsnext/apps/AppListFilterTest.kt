package dev.izumi.appopsnext.apps

import dev.izumi.appopsnext.apps.model.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Test

class AppListFilterTest {
    private val apps = listOf(
        installedApp(
            label = "Permission Manager",
            packageName = "dev.izumi.appopsnext",
        ),
        installedApp(
            label = "测试目标",
            packageName = "dev.izumi.appopsnext.testtarget",
        ),
        installedApp(
            label = "Camera",
            packageName = "com.android.camera",
            isSystemApp = true,
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
            AppListFilter.apply(apps, "permission manager"),
        )
    }

    @Test
    fun `filters package names and trims the query`() {
        assertEquals(
            apps.take(2),
            AppListFilter.apply(apps, " izumi.appops "),
        )
    }

    @Test
    fun `hides system applications when requested`() {
        assertEquals(
            apps.take(2),
            AppListFilter.apply(
                apps = apps,
                query = "",
                hideSystemApps = true,
            ),
        )
    }

    private fun installedApp(
        label: String,
        packageName: String,
        isSystemApp: Boolean = false,
    ) = InstalledApp(
        label = label,
        packageName = packageName,
        uid = 10_000,
        isSystemApp = isSystemApp,
    )
}
