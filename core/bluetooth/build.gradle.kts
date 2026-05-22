plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.chimali.core.bluetooth"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()
    defaultConfig {
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.androidx.core.ktx)

    // Koin (replaces Hilt)
    implementation(libs.koin.core)
    implementation(libs.koin.android)
}
