package dev.bgcheck.app.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CommandPolicyTest {
    @Test fun profileSettingsAndPidDiagnosticsRemainNarrowlyScoped() {
        val settings = listOf("am", "start", "--user", "10", "-a", "android.settings.APPLICATION_DETAILS_SETTINGS", "-d", "package:dev.example")
        assertEquals(settings, CommandPolicy.validate(settings))
        assertEquals(listOf("ps", "-A", "-o", "PID,UID,NAME"), CommandPolicy.validate(listOf("ps", "-A", "-o", "PID,UID,NAME")))
        assertEquals(listOf("dumpsys", "meminfo", "--local", "31"), CommandPolicy.validate(listOf("dumpsys", "meminfo", "--local", "31")))
        listOf(settings.toMutableList().apply { set(3, "all") }, settings.toMutableList().apply { set(5, "android.intent.action.DELETE") },
            settings.toMutableList().apply { set(7, "package:dev.example;id") },
            listOf("ps", "-A"), listOf("dumpsys", "meminfo", "--local", "-1")).forEach { command ->
            assertThrows(IllegalArgumentException::class.java) { CommandPolicy.validate(command) }
        }
    }
    @Test fun acceptsBoundedResourceQueriesAndUserScopedStop() {
        val commands = listOf(
            listOf("top", "-b", "-n", "2", "-d", "1", "-s", "4", "-o", "PID,PPID,UID,%CPU,RSS,VSZ,S,USER,CMDLINE"),
            listOf("cat", "/proc/meminfo"),
            listOf("dumpsys", "batterystats", "-c", "dev.bgcheck.app"),
            listOf("dumpsys", "meminfo", "--local", "--package", "com.example.app"),
            listOf("dumpsys", "activity", "-p", "com.example.app", "services"),
            listOf("pm", "list", "packages", "-U", "--user", "0"),
            listOf("pm", "list", "packages", "-U", "--user", "0", "com.example.app"),
            listOf("dumpsys", "package", "com.example.app"),
            listOf("am", "force-stop", "--user", "0", "com.example.app"),
        )
        commands.forEach { assertEquals(it, CommandPolicy.validate(it)) }
    }

    @Test fun refusesUnboundedMonitoringResetAndUnscopedStop() {
        val commands = listOf(
            listOf("top"), listOf("top", "-b", "-n", "9999"),
            listOf("cat", "/proc/1/environ"), listOf("cat", "/proc/meminfo", "/proc/stat"),
            listOf("dumpsys", "batterystats", "--reset"), listOf("dumpsys", "batterystats", "--checkin"),
            listOf("dumpsys", "meminfo", "--local", "--package", "com.example;id"),
            listOf("dumpsys", "activity", "-p", "--all", "services"),
            listOf("am", "force-stop", "--user", "all", "com.example.app"),
            listOf("am", "force-stop", "--user", "0", "com.example.app", "com.other"),
            listOf("pm", "clear", "com.example.app"), listOf("kill", "123"),
        )
        commands.forEach { args -> assertThrows(IllegalArgumentException::class.java) { CommandPolicy.validate(args) } }
    }

    @Test fun acceptsOnlyTheSupportedQueriesAndChanges() {
        val commands = listOf(
            listOf("dumpsys", "activity", "processes"),
            listOf("am", "get-standby-bucket", "--user", "0"),
            listOf("am", "get-standby-bucket", "--user", "10", "com.example.app"),
            listOf("am", "get-uid-state", "10123"),
            listOf("cmd", "deviceidle", "whitelist"),
            listOf("cmd", "deviceidle", "whitelist", "+com.example.app"),
            listOf("cmd", "deviceidle", "whitelist", "-com.example.app"),
            listOf("cmd", "deviceidle", "get", "light"),
            listOf("cmd", "deviceidle", "get", "deep"),
            listOf("cmd", "appops", "get", "--user", "0", "android", "RUN_ANY_IN_BACKGROUND"),
        ) + listOf("allow", "ignore", "deny", "default").flatMap { mode ->
            listOf("RUN_ANY_IN_BACKGROUND", "RUN_IN_BACKGROUND").map { op ->
                listOf("cmd", "appops", "set", "--user", "10", "com.example.app", op, mode)
            }
        }
        commands.forEach { assertEquals(it, CommandPolicy.validate(it)) }
    }

    @Test fun rejectsInjectionExtraArgumentsAndUnrelatedOperations() {
        val commands = listOf(
            emptyList(), listOf("sh", "-c", "id"), listOf("/system/bin/cmd", "deviceidle", "whitelist"),
            listOf("dumpsys", "activity", "processes", "extra"),
            listOf("am", "get-standby-bucket", "--user", "all"),
            listOf("am", "get-standby-bucket", "--user", "-1"),
            listOf("am", "get-standby-bucket", "--user", "2147483648"),
            listOf("am", "get-standby-bucket", "--user", "0", "com.app;id"),
            listOf("am", "get-standby-bucket", "--user", "0", "com.app\nfoo"),
            listOf("am", "force-stop", "com.example.app"),
            listOf("cmd", "deviceidle", "whitelist", "+com.example.app", "-com.other"),
            listOf("cmd", "deviceidle", "whitelist", "+com..app"),
            listOf("cmd", "deviceidle", "whitelist", "+--help"),
            listOf("cmd", "deviceidle", "force-idle"),
            listOf("cmd", "appops", "get", "--uid", "0", "com.example.app", "RUN_IN_BACKGROUND"),
            listOf("cmd", "appops", "set", "--user", "0", "com.example.app", "CAMERA", "allow"),
            listOf("cmd", "appops", "set", "--user", "0", "com.example.app", "RUN_IN_BACKGROUND", "foreground"),
        )
        commands.forEach { args ->
            assertThrows("Must reject $args", IllegalArgumentException::class.java) { CommandPolicy.validate(args) }
        }
    }
}
