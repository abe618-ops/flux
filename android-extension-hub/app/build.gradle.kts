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
        versionCode = 2
        versionName = "0.1.1"
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
    // Stable GeckoView is intentionally pinned. Nightly 157 currently pulls
    // Android API 37 / AGP 9.1-era dependencies, while Flux WebOS targets
    // stable Android 16 (API 36) for the first installable MVP.
    implementation("org.mozilla.geckoview:geckoview:145.0.20251215155055")
}
