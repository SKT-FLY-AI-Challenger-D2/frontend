package com.example.ytnowplaying.config

import com.example.ytnowplaying.BuildConfig

/**
 * Backend 서버 주소를 단일하게 제공한다 (TASK-03).
 *
 * 값은 빌드 시 build.gradle.kts가 BACKEND_BASE_URL(local.properties 또는 Gradle 프로퍼티)에서
 * 읽어 BuildConfig.BACKEND_BASE_URL로 주입한다. 모든 Retrofit 클라이언트는 이 객체를 통해서만
 * base URL을 얻는다.
 */
object BackendConfig {
    val baseUrl: String = normalizeBaseUrl(BuildConfig.BACKEND_BASE_URL)
}

/**
 * scheme(http/https)과 마지막 '/'를 검증·보정한다.
 * Retrofit은 baseUrl이 '/'로 끝나지 않으면 런타임에 예외를 던지므로 여기서 보정한다.
 */
fun normalizeBaseUrl(url: String): String {
    require(url.startsWith("http://") || url.startsWith("https://")) {
        "BACKEND_BASE_URL은 http:// 또는 https://로 시작해야 합니다: $url"
    }
    return if (url.endsWith("/")) url else "$url/"
}
