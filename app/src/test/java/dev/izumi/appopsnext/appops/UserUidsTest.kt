package dev.izumi.appopsnext.appops

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UserUidsTest {
    @Test
    fun `a uid is the user range plus the app id`() {
        assertEquals(10_010_123, UserUids.forUser(100, 10123))
        assertEquals(1_010_123, UserUids.forUser(10, 10123))
        assertEquals(10123, UserUids.forUser(0, 10123))
    }

    @Test
    fun `only the app id of the source uid is kept`() {
        assertEquals(1_010_123, UserUids.forUser(10, 10123))
        assertEquals(1_010_123, UserUids.forUser(10, 2_010_123))
    }

    @Test
    fun `user and app id are split back out of a uid`() {
        assertEquals(10, UserUids.userIdOf(1_010_123))
        assertEquals(10123, UserUids.appIdOf(1_010_123))
        assertEquals(0, UserUids.userIdOf(10123))
    }

    @Test
    fun `a negative user is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { UserUids.forUser(-1, 10123) }
    }
}
