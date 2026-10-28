package dev.izumi.appopsnext.apps

import dev.izumi.appopsnext.apps.model.AndroidUser
import dev.izumi.appopsnext.apps.model.UserPackage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserListParserTest {
    @Test
    fun `users are read with id, name and flags`() {
        val users = UserListParser.parse(
            """
            Users:
            	UserInfo{0:Owner:c13} running
            	UserInfo{10:Work profile:1030} running
            	UserInfo{999:Clone:1000} running
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                AndroidUser(0, "Owner", 0xc13),
                AndroidUser(10, "Work profile", 0x1030),
                AndroidUser(999, "Clone", 0x1000),
            ),
            users,
        )
        assertFalse(users[0].isProfile)
        assertTrue(users[1].isProfile)
        assertTrue(users[2].isProfile)
    }

    @Test
    fun `a name that holds colons keeps them`() {
        val user = UserListParser.parse("	UserInfo{11:Work: personal:10} running").single()

        assertEquals(11, user.id)
        assertEquals("Work: personal", user.name)
    }

    @Test
    fun `unrelated lines and duplicates are ignored`() {
        val users = UserListParser.parse(
            "Users:\nUserInfo{0:Owner:13}\nUserInfo{0:Owner:13}\nError: something",
        )

        assertEquals(listOf(0), users.map(AndroidUser::id))
        assertTrue(UserListParser.parse("").isEmpty())
    }

    @Test
    fun `packages are read with their uid`() {
        val packages = UserPackageListParser.parse(
            """
            package:com.android.chrome uid:1010123
            package:com.example.app uid:1010456
            package:com.example.app uid:1010456
            warning: noise
            package:broken uid:abc
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                UserPackage("com.android.chrome", 1010123),
                UserPackage("com.example.app", 1010456),
            ),
            packages,
        )
    }
}
