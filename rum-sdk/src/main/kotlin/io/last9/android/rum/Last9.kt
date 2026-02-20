package io.last9.android.rum

import android.app.Application
import io.last9.android.rum.internal.AgentConfigurator

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
 *             serviceName = "watcho-android"
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

    /**
     * Resets the SDK instance. Intended for use in tests only.
     */
    internal fun reset() {
        synchronized(this) { instance = null }
    }

    private fun buildInstance(
        app: Application,
        configure: Last9Options.() -> Unit,
    ): Last9RumInstance {
        val options = Last9Options().apply(configure)
        options.validate()
        val otelRum = AgentConfigurator.configure(app, options)
        return Last9RumInstance(otelRum, options)
    }
}
