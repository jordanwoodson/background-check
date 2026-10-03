package dev.bgcheck.app.shell

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal data class CommandResult(val exitCode: Int, val stdout: String, val stderr: String)

/** Both pipes share limits, and every wait uses the same monotonic deadline. */
internal class BoundedProcessRunner(
    private val timeoutMillis: Long = 10_000,
    private val maxOutputBytes: Int = 256 * 1024,
    private val maxRawBytes: Int = 8 * 1024 * 1024,
) {
    fun collect(process: Process, reduceProcessDump: Boolean = false): CommandResult {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        val budget = OutputBudget(maxOutputBytes, maxRawBytes)
        val readers = Executors.newFixedThreadPool(2) { runnable ->
            Thread(runnable, "bgcheck-command-output").apply { isDaemon = true }
        }
        fun read(stream: InputStream, reduce: Boolean) = readers.submit(Callable {
            try { drain(stream, budget, reduce) }
            catch (failure: Exception) { process.destroyForcibly(); throw failure }
        })
        val stdout = read(process.inputStream, reduceProcessDump)
        val stderr = read(process.errorStream, false)
        try {
            process.outputStream.close()
            if (!process.waitFor(remaining(deadline), TimeUnit.NANOSECONDS)) {
                throw TimeoutException("Command timed out after ${timeoutMillis / 1000.0} seconds.")
            }
            return CommandResult(process.exitValue(), await(stdout, deadline), await(stderr, deadline))
        } finally {
            if (process.isAlive) {
                process.destroyForcibly()
                process.waitFor(200, TimeUnit.MILLISECONDS)
            }
            stdout.cancel(true)
            stderr.cancel(true)
            readers.shutdownNow()
            runCatching { process.inputStream.close() }
            runCatching { process.errorStream.close() }
            runCatching { process.outputStream.close() }
        }
    }

    private fun remaining(deadline: Long): Long = (deadline - System.nanoTime()).coerceAtLeast(1)

    private fun await(future: Future<String>, deadline: Long): String = try {
        future.get(remaining(deadline), TimeUnit.NANOSECONDS)
    } catch (failure: ExecutionException) {
        throw (failure.cause as? Exception ?: IOException("Could not read command output.", failure))
    }

    private fun drain(stream: InputStream, budget: OutputBudget, reduce: Boolean): String {
        val retained = ByteArrayOutputStream()
        val line = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        fun keepLine() {
            val bytes = line.toByteArray()
            val value = bytes.toString(Charsets.UTF_8).trim()
            if (value.startsWith("ACTIVITY MANAGER RUNNING PROCESSES") ||
                value.startsWith("*APP* UID ") || value.startsWith("*PERS* UID ") ||
                value.startsWith("packageList={") || value.startsWith("curProcState=") ||
                value.startsWith("user #")) {
                budget.retain(bytes.size)
                retained.write(bytes)
            }
            line.reset()
        }
        stream.use {
            while (true) {
                val count = it.read(buffer)
                if (count < 0) break
                budget.consume(count)
                if (!reduce) {
                    budget.retain(count)
                    retained.write(buffer, 0, count)
                } else {
                    for (index in 0 until count) {
                        line.write(buffer[index].toInt())
                        if (line.size() > 64 * 1024) throw IOException("Process dump contains an oversized line.")
                        if (buffer[index] == '\n'.code.toByte()) keepLine()
                    }
                }
            }
            if (reduce && line.size() > 0) keepLine()
        }
        return retained.toString(Charsets.UTF_8.name())
    }

    private class OutputBudget(private val retainedLimit: Int, private val rawLimit: Int) {
        private var raw = 0L
        private var retained = 0L
        @Synchronized fun consume(count: Int) {
            raw += count
            if (raw > rawLimit) throw IOException("Command output exceeded the read limit; no partial result was used.")
        }
        @Synchronized fun retain(count: Int) {
            retained += count
            if (retained > retainedLimit) throw IOException("Command output exceeded the result limit; no partial result was used.")
        }
    }
}
