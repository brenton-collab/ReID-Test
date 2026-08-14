plugins {
    id("com.android.application")
}

val releaseKeystorePath = System.getenv("RELAY_KEYSTORE_PATH")
val releaseKeystorePassword = System.getenv("RELAY_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("RELAY_KEY_ALIAS")
val releaseKeyPassword = System.getenv("RELAY_KEY_PASSWORD")
val hasReleaseSigning = !releaseKeystorePath.isNullOrBlank() && !releaseKeystorePassword.isNullOrBlank() && !releaseKeyAlias.isNullOrBlank() && !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "ca.brentzinck.fintracid"
    compileSdk = 35

    defaultConfig {
        applicationId = "ca.brentzinck.relaycapture"
        minSdk = 26
        targetSdk = 35
        versionCode = 36
        versionName = "1.4.0"
    }

    if (hasReleaseSigning) {
        signingConfigs {
            create("relayRelease") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("relayRelease")
        }
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
