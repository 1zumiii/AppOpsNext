package dev.izumi.appopsnext.monitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorTemplateApplierTest {
    private val template = MonitorTemplate(
        id = "t",
        name = "Sensors",
        rules = listOf(
            MonitorTemplateRule("android:camera", headsUp = true, backgroundOnly = true),
            MonitorTemplateRule("android:record_audio", throttleSeconds = 60),
        ),
    )
    private val known = setOf("android:camera", "android:record_audio", "android:read_sms", "android:fine_location")

    private val targets = listOf(
        MonitorTarget("one.app", setOf("android:read_sms")),
        MonitorTarget("other.app", setOf("android:camera")),
    )
    private val points = listOf(
        // Overwritten: the template names this point.
        MonitorPointSettings("one.app", "android:camera", throttleSeconds = 300, outcomes = MonitorOutcomes.REFUSED),
        // Left alone: the template does not name this operation.
        MonitorPointSettings("one.app", "android:read_sms", headsUp = false),
        // Left alone: this app is not chosen.
        MonitorPointSettings("other.app", "android:camera", throttleSeconds = 120),
    )

    private fun apply(
        template: MonitorTemplate = this.template,
        packages: List<String> = listOf("one.app", "new.app"),
    ) = MonitorTemplateApplier.apply(template, packages, targets, points) { it in known }

    @Test
    fun `operations are added to what each chosen app already watches`() {
        val result = apply()
        assertEquals(
            listOf(
                MonitorTarget("new.app", setOf("android:camera", "android:record_audio")),
                MonitorTarget("one.app", setOf("android:read_sms", "android:camera", "android:record_audio")),
                MonitorTarget("other.app", setOf("android:camera")),
            ),
            result.targets,
        )
        assertEquals(listOf("one.app", "new.app"), result.packageNames)
        assertEquals(listOf("android:camera", "android:record_audio"), result.appliedOperations)
    }

    @Test
    fun `the settings of the named points are overwritten and others are untouched`() {
        val result = apply()
        val byKey = result.points.associateBy { it.packageName to it.operationName }
        assertEquals(
            MonitorPointSettings("one.app", "android:camera", headsUp = true, backgroundOnly = true),
            byKey["one.app" to "android:camera"],
        )
        assertEquals(
            MonitorPointSettings("one.app", "android:record_audio", throttleSeconds = 60),
            byKey["one.app" to "android:record_audio"],
        )
        assertEquals(
            MonitorPointSettings("new.app", "android:camera", headsUp = true, backgroundOnly = true),
            byKey["new.app" to "android:camera"],
        )
        assertEquals(points[1], byKey["one.app" to "android:read_sms"])
        assertEquals(points[2], byKey["other.app" to "android:camera"])
        assertEquals(6, result.points.size)
    }

    @Test
    fun `a rule with only default settings clears the point it names`() {
        val plain = MonitorTemplate("p", "Plain", listOf(MonitorTemplateRule("android:camera")))
        val result = apply(plain, listOf("one.app"))
        assertTrue(result.points.none { it.packageName == "one.app" && it.operationName == "android:camera" })
        assertEquals(setOf("android:read_sms", "android:camera"), result.targets.first { it.packageName == "one.app" }.operationNames)
    }

    @Test
    fun `an operation this device does not know is skipped and reported`() {
        val withUnknown = template.copy(
            rules = template.rules + MonitorTemplateRule("android:future_sensor", headsUp = true),
        )
        val result = apply(withUnknown, listOf("new.app"))
        assertEquals(listOf("android:future_sensor"), result.skippedOperations)
        assertEquals(listOf("android:camera", "android:record_audio"), result.appliedOperations)
        assertEquals(
            setOf("android:camera", "android:record_audio"),
            result.targets.first { it.packageName == "new.app" }.operationNames,
        )
        assertTrue(result.points.none { it.operationName == "android:future_sensor" })
    }

    @Test
    fun `nothing changes when every operation is unknown or no app is chosen`() {
        val unknownOnly = MonitorTemplate("u", "Unknown", listOf(MonitorTemplateRule("android:future_sensor")))
        val skipped = apply(unknownOnly)
        assertEquals(targets, skipped.targets)
        assertEquals(points, skipped.points)
        assertEquals(listOf("android:future_sensor"), skipped.skippedOperations)

        val noApps = apply(packages = emptyList())
        assertEquals(targets, noApps.targets)
        assertEquals(points, noApps.points)
    }

    @Test
    fun `applying twice gives the same configuration`() {
        val once = apply()
        val twice = MonitorTemplateApplier.apply(template, listOf("one.app", "new.app"), once.targets, once.points) {
            it in known
        }
        assertEquals(once.targets, twice.targets)
        assertEquals(once.points, twice.points)
    }

    @Test
    fun `the result encodes and decodes unchanged`() {
        val result = apply()
        assertEquals(result.targets, MonitorTargetsCodec.decode(MonitorTargetsCodec.encode(result.targets)))
        assertEquals(result.points, MonitorPointSettingsCodec.decode(MonitorPointSettingsCodec.encode(result.points)))
    }

    @Test
    fun `picking operations keeps existing rules and adds new ones after them`() {
        val picked = template.withOperations(setOf("android:record_audio", "READ_SMS", "android:fine_location"))
        assertEquals(
            listOf(
                MonitorTemplateRule("android:record_audio", throttleSeconds = 60),
                MonitorTemplateRule("android:fine_location"),
                MonitorTemplateRule("android:read_sms"),
            ),
            picked.rules,
        )
    }

    @Test
    fun `built-in templates follow the configuration examples`() {
        val byId = BuiltInMonitorTemplates.all.associateBy(MonitorTemplate::id)
        val location = listOf("android:fine_location", "android:coarse_location")
        assertEquals(
            location.map { MonitorTemplateRule(it, throttleSeconds = 60, headsUp = false, backgroundOnly = true) },
            byId.getValue(BuiltInMonitorTemplates.OWN_USE_LOCATION).rules,
        )
        assertEquals(
            location.map { MonitorTemplateRule(it, throttleSeconds = 60, headsUp = true, backgroundOnly = true) },
            byId.getValue(BuiltInMonitorTemplates.QUIET_LOCATION).rules,
        )
        assertEquals(
            listOf("android:camera", "android:record_audio")
                .map { MonitorTemplateRule(it, headsUp = true, backgroundOnly = true) },
            byId.getValue(BuiltInMonitorTemplates.CAMERA_MICROPHONE).rules,
        )
        assertEquals(
            listOf("android:read_clipboard", "android:read_contacts", "android:read_sms", "android:read_call_log")
                .map(::MonitorTemplateRule),
            byId.getValue(BuiltInMonitorTemplates.PERSONAL_DATA_READS).rules,
        )
        assertTrue(BuiltInMonitorTemplates.all.all { BuiltInMonitorTemplates.isBuiltIn(it.id) })
        // Every built-in operation is one the bundled table can watch.
        assertTrue(
            BuiltInMonitorTemplates.all.flatMap(MonitorTemplate::operationNames)
                .all { AppOpCodes.codeOf(it) != null },
        )
    }
}
