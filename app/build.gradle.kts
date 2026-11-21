import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.android.application)
}

val releaseKeystoreProperties = Properties().apply {
    val file = rootProject.file("signing/keystore.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}

fun releaseSigningValue(fileKey: String, envName: String): String? =
    System.getenv(envName)?.takeIf { it.isNotBlank() }
        ?: releaseKeystoreProperties.getProperty(fileKey)?.takeIf { it.isNotBlank() }

val releaseStoreFile = releaseSigningValue("storeFile", "ANDROID_KEYSTORE_FILE")
val releaseStorePassword = releaseSigningValue("storePassword", "ANDROID_KEYSTORE_PASSWORD")
val releaseKeyAlias = releaseSigningValue("keyAlias", "ANDROID_KEY_ALIAS")
val releaseKeyPassword = releaseSigningValue("keyPassword", "ANDROID_KEY_PASSWORD")
val hasReleaseKey = !releaseStoreFile.isNullOrBlank() &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank() &&
    rootProject.file(releaseStoreFile).isFile

android {
    namespace = "dev.robertmichalak.aaactionbuttons"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "dev.robertmichalak.aaactionbuttons"
        minSdk = 28
        targetSdk = 36
        val versionProperties = Properties().apply {
            rootProject.file("version.properties").inputStream().use(::load)
        }
        versionCode = versionProperties.getProperty("versionCode").toInt()
        versionName = versionProperties.getProperty("versionName")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        buildConfig = true
    }

    // Debug builds always use signing/debug.keystore. Release builds use the
    // release key when it is configured, and fail otherwise.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("signing/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
            storeType = "pkcs12"
        }
        if (hasReleaseKey) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                storeType = "pkcs12"
            }
        }
    }

    buildTypes {
        // Installable APK: not debuggable, so onGetRoot uses the host allowlist.
        // Debug builds allow any media-browser caller for DHU / local testing.
        release {
            isMinifyEnabled = false
            if (hasReleaseKey) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

// AGP only wires unit tests for debug unless release opts in.
androidComponents {
    beforeVariants(selector().withBuildType("release")) { builder ->
        builder.hostTests.getValue("UnitTest").enable = true
    }
}

dependencies {
    implementation(libs.androidx.media)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.json)
}

tasks.register<Exec>("ensureDebugKeystore") {
    group = "build"
    description = "Create signing/debug.keystore when it is missing."
    workingDir = rootDir
    commandLine("bash", "scripts/ensure-debug-keystore.sh")
}

tasks.named("preBuild").configure {
    dependsOn("ensureDebugKeystore")
}

abstract class RequireReleaseSigningTask : DefaultTask() {
    @get:Input
    abstract val configured: Property<Boolean>

    @TaskAction
    fun check() {
        if (!configured.get()) {
            throw GradleException(
                "assembleRelease requires signing/release.keystore and signing/keystore.properties, " +
                    "or ANDROID_KEYSTORE_FILE, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS, and ANDROID_KEY_PASSWORD. " +
                    "assembleDebug does not need a release key.",
            )
        }
    }
}

tasks.register<RequireReleaseSigningTask>("requireReleaseSigning") {
    group = "build"
    description = "Fail assembleRelease when the release keystore is not configured."
    configured.set(hasReleaseKey)
}

tasks.configureEach {
    if (name == "packageRelease" || name == "assembleRelease") {
        dependsOn("requireReleaseSigning")
    }
}
