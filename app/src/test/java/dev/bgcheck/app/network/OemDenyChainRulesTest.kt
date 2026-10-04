package dev.bgcheck.app.network

import dev.bgcheck.network.domain.firewall.OemDenyChainRules
import org.junit.Assert.*
import org.junit.Test

class OemDenyChainRulesTest {
    @Test fun findsOnlyOemDenyThreeIncludingIsolatedUids() {
        val dump = """
            TrafficController
            current ownerMatch configuration: 1024 OEM_DENY_3_MATCH
            sUidOwnerMap:
                10123 BACKGROUND_MATCH
                1010123 OEM_DENY_3_MATCH BACKGROUND_MATCH
                1099001 OEM_DENY_3_MATCH
                1010456 OEM_DENY_2_MATCH
            sUidPermissionMap:
                10123 PERMISSION_NONE
        """.trimIndent()
        assertEquals(setOf(1010123, 1099001), OemDenyChainRules.parse(dump))
    }

    @Test fun refusesTruncatedAndUnreadableMaps() {
        listOf("sUidOwnerMap:\n10123 OEM_DENY_3_MATCH", "sUidOwnerMap:\nError: permission denied\nsUidPermissionMap:")
            .forEach { dump ->
                try { OemDenyChainRules.parse(dump); fail("Incomplete map accepted") }
                catch (_: IllegalArgumentException) { }
            }
    }
}
