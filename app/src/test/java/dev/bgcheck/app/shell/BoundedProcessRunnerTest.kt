package dev.bgcheck.app.shell

import java.io.IOException
import java.util.concurrent.TimeoutException
import org.junit.Assert.*
import org.junit.Test

class BoundedProcessRunnerTest {
    private fun python(code: String): Process = ProcessBuilder("python3", "-c", code).start()

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
