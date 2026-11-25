import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties
import java.util.TimeZone

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

fun gradleProp(name: String, default: String = ""): String =
    (project.findProperty(name) as String?) ?: default

// keystore.properties is never committed (see .gitignore) — if it's missing, the release build
// just falls back to unsigned instead of failing, so `./gradlew assembleRelease` still works for
// local compile/R8 checks without a real signing key.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
fun keystoreProp(name: String): String? =
    (keystoreProperties.getProperty(name)) ?: System.getenv(name)

android {
    namespace = "com.joyel.tripguard"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.joyel.tripguard"
        minSdk = 26
        targetSdk = 37
        versionCode = gradleProp("APP_VERSION_CODE", "1").toInt()
        versionName = gradleProp("APP_VERSION_NAME", "1.0.0")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "OSRM_BASE_URL", "\"${gradleProp("OSRM_BASE_URL", "https://router.project-osrm.org")}\"")
        buildConfigField("String", "NOMINATIM_BASE_URL", "\"${gradleProp("NOMINATIM_BASE_URL", "https://nominatim.openstreetmap.org")}\"")
        buildConfigField("String", "PHOTON_BASE_URL", "\"${gradleProp("PHOTON_BASE_URL", "https://photon.komoot.io")}\"")
        buildConfigField("String", "CONTACT_EMAIL", "\"${gradleProp("KNOCKME_CONTACT_EMAIL", "contact@example.com")}\"")

        val kolkataNow = SimpleDateFormat("yyyy-MM-dd HH:mm").apply { timeZone = TimeZone.getTimeZone("Asia/Kolkata") }.format(Date())
        buildConfigField("String", "BUILD_DATE", "\"$kolkataNow\"")
        buildConfigField("String", "VERSION", "\"${gradleProp("APP_VERSION_NAME", "1.0.0")}\"")
        val gitHashForBuildConfig = try {
            val process = ProcessBuilder("git", "rev-parse", "--short", "HEAD").directory(rootDir).start()
            val out = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            out.ifBlank { "unknown" }
        } catch (e: Exception) {
            "unknown"
        }
        buildConfigField("String", "GIT_COMMIT", "\"$gitHashForBuildConfig\"")
    }

    signingConfigs {
        val storeFilePath = keystoreProp("storeFile")
        if (storeFilePath != null) {
            create("release") {
                storeFile = rootProject.file(storeFilePath)
                storePassword = keystoreProp("storePassword")
                keyAlias = keystoreProp("keyAlias")
                keyPassword = keystoreProp("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
        debug {
            isDebuggable = true
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

    bundle {
        language { enableSplit = true }
        density { enableSplit = true }
        abi { enableSplit = true }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.play.services.location)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
}

// Generated (never committed, see .gitignore) and packed into the APK at assets/build_info.txt
// so About/Settings can show the exact build that's installed.
val generateBuildInfo = tasks.register("generateBuildInfo") {
    doLast {
        val gitHash = try {
            val process = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
                .directory(rootProject.rootDir)
                .start()
            val out = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            out.ifBlank { "unknown" }
        } catch (e: Exception) {
            "unknown"
        }

        val changelogFile = rootProject.file("CHANGELOG.md")
        val changes = if (changelogFile.exists()) {
            changelogFile.readLines()
                .dropWhile { !it.startsWith("## ") }
                .drop(1)
                .map { it.trim() }
                .filter { it.startsWith("-") }
                .take(5)
                .joinToString("; ") { it.removePrefix("-").trim() }
        } else {
            "unknown"
        }

        val buildDate = SimpleDateFormat("yyyy-MM-dd HH:mm").apply { timeZone = TimeZone.getTimeZone("Asia/Kolkata") }.format(Date())
        val assetsDir = file("src/main/assets")
        assetsDir.mkdirs()
        file("src/main/assets/build_info.txt").writeText(
            """
            appName=Knock Me
            versionName=${gradleProp("APP_VERSION_NAME", "1.0.0")}
            versionCode=${gradleProp("APP_VERSION_CODE", "1")}
            buildDate=$buildDate
            gitCommit=$gitHash
            changes: $changes
            """.trimIndent()
        )
    }
}

tasks.named("preBuild") { dependsOn(generateBuildInfo) }
