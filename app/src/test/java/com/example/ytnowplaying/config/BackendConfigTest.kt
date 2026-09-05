package com.example.ytnowplaying.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackendConfigTest {

    @Test
    fun `trailing slash가 없으면 추가한다`() {
        assertEquals("http://10.0.2.2:8000/", normalizeBaseUrl("http://10.0.2.2:8000"))
    }

    @Test
    fun `trailing slash가 있으면 그대로 유지한다`() {
        assertEquals("http://10.0.2.2:8000/", normalizeBaseUrl("http://10.0.2.2:8000/"))
    }

    @Test
    fun `https도 허용한다`() {
        assertEquals("https://example.com/", normalizeBaseUrl("https://example.com"))
    }

    @Test
    fun `http_https가 아니면 예외를 던진다`() {
        assertThrows(IllegalArgumentException::class.java) {
            normalizeBaseUrl("ftp://10.0.2.2:8000/")
        }
    }

    @Test
    fun `scheme이 없으면 예외를 던진다`() {
        assertThrows(IllegalArgumentException::class.java) {
            normalizeBaseUrl("10.0.2.2:8000")
        }
    }
}
