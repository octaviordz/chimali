import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false

    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.androidLint) apply false
}

subprojects {
    apply(
        plugin =
            rootProject.libs.plugins.detekt
                .get()
                .pluginId,
    )
    apply(
        plugin =
            rootProject.libs.plugins.ktlint
                .get()
                .pluginId,
    )

    dependencies {
        add("detektPlugins", rootProject.libs.detekt.compose.rules)
    }

    configure<DetektExtension> {
        config.setFrom(files("${rootProject.projectDir}/config/detekt/detekt.yml"))
        baseline = layout.projectDirectory.file("detekt-baseline.xml").asFile
        buildUponDefaultConfig = true
        allRules = false
        source.setFrom(files("src"))
        autoCorrect = project.hasProperty("detekt.autoCorrect")
    }

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.3.1")
        ignoreFailures.set(false)
        android.set(project.path == ":androidApp")
        outputToConsole.set(true)
        coloredOutput.set(true)
        filter {
            exclude("**/generated/**")
            exclude("**/build/**")
        }
    }

    tasks.withType<Detekt>().configureEach {
        reports {
            html.outputLocation.set(layout.buildDirectory.file("reports/detekt/${project.name}.html"))
        }
    }
}
