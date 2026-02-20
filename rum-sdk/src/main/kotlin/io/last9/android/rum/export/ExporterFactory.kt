package io.last9.android.rum.export

import io.last9.android.rum.Last9Options
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.sdk.trace.export.SpanExporter
import java.util.concurrent.TimeUnit

/**
 * Constructs the OTLP/HTTP span exporter pointed at the Last9 ingestion endpoint.
 *
 * Deliberately a separate class from [AgentConfigurator] so the exporter configuration
 * can be tested in isolation (no Android context required).
 */
internal object ExporterFactory {

    private const val EXPORT_TIMEOUT_SECONDS = 10L

    /**
     * Returns an [OtlpHttpSpanExporter] pre-configured for Last9:
     * - Endpoint: [Last9Options.tracesEndpoint]
     * - Auth:     `X-LAST9-API-TOKEN: Bearer <token>` header
     * - Timeout:  10 seconds per export batch
     */
    fun createSpanExporter(options: Last9Options): SpanExporter {
        val (headerName, headerValue) = options.authHeader()
        return OtlpHttpSpanExporter.builder()
            .setEndpoint(options.tracesEndpoint())
            .addHeader(headerName, headerValue)
            .setTimeout(EXPORT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }
}
