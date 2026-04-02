package io.last9.android.rum

import android.app.Application
import io.last9.android.rum.instrumentation.OkHttpInstrumentation
import io.last9.android.rum.session.SessionStore
import io.last9.android.rum.session.UserInfo
import io.last9.android.rum.view.ViewManager
import io.opentelemetry.android.OpenTelemetryRum
import io.opentelemetry.api.trace.Tracer

/**
 * A live handle to an initialized Last9 RUM session.
 *
 * Obtain via [Last9.init] or [Last9.getInstance].
 *
 * ```kotlin
 * val rum = Last9.getInstance()
 *
 * // Manual span
 * val tracer = rum.getTracer()
 * val span = tracer.spanBuilder("checkout.confirm").startSpan()
 * span.end()
 *
 * // OkHttp with W3C traceparent auto-injection
 * val client = OkHttpClient.Builder()
 *     .addInterceptor(rum.createOkHttpInterceptor())
 *     .build()
 *
 * // Automatic screen tracking for all Activities
 * rum.enableAutomaticScreenTracking(app)
 *
 * // User identity (injected on all spans)
 * rum.identify(UserInfo(id = "u123", name = "Alice", email = "alice@example.com"))
 * ```
 */
class Last9RumInstance internal constructor(
    /** The underlying [OpenTelemetryRum] instance for advanced use cases. */
    val otelRum: OpenTelemetryRum,
    private val options: Last9Options,
    internal val viewManager: ViewManager,
) {
    /**
     * Returns an OTel [Tracer] for creating manual spans.
     *
     * @param instrumentationName Defaults to [Last9Options.serviceName].
     */
    fun getTracer(instrumentationName: String = options.serviceName): Tracer =
        otelRum.openTelemetry.getTracer(instrumentationName)

    /**
     * Returns an OkHttp [okhttp3.Interceptor] that:
     * - Creates a CLIENT span for each HTTP request
     * - Injects W3C `traceparent` headers for backend correlation
     *
     * Add to your `OkHttpClient.Builder` via `addInterceptor()`.
     * Only available when [Last9Options.enableOkHttpInstrumentation] is true.
     */
    fun createOkHttpInterceptor(): okhttp3.Interceptor {
        check(options.enableOkHttpInstrumentation) {
            "OkHttp instrumentation is disabled. Set enableOkHttpInstrumentation = true " +
                "in Last9.init { } to use createOkHttpInterceptor()."
        }
        return OkHttpInstrumentation(otelRum.openTelemetry)
    }

    /**
     * Enable automatic screen/view tracking for all Activities.
     *
     * Creates "View" spans with `view.id`, `view.name`, and `view.time_spent`.
     * View context (`view.id` + `view.name`) is injected on ALL spans while
     * the view is active.
     *
     * @param application The Application instance to register callbacks with.
     */
    fun enableAutomaticScreenTracking(application: Application) {
        application.registerActivityLifecycleCallbacks(viewManager)
    }

    /**
     * Set user identity. Attributes are injected on all subsequent spans.
     * Pass null to clear.
     */
    fun identify(user: UserInfo?) {
        if (user == null) {
            clearUser()
            return
        }
        SessionStore.setCurrentUser(
            UserInfo(
                id = user.id?.takeIf { it.isNotBlank() },
                name = user.name?.takeIf { it.isNotBlank() },
                email = user.email?.takeIf { it.isNotBlank() },
                roles = user.roles?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() },
            )
        )
    }

    /** Clear user identity from all subsequent spans. */
    fun clearUser() {
        SessionStore.setCurrentUser(null)
    }

    /** Start a named view manually (for non-Activity screens like Compose). */
    fun startView(name: String) {
        viewManager.startView(name)
    }

    /** Override the current view name (e.g., after loading dynamic content). */
    fun setViewName(name: String) {
        viewManager.setViewName(name)
    }
}
