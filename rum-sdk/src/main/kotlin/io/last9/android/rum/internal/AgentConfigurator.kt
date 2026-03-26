package io.last9.android.rum.internal

import android.app.Application
import android.util.Log
import io.last9.android.rum.Last9Options
import io.last9.android.rum.SessionManager
import io.last9.android.rum.export.ExporterFactory
import io.last9.android.rum.export.Last9SpanExporter
import io.opentelemetry.android.OpenTelemetryRum
import io.opentelemetry.android.OpenTelemetryRumBuilder
import io.opentelemetry.android.config.OtelRumConfig
import io.opentelemetry.android.features.diskbuffering.DiskBufferingConfig
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SpanProcessor

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
    const val ACTIVITY = "activity"
    const val FRAGMENT = "fragment"
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
            Log.d(TAG, "Activity instrumentation: ${options.enableActivityInstrumentation}")
            Log.d(TAG, "Fragment instrumentation: ${options.enableFragmentInstrumentation}")
        }

        val rumConfig = OtelRumConfig().apply {
            // Disk buffering is off by default — spans are held in memory until exported.
            setDiskBufferingConfig(DiskBufferingConfig.create(false))

            // Suppress instrumentations the user has opted out of.
            // Uses named constants (not magic strings) so a key mismatch is visible.
            if (!options.enableCrashReporting) suppressInstrumentation(InstrumentationNames.CRASH)
            if (!options.enableAnrDetection) suppressInstrumentation(InstrumentationNames.ANR)
            if (!options.enableActivityInstrumentation) suppressInstrumentation(InstrumentationNames.ACTIVITY)
            if (!options.enableFragmentInstrumentation) suppressInstrumentation(InstrumentationNames.FRAGMENT)

            // Note: Built-in session tracking in OpenTelemetry Android 1.0.1 has a bug
            // where session.id is empty. Use Last9RumInstance.enableSessionTracking()
            // for a working session tracking implementation.
        }

        val baseExporter = ExporterFactory.createSpanExporter(options)
        val spanExporter = Last9SpanExporter(baseExporter, options.debugMode)
        val resourceAttributes = ResourceAttributeBuilder.build(options)

        // Create session manager for custom session tracking
        val sessionManager = SessionManager(options.debugMode)
        val sessionAttributes = sessionManager.getSessionAttributes()

        // Create SpanProcessor to inject session.id into all spans
        val sessionSpanProcessor = object : SpanProcessor {
            override fun onStart(parentContext: Context, span: ReadWriteSpan) {
                // Add session.id to every span when it starts
                sessionAttributes.forEach { key, value ->
                    span.setAttribute(key as AttributeKey<Any>, value)
                }
            }

            override fun onEnd(span: ReadableSpan) {}
            override fun isStartRequired() = true
            override fun isEndRequired() = false
        }

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

                // Add custom session SpanProcessor to inject session.id into all spans
                builder.addSpanProcessor(sessionSpanProcessor)

                if (options.debugMode) {
                    Log.d(TAG, "Custom session tracking enabled")
                }

                builder  // Return the builder
            }
            .build()
    }
}
