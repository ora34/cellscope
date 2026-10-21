package app.cellscope

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CellViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = CellRepository(app)

    private val _snapshot = MutableStateFlow<Snapshot?>(null)
    val snapshot: StateFlow<Snapshot?> = _snapshot

    /** История для экрана «Графики» (последние 120 отсчётов). */
    private val _history = MutableStateFlow<List<Snapshot>>(emptyList())
    val history: StateFlow<List<Snapshot>> = _history

    /** Журнал: каждая смена основной соты. */
    private val _journal = MutableStateFlow<List<Snapshot>>(emptyList())
    val journal: StateFlow<List<Snapshot>> = _journal

    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            while (isActive) {
                val s = withContext(Dispatchers.IO) { repo.read() }
                _snapshot.value = s
                _history.value = (_history.value + s).takeLast(120)
                val last = _journal.value.lastOrNull()?.serving
                val cur = s.serving
                if (cur != null && (last == null || last.pci != cur.pci || last.channel != cur.channel || last.tech != cur.tech)) {
                    _journal.value = _journal.value + s
                }
                delay(2000)
            }
        }
    }

    fun stop() {
        job?.cancel()
    }
}
