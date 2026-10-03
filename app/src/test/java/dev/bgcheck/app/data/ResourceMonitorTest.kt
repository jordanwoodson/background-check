package dev.bgcheck.app.data

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ResourceMonitorTest {
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
        assertEquals(listOf("dumpsys", "meminfo", "--local", "--package", "com.example"), calls[0])
        assertEquals(listOf("dumpsys", "activity", "-p", "com.example", "services"), calls[1])
    }
}
