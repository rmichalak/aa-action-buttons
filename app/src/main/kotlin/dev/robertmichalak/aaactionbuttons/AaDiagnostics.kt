package dev.robertmichalak.aaactionbuttons

import android.content.Context

object AaDiagnostics {
    private const val PREFS = "aa_action_buttons_aa"
    private const val KEY_LIMIT = "custom_action_limit"
    private const val KEY_SUPPORTED = "custom_actions_supported"
    private const val KEY_CLIENT = "last_client"
    private const val KEY_HINT_KEYS = "root_hint_keys"

    fun save(
        context: Context,
        clientPackage: String,
        actionLimit: Int,
        supported: Boolean,
        hintKeys: String,
    ) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CLIENT, clientPackage)
            .putInt(KEY_LIMIT, actionLimit)
            .putBoolean(KEY_SUPPORTED, supported)
            .putString(KEY_HINT_KEYS, hintKeys)
            .apply()
    }

    fun summary(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_LIMIT)) {
            return "AA diagnostics: open AA Action Buttons once from Android Auto, then Refresh."
        }
        return buildString {
            append("AA client=").append(prefs.getString(KEY_CLIENT, "?"))
            append(" customActionLimit=").append(prefs.getInt(KEY_LIMIT, 0))
            append(" supported=").append(prefs.getBoolean(KEY_SUPPORTED, false))
            append("\nhints=").append(prefs.getString(KEY_HINT_KEYS, ""))
        }
    }
}
