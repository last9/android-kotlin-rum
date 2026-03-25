package io.last9.android.rum.export

import android.util.Log
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter

private const val TAG = "Last9SpanExporter"

/**
 * Wraps the underlying OTLP exporter to add:
 * - Debug logging when [debugMode] is true
 * - Enhanced error logging with detailed failure information
 * - A named delegation layer for future telemetry/retry logic
 *
 * All export operations delegate directly to [delegate].
 */
internal class Last9SpanExporter(
    private val delegate: SpanExporter,
    private val debugMode: Boolean,
) : SpanExporter {

    override fun export(spans: MutableCollection<SpanData>): CompletableResultCode {
        if (debugMode) {
            Log.d(TAG, "Exporting ${spans.size} span(s)...")
            spans.forEach { span ->
                Log.d(TAG, "→ span: name=${span.name} traceId=${span.traceId} spanId=${span.spanId} status=${span.status}")
            }
        }

        val result = delegate.export(spans)

        // Add callback to log export results
        result.whenComplete {
            if (result.isSuccess) {
                if (debugMode) {
                    Log.d(TAG, "Successfully exported ${spans.size} span(s)")
                }
            } else {
                // Always log failures, even when debug mode is off
                Log.e(TAG, "Failed to export ${spans.size} span(s)")
                // Note: The underlying OtlpHttpSpanExporter logs the actual exception
                // We log this summary to make it visible in production builds
            }
        }

        return result
    }

    override fun flush(): CompletableResultCode {
        if (debugMode) {
            Log.d(TAG, "Flushing exporter...")
        }
        return delegate.flush()
    }

    override fun shutdown(): CompletableResultCode {
        if (debugMode) {
            Log.d(TAG, "Shutting down exporter...")
        }
        return delegate.shutdown()
    }
}
