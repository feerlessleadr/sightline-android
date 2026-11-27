import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// A release workflow can pass -PappVersion=1.2.3; local builds use 0.0.1.
val appVersion: String = (findProperty("appVersion") as String?) ?: "0.0.1"
val appVersionCode: Int = appVersion.split(".").let { parts ->
    val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
    major * 1_000_000 + minor * 1_000 + patch
}.coerceAtLeast(1)

android {
    namespace = "com.feerlessleadr.sightline"
    compileSdk { version = release(37) { minorApiLevel = 1 } }

    defaultConfig {
        applicationId = "com.feerlessleadr.sightline"
        minSdk = 30
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersion
    }

    // Release signing. The key is never in the repository: the release workflow passes it in environment variables
    // (from GitHub secrets), and a local build can read keystore.properties (git-ignored) in the project folder.
    // See docs/release.md. Without either, the release build is simply left unsigned.
    val keystoreProps = Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }
    fun signingValue(env: String, prop: String): String? = System.getenv(env)?.takeIf { it.isNotBlank() } ?: keystoreProps.getProperty(prop)
    val releaseStoreFile = signingValue("SIGNING_STORE_FILE", "storeFile")
    val releaseSigningReady = releaseStoreFile != null && file(releaseStoreFile).exists() &&
        signingValue("SIGNING_STORE_PASSWORD", "storePassword") != null &&
        signingValue("SIGNING_KEY_ALIAS", "keyAlias") != null &&
        signingValue("SIGNING_KEY_PASSWORD", "keyPassword") != null

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = signingValue("SIGNING_STORE_PASSWORD", "storePassword")
                keyAlias = signingValue("SIGNING_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("SIGNING_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = when {
                releaseSigningReady -> signingConfigs.getByName("release")
                // For trying the shrunk release build on a phone before there is a real key: -PreleaseDebugSigned=true.
                // It gets its own app id (.rtest), so it sits next to the debug build and can never be mistaken for, or
                // stop the install of, the real signed release (Android refuses an update signed with a different key).
                findProperty("releaseDebugSigned") == "true" -> {
                    applicationIdSuffix = ".rtest"
                    versionNameSuffix = "-rtest"
                    signingConfigs.getByName("debug")
                }
                else -> null
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.biometric)
    // Biometric 1.1.0 pulls in an old Fragment (1.2.5) whose FragmentActivity rejects the activity result request codes.
    implementation(libs.androidx.fragment)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}
