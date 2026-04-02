package io.last9.android.rum

import android.app.Application
import io.last9.android.rum.internal.AgentConfigurator
import io.last9.android.rum.session.SessionManager
import io.last9.android.rum.session.SessionStore
import io.last9.android.rum.session.UserInfo
import io.last9.android.rum.view.ViewManager

/**
 * Entry point for the Last9 Android RUM SDK.
 *
 * Initialize once in [Application.onCreate]:
 * ```kotlin
 * class MyApplication : Application() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         Last9.init(this) {
 *             token = "YOUR-LAST9-INGESTION-TOKEN"
 *             serviceName = "my-android-app"
 *         }
 *     }
 * }
 * ```
 *
 * After initialization, use [getInstance] to access the live SDK handle.
 */
object Last9 {

    @Volatile private var instance: Last9RumInstance? = null

    /**
     * Initializes the SDK and starts all configured instrumentation.
     *
     * Thread-safe: if called concurrently before the first instance is built,
     * only one initialization runs and the result is shared.
     *
     * Calling [init] a second time after initialization is a no-op — the
     * existing instance is returned unchanged.
     *
     * @param app    The application context.
     * @param configure DSL block to configure [Last9Options].
     * @return       The initialized [Last9RumInstance].
     */
    fun init(app: Application, configure: Last9Options.() -> Unit): Last9RumInstance {
        instance?.let { return it }
        return synchronized(this) {
            instance ?: buildInstance(app, configure).also { instance = it }
        }
    }

    /**
     * Returns the initialized [Last9RumInstance].
     *
     * @throws IllegalStateException if [init] has not been called.
     */
    fun getInstance(): Last9RumInstance =
        instance ?: error(
            "Last9 SDK has not been initialized. " +
                "Call Last9.init() in Application.onCreate() before using getInstance()."
        )

    /** Convenience for [Last9RumInstance.identify]. */
    fun identify(user: UserInfo?) = getInstance().identify(user)

    /** Convenience for [Last9RumInstance.clearUser]. */
    fun clearUser() = getInstance().clearUser()

    /**
     * Resets the SDK instance. Intended for use in tests only.
     */
    internal fun reset() {
        synchronized(this) {
            instance = null
            SessionStore.reset()
            io.last9.android.rum.internal.BreadcrumbStore.reset()
        }
    }

    private fun buildInstance(
        app: Application,
        configure: Last9Options.() -> Unit,
    ): Last9RumInstance {
        val options = Last9Options().apply(configure)
        options.validate()

        val otelRum = AgentConfigurator.configure(app, options)
        val tracer = otelRum.openTelemetry.getTracer("io.last9.android.rum")

        // Resolve circular dependency: ViewManager needs sessionManager for
        // resume checks, SessionManager needs viewManager for rollover.
        // Both lambdas capture the local var and are only invoked during
        // Activity lifecycle callbacks (long after buildInstance returns).
        var sessionManager: SessionManager? = null
        val viewManager = ViewManager(
            tracer = tracer,
            debugMode = options.debugMode,
            onResume = { sessionManager?.checkAndMaybeRollover() },
        )

        sessionManager = SessionManager(
            tracer = tracer,
            debugMode = options.debugMode,
            maxDurationMs = options.sessionMaxDurationMs,
            inactivityTimeoutMs = options.sessionInactivityTimeoutMs,
            onSessionRollover = { viewManager.onSessionRollover() },
        )

        sessionManager.start()

        return Last9RumInstance(otelRum, options, viewManager)
    }
}
