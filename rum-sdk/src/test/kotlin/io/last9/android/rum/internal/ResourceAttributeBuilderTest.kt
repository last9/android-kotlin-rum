package io.last9.android.rum.internal

import io.last9.android.rum.Last9Options
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class ResourceAttributeBuilderTest {

    private fun options(block: Last9Options.() -> Unit = {}): Last9Options =
        Last9Options().apply {
            token = "tok"
            serviceName = "test-service"
            block()
        }

    // -------------------------------------------------------------------------
    // P2 — Attribute precedence: SDK keys always win (3.1.1, 3.1.2)
    // -------------------------------------------------------------------------

    @Test
    fun `user cannot override service_name via additionalResourceAttributes`() {
        val result = ResourceAttributeBuilder.build(
            options {
                serviceName = "real-service"
                additionalResourceAttributes = mapOf("service.name" to "hacker-name")
            }
        )
        assertEquals("real-service", result["service.name"])
    }

    @Test
    fun `user cannot override telemetry_sdk_name via additionalResourceAttributes`() {
        val result = ResourceAttributeBuilder.build(
            options {
                additionalResourceAttributes = mapOf("telemetry.sdk.name" to "custom-sdk")
            }
        )
        assertEquals("last9-android-rum", result["telemetry.sdk.name"])
    }

    @Test
    fun `user cannot override telemetry_sdk_language via additionalResourceAttributes`() {
        val result = ResourceAttributeBuilder.build(
            options {
                additionalResourceAttributes = mapOf("telemetry.sdk.language" to "java")
            }
        )
        assertEquals("kotlin", result["telemetry.sdk.language"])
    }

    // -------------------------------------------------------------------------
    // P2 — User attributes that don't clash are preserved (3.1.3)
    // -------------------------------------------------------------------------

    @Test
    fun `user-supplied attributes that don't clash are preserved`() {
        val result = ResourceAttributeBuilder.build(
            options {
                additionalResourceAttributes = mapOf(
                    "custom.team" to "platform",
                    "custom.region" to "ap-south-1",
                )
            }
        )
        assertEquals("platform", result["custom.team"])
        assertEquals("ap-south-1", result["custom.region"])
    }

    // -------------------------------------------------------------------------
    // P2 — Conditional keys (3.1.4, 3.1.5)
    // -------------------------------------------------------------------------

    @Test
    fun `serviceVersion is omitted when blank`() {
        val result = ResourceAttributeBuilder.build(options { serviceVersion = "" })
        assertFalse(result.containsKey("service.version"))
    }

    @Test
    fun `serviceVersion is included when non-blank`() {
        val result = ResourceAttributeBuilder.build(options { serviceVersion = "2.3.1" })
        assertEquals("2.3.1", result["service.version"])
    }

    @Test
    fun `deploymentEnvironment is omitted when blank`() {
        val result = ResourceAttributeBuilder.build(options { deploymentEnvironment = "" })
        assertFalse(result.containsKey("deployment.environment"))
    }

    @Test
    fun `deploymentEnvironment is included when non-blank`() {
        val result = ResourceAttributeBuilder.build(options { deploymentEnvironment = "staging" })
        assertEquals("staging", result["deployment.environment"])
    }

    // -------------------------------------------------------------------------
    // P2 — SDK-managed device attributes always present (3.1.6)
    // -------------------------------------------------------------------------

    @Test
    fun `SDK-managed device attributes are always present`() {
        val result = ResourceAttributeBuilder.build(options())

        assertTrue(result.containsKey("device.manufacturer"))
        assertTrue(result.containsKey("device.model.identifier"))
        assertEquals("android", result["os.name"])
        assertTrue(result.containsKey("os.version"))
        assertTrue(result["os.api_level"]!!.toIntOrNull() != null)
        assertEquals("kotlin", result["telemetry.sdk.language"])
        assertEquals("last9-android-rum", result["telemetry.sdk.name"])
        assertTrue(result.containsKey("telemetry.sdk.version"))
    }

    // -------------------------------------------------------------------------
    // P2 — Empty additionalResourceAttributes (3.1.7)
    // -------------------------------------------------------------------------

    @Test
    fun `empty additionalResourceAttributes produces correct map without crash`() {
        val result = ResourceAttributeBuilder.build(options { additionalResourceAttributes = emptyMap() })

        // All SDK-managed keys must still be present
        assertTrue(result.containsKey("service.name"))
        assertTrue(result.containsKey("telemetry.sdk.name"))
        assertTrue(result.containsKey("device.manufacturer"))
    }
}
