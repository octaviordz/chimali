package com.chimali.security

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import com.chimali.core.clipboard.AndroidClipboardManagerService
import com.chimali.core.clipboard.CLIPBOARD_CLEANUP_DELAY_MS
import com.chimali.core.clipboard.CleanupOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.koin.core.context.GlobalContext

/** Disposable-build-only foreground fixture for process-interruption clipboard evidence. */
class ClipboardTargetCopyActivity : Activity() {
    private val activityScope = MainScope()
    private var copyStarted = false
    private var repeatCopyPending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getStringExtra(EXTRA_ACTION) == ACTION_MANUAL_CLEAR) {
            activityScope.launch { requestManualClear() }
        }
        if (intent.getStringExtra(EXTRA_ACTION) == ACTION_REPEAT_COPY) {
            repeatCopyPending = true
        }
        if (intent.getStringExtra(EXTRA_ACTION) == ACTION_UNCONFIRMED_COPY) {
            activityScope.launch { publishSyntheticCopy(TARGET_UNCONFIRMED_TOKEN_FILE, "-unconfirmed") }
        }
        if (intent.getStringExtra(EXTRA_ACTION) == ACTION_REJECT_COPY) {
            activityScope.launch { publishSyntheticCopy(TARGET_REJECT_TOKEN_FILE, "-reject") }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getStringExtra(EXTRA_ACTION) == ACTION_MANUAL_CLEAR) {
            activityScope.launch { requestManualClear() }
        }
        if (intent.getStringExtra(EXTRA_ACTION) == ACTION_REPEAT_COPY) {
            repeatCopyPending = true
        }
        if (intent.getStringExtra(EXTRA_ACTION) == ACTION_UNCONFIRMED_COPY) {
            activityScope.launch { publishSyntheticCopy(TARGET_UNCONFIRMED_TOKEN_FILE, "-unconfirmed") }
        }
        if (intent.getStringExtra(EXTRA_ACTION) == ACTION_REJECT_COPY) {
            activityScope.launch { publishSyntheticCopy(TARGET_REJECT_TOKEN_FILE, "-reject") }
        }
    }

    @Suppress("TooGenericExceptionCaught") // The isolated audit target records sanitized manual-clear failure classes.
    private suspend fun requestManualClear() {
        val runId = intent.getStringExtra(EXTRA_RUN_ID) ?: "unknown"
        var stage = "service-resolution"
        var failureClass: String? = null
        try {
            val service = GlobalContext.get().get<AndroidClipboardManagerService>()
            stage = "clear-request"
            val result = service.clearClipboard()
            stage = if (result.isSuccess) "clear-success" else "clear-failure"
            failureClass = result.exceptionOrNull()?.javaClass?.simpleName
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            failureClass = failure.javaClass.simpleName
        } finally {
            val receipt = JSONObject().put("runId", runId).put("stage", stage)
            failureClass?.let { receipt.put("failureClass", it) }
            runCatching { getFileStreamPath("clipboard-manual-clear-$runId.json").writeText(receipt.toString()) }
            finish()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        if (repeatCopyPending) {
            repeatCopyPending = false
            activityScope.launch { publishSyntheticRepeatCopy() }
            return
        }
        if (copyStarted ||
            intent.getStringExtra(EXTRA_ACTION) in DEFERRED_ACTIONS
        ) {
            return
        }
        copyStarted = true
        activityScope.launch { publishSyntheticCopy(TARGET_TOKEN_FILE, "") }
    }

    private suspend fun publishSyntheticRepeatCopy() {
        val runId = intent.getStringExtra(EXTRA_RUN_ID)
        if (runId.isNullOrBlank() || !RUN_ID_PATTERN.matches(runId)) {
            writeReceipt(runId ?: "invalid", "InvalidRunId", fileSuffix = "-repeat")
            return
        }
        publishSyntheticCopy(TARGET_REPEAT_TOKEN_FILE, "-repeat")
    }

    @Suppress("TooGenericExceptionCaught") // The isolated audit target records sanitized setup failure classes.
    private suspend fun publishSyntheticCopy(tokenFileName: String, fileSuffix: String) {
        val runId = intent.getStringExtra(EXTRA_RUN_ID)
        if (runId.isNullOrBlank() || !RUN_ID_PATTERN.matches(runId)) {
            writeReceipt(runId ?: "invalid", "InvalidRunId", fileSuffix = fileSuffix)
            return
        }

        try {
            val tokenFile = getFileStreamPath(tokenFileName)
            val expectedToken = tokenFile.inputStream().bufferedReader().use { it.readText() }
            check(expectedToken.isNotBlank())
            check(tokenFile.delete())

            val service = GlobalContext.get().get<AndroidClipboardManagerService>()
            val tokenSource = service.javaClass.getDeclaredField("ownershipTokenSource").apply { isAccessible = true }
            val productionSource = tokenSource.get(service)
            val copyCallStartedElapsedMs = SystemClock.elapsedRealtime()
            val receipt =
                try {
                    tokenSource.set(service, { expectedToken })
                    val syntheticPassword = "synthetic-observer-password-$runId".toCharArray()
                    try {
                        service.copySensitiveData("synthetic test", syntheticPassword).getOrThrow()
                    } finally {
                        syntheticPassword.fill('\u0000')
                    }
                } finally {
                    tokenSource.set(service, productionSource)
                }
            val copyCallReturnedElapsedMs = SystemClock.elapsedRealtime()
            val publicationElapsedMs = receipt.scheduledElapsedMs - CLIPBOARD_CLEANUP_DELAY_MS
            ClipboardAuditCleanupRecorder.observe(
                applicationContext,
                service,
                runId,
                receipt.generation,
                receipt.scheduledElapsedMs,
                fileSuffix,
            )
            writeReceipt(
                runId,
                receipt.publication.name,
                receipt.generation,
                receipt.scheduledElapsedMs,
                publicationElapsedMs = publicationElapsedMs,
                copyCallStartedElapsedMs = copyCallStartedElapsedMs,
                copyCallReturnedElapsedMs = copyCallReturnedElapsedMs,
                fileSuffix = fileSuffix,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            writeReceipt(runId, "CopyUnavailable", failureClass = failure.javaClass.simpleName, fileSuffix = fileSuffix)
        }
    }

    private fun writeReceipt(
        runId: String,
        publication: String,
        generation: Long? = null,
        scheduledElapsedMs: Long? = null,
        publicationElapsedMs: Long? = null,
        copyCallStartedElapsedMs: Long? = null,
        copyCallReturnedElapsedMs: Long? = null,
        failureClass: String? = null,
        fileSuffix: String = "",
    ) {
        val receipt =
            JSONObject()
                .put("runId", runId)
                .put("targetWindowFocused", hasWindowFocus())
                .put("publication", publication)
        generation?.let { receipt.put("generation", it) }
        scheduledElapsedMs?.let { receipt.put("scheduledElapsedMs", it) }
        publicationElapsedMs?.let { receipt.put("publicationElapsedMs", it) }
        copyCallStartedElapsedMs?.let { receipt.put("copyCallStartedElapsedMs", it) }
        copyCallReturnedElapsedMs?.let { receipt.put("copyCallReturnedElapsedMs", it) }
        failureClass?.let { receipt.put("failureClass", it) }
        getFileStreamPath("clipboard-target-ready-$runId$fileSuffix.json").writeText(receipt.toString())
    }

    override fun onDestroy() {
        activityScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_RUN_ID = "com.chimali.security.extra.RUN_ID"
        const val EXTRA_ACTION = "com.chimali.security.extra.ACTION"
        const val ACTION_MANUAL_CLEAR = "manual-clear"
        const val ACTION_REPEAT_COPY = "repeat-copy"
        const val ACTION_UNCONFIRMED_COPY = "unconfirmed-copy"
        const val ACTION_REJECT_COPY = "reject-copy"
        const val TARGET_TOKEN_FILE = "clipboard-target.expected"
        const val TARGET_REPEAT_TOKEN_FILE = "clipboard-target.expected.repeat"
        const val TARGET_UNCONFIRMED_TOKEN_FILE = "clipboard-target.expected.unconfirmed"
        const val TARGET_REJECT_TOKEN_FILE = "clipboard-target.expected.reject"
        private val RUN_ID_PATTERN = Regex("[A-Za-z0-9_-]{1,64}")
        private val DEFERRED_ACTIONS =
            setOf(
                ACTION_MANUAL_CLEAR,
                ACTION_REPEAT_COPY,
                ACTION_UNCONFIRMED_COPY,
                ACTION_REJECT_COPY,
            )
    }
}

/** Keeps audit-only status observation alive when the target activity is backgrounded. */
private object ClipboardAuditCleanupRecorder {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun observe(
        context: Context,
        service: AndroidClipboardManagerService,
        runId: String,
        generation: Long,
        scheduledElapsedMs: Long,
        fileSuffix: String = "",
    ) {
        scope.launch {
            service.cleanupStatuses.collect { statuses ->
                val status = statuses[generation] ?: return@collect
                if (status.outcome != CleanupOutcome.Pending) {
                    val outcome =
                        JSONObject()
                            .put("runId", runId)
                            .put("generation", generation)
                            .put("scheduledElapsedMs", scheduledElapsedMs)
                            .put("terminalStatusElapsedMs", SystemClock.elapsedRealtime())
                            .put("outcome", status.outcome.name)
                    context
                        .getFileStreamPath(
                            "clipboard-target-outcome-$runId$fileSuffix.json",
                        ).writeText(outcome.toString())
                    service.acknowledge(generation)
                    cancel()
                }
            }
        }
    }
}
