package io.last9.android.rum.export

import android.util.Log
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter

private const val TAG = "Last9SpanExporter"

/**
 * Wraps the underlying OTLP exporter to add:
 * - Debug logging when [debugMode] is true
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
            spans.forEach { span ->
                Log.d(TAG, "Exporting span: name=${span.name} traceId=${span.traceId} spanId=${span.spanId}")
            }
        }
        return delegate.export(spans)
    }

    override fun flush(): CompletableResultCode = delegate.flush()

    override fun shutdown(): CompletableResultCode = delegate.shutdown()
}
