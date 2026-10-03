package dev.bgcheck.app.data

import org.junit.Assert.*
import org.junit.Test

class ShellParsersTest {
    @Test fun uidOverrideWinsOverPackageMode() {
        val result = ShellParsers.appOp("Uid mode: RUN_ANY_IN_BACKGROUND: ignore\nRUN_ANY_IN_BACKGROUND: allow; time=+2m ago", "RUN_ANY_IN_BACKGROUND")
        assertEquals("ignore", result.mode)
        assertTrue(result.uidOverride)
    }

    @Test fun noOperationUsesReportedDefault() {
        assertEquals("allow", ShellParsers.appOp("No operations.\nDefault mode: allow\n", "RUN_ANY_IN_BACKGROUND").mode)
    }

    @Test fun malformedOperationIsNeverAssumedAllowed() {
        assertThrows(IllegalArgumentException::class.java) { ShellParsers.appOp("Error: no package", "RUN_ANY_IN_BACKGROUND") }
        assertThrows(IllegalArgumentException::class.java) { ShellParsers.appOp("No operations.", "RUN_ANY_IN_BACKGROUND") }
    }

    @Test fun exceptIdleEntryIsNotDozeExempt() {
        val list = ShellParsers.allowlist("system-excidle,com.example.partial,10101\nsystem,com.example.system,10102\nuser,com.example.user,10103\n")
        assertEquals(setOf("com.example.system", "com.example.user"), list.exempt)
        assertEquals(setOf("com.example.user"), list.user)
    }

    @Test fun malformedListDoesNotBecomeEmptySuccessfulList() {
        assertThrows(IllegalArgumentException::class.java) { ShellParsers.allowlist("Permission Denial") }
        assertThrows(IllegalArgumentException::class.java) { ShellParsers.buckets("bad output") }
    }

    @Test fun standbyBucketsPreserveRestrictedAndUnknownFutureValues() {
        assertEquals(mapOf("com.example.app" to 45, "android" to 70), ShellParsers.buckets("com.example.app: 45\nandroid: 70\n"))
    }

    @Test fun processPackagesAreReadFromPackageListAndOtherUsersExcluded() {
        val text = """
            ACTIVITY MANAGER RUNNING PROCESSES (dumpsys activity processes)
              *APP* UID 10101 ProcessRecord{a 500:custom.process/u0a101}
                user #0 uid=10101 gids={}
                packageList={com.example.app, com.example.shared}
                curProcState=19 mRepProcState=19
              *APP* UID 99012 ProcessRecord{b 502:renderer/u0i12}
                user #0 uid=10101 ISOLATED uid=99012 gids={}
                packageList={com.example.app}
                curProcState=4 mRepProcState=4
              *APP* UID 1010101 ProcessRecord{c 503:custom.process/u10a101}
                user #10 uid=1010101 gids={}
                packageList={com.example.other}
                curProcState=2 mRepProcState=2
        """.trimIndent()
        assertEquals(mapOf("com.example.app" to ProcessState.SERVICE, "com.example.shared" to ProcessState.CACHED), ShellParsers.processes(text, 0))
    }

    @Test fun incompleteProcessDumpDoesNotReportAppsStopped() {
        assertThrows(IllegalArgumentException::class.java) { ShellParsers.processes("Permission Denial", 0) }
        assertThrows(IllegalArgumentException::class.java) { ShellParsers.processes("ACTIVITY MANAGER RUNNING PROCESSES\n  *APP* UID 10101 ProcessRecord{a}\n    packageList={com.example.app}", 0) }
    }

    @Test fun homeAndLastActivityAreNotReportedAsCached() {
        assertEquals(ProcessState.BACKGROUND, ShellParsers.processState(14))
        assertEquals(ProcessState.BACKGROUND, ShellParsers.processState(15))
        assertEquals(ProcessState.CACHED, ShellParsers.processState(16))
    }

    @Test fun emptyShellReadsAreUnknownRatherThanEmptyPolicy() {
        assertThrows(IllegalArgumentException::class.java) { ShellParsers.allowlist("") }
        assertThrows(IllegalArgumentException::class.java) { ShellParsers.buckets("") }
    }
}
