package dev.izumi.appopsnext.presentation.app_detail

import dev.izumi.appopsnext.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppOpUsageDetailsFormatterTest {
    @Test
    fun `last use keeps only the two largest non-zero units`() {
        assertEquals(
            "1 小时 30 分前",
            format("time=+1h30m12s345ms ago"),
        )
    }

    @Test
    fun `duration below one second does not expose milliseconds`() {
        assertEquals(
            "2 秒前 · 持续不足 1 秒",
            format("time=+2s15ms ago; duration=+637ms"),
        )
    }

    @Test
    fun `duration and rejected time use user-facing labels`() {
        assertEquals(
            "13 天 48 分前 · 持续 2 秒 · 2 分 15 秒前被拒绝",
            format(
                "time=+13d0h48m21s752ms ago; " +
                    "duration=+2s637ms; rejectTime=+2m15s ago",
            ),
        )
    }

    @Test
    fun `unsupported diagnostic details remain available only in raw data`() {
        assertNull(format("proxyUid=10234"))
    }

    private fun format(rawDetails: String): String? =
        AppOpUsageDetailsFormatter.format(rawDetails, ::resolveString)

    private fun resolveString(
        resourceId: Int,
        arguments: List<Any>,
    ): String {
        val template = when (resourceId) {
            R.string.app_op_usage_separator -> " · "
            R.string.app_op_usage_just_now -> "刚刚"
            R.string.app_op_usage_ago -> "%s前"
            R.string.app_op_usage_duration -> "持续 %s"
            R.string.app_op_usage_duration_less_than_second ->
                "持续不足 1 秒"

            R.string.app_op_usage_rejected_just_now -> "刚刚被拒绝"
            R.string.app_op_usage_rejected_ago -> "%s前被拒绝"
            R.string.app_op_usage_days -> "%d 天"
            R.string.app_op_usage_hours -> "%d 小时"
            R.string.app_op_usage_minutes -> "%d 分"
            R.string.app_op_usage_seconds -> "%d 秒"
            else -> error("Unexpected resource: $resourceId")
        }
        return template.format(*arguments.toTypedArray())
    }
}
