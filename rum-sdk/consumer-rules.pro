# Keep all Last9 SDK public API classes
-keep class io.last9.android.rum.Last9 { *; }
-keep class io.last9.android.rum.Last9Options { *; }
-keep class io.last9.android.rum.Last9RumInstance { *; }
-keep class io.last9.android.rum.instrumentation.OkHttpInstrumentation { *; }

# Keep OTel Android agent classes (loaded via ServiceLoader reflection)
-keep class io.opentelemetry.android.** { *; }
-keep interface io.opentelemetry.android.** { *; }

# Keep OTel Java API (used at runtime via reflection in ServiceLoader)
-keep class io.opentelemetry.api.** { *; }
-keep class io.opentelemetry.sdk.** { *; }

-dontwarn io.opentelemetry.**
-dontwarn io.opentelemetry.android.**
