package io.last9.android.rum

/**
 * Scopes the DSL so nested lambdas cannot accidentally access the outer receiver.
 */
@DslMarker
annotation class Last9Dsl

/**
 * Configuration for the Last9 Android RUM SDK.
 *
 * Use via [Last9.init]:
 * ```kotlin
 * Last9.init(this) {
 *     token = "YOUR-LAST9-INGESTION-TOKEN"
 *     serviceName = "my-android-app"
 * }
 * ```
 */
@Last9Dsl
class Last9Options {

    // -------------------------------------------------------------------------
    // Required
    // -------------------------------------------------------------------------

    /** Last9 ingestion token or Basic Auth credentials. Required. */
    var token: String = ""

    /** OpenTelemetry service.name resource attribute. Required. */
    var serviceName: String = ""

    // -------------------------------------------------------------------------
    // Optional — override defaults
    // -------------------------------------------------------------------------

    /**
     * OTLP ingestion base URL.
     * Traces will be exported to [baseUrl]/v1/traces.
     * Must be set to your OTLP endpoint.
     */
    var baseUrl: String = "YOUR_OTLP_ENDPOINT"

    /**
     * Use standard OTLP endpoint (/v1/traces) instead of beacon endpoint.
     * When true: Uses standard endpoint with Basic Auth or API key
     * When false: Uses beacon endpoint (/telemetry/beacon/v1/traces) with Bearer token
     * Default: false (for backward compatibility)
     */
    var useStandardEndpoint: Boolean = false

    /**
     * Use HTTP Basic Authentication instead of Bearer token.
     * When true: token is treated as base64-encoded "username:password"
     * When false: token is sent as "Bearer <token>" in X-LAST9-API-TOKEN header
     * Default: false (for backward compatibility)
     */
    var useBasicAuth: Boolean = false

    /** deployment.environment resource attribute (e.g. "production", "staging"). */
    var deploymentEnvironment: String = ""

    /** service.version resource attribute. */
    var serviceVersion: String = ""

    // -------------------------------------------------------------------------
    // Instrumentation toggles
    // -------------------------------------------------------------------------

    /** Auto-capture unhandled JVM exceptions as crash spans. Default: true. */
    var enableCrashReporting: Boolean = true

    /** Detect ANRs (main thread blocked > 5s) and emit spans. Default: true. */
    var enableAnrDetection: Boolean = true

    /**
     * Automatically create CLIENT spans and inject W3C traceparent headers
     * for all OkHttp requests. When enabled, call [Last9RumInstance.createOkHttpInterceptor]
     * and add it to your OkHttpClient. Default: true.
     */
    var enableOkHttpInstrumentation: Boolean = true

    // Note: app startup tracking (cold/warm) is always-on via the OTel Android agent.
    // A user-facing toggle will be added in a future release once the agent API stabilises.

    // -------------------------------------------------------------------------
    // Advanced
    // -------------------------------------------------------------------------

    /** Emit debug logs to Logcat. Default: false. */
    var debugMode: Boolean = false

    /**
     * Timeout for exporting spans to the OTLP endpoint, in seconds.
     * Default: 10 seconds.
     *
     * Increase this value if you're experiencing timeout errors on slow networks.
     * Recommended range: 10-30 seconds for mobile apps.
     */
    var exportTimeoutSeconds: Long = 10L

    /**
     * Extra resource attributes attached to every exported span and log.
     * Keys follow OTel semantic conventions where possible.
     *
     * Note: SDK-managed keys (`service.name`, `telemetry.sdk.name`, etc.) always
     * take precedence and cannot be overridden here.
     */
    var additionalResourceAttributes: Map<String, String> = emptyMap()

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    internal fun validate() {
        require(token.isNotBlank()) {
            "Last9Options: `token` must not be blank. " +
                "Get your token from the Last9 console."
        }
        require(serviceName.isNotBlank()) {
            "Last9Options: `serviceName` must not be blank."
        }
        require(baseUrl.isNotBlank()) {
            "Last9Options: `baseUrl` must not be blank."
        }
    }

    /** Fully-qualified OTLP traces endpoint. */
    internal fun tracesEndpoint(): String {
        val base = baseUrl.trimEnd('/')
        return if (useStandardEndpoint) {
            "$base/v1/traces"
        } else {
            "$base/telemetry/beacon/v1/traces"
        }
    }

    /** Authorization header pair for all OTLP export requests. */
    internal fun authHeader(): Pair<String, String> {
        return if (useBasicAuth) {
            "Authorization" to "Basic $token"
        } else {
            "X-LAST9-API-TOKEN" to "Bearer $token"
        }
    }

    /**
     * Masks the token to prevent accidental exposure in logs or crash reports.
     * Reflective loggers (Sentry, Firebase Crashlytics) will see `***` for the token.
     */
    override fun toString(): String =
        "Last9Options(serviceName=$serviceName, baseUrl=$baseUrl, " +
            "token=***, enableCrashReporting=$enableCrashReporting, " +
            "enableAnrDetection=$enableAnrDetection, " +
            "enableOkHttpInstrumentation=$enableOkHttpInstrumentation, " +
            "debugMode=$debugMode)"
}
