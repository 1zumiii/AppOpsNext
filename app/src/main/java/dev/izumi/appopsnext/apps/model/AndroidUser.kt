package dev.izumi.appopsnext.apps.model

/** One line of `pm list users`. */
data class AndroidUser(
    val id: Int,
    val name: String,
    val flags: Int,
) {
    /** A work profile, an app clone or another profile that belongs to a parent user. */
    val isProfile: Boolean
        get() = flags and (FLAG_MANAGED_PROFILE or FLAG_PROFILE) != 0

    companion object {
        const val FLAG_MANAGED_PROFILE = 0x20
        const val FLAG_PROFILE = 0x1000
    }
}

/** One line of `pm list packages --user <n> -U`. */
data class UserPackage(
    val packageName: String,
    val uid: Int,
)
