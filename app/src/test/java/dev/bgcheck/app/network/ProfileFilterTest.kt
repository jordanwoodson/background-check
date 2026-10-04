package dev.bgcheck.app.network

import dev.bgcheck.network.domain.model.ProfileFilter
import org.junit.Assert.*
import org.junit.Test

class ProfileFilterTest {
    @Test fun individualWorkProfilesRemainSeparateAndRecreatedProfileDoesNotMatch() {
        val selected = ProfileFilter.key(10, 42)
        assertTrue(ProfileFilter.matches(selected, 10, 42, true, false))
        assertFalse(ProfileFilter.matches(selected, 11, 43, true, false))
        assertFalse(ProfileFilter.matches(selected, 10, 99, true, false))
        assertFalse(ProfileFilter.matches(selected, 0, 0, false, false))
    }

    @Test fun legacyFiltersStayUsableAndUnknownFiltersNeverWidenTheList() {
        assertTrue(ProfileFilter.matches("Work", 10, 42, true, false))
        assertTrue(ProfileFilter.matches("All", 10, 42, true, false))
        assertFalse(ProfileFilter.matches("Personal", 10, 42, true, false))
        assertFalse(ProfileFilter.matches("invalid", 10, 42, true, false))
    }
}
