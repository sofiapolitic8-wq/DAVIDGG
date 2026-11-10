package app.rshmap.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileCodecTest {
    @Test fun everyTemplateRoundTrips() {
        for (t in Templates.all()) {
            val json = ProfileCodec.encode(t)
            val back = ProfileCodec.decode(json)
            assertEquals(t.name, back.name)
            assertEquals(t.packageName, back.packageName)
            assertEquals(t.elements.size, back.elements.size)
            assertEquals(t.elements.map { it.keys }, back.elements.map { it.keys })
            assertEquals(t.mouse, back.mouse)
        }
    }

    @Test fun templatesCoverRequestedGames() {
        val names = Templates.all().map { it.name }
        for (n in listOf("BGMI", "PUBG Mobile", "Free Fire", "COD Mobile", "Minecraft PE", "Genshin Impact", "Generic FPS", "Generic MOBA", "Generic Racing")) {
            assertTrue("missing $n", n in names)
        }
    }

    @Test fun sanitizeClampsHostileValues() {
        val p = Profile(
            name = "x",
            mouse = MouseSettings(sensX = 9999f, sensY = -5f, smoothing = 500, deadZone = 1e6f),
            elements = listOf(
                Element("a", ElementType.TAP, x = 5f, y = -3f, w = 99f, durationMs = -4, rampMs = 99999),
                Element("a", ElementType.TAP, x = 0.5f, y = 0.5f),
            ),
        )
        val s = ProfileCodec.sanitize(p)
        assertEquals(20f, s.mouse.sensX, 0f)
        assertEquals(0.01f, s.mouse.sensY, 0f)
        assertEquals(100, s.mouse.smoothing)
        assertEquals(50f, s.mouse.deadZone, 0f)
        assertEquals(1f, s.elements[0].x, 0f)
        assertEquals(0f, s.elements[0].y, 0f)
        assertEquals(1f, s.elements[0].w, 0f)
        assertEquals(1, s.elements[0].durationMs)
        assertEquals(2000, s.elements[0].rampMs)
        assertTrue(s.elements[0].id != s.elements[1].id)
    }

    @Test fun unknownFieldsAreIgnored() {
        val json = """{"name":"Hand made","future":42,"elements":[{"id":"t","type":"TAP","x":0.1,"y":0.2,"keys":[62],"extra":true}]}"""
        val p = ProfileCodec.decode(json)
        assertEquals("Hand made", p.name)
        assertEquals(listOf(62), p.elements[0].keys)
    }

    @Test(expected = IllegalArgumentException::class) fun malformedJsonThrows() {
        ProfileCodec.decode("{ this is not json")
    }
}
