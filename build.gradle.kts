plugins {
    alias(libs.plugins.android.application) apply false
}

tasks.register<Exec>("adbConnect") {
    group = "install"
    description = "Connect adb to the wireless debugging endpoints in .adb-devices."
    workingDir = rootDir
    commandLine("bash", "scripts/adb-connect.sh")
}
