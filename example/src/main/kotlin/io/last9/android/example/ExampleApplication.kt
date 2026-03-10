package io.last9.android.example

import android.app.Application
import io.last9.android.rum.Last9

class ExampleApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // 3-line initialization — crash, ANR, sessions, startup, and OkHttp
        // instrumentation are all enabled by default.
        Last9.init(this) {
            token = BuildConfig.LAST9_TOKEN          // set in local.properties or CI secret
            serviceName = "watcho-android"
            baseUrl = "https://otlp-ext-aps1.last9.io/v1/otlp/organizations/dishtv"
            deploymentEnvironment = "production"
            serviceVersion = BuildConfig.VERSION_NAME
            debugMode = BuildConfig.DEBUG
        }
    }
}
