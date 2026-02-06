plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Kotlin 2.0+에서는 Compose Compiler Gradle plugin 사용 권장 :contentReference[oaicite:1]{index=1}
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.ytnowplaying"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.ytnowplaying"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

}

dependencies {
    // Jetpack Compose BOM (Compose 1.9 계열 안정 릴리스) :contentReference[oaicite:2]{index=2}
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.activity:activity-compose:1.12.3") // 2026-01 안정 :contentReference[oaicite:3]{index=3}
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.core:core-ktx:1.17.0") // 안정 릴리스 :contentReference[oaicite:4]{index=4}

    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("com.google.android.material:material:1.13.0")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")



}

// Kotlin DSL에서 toolchain 지정(권장)
kotlin {
    jvmToolchain(21)
}