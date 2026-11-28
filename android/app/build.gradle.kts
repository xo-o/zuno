import groovy.json.JsonSlurper
import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Auth backend (Next.js + Better Auth). Override in gradle.properties or with -P.
val authBaseUrl = providers.gradleProperty("zuno.authBaseUrl").getOrElse("http://10.0.2.2:3000").trimEnd('/')
val googleWebClientId = providers.gradleProperty("zuno.googleWebClientId").getOrElse("")
val magicLinkHost: String = URI(authBaseUrl).host

/**
 * Release (Play upload key) signing. Keep these out of the repo: set them in ~/.gradle/gradle.properties
 * or as ZUNO_RELEASE_* environment variables. Without them, release builds are left unsigned.
 */
fun releaseSigning(name: String): String? =
    providers.gradleProperty("zuno.release.$name").orNull
        ?: providers.environmentVariable("ZUNO_RELEASE_${name.uppercase()}").orNull
val releaseStoreFile = releaseSigning("storeFile")?.let { file(it.replaceFirst("~", System.getProperty("user.home"))) }
val releaseStorePassword = releaseSigning("storePassword")
val releaseKeyAlias = releaseSigning("keyAlias") ?: "zuno"
val releaseKeyPassword = releaseSigning("keyPassword") ?: releaseStorePassword
val hasReleaseSigning = releaseStoreFile?.exists() == true && releaseStorePassword != null

/**
 * Firebase (push) config read from app/google-services.json — the same resources the google-services
 * plugin would generate. Without the file the app still builds; push is simply off.
 */
val firebaseConfig: Map<String, String>? = file("google-services.json").takeIf { it.exists() }?.let { json ->
    @Suppress("UNCHECKED_CAST")
    val root = JsonSlurper().parse(json) as Map<String, Any?>
    val project = root["project_info"] as Map<String, Any?>
    val clients = root["client"] as List<Map<String, Any?>>
    val client = clients.firstOrNull {
        ((it["client_info"] as Map<String, Any?>)["android_client_info"] as Map<String, Any?>)["package_name"] == "com.zuno.ai"
    } ?: return@let null
    val clientInfo = client["client_info"] as Map<String, Any?>
    val apiKey = (client["api_key"] as List<Map<String, Any?>>).first()["current_key"] as String
    mapOf(
        "google_app_id" to clientInfo["mobilesdk_app_id"] as String,
        "gcm_defaultSenderId" to project["project_number"] as String,
        "google_api_key" to apiKey,
        "google_crash_reporting_api_key" to apiKey,
        "project_id" to project["project_id"] as String,
        "google_storage_bucket" to (project["storage_bucket"] as String? ?: ""),
    )
}

android {
    namespace = "com.zuno.ai"
    compileSdk = 36
    ndkVersion = "27.1.12297006"

    defaultConfig {
        applicationId = "com.zuno.ai"
        minSdk = 26
        targetSdk = 36
        versionCode = 12
        versionName = "1.0.10"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "AUTH_BASE_URL", "\"$authBaseUrl\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
        manifestPlaceholders["magicLinkHost"] = magicLinkHost
        buildConfigField("boolean", "PUSH_ENABLED", (firebaseConfig != null).toString())
        firebaseConfig?.forEach { (name, value) -> resValue("string", name, value) }
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
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
        compose = true
        buildConfig = true
        resValues = true
    }

    androidResources {
        localeFilters += listOf("en", "es", "fr", "de", "it", "pt-rBR", "nl", "pl")
    }
}

dependencies {
    // LiveKit Official Android SDK
    implementation("io.livekit:livekit-android:2.29.1")

    // Jetpack Compose BOM & UI
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")

    // Activity & Lifecycle Compose
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // OkHttp (for token HTTP fallback)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Sign in with Google (Credential Manager)
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Attachments: image loading and EXIF orientation
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Push notifications (Firebase Cloud Messaging)
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // Android Core & AppCompat (for Per-App Language APIs)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
}

// Automated Google Play Console upload tasks (via android/scripts/play-upload.mjs)
val playScriptPath = rootProject.layout.projectDirectory.file("scripts/play-upload.mjs").asFile.absolutePath
val playServiceAccountFile = providers.gradleProperty("zuno.play.serviceAccountFile").orNull
    ?: providers.environmentVariable("ZUNO_PLAY_SERVICE_ACCOUNT_FILE").orNull
val playTrack = providers.gradleProperty("zuno.play.track").getOrElse("internal")
val playStatus = providers.gradleProperty("zuno.play.status").getOrElse("completed")
val playReleaseName = providers.gradleProperty("zuno.play.releaseName").orNull
val playReleaseNotes = providers.gradleProperty("zuno.play.releaseNotes").orNull
val playDryRun = providers.gradleProperty("zuno.play.dryRun").map { it.toBoolean() }.getOrElse(false)
val playChangesNotSentForReview = providers.gradleProperty("zuno.play.changesNotSentForReview").map { it.toBoolean() }.getOrElse(false)
val playSkipBuild = providers.gradleProperty("zuno.play.skipBuild").map { it.toBoolean() }.getOrElse(false)
val playVersionCode = providers.gradleProperty("zuno.play.versionCode").orNull
val releaseAabFile = layout.buildDirectory.file("outputs/bundle/release/app-release.aab")

tasks.register<Exec>("checkPlayAccess") {
    group = "publishing"
    description = "Verify Google Play Developer API OAuth credentials and Play Console access."
    val args = mutableListOf("node", playScriptPath, "--check", "--package", "com.zuno.ai")
    if (playServiceAccountFile != null) {
        args += listOf("--service-account", playServiceAccountFile)
    }
    commandLine(args)
}

tasks.register<Exec>("publishPlayBundle") {
    group = "publishing"
    description = "Build (unless -Pzuno.play.skipBuild=true or -Pzuno.play.versionCode=...) and publish to Google Play Console."
    if (!playSkipBuild && playVersionCode == null) {
        dependsOn("bundleRelease")
    }
    val args = mutableListOf(
        "node", playScriptPath,
        "--package", "com.zuno.ai",
        "--track", playTrack,
        "--status", playStatus,
    )
    if (playVersionCode != null) {
        args += listOf("--version-code", playVersionCode)
    } else {
        args += listOf("--aab", releaseAabFile.get().asFile.absolutePath)
    }
    if (playServiceAccountFile != null) {
        args += listOf("--service-account", playServiceAccountFile)
    }
    if (playReleaseName != null) {
        args += listOf("--release-name", playReleaseName)
    }
    if (playReleaseNotes != null) {
        args += listOf("--release-notes", playReleaseNotes)
    }
    if (playDryRun) {
        args += "--dry-run"
    }
    if (playChangesNotSentForReview) {
        args += "--changes-not-sent-for-review"
    }
    commandLine(args)
}
