package io.last9.android.rum

import io.last9.android.rum.instrumentation.OkHttpInstrumentation
import io.opentelemetry.android.OpenTelemetryRum
import io.opentelemetry.api.trace.Tracer

/**
 * A live handle to an initialized Last9 RUM session.
 *
 * Obtain via [Last9.init] or [Last9.getInstance].
 *
 * ```kotlin
 * val rum = Last9.getInstance()
 *
 * // Manual span
 * val tracer = rum.getTracer()
 * val span = tracer.spanBuilder("checkout.confirm").startSpan()
 * span.end()
 *
 * // OkHttp with W3C traceparent auto-injection
 * val client = OkHttpClient.Builder()
 *     .addInterceptor(rum.createOkHttpInterceptor())
 *     .build()
 * ```
 */
class Last9RumInstance internal constructor(
    /** The underlying [OpenTelemetryRum] instance for advanced use cases. */
    val otelRum: OpenTelemetryRum,
    private val options: Last9Options,
) {

    /**
     * Returns an OTel [Tracer] for creating manual spans.
     *
     * @param instrumentationName Defaults to [Last9Options.serviceName].
     */
    fun getTracer(instrumentationName: String = options.serviceName): Tracer =
        otelRum.openTelemetry.getTracer(instrumentationName)

    /**
     * Returns an OkHttp [okhttp3.Interceptor] that:
     * - Creates a CLIENT span for each HTTP request
     * - Injects W3C `traceparent` headers for backend correlation
     *
     * Add to your `OkHttpClient.Builder` via `addInterceptor()`.
     * Only available when [Last9Options.enableOkHttpInstrumentation] is true.
     */
    fun createOkHttpInterceptor(): okhttp3.Interceptor {
        check(options.enableOkHttpInstrumentation) {
            "OkHttp instrumentation is disabled. Set enableOkHttpInstrumentation = true " +
                "in Last9.init { } to use createOkHttpInterceptor()."
        }
        return OkHttpInstrumentation(otelRum.openTelemetry)
    }
}
