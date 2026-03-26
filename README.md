# Last9 Android RUM SDK

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Android API](https://img.shields.io/badge/API-24%2B-brightgreen.svg)](https://developer.android.com/about/versions/nougat)

Open-source Real User Monitoring (RUM) SDK for Android, built on [OpenTelemetry](https://opentelemetry.io/). Exports telemetry via standard OTLP/HTTP — works with Last9, Jaeger, Grafana Tempo, or any OTLP-compatible backend.

## What it does

- **App startup tracking** — cold and warm start times via OTel Android agent
- **Crash reporting** — unhandled exceptions captured as error spans with stack traces
- **ANR detection** — main thread blocked >5s reported as spans
- **Activity lifecycle tracking** — automatic capture of all Activity events (onCreate, onResume, onPause, etc.)
- **Automatic screen tracking** — zero-code solution for tracking all screens (perfect for apps with 50+ Activities)
- **Session tracking** — automatic session.id on every span
- **OkHttp instrumentation** — automatic CLIENT spans with W3C `traceparent` injection for distributed tracing
- **Custom spans** — manual instrumentation API for business logic

## Quick Start

### 1. Add dependency

The SDK is not yet published to Maven Central. For now, use JitPack:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

// app/build.gradle.kts
dependencies {
    implementation("com.github.last9:android-kotlin-rum:v0.2.0")
}
```

### 2. Initialize in your Application class

```kotlin
import android.app.Application
import io.last9.android.rum.Last9

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        Last9.init(this) {
            // Required configuration
            token = "YOUR_TOKEN"                    // Your OTLP auth token
            serviceName = "my-android-app"          // Your app identifier
            baseUrl = "YOUR_OTLP_ENDPOINT"          // Your OTLP endpoint URL

            // Endpoint configuration
            useStandardEndpoint = true              // Use /v1/traces path
            useBasicAuth = true                     // Use Basic Auth header

            // Optional configuration
            deploymentEnvironment = "production"    // Environment name
            serviceVersion = BuildConfig.VERSION_NAME
            debugMode = false                       // Enable for troubleshooting
        }
    }
}
```

Register in `AndroidManifest.xml`:

```xml
<application android:name=".MyApp" ...>
    <!-- Your activities -->
</application>
```

### 3. Enable automatic screen tracking (recommended for apps with many screens)

**For apps with 50+ Activities, add just ONE line:**

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        Last9.init(this) { /* config */ }

        // 🎯 Add this ONE line to automatically track ALL Activities:
        Last9.getInstance().enableAutomaticScreenTracking(this)
    }
}
```

**What this does:**
- Automatically creates `screen.view` spans for EVERY Activity
- Adds `screen.name` attribute with Activity class name
- Works for all current AND future Activities
- No code changes needed in individual Activities!

**What you'll see in your backend:**
- Span name: `screen.view`
- Attributes: `screen.name` (e.g., "MainActivity", "ProfileActivity", "CheckoutActivity")
- All Activity lifecycle events: Created, Resumed, Paused, Stopped, Destroyed

### 4. Add OkHttp instrumentation (for backend API tracking)

```kotlin
val httpClient = OkHttpClient.Builder()
    .addInterceptor(Last9.getInstance().createOkHttpInterceptor())
    .build()

// Use with Retrofit
val retrofit = Retrofit.Builder()
    .baseUrl("https://api.example.com")
    .client(httpClient)
    .build()
```

**What this tracks:**
- HTTP method, URL, status code, duration
- Request/response headers and body size
- W3C `traceparent` header injection for distributed tracing
- Network errors and timeouts

### 5. Create custom spans (optional)

```kotlin
val tracer = Last9.getInstance().getTracer()
val span = tracer.spanBuilder("checkout.confirm")
    .setAttribute("user.id", userId)
    .setAttribute("cart.total", totalAmount)
    .startSpan()

try {
    processCheckout()
} catch (e: Exception) {
    span.recordException(e)
    throw e
} finally {
    span.end()
}
```

### 6. Verify integration

Enable debug logging temporarily:

```kotlin
Last9.init(this) {
    // ... your config
    debugMode = true
}
```

Check Logcat for `Last9SpanExporter` and `ExporterFactory` tags:

```
ExporterFactory: Creating OTLP exporter: YOUR_OTLP_ENDPOINT/v1/traces
Last9AgentConfigurator: Custom session tracking enabled
ScreenTracking: Screen view: MainActivity
Last9SpanExporter: Exporting 5 span(s)...
Last9SpanExporter: Successfully exported 5 span(s)
```

**Seeing timeout errors?** See [TROUBLESHOOTING.md](TROUBLESHOOTING.md#export-timeout-errors) for solutions.

## Configuration Options

| Option | Type | Required | Default | Description |
|--------|------|----------|---------|-------------|
| `token` | String | Yes | — | Auth token for the OTLP endpoint |
| `serviceName` | String | Yes | — | Identifies your app in the backend |
| `baseUrl` | String | Yes | — | OTLP endpoint base URL (without path) |
| `useStandardEndpoint` | Boolean | No | `false` | Use `/v1/traces` path (recommended for standard OTLP) |
| `useBasicAuth` | Boolean | No | `false` | Send `Authorization: Basic` header instead of `X-LAST9-API-TOKEN` |
| `deploymentEnvironment` | String | No | `""` | `deployment.environment` resource attribute |
| `serviceVersion` | String | No | `""` | `service.version` resource attribute |
| `enableCrashReporting` | Boolean | No | `true` | Capture unhandled exceptions |
| `enableAnrDetection` | Boolean | No | `true` | Detect Application Not Responding events |
| `enableActivityInstrumentation` | Boolean | No | `true` | Capture Activity lifecycle events |
| `enableFragmentInstrumentation` | Boolean | No | `true` | Capture Fragment lifecycle events |
| `enableOkHttpInstrumentation` | Boolean | No | `true` | Enable `createOkHttpInterceptor()` |
| `debugMode` | Boolean | No | `false` | Log span exports to Logcat |
| `exportTimeoutSeconds` | Long | No | `10` | Timeout for exporting spans (increase for slow networks) |
| `additionalResourceAttributes` | Map | No | `{}` | Extra OTel resource attributes on every span |

## Features

### Automatic Screen Tracking (Perfect for Apps with Many Screens)

**Problem:** Manually adding tracking code to 50+ Activities is tedious and error-prone.

**Solution:** One-line automatic screen tracking for ALL Activities.

```kotlin
// In Application.onCreate():
Last9.getInstance().enableAutomaticScreenTracking(this)

// That's it! All Activities are now tracked automatically
```

**Benefits:**
- ✅ Zero code in individual Activities
- ✅ Works for all current and future Activities
- ✅ Automatic screen.name attribute
- ✅ Scales to any number of Activities

### Session Tracking

**Automatic** — every span gets a unique `session.id` attribute:

- Same `session.id` across all spans in an app session
- New `session.id` when app is restarted
- No configuration needed

**Note:** This is a workaround for OpenTelemetry Android 1.0.1 bug ([issue #781](https://github.com/open-telemetry/opentelemetry-android/issues/781)) where the built-in session.id is empty.

### HTTP Request Tracking

Automatic tracking with OkHttp interceptor:

```kotlin
val httpClient = OkHttpClient.Builder()
    .addInterceptor(Last9.getInstance().createOkHttpInterceptor())
    .build()
```

**Captures:**
- All HTTP methods (GET, POST, PUT, DELETE, etc.)
- Request URL, headers, body size
- Response status code, headers, body size
- Request duration
- Network errors
- W3C traceparent header for distributed tracing

## Production Best Practices

### Use a proxy for credentials

❌ **Don't do this:**
```kotlin
Last9.init(this) {
    token = "hardcoded-secret-token"  // ❌ Exposed in APK!
    baseUrl = "https://backend-with-secrets.com"
}
```

✅ **Do this instead:**
```
App  ──►  Your Proxy  ──►  OTLP Backend
          (validates      (Last9, Jaeger,
           your auth)      Tempo, etc.)
```

```kotlin
Last9.init(this) {
    serviceName = "my-android-app"
    token = "your-app-session-token"        // Your own auth, not backend creds
    baseUrl = "https://telemetry.yourco.com"  // Your proxy
    useStandardEndpoint = true
    useBasicAuth = false                      // Proxy handles backend auth
}
```

The proxy validates the caller, injects backend credentials, and forwards the OTLP payload. See how [Datadog recommends this pattern](https://docs.datadoghq.com/real_user_monitoring/guide/proxy-mobile-rum-data/) for mobile apps.

### Store credentials securely

```kotlin
// Use BuildConfig for tokens
Last9.init(this) {
    token = BuildConfig.OTLP_TOKEN  // Set in build.gradle.kts
    baseUrl = BuildConfig.OTLP_ENDPOINT
}
```

```kotlin
// build.gradle.kts
android {
    defaultConfig {
        buildConfigField("String", "OTLP_TOKEN", "\"${project.findProperty("otlp.token") ?: ""}\"")
        buildConfigField("String", "OTLP_ENDPOINT", "\"${project.findProperty("otlp.endpoint") ?: ""}\"")
    }
}

// gradle.properties (add to .gitignore!)
otlp.token=YOUR_TOKEN
otlp.endpoint=YOUR_OTLP_ENDPOINT
```

## Example Configuration for Different Backends

### Last9

```kotlin
Last9.init(this) {
    token = "YOUR_LAST9_TOKEN"
    serviceName = "my-android-app"
    baseUrl = "https://otlp-aps1.last9.io:443"
    useStandardEndpoint = true
    useBasicAuth = true
}
```

### Jaeger

```kotlin
Last9.init(this) {
    token = ""  // Jaeger often doesn't require auth
    serviceName = "my-android-app"
    baseUrl = "http://localhost:4318"
    useStandardEndpoint = true
    useBasicAuth = false
}
```

### Grafana Cloud

```kotlin
Last9.init(this) {
    token = "YOUR_GRAFANA_CLOUD_TOKEN"
    serviceName = "my-android-app"
    baseUrl = "https://otlp-gateway-prod-us-central-0.grafana.net"
    useStandardEndpoint = true
    useBasicAuth = true
}
```

### Custom OTLP Endpoint

```kotlin
Last9.init(this) {
    token = "YOUR_TOKEN"
    serviceName = "my-android-app"
    baseUrl = "https://your-otlp-collector.com"
    useStandardEndpoint = true
    useBasicAuth = false
}
```

## Building from Source

**Requirements:** JDK 11+, Android SDK (API 36), Android Studio Ladybug or later.

```bash
git clone https://github.com/last9/android-kotlin-rum.git
cd android-kotlin-rum

# Run unit tests
./gradlew :rum-sdk:test

# Build the SDK AAR
./gradlew :rum-sdk:assembleRelease

# Run the example app (device/emulator required)
./gradlew :example:installDebug
```

The AAR is output to `rum-sdk/build/outputs/aar/rum-sdk-release.aar`.

## Architecture

```
Last9.init(app) { ... }
    │
    ├── Last9Options          ← Configuration DSL
    ├── AgentConfigurator     ← Wires OTel Android agent
    │   ├── ExporterFactory   ← OTLP/HTTP span exporter
    │   ├── Last9SpanExporter ← Debug logging wrapper
    │   ├── SessionManager    ← Custom session.id tracking
    │   └── ResourceAttributeBuilder ← device/OS/SDK attributes
    └── Last9RumInstance      ← Public API
        ├── getTracer()
        ├── createOkHttpInterceptor()
        └── enableAutomaticScreenTracking()
```

**Key dependencies:**
- [OpenTelemetry Android](https://github.com/open-telemetry/opentelemetry-android) v1.0.1 — crash, ANR, startup instrumentation
- [OpenTelemetry Java](https://github.com/open-telemetry/opentelemetry-java) v1.57.0 — OTLP exporter, SDK, API
- OkHttp 4.12.0 (`compileOnly` — apps bring their own version)

## Platform Support

| | Version |
|---|---|
| Minimum SDK | API 24 (Android 7.0) |
| Compile SDK | API 36 |
| Kotlin | 2.1+ |
| Java target | 11 |

**Note:** For apps with `minSdk < 26`, core library desugaring is automatically enabled to support Java 8+ APIs on older Android versions.

## FAQ

### How do I track screens in an app with 50+ Activities?

Use `enableAutomaticScreenTracking()` - it automatically tracks ALL Activities with zero code changes:

```kotlin
Last9.getInstance().enableAutomaticScreenTracking(this)
```

### Why is session.id empty?

This is a bug in OpenTelemetry Android 1.0.1 ([issue #781](https://github.com/open-telemetry/opentelemetry-android/issues/781)). This SDK includes a custom workaround that automatically adds `session.id` to all spans. No configuration needed!

### How do I track backend API calls?

Add the Last9 OkHttp interceptor:

```kotlin
val httpClient = OkHttpClient.Builder()
    .addInterceptor(Last9.getInstance().createOkHttpInterceptor())
    .build()
```

All HTTP requests from this client will be automatically tracked.

### Can I use this with other OTLP backends (not Last9)?

Yes! This SDK works with any OTLP-compatible backend: Jaeger, Grafana Tempo, SigNoz, etc. Just configure your endpoint and auth token.

## Contributing

Contributions are welcome! Please open an issue first to discuss what you'd like to change.

```bash
# Fork and clone
git clone https://github.com/<you>/android-kotlin-rum.git

# Create a branch
git checkout -b my-feature

# Make changes, run tests
./gradlew :rum-sdk:test

# Push and open a PR
git push origin my-feature
```

## License

Apache License 2.0 — see [LICENSE](LICENSE) for details.
