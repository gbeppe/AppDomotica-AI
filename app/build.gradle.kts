plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

// Build identity (same scheme as WallBox REL-APP.1): only tools/release_android.py passes
// a release identity, which it then publishes itself. Every other build (assembleDebug,
// Android Studio Run, tests) gets a local development identity and never publishes.
val releaseGitSha = providers.gradleProperty("domopi.release.gitSha").orNull
val releaseBuildId = providers.gradleProperty("domopi.release.buildId").orNull
val releaseBuildTimestamp = providers.gradleProperty("domopi.release.buildTimestamp").orNull
val releaseIdentity = listOf(releaseGitSha, releaseBuildId, releaseBuildTimestamp)
if (releaseIdentity.any { it != null } && releaseIdentity.any { it == null }) {
    throw GradleException("Incomplete DomoPi release identity")
}

val buildInstant = System.currentTimeMillis()
val buildTimestamp = releaseBuildTimestamp ?: buildInstant.toString()
val buildId = releaseBuildId ?: "dev_$buildInstant"

val gitShaVal = releaseGitSha ?: providers.exec {
    commandLine("git", "rev-parse", "HEAD")
}.standardOutput.asText.map { it.trim() }.getOrElse("unknown")

val gitBranchVal = providers.exec {
    commandLine("git", "rev-parse", "--abbrev-ref", "HEAD")
}.standardOutput.asText.map { it.trim() }.getOrElse("unknown")

val gitDirtyVal = providers.exec {
    commandLine("git", "status", "--porcelain")
}.standardOutput.asText.map { out ->
    if (out.trim().isEmpty()) "clean" else "dirty"
}.getOrElse("unknown")

android {
    namespace = "com.domopi.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.domopi.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 37
        versionName = "9.9"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "GIT_SHA", "\"$gitShaVal\"")
        buildConfigField("String", "GIT_BRANCH", "\"$gitBranchVal\"")
        buildConfigField("String", "BUILD_DIRTY", "\"$gitDirtyVal\"")
        buildConfigField("String", "BUILD_TIMESTAMP", "\"$buildTimestamp\"")
        buildConfigField("String", "BUILD_ID", "\"$buildId\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.mqtt.client)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.serialization.json)
    
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
