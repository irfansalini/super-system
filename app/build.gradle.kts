plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.superremote.ir"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.superremote.ir"
        minSdk = 23
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }

    signingConfigs {
        // A fixed key committed to the repo so every build can be installed
        // over the previous one without uninstalling (keeps your saved codes).
        create("fixed") {
            storeFile = file("superremote.keystore")
            storePassword = "superremote"
            keyAlias = "superremote"
            keyPassword = "superremote"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("fixed")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixed")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
