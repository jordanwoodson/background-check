package dev.bgcheck.app.network

import dev.bgcheck.network.domain.model.PackageIdentity
import org.junit.Assert.fail
import org.junit.Test

class PackageIdentityTest {
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
