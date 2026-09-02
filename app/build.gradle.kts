import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Date
import java.text.SimpleDateFormat

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

val gitHash: String by lazy {
    try {
        val hash = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
            .start().inputStream.bufferedReader().readText().trim()
        
        // Verifica se ci sono modifiche non committate (dirty state)
        val isDirty = ProcessBuilder("git", "status", "--porcelain")
            .start().inputStream.bufferedReader().readText().isNotEmpty()
            
        if (isDirty) "$hash+" else hash
    } catch (_: Exception) {
        "unknown"
    }
}

val gitBranch: String by lazy {
    try {
        ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD")
            .start().inputStream.bufferedReader().readText().trim()
    } catch (_: Exception) {
        "unknown"
    }
}

val buildTime: String by lazy {
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())
}

android {
    namespace = "com.domopi.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.domopi.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "6.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        buildConfigField("String", "GIT_HASH", "\"$gitHash\"")
        buildConfigField("String", "GIT_BRANCH", "\"$gitBranch\"")
        buildConfigField("String", "BUILD_TIME", "\"$buildTime\"")
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
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
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
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
