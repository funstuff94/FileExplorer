package com.droid.fileexplorer.data

import com.droid.fileexplorer.model.ClipboardState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared clipboard and cross-window file event bus.
 * Enables seamless cut/copy/paste and real-time directory synchronization
 * between multiple application windows and tabs.
 */
object SharedClipboardManager {
    private val _clipboardState = MutableStateFlow<ClipboardState?>(null)
    val clipboardState: StateFlow<ClipboardState?> = _clipboardState.asStateFlow()

    // Event bus to broadcast directory changes (e.g. after cut, paste, delete, move, create)
    // so any open application window currently displaying that directory automatically refreshes
    private val _directoryChangeEvents = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val directoryChangeEvents: SharedFlow<String> = _directoryChangeEvents.asSharedFlow()

    fun setClipboard(state: ClipboardState?) {
        _clipboardState.value = state
    }

    fun getClipboard(): ClipboardState? = _clipboardState.value

    fun clearClipboard() {
        _clipboardState.value = null
    }

    fun notifyDirectoryChanged(path: String) {
        if (path.isNotEmpty()) {
            _directoryChangeEvents.tryEmit(path)
        }
    }
}
