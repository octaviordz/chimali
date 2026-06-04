plugins {
// Android & Kotlin Platform Plugins
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.androidLint)
    alias(libs.plugins.kotlinMultiplatform)
// Dependency Injection & Serialization Plugins
    alias(libs.plugins.koin.compiler)
}

kotlin {
    // Target declarations - add or remove as needed below. These define
    // which platforms this KMP module supports.
    // See: https://kotlinlang.org/docs/multiplatform-discover-project.html#targets
    iosArm64()
    iosSimulatorArm64()
    jvm()
    android {
        namespace = "app.chimali.core.domain"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
    }
    sourceSets {
        commonMain.dependencies {
// KotlinX & Core Libraries
            api(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.core)
// Koin Dependency Injection
            implementation(libs.koin.annotations)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.koin.core)
// Project Modules
            implementation(projects.core.data)
            implementation(projects.core.model)
        }
    }
}
