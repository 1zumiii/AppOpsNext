package dev.izumi.appopsnext.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteOperationsTest {
    @Test fun `frequently used operations move to their own group and keep their order`() {
        val items = listOf("CAMERA", "RECORD_AUDIO", "10008", "READ_CLIPBOARD")
        val favorites = setOf("android:read_clipboard", "android:camera", "android:10008")

        val (favorite, rest) = FavoriteOperations.split(items, favorites) { it }

        assertEquals(listOf("CAMERA", "10008", "READ_CLIPBOARD"), favorite)
        assertEquals(listOf("RECORD_AUDIO"), rest)
    }

    @Test fun `nothing marked leaves the list as it is`() {
        val items = listOf("CAMERA", "RECORD_AUDIO")
        assertEquals(emptyList<String>() to items, FavoriteOperations.split(items, emptySet()) { it })
    }

    @Test fun `a marked operation the device lacks adds no row`() {
        val (favorite, rest) = FavoriteOperations.split(listOf("CAMERA"), setOf("android:10008")) { it }
        assertTrue(favorite.isEmpty())
        assertEquals(listOf("CAMERA"), rest)
    }

    @Test fun `names match in any spelling the app uses`() {
        assertTrue(FavoriteOperations.isFavorite(setOf("android:camera"), "CAMERA"))
        assertTrue(FavoriteOperations.isFavorite(setOf("android:camera"), "android:camera"))
        assertFalse(FavoriteOperations.isFavorite(setOf("android:camera"), "COARSE_LOCATION"))
    }
}
