package app.rshmap.core

/** Test helper: records every touch event as a readable string. */
class RecordingSink : TouchSink {
    val events = ArrayList<String>()
    private val live = HashMap<Int, Pair<Float, Float>>()

    override fun down(id: Int, x: Float, y: Float) { events.add("D$id"); live[id] = x to y }
    override fun move(id: Int, x: Float, y: Float) { events.add("M$id"); live[id] = x to y }
    override fun up(id: Int) { events.add("U$id"); live.remove(id) }

    fun pos(id: Int): Pair<Float, Float>? = live[id]
    val activeCount: Int get() = live.size
}
