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
        namespace = "app.chimali.core.datastore"
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
            api(projects.core.datastoreProto)

            implementation(libs.androidx.datastore)
            implementation(libs.androidx.datastore.core.okio)
            implementation(libs.koin.annotations)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.core)

            implementation(projects.core.model)
        }
    }
}
