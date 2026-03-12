# Last9 Android Kotlin RUM SDK

Real User Monitoring (RUM) SDK for Android applications. Built on OpenTelemetry, this SDK automatically captures application performance, user interactions, network requests, crashes, and ANRs.

## Features

- ✅ **Automatic Performance Tracking**: App startup time, screen load times, UI rendering
- ✅ **User Interaction Monitoring**: Button clicks, navigation, gestures
- ✅ **Network Request Tracing**: Automatic HTTP/HTTPS instrumentation with distributed tracing
- ✅ **Crash & ANR Detection**: Unhandled exceptions and Application Not Responding events
- ✅ **Custom Instrumentation**: Manual span creation for business logic tracking
- ✅ **Zero Configuration OpenTelemetry**: Based on industry-standard OTLP protocol

## Quick Start

### 1. Add SDK Dependency

Add to your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.last9.android:rum-sdk:1.0.0")
}
```

### 2. Get Your Last9 Credentials

You'll need:
- **Service Name**: Identifier for your app (e.g., `my-android-app`)
- **Basic Auth Token**: Get from Last9 console (base64 encoded `username:password`)
- **Endpoint URL**: Your Last9 OTLP endpoint (e.g., `YOUR_OTLP_ENDPOINT`)

### 3. Initialize SDK in Your Application Class

Create or update your `Application` class:

```kotlin
import android.app.Application
import io.last9.android.rum.Last9

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        Last9.init(this) {
            // Required
            token = "YOUR_BASE64_BASIC_AUTH_TOKEN"
            serviceName = "my-android-app"
            baseUrl = "YOUR_OTLP_ENDPOINT"

            // Use standard OTLP endpoint with Basic Auth
            useStandardEndpoint = true
            useBasicAuth = true

            // Optional - environment tracking
            deploymentEnvironment = "production"  // or "staging", "development"
            serviceVersion = BuildConfig.VERSION_NAME

            // Optional - features (enabled by default)
            enableCrashReporting = true
            enableAnrDetection = true
            enableOkHttpInstrumentation = true
        }
    }
}
```

### 4. Register Your Application Class

In `AndroidManifest.xml`:

```xml
<application
    android:name=".MyApp"
    ...>
</application>
```

### 5. Add OkHttp Instrumentation (Optional but Recommended)

If you're using OkHttp for network requests:

```kotlin
import io.last9.android.rum.Last9
import okhttp3.OkHttpClient

val httpClient = OkHttpClient.Builder()
    .addInterceptor(Last9.createOkHttpInterceptor())
    .build()
```

This automatically:
- Creates spans for all HTTP requests
- Injects W3C `traceparent` headers for distributed tracing
- Captures request/response metadata (method, URL, status code, duration)

### 6. Verify Integration

Enable debug mode temporarily to verify spans are being exported:

```kotlin
Last9.init(this) {
    // ... other config
    debugMode = true  // Enable debug logging
}
```

Check Logcat for:
```
Last9: SDK initialized successfully
Last9: Exporter configured: https://YOUR_OTLP_ENDPOINT/v1/traces
Last9: Exported 3 spans successfully
```

Then check your Last9 dashboard for incoming traces!

## Advanced Usage

### Custom Spans for Business Logic

Track specific operations in your app:

```kotlin
import io.last9.android.rum.Last9

// Example: Track video playback
val span = Last9.startSpan("video.playback")
    .setAttribute("video.id", videoId)
    .setAttribute("video.duration_ms", durationMs)
    .setAttribute("user.id", userId)

try {
    playVideo(videoId)
} catch (e: Exception) {
    span.recordException(e)
    throw e
} finally {
    span.end()
}
```

### Track Custom User Actions

```kotlin
// Track user interactions
Last9.startSpan("user.checkout")
    .setAttribute("cart.total", totalAmount)
    .setAttribute("cart.items", itemCount)
    .end()

// Track navigation
Last9.startSpan("screen.view")
    .setAttribute("screen.name", "ProductDetails")
    .setAttribute("product.id", productId)
    .end()
```

### Add Global Context

Add metadata that applies to all traces:

```kotlin
Last9.init(this) {
    // ... other config
    additionalResourceAttributes = mapOf(
        "device.model" to Build.MODEL,
        "device.manufacturer" to Build.MANUFACTURER,
        "os.version" to Build.VERSION.RELEASE,
        "user.tier" to "premium"
    )
}
```

## Configuration Reference

### Required Options

| Option | Type | Description |
|--------|------|-------------|
| `token` | String | Last9 Basic Auth token (base64 encoded username:password) |
| `serviceName` | String | Identifier for your application |
| `baseUrl` | String | Last9 OTLP endpoint URL |

### Endpoint Configuration

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `useStandardEndpoint` | Boolean | false | Use standard OTLP `/v1/traces` endpoint (recommended) |
| `useBasicAuth` | Boolean | false | Use HTTP Basic Auth instead of Bearer token (recommended) |

### Optional Configuration

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `deploymentEnvironment` | String | "" | Environment name (production, staging, development) |
| `serviceVersion` | String | "" | Application version |
| `enableCrashReporting` | Boolean | true | Capture unhandled exceptions |
| `enableAnrDetection` | Boolean | true | Detect Application Not Responding (ANR) events |
| `enableOkHttpInstrumentation` | Boolean | true | Enable automatic HTTP tracing |
| `debugMode` | Boolean | false | Enable debug logging to Logcat |
| `additionalResourceAttributes` | Map<String, String> | {} | Custom metadata for all traces |

## What Gets Tracked Automatically?

### Application Performance
- **App Startup**: Cold start, warm start times
- **Screen Rendering**: Time to first render, total render time
- **UI Interactions**: Activity/Fragment lifecycle events

### Network Requests (with OkHttp interceptor)
- Request method, URL, headers
- Response status code, content length
- Request duration
- Distributed tracing context propagation

### Errors & Stability
- Unhandled exceptions with full stack traces
- ANR events with thread dumps
- HTTP error responses

## Production Best Practices

### 1. Use Proxy Pattern (Recommended)

**Don't embed Last9 credentials directly in your APK.** Use a proxy server:

```
┌──────────────┐         ┌─────────────────────┐         ┌──────────────┐
│   Your App   │  ──►    │   Your Proxy        │  ──►    │  Last9 OTLP  │
│              │         │   (your backend)    │         │  Collector   │
│  Sends:      │         │                     │         │              │
│  App auth    │         │  Validates caller   │         │  Basic Auth  │
│  (JWT/token) │         │  Injects Last9 auth │         │  (secure)    │
└──────────────┘         └─────────────────────┘         └──────────────┘
```

App configuration:
```kotlin
Last9.init(this) {
    serviceName = "my-android-app"
    token = "your-app-internal-token"  // Your own auth, not Last9 credentials
    baseUrl = "https://telemetry-proxy.yourcompany.com"
    useStandardEndpoint = true
    useBasicAuth = false  // Your proxy handles Last9 auth
}
```

See [`examples/DISHTV_CONFIGURATION.md`](examples/DISHTV_CONFIGURATION.md) for detailed proxy setup.

### 2. ProGuard/R8 Configuration

Add to `proguard-rules.pro`:

```proguard
# Keep Last9 SDK classes
-keep class io.last9.android.rum.** { *; }

# Keep OpenTelemetry classes used by Last9
-keep class io.opentelemetry.** { *; }
-dontwarn io.opentelemetry.**
```

### 3. Security

- ✅ Use proxy pattern in production
- ✅ Rotate authentication tokens regularly
- ✅ Enable ProGuard/R8 obfuscation
- ✅ Don't log sensitive user data in custom spans
- ✅ Disable `debugMode` in production builds

### 4. Performance

The SDK is designed for minimal overhead:
- Asynchronous span export (non-blocking)
- Automatic batching of multiple spans
- 10-second export timeout
- Graceful degradation on network failures
- No impact on app startup time

## Troubleshooting

### No data in Last9 dashboard

1. **Enable debug mode** and check Logcat:
```kotlin
Last9.init(this) {
    debugMode = true
}
```

2. **Verify configuration**:
   - Token is correct base64 Basic Auth
   - `useStandardEndpoint = true`
   - `useBasicAuth = true`
   - `baseUrl` points to correct Last9 endpoint

3. **Check network connectivity** from your device/emulator

### HTTP 401 Unauthorized

- Verify your Basic Auth token is correct
- Check token format: should be base64 encoded `username:password`
- Contact Last9 support to verify credentials

### HTTP 400 Bad Request

- If using beacon endpoint: Switch to standard endpoint (`useStandardEndpoint = true`)
- Verify JSON payload format (SDK handles this automatically)

### Spans not appearing

1. Check that `Application` class is registered in `AndroidManifest.xml`
2. Verify SDK initialization happens in `Application.onCreate()`
3. For custom spans, ensure you call `.end()` on all spans
4. Check Logcat for export errors with `debugMode = true`

### OkHttp instrumentation not working

Ensure you've added the interceptor to your OkHttpClient:
```kotlin
val client = OkHttpClient.Builder()
    .addInterceptor(Last9.createOkHttpInterceptor())
    .build()
```

## Example Application

See [`example/`](example/) directory for a complete working example showing:
- SDK initialization in Application class
- Custom span creation for user actions
- OkHttp instrumentation for network requests
- Error tracking with exception recording

Run the example:
```bash
./gradlew :example:installDebug
```

## Platform Support

- **Minimum SDK**: Android API 21 (Lollipop 5.0)
- **Target SDK**: Android API 34
- **Kotlin**: 1.9+
- **Java**: Compatible with Java apps (Kotlin SDK with Java interop)

## Support

For issues or questions:
1. Enable `debugMode = true` and collect logs
2. Review this README and example app
3. Check [`examples/DISHTV_CONFIGURATION.md`](examples/DISHTV_CONFIGURATION.md) for advanced patterns
4. Contact Last9 support with logs and configuration details

## License

[Your License Here]
