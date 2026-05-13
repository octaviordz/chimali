plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.ksp)
}

kotlin {
    // Android target
    android {
        namespace = "com.chimali.core.security"
        compileSdk = 35
        minSdk = 28

        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
        withHostTest {}
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
            // and legacy key wrapping requirements (Constitution §I.2)
            // (no KMP-native AES-SIV alternative available in Signum 3.20.0)
            implementation(libs.bouncycastle.provider)
            implementation(libs.androidx.security.crypto)
        }

        val androidHostTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.junit.jupiter)
                implementation(libs.junit.jupiter.api)
                implementation(libs.junit.jupiter.params)
                implementation(libs.junit.platform.launcher)
                implementation(libs.mockk)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.koin.test)
            }
        }
    }
}

// KSP: Target-specific Koin Annotations processor (T189)
dependencies {
    add("kspAndroid", libs.koin.ksp.compiler)
    add("kspIosArm64", libs.koin.ksp.compiler)
    add("kspIosSimulatorArm64", libs.koin.ksp.compiler)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
