package io.last9.android.rum.export

import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.testing.trace.TestSpanData
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.data.StatusData
import io.opentelemetry.sdk.trace.export.SpanExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class Last9SpanExporterTest {

    private lateinit var delegate: RecordingSpanExporter

    @Before
    fun setUp() {
        delegate = RecordingSpanExporter()
        ShadowLog.clear()
    }

    // -------------------------------------------------------------------------
    // P1 — Delegation (2.3.1, 2.3.4, 2.3.5)
    // -------------------------------------------------------------------------

    @Test
    fun `export delegates to underlying exporter`() {
        val exporter = Last9SpanExporter(delegate, debugMode = false)
        val spans = listOf(fakeSpan("my-span"))

        exporter.export(spans.toMutableList())

        assertEquals(1, delegate.exportedSpans.size)
        assertSame(spans[0], delegate.exportedSpans[0])
    }

    @Test
    fun `flush delegates to underlying exporter`() {
        val exporter = Last9SpanExporter(delegate, debugMode = false)
        exporter.flush()
        assertTrue(delegate.flushed)
    }

    @Test
    fun `shutdown delegates to underlying exporter`() {
        val exporter = Last9SpanExporter(delegate, debugMode = false)
        exporter.shutdown()
        assertTrue(delegate.shutdown)
    }

    // -------------------------------------------------------------------------
    // P1 — Debug logging (2.3.2, 2.3.3)
    // -------------------------------------------------------------------------

    @Test
    fun `export does not log when debugMode is false`() {
        val exporter = Last9SpanExporter(delegate, debugMode = false)
        exporter.export(listOf(fakeSpan("test-span")).toMutableList())

        val logs = ShadowLog.getLogs().filter { it.tag == "Last9SpanExporter" }
        assertTrue("No logs expected when debugMode=false", logs.isEmpty())
    }

    @Test
    fun `export logs one line per span when debugMode is true`() {
        val exporter = Last9SpanExporter(delegate, debugMode = true)
        val spans = listOf(fakeSpan("span-a"), fakeSpan("span-b"), fakeSpan("span-c"))

        exporter.export(spans.toMutableList())

        val logs = ShadowLog.getLogs().filter { it.tag == "Last9SpanExporter" }
        assertEquals("One log per span", 3, logs.size)
        logs.forEach { log ->
            assertTrue("Log must contain 'name='", log.msg.contains("name="))
            assertTrue("Log must contain 'traceId='", log.msg.contains("traceId="))
            assertTrue("Log must contain 'spanId='", log.msg.contains("spanId="))
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun fakeSpan(name: String): SpanData =
        TestSpanData.builder()
            .setName(name)
            .setKind(SpanKind.INTERNAL)
            .setStatus(StatusData.ok())
            .setStartEpochNanos(System.nanoTime())
            .setEndEpochNanos(System.nanoTime() + 1_000_000)
            .setHasEnded(true)
            .build()

    private fun assertSame(expected: Any, actual: Any) =
        assertTrue("Expected same instance", expected === actual)

    class RecordingSpanExporter : SpanExporter {
        val exportedSpans = mutableListOf<SpanData>()
        var flushed = false
        var shutdown = false

        override fun export(spans: MutableCollection<SpanData>): CompletableResultCode {
            exportedSpans.addAll(spans)
            return CompletableResultCode.ofSuccess()
        }

        override fun flush(): CompletableResultCode {
            flushed = true
            return CompletableResultCode.ofSuccess()
        }

        override fun shutdown(): CompletableResultCode {
            shutdown = true
            return CompletableResultCode.ofSuccess()
        }
    }
}
