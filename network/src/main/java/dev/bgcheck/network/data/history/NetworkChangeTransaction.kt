package dev.bgcheck.network.data.history

import dev.bgcheck.network.domain.model.NetworkChangeRecord
import dev.bgcheck.network.domain.model.NetworkChangeKind
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Checkpoint before effects; finish verification even if the caller leaves the screen. */
class NetworkChangeTransaction(private val history: NetworkChangeHistoryStore) {
    suspend fun <T> execute(pending: List<NetworkChangeRecord>, operation: suspend () -> T,
        verify: suspend (completed: Boolean) -> List<NetworkChangeRecord>): T {
        val ids = pending.mapTo(hashSetOf()) { it.id }
        history.append(pending.map { it.copy(recovery = true,
            recoveryReason = "Change started; completion has not been verified. Inspect current settings before acting.") })
        var failure: Exception? = null
        var result: T? = null
        try { result = operation() } catch (error: Exception) { failure = error }
        withContext(NonCancellable) {
            try {
                val verified = verify(failure == null)
                history.replace(ids, verified)
                // A successful shell exit is not proof of the resulting package setting. Persist
                // the observed recovery entry first, then tell callers to refresh rather than keep
                // an optimistic enabled/disabled row. Saved firewall-rule writes retain their own
                // success semantics independently of active firewall enforcement.
                val unverifiedPackage = verified.firstOrNull {
                    it.kind == NetworkChangeKind.PACKAGE_ENABLED && it.recovery
                }
                if (failure == null && unverifiedPackage != null) {
                    failure = NetworkChangeUnverifiedException(unverifiedPackage.recoveryReason
                        ?: "Inspect the recovery entry in history and Android's current setting.")
                }
            } catch (error: Exception) {
                runCatching { history.markRecovery(ids,
                    "Change may have completed; verification unavailable: ${error.message ?: "unknown error"}") }
                if (failure == null) {
                    val message = "Change may have completed. Inspect current settings and the recovery entry in history."
                    failure = if (pending.any { it.kind == NetworkChangeKind.PACKAGE_ENABLED }) {
                        NetworkChangeUnverifiedException(message, error)
                    } else IllegalStateException("Verification is unavailable. $message", error)
                }
            }
        }
        failure?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
}

/** The command may have changed Android; callers must refresh instead of restoring an old row. */
class NetworkChangeUnverifiedException(detail: String, cause: Exception? = null) :
    IllegalStateException("Change could not be verified. $detail", cause)

/** The repository guarantees this rejection happened before touching the saved/device setting. */
class NetworkUndoPreflightException(cause: Exception) : IllegalStateException(cause.message, cause)
