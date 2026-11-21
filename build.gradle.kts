plugins {
    alias(libs.plugins.android.application) apply false
}

tasks.register<Exec>("adbConnect") {
    group = "install"
    description = "Connect adb to the wireless debugging endpoints in .adb-devices."
    workingDir = rootDir
    commandLine("bash", "scripts/adb-connect.sh")
}

tasks.register<Exec>("adbInstall") {
    group = "install"
    description = "Build the release APK, reconnect .adb-devices when present, and install on every connected adb device."
    workingDir = rootDir
    dependsOn(":app:assembleRelease")
    commandLine("bash", "scripts/adb-install.sh")
}
