plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.chimali.core.crdt"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.android.minSdk.get().toInt() }

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
