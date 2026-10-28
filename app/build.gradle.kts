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
// Tolerates a BOM, stray spaces and values wrapped in quotes (AI_TRANSLATE_KEY="abc" would otherwise
// send the quotes as part of the key and every request would be rejected).
fun secret(name: String): String = (
    providers.gradleProperty(name).orNull
        ?: System.getenv(name)
        ?: rootProject.file("local.properties").takeIf { it.exists() }?.let { f ->
            Properties().apply { f.reader(Charsets.UTF_8).use(::load) }.getProperty(name)
        }
        ?: ""
).trim().removePrefix("\uFEFF").trim().removeSurrounding("\"").removeSurrounding("'").trim()

// Makes a value safe to place inside a quoted BuildConfig string.
fun String.asJavaLiteral(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""
val aiTranslateKey = secret("AI_TRANSLATE_KEY")
val aiTranslateProvider = secret("AI_TRANSLATE_PROVIDER")
val aiTranslateModel = secret("AI_TRANSLATE_MODEL")
val aiTranslateFallbacks = secret("AI_TRANSLATE_FALLBACKS")
if (aiTranslateKey.isEmpty()) {
    logger.warn(
        "AI_TRANSLATE_KEY is empty: lyrics translation will be disabled. Add AI_TRANSLATE_KEY=<key> to " +
            "${rootProject.file("local.properties").absolutePath} (no quotes), then rebuild."
    )
}

// Release signing (stable key, required so in-app updates can install over the previous version).
// RELEASE_KEYSTORE_FILE / RELEASE_KEYSTORE_PASSWORD / RELEASE_KEY_ALIAS / RELEASE_KEY_PASSWORD
// come from env or local.properties. If any is missing the release build stays unsigned.
// Local fallback: <repo>/keystore.properties (git-ignored, see keystore.properties.example and SIGNING.md)
// with storeFile / storePassword / keyAlias / keyPassword. Env vars and local.properties win over it.
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}
fun signingValue(name: String, fileKey: String): String =
    secret(name).ifEmpty { keystoreProps.getProperty(fileKey, "").trim() }
val releaseKeystoreFile = signingValue("RELEASE_KEYSTORE_FILE", "storeFile")
val releaseKeystorePassword = signingValue("RELEASE_KEYSTORE_PASSWORD", "storePassword")
val releaseKeyAlias = signingValue("RELEASE_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = signingValue("RELEASE_KEY_PASSWORD", "keyPassword")
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
        versionCode = 8
        versionName = "2.2.0"
        buildConfigField("String", "SPOTIFY_CLIENT_ID", spotifyClientId.asJavaLiteral())
        buildConfigField("String", "AI_TRANSLATE_KEY", aiTranslateKey.asJavaLiteral())
        buildConfigField("String", "AI_TRANSLATE_PROVIDER", aiTranslateProvider.asJavaLiteral())
        buildConfigField("String", "AI_TRANSLATE_MODEL", aiTranslateModel.asJavaLiteral())
        buildConfigField("String", "AI_TRANSLATE_FALLBACKS", aiTranslateFallbacks.asJavaLiteral())
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
    sourceSets {
        // Room's exported schemas, so MigrationTestHelper can find them in unit tests (Robolectric).
        getByName("test").assets.srcDir("$projectDir/schemas")
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

    // Unit tests (JVM): ./gradlew testDebugUnitTest
    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
    testImplementation("androidx.room:room-testing:${libs.versions.room.get()}")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")

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
