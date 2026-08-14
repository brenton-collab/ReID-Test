plugins {
    id("com.android.application")
}

android {
    namespace = "ca.brentzinck.fintracid"
    compileSdk = 35

    defaultConfig {
        applicationId = "ca.brentzinck.fintracid"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.documentfile:documentfile:1.0.1")
}
