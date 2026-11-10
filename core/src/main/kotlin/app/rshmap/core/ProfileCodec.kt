package app.rshmap.core

import kotlinx.serialization.json.Json

/** JSON import / export with defensive clamping so hostile or hand-edited files cannot crash the engine. */
object ProfileCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
        isLenient = true
    }

    fun encode(p: Profile): String = json.encodeToString(Profile.serializer(), sanitize(p))

    /** @throws IllegalArgumentException on malformed input. */
    fun decode(text: String): Profile {
        val p = try {
            json.decodeFromString(Profile.serializer(), text.trim())
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid profile JSON: ${e.message}", e)
        }
        return sanitize(p)
    }

    private fun c01(v: Float): Float = if (v.isNaN()) 0f else v.coerceIn(0f, 1f)

    fun sanitize(p: Profile): Profile {
        val seen = HashSet<String>()
        val els = ArrayList<Element>()
        for ((i, e) in p.elements.take(64).withIndex()) {
            var id = e.id.ifBlank { "e$i" }.take(40)
            while (!seen.add(id)) id += "_"
            els.add(
                e.copy(
                    id = id,
                    label = e.label.take(24),
                    x = c01(e.x), y = c01(e.y),
                    w = e.w.coerceIn(0.01f, 1f), h = e.h.coerceIn(0.01f, 1f),
                    x2 = c01(e.x2), y2 = c01(e.y2),
                    keys = e.keys.take(8),
                    durationMs = e.durationMs.coerceIn(1, 10_000),
                    baseScale = e.baseScale.coerceIn(0.05f, 1f),
                    modifierScale = e.modifierScale.coerceIn(0.05f, 1f),
                    rampMs = e.rampMs.coerceIn(0, 2000),
                    burstMs = e.burstMs.coerceIn(30, 2000),
                    steps = e.steps.take(64).map { it.copy(x = c01(it.x), y = c01(it.y), ms = it.ms.coerceIn(1, 10_000)) },
                ),
            )
        }
        val m = p.mouse
        return p.copy(
            name = p.name.take(64),
            packageName = p.packageName.take(200),
            elements = els,
            mouse = m.copy(
                sensX = m.sensX.coerceIn(0.01f, 20f),
                sensY = m.sensY.coerceIn(0.01f, 20f),
                adsMultiplier = m.adsMultiplier.coerceIn(0.05f, 5f),
                smoothing = m.smoothing.coerceIn(0, 100),
                accelGain = m.accelGain.coerceIn(0f, 5f),
                deadZone = m.deadZone.coerceIn(0f, 50f),
            ),
        )
    }
}
