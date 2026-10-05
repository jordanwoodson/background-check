package dev.bgcheck.network.data.history

import dev.bgcheck.network.domain.model.NetworkChangeKind
import dev.bgcheck.network.domain.model.NetworkChangeRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Repositories own the shared compare/write lock and verify the restored value before returning. */
class NetworkChangeUndoService(
    private val history: NetworkChangeHistoryStore,
    private val restoreFirewall: suspend (NetworkChangeRecord) -> String,
    private val restorePackage: suspend (NetworkChangeRecord) -> String,
) {
    private val undoMutex = Mutex()

    suspend fun undo(id: String): Result<String> = withContext(Dispatchers.IO) {
        undoMutex.withLock {
            try {
                val record = history.records.value.firstOrNull { it.id == id } ?: error("Change is no longer in history")
                require(record.canUndo) { "This change cannot be undone: already restored or installation identity unavailable" }
                history.markRecovery(setOf(id), "Undo started; restoration has not yet been verified. Inspect current settings before acting.")
                // Once checkpointed, finish the bounded backend operation and its journal update together.
                withContext(NonCancellable) {
                    try {
                        val message = when (record.kind) {
                            NetworkChangeKind.FIREWALL_RULE -> restoreFirewall(record)
                            NetworkChangeKind.PACKAGE_ENABLED -> restorePackage(record)
                        }
                        try {
                            history.markUndone(id)
                        } catch (error: Exception) {
                            throw IllegalStateException("Saved settings were restored and verified, but Undo status could not be saved. Inspect the recovery entry before trying again.", error)
                        }
                        Result.success(message)
                    } catch (safe: NetworkUndoPreflightException) {
                        // The repository promises no effect occurred. Restore the original, still guarded action.
                        history.replace(setOf(id), listOf(record))
                        Result.failure(safe)
                    } catch (error: Exception) {
                        runCatching { history.markRecovery(setOf(id),
                            "Undo did not finish verification: ${error.message ?: "unknown error"}. Inspect current settings.") }
                        Result.failure(error)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }
        }
    }
}
