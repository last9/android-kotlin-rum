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
        Last9.init(this) {

            // ============================================================
            // REQUIRED CONFIGURATION
            // ============================================================

            /**
             * Authentication token for Last9.
             *
             * This is a base64-encoded Basic Auth credential in format: username:password
             * Get this from your Last9 console.
             *
             * ⚠️ SECURITY WARNING: For production apps, use a proxy pattern instead
             * of embedding credentials directly. See README.md "Production Best Practices"
             * section for details.
             */
            token = "YOUR_BASE64_BASIC_AUTH_TOKEN"  // Replace with your actual Last9 token

            /**
             * Service name that identifies your application in Last9.
             *
             * This appears in your Last9 dashboard and groups all traces from this app.
             * Use a consistent name across environments (e.g., "my-android-app").
             *
             * Recommended format: lowercase with hyphens (e.g., "my-app-name")
             */
            serviceName = "android-kotlin-rum-test"

            /**
             * Last9 OTLP endpoint URL (without path).
             *
             * This is the base URL where traces will be sent.
             * The SDK automatically appends the correct path based on useStandardEndpoint.
             *
             * Examples:
             * - Standard OTLP: "https://otlp.example.com" → sends to /v1/traces
             * - Your proxy: "https://telemetry-proxy.yourcompany.com" → sends to /v1/traces
             */
            baseUrl = "https://otlp.example.com"

            // ============================================================
            // ENDPOINT CONFIGURATION
            // ============================================================

            /**
             * Use standard OTLP endpoint (/v1/traces).
             *
             * When true: Uses industry-standard OpenTelemetry endpoint
             * When false: Uses legacy beacon endpoint (not recommended for mobile)
             *
             * ✅ RECOMMENDED: Always set to true for mobile apps
             */
            useStandardEndpoint = true

            /**
             * Use HTTP Basic Authentication.
             *
             * When true: Sends "Authorization: Basic <token>" header
             * When false: Sends "X-LAST9-API-TOKEN: <token>" header
             *
             * ✅ RECOMMENDED: Set to true when using standard OTLP endpoint
             *
             * Note: If using a proxy, you may set this to false and let your
             * proxy handle Last9 authentication.
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

            // ============================================================
            // FEATURE FLAGS (all enabled by default)
            // ============================================================

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
