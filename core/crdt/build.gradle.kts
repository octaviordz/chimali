plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.chimali.core.crdt"
    compileSdk = 35
    defaultConfig { minSdk = 28 }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.jna) // Required for uniffi
}

// TODO: Define rust compilation and uniffi binding generation tasks here later as needed for Phase 2 T006.
