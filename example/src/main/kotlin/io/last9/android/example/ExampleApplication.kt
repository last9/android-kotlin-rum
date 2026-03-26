package io.last9.android.example

import android.app.Application
import io.last9.android.rum.Last9

/**
 * Example Application class demonstrating Last9 RUM SDK initialization.
 *
 * This class shows the recommended configuration for integrating Last9's
 * Real User Monitoring SDK into your Android application.
 *
 * To use this in your own app:
 * 1. Create a class extending Application (or use your existing one)
 * 2. Initialize Last9 in onCreate() before any other code
 * 3. Register this class in AndroidManifest.xml with android:name attribute
 */
class ExampleApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize Last9 SDK with configuration
        // This should be done as early as possible in your application lifecycle
        val rumInstance = Last9.init(this) {

            // ============================================================
            // REQUIRED CONFIGURATION
            // ============================================================

            /**
             * Authentication token for your OTLP endpoint.
             *
             * For Last9: Get your token from Last9 console
             * For other backends: Use your authentication token
             *
             * ⚠️ SECURITY WARNING: For production apps, use a proxy pattern instead
             * of embedding credentials directly. See README.md "Production Best Practices"
             * section for details.
             */
            token = "YOUR_TOKEN"

            /**
             * Service name that identifies your application in your observability backend.
             *
             * This appears in your dashboard and groups all traces from this app.
             * Use a consistent name across environments (e.g., "my-android-app").
             *
             * Recommended format: lowercase with hyphens (e.g., "my-app-name")
             */
            serviceName = "my-android-app"

            /**
             * OTLP endpoint URL (without path).
             *
             * This is the base URL where traces will be sent.
             * The SDK automatically appends the correct path based on useStandardEndpoint.
             *
             * Examples:
             * - Standard OTLP: "https://otlp.example.com" → sends to /v1/traces
             * - Last9: "https://otlp-aps1.last9.io:443" → sends to /v1/traces
             * - Your proxy: "https://telemetry-proxy.yourcompany.com" → sends to /v1/traces
             */
            baseUrl = "YOUR_OTLP_ENDPOINT"

            // ============================================================
            // ENDPOINT CONFIGURATION
            // ============================================================

            /**
             * Use standard OTLP endpoint (/v1/traces).
             *
             * When true: Uses industry-standard OpenTelemetry endpoint
             * When false: Uses beacon endpoint /telemetry/beacon/v1/traces
             *
             * For standard OTLP endpoints, set to true
             */
            useStandardEndpoint = true

            /**
             * Use HTTP Basic Authentication.
             *
             * When true: Sends "Authorization: Basic <token>" header
             * When false: Sends "X-LAST9-API-TOKEN: Bearer <token>" header
             *
             * For Basic Auth credentials, set to true
             */
            useBasicAuth = true

            // ============================================================
            // OPTIONAL CONFIGURATION
            // ============================================================

            /**
             * Deployment environment name.
             *
             * This helps you filter traces by environment in Last9 dashboard.
             * Common values: "production", "staging", "development", "qa"
             *
             * Use BuildConfig.BUILD_TYPE to auto-detect:
             *   deploymentEnvironment = BuildConfig.BUILD_TYPE  // "debug" or "release"
             */
            deploymentEnvironment = "development"

            /**
             * Application version.
             *
             * This appears in trace metadata and helps you correlate issues with releases.
             *
             * ✅ RECOMMENDED: Use BuildConfig.VERSION_NAME to auto-populate
             */
            serviceVersion = BuildConfig.VERSION_NAME

            /**
             * Enable debug logging to Logcat.
             *
             * When true: SDK logs initialization, export attempts, and errors
             * When false: Only critical errors are logged
             *
             * ⚠️ IMPORTANT: Set to false in production builds to avoid log spam
             *
             * Logs will show:
             * - "ExporterFactory: Creating OTLP exporter: ..."
             * - "Last9SpanExporter: Exporting N span(s)..."
             * - "Last9SpanExporter: Successfully exported N span(s)"
             * - Any export errors with details
             */
            debugMode = true

            // Explicitly enable Activity and Fragment tracking for testing
            enableActivityInstrumentation = true
            enableFragmentInstrumentation = true
            enableCrashReporting = true
            enableAnrDetection = true
            enableOkHttpInstrumentation = true

            /**
             * Timeout for exporting spans to OTLP endpoint (in seconds).
             *
             * Default: 10 seconds
             * Recommended range: 10-30 seconds for mobile apps
             *
             * Increase this if you're experiencing timeout errors on slow networks:
             * - WiFi: 10-15 seconds
             * - Mobile data (4G/5G): 20-30 seconds
             * - Mobile data (3G or slower): 30-60 seconds
             *
             * See TROUBLESHOOTING.md for timeout error solutions.
             *
             * Uncomment to customize:
             * exportTimeoutSeconds = 30
             */
        }

        // ============================================================
        // AUTOMATIC SCREEN TRACKING (RECOMMENDED FOR 50+ SCREENS)
        // ============================================================

        /**
         * Enable automatic screen tracking for all Activities.
         *
         * This is HIGHLY RECOMMENDED for apps with many screens (50+).
         * Instead of manually adding tracking code to every Activity,
         * this single line automatically tracks ALL Activities.
         *
         * What it does:
         * - Registers a global ActivityLifecycleCallbacks
         * - Automatically creates "screen.view" spans for every Activity
         * - Sets screen.name attribute to the Activity's class name
         * - Works for all current AND future Activities
         *
         * Benefits:
         * - No need to modify individual Activities
         * - Future-proof - new Activities are tracked automatically
         * - Scales to any number of screens
         * - Clean separation of concerns
         *
         * What you'll see in Last9:
         * - "screen.view" spans with screen.name = "MainActivity", "SecondActivity", etc.
         * - Plus automatic Activity lifecycle events: Created, Resumed, Paused, etc.
         */
        rumInstance.enableAutomaticScreenTracking(this)

        // ============================================================
        // SESSION TRACKING (AUTOMATIC - ZERO CONFIG)
        // ============================================================

        /**
         * Session tracking is now AUTOMATIC!
         *
         * The SDK automatically adds session.id to ALL spans with zero configuration.
         *
         * This is a workaround for OpenTelemetry Android 1.0.1 bug where
         * the built-in session.id is empty (issue #781).
         *
         * What you get automatically:
         * - Unique session.id attribute on every span
         * - Same session ID across the entire app session
         * - New session ID when app is restarted
         * - No code needed!
         *
         * Issue: https://github.com/open-telemetry/opentelemetry-android/issues/781
         */

        // ============================================================
        // FEATURE FLAGS (all enabled by default)
        // ============================================================

        /**
         * Note: The configuration block above has ended.
         * The following comments are for reference only.
         */

        if (false) {

            /**
             * Enable automatic crash reporting.
             *
             * When true: Unhandled exceptions are captured and sent as error spans
             * Default: true
             *
             * Uncomment to disable:
             * enableCrashReporting = false
             */

            /**
             * Enable ANR (Application Not Responding) detection.
             *
             * When true: Main thread blocking is detected and reported
             * Default: true
             *
             * Uncomment to disable:
             * enableAnrDetection = false
             */

            /**
             * Enable Activity lifecycle instrumentation.
             *
             * When true: Captures Activity lifecycle events (onCreate, onStart, onResume, etc.)
             * Default: true
             *
             * Each Activity transition will be recorded as a span with screen.name attribute
             * showing the Activity class name, helping you track user navigation flow.
             *
             * Uncomment to disable:
             * enableActivityInstrumentation = false
             */

            /**
             * Enable Fragment lifecycle instrumentation.
             *
             * When true: Captures Fragment lifecycle events
             * Default: true
             *
             * Useful for single-activity apps using Navigation Component or manual
             * Fragment transactions. Each Fragment transition will be recorded as a span.
             *
             * Uncomment to disable:
             * enableFragmentInstrumentation = false
             */

            /**
             * Enable automatic OkHttp instrumentation.
             *
             * When true: OkHttp interceptor is available via createOkHttpInterceptor()
             * Default: true
             *
             * Note: You still need to manually add the interceptor to your OkHttpClient
             * See MainActivity.kt for example usage
             *
             * Uncomment to disable:
             * enableOkHttpInstrumentation = false
             */

            // ============================================================
            // CUSTOM RESOURCE ATTRIBUTES (optional)
            // ============================================================

            /**
             * Add custom metadata to all traces from this app.
             *
             * These attributes appear in every trace and can be used for filtering
             * in the Last9 dashboard.
             *
             * Example use cases:
             * - Device information (model, manufacturer, OS version)
             * - User tier (free, premium, enterprise)
             * - Build metadata (commit hash, build number)
             *
             * Uncomment to add custom attributes:
             *
             * additionalResourceAttributes = mapOf(
             *     "device.model" to Build.MODEL,
             *     "device.manufacturer" to Build.MANUFACTURER,
             *     "os.version" to Build.VERSION.RELEASE,
             *     "app.platform" to "android"
             * )
             */
        }
    }
}
