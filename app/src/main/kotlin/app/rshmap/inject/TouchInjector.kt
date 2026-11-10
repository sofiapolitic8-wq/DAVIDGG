package app.rshmap.inject

import app.rshmap.core.TouchSink

/**
 * A backend that turns virtual touches (screen pixels, stable pointer ids) into real touch input.
 * All methods are called from the input thread.
 */
interface TouchInjector : TouchSink {
    val name: String

    /** Prepare the backend. Returns false when it cannot be used right now. */
    fun start(): Boolean

    fun setScreen(widthPx: Int, heightPx: Int)

    /** Called after every drain and every frame; send whatever changed. */
    fun flush()

    /** Lift every finger. Must be safe to call at any time, repeatedly. */
    fun releaseAll()

    fun stop()

    /** Invoked (on the input thread) when the backend fails; the service drops to a safe state. */
    var onError: ((String) -> Unit)?
}
