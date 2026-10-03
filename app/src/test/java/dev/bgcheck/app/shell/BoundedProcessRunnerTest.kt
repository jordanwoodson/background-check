package dev.bgcheck.app.shell

import java.io.IOException
import java.util.concurrent.TimeoutException
import org.junit.Assert.*
import org.junit.Test

class BoundedProcessRunnerTest {
    private fun python(code: String): Process = ProcessBuilder("python3", "-c", code).start()

    @Test fun batteryReductionKeepsAttributionButBoundsVerboseHistory() {
        val source = """9,0,i,vers,36,1,build,build
9,0,l,bt,0,120000,100000,150000,120000,1700000000000
9,0,l,pws,5000,90.5,80,100
9,10123,l,pwi,uid,1.25,0,0,0
9,10123,l,cpu,200,300
9,10123,l,awl,4500,2000
9,10123,l,nt,100,200,300,400
9,10123,l,wl,unneeded private tag,other
"""
        val process = python("import sys; sys.stdout.write('9,h,verbose history\\n'*10000 + " + pythonString(source) + ")")
        val result = BoundedProcessRunner(maxOutputBytes = 1024).collect(process, reduceBatteryDump = true)
        assertEquals(source.substringBefore("9,10123,l,wl,"), result.stdout)
    }

    @Test fun drainsBothPipesAndPreservesFailureExitStatus() {
        val result = BoundedProcessRunner().collect(python("import sys; print('result'); print('denied', file=sys.stderr); sys.exit(7)"))
        assertEquals(7, result.exitCode)
        assertEquals("result\n", result.stdout)
        assertEquals("denied\n", result.stderr)
    }

    @Test fun killsProcessThatExceedsDeadline() {
        val process = python("import time; time.sleep(30)")
        val started = System.nanoTime()
        assertThrows(TimeoutException::class.java) { BoundedProcessRunner(timeoutMillis = 150).collect(process) }
        assertTrue("Timeout must remain bounded", (System.nanoTime() - started) / 1_000_000 < 3_000)
        assertFalse(process.isAlive)
    }

    @Test fun rejectsCombinedOutputOverflowWithoutDeadlock() {
        val process = python("import sys; sys.stdout.write('a'*100000); sys.stdout.flush(); sys.stderr.write('b'*100000)")
        assertThrows(IOException::class.java) { BoundedProcessRunner(maxOutputBytes = 120_000).collect(process) }
        assertFalse(process.isAlive)
    }

    @Test fun reducesProcessDumpWithoutLosingPackagesOrStates() {
        val source = """ACTIVITY MANAGER RUNNING PROCESSES (dumpsys activity processes)
  *APP* UID 10123 ProcessRecord{abc 321:com.example.app/u0a123}
    user #0 uid=10123
    packageList={com.example.app, com.example.shared}
    unneeded detail
    curProcState=19 mState=other
  *PERS* UID 1000 ProcessRecord{def 456:system/1000}
    packageList={android}
    curProcState=0
"""
        val process = python("import sys; sys.stdout.write(" + pythonString(source) + ")")
        val result = BoundedProcessRunner().collect(process, reduceProcessDump = true)
        assertEquals(source.replace("    unneeded detail\n", ""), result.stdout)
        assertEquals(0, result.exitCode)
    }

    @Test fun rejectsRawOutputOverflowEvenWhenDiscardingEveryLine() {
        val process = python("print('discard me\\n' * 10000)")
        assertThrows(IOException::class.java) {
            BoundedProcessRunner(maxRawBytes = 1024).collect(process, reduceProcessDump = true)
        }
        assertFalse(process.isAlive)
    }

    private fun pythonString(text: String) = "'" + text.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n") + "'"
}
