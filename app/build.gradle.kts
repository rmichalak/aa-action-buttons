plugins {
    alias(libs.plugins.android.application)
}

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
        versionCode = 5
        versionName = "0.4.0"
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

    // Gitignored file in the workspace mount. Rebuilding the container does not
    // replace it, so installs keep a matching signature.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("signing/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
            storeType = "pkcs12"
        }
    }

    buildTypes {
        // Installable CI/public APK: not debuggable, so onGetRoot uses the host allowlist
        // (debug builds allow any media-browser caller for DHU / local testing).
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.media)
    implementation(libs.kotlinx.coroutines.android)
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
