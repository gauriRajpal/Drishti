plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.drishti"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.drishti"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation ("com.google.mlkit:object-detection:16.2.1")  // ML Kit for Object Detection
    implementation ("androidx.camera:camera-core:1.3.0")        // CameraX Core
    implementation ("androidx.camera:camera-camera2:1.3.0")    // CameraX Camera2
    implementation ("androidx.camera:camera-lifecycle:1.3.0")   // Lifecycle for CameraX
    implementation ("androidx.camera:camera-view:1.3.0")     // CameraX Preview View
    implementation ("com.google.android.material:material:1.8.0") // Material UI
    implementation ("androidx.lifecycle:lifecycle-runtime-ktx:2.6.0") // Lifecycle support
    implementation ("androidx.activity:activity-ktx:1.6.1") // Activity KTX
    implementation ("com.google.mlkit:object-detection-custom:16.3.0")// Custom model (optional)
    implementation ("com.google.android.gms:play-services-vision:20.1.3") // Google Vision (fallback)
    implementation ("com.google.android.material:material:1.9.0") // ✅ Material Components


}