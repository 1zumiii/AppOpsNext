package dev.izumi.appopsnext.appops.changes

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpIdentifier
import dev.izumi.appopsnext.appops.model.AppOpScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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

    @Test fun `the package index gives the same latest time as scanning every record`() {
        val records = listOf(
            record(AppOpMode.IGNORE, time = 400, packageName = "other"),
            record(AppOpMode.IGNORE, time = 300, operation = "android:read_clipboard"),
            record(AppOpMode.IGNORE, time = 200, userId = 10),
            record(AppOpMode.IGNORE, time = 100),
        )
        val index = AppOpChangeRecords.byPackage(records)
        for (userId in listOf(0, 10, 11)) {
            for (packageName in listOf(PACKAGE, "other", "missing")) {
                for (installedAt in listOf(null, 150L, 500L)) {
                    for (operation in listOf(null, "RUN_IN_BACKGROUND")) {
                        assertEquals(
                            AppOpChangeRecords.latestTime(records, userId, packageName, installedAt, operation),
                            AppOpChangeRecords.latestTime(
                                index[userId to packageName].orEmpty(),
                                userId, packageName, installedAt, operation,
                            ),
                        )
                    }
                }
            }
        }
        assertEquals(listOf(300L, 100L), index[0 to PACKAGE]?.map(AppOpChangeRecord::timeMillis))
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

    @Test fun `prune keeps a removed package for a while when asked to`() {
        val records = listOf(
            record(AppOpMode.IGNORE, packageName = "installed"),
            record(AppOpMode.IGNORE, packageName = "gone"),
            record(AppOpMode.IGNORE, packageName = "reinstalled", time = 100),
        )
        val installed = mapOf("installed" to 50L, "reinstalled" to 200L)

        val first = AppOpChangeRecords.prune(records, 0, installed, keepRemovedMillis = 1_000, nowMillis = 5_000)

        assertEquals(
            mapOf("installed" to null, "gone" to 5_000L, "reinstalled" to 5_000L),
            first.associate { it.packageName to it.removedAtMillis },
        )
        // The time runs from the first pass that missed the package, not from the change.
        val later = AppOpChangeRecords.prune(first, 0, installed, keepRemovedMillis = 1_000, nowMillis = 6_000)
        assertEquals(first, later)
        val expired = AppOpChangeRecords.prune(first, 0, installed, keepRemovedMillis = 1_000, nowMillis = 6_001)
        assertEquals(listOf("installed"), expired.map { it.packageName })
        // Without the setting they go at once, kept or not.
        assertEquals(listOf("installed"), AppOpChangeRecords.prune(first, 0, installed).map { it.packageName })
    }

    @Test fun `a record that applies again is no longer marked as removed`() {
        val kept = record(AppOpMode.IGNORE, time = 100).copy(removedAtMillis = 5_000)

        val pruned = AppOpChangeRecords.prune(
            listOf(kept), 0, mapOf(PACKAGE to 50L), keepRemovedMillis = 1_000, nowMillis = 9_000,
        )

        assertEquals(listOf(kept.copy(removedAtMillis = null)), pruned)
    }

    @Test fun `only the settings of an earlier installation are restorable`() {
        val records = listOf(
            record(AppOpMode.IGNORE, time = 100, operation = "android:camera"),
            record(AppOpMode.ALLOW, time = 300, operation = "android:record_audio"),
            record(AppOpMode.IGNORE, time = 100, operation = "android:camera", userId = 10),
            record(AppOpMode.IGNORE, time = 100, operation = "android:camera", packageName = "other"),
        )

        val restorable = AppOpChangeRecords.restorable(records, 0, PACKAGE, firstInstallTimeMillis = 200)

        assertEquals(listOf("android:camera" to 0), restorable.map { it.stableOperationName to it.userId })
    }

    @Test fun `one setting is restored per operation and package scope comes first`() {
        val records = listOf(
            record(AppOpMode.ALLOW, scope = AppOpScope.UID, time = 150),
            record(AppOpMode.IGNORE, scope = AppOpScope.PACKAGE, time = 100),
            record(AppOpMode.DENY, scope = AppOpScope.UID, time = 120, operation = "android:camera"),
            record(AppOpMode.ALLOW, scope = AppOpScope.UID, time = 110, operation = "android:camera"),
        )

        val restorable = AppOpChangeRecords.restorable(records, 0, PACKAGE, firstInstallTimeMillis = 200)

        assertEquals(
            listOf("android:camera" to AppOpMode.DENY, OPERATION to AppOpMode.IGNORE),
            restorable.map { it.stableOperationName to it.recordedMode },
        )
    }

    @Test fun `a change to an app installed again starts from its new original mode`() {
        val earlier = record(AppOpMode.IGNORE).copy(removedAtMillis = 5_000)

        val merged = AppOpChangeRecords.merge(
            listOf(earlier), change(AppOpMode.ALLOW, AppOpMode.IGNORE), userId = 0, nowMillis = 9_000,
        )

        assertEquals(1, merged.size)
        assertEquals(AppOpMode.ALLOW, merged.single().originalMode)
        assertNull(merged.single().removedAtMillis)
    }

    @Test fun `the time a package was removed survives encoding`() {
        val kept = record(AppOpMode.IGNORE).copy(removedAtMillis = 5_000)

        assertEquals(kept, AppOpChangeCodec.decode(AppOpChangeCodec.encode(kept)))
        assertNull(AppOpChangeCodec.decode(AppOpChangeCodec.encode(kept) + "\tx"))
        assertNull(AppOpChangeCodec.decode(AppOpChangeCodec.encode(record(AppOpMode.IGNORE)) + "\tx"))
    }

    @Test fun `a setting changed elsewhere and back is not marked as this app's change`() {
        val records = AppOpChangeRecords.merge(
            emptyList(), change(AppOpMode.ALLOW, AppOpMode.IGNORE, scope = AppOpScope.UID),
            userId = 0, nowMillis = 5,
        )
        fun statusAt(records: List<AppOpChangeRecord>, mode: AppOpMode) =
            AppOpChangeRecords.status(records, OPERATION) { mode }

        assertTrue(statusAt(records, AppOpMode.IGNORE) is AppOpChangeStatus.Modified)
        // System Settings sets "allow only while in use".
        val elsewhere = statusAt(records, AppOpMode.FOREGROUND) as AppOpChangeStatus.ChangedElsewhere
        val flagged = AppOpChangeRecords.markChangedElsewhere(records, listOf(elsewhere.record))
        assertTrue(flagged.single().changedElsewhere)
        assertTrue(statusAt(flagged, AppOpMode.FOREGROUND) is AppOpChangeStatus.ChangedElsewhere)
        // Then "don't allow", the mode this app recorded.
        assertNull(statusAt(flagged, AppOpMode.IGNORE))
        assertNull(AppOpChangeRecords.latestTime(flagged, 0, PACKAGE, firstInstallTimeMillis = 1))
    }

    @Test fun `a write here clears the flag and keeps the first original mode`() {
        val flagged = listOf(record(AppOpMode.IGNORE).copy(changedElsewhere = true))

        val written = AppOpChangeRecords.merge(
            flagged, change(AppOpMode.FOREGROUND, AppOpMode.IGNORE), userId = 0, nowMillis = 900,
        )

        val record = written.single()
        assertEquals(false, record.changedElsewhere)
        assertEquals(AppOpMode.DEFAULT, record.originalMode)
        assertTrue(AppOpChangeRecords.status(written, OPERATION) { AppOpMode.IGNORE } is AppOpChangeStatus.Modified)
        assertEquals(900L, AppOpChangeRecords.latestTime(written, 0, PACKAGE, firstInstallTimeMillis = 1))
    }

    @Test fun `marking leaves a record written again since and an unchanged log alone`() {
        val seen = record(AppOpMode.IGNORE, time = 100)
        val rewritten = record(AppOpMode.IGNORE, time = 200)
        val other = record(AppOpMode.IGNORE, packageName = "other")
        val records = listOf(rewritten, other)

        assertSame(records, AppOpChangeRecords.markChangedElsewhere(records, listOf(seen)))
        val flagged = AppOpChangeRecords.markChangedElsewhere(listOf(seen, other), listOf(seen))
        assertEquals(listOf(true, false), flagged.map(AppOpChangeRecord::changedElsewhere))
        assertSame(flagged, AppOpChangeRecords.markChangedElsewhere(flagged, listOf(seen)))
    }

    @Test fun `another scope still as this app left it keeps the marker`() {
        val records = listOf(
            record(AppOpMode.IGNORE, scope = AppOpScope.PACKAGE, time = 200).copy(changedElsewhere = true),
            record(AppOpMode.IGNORE, scope = AppOpScope.UID, time = 100),
        )

        val status = AppOpChangeRecords.status(records, OPERATION) { AppOpMode.IGNORE }

        assertEquals(AppOpScope.UID, (status as AppOpChangeStatus.Modified).record.scope)
    }

    @Test fun `records written before the flag decode as not changed elsewhere`() {
        val current = AppOpChangeCodec.decode("C1\t0\tpkg\top\tUID\tALLOW\tIGNORE\t1")
        val removed = AppOpChangeCodec.decode("C1\t0\tpkg\top\tUID\tALLOW\tIGNORE\t1\t7")

        assertEquals(false, current?.changedElsewhere)
        assertNull(current?.removedAtMillis)
        assertEquals(false, removed?.changedElsewhere)
        assertEquals(7L, removed?.removedAtMillis)
    }

    @Test fun `the flag survives encoding and other records keep the old format`() {
        val plain = record(AppOpMode.IGNORE)
        val flagged = plain.copy(changedElsewhere = true)
        val flaggedRemoved = flagged.copy(removedAtMillis = 5_000)

        assertEquals("C1\t0\t$PACKAGE\t$OPERATION\tPACKAGE\tDEFAULT\tIGNORE\t100", AppOpChangeCodec.encode(plain))
        assertEquals(flagged, AppOpChangeCodec.decode(AppOpChangeCodec.encode(flagged)))
        assertEquals(flaggedRemoved, AppOpChangeCodec.decode(AppOpChangeCodec.encode(flaggedRemoved)))
        assertNull(AppOpChangeCodec.decode("C2\t0\tpkg\top\tUID\tALLOW\tIGNORE\t1\t\tx"))
        assertNull(AppOpChangeCodec.decode("C2\t0\tpkg\top\tUID\tALLOW\tIGNORE\t1\ty\t1"))
        assertNull(AppOpChangeCodec.decode("C1\t0\tpkg\top\tUID\tALLOW\tIGNORE\t1\t\t1"))
    }

    @Test fun `prune keeps the flag`() {
        val flagged = record(AppOpMode.IGNORE, time = 100).copy(changedElsewhere = true)

        val removed = AppOpChangeRecords.prune(
            listOf(flagged), 0, emptyMap(), keepRemovedMillis = 1_000, nowMillis = 5_000,
        )
        val back = AppOpChangeRecords.prune(removed, 0, mapOf(PACKAGE to 50L), nowMillis = 6_000)

        assertEquals(listOf(true), removed.map(AppOpChangeRecord::changedElsewhere))
        assertEquals(listOf(flagged), back)
    }

    @Test fun `a setting changed elsewhere is restored to the mode this app set`() {
        val records = listOf(
            record(AppOpMode.IGNORE, scope = AppOpScope.PACKAGE, time = 100).copy(changedElsewhere = true),
            record(AppOpMode.ALLOW, scope = AppOpScope.UID, time = 120),
            record(AppOpMode.IGNORE, time = 100, operation = "android:camera"),
        )

        val restorable = AppOpChangeRecords.restorable(records, 0, PACKAGE, firstInstallTimeMillis = 200)

        assertEquals(
            listOf("android:camera" to AppOpMode.IGNORE, OPERATION to AppOpMode.IGNORE),
            restorable.map { it.stableOperationName to it.recordedMode },
        )
    }

    @Test fun `the write that restores a flagged setting clears the flag`() {
        val earlier = record(AppOpMode.IGNORE, time = 100)
            .copy(removedAtMillis = 5_000, changedElsewhere = true)

        val restored = AppOpChangeRecords.merge(
            listOf(earlier), change(AppOpMode.ALLOW, AppOpMode.IGNORE), userId = 0, nowMillis = 9_000,
        )

        assertEquals(false, restored.single().changedElsewhere)
        assertEquals(AppOpMode.IGNORE, restored.single().recordedMode)
        assertNull(restored.single().removedAtMillis)
        assertEquals(9_000L, AppOpChangeRecords.latestTime(restored, 0, PACKAGE, firstInstallTimeMillis = 8_000))
    }

    private companion object {
        const val PACKAGE = "com.example.target"
        const val OPERATION = "android:run_in_background"
    }
}
