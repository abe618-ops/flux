plugins {
    id("com.android.application")
}

android {
    namespace = "com.flux.webos"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.flux.webos"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "0.2.0"
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    // Stable GeckoView pinned for Android 16/API 36. ARM64-only packaging
    // keeps the phone build substantially smaller than the universal APK.
    implementation("org.mozilla.geckoview:geckoview:145.0.20251124145406")
}
