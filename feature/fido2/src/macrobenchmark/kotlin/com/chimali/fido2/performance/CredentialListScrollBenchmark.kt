package com.chimali.fido2.performance

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CredentialListScrollBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollCredentialList() {
        benchmarkRule.measureRepeated(
            packageName = "com.chimali.app", // Replace with actual app package
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.DEFAULT,
            startupMode = StartupMode.COLD,
            iterations = 5,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                
                // Navigate to Passkeys screen
                // Example navigation:
                val passkeysButton = device.findObject(By.text("Passkeys"))
                if (passkeysButton != null) {
                    passkeysButton.click()
                }
            }
        ) {
            val list = device.wait(Until.findObject(By.scrollable(true)), 5000)
            if (list != null) {
                // Scroll down
                list.setGestureMargin(device.displayWidth / 5)
                list.scroll(Direction.DOWN, 2f)
                
                // Scroll up
                list.scroll(Direction.UP, 2f)
            }
        }
    }
}
