package io.last9.android.rum.helpers

import okhttp3.Call
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.concurrent.TimeUnit

/**
 * Minimal fake [Interceptor.Chain] for unit tests.
 *
 * Captures the [Request] passed to [proceed] so tests can inspect injected headers.
 * Can be configured to return a specific HTTP response code or throw a [Throwable].
 */
class FakeChain(
    private val request: Request,
    private val responseCode: Int = 200,
    private val responseBody: String = "",
    private val throwOnProceed: Throwable? = null,
) : Interceptor.Chain {

    /** The request ultimately forwarded by [proceed] — inspect headers here. */
    var proceededRequest: Request? = null
        private set

    override fun request(): Request = request

    override fun proceed(request: Request): Response {
        proceededRequest = request
        throwOnProceed?.let { throw it }
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(responseCode)
            .message(if (responseCode in 200..299) "OK" else "Error")
            .body(responseBody.toResponseBody())
            .build()
    }

    override fun connection(): Connection? = null
    override fun call(): Call = throw UnsupportedOperationException()
    override fun connectTimeoutMillis(): Int = 0
    override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    override fun readTimeoutMillis(): Int = 0
    override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    override fun writeTimeoutMillis(): Int = 0
    override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
}
