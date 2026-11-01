plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.novelflow.reader"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.novelflow.reader"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.1.1"
    }
}
dependencies { implementation("androidx.core:core-ktx:1.15.0") }
