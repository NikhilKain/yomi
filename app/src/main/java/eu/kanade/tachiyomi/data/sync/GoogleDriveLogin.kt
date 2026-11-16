package eu.kanade.tachiyomi.data.sync

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Observable state for the Google Drive sign-in.
 *
 * The work itself lives in [SyncLoginJob] rather than here, because the flow spans leaving the app
 * for a browser and a plain background coroutine gets frozen and loses network in that window.
 * This object only carries the outcome back to the settings screen.
 */
object GoogleDriveLogin {

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    fun start(context: Context) {
        if (SyncLoginJob.isRunning(context)) return
        _state.value = State.Idle
        SyncLoginJob.start(context.applicationContext)
    }

    fun cancel(context: Context) {
        SyncLoginJob.stop(context.applicationContext)
        _state.value = State.Idle
    }

    internal fun report(state: State) {
        _state.value = state
    }

    /**
     * Clears a terminal state once the UI has shown it, so it is not reported twice.
     */
    fun consume() {
        if (_state.value is State.Success || _state.value is State.Failed) {
            _state.value = State.Idle
        }
    }

    sealed interface State {
        data object Idle : State
        data object WaitingForBrowser : State
        data object Success : State
        data class Failed(val reason: String) : State
    }
}
