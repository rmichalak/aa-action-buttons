package dev.robertmichalak.aaactionbuttons

import dev.robertmichalak.aaactionbuttons.model.ButtonConfig

/**
 * Defaults when the user has not saved a custom list yet.
 * Empty on purpose — configure buttons on the phone (or import from Tasker).
 */
object ButtonCatalog {
    val defaults: List<ButtonConfig> = emptyList()
}
