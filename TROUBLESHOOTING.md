# Troubleshooting Guide

This guide helps you diagnose and fix common issues with the Last9 Android RUM SDK.

## Table of Contents

- [Export Timeout Errors](#export-timeout-errors)
- [No Data Appearing in Last9](#no-data-appearing-in-last9)
- [Debug Logging](#debug-logging)
- [Network Issues](#network-issues)

---

## Export Timeout Errors

### Symptoms

You see errors in Logcat like:

```
Last9SpanExporter: export: io.opentelemetry.exporter.internal.FailedExportException$HttpExportException: java.io.InterruptedIOException: timeout
Last9SpanExporter: Failed to export X span(s)
```

### Root Causes

1. **Slow network connection** — The OTLP endpoint isn't responding within the timeout period
2. **Wrong endpoint URL** — The SDK is trying to connect to an incorrect or non-existent endpoint
3. **Network blocked** — Corporate firewall, VPN, or Android network security is blocking requests
4. **OTLP endpoint is down or slow** — Backend service is overloaded or experiencing issues

### Solutions

#### 1. Increase the export timeout

Add `exportTimeoutSeconds` to your configuration:

```kotlin
Last9.init(this) {
    // ... other config ...
    exportTimeoutSeconds = 30  // Increase from default 10s to 30s
}
```

**Recommended values:**
- WiFi: 10-15 seconds (default)
- Mobile data (4G/5G): 20-30 seconds
- Mobile data (3G or slower): 30-60 seconds

#### 2. Verify your endpoint URL

Enable debug logging to see the exact endpoint being used:

```kotlin
Last9.init(this) {
    // ... other config ...
    debugMode = true
}
```

Check Logcat for:

```
ExporterFactory: Creating OTLP exporter:
ExporterFactory:   Endpoint: https://otlp.example.com/v1/traces
ExporterFactory:   Timeout: 10s
```

**Common endpoint mistakes:**
- ❌ `https://otlp.example.com/v1/traces` — Don't include the path
- ✅ `https://otlp.example.com` — Correct (SDK adds `/v1/traces` automatically)

#### 3. Test network connectivity

Use `curl` or a REST client to test if the endpoint is reachable:

```bash
# Test from your computer (not the device)
curl -v https://otlp.example.com/v1/traces

# Expected response: HTTP 200, 400, or 401 (not timeout or connection refused)
```

#### 4. Check Android network security configuration

Android may block cleartext HTTP or untrusted HTTPS certificates. Ensure:

1. Your endpoint uses **HTTPS** (not HTTP)
2. The certificate is valid and trusted
3. If using a self-signed certificate, add a network security config:

```xml
<!-- res/xml/network_security_config.xml -->
<network-security-config>
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">otlp.example.com</domain>
        <trust-anchors>
            <certificates src="system" />
            <!-- Add your self-signed cert if needed -->
        </trust-anchors>
    </domain-config>
</network-security-config>
```

```xml
<!-- AndroidManifest.xml -->
<application
    android:networkSecurityConfig="@xml/network_security_config"
    ...>
```

#### 5. Use a proxy endpoint

If the timeout persists, route telemetry through your own backend proxy:

```kotlin
Last9.init(this) {
    serviceName = "my-android-app"
    token = "your-app-session-token"  // Your auth, not Last9 creds
    baseUrl = "https://telemetry.yourcompany.com"  // Your proxy
    useStandardEndpoint = true
    useBasicAuth = false  // Proxy handles Last9 auth
    exportTimeoutSeconds = 20  // Adjust as needed
}
```

Your proxy can:
- Add retry logic
- Buffer requests during network outages
- Inject Last9 credentials server-side
- Monitor export success rates

---

## No Data Appearing in Last9

### Symptoms

- SDK initializes successfully
- No errors in Logcat
- But traces don't appear in Last9 dashboard

### Diagnostic Steps

#### 1. Enable debug logging

```kotlin
Last9.init(this) {
    // ... other config ...
    debugMode = true
}
```

Look for these logs in Logcat:

```
ExporterFactory: Creating OTLP exporter: ...
Last9SpanExporter: Exporting 3 span(s)...
Last9SpanExporter: → span: name=AppStart traceId=...
Last9SpanExporter: Successfully exported 3 span(s)
```

#### 2. Check authentication

Verify your token and endpoint are correct:

```kotlin
Last9.init(this) {
    token = "your-actual-token"  // NOT "YOUR-TOKEN-HERE"
    baseUrl = "https://YOUR_OTLP_ENDPOINT"  // Your actual OTLP endpoint
    useStandardEndpoint = true
    useBasicAuth = true
    debugMode = true
}
```

If you see `HTTP 401 Unauthorized` errors, your token is incorrect.

#### 3. Verify spans are being created

Trigger some activity:
- Open/close screens (creates lifecycle spans)
- Make HTTP requests (if OkHttp instrumentation is enabled)
- Create custom spans

Check Logcat for span creation:

```
Last9SpanExporter: Exporting 5 span(s)...
Last9SpanExporter: → span: name=Created traceId=...
Last9SpanExporter: → span: name=POST /api/users traceId=...
```

#### 4. Check for export failures

Look for errors:

```
Last9SpanExporter: Failed to export X span(s)
```

If you see failures, see [Export Timeout Errors](#export-timeout-errors) above.

---

## Debug Logging

Enable comprehensive logging to diagnose issues:

```kotlin
Last9.init(this) {
    // ... required config ...
    debugMode = true
}
```

### What gets logged

**ExporterFactory logs:**
- OTLP endpoint URL
- Timeout settings
- Authentication method
- Headers being sent

**Last9SpanExporter logs:**
- Number of spans being exported
- Span names, trace IDs, span IDs
- Export success/failure status

**OpenTelemetry logs** (from underlying SDK):
- HTTP request/response details
- Network errors
- Timeout exceptions

### Filter Logcat

```bash
# See only Last9 logs
adb logcat Last9*:D *:S

# See Last9 + OpenTelemetry logs
adb logcat Last9*:D OkHttp:D *:S

# Save logs to file
adb logcat -d > logcat.txt
```

---

## Network Issues

### Check network permissions

Ensure your `AndroidManifest.xml` has:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

### Test on different networks

Try exporting on:
- WiFi
- Mobile data (4G/5G)
- VPN

If exports work on one network but not another, there may be:
- Firewall rules blocking the endpoint
- DNS resolution issues
- Proxy configuration required

### Verify DNS resolution

```bash
# Test from your computer
nslookup otlp.example.com

# Expected: Returns an IP address
# If it fails: DNS issue or wrong domain
```

### Check Android network logs

Enable `StrictMode` to detect network issues:

```kotlin
if (BuildConfig.DEBUG) {
    StrictMode.setThreadPolicy(
        StrictMode.ThreadPolicy.Builder()
            .detectNetwork()
            .penaltyLog()
            .build()
    )
}
```

---

## Common Configuration Mistakes

### ❌ Wrong: Including path in baseUrl

```kotlin
baseUrl = "https://otlp.example.com/v1/traces"  // DON'T DO THIS
```

✅ **Correct:**

```kotlin
baseUrl = "https://otlp.example.com"  // SDK adds /v1/traces automatically
useStandardEndpoint = true
```

### ❌ Wrong: Using HTTP instead of HTTPS

```kotlin
baseUrl = "http://otlp.example.com"  // Android blocks cleartext traffic by default
```

✅ **Correct:**

```kotlin
baseUrl = "https://otlp.example.com"  // Always use HTTPS
```

### ❌ Wrong: Beacon endpoint without required headers

```kotlin
useStandardEndpoint = false  // Beacon mode (not recommended for Android)
```

✅ **Correct:**

```kotlin
useStandardEndpoint = true  // Always use standard OTLP endpoint for mobile apps
useBasicAuth = true
```

---

## Still Having Issues?

1. **Collect debug logs:**
   ```bash
   adb logcat -d > last9-debug.txt
   ```

2. **Check OpenTelemetry version:**
   ```bash
   ./gradlew :rum-sdk:dependencies | grep opentelemetry
   ```

3. **Test with a minimal config:**
   ```kotlin
   Last9.init(this) {
       token = "your-token"
       serviceName = "test-app"
       baseUrl = "https://YOUR_OTLP_ENDPOINT"
       useStandardEndpoint = true
       useBasicAuth = true
       debugMode = true
       exportTimeoutSeconds = 30  // Increase timeout
   }
   ```

4. **Open an issue** at https://github.com/last9/android-kotlin-rum/issues with:
   - Debug logs
   - SDK version
   - Android version and device model
   - Network type (WiFi/mobile)
   - Complete configuration (with token redacted)
