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

class MainActivity : AppCompatActivity() {

    // OkHttpClient with Last9 interceptor — automatically injects traceparent
    // headers on every outgoing request and creates CLIENT spans.
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(Last9.getInstance().createOkHttpInterceptor())
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- Manual span example ---
        val tracer = Last9.getInstance().getTracer()
        val span = tracer.spanBuilder("user-action.app-open")
            .setAttribute("user.id", "demo-user-123")
            .startSpan()
        span.end()

        // --- Network request with automatic traceparent injection ---
        fetchDemoData()
    }

    private fun fetchDemoData() {
        val request = Request.Builder()
            .url("https://httpbin.org/get")
            .build()

        // The Last9 interceptor adds:
        //   traceparent: 00-<traceId>-<spanId>-01
        // to this request automatically. The backend will see the correlation header.
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Request failed", e)
            }

            override fun onResponse(call: Call, response: Response) {
                Log.d(TAG, "Response: ${response.code}")
                response.close()
            }
        })
    }
}
