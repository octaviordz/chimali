plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}


kotlin {
    // Android target
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    // iOS targets (placeholder — no functional code, per NFR-ARCH-050 / T192)
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.koin.core)
            // Koin Annotations for @Single, @Factory, @Module (compile-time DI)
            implementation(libs.koin.annotations)
            // Signum — KMP-native crypto (primary engine per Constitution §I)
            implementation(libs.signum.indispensable)
        }

        androidMain.dependencies {
            implementation(libs.koin.android)
            implementation(libs.androidx.core.ktx)
            // BouncyCastle — retained in androidMain ONLY for AES-SIV
            // (no KMP-native AES-SIV alternative available in Signum 3.20.0)
            implementation(libs.bouncycastle.provider)
        }

        commonTest.dependencies {
            // kotlin.test: multiplatform assertions — runs on all targets (Android, iOS, JVM)
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.mockk)
        }
    }
}

android {
    namespace = "com.chimali.core.security"
    compileSdk = 35

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

// KSP: Target-specific processor wiring for Koin Annotations (T189)
// In KMP projects, ksp() is ambiguous — use target-specific configurations.
dependencies {
    add("kspAndroid", libs.koin.ksp.compiler)
    // iOS KSP wiring — placeholder, generates stubs until actual implementations exist
    add("kspIosArm64", libs.koin.ksp.compiler)
    add("kspIosSimulatorArm64", libs.koin.ksp.compiler)
}
