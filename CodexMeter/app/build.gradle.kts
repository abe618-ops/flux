plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun stringPropertyOrEnv(propertyName: String, envName: String): String? =
    (project.findProperty(propertyName) as String?)?.takeIf { it.isNotBlank() }
        ?: System.getenv(envName)?.takeIf { it.isNotBlank() }

val phoneRuntimeArchivePath = stringPropertyOrEnv(
    "codexMeter.phoneRuntime.archive",
    "CODEXMETER_PHONE_RUNTIME_ARCHIVE",
)
val phoneRuntimeArchive = phoneRuntimeArchivePath?.let(::file)?.takeIf { it.exists() }
val phoneRuntimePackaged = phoneRuntimeArchive != null
val phoneRuntimeVersion = stringPropertyOrEnv(
    "codexMeter.phoneRuntime.version",
    "CODEXMETER_PHONE_RUNTIME_VERSION",
) ?: "codex-termux-0.150.1"
val generatedPhoneRuntimeAssets = layout.buildDirectory.dir("generated/phoneRuntimeAssets")

val preparePhoneRuntimeAssets by tasks.registering {
    outputs.dir(generatedPhoneRuntimeAssets)
    inputs.property("phoneRuntimePackaged", phoneRuntimePackaged)
    inputs.property("phoneRuntimeVersion", phoneRuntimeVersion)
    phoneRuntimeArchive?.let { inputs.file(it) }

    doLast {
        val outputRoot = generatedPhoneRuntimeAssets.get().asFile
        val runtimeDir = outputRoot.resolve("phone-runtime")
        outputRoot.deleteRecursively()
        runtimeDir.mkdirs()
        val source = phoneRuntimeArchive ?: return@doLast
        val staging = temporaryDir.resolve("phone-runtime-staging").apply {
            deleteRecursively(); mkdirs()
        }
        when {
            source.name.endsWith(".zip", ignoreCase = true) -> copy {
                from(zipTree(source)); into(staging)
            }
            source.name.endsWith(".tgz", ignoreCase = true) || source.name.endsWith(".tar.gz", ignoreCase = true) -> copy {
                from(tarTree(resources.gzip(source))); into(staging)
            }
            else -> throw GradleException("Unsupported phone runtime archive: ${source.absolutePath}")
        }
        ant.withGroovyBuilder {
            "zip"(
                "destfile" to runtimeDir.resolve("codex-runtime-arm64.zip").absolutePath,
                "basedir" to staging.absolutePath,
            )
        }
    }
}

android {
    namespace = "com.codexmeter.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.codexmeter.app"
        minSdk = 28
        targetSdk = 35
        versionCode = 4
        versionName = "0.4.0-phone"
        buildConfigField("boolean", "CODEX_PHONE_RUNTIME_PACKAGED", phoneRuntimePackaged.toString())
        buildConfigField("String", "CODEX_PHONE_RUNTIME_VERSION", "\"$phoneRuntimeVersion\"")
        buildConfigField("String", "CODEX_PHONE_RUNTIME_ASSET", "\"phone-runtime/codex-runtime-arm64.zip\"")
        ndk { abiFilters += "arm64-v8a" }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    sourceSets.getByName("main").assets.srcDir(generatedPhoneRuntimeAssets.get().asFile)
}
tasks.named("preBuild") { dependsOn(preparePhoneRuntimeAssets) }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
