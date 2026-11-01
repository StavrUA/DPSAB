plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ua.stavr.dpsab"
    compileSdk = 35

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "ua.stavr.dpsab"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}
