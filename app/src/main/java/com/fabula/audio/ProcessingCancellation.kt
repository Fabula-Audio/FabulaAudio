package com.fabula.audio

import java.util.concurrent.CancellationException

/** One token per foreground processing job; never cancels unrelated FFmpeg sessions. */
class ProcessingCancellation {
    @Volatile var isCancelled = false
        private set
    private var cancelAction: (() -> Unit)? = null

    @Synchronized fun cancel() {
        if (isCancelled) return
        isCancelled = true
        cancelAction?.invoke()
    }

    @Synchronized fun attach(action: () -> Unit) {
        cancelAction = action
        if (isCancelled) action()
    }

    @Synchronized fun detach() { cancelAction = null }

    fun check() {
        if (isCancelled) throw CancellationException("Обработка отменена")
    }
}
