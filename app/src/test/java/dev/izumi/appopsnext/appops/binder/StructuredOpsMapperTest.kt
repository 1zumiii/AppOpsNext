package dev.izumi.appopsnext.appops.binder

import dev.izumi.appopsnext.appops.command.AppOpMode
import dev.izumi.appopsnext.appops.model.AppOpScope
import dev.izumi.appopsnext.appops.parser.EffectiveOpState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StructuredOpsMapperTest {
    private val names = mapOf(26 to "CAMERA", 27 to "RECORD_AUDIO", 63 to "RUN_IN_BACKGROUND")
    private val now = 1_000_000_000L

    @Test fun `snapshot lists UID modes before package modes like cmd appops get`() {
        val snapshot = StructuredOpsMapper.packageSnapshot(
            packageName = "com.example",
            uidOps = listOf(RawPackageOps(null, 10123, listOf(RawOpEntry(26, 4)))),
            packageOps = listOf(
                RawPackageOps("com.example", 10123, listOf(RawOpEntry(63, 1, lastAccessTime = now - 121_267))),
                RawPackageOps("com.other", 10123, listOf(RawOpEntry(27, 0))),
            ),
            nameOf = names::get,
            nowMillis = now,
        )!!
        assertEquals(listOf("CAMERA", "RUN_IN_BACKGROUND"), snapshot.entries.map { it.name })
        assertEquals(listOf(AppOpScope.UID, AppOpScope.PACKAGE), snapshot.entries.map { it.scope })
        assertEquals(listOf("foreground", "ignore"), snapshot.entries.map { it.mode })
        assertEquals("time=+2m1s267ms ago", snapshot.entries[1].details)
    }

    @Test fun `an unnamed operation or unknown mode rejects the whole snapshot`() {
        assertNull(
            StructuredOpsMapper.packageSnapshot(
                "com.example", emptyList(),
                listOf(RawPackageOps("com.example", 10123, listOf(RawOpEntry(999, 0)))),
                names::get, now,
            ),
        )
        assertNull(
            StructuredOpsMapper.packageSnapshot(
                "com.example", emptyList(),
                listOf(RawPackageOps("com.example", 10123, listOf(RawOpEntry(26, 9)))),
                names::get, now,
            ),
        )
    }

    @Test fun `details follow the platform duration spelling`() {
        assertEquals("+0ms", StructuredOpsMapper.elapsed(0))
        assertEquals("+1d2h3m4s5ms", StructuredOpsMapper.elapsed(93_784_005))
        assertEquals(
            "time=+5s ago; rejectTime=+1m ago; duration=+250ms",
            StructuredOpsMapper.details(RawOpEntry(26, 0, now - 5_000, now - 60_000, 250), now),
        )
        assertNull(StructuredOpsMapper.details(RawOpEntry(26, 0), now))
    }

    @Test fun `a missing record reads as the default mode`() {
        assertEquals(AppOpMode.DEFAULT, StructuredOpsMapper.mode(emptyList(), 26))
        assertEquals(
            AppOpMode.IGNORE,
            StructuredOpsMapper.mode(listOf(RawPackageOps("p", 1, listOf(RawOpEntry(26, 1)))), 26),
        )
        assertNull(StructuredOpsMapper.mode(listOf(RawPackageOps("p", 1, listOf(RawOpEntry(26, 7)))), 26))
    }

    @Test fun `op states keep only the requested user and operation`() {
        val states = StructuredOpsMapper.opStates(
            code = 26,
            userId = 0,
            packages = listOf(
                RawPackageOps("com.a", 10001, listOf(RawOpEntry(26, 1))),
                RawPackageOps("com.b", 10002, listOf(RawOpEntry(27, 1))),
                RawPackageOps("com.a", 1010001, listOf(RawOpEntry(26, 1))),
            ),
            uidRecords = listOf(
                RawPackageOps(null, 10002, listOf(RawOpEntry(26, 4))),
                RawPackageOps(null, 1010002, listOf(RawOpEntry(26, 1))),
            ),
        )!!
        assertEquals(mapOf(10002 to "foreground"), states.uidModes)
        assertEquals(mapOf((10001 to "com.a") to "ignore"), states.packageModes)
        assertEquals(EffectiveOpState(AppOpMode.FOREGROUND, AppOpScope.UID), states.effective(10002, "com.b"))
    }
}
