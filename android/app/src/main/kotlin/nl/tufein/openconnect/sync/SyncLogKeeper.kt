package nl.tufein.openconnect.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nl.tufein.openconnect.core.SyncLog
import nl.tufein.openconnect.core.SyncLogEntry

/** Holds the record of the last syncs for the screen and writes it away after every change. [load] and [save] are the phone's storage. */
class SyncLogKeeper(private val load: () -> String, private val save: (String) -> Unit) {
    private val _entries = MutableStateFlow(SyncLog.decode(load()))
    val entries: StateFlow<List<SyncLogEntry>> = _entries.asStateFlow()

    /** Called from sync threads and from the background job, so the read-modify-write is one step. */
    @Synchronized
    fun record(entry: SyncLogEntry) {
        val next = SyncLog.add(_entries.value, entry)
        _entries.value = next
        save(SyncLog.encode(next))
    }

    @Synchronized
    fun clear() {
        _entries.value = emptyList()
        save("")
    }
}
