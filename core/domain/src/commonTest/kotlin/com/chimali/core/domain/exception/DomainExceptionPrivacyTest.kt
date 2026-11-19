package com.chimali.core.domain.exception

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame

class DomainExceptionPrivacyTest {
    @Test
    fun genericRepresentationsDoNotExposeValidationOrRepositoryDetails() {
        val marker = "SYNTHETIC_DOMAIN_ERROR_MARKER"
        val validation = DomainException.ValidationError(field = marker, message = marker)
        val cause = IllegalStateException(marker)
        val repository = DomainException.RepositoryError(message = marker, cause = cause)

        assertFalse(marker in validation.toString())
        assertEquals(marker, validation.field)
        assertEquals(marker, validation.message)

        assertFalse(marker in repository.toString())
        assertEquals(marker, repository.message)
        assertSame(cause, repository.cause)
    }
}
