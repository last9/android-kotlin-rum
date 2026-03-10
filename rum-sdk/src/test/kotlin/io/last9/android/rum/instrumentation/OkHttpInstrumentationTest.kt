package io.last9.android.rum.instrumentation

import io.last9.android.rum.helpers.FakeChain
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import okhttp3.Request
import okhttp3.RequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OkHttpInstrumentationTest {

    private lateinit var spanExporter: InMemorySpanExporter
    private lateinit var interceptor: OkHttpInstrumentation

    @Before
    fun setUp() {
        spanExporter = InMemorySpanExporter.create()
        val tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
            .build()
        val openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .build()
        interceptor = OkHttpInstrumentation(openTelemetry)
    }

    // -------------------------------------------------------------------------
    // P0 — Core span creation (2.1.1)
    // -------------------------------------------------------------------------

    @Test
    fun `intercept creates a CLIENT span`() {
        interceptor.intercept(FakeChain(get("https://example.com/api/v1/users"), responseCode = 200))

        val spans = spanExporter.finishedSpanItems
        assertEquals(1, spans.size)
        val span = spans[0]
        assertEquals(SpanKind.CLIENT, span.kind)
        assertEquals("GET", span.attributes[AttributeKey.stringKey("http.request.method")])
        assertEquals("https://example.com/api/v1/users", span.attributes[AttributeKey.stringKey("url.full")])
        assertEquals("example.com", span.attributes[AttributeKey.stringKey("server.address")])
        assertEquals(443L, span.attributes[AttributeKey.longKey("server.port")])
        assertEquals(200L, span.attributes[AttributeKey.longKey("http.response.status_code")])
        assertEquals(StatusCode.UNSET, span.status.statusCode)
    }

    // -------------------------------------------------------------------------
    // P0 — W3C traceparent injection (2.1.2)
    // -------------------------------------------------------------------------

    @Test
    fun `intercept injects W3C traceparent header`() {
        val chain = FakeChain(get("https://api.example.com/data"), responseCode = 200)
        interceptor.intercept(chain)

        val forwarded = chain.proceededRequest
        assertNotNull("Request must have been forwarded to chain", forwarded)

        val traceparent = forwarded!!.header("traceparent")
        assertNotNull("traceparent header must be present", traceparent)

        // W3C format: 00-{32 hex traceId}-{16 hex spanId}-{2 hex flags}
        val w3cPattern = Regex("^00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]$")
        assertTrue(
            "traceparent '$traceparent' must match W3C format",
            w3cPattern.matches(traceparent!!),
        )

        val span = spanExporter.finishedSpanItems[0]
        assertTrue("traceparent must contain the span's traceId", traceparent.contains(span.traceId))
        assertTrue("traceparent must contain the span's spanId", traceparent.contains(span.spanId))
    }

    // -------------------------------------------------------------------------
    // P0 — Error spans (2.1.3, 2.1.4, 2.1.5)
    // -------------------------------------------------------------------------

    @Test
    fun `intercept marks span ERROR for 4xx response`() {
        interceptor.intercept(FakeChain(get("https://example.com/missing"), responseCode = 404))

        val span = spanExporter.finishedSpanItems[0]
        assertEquals(StatusCode.ERROR, span.status.statusCode)
        assertEquals("HTTP 404", span.status.description)
        assertEquals(404L, span.attributes[AttributeKey.longKey("http.response.status_code")])
    }

    @Test
    fun `intercept marks span ERROR for 5xx response`() {
        interceptor.intercept(FakeChain(get("https://example.com/api"), responseCode = 503))

        val span = spanExporter.finishedSpanItems[0]
        assertEquals(StatusCode.ERROR, span.status.statusCode)
        assertEquals("HTTP 503", span.status.description)
    }

    @Test
    fun `intercept does not mark span ERROR for 3xx response`() {
        interceptor.intercept(FakeChain(get("https://example.com/old"), responseCode = 302))

        val span = spanExporter.finishedSpanItems[0]
        assertEquals(StatusCode.UNSET, span.status.statusCode)
        assertEquals(302L, span.attributes[AttributeKey.longKey("http.response.status_code")])
    }

    @Test
    fun `intercept marks exactly status 400 as ERROR boundary`() {
        interceptor.intercept(FakeChain(get("https://example.com/a"), responseCode = 400))
        val span400 = spanExporter.finishedSpanItems.last()
        assertEquals(StatusCode.ERROR, span400.status.statusCode)

        interceptor.intercept(FakeChain(get("https://example.com/b"), responseCode = 399))
        val span399 = spanExporter.finishedSpanItems.last()
        assertEquals(StatusCode.UNSET, span399.status.statusCode)
    }

    // -------------------------------------------------------------------------
    // P0 — Span always ends + error handling (2.1.6, 2.1.7)
    // -------------------------------------------------------------------------

    @Test
    fun `intercept records exception and rethrows on IOException`() {
        val chain = FakeChain(
            get("https://example.com/api"),
            throwOnProceed = java.io.IOException("Connection refused"),
        )

        try {
            interceptor.intercept(chain)
        } catch (_: java.io.IOException) { }

        val span = spanExporter.finishedSpanItems[0]
        assertEquals(StatusCode.ERROR, span.status.statusCode)
        assertEquals("Connection refused", span.status.description)
        assertTrue(
            "Span must have an exception event",
            span.events.any { it.name == "exception" },
        )
    }

    @Test
    fun `intercept ends span even when chain throws Error`() {
        val chain = FakeChain(
            get("https://example.com/api"),
            throwOnProceed = OutOfMemoryError("forced OOM"),
        )

        try {
            interceptor.intercept(chain)
        } catch (_: OutOfMemoryError) { }

        assertEquals(
            "Span must be exported even after Error — proves finally { span.end() } runs",
            1,
            spanExporter.finishedSpanItems.size,
        )
    }

    @Test
    fun `intercept records null exception message as fallback`() {
        val chain = FakeChain(
            get("https://example.com/api"),
            throwOnProceed = java.io.IOException(null as String?),
        )

        try {
            interceptor.intercept(chain)
        } catch (_: java.io.IOException) { }

        assertEquals("Network error", spanExporter.finishedSpanItems[0].status.description)
    }

    // -------------------------------------------------------------------------
    // P0 — Span naming excludes query string (2.1.8)
    // -------------------------------------------------------------------------

    @Test
    fun `intercept span name uses method and path without query string`() {
        val chain = FakeChain(
            Request.Builder()
                .url("https://api.example.com/api/v1/checkout?ref=home&user=123")
                .post(RequestBody.create(null, ""))
                .build(),
            responseCode = 200,
        )

        interceptor.intercept(chain)

        assertEquals("POST /api/v1/checkout", spanExporter.finishedSpanItems[0].name)
    }

    // -------------------------------------------------------------------------
    // P3 — Port and propagator edge cases (3.4.x)
    // -------------------------------------------------------------------------

    @Test
    fun `intercept span includes port 80 for plain HTTP`() {
        interceptor.intercept(FakeChain(get("http://example.com/path"), responseCode = 200))
        assertEquals(80L, spanExporter.finishedSpanItems[0].attributes[AttributeKey.longKey("server.port")])
    }

    @Test
    fun `intercept span includes custom port`() {
        interceptor.intercept(FakeChain(get("https://example.com:8443/path"), responseCode = 200))
        assertEquals(8443L, spanExporter.finishedSpanItems[0].attributes[AttributeKey.longKey("server.port")])
    }

    @Test
    fun `intercept does not inject traceparent when propagator is noop`() {
        val noopOtel = OpenTelemetrySdk.builder()
            .setTracerProvider(
                SdkTracerProvider.builder()
                    .addSpanProcessor(SimpleSpanProcessor.create(InMemorySpanExporter.create()))
                    .build()
            )
            .setPropagators(ContextPropagators.noop())
            .build()
        val noopInterceptor = OkHttpInstrumentation(noopOtel)

        val chain = FakeChain(get("https://example.com/api"), responseCode = 200)
        noopInterceptor.intercept(chain)

        assertNull(
            "traceparent must not be injected when propagator is noop",
            chain.proceededRequest?.header("traceparent"),
        )
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun get(url: String) = Request.Builder().url(url).build()
}
