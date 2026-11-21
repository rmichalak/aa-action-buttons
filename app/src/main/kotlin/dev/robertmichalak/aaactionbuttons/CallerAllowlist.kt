package dev.robertmichalak.aaactionbuttons

object CallerAllowlist {
    val packages: Set<String> = setOf(
        "com.google.android.projection.gearhead",
        "com.google.android.autosimulator",
        "com.android.car.media",
        "com.google.android.car.media",
    )

    fun allows(callerPackage: String, selfPackage: String, debugBuild: Boolean): Boolean {
        if (debugBuild) return true
        if (callerPackage == selfPackage) return true
        return callerPackage in packages
    }
}
