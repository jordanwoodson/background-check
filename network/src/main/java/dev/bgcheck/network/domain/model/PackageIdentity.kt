package dev.bgcheck.network.domain.model

/** The installation the user selected, including the profile incarnation. */
data class PackageIdentity(val uid: Int, val userSerial: Long?, val firstInstallTime: Long? = null) {
    fun requireCurrent(userId: Int, currentUid: Int, currentSerial: Long?, available: Boolean,
        currentFirstInstallTime: Long? = null) {
        require(available) { "Profile is paused, locked or unavailable" }
        require(uid >= 0 && uid / 100000 == userId && currentUid == uid) { "App identity changed; refresh the app list" }
        require(userSerial != null && userSerial == currentSerial) { "Profile changed; refresh the app list" }
        require(firstInstallTime == null || (firstInstallTime > 0 && firstInstallTime == currentFirstInstallTime)) {
            "App installation changed or cannot be verified; refresh the app list"
        }
    }
}
