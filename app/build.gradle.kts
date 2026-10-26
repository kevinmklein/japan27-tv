plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing comes from environment variables (set by GitHub Actions from repo secrets).
// The keystore itself never lives in the repo. Without them, release builds fall back to the debug key.
val signingKeystore: String? = System.getenv("SIGNING_KEYSTORE")

android {
    namespace = "com.kleinfamily.japan27tv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kleinfamily.japan27tv"
        minSdk = 22
        targetSdk = 34
        // CI passes these; versionCode must go up for an update to install over the old APK.
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "0.1-dev"
    }

    signingConfigs {
        create("release") {
            if (signingKeystore != null) {
                storeFile = file(signingKeystore)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (signingKeystore != null) "release" else "debug")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
