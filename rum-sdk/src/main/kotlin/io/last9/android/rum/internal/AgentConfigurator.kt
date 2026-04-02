package io.last9.android.rum.internal

import android.app.Application
import android.util.Log
import io.last9.android.rum.Last9Options
import io.last9.android.rum.export.ExporterFactory
import io.last9.android.rum.export.Last9SpanExporter
import io.last9.android.rum.session.SessionStore
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
    const val ACTIVITY = "activity"
    const val FRAGMENT = "fragment"
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

        // Initialize session store (needs Context for SharedPreferences)
        SessionStore.init(app)

        val rumConfig = OtelRumConfig().apply {
            setDiskBufferingConfig(DiskBufferingConfig.create(false))

            if (!options.enableCrashReporting) suppressInstrumentation(InstrumentationNames.CRASH)
            if (!options.enableAnrDetection) suppressInstrumentation(InstrumentationNames.ANR)
            if (!options.enableActivityInstrumentation) suppressInstrumentation(InstrumentationNames.ACTIVITY)
            if (!options.enableFragmentInstrumentation) suppressInstrumentation(InstrumentationNames.FRAGMENT)
        }

        val baseExporter = ExporterFactory.createSpanExporter(options)
        val spanExporter = Last9SpanExporter(baseExporter, options.debugMode)
        val resourceAttributes = ResourceAttributeBuilder.build(options)

        return OpenTelemetryRumBuilder.create(app, rumConfig)
            .addSpanExporterCustomizer { spanExporter }
            .addTracerProviderCustomizer { builder, _ ->
                val last9Resource = Resource.builder()
                    .apply { resourceAttributes.forEach { (k, v) -> put(k, v) } }
                    .build()

                builder.setResource(Resource.getDefault().merge(last9Resource))

                // Last9SpanProcessor injects session.id, view.id, user.* on every span
                builder.addSpanProcessor(Last9SpanProcessor())

                if (options.debugMode) {
                    Log.d(TAG, "Last9SpanProcessor registered (session + view + user injection)")
                }

                builder
            }
            .build()
    }
}
