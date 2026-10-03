package dev.bgcheck.app.shell

import android.os.Bundle
import androidx.annotation.Keep
import kotlin.system.exitProcess

/** Constructed by Shizuku in its shell-identity UserService process. */
@Keep
class CommandService : ICommandService.Stub() {
    @Synchronized
    override fun execute(args: Array<out String>): Bundle {
        return try {
            val command = CommandPolicy.validate(args.toList())
            val executable = listOf("/system/bin/${command.first()}") + command.drop(1)
            val result = BoundedProcessRunner().collect(
                ProcessBuilder(executable).start(),
                reduceProcessDump = command == listOf("dumpsys", "activity", "processes"),
                reduceBatteryDump = command == listOf("dumpsys", "batterystats", "-c", "dev.bgcheck.app"),
            )
            Bundle().apply {
                putInt("exitCode", result.exitCode)
                putString("stdout", result.stdout)
                putString("stderr", result.stderr)
            }
        } catch (failure: Exception) {
            Bundle().apply {
                putInt("exitCode", -1)
                putString("error", failure.message ?: "Command execution failed.")
            }
        }
    }

    override fun destroy() { exitProcess(0) }
}
