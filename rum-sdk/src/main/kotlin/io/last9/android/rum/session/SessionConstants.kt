package io.last9.android.rum.session

internal object SessionConstants {
    const val MAX_SESSION_DURATION_MS = 4L * 60 * 60 * 1000   // 4 hours
    const val SESSION_INACTIVITY_TIMEOUT_MS = 30L * 60 * 1000 // 30 minutes

    const val PREFS_NAME = "l9rum_session"
    const val PREFS_KEY_SESSION_ID = "session_id"
    const val PREFS_KEY_PREVIOUS_ID = "previous_id"
    const val PREFS_KEY_STARTED_AT = "started_at"
    const val PREFS_KEY_LAST_ACTIVITY_AT = "last_activity_at"
}
