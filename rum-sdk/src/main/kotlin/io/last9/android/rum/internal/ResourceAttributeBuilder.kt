package io.last9.android.rum.internal

import android.os.Build
import io.last9.android.rum.BuildConfig
import io.last9.android.rum.Last9Options

/**
 * Builds the set of OTel resource attributes that identify this device and SDK.
 *
 * Merge order (later entries win):
 * 1. [Last9Options.additionalResourceAttributes] — user-supplied, lowest priority
 * 2. SDK-managed keys (device info, service identity, SDK version) — always win
 *
 * This means users cannot accidentally override `service.name` or `telemetry.sdk.name`
 * via [Last9Options.additionalResourceAttributes].
 */
internal object ResourceAttributeBuilder {

    fun build(options: Last9Options): Map<String, String> = buildMap {
        // 1. User-supplied attributes first — lowest priority
        putAll(options.additionalResourceAttributes)

        // 2. SDK-managed keys — applied after, always take precedence
        // Service identity
        put("service.name", options.serviceName)
        if (options.serviceVersion.isNotBlank()) {
            put("service.version", options.serviceVersion)
        }
        if (options.deploymentEnvironment.isNotBlank()) {
            put("deployment.environment", options.deploymentEnvironment)
        }

        // Device / OS (OTel semantic conventions)
        put("device.manufacturer", Build.MANUFACTURER)
        put("device.model.identifier", Build.MODEL)
        put("os.name", "android")
        put("os.version", Build.VERSION.RELEASE)
        put("os.api_level", Build.VERSION.SDK_INT.toString())

        // SDK identity — stamped on every span for supportability
        put("telemetry.sdk.name", "last9-android-rum")
        put("telemetry.sdk.version", BuildConfig.SDK_VERSION)
        put("telemetry.sdk.language", "kotlin")
    }
}
