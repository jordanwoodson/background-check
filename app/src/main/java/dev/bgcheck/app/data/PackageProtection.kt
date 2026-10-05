package dev.bgcheck.app.data

import android.content.Context
import dev.bgcheck.network.domain.model.PackageCriticality
import dev.bgcheck.network.utils.PackageSafetyLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Uses the network module's process cache; asset reading and mapping stay off the UI thread. */
object PackageProtection {
    @Volatile private var cached: Map<String, PackageCriticality>? = null
    private val mutex = Mutex()

    suspend fun load(context: Context): Map<String, PackageCriticality> {
        cached?.let { return it }
        return mutex.withLock {
            cached ?: withContext(Dispatchers.IO) {
                PackageSafetyLoader.loadSafetyData(context.applicationContext).packages.mapValues { (_, info) ->
                    when (info.criticality) {
                        "bloatware" -> PackageCriticality.BLOATWARE
                        "optional" -> PackageCriticality.OPTIONAL
                        "important" -> PackageCriticality.IMPORTANT
                        "essential" -> PackageCriticality.ESSENTIAL
                        else -> PackageCriticality.UNKNOWN
                    }
                }.also { if (it.isNotEmpty()) cached = it }
            }
        }
    }
}
