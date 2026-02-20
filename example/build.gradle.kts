plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "io.last9.android.example"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.last9.android.example"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        // Set LAST9_TOKEN in local.properties: last9.token=YOUR_TOKEN
        val last9Token = project.findProperty("last9.token") as String? ?: ""
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
}
