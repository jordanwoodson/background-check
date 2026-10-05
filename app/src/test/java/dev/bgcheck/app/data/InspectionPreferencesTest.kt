package dev.bgcheck.app.data

import org.junit.Assert.*
import org.junit.Test

class InspectionPreferencesTest {
    private val app = AppEntry("com.example.app", "Example", 10123, false,
        profile = AndroidProfile(0, serialNumber = 0), firstInstallTime = 100)

    @Test fun pinsNeverTransferToAnotherInstallationOrProfile() {
        val pin = PinnedApp.from(app)!!
        assertTrue(pin.matches(app))
        assertFalse(pin.matches(app.copy(uid = 10124)))
        assertFalse(pin.matches(app.copy(firstInstallTime = 200)))
        assertFalse(pin.matches(app.copy(profile = app.profile.copy(serialNumber = 20))))
        assertFalse(pin.matches(app.copy(uid = 1010123, profile = AndroidProfile(10, serialNumber = 10))))
    }

    @Test fun unknownIdentityCannotBePinned() {
        assertNull(PinnedApp.from(app.copy(firstInstallTime = null)))
        assertNull(PinnedApp.from(app.copy(profile = app.profile.copy(serialNumber = null))))
        assertNull(PinnedApp.from(app.copy(installed = false)))
    }

    @Test fun pinCodecPreservesIdentityAndSkipsMalformedRecords() {
        val pin = PinnedApp.from(app)!!
        assertEquals(listOf(pin), PinnedApp.decode(PinnedApp.encode(listOf(pin))))
        assertTrue(PinnedApp.decode("bad json").isEmpty())
        assertTrue(PinnedApp.decode("[{\"package\":\"com.example.app\"}]").isEmpty())
    }
}
