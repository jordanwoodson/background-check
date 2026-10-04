package dev.bgcheck.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ProcessManagerTest {
    private val app = AppEntry("com.example.app", "Example", 10101, false)

    @Test fun stopIsVerifiedWithPackageFlagAndProcessAbsence() = runBlocking {
        val device = Device()
        val result = ProcessManager(device::exec, 0).forceStop(app)
        assertTrue(result.message, result.stopped)
        assertEquals(app.packageName, result.packageName)
        assertEquals(listOf(listOf("am", "force-stop", "--user", "0", "com.example.app")), device.writes)
        assertTrue(device.commands.indexOf(device.filteredCommand) < device.commands.indexOf(device.writes.single()))
        assertTrue(device.commands.indexOf(device.allCommand) < device.commands.indexOf(device.writes.single()))
        assertTrue(device.commands.contains(listOf("dumpsys", "package", "com.example.app")))
        assertTrue(device.commands.contains(listOf("dumpsys", "activity", "processes")))
    }

    @Test fun profileUsesExplicitUserForEveryPackageAndStopCommand() = runBlocking {
        val device = Device(userId = 10, uid = 1010101)
        val result = ProcessManager(device::exec, 10).forceStop(app.copy(uid = 1010101))
        assertTrue(result.message, result.stopped)
        assertEquals(listOf(listOf("am", "force-stop", "--user", "10", "com.example.app")), device.writes)
    }

    @Test fun unsafeLocalIdentityIsRejectedWithoutCallingTransport() = runBlocking {
        val blocked = listOf(
            app.copy(installed = false),
            app.copy(protectedReason = "Active keyboard"),
            app.copy(system = true),
            app.copy(uid = 1000),
            app.copy(uid = 110101),
            app.copy(uid = -1),
            app.copy(packageName = "com.example.app;reboot"),
            app.copy(packageName = "dev.bgcheck.app"),
            app.copy(packageName = "moe.shizuku.privileged.api"),
            app.copy(packageName = "com.android.systemui"),
        )
        blocked.forEach { entry ->
            val device = Device()
            assertFalse(ProcessManager(device::exec, 0).forceStop(entry).stopped)
            assertTrue("Commands executed for protected ${entry.packageName}", device.commands.isEmpty())
        }
        val device = Device()
        assertFalse(ProcessManager(device::exec, -1).forceStop(app).stopped)
        assertTrue(device.commands.isEmpty())
    }

    @Test fun staleUidIsRejectedBeforeMutation() = runBlocking {
        val device = Device(uid = 10102)
        assertFalse(ProcessManager(device::exec, 0).forceStop(app).stopped)
        assertTrue(device.writes.isEmpty())
    }

    @Test fun substringPackageMatchCannotAuthorizeStop() = runBlocking {
        val device = Device(filtered = "package:com.example.app.extension uid:10101")
        assertFalse(ProcessManager(device::exec, 0).forceStop(app).stopped)
        assertTrue(device.writes.isEmpty())
    }

    @Test fun removedPackageIsRejectedBeforeMutation() = runBlocking {
        val device = Device(filtered = "")
        assertFalse(ProcessManager(device::exec, 0).forceStop(app).stopped)
        assertTrue(device.writes.isEmpty())
    }

    @Test fun sharedUidIsRejectedBeforeMutation() = runBlocking {
        val device = Device(additionalPackages = "package:com.example.sibling uid:10101")
        assertFalse(ProcessManager(device::exec, 0).forceStop(app).stopped)
        assertTrue(device.writes.isEmpty())
    }

    @Test fun malformedAndConflictingPackageListsCannotAuthorizeStop() = runBlocking {
        listOf("Warning: package lookup incomplete", "package:com.example.other uid:not-a-number", "package:com.example.app uid:10102").forEach { line ->
            val device = Device(additionalPackages = line)
            assertFalse(ProcessManager(device::exec, 0).forceStop(app).stopped)
            assertTrue(device.writes.isEmpty())
        }
    }

    @Test fun filteredLookupRechecksIdentityImmediatelyBeforeStop() = runBlocking {
        val device = Device(filtered = "package:com.example.app uid:10102")
        assertFalse(ProcessManager(device::exec, 0).forceStop(app).stopped)
        assertTrue(device.writes.isEmpty())
    }

    @Test fun incompleteActivitySnapshotCannotConfirmStop() = runBlocking {
        listOf("Permission denied", "ACTIVITY MANAGER RUNNING PROCESSES (dumpsys activity processes)\n", "ACTIVITY MANAGER RUNNING PROCESSES\n  *APP* UID 10101 ProcessRecord{abc 42:com.example.app/u0a101}").forEach { output ->
            val device = Device(processes = output)
            val result = ProcessManager(device::exec, 0).forceStop(app)
            assertFalse(result.stopped)
            assertEquals(1, device.writes.size)
            assertTrue(result.message.contains("verif", ignoreCase = true))
        }
    }

    @Test fun stillObservedProcessDoesNotCountAsStopped() = runBlocking {
        val device = Device(processes = processSnapshot("com.example.app", 0))
        assertFalse(ProcessManager(device::exec, 0).forceStop(app).stopped)
        assertEquals(1, device.writes.size)
    }

    @Test fun samePackageInOtherProfileDoesNotInvalidateRequestedUserStop() = runBlocking {
        val device = Device(processes = processSnapshot("com.example.app", 10))
        val result = ProcessManager(device::exec, 0).forceStop(app)
        assertTrue(result.message, result.stopped)
    }

    @Test fun absentProcessWithoutStoppedFlagIsNotVerified() = runBlocking {
        val device = Device(packageOutput = packageSnapshot(0, stopped = false))
        assertFalse(ProcessManager(device::exec, 0).forceStop(app).stopped)
        assertEquals(1, device.writes.size)
    }

    @Test fun stoppedFlagMustBelongToExactPackageAndRequestedUser() = runBlocking {
        listOf(
            packageSnapshot(10),
            packageSnapshot(0, packageName = "com.example.app.extension"),
            packageSnapshot(0).replace("installed=true", "installed=false"),
            packageSnapshot(0).replace("stopped=true", ""),
            packageSnapshot(0, stopped = false) + "\nHidden system packages:\n" + packageSnapshot(0).substringAfter("Packages:\n"),
        ).forEach { output ->
            val device = Device(packageOutput = output)
            assertFalse(ProcessManager(device::exec, 0).forceStop(app).stopped)
            assertEquals(1, device.writes.size)
        }
    }

    @Test fun laterUnrelatedUserHeaderCannotProvideStoppedFlag() = runBlocking {
        val output = packageSnapshot(0).replace("stopped=true", "") + "\nQueries:\n  User 0: installed=true stopped=true\n"
        val device = Device(packageOutput = output)
        assertFalse(ProcessManager(device::exec, 0).forceStop(app).stopped)
    }

    @Test fun identityChangeAfterCommandPreventsSuccessClaim() = runBlocking {
        val device = Device(uidAfterStop = 10102)
        assertFalse(ProcessManager(device::exec, 0).forceStop(app).stopped)
        assertEquals(1, device.writes.size)
    }

    @Test fun commandFailureIsReportedWithoutRetry() = runBlocking {
        val device = Device(stopFailure = IllegalStateException("Disconnected"))
        val result = ProcessManager(device::exec, 0).forceStop(app)
        assertFalse(result.stopped)
        assertEquals(1, device.writes.size)
        assertTrue(result.message.contains("Disconnected"))
    }

    @Test fun errorTextFromSuccessfulTransportIsNotSuccess() = runBlocking {
        val device = Device(stopOutput = "Error: package is protected")
        assertFalse(ProcessManager(device::exec, 0).forceStop(app).stopped)
        assertEquals(1, device.writes.size)
    }

    @Test fun transportFailureBeforeStopDoesNotMutate() = runBlocking {
        val device = Device(lookupFailure = IllegalStateException("Permission denied"))
        val result = ProcessManager(device::exec, 0).forceStop(app)
        assertFalse(result.stopped)
        assertTrue(device.writes.isEmpty())
    }

    @Test fun cancellationIsPropagatedWithoutRetry() = runBlocking {
        val device = Device(stopFailure = CancellationException("Cancelled"))
        try {
            ProcessManager(device::exec, 0).forceStop(app)
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertEquals(1, device.writes.size)
    }

    private class Device(
        val userId: Int = 0,
        var uid: Int = 10101,
        val filtered: String? = null,
        val additionalPackages: String = "",
        val processes: String = processSnapshot("com.example.other", 0),
        val packageOutput: String = packageSnapshot(userId),
        val uidAfterStop: Int? = null,
        val stopOutput: String = "",
        val stopFailure: Exception? = null,
        val lookupFailure: Exception? = null,
    ) {
        val commands = mutableListOf<List<String>>()
        val writes = mutableListOf<List<String>>()
        val allCommand = listOf("pm", "list", "packages", "-U", "--user", userId.toString())
        val filteredCommand = allCommand + "com.example.app"
        suspend fun exec(args: List<String>): String {
            commands += args
            return when (args) {
                allCommand -> {
                    lookupFailure?.let { throw it }
                    "package:com.example.app uid:$uid\npackage:com.example.other uid:${userId * 100000 + 10201}\n$additionalPackages"
                }
                filteredCommand -> filtered ?: "package:com.example.app uid:$uid"
                listOf("am", "force-stop", "--user", userId.toString(), "com.example.app") -> {
                    writes += args
                    uidAfterStop?.let { uid = it }
                    stopFailure?.let { throw it }
                    stopOutput
                }
                listOf("dumpsys", "activity", "processes") -> processes
                listOf("dumpsys", "package", "com.example.app") -> packageOutput
                else -> error("Unexpected command $args")
            }
        }
    }

    private companion object {
        fun packageSnapshot(userId: Int, stopped: Boolean = true, packageName: String = "com.example.app") = """
            Packages:
              Package [$packageName] (98d187d):
                appId=10101
                flags=[ HAS_CODE ALLOW_CLEAR_USER_DATA ALLOW_BACKUP ]
                User $userId: ceDataInode=341494 deDataInode=341260 installed=true hidden=false suspended=false distractionFlags=0 stopped=$stopped notLaunched=false enabled=0 instant=false virtual=false

            Queries:
              User 0:
                queryable via interaction:
        """.trimIndent()

        fun processSnapshot(packageName: String, userId: Int) = """
            ACTIVITY MANAGER RUNNING PROCESSES (dumpsys activity processes)
              *APP* UID ${userId * 100000 + 10201} ProcessRecord{ab123 1234:$packageName/u${userId}a201}
                user #$userId uid=${userId * 100000 + 10201}
                packageList={$packageName}
                curProcState=19
        """.trimIndent()
    }
}
