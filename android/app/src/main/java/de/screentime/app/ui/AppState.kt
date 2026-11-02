package de.screentime.app.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.screentime.app.data.Api
import de.screentime.app.data.Status
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Gemeinsamer Zustand der App: letzter Status vom PC, Ladeanzeige, Meldungen. */
class AppState(
    val api: Api,
    private val scope: CoroutineScope,
    val snackbar: SnackbarHostState,
) {
    var status by mutableStateOf<Status?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** Führt [block] im Hintergrund aus, lädt danach den Status neu und zeigt [doneMessage]. */
    fun act(doneMessage: String? = null, onSuccess: () -> Unit = {}, block: () -> Unit = {}) {
        if (loading) return
        scope.launch {
            loading = true
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    block()
                    api.status()
                }
            }
            loading = false
            result.onSuccess { s ->
                status = s
                error = null
                onSuccess()
                if (doneMessage != null) snackbar.showSnackbar(doneMessage)
            }.onFailure { e ->
                val msg = e.message ?: e.javaClass.simpleName
                if (status == null) error = msg
                snackbar.showSnackbar(msg)
            }
        }
    }

    fun refresh() = act()
}
