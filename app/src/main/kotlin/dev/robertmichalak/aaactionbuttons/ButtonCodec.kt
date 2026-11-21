package dev.robertmichalak.aaactionbuttons

import dev.robertmichalak.aaactionbuttons.model.Action
import dev.robertmichalak.aaactionbuttons.model.ButtonConfig
import org.json.JSONArray
import org.json.JSONObject

object ButtonCodec {
    fun encode(buttons: List<ButtonConfig>): String {
        val array = JSONArray()
        buttons.forEach { button ->
            array.put(
                JSONObject().apply {
                    put("id", button.id)
                    put("label", button.label)
                    put("action", encodeAction(button.action))
                },
            )
        }
        return array.toString()
    }

    fun decode(raw: String): List<ButtonConfig> {
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(
                    ButtonConfig(
                        id = obj.getString("id"),
                        label = obj.getString("label"),
                        action = decodeAction(obj.getJSONObject("action")),
                    ),
                )
            }
        }
    }

    fun decodeStored(raw: String?): List<ButtonConfig> {
        raw ?: return ButtonCatalog.defaults
        return runCatching { decode(raw) }
            .getOrElse { ButtonCatalog.defaults }
            .ifEmpty { ButtonCatalog.defaults }
    }

    private fun encodeAction(action: Action): JSONObject = when (action) {
        is Action.Tasker -> JSONObject()
            .put("type", "tasker")
            .put("taskName", action.taskName)

        is Action.Http -> JSONObject()
            .put("type", "http")
            .put("method", action.method)
            .put("url", action.url)
            .put("headers", JSONObject(action.headers))
            .put("body", action.body)

        is Action.IntentAction -> JSONObject()
            .put("type", "intent")
            .put("action", action.action)
            .put("packageName", action.packageName)
            .put("extras", JSONObject(action.extras))
            .put("startActivity", action.startActivity)
    }

    private fun decodeAction(obj: JSONObject): Action = when (obj.getString("type")) {
        "tasker" -> Action.Tasker(taskName = obj.getString("taskName"))
        "http" -> Action.Http(
            method = obj.optString("method", "GET"),
            url = obj.getString("url"),
            headers = obj.optJSONObject("headers")?.toStringMap().orEmpty(),
            body = if (obj.has("body") && !obj.isNull("body")) obj.getString("body") else null,
        )
        "intent" -> Action.IntentAction(
            action = obj.getString("action"),
            packageName = obj.optString("packageName").takeIf { it.isNotEmpty() },
            extras = obj.optJSONObject("extras")?.toStringMap().orEmpty(),
            startActivity = obj.optBoolean("startActivity", false),
        )
        else -> error("Unknown action type")
    }

    private fun JSONObject.toStringMap(): Map<String, String> = buildMap {
        keys().forEach { key -> put(key, getString(key)) }
    }
}
