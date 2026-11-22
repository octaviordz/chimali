package com.chimali.core.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import android.os.SystemClock
import com.chimali.core.common.annotation.Req
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single

private const val OWNERSHIP_TOKEN_KEY = "com.chimali.clipboard.OWNERSHIP_TOKEN"
private const val SENSITIVE_KEY = ClipDescription.EXTRA_IS_SENSITIVE
private const val SAFE_CLIP_LABEL = "Password"

private sealed interface OwnershipTokenRead {
    data class Readable(
        val token: String?,
    ) : OwnershipTokenRead

    data object Unavailable : OwnershipTokenRead
}

@Single(binds = [ClipboardManagerService::class])
class AndroidClipboardManagerService(
    private val context: Context,
) : ClipboardManagerService {
    private val manager by lazy { context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager }

    // Keep the production source private and random. Isolated instrumentation may replace this
    // field reflectively before a synthetic copy; no production setter or IPC surface is exposed.
    private var ownershipTokenSource: () -> String = { UUID.randomUUID().toString() }
    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private val owner = Mutex()

    // The synchronous ViewModel disposal handoff can race a terminal callback. Serialize
    // only receipt disposition here; clipboard operations remain serialized by owner.
    private val receiptLock = Any()
    private var nextGeneration = 0L
    private val jobs = mutableMapOf<Long, Job>()
    private val tokens = mutableMapOf<Long, String>()
    private val custodianOwned = ConcurrentHashMap.newKeySet<Long>()
    private val _cleanupStatuses = MutableStateFlow<Map<Long, ClipboardCleanupStatus>>(emptyMap())
    override val cleanupStatuses: StateFlow<Map<Long, ClipboardCleanupStatus>> = _cleanupStatuses.asStateFlow()

    @Req("NFR-SEC-050", "TR-CLIP-010")
    @Suppress("UNUSED_PARAMETER") // Caller labels may contain identifying data; the platform label stays fixed.
    override suspend fun copySensitiveData(
        label: String,
        text: CharArray,
    ): Result<ClipboardCopyReceipt> =
        owner.withLock {
            val clipboard = manager ?: return@withLock Result.failure(ClipboardError.ClipboardUnavailable())
            val generation = ++nextGeneration
            val admitted =
                synchronized(receiptLock) {
                    val unconfirmedPending =
                        _cleanupStatuses.value.keys.any { current ->
                            _cleanupStatuses.value[current]?.outcome == CleanupOutcome.Pending &&
                                lastPublication[current] == PublicationStatus.Unconfirmed
                        }
                    if (_cleanupStatuses.value.size >= 2 || unconfirmedPending) {
                        false
                    } else {
                        _cleanupStatuses.value =
                            _cleanupStatuses.value +
                            (
                                generation to
                                    ClipboardCleanupStatus(
                                        generation,
                                        CleanupOutcome.Pending,
                                        CleanupOrigin.Automatic,
                                    )
                            )
                        true
                    }
                }
            if (!admitted) return@withLock Result.failure(ClipboardError.CopyAdmissionRejected())
            var publicationReturned = false
            try {
                val token = ownershipTokenSource()
                val clip =
                    ClipData.newPlainText(SAFE_CLIP_LABEL, String(text)).apply {
                        description.extras =
                            PersistableBundle().apply {
                                putBoolean(SENSITIVE_KEY, true)
                                putString(OWNERSHIP_TOKEN_KEY, token)
                            }
                    }
                clipboard.setPrimaryClip(clip)
                publicationReturned = true
                val copiedAt = SystemClock.elapsedRealtime()
                val dueAt = copiedAt + CLIPBOARD_CLEANUP_DELAY_MS
                val confirmed =
                    try {
                        (readOwnershipToken(clipboard) as? OwnershipTokenRead.Readable)?.token == token
                    } catch (cancellation: CancellationException) {
                        tokens[generation] = token
                        lastPublication[generation] = PublicationStatus.Unconfirmed
                        jobs[generation] =
                            scope.launch {
                                delay((dueAt - SystemClock.elapsedRealtime()).coerceAtLeast(0))
                                attemptAutomatic(generation, token)
                            }
                        jobs[generation]?.invokeOnCompletion { cause ->
                            if (cause is CancellationException) recordInterruptedCleanup(generation, token)
                        }
                        transferToCustodian(generation)
                        throw cancellation
                    }
                val publication = if (confirmed) PublicationStatus.MetadataConfirmed else PublicationStatus.Unconfirmed
                if (confirmed) {
                    tokens.keys.toList().forEach { previous ->
                        tokens.remove(previous)
                        jobs.remove(previous)?.cancel()
                        lastPublication.remove(previous)
                        finishReceipt(previous, CleanupOutcome.Superseded, CleanupOrigin.Automatic)
                    }
                }
                tokens[generation] = token
                lastPublication[generation] = publication
                jobs[generation] =
                    scope.launch {
                        delay((dueAt - SystemClock.elapsedRealtime()).coerceAtLeast(0))
                        attemptAutomatic(generation, token)
                    }
                jobs[generation]?.invokeOnCompletion { cause ->
                    if (cause is CancellationException) recordInterruptedCleanup(generation, token)
                }
                Result.success(ClipboardCopyReceipt(generation, dueAt, publication))
            } catch (cancellation: CancellationException) {
                if (!publicationReturned) {
                    synchronized(receiptLock) {
                        _cleanupStatuses.value =
                            _cleanupStatuses.value - generation
                    }
                }
                throw cancellation
            } catch (_: SecurityException) {
                synchronized(receiptLock) { _cleanupStatuses.value = _cleanupStatuses.value - generation }
                Result.failure(ClipboardError.CopyFailed("clipboard publication rejected"))
            } catch (_: IllegalStateException) {
                synchronized(receiptLock) { _cleanupStatuses.value = _cleanupStatuses.value - generation }
                Result.failure(ClipboardError.CopyFailed("clipboard publication unavailable"))
            } catch (_: Exception) {
                synchronized(receiptLock) { _cleanupStatuses.value = _cleanupStatuses.value - generation }
                Result.failure(ClipboardError.CopyFailed("clipboard publication unavailable"))
            }
        }

    private val lastPublication = mutableMapOf<Long, PublicationStatus>()

    private suspend fun attemptAutomatic(
        generation: Long,
        token: String,
    ) = owner.withLock {
        if (tokens[generation] != token) return@withLock
        val clipboard = manager
        val callbackElapsedMs = SystemClock.elapsedRealtime()
        val ownership = readOwnershipToken(clipboard)
        val ownershipCheckElapsedMs = SystemClock.elapsedRealtime()
        var clearAttemptElapsedMs: Long? = null
        val outcome =
            when (ownership) {
                OwnershipTokenRead.Unavailable -> CleanupOutcome.SkippedOwnershipUnavailable
                is OwnershipTokenRead.Readable ->
                    when {
                        ownership.token == null || ownership.token != token -> CleanupOutcome.SkippedReplaced
                        else ->
                            try {
                                clearAttemptElapsedMs = SystemClock.elapsedRealtime()
                                clipboard?.clearPrimaryClip()
                                CleanupOutcome.ClearRequestSubmitted
                            } catch (cancellation: CancellationException) {
                                throw cancellation
                            } catch (_: SecurityException) {
                                CleanupOutcome.ClearRejected
                            } catch (_: IllegalStateException) {
                                CleanupOutcome.ClearRejected
                            } catch (_: Exception) {
                                CleanupOutcome.ClearRejected
                            }
                    }
            }
        jobs.remove(generation)
        tokens.remove(generation)
        lastPublication.remove(generation)
        finishReceipt(
            generation,
            outcome,
            CleanupOrigin.Automatic,
            callbackElapsedMs,
            ownershipCheckElapsedMs,
            clearAttemptElapsedMs,
        )
    }

    private fun recordInterruptedCleanup(
        generation: Long,
        token: String,
    ) {
        // A caller may cancel this scheduled job while the app remains alive. Report that
        // interruption without turning it into a false claim that the clip was removed.
        // Intentional supersede/manual cancellation removes the token before cancelling.
        scope.launch {
            owner.withLock {
                if (tokens[generation] != token) return@withLock
                jobs.remove(generation)
                tokens.remove(generation)
                lastPublication.remove(generation)
                finishReceipt(generation, CleanupOutcome.Interrupted, CleanupOrigin.Automatic)
            }
        }
    }

    override suspend fun clearClipboard(): Result<Unit> =
        owner.withLock {
            val clipboard = manager ?: return@withLock Result.failure(ClipboardError.ClipboardUnavailable())
            try {
                clipboard.clearPrimaryClip()
                tokens.keys.toList().forEach { generation ->
                    tokens.remove(generation)
                    jobs.remove(generation)?.cancel()
                    lastPublication.remove(generation)
                    finishReceipt(generation, CleanupOutcome.ManualClearSubmitted, CleanupOrigin.Manual)
                }
                Result.success(Unit)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: SecurityException) {
                Result.failure(ClipboardError.ClearFailed("clipboard clear rejected"))
            } catch (_: IllegalStateException) {
                Result.failure(ClipboardError.ClearFailed("clipboard clear unavailable"))
            } catch (_: Exception) {
                Result.failure(ClipboardError.ClearFailed("clipboard clear unavailable"))
            }
        }

    private fun readOwnershipToken(clipboard: ClipboardManager?): OwnershipTokenRead =
        try {
            if (clipboard == null) {
                OwnershipTokenRead.Unavailable
            } else {
                val description = clipboard.primaryClipDescription
                if (description == null) {
                    OwnershipTokenRead.Unavailable
                } else {
                    OwnershipTokenRead.Readable(description.extras?.getString(OWNERSHIP_TOKEN_KEY))
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            OwnershipTokenRead.Unavailable
        }

    override fun acknowledge(generation: Long) = releaseTerminal(generation)

    override fun abandon(generation: Long) {
        synchronized(receiptLock) {
            val current = _cleanupStatuses.value[generation] ?: return
            if (current.outcome != CleanupOutcome.Pending) {
                setOutcome(generation, CleanupOutcome.Abandoned, current.origin)
                releaseTerminal(generation)
            }
        }
    }

    override fun transferToCustodian(generation: Long) {
        synchronized(receiptLock) {
            val current = _cleanupStatuses.value[generation] ?: return
            if (current.outcome == CleanupOutcome.Pending) {
                custodianOwned.add(generation)
            } else {
                setOutcome(generation, CleanupOutcome.Abandoned, current.origin)
                releaseTerminal(generation)
            }
        }
    }

    private fun finishReceipt(
        generation: Long,
        outcome: CleanupOutcome,
        origin: CleanupOrigin,
        callbackElapsedMs: Long? = null,
        ownershipCheckElapsedMs: Long? = null,
        clearAttemptElapsedMs: Long? = null,
    ) {
        synchronized(receiptLock) {
            if (_cleanupStatuses.value[generation] == null) return
            if (custodianOwned.remove(generation)) {
                setOutcome(generation, CleanupOutcome.Abandoned, origin)
                releaseTerminal(generation)
            } else {
                setOutcome(
                    generation,
                    outcome,
                    origin,
                    callbackElapsedMs,
                    ownershipCheckElapsedMs,
                    clearAttemptElapsedMs,
                )
            }
        }
    }

    private fun releaseTerminal(generation: Long) {
        synchronized(receiptLock) {
            if (_cleanupStatuses.value[generation]?.outcome != CleanupOutcome.Pending) {
                _cleanupStatuses.value = _cleanupStatuses.value - generation
                custodianOwned.remove(generation)
            }
        }
    }

    private fun setOutcome(
        generation: Long,
        outcome: CleanupOutcome,
        origin: CleanupOrigin,
        callbackElapsedMs: Long? = null,
        ownershipCheckElapsedMs: Long? = null,
        clearAttemptElapsedMs: Long? = null,
    ) {
        _cleanupStatuses.value =
            _cleanupStatuses.value +
            (
                generation to
                    ClipboardCleanupStatus(
                        generation,
                        outcome,
                        origin,
                        callbackElapsedMs,
                        ownershipCheckElapsedMs,
                        clearAttemptElapsedMs,
                    )
            )
    }
}
