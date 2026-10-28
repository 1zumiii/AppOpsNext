package dev.izumi.appopsnext.appops

/** Android's uid layout: one range of [PER_USER_RANGE] uids per user. */
object UserUids {
    /** `UserHandle.PER_USER_RANGE`. */
    const val PER_USER_RANGE = 100_000

    fun userIdOf(uid: Int): Int = uid / PER_USER_RANGE

    fun appIdOf(uid: Int): Int = uid % PER_USER_RANGE

    /** [uid] may belong to any user, only its app id is kept. */
    fun forUser(userId: Int, uid: Int): Int {
        require(userId >= 0) { "Invalid Android user" }
        return userId * PER_USER_RANGE + appIdOf(uid)
    }
}
