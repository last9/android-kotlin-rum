package io.last9.android.rum.internal

import android.app.Application
import android.util.Log
import io.last9.android.rum.Last9Options
import io.last9.android.rum.export.ExporterFactory
import io.last9.android.rum.export.Last9SpanExporter
import io.opentelemetry.android.OpenTelemetryRum
import io.opentelemetry.android.OpenTelemetryRumBuilder
import io.opentelemetry.android.config.OtelRumConfig
import io.opentelemetry.android.features.diskbuffering.DiskBufferingConfig
import io.opentelemetry.sdk.resources.Resource

private const val TAG = "Last9AgentConfigurator"

/**
 * Instrumentation name constants used by [OtelRumConfig.suppressInstrumentation].
 *
 * These strings match the names registered by the OTel Android agent's ServiceLoader
 * entries (confirmed against honeycombio/honeycomb-opentelemetry-android which uses
 * the same upstream agent and the same string keys).
 */
private object InstrumentationNames {
    const val CRASH = "crash"
    const val ANR = "anr"
    // "startup" is always-on in the current agent API; a toggle will be added
    // in a future release when the agent exposes a stable suppression key.
}

/**
 * Single point of contact with [OpenTelemetryRum].
 *
 * Translates [Last9Options] into the OTel Android agent builder configuration.
 * Keeping this isolated means neither [Last9] nor the instrumentation classes
 * need to import anything from `io.opentelemetry.android`.
 */
internal object AgentConfigurator {

    fun configure(app: Application, options: Last9Options): OpenTelemetryRum {
        if (options.debugMode) {
            Log.d(TAG, "Initializing Last9 RUM SDK: $options")
            Log.d(TAG, "OTLP traces endpoint: ${options.tracesEndpoint()}")
            Log.d(TAG, "Crash reporting: ${options.enableCrashReporting}")
            Log.d(TAG, "ANR detection: ${options.enableAnrDetection}")
        }

        val rumConfig = OtelRumConfig().apply {
            // Disk buffering is off by default — spans are held in memory until exported.
            setDiskBufferingConfig(DiskBufferingConfig.create(false))

            // Suppress instrumentations the user has opted out of.
            // Uses named constants (not magic strings) so a key mismatch is visible.
            if (!options.enableCrashReporting) suppressInstrumentation(InstrumentationNames.CRASH)
            if (!options.enableAnrDetection) suppressInstrumentation(InstrumentationNames.ANR)
        }

        val baseExporter = ExporterFactory.createSpanExporter(options)
        val spanExporter = Last9SpanExporter(baseExporter, options.debugMode)
        val resourceAttributes = ResourceAttributeBuilder.build(options)

        return OpenTelemetryRumBuilder.create(app, rumConfig)
            .addSpanExporterCustomizer { spanExporter }
            .addTracerProviderCustomizer { builder, _ ->
                // Build a Resource from Last9-specific attributes (service name, device
                // info, SDK version, user extras).
                val last9Resource = Resource.builder()
                    .apply { resourceAttributes.forEach { (k, v) -> put(k, v) } }
                    .build()

                // Resource.merge(other) uses `other` as the higher-priority source.
                // Merging getDefault() as base and last9Resource as `other` ensures
                // our keys (service.name, telemetry.sdk.name, etc.) override the OTel
                // defaults (which would otherwise stamp telemetry.sdk.name="opentelemetry").
                builder.setResource(Resource.getDefault().merge(last9Resource))
                builder  // Return the builder
            }
            .build()
    }
}
