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
 * All public methods must be called on the main thread (Android lifecycle
 * callbacks and Handler posts guarantee this).
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

        val persisted = SessionStore.getPersistedSession()
        if (persisted != null) {
            val now = System.currentTimeMillis()
            val elapsed = now - persisted.startedAt
            val inactiveFor = now - persisted.lastActivityAt

            val expiredByDuration = elapsed >= maxDurationMs
            val expiredByInactivity = inactiveFor >= inactivityTimeoutMs

            if (!expiredByDuration && !expiredByInactivity) {
                if (debugMode) Log.d(TAG, "Restoring session ${persisted.id} (elapsed=${elapsed}ms)")

                current = SessionInfo(
                    id = persisted.id,
                    previousId = persisted.previousId,
                    startedAt = persisted.startedAt,
                    lastActivityAt = now,
                    state = SessionState.ACTIVE,
                )

                SessionStore.setSession(persisted.id, persisted.previousId, persisted.startedAt, now)

                val remaining = maxDurationMs - elapsed
                scheduleRolloverIn(minOf(remaining, inactivityTimeoutMs))
                return
            }

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

        SessionStore.setSession(sessionId, previousId, now, now)

        if (debugMode) Log.d(TAG, "Session started: $sessionId (previous=$previousId)")

        scheduleRollover()
    }

    fun end(endState: SessionState = SessionState.ENDED) {
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

        current = session.copy(state = endState)
        clearRollover()

        if (debugMode) Log.d(TAG, "Session ended: ${session.id} ($endState, ${timeSpent}ms)")
    }

    fun rollover() {
        if (debugMode) Log.d(TAG, "Session rollover")
        end(SessionState.EXPIRED)
        start()
        onSessionRollover?.invoke()
    }

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
