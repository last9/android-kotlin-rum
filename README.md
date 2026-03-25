# Last9 Android RUM SDK

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Android API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://developer.android.com/about/versions/oreo)

Open-source Real User Monitoring (RUM) SDK for Android, built on [OpenTelemetry](https://opentelemetry.io/). Exports telemetry via standard OTLP/HTTP — works with Last9, Jaeger, Grafana Tempo, or any OTLP-compatible backend.

## What it does

- **App startup tracking** — cold and warm start times via OTel Android agent
- **Crash reporting** — unhandled exceptions captured as error spans with stack traces
- **ANR detection** — main thread blocked >5s reported as spans
- **OkHttp instrumentation** — automatic CLIENT spans with W3C `traceparent` injection for distributed tracing
- **Session tracking** — automatic session lifecycle management
- **Custom spans** — manual instrumentation API for business logic

## Quick Start

### 1. Add dependency

The SDK is not yet published to Maven Central. For now, use a local build or JitPack:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven("https://jitpack.io")
    }
}

// app/build.gradle.kts
dependencies {
    implementation("com.github.last9:android-kotlin-rum:<version>")
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
            token = "<your-auth-token>"
            serviceName = "my-android-app"
            baseUrl = "https://your-otlp-endpoint.com"
            useStandardEndpoint = true
            useBasicAuth = true

            // Optional
            deploymentEnvironment = "production"
            serviceVersion = BuildConfig.VERSION_NAME
        }
    }
}
```

Register in `AndroidManifest.xml`:

```xml
<application android:name=".MyApp" ... />
```

### 3. Add OkHttp instrumentation (optional)

```kotlin
val rum = Last9.getInstance()

val httpClient = OkHttpClient.Builder()
    .addInterceptor(rum.createOkHttpInterceptor())
    .build()
```

This automatically creates spans for every HTTP request and injects `traceparent` headers for distributed tracing across your backend services.

### 4. Create custom spans (optional)

```kotlin
val tracer = Last9.getInstance().getTracer()
val span = tracer.spanBuilder("checkout.confirm").startSpan()

try {
    processCheckout()
} catch (e: Exception) {
    span.recordException(e)
    throw e
} finally {
    span.end()
}
```

### 5. Verify

Enable debug logging temporarily:

```kotlin
Last9.init(this) {
    // ...
    debugMode = true
}
```

Check Logcat for `Last9SpanExporter` and `ExporterFactory` tags. You should see:

```
ExporterFactory: Creating OTLP exporter:
ExporterFactory:   Endpoint: https://otlp.example.com/v1/traces
Last9SpanExporter: Exporting 3 span(s)...
Last9SpanExporter: Successfully exported 3 span(s)
```

**Seeing timeout errors?** See [TROUBLESHOOTING.md](TROUBLESHOOTING.md#export-timeout-errors) for solutions.

## Configuration

| Option | Type | Required | Default | Description |
|--------|------|----------|---------|-------------|
| `token` | String | Yes | — | Auth token for the OTLP endpoint |
| `serviceName` | String | Yes | — | Identifies your app in the backend |
| `baseUrl` | String | Yes | — | OTLP endpoint base URL |
| `useStandardEndpoint` | Boolean | No | `false` | Use `/v1/traces` path (recommended for most setups) |
| `useBasicAuth` | Boolean | No | `false` | Send `Authorization: Basic` header instead of `X-LAST9-API-TOKEN` |
| `deploymentEnvironment` | String | No | `""` | `deployment.environment` resource attribute |
| `serviceVersion` | String | No | `""` | `service.version` resource attribute |
| `enableCrashReporting` | Boolean | No | `true` | Capture unhandled exceptions |
| `enableAnrDetection` | Boolean | No | `true` | Detect Application Not Responding events |
| `enableOkHttpInstrumentation` | Boolean | No | `true` | Enable `createOkHttpInterceptor()` |
| `debugMode` | Boolean | No | `false` | Log span exports to Logcat |
| `exportTimeoutSeconds` | Long | No | `10` | Timeout for exporting spans (increase for slow networks) |
| `additionalResourceAttributes` | Map | No | `{}` | Extra OTel resource attributes on every span |

## Production: Use a proxy

Don't embed backend credentials in your APK. Route telemetry through your own proxy:

```
App  ──►  Your Proxy  ──►  OTLP Backend
          (holds real       (Last9, Jaeger,
           credentials)      Tempo, etc.)
```

```kotlin
Last9.init(this) {
    serviceName = "my-android-app"
    token = "<your-app-session-token>"        // your own auth, not backend creds
    baseUrl = "https://telemetry.yourco.com"  // your proxy
    useStandardEndpoint = true
    useBasicAuth = false                      // proxy handles backend auth
}
```

The proxy validates the caller, injects backend credentials, and forwards the OTLP payload. See how [Datadog recommends this pattern](https://docs.datadoghq.com/real_user_monitoring/guide/proxy-mobile-rum-data/) for mobile apps.

## Building from source

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

The AAR is output to `rum-sdk/build/outputs/aar/`.

## Architecture

```
Last9.init(app) { ... }
    │
    ├── Last9Options          ← Configuration DSL
    ├── AgentConfigurator     ← Wires OTel Android agent
    │   ├── ExporterFactory   ← OTLP/HTTP span exporter
    │   ├── Last9SpanExporter ← Debug logging wrapper
    │   └── ResourceAttributeBuilder ← device/OS/SDK attributes
    └── Last9RumInstance      ← Public handle
        ├── getTracer()
        └── createOkHttpInterceptor()
```

**Key dependencies:**
- [OpenTelemetry Android](https://github.com/open-telemetry/opentelemetry-android) v1.0.1 — crash, ANR, startup, session instrumentation
- [OpenTelemetry Java](https://github.com/open-telemetry/opentelemetry-java) v1.57.0 — OTLP exporter, SDK, API
- OkHttp 4.12.0 (`compileOnly` — apps bring their own version)

## Platform support

| | Version |
|---|---|
| Minimum SDK | API 26 (Android 8.0) |
| Compile SDK | API 36 |
| Kotlin | 2.1+ |
| Java target | 11 |

## Contributing

Contributions are welcome. Please open an issue first to discuss what you'd like to change.

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
