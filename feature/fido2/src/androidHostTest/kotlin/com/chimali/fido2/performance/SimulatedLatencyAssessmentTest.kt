package com.chimali.fido2.performance

import com.chimali.core.common.annotation.Req
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Runs the reproducible host assessment and writes its per-operation decisions. */
@Req("TR-PERF-010")
class SimulatedLatencyAssessmentTest {
    @Test
    fun runDeclaredAssessment() =
        runTest {
            val runId = UUID.randomUUID().toString()
            val warmups = 2
            val repetitions = 10
            val workload =
                SimulatedWorkload(
                    version = "060-host-v1",
                    warmupCount = warmups,
                    repetitionCount = repetitions,
                    summary =
                        listOf(
                            "Assess every measured internal FIDO operation occurrence",
                            "full RP interval is diagnostic",
                        ).joinToString("; "),
                    eligibleAfterWarmup = true,
                    operationTypes = setOf("MakeCredential", "GetAssertion", "GetInfo", "ResetAuthenticator"),
                    exercisedOperationTypes =
                        setOf("MakeCredential", "GetAssertion", "GetInfo", "ResetAuthenticator"),
                    host = "${System.getProperty("os.name")} ${System.getProperty("os.arch")}",
                    runtime = "${System.getProperty("java.vm.name")} ${System.getProperty("java.runtime.version")}",
                    sourceRevision = worktreeRevisionFingerprint(),
                    realComponents =
                        setOf(
                            "RegisterCredentialUseCase",
                            "GetAssertionUseCase",
                            "SelectCredentialUseCase",
                            "CborCodec",
                            "ClientDataHashService",
                            "JCA ECDSA signing and verification",
                            "CtapHidCommandDispatcher GetInfo, AuthenticatorInfoProvider and CTAP response encoding",
                            "Ctap2ResetAuthenticatorHandler and ResetAuthenticatorUseCase",
                        ),
                    simulatedComponents =
                        setOf(
                            "in-process synthetic relying party",
                            "immediate user-verification double",
                            "credential and relying-party storage",
                            "credential key-derivation adapter",
                            "registration/authentication CTAP handlers and HID/Bluetooth transport",
                        ),
                    limitations =
                        listOf(
                            "Registration and authentication call production use cases directly; their CTAP handlers " +
                                "and HID/Bluetooth transport are not traversed.",
                            "GetInfo probe exercises the production dispatcher and capability provider; response " +
                                "encoding has HID transport callbacks replaced; ResetAuthenticator uses isolated " +
                                "synthetic storage.",
                            "Storage and credential key derivation are synthetic adapters; no real " +
                                "user or credential data is used.",
                            "System.nanoTime suspend behavior is not used to claim physical-device timing; " +
                                "this host run does not establish device latency.",
                        ),
                )
            val fixture = SimulatedAssessmentFixture()
            val attempts = mutableListOf<CeremonyAttempt>()

            try {
                repeat(warmups + repetitions) { index ->
                    val attemptKind = if (index < warmups) AttemptKind.WARMUP else AttemptKind.MEASURED
                    val repetition = if (attemptKind == AttemptKind.WARMUP) index + 1 else index - warmups + 1
                    attempts +=
                        fixture.registration(
                            runId = runId,
                            attemptId = "registration-${attemptKind.name.lowercase()}-$repetition-${UUID.randomUUID()}",
                            actionId = UUID.randomUUID().toString(),
                            kind = attemptKind,
                            repetition = repetition,
                        )
                    attempts +=
                        fixture.authentication(
                            runId = runId,
                            attemptId = "authentication-${attemptKind.name.lowercase()}-$repetition",
                            actionId = UUID.randomUUID().toString(),
                            kind = attemptKind,
                            repetition = repetition,
                        )
                    attempts +=
                        fixture.getInfoProbe(
                            runId = runId,
                            attemptId = "get-info-${attemptKind.name.lowercase()}-$repetition-${UUID.randomUUID()}",
                            actionId = UUID.randomUUID().toString(),
                            kind = attemptKind,
                            repetition = repetition,
                        )
                    attempts +=
                        fixture.resetProbe(
                            runId = runId,
                            attemptId = "reset-${attemptKind.name.lowercase()}-$repetition-${UUID.randomUUID()}",
                            actionId = UUID.randomUUID().toString(),
                            kind = attemptKind,
                            repetition = repetition,
                        )
                }

                val report = MeasurementReport.create(workload, attempts)
                val encoded = MeasurementReportJson.encode(report)
                val parsed = Json.parseToJsonElement(encoded).jsonObject
                val output = Path.of("build", "reports", "fido2", "060", "simulated-latency-assessment.json")
                Files.createDirectories(output.parent)
                Files.writeString(output, encoded)

                assertEquals(48, report.attempts.size)
                assertEquals(40, report.attempts.count { it.attemptKind == AttemptKind.MEASURED })
                assertTrue(report.attempts.all { it.outcome == CeremonyOutcome.VERIFIED })
                assertEquals(10, report.operationSummaries.getValue("MakeCredential").occurrenceCount)
                assertEquals(10, report.operationSummaries.getValue("GetAssertion").occurrenceCount)
                assertEquals(10, report.operationSummaries.getValue("GetInfo").occurrenceCount)
                assertEquals(10, report.operationSummaries.getValue("ResetAuthenticator").occurrenceCount)
                assertTrue(
                    report.attempts
                        .filter { it.attemptKind == AttemptKind.WARMUP }
                        .flatMap { it.operationAssessments }
                        .all { !it.eligibleForDecision },
                )
                assertTrue(
                    report.attempts
                        .filter { it.attemptKind == AttemptKind.MEASURED }
                        .flatMap { it.operationAssessments }
                        .all { it.eligibleForDecision },
                )
                assertTrue(report.disposition != ProjectDisposition.INCOMPLETE)
                assertTrue(report.reasons.isEmpty(), "all inventoried internal operation types are covered")
                assertEquals(48, parsed.getValue("attempts").jsonArray.size)
                assertEquals(report.disposition.name, parsed.getValue("disposition").jsonPrimitive.content)

                println("FIDO2_LATENCY_REPORT=${output.toAbsolutePath()}")
                println("FIDO2_LATENCY_DISPOSITION=${report.disposition}")
            } finally {
                fixture.dispose()
            }
        }

    private fun worktreeRevisionFingerprint(): String {
        val workingDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        val root = Path.of(gitOutput(workingDirectory, "rev-parse", "--show-toplevel")).toAbsolutePath().normalize()
        val head = gitOutput(root, "rev-parse", "HEAD")
        val sources =
            listOf(
                root.resolve("feature/fido2/src/androidMain/kotlin/com/chimali/fido2/domain"),
                root.resolve("feature/fido2/src/androidMain/kotlin/com/chimali/fido2/data/crypto"),
                root.resolve(
                    "feature/fido2/src/androidMain/kotlin/com/chimali/fido2/util/performance/BaselineMeasurements.kt",
                ),
                root.resolve("feature/fido2/src/androidHostTest/kotlin/com/chimali/fido2/performance"),
                root.resolve("feature/fido2/build.gradle.kts"),
                root.resolve("gradle/libs.versions.toml"),
                root.resolve("specs/060-fido2-latency-measurement/spec.md"),
                root.resolve("specs/060-fido2-latency-measurement/contracts/measurement-contract.md"),
            )
        val files =
            sources
                .flatMap { path ->
                    if (Files.isDirectory(path)) {
                        Files.walk(path).use { stream -> stream.filter(Files::isRegularFile).sorted().toList() }
                    } else {
                        listOf(path).filter(Files::isRegularFile)
                    }
                }.distinct()
                .sorted()
        val digest = MessageDigest.getInstance("SHA-256")
        files.forEach { file ->
            digest.update(
                root
                    .relativize(file)
                    .toString()
                    .replace('\\', '/')
                    .encodeToByteArray(),
            )
            digest.update(0.toByte())
            digest.update(Files.readAllBytes(file))
            digest.update(0.toByte())
        }
        val fingerprint = digest.digest().joinToString("") { "%02x".format(it) }
        return "$head+worktree-source-sha256:$fingerprint"
    }

    private fun gitOutput(
        directory: Path,
        vararg arguments: String,
    ): String {
        val process = ProcessBuilder(listOf("git") + arguments).directory(directory.toFile()).start()
        val output = process.inputStream.bufferedReader().use { it.readText().trim() }
        check(process.waitFor() == 0 && output.isNotBlank()) { "git command failed" }
        return output
    }
}
