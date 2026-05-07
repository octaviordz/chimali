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
            // (no KMP-native AES-SIV alternative available in Signum 3.20.0)
            implementation(libs.bouncycastle.provider)
        }

        commonTest.dependencies {
            // kotlin.test: multiplatform assertions — runs on all targets (Android, iOS, JVM)
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }

        val androidHostTest by getting {
            dependencies {
                implementation(libs.mockk)
            }
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

tasks.withType<Test> {
    useJUnitPlatform()
}

val isMac = System.getProperty("os.name").lowercase().contains("mac")

// Disable native compilation on non-Mac hosts to support Windows development (T010)
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile>().configureEach {
    enabled = isMac
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink>().configureEach {
    enabled = isMac
}
