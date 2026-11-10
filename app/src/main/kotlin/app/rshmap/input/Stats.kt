package app.rshmap.input

/** Lock-free live numbers for the diagnostics screen. Written from several threads, read from the UI. */
object Stats {
    @Volatile var mouseHz = 0f
    @Volatile var injectOverheadMs = 0f
    @Volatile var chunkMs = 0f
    @Volatile var backend = "-"
    @Volatile var captured = false
    @Volatile var gameMode = false
    @Volatile var devices = ""
    @Volatile var activeTouches = 0
}
