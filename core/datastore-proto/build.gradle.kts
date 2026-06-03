plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.androidLint)
    alias(libs.plugins.squareup.wire)
}

kotlin {
    // Target declarations - add or remove as needed below. These define
    // which platforms this KMP module supports.
    // See: https://kotlinlang.org/docs/multiplatform-discover-project.html#targets
    iosArm64()
    iosSimulatorArm64()
    jvm()
    android {
        namespace = "app.chimali.core.datastore.proto"
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
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.androidx.datastore.core.okio)
            implementation(libs.squareup.wire.runtime)
        }
    }
}

wire {
    // 1. Explicitly point to your proto directory
    sourcePath {
        srcDir("src/commonMain/proto")
    }

    // 2. Configure the Kotlin target to output into KMP common code
    kotlin {
        // Optional: If you need Java/Android Parcelable support, uncomment below
        // android = true
    }
}
