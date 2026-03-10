plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

android {
    namespace = "io.last9.android.rum"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        buildConfigField("String", "SDK_VERSION", "\"0.1.0\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    // Bundles crash, ANR, activity/fragment lifecycle, sessions, startup, slow-rendering.
    // Uses `api` so consumers transitively get the OTel API for manual span creation.
    api(libs.opentelemetry.android.agent)
    api(libs.opentelemetry.api)

    // OkHttp is compileOnly — SDK consumers supply their own version.
    // We hand-roll the OkHttp interceptor (OkHttpInstrumentation.kt) using the
    // core OTel API, avoiding a dependency on okhttp3-library which would ship
    // a duplicate implementation alongside ours.
    compileOnly(libs.okhttp)

    // OTLP/HTTP exporter
    implementation(libs.opentelemetry.exporter.otlp)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.runner)
    testImplementation(libs.okhttp)
    // InMemorySpanExporter + TestSpanData for OTel-based unit tests
    testImplementation(libs.opentelemetry.sdk.testing)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "io.last9.android"
            artifactId = "rum-sdk"
            version = "0.1.0"

            afterEvaluate {
                from(components["release"])
            }

            pom {
                name.set("Last9 Android RUM SDK")
                description.set("Real User Monitoring SDK for Android, built on OpenTelemetry")
                url.set("https://github.com/last9/android-rum")
                licenses {
                    license {
                        name.set("Apache-2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }
            }
        }
    }
}
