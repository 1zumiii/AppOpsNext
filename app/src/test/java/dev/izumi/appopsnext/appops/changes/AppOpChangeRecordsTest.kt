package dev.izumi.appopsnext.appops.changes

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpIdentifier
import dev.izumi.appopsnext.appops.model.AppOpScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppOpChangeRecordsTest {
    private fun change(
        original: AppOpMode,
        applied: AppOpMode,
        scope: AppOpScope = AppOpScope.PACKAGE,
        packageName: String = PACKAGE,
    ) = AppOpModeChange(
        packageName = packageName,
        userId = null,
        operation = AppOpIdentifier("RUN_IN_BACKGROUND", "RUN_IN_BACKGROUND"),
        scope = scope,
        originalMode = original,
        appliedMode = applied,
    )

    private fun record(
        recorded: AppOpMode,
        scope: AppOpScope = AppOpScope.PACKAGE,
        time: Long = 100,
        packageName: String = PACKAGE,
        userId: Int = 0,
        operation: String = OPERATION,
    ) = AppOpChangeRecord(
        userId = userId,
        packageName = packageName,
        stableOperationName = operation,
        scope = scope,
        originalMode = AppOpMode.DEFAULT,
        recordedMode = recorded,
        timeMillis = time,
    )

    @Test fun `a first change records the mode before and after under the stable name`() {
        val records = AppOpChangeRecords.merge(
            emptyList(), change(AppOpMode.ALLOW, AppOpMode.IGNORE), userId = 0, nowMillis = 5,
        )
        assertEquals(
            listOf(
                AppOpChangeRecord(
                    0, PACKAGE, OPERATION, AppOpScope.PACKAGE,
                    originalMode = AppOpMode.ALLOW, recordedMode = AppOpMode.IGNORE, timeMillis = 5,
                ),
            ),
            records,
        )
    }

    @Test fun `a later change keeps the first original mode`() {
        val first = AppOpChangeRecords.merge(
            emptyList(), change(AppOpMode.ALLOW, AppOpMode.IGNORE), userId = 0, nowMillis = 5,
        )
        // Changed elsewhere to deny in between, then set to foreground here.
        val second = AppOpChangeRecords.merge(
            first, change(AppOpMode.DENY, AppOpMode.FOREGROUND), userId = 0, nowMillis = 9,
        )
        val record = second.single()
        assertEquals(AppOpMode.ALLOW, record.originalMode)
        assertEquals(AppOpMode.FOREGROUND, record.recordedMode)
        assertEquals(9L, record.timeMillis)
    }

    @Test fun `a change back to the original mode removes the record`() {
        val first = AppOpChangeRecords.merge(
            emptyList(), change(AppOpMode.ALLOW, AppOpMode.IGNORE), userId = 0, nowMillis = 5,
        )
        assertTrue(
            AppOpChangeRecords.merge(
                first, change(AppOpMode.IGNORE, AppOpMode.ALLOW), userId = 0, nowMillis = 9,
            ).isEmpty(),
        )
    }

    @Test fun `scopes are separate settings`() {
        val records = AppOpChangeRecords.merge(
            AppOpChangeRecords.merge(
                emptyList(), change(AppOpMode.ALLOW, AppOpMode.IGNORE), userId = 0, nowMillis = 5,
            ),
            change(AppOpMode.DEFAULT, AppOpMode.IGNORE, scope = AppOpScope.UID),
            userId = 0,
            nowMillis = 6,
        )
        assertEquals(setOf(AppOpScope.PACKAGE, AppOpScope.UID), records.map { it.scope }.toSet())
    }

    @Test fun `the log keeps the newest records once it is full`() {
        var records = emptyList<AppOpChangeRecord>()
        repeat(AppOpChangeRecords.MAX_RECORDS + 1) { index ->
            records = AppOpChangeRecords.merge(
                records,
                change(AppOpMode.ALLOW, AppOpMode.IGNORE, packageName = "p$index"),
                userId = 0,
                nowMillis = index.toLong(),
            )
        }
        assertEquals(AppOpChangeRecords.MAX_RECORDS, records.size)
        assertTrue(records.none { it.packageName == "p0" })
    }

    @Test fun `a mode still as recorded reads as modified`() {
        val status = AppOpChangeRecords.status(listOf(record(AppOpMode.IGNORE)), "RUN_IN_BACKGROUND") {
            AppOpMode.IGNORE
        }
        assertTrue(status is AppOpChangeStatus.Modified)
    }

    @Test fun `a different mode at the recorded scope reads as changed elsewhere`() {
        val status = AppOpChangeRecords.status(
            listOf(record(AppOpMode.IGNORE, scope = AppOpScope.UID)),
            "android:run_in_background",
        ) { scope -> if (scope == AppOpScope.UID) AppOpMode.ALLOW else AppOpMode.IGNORE }
        assertEquals(
            AppOpChangeStatus.ChangedElsewhere(
                record(AppOpMode.IGNORE, scope = AppOpScope.UID),
                currentMode = AppOpMode.ALLOW,
            ),
            status,
        )
    }

    @Test fun `a change elsewhere at one scope outweighs another scope still as recorded`() {
        val status = AppOpChangeRecords.status(
            listOf(
                record(AppOpMode.IGNORE, scope = AppOpScope.PACKAGE, time = 200),
                record(AppOpMode.IGNORE, scope = AppOpScope.UID, time = 100),
            ),
            "RUN_IN_BACKGROUND",
        ) { scope -> if (scope == AppOpScope.UID) null else AppOpMode.IGNORE }
        status as AppOpChangeStatus.ChangedElsewhere
        assertEquals(AppOpScope.UID, status.record.scope)
        assertNull(status.currentMode)
    }

    @Test fun `other operations do not count`() {
        assertNull(
            AppOpChangeRecords.status(
                listOf(record(AppOpMode.IGNORE, operation = "android:read_clipboard")),
                "RUN_IN_BACKGROUND",
            ) { AppOpMode.ALLOW },
        )
    }

    @Test fun `a package installed after the change has no records`() {
        val records = listOf(record(AppOpMode.IGNORE, time = 100))
        assertEquals(1, AppOpChangeRecords.forPackage(records, 0, PACKAGE, firstInstallTimeMillis = 50).size)
        assertEquals(1, AppOpChangeRecords.forPackage(records, 0, PACKAGE, firstInstallTimeMillis = null).size)
        assertTrue(AppOpChangeRecords.forPackage(records, 0, PACKAGE, firstInstallTimeMillis = 150).isEmpty())
        assertTrue(AppOpChangeRecords.forPackage(records, 10, PACKAGE, firstInstallTimeMillis = null).isEmpty())
    }

    @Test fun `latest time can be limited to one operation`() {
        val records = listOf(
            record(AppOpMode.IGNORE, time = 300, operation = "android:read_clipboard"),
            record(AppOpMode.IGNORE, time = 100),
        )
        assertEquals(300L, AppOpChangeRecords.latestTime(records, 0, PACKAGE, null))
        assertEquals(
            100L,
            AppOpChangeRecords.latestTime(records, 0, PACKAGE, null, operationName = "RUN_IN_BACKGROUND"),
        )
        assertNull(AppOpChangeRecords.latestTime(records, 0, "other", null))
    }

    @Test fun `prune drops removed and reinstalled packages of one user only`() {
        val records = listOf(
            record(AppOpMode.IGNORE, packageName = "kept", time = 100),
            record(AppOpMode.IGNORE, packageName = "removed", time = 100),
            record(AppOpMode.IGNORE, packageName = "reinstalled", time = 100),
            record(AppOpMode.IGNORE, packageName = "removed", time = 100, userId = 10),
        )
        val pruned = AppOpChangeRecords.prune(
            records,
            userId = 0,
            installed = mapOf("kept" to 50L, "reinstalled" to 150L),
        )
        assertEquals(
            listOf("kept" to 0, "removed" to 10),
            pruned.map { it.packageName to it.userId },
        )
    }

    @Test fun `records survive encoding and malformed ones are dropped`() {
        val original = record(AppOpMode.FOREGROUND, scope = AppOpScope.UID, time = 1_700_000_000_000)
        assertEquals(original, AppOpChangeCodec.decode(AppOpChangeCodec.encode(original)))
        assertNull(AppOpChangeCodec.decode("C1\t0\tpkg"))
        assertNull(AppOpChangeCodec.decode("C2\t0\tpkg\top\tUID\tALLOW\tIGNORE\t1"))
        assertNull(AppOpChangeCodec.decode("C1\t0\tpkg\top\tUID\tALLOW\tMAYBE\t1"))
        assertNull(AppOpChangeCodec.decode("C1\t-1\tpkg\top\tUID\tALLOW\tIGNORE\t1"))
    }

    private companion object {
        const val PACKAGE = "com.example.target"
        const val OPERATION = "android:run_in_background"
    }
}
