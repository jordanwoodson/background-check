package dev.bgcheck.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.bgcheck.app.data.ResourceParsers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Android uses ICU regex syntax, which is stricter than the JVM unit-test engine. */
@RunWith(AndroidJUnit4::class)
class ResourceParserRuntimeTest {
    @Test fun serviceRecordsParseWithAndroidIcu() {
        val services = ResourceParsers.services("""
            ACTIVITY MANAGER SERVICES (dumpsys activity services)
              * ServiceRecord{abc u0 com.example/.Worker}
                processName=com.example:worker
                app=ProcessRecord{def 123:com.example:worker/u0a101}
                isForeground=true foregroundId=7
        """.trimIndent(), "com.example", 0)
        assertEquals(1, services.size)
        assertEquals("com.example/.Worker", services.single().component)
        assertTrue(services.single().foreground)
    }
}
