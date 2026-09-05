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
        versionCode = 1
        versionName = "0.1.0"
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
    implementation("org.mozilla.geckoview:geckoview-nightly:157.0.20260904092011")
}
