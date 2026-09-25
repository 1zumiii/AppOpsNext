package dev.izumi.appopsnext.presentation.history

import dev.izumi.appopsnext.apps.model.InstalledApp
import dev.izumi.appopsnext.history.model.AppOpHistoryEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryAppStatisticsTest {
    private val alpha = app("Alpha", "com.example.alpha", 10_123)
    private val beta = app("Beta", "com.example.beta", 10_124)

    @Test
    fun `groups access counts by app and sorts highest first`() {
        val summaries = HistoryAppStatistics.summarize(
            listOf(
                event(alpha, accessCount = 2, timestamp = 10L),
                event(beta, accessCount = 5, timestamp = 20L),
                event(alpha, accessCount = 4, timestamp = 30L),
            ),
        )

        assertEquals(
            listOf(alpha.packageName, beta.packageName),
            summaries.map { it.app.packageName },
        )
        assertEquals(listOf(6, 5), summaries.map { it.accessCount })
        assertEquals(30L, summaries.first().latestAccessTimeMillis)
    }

    @Test
    fun `denied attempts count toward default activity order and other orders remain available`() {
        val summaries = HistoryAppStatistics.summarize(
            listOf(
                event(alpha, accessCount = 5, timestamp = 10L),
                event(beta, accessCount = 1, rejectCount = 10, timestamp = 20L),
            ),
        )

        assertEquals(listOf(beta, alpha), summaries.map { it.app })
        assertEquals(
            listOf(alpha, beta),
            HistoryAppStatistics.sort(summaries, HistoryAppSort.ACCESSES).map { it.app },
        )
        assertEquals(
            listOf(beta, alpha),
            HistoryAppStatistics.sort(summaries, HistoryAppSort.DENIALS).map { it.app },
        )
        assertEquals(
            listOf(beta, alpha),
            HistoryAppStatistics.sort(summaries, HistoryAppSort.RECENT).map { it.app },
        )
    }

    private fun app(
        label: String,
        packageName: String,
        uid: Int,
    ) = InstalledApp(
        label = label,
        packageName = packageName,
        uid = uid,
        isSystemApp = false,
    )

    private fun event(
        app: InstalledApp,
        accessCount: Int,
        timestamp: Long,
        rejectCount: Int = 0,
    ) = ResolvedHistoryEvent(
        event = AppOpHistoryEvent(
            uid = app.uid,
            packageName = app.packageName,
            operationName = "READ_CLIPBOARD",
            attributionTag = null,
            accessTimeMillis = timestamp,
            durationMillis = null,
            uidState = "top",
            flags = "s",
            accessCount = accessCount,
            rejectCount = rejectCount,
        ),
        app = app,
    )
}
