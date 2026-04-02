package io.last9.android.rum.session

import android.content.Context
import android.content.SharedPreferences

/**
 * Thread-safe in-memory + SharedPreferences store for session, view, and user state.
 *
 * The [io.last9.android.rum.internal.Last9SpanProcessor] reads from this store
 * on every span start — reads must be fast and lock-free.
 *
 * Analog of the browser SDK's `sessions/store.ts`.
 */
internal object SessionStore {

    @Volatile private var currentSessionId: String? = null
    @Volatile private var previousSessionId: String? = null
    @Volatile private var sessionStartedAt: Long? = null
    @Volatile private var sessionLastActivityAt: Long? = null

    @Volatile private var currentViewId: String? = null
    @Volatile private var currentViewName: String? = null

    @Volatile private var currentUser: UserInfo? = null

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(
            SessionConstants.PREFS_NAME, Context.MODE_PRIVATE
        )
    }

    // ── Session ─────────────────────────────────────────────────

    fun setCurrentSessionId(id: String?) {
        currentSessionId = id
        persistToPrefs()
    }

    fun getCurrentSessionId(): String? = currentSessionId

    fun setPreviousSessionId(id: String?) {
        previousSessionId = id
        persistToPrefs()
    }

    fun getPreviousSessionId(): String? = previousSessionId

    fun setSessionStartedAt(ts: Long?) {
        sessionStartedAt = ts
        persistToPrefs()
    }

    fun getSessionStartedAt(): Long? = sessionStartedAt

    fun setSessionLastActivityAt(ts: Long?) {
        sessionLastActivityAt = ts
        persistToPrefs()
    }

    fun getSessionLastActivityAt(): Long? = sessionLastActivityAt

    fun updateSessionActivity() {
        sessionLastActivityAt = System.currentTimeMillis()
        persistToPrefs()
    }

    // ── Persistence ─────────────────────────────────────────────

    fun getPersistedSession(): PersistedSession? {
        val p = prefs ?: return null
        val id = p.getString(SessionConstants.PREFS_KEY_SESSION_ID, null) ?: return null
        val startedAt = p.getLong(SessionConstants.PREFS_KEY_STARTED_AT, 0L)
        if (startedAt == 0L) return null
        return PersistedSession(
            id = id,
            previousId = p.getString(SessionConstants.PREFS_KEY_PREVIOUS_ID, null),
            startedAt = startedAt,
            lastActivityAt = p.getLong(SessionConstants.PREFS_KEY_LAST_ACTIVITY_AT, startedAt),
        )
    }

    fun clearPersistedSession() {
        prefs?.edit()?.clear()?.apply()
    }

    private fun persistToPrefs() {
        val id = currentSessionId ?: return
        val startedAt = sessionStartedAt ?: return
        prefs?.edit()
            ?.putString(SessionConstants.PREFS_KEY_SESSION_ID, id)
            ?.putString(SessionConstants.PREFS_KEY_PREVIOUS_ID, previousSessionId)
            ?.putLong(SessionConstants.PREFS_KEY_STARTED_AT, startedAt)
            ?.putLong(
                SessionConstants.PREFS_KEY_LAST_ACTIVITY_AT,
                sessionLastActivityAt ?: startedAt
            )
            ?.apply()
    }

    // ── View ────────────────────────────────────────────────────

    fun setCurrentView(viewId: String?, viewName: String?) {
        currentViewId = viewId
        currentViewName = viewName
    }

    fun getCurrentViewId(): String? = currentViewId
    fun getCurrentViewName(): String? = currentViewName

    // ── User ────────────────────────────────────────────────────

    fun setCurrentUser(user: UserInfo?) {
        currentUser = user
    }

    fun getCurrentUser(): UserInfo? = currentUser

    // ── Reset (testing) ─────────────────────────────────────────

    internal fun reset() {
        currentSessionId = null
        previousSessionId = null
        sessionStartedAt = null
        sessionLastActivityAt = null
        currentViewId = null
        currentViewName = null
        currentUser = null
        clearPersistedSession()
    }
}
