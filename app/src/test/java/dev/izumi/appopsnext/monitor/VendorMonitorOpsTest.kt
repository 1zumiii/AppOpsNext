package dev.izumi.appopsnext.monitor

import dev.izumi.appopsnext.appops.model.MiuiOps
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VendorMonitorOpsTest {
    private val stock = listOf("CAMERA", "RECORD_AUDIO", "READ_CLIPBOARD")

    @Test fun `a device whose packages show no private operation offers none`() = runBlocking {
        val discovery = VendorOpsDiscovery()
        assertNull(discovery.codes.value)
        val codes = discovery.probe(listOf("a", "b")) { stock }
        assertEquals(emptySet<Int>(), codes)
        assertEquals(emptySet<Int>(), discovery.codes.value)
        assertEquals(emptyList<String>(), MonitorOpCodes.vendorNames(discovery.codes.value))
    }

    @Test fun `packages that cannot be read leave the answer open`() = runBlocking {
        val discovery = VendorOpsDiscovery()
        assertEquals(emptySet<Int>(), discovery.probe(listOf("a")) { null })
        assertNull(discovery.codes.value)
    }

    @Test fun `one private entry offers the catalogue and every code seen`() = runBlocking {
        val discovery = VendorOpsDiscovery()
        val codes = discovery.probe(listOf("a", "b")) { pkg ->
            if (pkg == "b") stock + listOf("10008", "10054") else stock
        }
        assertEquals(MiuiOps.CATALOGUED_CODES.toSet() + 10054, codes)
        assertEquals(codes, discovery.codes.value)
    }

    @Test fun `a later probe reads only packages it has not read`() = runBlocking {
        val discovery = VendorOpsDiscovery()
        val read = mutableListOf<String>()
        discovery.probe(listOf("a")) { read += it; stock }
        discovery.probe(listOf("a", "b")) { read += it; if (it == "b") listOf("10021") else stock }
        assertEquals(listOf("a", "b"), read)
        assertTrue(10021 in discovery.codes.value.orEmpty())
        // Once found, nothing is read again.
        discovery.probe(listOf("c")) { read += it; stock }
        assertEquals(listOf("a", "b"), read)
    }

    @Test fun `private names resolve only on a device that has them`() {
        assertNull(MonitorOpCodes.codeOf("android:10008", null))
        assertNull(MonitorOpCodes.codeOf("android:10008", emptySet()))
        assertEquals(10008, MonitorOpCodes.codeOf("android:10008", setOf(10008)))
        assertNull(MonitorOpCodes.codeOf("android:10009", setOf(10008)))
        assertEquals(26, MonitorOpCodes.codeOf("android:camera", emptySet()))
        assertTrue(MonitorOpCodes.isVendor("android:10008"))
        assertFalse(MonitorOpCodes.isVendor("android:camera"))
    }

    @Test fun `names and codes agree in both directions`() {
        val vendor = MiuiOps.CATALOGUED_CODES.toSet()
        MonitorOpCodes.vendorNames(vendor).forEach { name ->
            assertEquals(name, MonitorOpCodes.codeOf(name, vendor)?.let(MonitorOpCodes::nameOf))
        }
        assertEquals("android:10008", MonitorOpCodes.nameOf(10008))
        assertEquals("android:camera", MonitorOpCodes.nameOf(26))
        assertNull(MonitorOpCodes.nameOf(96))
    }

    @Test fun `a selection splits into standard, private and unknown codes`() {
        val names = listOf("android:camera", "android:10021", "android:record_audio", "android:10008", "android:nope")
        assertEquals(
            MonitorOpCodes.Split(intArrayOf(26, 27), intArrayOf(10008, 10021), listOf("android:nope")),
            MonitorOpCodes.split(names, setOf(10008, 10021)),
        )
        // Without private operations the private names are unknown, as before.
        assertEquals(
            MonitorOpCodes.Split(intArrayOf(26, 27), intArrayOf(), listOf("android:10021", "android:10008", "android:nope")),
            MonitorOpCodes.split(names, emptySet()),
        )
    }

    @Test fun `a template skips private rules on a device without them`() {
        val template = MonitorTemplate("t", "T", listOf(MonitorTemplateRule("android:camera"), MonitorTemplateRule("android:10008")))
        val stockDevice = MonitorTemplateApplier.apply(template, listOf("app"), emptyList(), emptyList()) {
            MonitorOpCodes.codeOf(it, emptySet()) != null
        }
        assertEquals(listOf("android:camera"), stockDevice.appliedOperations)
        assertEquals(listOf("android:10008"), stockDevice.skippedOperations)
        val miui = MonitorTemplateApplier.apply(template, listOf("app"), emptyList(), emptyList()) {
            MonitorOpCodes.codeOf(it, setOf(10008)) != null
        }
        assertEquals(listOf("android:camera", "android:10008"), miui.appliedOperations)
    }

    @Test fun `picking a private operation for a template keeps its stored name`() {
        val template = MonitorTemplate("t", "T", emptyList()).withOperations(listOf("android:10008"))
        assertEquals(listOf("android:10008"), template.operationNames)
        val decoded = MonitorTemplatesCodec.decode(MonitorTemplatesCodec.encode(listOf(template)))
        assertEquals(listOf("android:10008"), decoded.single().operationNames)
    }
}
