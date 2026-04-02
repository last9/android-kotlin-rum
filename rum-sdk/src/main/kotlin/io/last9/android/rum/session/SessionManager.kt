package io.last9.android.rum.session

import android.os.Handler
import android.os.Looper
import android.util.Log
import io.last9.android.rum.internal.SemanticConventions
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer

private const val TAG = "Last9Session"

/**
 * Full session lifecycle manager matching the browser SDK's `SessionManager`.
 *
 * - Emits "Session Start" and "Session End" spans
 * - session.id = traceId of the "Session Start" span (browser convention)
 * - Timeout-based rollover (max duration + inactivity)
 * - Persists to SharedPreferences, restores on next launch
 * - Notifies [onSessionRollover] so ViewManager can rotate views
 */
internal class SessionManager(
    private val tracer: Tracer,
    private val debugMode: Boolean,
    private val maxDurationMs: Long = SessionConstants.MAX_SESSION_DURATION_MS,
    private val inactivityTimeoutMs: Long = SessionConstants.SESSION_INACTIVITY_TIMEOUT_MS,
    private val onSessionRollover: (() -> Unit)? = null,
) {
    private var current: SessionInfo? = null
    private val handler = Handler(Looper.getMainLooper())
    private var rolloverRunnable: Runnable? = null

    fun start() {
        if (current?.state == SessionState.ACTIVE) {
            if (debugMode) Log.d(TAG, "start() called while session active, ignoring")
            return
        }

        // Check for persisted session from SharedPreferences
        val persisted = SessionStore.getPersistedSession()
        if (persisted != null) {
            val now = System.currentTimeMillis()
            val elapsed = now - persisted.startedAt
            val inactiveFor = now - persisted.lastActivityAt

            val expiredByDuration = elapsed >= maxDurationMs
            val expiredByInactivity = inactiveFor >= inactivityTimeoutMs

            if (!expiredByDuration && !expiredByInactivity) {
                // Restore existing session — it's still valid
                if (debugMode) Log.d(TAG, "Restoring session ${persisted.id} (elapsed=${elapsed}ms)")

                current = SessionInfo(
                    id = persisted.id,
                    previousId = persisted.previousId,
                    startedAt = persisted.startedAt,
                    lastActivityAt = now,
                    state = SessionState.ACTIVE,
                )

                SessionStore.setPreviousSessionId(persisted.previousId)
                SessionStore.setCurrentSessionId(persisted.id)
                SessionStore.setSessionStartedAt(persisted.startedAt)
                SessionStore.setSessionLastActivityAt(now)

                val remaining = maxDurationMs - elapsed
                scheduleRolloverIn(minOf(remaining, inactivityTimeoutMs))
                return
            }

            // Persisted session expired — use it as previousId
            if (debugMode) {
                val reason = if (expiredByInactivity) "inactivity" else "max_duration"
                Log.d(TAG, "Persisted session ${persisted.id} expired ($reason)")
            }
            current = SessionInfo(
                id = persisted.id,
                previousId = persisted.previousId,
                startedAt = persisted.startedAt,
                lastActivityAt = persisted.lastActivityAt,
                state = SessionState.EXPIRED,
            )
        }

        // Create new session
        val previousId = current?.id
        val startSpan = tracer.spanBuilder(SemanticConventions.SESSION_START_NAME)
            .setNoParent()
            .setSpanKind(SpanKind.INTERNAL)
            .startSpan()

        val sessionId = startSpan.spanContext.traceId
        if (previousId != null) {
            startSpan.setAttribute(SemanticConventions.SESSION_PREVIOUS_ID, previousId)
        }
        startSpan.setStatus(StatusCode.OK)
        startSpan.end()

        val now = System.currentTimeMillis()
        current = SessionInfo(
            id = sessionId,
            previousId = previousId,
            startedAt = now,
            lastActivityAt = now,
            state = SessionState.ACTIVE,
        )

        SessionStore.setPreviousSessionId(previousId)
        SessionStore.setCurrentSessionId(sessionId)
        SessionStore.setSessionStartedAt(now)
        SessionStore.setSessionLastActivityAt(now)

        if (debugMode) Log.d(TAG, "Session started: $sessionId (previous=$previousId)")

        scheduleRollover()
    }

    fun end(reason: String = "exit") {
        val session = current ?: return
        if (session.state != SessionState.ACTIVE) return

        val timeSpent = System.currentTimeMillis() - session.startedAt

        val endSpan = tracer.spanBuilder(SemanticConventions.SESSION_END_NAME)
            .setNoParent()
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute(SemanticConventions.SESSION_ID, session.id)
            .setAttribute(SemanticConventions.SESSION_TIME_SPENT, timeSpent)
            .startSpan()
        endSpan.setStatus(StatusCode.OK)
        endSpan.end()

        current = session.copy(
            state = if (reason == "expired") SessionState.EXPIRED else SessionState.ENDED
        )
        clearRollover()

        if (debugMode) Log.d(TAG, "Session ended: ${session.id} ($reason, ${timeSpent}ms)")
    }

    fun rollover() {
        if (debugMode) Log.d(TAG, "Session rollover")
        end("expired")
        start()
        onSessionRollover?.invoke()
    }

    /**
     * Called on Activity resume to check if the session has expired due to inactivity.
     * Cheap operation: two timestamp comparisons.
     */
    fun checkAndMaybeRollover() {
        val session = current ?: return
        if (session.state != SessionState.ACTIVE) return

        val now = System.currentTimeMillis()
        val elapsed = now - session.startedAt
        val inactiveFor = now - session.lastActivityAt

        if (elapsed >= maxDurationMs || inactiveFor >= inactivityTimeoutMs) {
            if (debugMode) {
                val reason = if (inactiveFor >= inactivityTimeoutMs) "inactivity" else "max_duration"
                Log.d(TAG, "Session expired on resume ($reason)")
            }
            rollover()
        } else {
            // Session still valid — update activity and reschedule
            current = session.copy(lastActivityAt = now)
            SessionStore.updateSessionActivity()

            val remaining = maxDurationMs - elapsed
            scheduleRolloverIn(minOf(remaining, inactivityTimeoutMs))
        }
    }

    fun getSessionId(): String? = current?.id

    private fun scheduleRollover() {
        scheduleRolloverIn(minOf(maxDurationMs, inactivityTimeoutMs))
    }

    private fun scheduleRolloverIn(delayMs: Long) {
        clearRollover()
        val safeDelay = maxOf(0L, delayMs)
        val runnable = Runnable {
            try {
                rollover()
            } catch (e: Exception) {
                Log.e(TAG, "Rollover failed", e)
            }
        }
        rolloverRunnable = runnable
        handler.postDelayed(runnable, safeDelay)
    }

    private fun clearRollover() {
        rolloverRunnable?.let { handler.removeCallbacks(it) }
        rolloverRunnable = null
    }
}
