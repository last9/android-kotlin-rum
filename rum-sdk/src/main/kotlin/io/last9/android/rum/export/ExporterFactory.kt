package io.last9.android.rum.export

import android.util.Log
import io.last9.android.rum.Last9Options
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.sdk.trace.export.SpanExporter
import java.util.concurrent.TimeUnit

private const val TAG = "ExporterFactory"

/**
 * Constructs the OTLP/HTTP span exporter pointed at the Last9 ingestion endpoint.
 *
 * Deliberately a separate class from [AgentConfigurator] so the exporter configuration
 * can be tested in isolation (no Android context required).
 */
internal object ExporterFactory {

    /**
     * Returns an [OtlpHttpSpanExporter] pre-configured for Last9.
     *
     * Supports two modes:
     *
     * **Standard OTLP Mode** ([Last9Options.useStandardEndpoint] = true):
     * - Endpoint: `<baseUrl>/v1/traces`
     * - Auth: HTTP Basic Auth or API key in Authorization header
     * - Use this when sending to Last9 via proxy or using standard OTLP endpoint
     * - Example config:
     *   ```kotlin
     *   Last9Options {
     *       baseUrl = "https://otlp.example.com"
     *       token = "<base64-encoded-credentials>"
     *       useStandardEndpoint = true
     *       useBasicAuth = true
     *   }
     *   ```
     *
     * **Beacon Mode** ([Last9Options.useStandardEndpoint] = false, default):
     * - Endpoint: `<baseUrl>/telemetry/beacon/v1/traces`
     * - Auth: Bearer token in X-LAST9-API-TOKEN header
     * - Additional headers: Client-ID (required)
     * - **KNOWN ISSUE**: Requires unknown "Origin" header, returns HTTP 400
     * - Designed for web browsers, not recommended for Android apps
     * - See investigation notes in git history (2026-03-12)
     */
    fun createSpanExporter(options: Last9Options): SpanExporter {
        val endpoint = options.tracesEndpoint()
        val (headerName, headerValue) = options.authHeader()
        val timeoutSeconds = options.exportTimeoutSeconds

        // Validate timeout value
        require(timeoutSeconds > 0) {
            "exportTimeoutSeconds must be positive, got: $timeoutSeconds"
        }

        if (options.debugMode) {
            Log.d(TAG, "Creating OTLP exporter:")
            Log.d(TAG, "  Endpoint: $endpoint")
            Log.d(TAG, "  Timeout: ${timeoutSeconds}s")
            Log.d(TAG, "  Auth header: $headerName")
            Log.d(TAG, "  Standard endpoint: ${options.useStandardEndpoint}")
            Log.d(TAG, "  Basic auth: ${options.useBasicAuth}")
        }

        val builder = OtlpHttpSpanExporter.builder()
            .setEndpoint(endpoint)
            .addHeader(headerName, headerValue)
            .addHeader("Content-Type", "application/json")
            .setTimeout(timeoutSeconds, TimeUnit.SECONDS)

        // Beacon endpoint requires Client-ID header
        // Standard endpoint does not need this header
        if (!options.useStandardEndpoint) {
            builder.addHeader("Client-ID", options.serviceName)
            if (options.debugMode) {
                Log.d(TAG, "  Client-ID: ${options.serviceName}")
            }
        }

        return builder.build()
    }
}
