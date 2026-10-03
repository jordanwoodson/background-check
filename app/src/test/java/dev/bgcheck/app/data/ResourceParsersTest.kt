package dev.bgcheck.app.data

import org.junit.Assert.*
import org.junit.Test

class ResourceParsersTest {
    private fun frame(cpu: String, rows: String) = """
        Tasks: 2 total, 1 running, 1 sleeping, 0 stopped, 0 zombie
          Mem: 1000K total, 900K used, 100K free, 0K buffers
         Swap: 500K total, 100K used, 400K free, 300K cached
        800%cpu 80%user 0%nice 80%sys ${cpu}%idle 8%iow 0%irq 0%sirq 0%host
          PID PPID UID[%CPU] RSS VSZ S USER CMDLINE
        $rows
    """.trimIndent()

    @Test fun topUsesSecondIntervalAndKeepsCommandsAndLargeCoreUsage() {
        val text = frame("600", "12 1 10101 0.0 200 400 S u0_a101 com.example") + "\n\n" +
            frame("632", "12 1 10101 125.5 2048 9000000 S u0_a101 /system/bin/example --argument value\n13 2 0 0.0 0 0 I root [worker with spaces]")
        val result = ResourceParsers.top(text)
        assertEquals(20.0, result.cpuPercent!!, 0.001)
        assertEquals(8, result.cpuCoreCount)
        assertTrue(result.processesKnown)
        assertEquals(125.5, result.processes[0].cpuPercent!!, 0.001)
        assertEquals(2048L, result.processes[0].rssKb)
        assertEquals("example", result.processes[0].name)
        assertEquals("/system/bin/example --argument value", result.processes[0].command)
        assertEquals("[worker with spaces]", result.processes[1].name)
    }

    @Test fun topRequiresSecondCompleteFrameAndRejectsDuplicatedThreadRows() {
        val first = frame("632", "12 1 10101 0.0 200 400 S u0_a101 com.example")
        assertThrows(IllegalArgumentException::class.java) { ResourceParsers.top(first) }
        assertThrows(IllegalArgumentException::class.java) { ResourceParsers.top(first + "\nTasks: 2 total") }
        assertThrows(IllegalArgumentException::class.java) {
            ResourceParsers.top(first + "\n" + frame("632", "12 1 10101 0.0 200 400 S u0_a101 com.example\n12 1 10101 0.0 200 400 S u0_a101 com.example"))
        }
    }

    @Test fun unavailableProcessMetricsAreNotZero() {
        val frame = frame("632", "12 1 10101 - - - S u0_a101 com.example")
        val proc = ResourceParsers.top(frame + "\n" + frame).processes.single()
        assertNull(proc.cpuPercent); assertNull(proc.rssKb); assertNull(proc.virtualKb)
    }

    @Test fun availableMemoryIncludesReclaimablePagesAndRejectsMissingFields() {
        val result = ResourceParsers.memory("MemTotal: 8000000 kB\nMemFree: 200000 kB\nMemAvailable: 3000000 kB\nSwapTotal: 4000000 kB\nSwapFree: 2500000 kB")
        assertEquals(3000000L, result.availableKb)
        assertThrows(IllegalArgumentException::class.java) { ResourceParsers.memory("MemTotal: 8000000 kB\nMemFree: 2 kB") }
    }

    @Test fun energyUsesOnlyCurrentUidAggregatesWithCorrectUnitsAndNoDoubleCounting() {
        val result = ResourceParsers.energy("""
            9,0,i,vers,36,215,build,build
            9,0,i,uid,10101,com.example
            9,0,i,uid,10101,com.example.shared
            9,0,l,bt,1,7261662,7253938,12118492,12110767,1791050833164
            9,0,l,pws,5032,918,900,920
            9,0,l,pwi,cpu,442,0,0,0
            9,10101,l,pwi,uid,12.5,0,0,0
            9,10101,l,cpu,100,250,0
            9,10101,l,awl,450,100
            9,10101,l,nt,3000000000,40,5000000000,60
            9,10102,l,pwi,uid,0,0,0,0
            9,10102,c,pwi,uid,99,0,0,0
        """.trimIndent())
        assertEquals(918.0, result.estimatedTotalMah!!, 0.001)
        assertEquals(7261662L, result.batteryRealtimeMs)
        assertEquals(1791050833164L, result.startClockTimeMs)
        assertEquals(2, result.uids.size)
        val uid = result.uids.first { it.uid == 10101 }
        assertEquals(350L, uid.cpuTimeMs)
        assertEquals(8000000000L, uid.networkRxBytes)
        assertEquals(100L, uid.networkTxBytes)
        assertEquals(450L, uid.wakelockMs)
        assertNull(result.uids.first { it.uid == 10102 }.networkRxBytes)
        assertEquals(0.0, result.uids.first { it.uid == 10102 }.estimatedMah!!, 0.001)
    }

    @Test fun energyRejectsUnknownVersionAndPreservesGoodMetricsAfterMalformedRecord() {
        assertThrows(IllegalArgumentException::class.java) { ResourceParsers.energy("Permission denied") }
        assertThrows(IllegalArgumentException::class.java) { ResourceParsers.energy("9,0,i,vers,999,215,build,build") }
        val result = ResourceParsers.energy("9,0,i,vers,36,215,build,build\n9,10101,l,pwi,uid,2.5,0,0,0\n9,10101,l,cpu,bad,4,0")
        assertEquals(2.5, result.uids.single().estimatedMah!!, 0.001)
        assertNull(result.uids.single().cpuTimeMs)
        assertTrue(result.warnings.isNotEmpty())
    }

    @Test fun unnamedPowerComponentsAreIndependentContributions() {
        val result = ResourceParsers.energy("""
            9,0,i,vers,36,215,build,build
            9,0,l,pws,5000,100,90,110
            9,10101,l,pwi,uid,12.5,0,0,0
            9,0,l,pwi,???,0,0,0,0
            9,0,l,pwi,???,0.0696,0,0,0
            9,0,l,pwi,???,1.25,0,0,0
            9,0,l,pwi,cpu,50,0,0,0
        """.trimIndent())
        assertEquals(1.3196, result.components.single { it.name == "Other components" }.estimatedMah, 0.00001)
        assertEquals(50.0, result.components.single { it.name == "cpu" }.estimatedMah, 0.00001)
        assertTrue(result.warnings.isEmpty())
    }

    @Test fun memoryDetailsSumsDistinctProcessesWithoutCountingSummaryTwice() {
        val result = ResourceParsers.appMemory("""
            Applications Memory Usage (in Kilobytes):
            ** MEMINFO in pid 12 [com.example] **
              TOTAL 1000 200 300 400 5000 0 0 0
              TOTAL PSS: 1000 TOTAL RSS: 5000 TOTAL SWAP PSS: 400
            ** MEMINFO in pid 13 [custom.process] **
              TOTAL 2000 500 600 700 9000 0 0 0
              TOTAL PSS: 2000 TOTAL RSS: 9000 TOTAL SWAP PSS: 700
        """.trimIndent())
        assertEquals(3000L, result.pssKb)
        assertEquals(700L, result.privateDirtyKb)
        assertEquals(1100L, result.swapPssKb)
    }

    @Test fun missingOrIncompleteMemoryIsNeverReportedAsZero() {
        assertThrows(IllegalArgumentException::class.java) { ResourceParsers.appMemory("No process found for: com.example") }
        assertThrows(IllegalArgumentException::class.java) {
            ResourceParsers.appMemory("Applications Memory Usage (in Kilobytes):\n** MEMINFO in pid 12 [com.example] **\nTOTAL 1 2 3 4 5\n** MEMINFO in pid 13 [com.example:worker] **")
        }
    }

    @Test fun servicesFiltersExactPackageAndUserAndIdentifiesForeground() {
        val result = ResourceParsers.services("""
            ACTIVITY MANAGER SERVICES (dumpsys activity services)
              * ServiceRecord{abc u0 com.example/.Worker c:com.example}
                packageName=com.example
                processName=com.example:worker
                app=ProcessRecord{abc 12:com.example:worker/u0a101}
                isForeground=true foregroundId=7
              * ServiceRecord{def u10 com.example/.WorkProfile c:com.example}
                packageName=com.example
              * ServiceRecord{ghi u0 com.example.other/.Unrelated c:com.example.other}
                packageName=com.example.other
              * ServiceRecord{jkl u0 com.example/.Pending c:com.example}
                packageName=com.example
                app=null
        """.trimIndent(), "com.example", 0)
        assertEquals(1, result.size)
        assertEquals("com.example/.Worker", result.single().component)
        assertEquals("com.example:worker", result.single().process)
        assertTrue(result.single().foreground)
        assertEquals(emptyList<RunningService>(), ResourceParsers.services("ACTIVITY MANAGER SERVICES (dumpsys activity services)\n  (nothing)", "com.example", 0))
    }

    @Test fun servicesWithoutRecognizedProcessAssociationAreUnavailable() {
        val header = "ACTIVITY MANAGER SERVICES (dumpsys activity services)\n  * ServiceRecord{abc u0 com.example/.Worker c:com.example}\n    processName=com.example"
        assertThrows(IllegalArgumentException::class.java) { ResourceParsers.services(header, "com.example", 0) }
        assertThrows(IllegalArgumentException::class.java) { ResourceParsers.services(header + "\n    app=ProcessRecord{broken}", "com.example", 0) }
        assertEquals(emptyList<RunningService>(), ResourceParsers.services(header + "\n    app=null", "com.example", 0))
        assertEquals(1, ResourceParsers.services(header + "\n    app=ProcessRecord{abc 123:com.example/u0a101}", "com.example", 0).size)
    }
}
