package com.chimali.fido2.data.eventsourcing

import android.database.SQLException as AndroidSQLException
import com.chimali.core.common.annotation.Req
import java.nio.charset.CharacterCodingException
import java.security.GeneralSecurityException
import java.sql.SQLException as JdbcSQLException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException

/** Converts expected persistence, serialization, and encryption failures to the repository Result. */
@Req("NFR-SEC-041", "TR-AUTH-031")
internal suspend fun <T> captureStorageResult(operation: suspend () -> T): Result<T> =
    try {
        Result.success(operation())
    } catch (failure: CancellationException) {
        throw failure
    } catch (failure: AndroidSQLException) {
        Result.failure(failure)
    } catch (failure: JdbcSQLException) {
        Result.failure(failure)
    } catch (failure: GeneralSecurityException) {
        Result.failure(failure)
    } catch (failure: SerializationException) {
        Result.failure(failure)
    } catch (failure: CharacterCodingException) {
        Result.failure(failure)
    } catch (failure: IllegalArgumentException) {
        Result.failure(failure)
    } catch (failure: IllegalStateException) {
        Result.failure(failure)
    } catch (failure: SecurityException) {
        Result.failure(failure)
    }
