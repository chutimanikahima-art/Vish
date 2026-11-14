plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.vish.keyboard"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.vish.keyboard"
        minSdk = 23
        targetSdk = 35
        versionCode = 6
        versionName = "2.3"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
