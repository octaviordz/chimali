plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.chimali.core.data"
    compileSdk = 35
    defaultConfig { minSdk = 28 }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:security"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
