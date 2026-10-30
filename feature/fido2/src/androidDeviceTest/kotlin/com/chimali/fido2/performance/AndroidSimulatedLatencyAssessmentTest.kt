package com.chimali.fido2.performance

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.platform.io.PlatformTestStorageRegistry
import com.chimali.core.common.annotation.Req
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/** Executes the same synthetic workflow on Android and emits one isolated device report. */
@RunWith(AndroidJUnit4::class)
@Suppress("MagicNumber")
class AndroidSimulatedLatencyAssessmentTest {
    @Test
    @Req("TR-PERF-010")
    fun runAndroidSimulatedAssessmentAndPersistSyntheticEvidence() =
        runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val arguments = InstrumentationRegistry.getArguments()
            val fingerprint = arguments.getString("sourceFingerprint")
            val runId = arguments.getString("runId") ?: UUID.randomUUID().toString()
            val metadata =
                instrumentation.targetContext.packageManager.getPackageInfo(
                    instrumentation.targetContext.packageName,
                    0,
                )
            val testPackage = instrumentation.context.packageName
            val testApk = File(instrumentation.context.applicationInfo.sourceDir)
            val testApkSha256 = sha256(testApk.readBytes())
            val fixture = SimulatedAssessmentFixture()
            val attempts = mutableListOf<CeremonyAttempt>()
            val reportFile = File(instrumentation.context.filesDir, "fido2-latency-$runId.json")
            val clockProbe = measureClockProbeOverhead()
            try {
                repeat(WARMUPS + REPETITIONS) { index ->
                    val kind = if (index < WARMUPS) AttemptKind.WARMUP else AttemptKind.MEASURED
                    val repetition = if (kind == AttemptKind.WARMUP) index + 1 else index - WARMUPS + 1
                    val label = "${kind.name.lowercase()}-$repetition"
                    attempts +=
                        fixture.registration(
                            runId = runId,
                            attemptId = "registration-$label-${UUID.randomUUID()}",
                            actionId = UUID.randomUUID().toString(),
                            kind = kind,
                            repetition = repetition,
                        )
                    persistCurrentReport(
                        reportFile,
                        attempts,
                        fingerprint,
                        testApkSha256,
                        testPackage,
                        metadata.versionName,
                        clockProbe,
                    )
                    attempts +=
                        fixture.authentication(
                            runId = runId,
                            attemptId = "authentication-$label-${UUID.randomUUID()}",
                            actionId = UUID.randomUUID().toString(),
                            kind = kind,
                            repetition = repetition,
                        )
                    persistCurrentReport(
                        reportFile,
                        attempts,
                        fingerprint,
                        testApkSha256,
                        testPackage,
                        metadata.versionName,
                        clockProbe,
                    )
                }
                val report = MeasurementReport.create(workload(fingerprint, testApkSha256), attempts)
                persist(
                    reportFile,
                    report,
                    testPackage,
                    testApkSha256,
                    metadata.versionName,
                    clockProbe,
                )
                assertEquals(ATTEMPT_COUNT, report.attempts.size)
                assertEquals(OPERATION_TYPES, report.workload.exercisedOperationTypes)
                assertEquals(
                    20,
                    report.attempts.count { it.attemptKind == AttemptKind.MEASURED && it.verifiedResponse },
                )
                if (fingerprint.isNullOrBlank()) {
                    val persisted = Json.parseToJsonElement(reportFile.readText()).jsonObject
                    assertEquals(
                        ProjectDisposition.INCOMPLETE.name,
                        persisted["disposition"]?.jsonPrimitive?.content,
                    )
                    assertFalse(persisted["provenance_valid"]?.jsonPrimitive?.content?.toBoolean() ?: true)
                    assertEquals("missing", report.workload.sourceRevision)
                } else {
                    assertEquals(fingerprint, report.workload.sourceRevision)
                }
                println("FIDO2_ANDROID_REPORT=${reportFile.name}")
                val disposition =
                    if (fingerprint.isNullOrBlank()) ProjectDisposition.INCOMPLETE else report.disposition
                println("FIDO2_ANDROID_DISPOSITION=$disposition")
                println("FIDO2_ANDROID_FINGERPRINT=${fingerprint ?: "missing"}")
                println("FIDO2_ANDROID_TEST_APK_SHA256=$testApkSha256")
            } finally {
                fixture.dispose()
            }
        }

    private fun persist(
        file: File,
        report: AssessmentReport,
        testPackage: String,
        testApkSha256: String,
        appVersion: String?,
        clockProbe: ClockProbeOverhead,
    ) {
        val provenanceValid = report.workload.sourceRevision != "missing"
        val disposition = if (provenanceValid) report.disposition.name else ProjectDisposition.INCOMPLETE.name
        val reportJson =
            buildJsonObject {
                put("schema", "060-android-v1")
                put("run_id", report.attempts.firstOrNull()?.runId ?: "")
                put("disposition", disposition)
                put("provenance_valid", provenanceValid)
                put("target_package", "com.chimali.fido2")
                put("target_version", appVersion ?: "unknown")
                put("test_package", testPackage)
                put("test_apk_sha256", testApkSha256)
                put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
                put("android_release", Build.VERSION.RELEASE)
                put("api_level", Build.VERSION.SDK_INT)
                put(
                    "clock_probe_overhead_ns",
                    buildJsonObject {
                        put("samples", clockProbe.sampleCount)
                        put("minimum", clockProbe.minimumNanos)
                        put("median", clockProbe.medianNanos)
                        put("maximum", clockProbe.maximumNanos)
                    },
                )
                put("assessment", Json.parseToJsonElement(MeasurementReportJson.encode(report)))
            }.let { Json.encodeToString(JsonElement.serializer(), it) + "\n" }
        file.writeText(reportJson)
        PlatformTestStorageRegistry
            .getInstance()
            .openOutputFile(file.name, false)
            .use { output -> output.write(reportJson.encodeToByteArray()) }
    }

    private fun persistCurrentReport(
        file: File,
        attempts: List<CeremonyAttempt>,
        fingerprint: String?,
        testApkSha256: String,
        testPackage: String,
        appVersion: String?,
        clockProbe: ClockProbeOverhead,
    ) = persist(
        file,
        MeasurementReport.create(workload(fingerprint, testApkSha256), attempts),
        testPackage,
        testApkSha256,
        appVersion,
        clockProbe,
    )

    private fun workload(
        fingerprint: String?,
        testApkSha256: String,
    ) = SimulatedWorkload(
        version = "060-android-v1",
        warmupCount = WARMUPS,
        repetitionCount = REPETITIONS,
        summary = "Android simulated fake-RP registration and authentication diagnostic",
        eligibleAfterWarmup = true,
        operationTypes = OPERATION_TYPES,
        exercisedOperationTypes = OPERATION_TYPES,
        host = "${Build.MANUFACTURER} ${Build.MODEL}",
        runtime = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}); test APK ${testApkSha256.take(16)}",
        sourceRevision = fingerprint ?: "missing",
        realComponents = setOf("production registration and assertion use cases", "CTAP GetInfo", "ResetAuthenticator"),
        simulatedComponents = setOf("fake relying party", "in-memory persistence", "immediate user verification"),
        clockDomain = MonotonicMeasurement.CLOCK_DOMAIN,
        limitations =
            listOf(
                "Instrumentation and fixture overhead are included in operation and ceremony spans.",
                "No browser, Bluetooth transport, physical authenticator, or biometric prompt is exercised.",
                "Installed test APK SHA-256: $testApkSha256; source fingerprint was supplied by the host collector.",
                "SystemClock detects deep sleep divergence above 5 ms; " +
                    "shorter sleep and awake scheduling are not excluded.",
            ),
    )

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun measureClockProbeOverhead(): ClockProbeOverhead {
        val samples =
            List(CLOCK_PROBE_SAMPLES) {
                val start = System.nanoTime()
                MonotonicMeasurement.begin()
                System.nanoTime() - start
            }.sorted()
        return ClockProbeOverhead(
            sampleCount = samples.size,
            minimumNanos = samples.first(),
            medianNanos = samples[samples.size / 2],
            maximumNanos = samples.last(),
        )
    }

    private data class ClockProbeOverhead(
        val sampleCount: Int,
        val minimumNanos: Long,
        val medianNanos: Long,
        val maximumNanos: Long,
    )

    private companion object {
        const val WARMUPS = 2
        const val REPETITIONS = 10
        const val ATTEMPT_COUNT = 24
        const val CLOCK_PROBE_SAMPLES = 1_000
        val OPERATION_TYPES = setOf("MakeCredential", "GetAssertion", "GetInfo", "ResetAuthenticator")
    }
}
