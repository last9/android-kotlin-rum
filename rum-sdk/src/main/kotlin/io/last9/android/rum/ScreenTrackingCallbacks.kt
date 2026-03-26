package io.last9.android.rum

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import io.opentelemetry.api.trace.Tracer

/**
 * Automatic screen tracking for all Activities.
 *
 * This class automatically creates custom spans for screen views without requiring
 * any code changes to individual Activities. Register it once in your Application
 * class and it will track all Activities automatically.
 *
 * What it tracks:
 * - Screen view spans when an Activity is created
 * - Screen name attribute set to the Activity's simple class name
 * - Works for all current and future Activities
 *
 * Usage:
 * ```kotlin
 * class MyApp : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         Last9.init(this) { ... }
 *
 *         // Enable automatic screen tracking
 *         Last9.getInstance().enableAutomaticScreenTracking(this)
 *     }
 * }
 * ```
 *
 * What you'll see in Last9 dashboard:
 * - Span name: "screen.view"
 * - Attributes:
 *   - screen.name: Activity's simple class name (e.g., "MainActivity", "CheckoutActivity")
 *   - screen.class: Full Activity class name for debugging
 *
 * Benefits:
 * - No need to add tracking code to every Activity
 * - Works automatically for all Activities (even 50+ screens)
 * - Future-proof - new Activities are tracked automatically
 * - Clean separation of concerns - business logic stays in Activities
 */
internal class ScreenTrackingCallbacks(
    private val tracer: Tracer,
    private val debugMode: Boolean
) : Application.ActivityLifecycleCallbacks {

    companion object {
        private const val TAG = "ScreenTracking"
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        // Automatically create a screen view span for every Activity
        val screenName = activity.javaClass.simpleName

        if (debugMode) {
            Log.d(TAG, "Screen view: $screenName")
        }

        tracer.spanBuilder("screen.view")
            .setAttribute("screen.name", screenName)
            .setAttribute("screen.class", activity.javaClass.name)
            .startSpan()
            .end()
    }

    // Other lifecycle methods - no action needed, OpenTelemetry Android handles these
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
