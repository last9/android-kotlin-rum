plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "io.last9.android.example"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.last9.android.example"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // Enable core library desugaring for minSdk < 26
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // Core library desugaring for minSdk < 26
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(project(":rum-sdk"))
    implementation(libs.appcompat)
    implementation(libs.activity.ktx)
    // Example app provides its own OkHttp instance
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
}
