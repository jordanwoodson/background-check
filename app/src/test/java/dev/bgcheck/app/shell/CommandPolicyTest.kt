package dev.bgcheck.app.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CommandPolicyTest {
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
