package io.last9.android.rum.view

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import io.last9.android.rum.internal.BreadcrumbStore
import io.last9.android.rum.internal.SemanticConventions
import io.last9.android.rum.session.SessionStore
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "Last9View"

/**
 * Activity-lifecycle-driven view tracking.
 *
 * Creates "View" spans matching the browser SDK's ViewRootSpanManager:
 * - view.id = traceId of the View span
 * - view.name = Activity simple class name (or custom name via [startView])
 * - view.time_spent = duration ms on span end
 * - view.error.count / view.resource.count aggregated during the view
 *
 * View context is injected on ALL spans via [io.last9.android.rum.internal.Last9SpanProcessor].
 */
internal class ViewManager(
    private val tracer: Tracer,
    private val debugMode: Boolean,
    internal val onResume: (() -> Unit)? = null,
) : Application.ActivityLifecycleCallbacks {

    private var currentViewSpan: Span? = null
    private var currentViewStartTime: Long? = null
    private var currentViewName: String? = null
    private val errorCount = AtomicLong(0)
    private val resourceCount = AtomicLong(0)

    private val viewLock = Any()

    override fun onActivityResumed(activity: Activity) {
        onResume?.invoke()

        val activityName = activity.javaClass.simpleName
        synchronized(viewLock) {
            if (currentViewName == activityName && currentViewSpan != null) {
                // Same activity resumed (e.g. returning from another app) — keep the view
                SessionStore.updateSessionActivity()
                return
            }
            // Different activity — end old view, start new one
            endCurrentViewLocked()
            startViewLocked(activityName)
        }
    }

    override fun onActivityPaused(activity: Activity) {
        // Don't end the view on pause — the view spans the time the user is on
        // this screen. It ends when a different Activity resumes (in onActivityResumed)
        // or on session rollover. This preserves error/resource counts across
        // brief pauses (e.g. going to recents and coming back).
    }

    fun startView(name: String) {
        synchronized(viewLock) {
            endCurrentViewLocked()
            startViewLocked(name)
        }
    }

    fun setViewName(name: String) {
        synchronized(viewLock) {
            currentViewName = name
            currentViewSpan?.setAttribute(SemanticConventions.VIEW_NAME, name)
            SessionStore.setCurrentView(SessionStore.getCurrentViewId(), name)
        }
    }

    fun endCurrentView() {
        synchronized(viewLock) {
            endCurrentViewLocked()
        }
    }

    /** Increment from any thread (e.g., OkHttp background thread). */
    fun incrementErrorCount() { errorCount.incrementAndGet() }
    fun incrementResourceCount() { resourceCount.incrementAndGet() }

    /** Called by SessionManager on session rollover — rotate the view span. */
    fun onSessionRollover() {
        synchronized(viewLock) {
            val savedName = currentViewName
            endCurrentViewLocked()
            if (savedName != null) {
                startViewLocked(savedName)
            }
        }
    }

    // ── Internal (must be called under viewLock) ────────────────

    private fun startViewLocked(name: String) {
        currentViewStartTime = System.currentTimeMillis()
        currentViewName = name
        errorCount.set(0)
        resourceCount.set(0)

        val span = tracer.spanBuilder(SemanticConventions.VIEW_SPAN_NAME)
            .setNoParent()
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute(SemanticConventions.VIEW_NAME, name)
            .startSpan()

        val viewId = span.spanContext.traceId
        span.setAttribute(SemanticConventions.VIEW_ID, viewId)
        span.setStatus(StatusCode.OK)

        currentViewSpan = span
        SessionStore.setCurrentView(viewId, name)
        SessionStore.updateSessionActivity()
        BreadcrumbStore.add("view", name)

        if (debugMode) Log.d(TAG, "View started: $name (viewId=$viewId)")
    }

    private fun endCurrentViewLocked() {
        val span = currentViewSpan ?: return
        val startTime = currentViewStartTime

        if (startTime != null) {
            val timeSpent = System.currentTimeMillis() - startTime
            span.setAttribute(SemanticConventions.VIEW_TIME_SPENT, timeSpent)
        }
        span.setAttribute(SemanticConventions.VIEW_ERROR_COUNT, errorCount.get())
        span.setAttribute(SemanticConventions.VIEW_RESOURCE_COUNT, resourceCount.get())
        span.end()

        if (debugMode) Log.d(TAG, "View ended: $currentViewName")

        currentViewSpan = null
        currentViewStartTime = null
        currentViewName = null
        SessionStore.setCurrentView(null, null)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
