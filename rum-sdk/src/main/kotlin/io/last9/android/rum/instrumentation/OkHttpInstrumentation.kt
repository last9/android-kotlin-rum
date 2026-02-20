package io.last9.android.rum.instrumentation

import io.last9.android.rum.BuildConfig
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapSetter
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * OkHttp [Interceptor] that:
 * 1. Creates an OTel CLIENT span for each outgoing HTTP request
 * 2. Injects W3C `traceparent` (and `tracestate`) headers via the OTel propagator
 * 3. Records HTTP response status codes and marks 4xx/5xx as ERROR spans
 *
 * W3C header injection is handled entirely by the OTel [io.opentelemetry.context.propagation.TextMapPropagator] —
 * no manual header construction is required. The agent registers
 * [io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator] as the default propagator on initialization.
 *
 * Usage:
 * ```kotlin
 * val client = OkHttpClient.Builder()
 *     .addInterceptor(Last9.getInstance().createOkHttpInterceptor())
 *     .build()
 * ```
 */
class OkHttpInstrumentation internal constructor(
    openTelemetry: OpenTelemetry,
) : Interceptor {

    private val tracer = openTelemetry.getTracer(
        "io.last9.android.rum.okhttp",
        BuildConfig.SDK_VERSION,
    )
    private val propagator = openTelemetry.propagators.textMapPropagator

    // TextMapSetter tells the propagator how to write a header into an OkHttp Request.Builder
    private val setter = TextMapSetter<Request.Builder> { carrier, key, value ->
        carrier?.header(key, value)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val requestBuilder = original.newBuilder()

        val spanName = "${original.method} ${original.url.encodedPath}"
        val span = tracer.spanBuilder(spanName)
            .setSpanKind(SpanKind.CLIENT)
            .setAttribute("http.request.method", original.method)
            .setAttribute("url.full", original.url.toString())
            .setAttribute("server.address", original.url.host)
            .setAttribute("server.port", original.url.port.toLong())
            .startSpan()

        val context = Context.current().with(span)

        // Inject W3C traceparent + tracestate into the outgoing request headers.
        // The propagator writes: traceparent: 00-<32-hex-traceId>-<16-hex-spanId>-01
        propagator.inject(context, requestBuilder, setter)

        // makeCurrent() pushes the span onto the thread-local context stack.
        // use { } closes the scope (pops the stack) even if an exception is thrown.
        // The finally block guarantees span.end() is always called regardless of
        // whether the error is an Exception or an Error (e.g. OutOfMemoryError).
        return span.makeCurrent().use {
            try {
                val response = chain.proceed(requestBuilder.build())
                span.setAttribute("http.response.status_code", response.code.toLong())
                if (response.code >= 400) {
                    span.setStatus(StatusCode.ERROR, "HTTP ${response.code}")
                }
                response
            } catch (e: Throwable) {
                span.setStatus(StatusCode.ERROR, e.message ?: "Network error")
                span.recordException(e)
                throw e
            } finally {
                span.end()
            }
        }
    }
}
