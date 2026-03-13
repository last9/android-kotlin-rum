import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Load local.properties
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

android {
    namespace = "io.last9.android.example"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.last9.android.example"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        // Set LAST9_TOKEN in local.properties: last9.token=YOUR_TOKEN
        val last9Token = localProperties.getProperty("last9.token") ?: ""
        buildConfigField("String", "LAST9_TOKEN", "\"$last9Token\"")
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
}

dependencies {
    implementation(project(":rum-sdk"))
    implementation(libs.appcompat)
    implementation(libs.activity.ktx)
    // Example app provides its own OkHttp instance
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
}
