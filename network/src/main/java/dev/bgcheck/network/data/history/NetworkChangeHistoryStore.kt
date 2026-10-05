package dev.bgcheck.network.data.history

import android.content.Context
import dev.bgcheck.network.domain.model.NetworkChangeHistoryCodec
import dev.bgcheck.network.domain.model.NetworkChangeRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A small, private journal. Publish state only after its durable write succeeds. */
class NetworkChangeHistoryStore(read: () -> String?, private val persist: (String) -> Boolean) {
    constructor(context: Context) : this(
        read = { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) },
        persist = { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, it).commit() },
    )

    private val state = MutableStateFlow(NetworkChangeHistoryCodec.decode(read()))
    val records: StateFlow<List<NetworkChangeRecord>> = state.asStateFlow()

    @Synchronized fun append(changes: List<NetworkChangeRecord>) {
        if (changes.isEmpty()) return
        save(NetworkChangeHistoryCodec.retained((changes.asReversed() + state.value).distinctBy { it.id }))
    }

    @Synchronized fun replace(ids: Set<String>, changes: List<NetworkChangeRecord>) {
        save(NetworkChangeHistoryCodec.retained((changes.asReversed() + state.value.filterNot { it.id in ids }).distinctBy { it.id }))
    }

    @Synchronized fun markRecovery(ids: Set<String>, reason: String) {
        save(state.value.map { if (it.id in ids) it.copy(recovery = true, recoveryReason = reason) else it })
    }

    @Synchronized fun markUndone(id: String) {
        check(state.value.any { it.id == id }) { "Change is no longer in history" }
        save(state.value.map { if (it.id == id) it.copy(undone = true, recovery = false, recoveryReason = null) else it })
    }

    fun exportJson(): String = NetworkChangeHistoryCodec.encode(state.value)

    private fun save(records: List<NetworkChangeRecord>) {
        check(persist(NetworkChangeHistoryCodec.encode(records))) { "Could not save change history" }
        state.value = records
    }

    companion object {
        private const val PREFS = "network_change_history"
        private const val KEY = "records_v1"
    }
}
