package io.last9.android.example

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import io.last9.android.rum.Last9
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

private const val TAG = "SecondActivity"

/**
 * SecondActivity demonstrating Activity lifecycle tracking.
 *
 * This Activity is part of the navigation flow testing for Last9 RUM SDK.
 * The SDK automatically captures lifecycle events for this Activity:
 * - Created (onCreate)
 * - Started (onStart)
 * - Resumed (onResume)
 * - Paused (onPause)
 * - Stopped (onStop)
 * - Destroyed (onDestroy)
 *
 * These lifecycle events appear in Last9 traces with:
 * - Span name: Activity lifecycle event name
 * - Attribute: screen.name = "SecondActivity"
 * - Duration: time spent in each lifecycle state
 */
class SecondActivity : AppCompatActivity() {

    /**
     * OkHttpClient with Last9 instrumentation.
     * Automatically captures all HTTP requests from this Activity.
     */
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(Last9.getInstance().createOkHttpInterceptor())
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_second)

        Log.d(TAG, "SecondActivity onCreate")

        /**
         * Screen view tracking is AUTOMATIC - no code needed!
         *
         * The SDK automatically creates a "screen.view" span for this Activity
         * thanks to rumInstance.enableAutomaticScreenTracking(this).
         *
         * What you'll see in Last9:
         * - "screen.view" span with screen.name = "SecondActivity"
         * - Activity lifecycle events: Created, Resumed, Paused, etc.
         */

        val tracer = Last9.getInstance().getTracer()

        // Make an API call to demonstrate automatic HTTP tracking
        fetchUserData()

        // Navigation buttons (optional manual tracking for navigation actions)
        findViewById<Button>(R.id.btnNavigateToMain).setOnClickListener {
            Log.d(TAG, "Navigating to MainActivity")
            val navigationSpan = tracer.spanBuilder("user-action.navigate")
                .setAttribute("from.screen", "SecondActivity")
                .setAttribute("to.screen", "MainActivity")
                .startSpan()
            navigationSpan.end()

            startActivity(Intent(this, MainActivity::class.java))
        }

        findViewById<Button>(R.id.btnNavigateToThird).setOnClickListener {
            Log.d(TAG, "Navigating to ThirdActivity")
            val navigationSpan = tracer.spanBuilder("user-action.navigate")
                .setAttribute("from.screen", "SecondActivity")
                .setAttribute("to.screen", "ThirdActivity")
                .startSpan()
            navigationSpan.end()

            startActivity(Intent(this, ThirdActivity::class.java))
        }

        findViewById<Button>(R.id.btnGoBack).setOnClickListener {
            Log.d(TAG, "Going back")
            val backSpan = tracer.spanBuilder("user-action.back")
                .setAttribute("from.screen", "SecondActivity")
                .startSpan()
            backSpan.end()

            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "SecondActivity onResume")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "SecondActivity onPause")
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "SecondActivity onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "SecondActivity onDestroy")
    }

    /**
     * Example backend API call - automatically tracked by Last9.
     *
     * What gets captured:
     * - HTTP method (GET)
     * - URL (httpbin.org/uuid)
     * - Status code
     * - Duration
     * - W3C traceparent header injected automatically
     */
    private fun fetchUserData() {
        val request = Request.Builder()
            .url("https://httpbin.org/uuid")
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "API call failed", e)
            }

            override fun onResponse(call: Call, response: Response) {
                Log.d(TAG, "API response: ${response.code}")
                response.close()
            }
        })
    }
}
