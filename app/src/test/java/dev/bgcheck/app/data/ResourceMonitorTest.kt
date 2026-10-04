package dev.bgcheck.app.data

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ResourceMonitorTest {
    @Test fun workMemoryUsesOnlyTheWorkUidPids() = runBlocking {
        val calls = mutableListOf<List<String>>()
        val monitor = ResourceMonitor { command ->
            calls += command
            when {
                command.first() == "ps" -> "PID UID NAME\n21 10001 dev.example\n31 1010001 dev.example\n32 1010001 dev.example:worker"
                command == listOf("dumpsys", "meminfo", "--local", "31") -> memory(31, 100)
                command == listOf("dumpsys", "meminfo", "--local", "32") -> memory(32, 200)
                command == listOf("dumpsys", "activity", "-p", "dev.example", "services") ->
                    "ACTIVITY MANAGER SERVICES (dumpsys activity services)\n  (nothing)"
                else -> error("Wrong profile command: $command")
            }
        }
        val result = monitor.diagnostics(AppEntry("dev.example", "Example", 1010001, false))
        assertEquals(300L, result.pssKb)
        assertEquals(10, result.userId)
        assertFalse(calls.any { "21" in it || "--package" in it })
    }

    @Test fun changedProcessUidDuringMemoryReadMakesMemoryUnavailable() = runBlocking {
        var samples = 0
        val monitor = ResourceMonitor { command ->
            when {
                command.first() == "ps" -> if (samples++ == 0) "PID UID NAME\n31 1010001 dev.example" else "PID UID NAME\n31 10001 dev.example"
                command.getOrNull(1) == "meminfo" -> memory(31, 100)
                else -> "ACTIVITY MANAGER SERVICES (dumpsys activity services)\n  (nothing)"
            }
        }
        val result = monitor.diagnostics(AppEntry("dev.example", "Example", 1010001, false))
        assertNull(result.pssKb)
        assertTrue(result.warnings.isNotEmpty())
    }

    private fun memory(pid: Int, pss: Int) = "Applications Memory Usage (in Kilobytes):\n** MEMINFO in pid $pid [dev.example] **\nTOTAL $pss 20 0 10 0"

    @Test fun failedProcessReadPreservesAvailableSystemMemory() = runBlocking {
        val monitor = ResourceMonitor { command ->
            when (command.first()) {
                "top" -> throw IOException("Process access denied")
                "cat" -> "MemTotal: 1000 kB\nMemAvailable: 400 kB\nSwapTotal: 0 kB\nSwapFree: 0 kB"
                else -> error("Unexpected command")
            }
        }
        val result = monitor.sample()
        assertFalse(result.processesKnown)
        assertNull(result.cpuPercent)
        assertEquals(400L, result.memory!!.availableKb)
        assertTrue(result.warnings.any { "Process access denied" in it })
    }

    @Test fun cancellationStopsCollectionRatherThanReturningAnUnknownSnapshot() {
        var calls = 0
        val monitor = ResourceMonitor { calls++; throw CancellationException("Stopped") }
        assertThrows(CancellationException::class.java) { runBlocking { monitor.sample() } }
        assertEquals(1, calls)
    }

    @Test fun batteryReadNeverConsumesOrResetsStatistics() = runBlocking {
        val calls = mutableListOf<List<String>>()
        val monitor = ResourceMonitor { command ->
            calls += command
            "9,0,i,vers,36,215,build,build\n9,0,l,pws,5000,200,190,210"
        }
        val result = monitor.energy()
        assertEquals(listOf(listOf("dumpsys", "batterystats", "-c", "dev.bgcheck.app")), calls)
        assertEquals(200.0, result.estimatedTotalMah!!, 0.001)
    }

    @Test fun servicesRemainKnownWhenMemoryFails() = runBlocking {
        val calls = mutableListOf<List<String>>()
        val monitor = ResourceMonitor { command ->
            calls += command
            when (command[1]) {
                "-A" -> "PID UID NAME\n21 10101 com.example"
                "meminfo" -> "No process found for: com.example"
                "activity" -> "ACTIVITY MANAGER SERVICES (dumpsys activity services)\n  (nothing)"
                else -> error("Unexpected command")
            }
        }
        val result = monitor.diagnostics(AppEntry("com.example", "Example", 10101, false))
        assertNull(result.pssKb)
        assertTrue(result.servicesKnown)
        assertEquals(emptyList<RunningService>(), result.services)
        assertTrue(result.warnings.any { "Package memory unavailable" in it })
        assertEquals(listOf("ps", "-A", "-o", "PID,UID,NAME"), calls[0])
        assertEquals(listOf("dumpsys", "meminfo", "--local", "21"), calls[1])
        assertEquals(listOf("dumpsys", "activity", "-p", "com.example", "services"), calls[2])
    }
}
