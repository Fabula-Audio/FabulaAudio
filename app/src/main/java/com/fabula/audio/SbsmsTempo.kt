package com.fabula.audio

/** The SBSMS engine from Audacity 3.4.2. Only used by stages ×144 and ×432. */
object SbsmsTempo {
    init { System.loadLibrary("fabula_tempo") }
    fun interface ProgressListener {
        fun onProgress(completed: Long, total: Long)
    }

    external fun process(input: String, output: String, tempo: Int, listener: ProgressListener): Long
}
