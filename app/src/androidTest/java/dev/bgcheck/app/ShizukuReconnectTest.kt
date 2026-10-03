package dev.bgcheck.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.bgcheck.app.data.AccessState
import dev.bgcheck.app.shell.ShizukuBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the SDK's shared connection cache while previous workers are still dying. */
@RunWith(AndroidJUnit4::class)
class ShizukuReconnectTest {
    @Test fun immediateBridgeReconnectKeepsTheNewConnection(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        repeat(3) { index ->
            val bridge = ShizukuBridge(context)
            try {
                bridge.check()
                try {
                    withTimeout(20_000) { bridge.access.first { it == AccessState.READY } }
                } catch (failure: TimeoutCancellationException) {
                    throw AssertionError("Reconnect cycle ${index + 1}: state=${bridge.access.value}; ${bridge.message.value}", failure)
                }
                val memory = bridge.execute(listOf("cat", "/proc/meminfo"))
                assertTrue("Reconnect cycle ${index + 1} must execute a real read", memory.lineSequence().any { it.startsWith("MemTotal:") })
            } finally {
                withContext(NonCancellable + Dispatchers.Main) { bridge.close() }
            }
        }
    }
}
