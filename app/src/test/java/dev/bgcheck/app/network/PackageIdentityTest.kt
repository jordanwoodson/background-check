package dev.bgcheck.app.network

import dev.bgcheck.network.domain.model.PackageIdentity
import org.junit.Assert.fail
import org.junit.Test

class PackageIdentityTest {
    @Test fun expectedInstallationTimestampRejectsSameUidReinstall() {
        val identity = PackageIdentity(10123, 0, firstInstallTime = 50)
        try {
            identity.requireCurrent(0, 10123, 0, true, currentFirstInstallTime = 51)
            fail("Reinstalled app accepted")
        } catch (_: IllegalArgumentException) { }
        try {
            identity.requireCurrent(0, 10123, 0, true, currentFirstInstallTime = null)
            fail("Unverified installation accepted")
        } catch (_: IllegalArgumentException) { }
        identity.requireCurrent(0, 10123, 0, true, currentFirstInstallTime = 50)
    }
    private val work = PackageIdentity(1010123, 11)

    @Test fun acceptsOnlyTheSelectedInstallation() {
        work.requireCurrent(10, 1010123, 11, true)
        rejects { work.requireCurrent(0, 10123, 0, true) }
        rejects { work.requireCurrent(10, 1010456, 11, true) }
        rejects { work.requireCurrent(10, 1010123, 12, true) }
        rejects { work.requireCurrent(10, 1010123, 11, false) }
        rejects { PackageIdentity(1010123, null).requireCurrent(10, 1010123, 11, true) }
    }

    private fun rejects(action: () -> Unit) {
        try { action(); fail("Stale or unavailable installation accepted") }
        catch (_: IllegalArgumentException) { }
    }
}
