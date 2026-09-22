package dev.izumi.appopsnext.appops.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppOpsWatchersParserTest {
    private val kinds = AccessWatchKind.entries.toSet()
    private val operations = setOf("CAMERA", "RECORD_AUDIO")

    @Test fun `matches every successful watch and operation under the privileged identity`() {
        val snapshot = AppOpsWatchersParser.parse(dump())
        assertEquals(WatchRegistration.CONFIRMED, snapshot.registration(operations, kinds, 2000))
        assertEquals(WatchRegistration.MISSING, snapshot.registration(operations + "READ_CLIPBOARD", kinds, 2000))
        assertEquals(WatchRegistration.MISSING, snapshot.registration(operations, kinds, 0))
    }

    @Test fun `own uid restriction cannot confirm global coverage`() {
        val snapshot = AppOpsWatchersParser.parse(dump().replace("watchinguid=-1", "watchinguid=2000"))
        assertEquals(WatchRegistration.MISSING, snapshot.registration(operations, kinds, 2000))
    }

    @Test fun `missing noted registration is not a successful three way check`() {
        val snapshot = AppOpsWatchersParser.parse(dump(setOf(AccessWatchKind.ACTIVE, AccessWatchKind.STARTED)))
        assertEquals(WatchRegistration.MISSING, snapshot.registration(operations, kinds, 2000))
        assertEquals(WatchRegistration.CONFIRMED,
            snapshot.registration(operations, setOf(AccessWatchKind.ACTIVE), 2000))
    }

    @Test fun `truncated callback and unsupported output remain unknown`() {
        val truncated = AppOpsWatchersParser.parse(dump().substringBeforeLast("NotedCallback"))
        assertEquals(WatchRegistration.UNKNOWN, truncated.registration(operations, kinds, 2000))
        for (output in listOf("", "Unknown option: --watchers", "Permission Denial", "Current AppOps Service state:\n")) {
            assertEquals(WatchRegistration.UNKNOWN,
                AppOpsWatchersParser.parse(output).registration(operations, kinds, 2000))
        }
    }

    @Test fun `does not combine disjoint operation lists from different registrations`() {
        val snapshot = AppOpsWatchersParser.parse("""
            Current AppOps Service state:
              All op active watchers:
                ab ->
                    [CAMERA]
                    ActiveCallback{aa watchinguid=-1 from uid=2000 pid=123}
                cd ->
                    [RECORD_AUDIO]
                    ActiveCallback{bb watchinguid=-1 from uid=2000 pid=456}
        """.trimIndent())
        assertEquals(WatchRegistration.MISSING,
            snapshot.registration(operations, setOf(AccessWatchKind.ACTIVE), 2000))
    }

    @Test fun `malformed lists and wrong callback kind cannot be confirmed`() {
        for (output in listOf(dump().replace("[CAMERA, RECORD_AUDIO]", "[CAMERA, 27]"),
            dump().replace("NotedCallback", "ActiveCallback"))) {
            assertEquals(WatchRegistration.UNKNOWN,
                AppOpsWatchersParser.parse(output).registration(operations, kinds, 2000))
        }
    }

    @Test fun `mode rows merge by callback identity keeping additive scopes`() {
        val snapshot = AppOpsWatchersParser.parse("""
            Current AppOps Service state:
              Op mode watchers:
                Op COARSE_LOCATION:
                  #0: ModeCallback{abc watchinguid=-1 flags=0x1 op=FINE_LOCATION from uid=u0a148 pid=55}
                Op FINE_LOCATION:
                  #0: ModeCallback{abc watchinguid=-1 flags=0x1 op=FINE_LOCATION from uid=u0a148 pid=55}
              Package mode watchers:
                Pkg com.example.location:
                  #0: ModeCallback{abc watchinguid=-1 flags=0x1 op=FINE_LOCATION from uid=u0a148 pid=55}
              All op mode watchers:
                123: ModeCallback{abc watchinguid=-1 flags=0x1 op=FINE_LOCATION from uid=u0a148 pid=55}
                456: ModeCallback{def watchinguid=-1 flags=0x0 op=ALL from uid=1000 pid=1}
        """.trimIndent())
        assertTrue(snapshot.recognized)
        assertFalse(snapshot.malformedModeWatchers)
        assertEquals(2, snapshot.modeWatchers.size)
        val row = snapshot.modeWatchers.first()
        assertEquals(10148, row.callerUid)
        assertEquals(setOf("COARSE_LOCATION", "FINE_LOCATION"), row.operations)
        assertEquals(setOf("com.example.location"), row.packages)
        assertTrue(snapshot.modeWatchers.last().operations.isEmpty())
        assertTrue(snapshot.modeWatchers.last().packages.isEmpty())
    }

    @Test fun `late sections and unrelated restrictions are not clipped or misparsed`() {
        val longDump = dump().replace("  All op active watchers:",
            "  Settings:\n" + "    setting=value\n".repeat(300) + "  All op active watchers:") +
            "\n  User restrictions for token android.os.Binder@abc:\n    Restricted ops:\n      [CAMERA]\n"
        val snapshot = AppOpsWatchersParser.parse(longDump)
        assertEquals(WatchRegistration.CONFIRMED, snapshot.registration(operations, kinds, 2000))
        assertEquals(3, snapshot.accessWatchers.size)
    }

    @Test fun `uid parser rejects overflow and invalid app id`() {
        assertEquals(1010148, UidStateParser.uidOf("u10a148"))
        assertEquals(null, UidStateParser.uidOf("u2147483647a1"))
        assertEquals(null, UidStateParser.uidOf("u0a999999"))
    }

    private fun dump(selected: Set<AccessWatchKind> = kinds): String = buildString {
        appendLine("Current AppOps Service state:")
        for (kind in selected) {
            val title = kind.name.lowercase().replaceFirstChar(Char::uppercaseChar)
            appendLine("  All op ${kind.name.lowercase()} watchers:")
            appendLine("    ab ->")
            appendLine("        [CAMERA, RECORD_AUDIO]")
            appendLine("        ${title}Callback{abc watchinguid=-1 from uid=2000 pid=123}")
        }
    }
}
