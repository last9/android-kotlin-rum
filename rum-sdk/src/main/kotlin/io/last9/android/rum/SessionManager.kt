package io.last9.android.rum

import android.util.Log
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import java.util.UUID

/**
 * Custom session tracking manager.
 *
 * This is a workaround for OpenTelemetry Android 1.0.1 bug where session.id
 * is not populated. This manager creates a custom session ID and adds it to
 * all spans as a global attribute.
 *
 * What it provides:
 * - Unique session ID for each app session
 * - session.id attribute on ALL spans and logs
 * - Simple initialization (one line in Application.onCreate)
 *
 * Usage:
 * ```kotlin
 * class MyApp : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         val rumInstance = Last9.init(this) { ... }
 *         rumInstance.enableSessionTracking()  // Add this line
 *     }
 * }
 * ```
 *
 * What you'll see in Last9:
 * - "session.id" attribute on every span
 * - Same session ID across all spans in the same app session
 * - New session ID when app is restarted
 *
 * @internal Workaround for https://github.com/open-telemetry/opentelemetry-android/issues/781
 */
internal class SessionManager(
    private val debugMode: Boolean
) {
    companion object {
        private const val TAG = "SessionManager"
        private val SESSION_ID_KEY = AttributeKey.stringKey("session.id")
    }

    /**
     * Current session ID (generated on first access).
     */
    private val sessionId: String by lazy {
        val id = UUID.randomUUID().toString()
        if (debugMode) {
            Log.d(TAG, "Session created: $id")
        }
        id
    }

    /**
     * Get session attributes to add to global attributes.
     */
    fun getSessionAttributes(): Attributes {
        return Attributes.of(SESSION_ID_KEY, sessionId)
    }
}
