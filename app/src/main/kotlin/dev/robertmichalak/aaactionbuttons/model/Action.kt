package dev.robertmichalak.aaactionbuttons.model

sealed class Action {
    data class Tasker(val taskName: String) : Action()

    data class Http(
        val method: String,
        val url: String,
        val headers: Map<String, String> = emptyMap(),
        val body: String? = null,
    ) : Action()

    data class IntentAction(
        val action: String,
        val packageName: String? = null,
        val extras: Map<String, String> = emptyMap(),
        val startActivity: Boolean = false,
    ) : Action()
}
