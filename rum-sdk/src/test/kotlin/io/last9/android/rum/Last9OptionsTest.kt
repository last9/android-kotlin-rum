package io.last9.android.rum

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class Last9OptionsTest {

    // -------------------------------------------------------------------------
    // validate()
    // -------------------------------------------------------------------------

    @Test
    fun `validate throws when token is blank`() {
        val options = Last9Options().apply {
            token = ""
            serviceName = "my-app"
        }
        assertThrows(IllegalArgumentException::class.java) { options.validate() }
    }

    @Test
    fun `validate throws when serviceName is blank`() {
        val options = Last9Options().apply {
            token = "tok"
            serviceName = ""
        }
        assertThrows(IllegalArgumentException::class.java) { options.validate() }
    }

    @Test
    fun `validate passes with required fields set`() {
        val options = Last9Options().apply {
            token = "tok"
            serviceName = "my-app"
        }
        options.validate() // must not throw
    }

    // -------------------------------------------------------------------------
    // tracesEndpoint()
    // -------------------------------------------------------------------------

    @Test
    fun `tracesEndpoint appends correct path to baseUrl`() {
        val options = Last9Options().apply {
            token = "tok"
            serviceName = "my-app"
            baseUrl = "https://otlp-ext-aps1.last9.io/v1/otlp/organizations/acme"
        }
        assertEquals(
            "https://otlp-ext-aps1.last9.io/v1/otlp/organizations/acme/telemetry/beacon/v1/traces",
            options.tracesEndpoint(),
        )
    }

    @Test
    fun `tracesEndpoint trims trailing slash from baseUrl`() {
        val options = Last9Options().apply {
            token = "tok"
            serviceName = "my-app"
            baseUrl = "https://otlp.last9.io/"
        }
        assertEquals(
            "https://otlp.last9.io/telemetry/beacon/v1/traces",
            options.tracesEndpoint(),
        )
    }

    @Test
    fun `tracesEndpoint uses default baseUrl when not overridden`() {
        val options = Last9Options().apply {
            token = "tok"
            serviceName = "my-app"
        }
        assertEquals(
            "https://otlp.last9.io/telemetry/beacon/v1/traces",
            options.tracesEndpoint(),
        )
    }

    // -------------------------------------------------------------------------
    // authHeader()
    // -------------------------------------------------------------------------

    @Test
    fun `authHeader returns correct header name and Bearer value`() {
        val options = Last9Options().apply {
            token = "my-secret-token"
            serviceName = "my-app"
        }
        val (name, value) = options.authHeader()
        assertEquals("X-LAST9-API-TOKEN", name)
        assertEquals("Bearer my-secret-token", value)
    }

    // -------------------------------------------------------------------------
    // toString() — token masking
    // -------------------------------------------------------------------------

    @Test
    fun `toString masks the token`() {
        val options = Last9Options().apply {
            token = "super-secret-token-12345"
            serviceName = "my-app"
        }
        val str = options.toString()
        assertFalse("token must not appear in toString()", str.contains("super-secret-token-12345"))
        assertTrue("masked token marker must appear", str.contains("token=***"))
    }

    @Test
    fun `toString includes serviceName and baseUrl`() {
        val options = Last9Options().apply {
            token = "tok"
            serviceName = "watcho-android"
            baseUrl = "https://otlp.last9.io"
        }
        val str = options.toString()
        assertTrue(str.contains("serviceName=watcho-android"))
        assertTrue(str.contains("baseUrl=https://otlp.last9.io"))
    }

    @Test
    fun `toString includes all instrumentation toggle fields`() {
        val str = Last9Options().apply { token = "t"; serviceName = "s" }.toString()
        assertTrue(str.contains("enableCrashReporting="))
        assertTrue(str.contains("enableAnrDetection="))
        assertTrue(str.contains("enableOkHttpInstrumentation="))
        assertTrue(str.contains("debugMode="))
    }

    // -------------------------------------------------------------------------
    // P3 — Missing validation cases (3.2.1, 3.2.2)
    // -------------------------------------------------------------------------

    @Test
    fun `validate throws when baseUrl is blank`() {
        val options = Last9Options().apply {
            token = "tok"
            serviceName = "svc"
            baseUrl = ""
        }
        assertThrows(IllegalArgumentException::class.java) { options.validate() }
    }

    @Test
    fun `validate throws when token is whitespace-only`() {
        val options = Last9Options().apply {
            token = "   "
            serviceName = "svc"
        }
        assertThrows(IllegalArgumentException::class.java) { options.validate() }
    }

    // -------------------------------------------------------------------------
    // P3 — Endpoint edge cases (3.2.3)
    // -------------------------------------------------------------------------

    @Test
    fun `tracesEndpoint removes all trailing slashes from baseUrl`() {
        val options = Last9Options().apply {
            token = "tok"
            serviceName = "svc"
            baseUrl = "https://otlp.last9.io//"
        }
        // trimEnd('/') removes ALL trailing slashes, leaving no double-slash before the path
        assertEquals(
            "https://otlp.last9.io/telemetry/beacon/v1/traces",
            options.tracesEndpoint(),
        )
    }

    // -------------------------------------------------------------------------
    // P3 — Auth header correctness (3.2.4)
    // -------------------------------------------------------------------------

    @Test
    fun `authHeader token value is not wrapped in extra quotes`() {
        val options = Last9Options().apply { token = "abc123"; serviceName = "s" }
        val (_, value) = options.authHeader()
        assertEquals("Bearer abc123", value)
        assertFalse("Value must not have extra quotes", value.contains("\""))
    }
}
