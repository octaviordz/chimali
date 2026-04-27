// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    // Kotlin Multiplatform — used by core:security, feature:fido2 (T187, T188)
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.jetbrains.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
}

subprojects {
    apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)
    apply(plugin = rootProject.libs.plugins.detekt.get().pluginId)

    dependencies {
        add("detektPlugins", rootProject.libs.detekt.compose.rules)
    }

    configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        config.setFrom(files("${rootProject.projectDir}/config/detekt/detekt.yml"))
        baseline = file("${projectDir}/detekt-baseline.xml")
        buildUponDefaultConfig = true
        allRules = false
        source.setFrom(files("src"))
    }

    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        reports {
            html.outputLocation.set(layout.buildDirectory.file("reports/detekt/${project.name}.html"))
            xml.outputLocation.set(layout.buildDirectory.file("reports/detekt/${project.name}.xml"))
        }
    }

    afterEvaluate {
        if (plugins.hasPlugin("com.android.application")) {
            configure<com.android.build.api.dsl.ApplicationExtension> {
                lint {
                    abortOnError = true
                    checkReleaseBuilds = true
                    textReport = false
                    htmlReport = true
                    xmlReport = true
                    htmlOutput = file("build/reports/lint/${project.name}.html")
                    xmlOutput = file("build/reports/lint/${project.name}.xml")
                    disable += arrayOf("TypographyFractions", "TypographyQuotes", "TypographyDashes", "TypographyEllipsis", "TypographyOther")
                    enable += arrayOf("RtlHardcoded", "RtlCompat", "RtlEnabled")
                    checkGeneratedSources = false
                    ignoreTestSources = true
                }
            }
        }
        if (plugins.hasPlugin("com.android.library")) {
            configure<com.android.build.api.dsl.LibraryExtension> {
                lint {
                    abortOnError = true
                    checkReleaseBuilds = true
                    textReport = false
                    htmlReport = true
                    xmlReport = true
                    htmlOutput = file("build/reports/lint/${project.name}.html")
                    xmlOutput = file("build/reports/lint/${project.name}.xml")
                    disable += arrayOf("TypographyFractions", "TypographyQuotes", "TypographyDashes", "TypographyEllipsis", "TypographyOther")
                    enable += arrayOf("RtlHardcoded", "RtlCompat", "RtlEnabled")
                    checkGeneratedSources = false
                    ignoreTestSources = true
                }
            }
        }
    }
}
