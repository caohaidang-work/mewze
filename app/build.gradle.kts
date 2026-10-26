@file:Suppress("UnstableApiUsage")

import java.util.Properties

// Spotify Client ID (public, not a secret) comes from, in order: -PSPOTIFY_CLIENT_ID, the
// SPOTIFY_CLIENT_ID env var, or SPOTIFY_CLIENT_ID in local.properties. If empty, the app asks for it.
val spotifyClientId: String = (
    providers.gradleProperty("SPOTIFY_CLIENT_ID").orNull
        ?: System.getenv("SPOTIFY_CLIENT_ID")
        ?: rootProject.file("local.properties").takeIf { it.exists() }?.let { f ->
            Properties().apply { f.inputStream().use(::load) }.getProperty("SPOTIFY_CLIENT_ID")
        }
        ?: ""
).trim()

// AI lyrics translation (OpenAI-compatible API with a free tier). Never commit the key: put it in
// local.properties (git-ignored) or the env / -P property. Same lookup order as above.
//   AI_TRANSLATE_KEY       API key (required, otherwise translation is silently disabled)
//   AI_TRANSLATE_PROVIDER  GEMINI (default), GROQ, CEREBRAS, MISTRAL or OPENROUTER
//   AI_TRANSLATE_MODEL     optional, defaults to the provider's default model
//   AI_TRANSLATE_FALLBACKS optional backup providers, tried in order when the primary fails or hits its
//                          rate limit. Comma-separated PROVIDER=key or PROVIDER=key|model, e.g.
//                          GROQ=gsk_xxx,CEREBRAS=csk_xxx,MISTRAL=xxx|mistral-small-latest
fun secret(name: String): String = (
    providers.gradleProperty(name).orNull
        ?: System.getenv(name)
        ?: rootProject.file("local.properties").takeIf { it.exists() }?.let { f ->
            Properties().apply { f.inputStream().use(::load) }.getProperty(name)
        }
        ?: ""
).trim()
val aiTranslateKey = secret("AI_TRANSLATE_KEY")
val aiTranslateProvider = secret("AI_TRANSLATE_PROVIDER")
val aiTranslateModel = secret("AI_TRANSLATE_MODEL")
val aiTranslateFallbacks = secret("AI_TRANSLATE_FALLBACKS")

// Release signing (stable key, required so in-app updates can install over the previous version).
// RELEASE_KEYSTORE_FILE / RELEASE_KEYSTORE_PASSWORD / RELEASE_KEY_ALIAS / RELEASE_KEY_PASSWORD
// come from env or local.properties. If any is missing the release build stays unsigned.
val releaseKeystoreFile = secret("RELEASE_KEYSTORE_FILE")
val releaseKeystorePassword = secret("RELEASE_KEYSTORE_PASSWORD")
val releaseKeyAlias = secret("RELEASE_KEY_ALIAS")
val releaseKeyPassword = secret("RELEASE_KEY_PASSWORD")
val hasReleaseSigning = listOf(releaseKeystoreFile, releaseKeystorePassword, releaseKeyAlias, releaseKeyPassword).all { it.isNotEmpty() }

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("kapt")
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.caohaidang.edward.music"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "com.caohaidang.edward.music"
        minSdk = 24
        targetSdk = 35
        versionCode = 6
        versionName = "2.1.3"
        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"$spotifyClientId\"")
        buildConfigField("String", "AI_TRANSLATE_KEY", "\"$aiTranslateKey\"")
        buildConfigField("String", "AI_TRANSLATE_PROVIDER", "\"$aiTranslateProvider\"")
        buildConfigField("String", "AI_TRANSLATE_MODEL", "\"$aiTranslateModel\"")
        buildConfigField("String", "AI_TRANSLATE_FALLBACKS", "\"$aiTranslateFallbacks\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystoreFile)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
        getByName("debug") {
            if (System.getenv("MUSIC_DEBUG_SIGNING_STORE_PASSWORD") != null) {
                storeFile = file(System.getenv("MUSIC_DEBUG_KEYSTORE_FILE"))
                storePassword = System.getenv("MUSIC_DEBUG_SIGNING_STORE_PASSWORD")
                keyAlias = "debug"
                keyPassword = System.getenv("MUSIC_DEBUG_SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            isCrunchPngs = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }
    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + "-Xcontext-receivers"
        jvmTarget = "17"
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }

    dependenciesInfo {
        includeInApk = false

        includeInBundle = false
    }
    lint {
        lintConfig = file("app/lint.xml")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.guava)
    implementation(libs.coroutines.guava)
    implementation(libs.concurrent.futures)

    implementation(libs.activity)
    implementation(libs.navigation)
    implementation(libs.hilt.navigation)
    implementation(libs.datastore)

    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.util)
    implementation(libs.compose.ui.tooling)
    implementation(libs.compose.animation)
    implementation(libs.compose.animation.graphics)
    implementation(libs.compose.reorderable)

    implementation(libs.viewmodel)
    implementation(libs.viewmodel.compose)

    implementation(libs.material3)
    implementation(libs.palette)
    implementation(projects.materialColorUtilities)
    implementation(libs.squigglyslider)

    implementation(libs.coil)

    implementation(libs.shimmer)

    implementation(libs.media3)
    implementation(libs.media3.session)
    implementation(libs.media3.okhttp)

    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.room.ktx)

    implementation(libs.apache.lang3)

    implementation(libs.hilt)
    kapt(libs.hilt.compiler)

    implementation(projects.innertube)
    implementation(projects.kugou)
    implementation(projects.lrclib)

    implementation(libs.ktor.client.core)

    coreLibraryDesugaring(libs.desugaring)

    implementation(libs.timber)

    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    implementation("io.ktor:ktor-client-okhttp:2.3.7")
}
