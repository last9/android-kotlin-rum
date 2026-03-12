package io.last9.android.example

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import io.last9.android.rum.Last9
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

private const val TAG = "MainActivity"

/**
 * Example MainActivity demonstrating Last9 RUM SDK usage patterns.
 *
 * This example shows three core SDK capabilities:
 * 1. Manual span creation for custom user actions
 * 2. Automatic HTTP request instrumentation with OkHttp interceptor
 * 3. Distributed tracing context propagation (W3C traceparent headers)
 *
 * What you'll see in Last9 dashboard:
 * - A span for "user-action.app-open" when the activity opens
 * - A span for the HTTP GET request to httpbin.org with full request/response details
 * - Both spans will be part of the same trace, showing the complete user journey
 */
class MainActivity : AppCompatActivity() {

    // ============================================================
    // HTTP CLIENT WITH AUTOMATIC INSTRUMENTATION
    // ============================================================

    /**
     * OkHttpClient configured with Last9 interceptor.
     *
     * The Last9 interceptor automatically:
     * 1. Creates a CLIENT span for every HTTP request
     * 2. Injects W3C traceparent header: "00-<traceId>-<spanId>-01"
     * 3. Captures request metadata: method, URL, headers
     * 4. Captures response metadata: status code, content length, duration
     * 5. Records any network errors or exceptions
     *
     * The traceparent header enables distributed tracing:
     * - Your backend receives the trace context
     * - Backend can create child spans with the same trace ID
     * - You can follow requests across your entire system
     *
     * Usage pattern:
     * - Add this interceptor to ALL OkHttpClient instances in your app
     * - Works with Retrofit, which uses OkHttp internally
     * - Also works with your own custom HTTP clients
     *
     * Example with Retrofit:
     * ```kotlin
     * val retrofit = Retrofit.Builder()
     *     .baseUrl("https://api.example.com")
     *     .client(httpClient)  // Use this pre-configured client
     *     .addConverterFactory(GsonConverterFactory.create())
     *     .build()
     * ```
     */
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(Last9.getInstance().createOkHttpInterceptor())
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ============================================================
        // EXAMPLE 1: MANUAL SPAN CREATION
        // ============================================================

        /**
         * Creating custom spans for user actions.
         *
         * Use manual spans to track:
         * - User interactions (button clicks, navigation, gestures)
         * - Business logic operations (checkout, video playback, search)
         * - Performance-critical code paths
         *
         * Best practices:
         * - Use descriptive span names: "category.action" format (e.g., "video.playback")
         * - Add attributes for context (user ID, item ID, parameters)
         * - Always call .end() to complete the span
         * - Use try/finally to ensure spans are ended even on errors
         *
         * What gets captured automatically:
         * - Start timestamp
         * - End timestamp
         * - Duration (calculated)
         * - Parent trace context (if within another span)
         * - All custom attributes you add
         */
        val tracer = Last9.getInstance().getTracer()
        val span = tracer.spanBuilder("user-action.app-open")
            .setAttribute("user.id", "demo-user-123")
            .startSpan()
        span.end()

        // Alternative pattern with try/finally (recommended for longer operations):
        // val span = tracer.spanBuilder("user-action.checkout").startSpan()
        // try {
        //     // Your business logic here
        //     performCheckout()
        //     span.setAttribute("order.total", totalAmount)
        // } catch (e: Exception) {
        //     // SDK automatically records the exception
        //     span.recordException(e)
        //     throw e
        // } finally {
        //     // Ensure span is always ended
        //     span.end()
        // }

        // ============================================================
        // EXAMPLE 2: AUTOMATIC HTTP INSTRUMENTATION
        // ============================================================

        /**
         * Making HTTP requests with automatic tracing.
         *
         * When you use the httpClient configured above, Last9 automatically:
         * - Creates a new span for the HTTP request
         * - Sets the span name to "HTTP <METHOD>" (e.g., "HTTP GET")
         * - Adds attributes: http.method, http.url, http.status_code
         * - Injects traceparent header for distributed tracing
         * - Records the full request duration
         * - Captures any network errors
         *
         * What you'll see in Last9:
         * - Span name: "HTTP GET"
         * - Attributes:
         *   - http.method: "GET"
         *   - http.url: "https://httpbin.org/get"
         *   - http.status_code: 200 (or error code)
         *   - http.response_content_length: <bytes>
         * - Duration: time from request start to response complete
         *
         * Headers injected automatically:
         * - traceparent: 00-<128-bit-trace-id>-<64-bit-span-id>-01
         * - (Your backend can parse this to continue the trace)
         */
        fetchDemoData()
    }

    /**
     * Example HTTP request with automatic distributed tracing.
     *
     * This demonstrates a real-world API call pattern.
     * The Last9 interceptor handles all instrumentation automatically.
     *
     * What happens:
     * 1. Request is built (URL, headers, etc.)
     * 2. OkHttp interceptor creates a CLIENT span
     * 3. Interceptor injects traceparent header into request
     * 4. Request is sent to the server
     * 5. Server receives traceparent header (if instrumented, can create child spans)
     * 6. Response is received
     * 7. Interceptor records response metadata (status, size, duration)
     * 8. Span is completed and exported to Last9
     *
     * If the backend is also instrumented with OpenTelemetry:
     * - It reads the traceparent header
     * - Creates SERVER span as a child of this CLIENT span
     * - You see the complete request flow: frontend → backend → database
     *
     * Troubleshooting:
     * - Check Logcat for "Last9: Exported N spans" messages
     * - Verify httpClient is using the Last9 interceptor
     * - Use debugMode = true in ExampleApplication.kt to see detailed logs
     */
    private fun fetchDemoData() {
        val request = Request.Builder()
            .url("https://httpbin.org/get")
            .build()

        // Make the request asynchronously
        // The Last9 interceptor adds traceparent header automatically:
        //   traceparent: 00-<traceId>-<spanId>-01
        //
        // httpbin.org will echo this header back in the response,
        // which you can verify in the response body
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Network errors are automatically recorded in the span
                Log.e(TAG, "Request failed", e)
            }

            override fun onResponse(call: Call, response: Response) {
                // Success - status code and duration are automatically recorded
                Log.d(TAG, "Response: ${response.code}")

                // You can verify the traceparent header was sent by checking the response:
                // httpbin.org echoes back all request headers in the response body
                // Look for "Traceparent": "00-<trace-id>-<span-id>-01"

                response.close()
            }
        })
    }

    // ============================================================
    // ADVANCED PATTERNS (for reference)
    // ============================================================

    /**
     * Example: Tracking video playback with custom attributes
     *
     * fun playVideo(videoId: String) {
     *     val span = Last9.getInstance().getTracer()
     *         .spanBuilder("video.playback")
     *         .setAttribute("video.id", videoId)
     *         .setAttribute("user.id", getCurrentUserId())
     *         .startSpan()
     *
     *     try {
     *         // Start playback
     *         player.play(videoId)
     *         span.setAttribute("video.duration_ms", player.getDuration())
     *     } catch (e: Exception) {
     *         span.recordException(e)
     *         throw e
     *     } finally {
     *         span.end()
     *     }
     * }
     */

    /**
     * Example: Tracking screen navigation
     *
     * fun navigateToScreen(screenName: String) {
     *     Last9.getInstance().getTracer()
     *         .spanBuilder("screen.view")
     *         .setAttribute("screen.name", screenName)
     *         .startSpan()
     *         .end()
     *
     *     // Navigate to the screen
     *     startActivity(Intent(this, TargetActivity::class.java))
     * }
     */

    /**
     * Example: Tracking user checkout flow
     *
     * fun performCheckout(items: List<Item>) {
     *     val span = Last9.getInstance().getTracer()
     *         .spanBuilder("user.checkout")
     *         .setAttribute("cart.items.count", items.size)
     *         .setAttribute("cart.total", calculateTotal(items))
     *         .startSpan()
     *
     *     try {
     *         val orderId = checkoutService.processOrder(items)
     *         span.setAttribute("order.id", orderId)
     *     } finally {
     *         span.end()
     *     }
     * }
     */
}
