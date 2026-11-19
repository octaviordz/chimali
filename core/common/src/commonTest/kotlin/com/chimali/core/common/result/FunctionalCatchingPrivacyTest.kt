package com.chimali.core.common.result

import com.chimali.core.common.annotation.Req
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest

class FunctionalCatchingPrivacyTest {
    @Test
    @Req("NFR-SEC-030", "FR-008", "FR-009", "FR-010")
    fun failureUsesFixedMessageWithoutRetainingThrowable() =
        runTest {
            val secretMarker = "synthetic-sensitive-throwable-marker"

            val outcome =
                functionalCatching<String> {
                    error(secretMarker)
                }

            val failure = assertIs<Outcome.Error<DomainError.UnknownError>>(outcome)
            assertEquals("An unexpected error occurred", failure.error.message)
            assertNull(failure.error.cause)
            assertEquals("UnknownError(message=An unexpected error occurred, cause=null)", failure.error.toString())
        }

    @Test
    @Req("NFR-SEC-030", "FR-008", "FR-009", "FR-010")
    fun cancellationIsRethrownWithoutWrapping() =
        runTest {
            val cancellation = CancellationException("synthetic-cancellation")

            val thrown =
                kotlin
                    .runCatching {
                        functionalCatching<String> { throw cancellation }
                    }.exceptionOrNull()

            assertSame(cancellation, thrown)
        }

    @Test
    @Req("NFR-SEC-030", "FR-008", "FR-009", "FR-010")
    fun fatalErrorIsRethrownWithoutWrapping() =
        runTest {
            val fatal = AssertionError("synthetic-fatal-marker")

            val thrown =
                kotlin
                    .runCatching {
                        functionalCatching<String> { throw fatal }
                    }.exceptionOrNull()

            assertSame(fatal, thrown)
        }

    @Test
    @Req("NFR-SEC-030", "FR-008", "FR-009", "FR-010")
    fun runCatchingOutcomeRethrowsFatalErrorWithoutMappingIt() {
        val fatal = AssertionError("synthetic-fatal-marker")
        var mapperCalled = false

        val thrown =
            kotlin
                .runCatching {
                    runCatchingOutcome(
                        onError = {
                            mapperCalled = true
                            DomainError.UnknownError("fixed")
                        },
                    ) { throw fatal }
                }.exceptionOrNull()

        assertSame(fatal, thrown)
        assertEquals(false, mapperCalled)
    }
}
