package dev.bgcheck.app.shell

/** The privileged process has no general-purpose shell endpoint. */
internal object CommandPolicy {
    private val packageName = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)*")
    private val number = Regex("0|[1-9][0-9]*")
    private val operations = setOf("RUN_ANY_IN_BACKGROUND", "RUN_IN_BACKGROUND")
    private val modes = setOf("allow", "ignore", "deny", "default")

    fun validate(args: List<String>): List<String> {
        require(args.size in 1..10 && args.all { it.length in 1..256 }) { "Unsupported command." }
        val allowed = when (args.first()) {
            "ps" -> args == listOf("ps", "-A", "-o", "PID,UID,NAME")
            "top" -> args == listOf("top", "-b", "-n", "2", "-d", "1", "-s", "4", "-o", "PID,PPID,UID,%CPU,RSS,VSZ,S,USER,CMDLINE")
            "cat" -> args == listOf("cat", "/proc/meminfo")
            "pm" -> args.size in 6..7 && args.take(5) == listOf("pm", "list", "packages", "-U", "--user") && validNumber(args[5]) &&
                (args.size == 6 || validPackage(args[6]))
            "dumpsys" -> args == listOf("dumpsys", "activity", "processes") ||
                (args.size == 4 && args.take(3) == listOf("dumpsys", "meminfo", "--local") && validNumber(args[3]) && args[3] != "0") ||
                args == listOf("dumpsys", "batterystats", "-c", "dev.bgcheck.app") ||
                (args.size == 5 && args.take(4) == listOf("dumpsys", "meminfo", "--local", "--package") && validPackage(args[4])) ||
                (args.size == 5 && args.take(3) == listOf("dumpsys", "activity", "-p") && validPackage(args[3]) && args[4] == "services") ||
                (args.size == 3 && args[1] == "package" && validPackage(args[2]))
            "am" -> when (args.getOrNull(1)) {
                "start" -> args.size == 8 && args[2] == "--user" && validNumber(args[3]) &&
                    args[4] == "-a" && args[5] == "android.settings.APPLICATION_DETAILS_SETTINGS" &&
                    args[6] == "-d" && args[7].startsWith("package:") && validPackage(args[7].removePrefix("package:"))
                "force-stop" -> args.size == 5 && args[2] == "--user" && validNumber(args[3]) && validPackage(args[4])
                "get-standby-bucket" -> args.size in 4..5 && args[2] == "--user" &&
                    validNumber(args[3]) && (args.size == 4 || validPackage(args[4]))
                "get-uid-state" -> args.size == 3 && validNumber(args[2])
                else -> false
            }
            "cmd" -> when (args.getOrNull(1)) {
                "deviceidle" -> when (args.getOrNull(2)) {
                    "whitelist" -> args.size == 3 || (args.size == 4 &&
                        args[3].firstOrNull() in setOf('+', '-') && validPackage(args[3].drop(1)))
                    "get" -> args.size == 4 && args[3] in setOf("light", "deep")
                    else -> false
                }
                "appops" -> args.size in 7..8 && args[3] == "--user" &&
                    validNumber(args[4]) && validPackage(args[5]) && args[6] in operations &&
                    ((args[2] == "get" && args.size == 7) ||
                        (args[2] == "set" && args.size == 8 && args[7] in modes))
                else -> false
            }
            else -> false
        }
        require(allowed) { "Unsupported command or invalid package/user." }
        return args.toList()
    }

    private fun validPackage(value: String) = value.length <= 255 && packageName.matches(value)
    private fun validNumber(value: String) = number.matches(value) && value.toIntOrNull() != null
}
