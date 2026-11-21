package dev.robertmichalak.aaactionbuttons

import dev.robertmichalak.aaactionbuttons.model.Action
import dev.robertmichalak.aaactionbuttons.model.ButtonConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class ButtonCodecTest {
    @Test
    fun roundTripActionShapes() {
        val buttons = listOf(
            ButtonConfig(
                id = "tasker-1",
                label = "Garage",
                action = Action.Tasker(taskName = "Open Garage"),
            ),
            ButtonConfig(
                id = "http-1",
                label = "Webhook",
                action = Action.Http(
                    method = "POST",
                    url = "https://example.com/hook",
                    headers = mapOf("Authorization" to "Bearer t", "X-Trace" to "1"),
                    body = """{"on":true}""",
                ),
            ),
            ButtonConfig(
                id = "http-2",
                label = "Ping",
                action = Action.Http(
                    method = "GET",
                    url = "https://example.com/ping",
                    body = null,
                ),
            ),
            ButtonConfig(
                id = "intent-1",
                label = "Maps",
                action = Action.IntentAction(
                    action = "android.intent.action.VIEW",
                    packageName = "com.google.android.apps.maps",
                    extras = mapOf("query" to "home"),
                    startActivity = true,
                ),
            ),
            ButtonConfig(
                id = "intent-2",
                label = "Broadcast",
                action = Action.IntentAction(
                    action = "dev.example.ACTION",
                    packageName = null,
                    startActivity = false,
                ),
            ),
        )
        assertEquals(buttons, ButtonCodec.decode(ButtonCodec.encode(buttons)))
    }

    @Test
    fun decodeStoredFallsBackToDefaults() {
        assertEquals(ButtonCatalog.defaults, ButtonCodec.decodeStored(null))
        assertEquals(ButtonCatalog.defaults, ButtonCodec.decodeStored(""))
        assertEquals(ButtonCatalog.defaults, ButtonCodec.decodeStored("not-json"))
        assertEquals(ButtonCatalog.defaults, ButtonCodec.decodeStored("[]"))
        val unknown = """[{"id":"x","label":"X","action":{"type":"unknown"}}]"""
        assertEquals(ButtonCatalog.defaults, ButtonCodec.decodeStored(unknown))
    }

    @Test
    fun decodeThrowsOnUnknownActionType() {
        val raw = """[{"id":"x","label":"X","action":{"type":"unknown"}}]"""
        try {
            ButtonCodec.decode(raw)
            fail("expected unknown action type")
        } catch (error: IllegalStateException) {
            assertEquals("Unknown action type", error.message)
        }
    }

    @Test
    fun decodeStoredReturnsNonEmptyList() {
        val raw = """[{"id":"b1","label":"Lights","action":{"type":"tasker","taskName":"Lights On"}}]"""
        val expected = listOf(
            ButtonConfig(
                id = "b1",
                label = "Lights",
                action = Action.Tasker(taskName = "Lights On"),
            ),
        )
        assertEquals(expected, ButtonCodec.decodeStored(raw))
    }
}
