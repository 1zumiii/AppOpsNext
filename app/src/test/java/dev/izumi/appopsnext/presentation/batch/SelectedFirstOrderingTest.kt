package dev.izumi.appopsnext.presentation.batch

import org.junit.Assert.assertEquals
import org.junit.Test

class SelectedFirstOrderingTest {
    @Test fun `selected items retain user order and remaining items retain catalog order`() {
        assertEquals(
            listOf("c", "a", "b", "d"),
            selectedFirstOrdering(listOf("a", "b", "c", "d"), linkedSetOf("c", "a")) { it },
        )
    }

    @Test fun `missing selected entries do not create rows or duplicates`() {
        assertEquals(listOf("b", "a"), selectedFirstOrdering(listOf("a", "b"), linkedSetOf("gone", "b")) { it })
    }

    @Test fun `search filters selected and remaining groups together`() {
        val ordered = selectedFirstOrdering(listOf("camera", "clipboard", "contacts"), setOf("contacts")) { it }
        assertEquals(listOf("contacts", "clipboard"), ordered.filter { it.contains("c") && it != "camera" })
    }
}
