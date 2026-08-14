plugins {
    id("com.android.application")
}

android {
    namespace = "ca.brentzinck.fintracid"
    compileSdk = 35
    defaultConfig {
        applicationId = "ca.brentzinck.relaycapture"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "1.0.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.activity:activity:1.10.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.camera:camera-core:1.4.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("androidx.lifecycle:lifecycle-runtime:2.8.7")
}
