import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Kotlin 2.0+에서는 Compose Compiler Gradle plugin 사용 권장 :contentReference[oaicite:1]{index=1}
    id("org.jetbrains.kotlin.plugin.compose")
}

// local.properties 또는 -P Gradle 프로퍼티에서 BACKEND_BASE_URL을 읽는다 (TASK-03).
// 둘 다 없으면 에뮬레이터가 호스트 로컬 서버를 가리키는 개발 기본값만 허용한다.
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

fun resolveBackendBaseUrl(): String {
    val fromProject = providers.gradleProperty("BACKEND_BASE_URL").orNull
    val fromLocal = localProperties.getProperty("BACKEND_BASE_URL")
    return fromProject ?: fromLocal ?: "http://10.0.2.2:8000/"
}

fun resolveGitCommit(): String {
    return try {
        providers.exec {
            commandLine("git", "rev-parse", "--short", "HEAD")
        }.standardOutput.asText.get().trim()
    } catch (e: Exception) {
        "unknown"
    }
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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "BACKEND_BASE_URL", "\"${resolveBackendBaseUrl()}\"")
        buildConfigField("String", "GIT_COMMIT", "\"${resolveGitCommit()}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
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
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")


    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:core:1.5.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")


}

// Kotlin DSL에서 toolchain 지정(권장)
kotlin {
    jvmToolchain(21)
}