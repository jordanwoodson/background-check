package dev.bgcheck.network.domain.model

/** The installation the user selected, including the profile incarnation. */
data class PackageIdentity(val uid: Int, val userSerial: Long?) {
    fun requireCurrent(userId: Int, currentUid: Int, currentSerial: Long?, available: Boolean) {
        require(available) { "Profile is paused, locked or unavailable" }
        require(uid >= 0 && uid / 100000 == userId && currentUid == uid) { "App identity changed; refresh the app list" }
        require(userSerial != null && userSerial == currentSerial) { "Profile changed; refresh the app list" }
    }
}
