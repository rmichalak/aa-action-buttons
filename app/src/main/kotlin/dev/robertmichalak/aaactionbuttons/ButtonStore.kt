package dev.robertmichalak.aaactionbuttons

import android.content.Context
import android.content.SharedPreferences
import dev.robertmichalak.aaactionbuttons.model.ButtonConfig

class ButtonStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<ButtonConfig> = ButtonCodec.decodeStored(prefs.getString(KEY_BUTTONS, null))

    fun save(buttons: List<ButtonConfig>) {
        prefs.edit().putString(KEY_BUTTONS, ButtonCodec.encode(buttons)).apply()
    }

    fun find(id: String): ButtonConfig? = load().find { it.id == id }

    companion object {
        private const val PREFS_NAME = "aa_action_buttons"
        private const val KEY_BUTTONS = "buttons"

        const val ACTION_BUTTONS_CHANGED = "dev.robertmichalak.aaactionbuttons.ACTION_BUTTONS_CHANGED"
    }
}
