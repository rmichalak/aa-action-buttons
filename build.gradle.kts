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

tasks.register<Exec>("adbInstallDebug") {
    group = "install"
    description = "Build the debug APK, reconnect .adb-devices when present, and install on every connected adb device."
    workingDir = rootDir
    dependsOn(":app:assembleDebug")
    commandLine("bash", "scripts/adb-install.sh", "app/build/outputs/apk/debug/app-debug.apk")
}
