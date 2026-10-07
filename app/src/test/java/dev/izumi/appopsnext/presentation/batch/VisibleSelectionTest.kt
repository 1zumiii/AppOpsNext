package dev.izumi.appopsnext.presentation.batch

import org.junit.Assert.assertEquals
import org.junit.Test

class VisibleSelectionTest {
    @Test
    fun `selecting across searches accumulates and deselecting only removes visible items`() {
        val first = toggleVisibleSelection(emptySet(), setOf("app.a"))
        val second = toggleVisibleSelection(first, setOf("app.b", "app.c"))
        assertEquals(setOf("app.a", "app.b", "app.c"), second)
        assertEquals(setOf("app.a"), toggleVisibleSelection(second, setOf("app.b", "app.c")))
    }

    @Test
    fun `partially selected visible items become fully selected without dropping hidden ones`() {
        assertEquals(
            setOf("a", "b", "hidden"),
            toggleVisibleSelection(setOf("a", "hidden"), setOf("a", "b")),
        )
    }

    @Test
    fun `empty results leave selection unchanged`() {
        assertEquals(setOf("a"), toggleVisibleSelection(setOf("a"), emptySet()))
    }
}
