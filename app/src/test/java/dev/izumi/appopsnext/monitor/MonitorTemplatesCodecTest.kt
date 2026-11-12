package dev.izumi.appopsnext.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorTemplatesCodecTest {
    private val templates = listOf(
        MonitorTemplate(
            id = "a1",
            name = "Night watch",
            rules = listOf(
                MonitorTemplateRule(
                    "android:camera",
                    throttleSeconds = 90,
                    headsUp = true,
                    outcomes = MonitorOutcomes.REFUSED,
                    backgroundOnly = true,
                ),
                MonitorTemplateRule("android:read_clipboard"),
                MonitorTemplateRule("android:fine_location", headsUp = false, outcomes = MonitorOutcomes.ALLOWED),
            ),
        ),
        MonitorTemplate(id = "b2", name = "Empty for now", rules = emptyList()),
    )

    @Test
    fun `an absent or blank value decodes to no templates`() {
        assertTrue(MonitorTemplatesCodec.decode(null).isEmpty())
        assertTrue(MonitorTemplatesCodec.decode(" \n ").isEmpty())
    }

    @Test
    fun `templates and every rule setting survive a round trip in order`() {
        assertEquals(templates, MonitorTemplatesCodec.decode(MonitorTemplatesCodec.encode(templates)))
    }

    @Test
    fun `names with spaces, line breaks and any other characters survive`() {
        val names = listOf(
            "  leading and trailing kept inside  ".trim(),
            "two\nlines",
            "tab\there",
            "100% sure + plus & = equals",
            "rule android:camera",
            "template x y",
            "相机与麦克风 · 夜间",
            "emoji 📷 and \\ backslash",
            "@section guard 1",
        )
        val named = names.mapIndexed { index, name ->
            MonitorTemplate("id$index", name, listOf(MonitorTemplateRule("android:camera")))
        }
        val encoded = MonitorTemplatesCodec.encode(named)
        assertEquals(named, MonitorTemplatesCodec.decode(encoded))
        // Every template is one header line and one rule line, whatever its name holds.
        assertEquals(names.size * 2, encoded.lines().size)
    }

    @Test
    fun `a malformed rule is dropped without losing the rest of the template`() {
        val decoded = MonitorTemplatesCodec.decode(
            """
            template t1 Mine
            rule
            rule android:camera headsup=on throttle=abc outcome=nonsense
            rule : headsup=on
            rule android:read_sms background=on
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                MonitorTemplate(
                    "t1",
                    "Mine",
                    listOf(
                        MonitorTemplateRule("android:camera", headsUp = true),
                        MonitorTemplateRule("android:read_sms", backgroundOnly = true),
                    ),
                ),
            ),
            decoded,
        )
    }

    @Test
    fun `a malformed template line takes its rules with it but not the next template`() {
        val decoded = MonitorTemplatesCodec.decode(
            """
            rule android:camera
            template t1 First
            rule android:camera
            template broken
            rule android:record_audio
            template t2 %20%20
            rule android:read_sms
            future line kind
            template t3 Third
            rule android:read_contacts
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                MonitorTemplate("t1", "First", listOf(MonitorTemplateRule("android:camera"))),
                MonitorTemplate("t3", "Third", listOf(MonitorTemplateRule("android:read_contacts"))),
            ),
            decoded,
        )
    }

    @Test
    fun `a name that does not decode is kept as written`() {
        val decoded = MonitorTemplatesCodec.decode("template t1 100%zz")
        assertEquals(listOf(MonitorTemplate("t1", "100%zz", emptyList())), decoded)
    }

    @Test
    fun `duplicate ids and duplicate operations keep the first`() {
        val decoded = MonitorTemplatesCodec.decode(
            """
            template t1 One
            rule android:camera headsup=on
            rule CAMERA headsup=off
            template t1 Again
            rule android:read_sms
            """.trimIndent(),
        )
        assertEquals(
            listOf(MonitorTemplate("t1", "One", listOf(MonitorTemplateRule("android:camera", headsUp = true)))),
            decoded,
        )
    }

    @Test
    fun `operation names are stored in their stable spelling`() {
        val decoded = MonitorTemplatesCodec.decode("template t1 One\nrule FINE_LOCATION")
        assertEquals("android:fine_location", decoded.single().rules.single().operationName)
    }

    @Test
    fun `built-in templates are never written or read`() {
        val builtIn = BuiltInMonitorTemplates.all.first()
        val encoded = MonitorTemplatesCodec.encode(listOf(builtIn) + templates)
        assertFalse(encoded.contains(builtIn.id))
        assertEquals(templates, MonitorTemplatesCodec.decode(encoded))
        val forged = "template ${BuiltInMonitorTemplates.CAMERA_MICROPHONE} Fake\nrule android:camera"
        assertTrue(MonitorTemplatesCodec.decode(forged).isEmpty())
    }
}
