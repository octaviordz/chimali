package com.chimali.fido2.presentation.navigation

import com.chimali.core.common.result.DomainError
import com.chimali.core.common.result.Outcome
import com.chimali.fido2.domain.model.AssertionObject
import com.chimali.fido2.domain.model.MakeCredentialResult
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.coroutines.CompletableDeferred

/** Pending-request retention and successor isolation. @see NFR-SEC-041 and TR-AUTH-031 */
class Fido2UiEventBusTest {
    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `registration cancellation clears cached request before any presenter exists`() {
        val bus = Fido2UiEventBus()
        val response = CompletableDeferred<Outcome<MakeCredentialResult, DomainError>>()
        val event = Fido2UiEvent.RegistrationRequested(mockk(), response)
        bus.dispatch(event)
        assertSame(event, bus.currentRegistrationRequest)
        response.cancel()
        assertNull(bus.currentRegistrationRequest)
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `authentication completion clears only that cached request`() {
        val bus = Fido2UiEventBus()
        val registration = Fido2UiEvent.RegistrationRequested(mockk(), CompletableDeferred())
        val response = CompletableDeferred<Outcome<AssertionObject, DomainError>>()
        bus.dispatch(registration)
        bus.dispatch(Fido2UiEvent.AuthenticationRequested(mockk(), response))
        response.complete(Outcome.Error(DomainError.OperationDenied("synthetic denial")))
        assertNull(bus.currentAuthenticationRequest)
        assertSame(registration, bus.currentRegistrationRequest)
        registration.deferred.cancel()
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `obsolete request completion cannot clear its successor`() {
        val bus = Fido2UiEventBus()
        val first = Fido2UiEvent.RegistrationRequested(mockk(), CompletableDeferred())
        val second = Fido2UiEvent.RegistrationRequested(mockk(), CompletableDeferred())
        bus.dispatch(first)
        bus.dispatch(second)
        first.deferred.cancel()
        assertSame(second, bus.currentRegistrationRequest)
        second.deferred.cancel()
        assertNull(bus.currentRegistrationRequest)
    }

    /** @see NFR-SEC-041 and TR-AUTH-031 */
    @Test
    fun `already expired request cannot enter the pending cache`() {
        val bus = Fido2UiEventBus()
        val expired = Fido2UiEvent.RegistrationRequested(mockk(), CompletableDeferred())
        expired.deferred.cancel()
        bus.dispatch(expired)
        assertNull(bus.currentRegistrationRequest)
    }
}
