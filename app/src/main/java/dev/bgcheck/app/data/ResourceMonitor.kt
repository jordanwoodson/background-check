package dev.bgcheck.app.data

import java.util.concurrent.CancellationException

class ResourceMonitor(private val execute: suspend (List<String>) -> String) {
    suspend fun sample(): ResourceSnapshot {
        val warnings = mutableListOf<String>()
        val snapshot = read("Live process sample", warnings) { ResourceParsers.top(execute(TOP_COMMAND)) }
            ?: ResourceSnapshot()
        val memory = read("Device memory", warnings) { ResourceParsers.memory(execute(listOf("cat", "/proc/meminfo"))) }
        return snapshot.copy(memory = memory, warnings = snapshot.warnings + warnings)
    }

    suspend fun energy(): EnergySnapshot {
        val warnings = mutableListOf<String>()
        // A package argument suppresses history in -c while AOSP still outputs all UID aggregates.
        // --checkin is deliberately never used: it may consume completed battery statistics.
        val snapshot = read("Battery statistics", warnings) {
            ResourceParsers.energy(execute(listOf("dumpsys", "batterystats", "-c", "dev.bgcheck.app")))
        } ?: EnergySnapshot()
        return snapshot.copy(warnings = snapshot.warnings + warnings)
    }

    suspend fun diagnostics(app: AppEntry): AppDiagnostics {
        require(ShellParsers.validPackage(app.packageName) && app.uid >= 0) { "Invalid app identity" }
        val warnings = mutableListOf<String>()
        val memory = read("Package memory", warnings) {
            ResourceParsers.appMemory(execute(listOf("dumpsys", "meminfo", "--local", "--package", app.packageName)))
        }
        val services = read("Running services", warnings) {
            ResourceParsers.services(execute(listOf("dumpsys", "activity", "-p", app.packageName, "services")),
                app.packageName, app.uid / 100000)
        }
        if (memory != null) warnings += "Package memory includes all profiles and shared processes that load this package."
        return AppDiagnostics(packageName = app.packageName, pssKb = memory?.pssKb,
            privateDirtyKb = memory?.privateDirtyKb, swapPssKb = memory?.swapPssKb,
            services = services.orEmpty(), servicesKnown = services != null, warnings = warnings)
    }

    private suspend fun <T> read(source: String, warnings: MutableList<String>, block: suspend () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        warnings += "$source unavailable: ${failure.message?.take(240) ?: "Android did not provide a valid response"}"
        null
    }

    companion object {
        val TOP_COMMAND = listOf("top", "-b", "-n", "2", "-d", "1", "-s", "4", "-o",
            "PID,PPID,UID,%CPU,RSS,VSZ,S,USER,CMDLINE")
    }
}
