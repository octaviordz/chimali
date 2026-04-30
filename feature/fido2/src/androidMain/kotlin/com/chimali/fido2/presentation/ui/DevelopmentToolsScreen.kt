package com.chimali.fido2.presentation.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chimali.core.common.result.map
import com.chimali.core.ui.theme.LegibilityType
import com.chimali.fido2.data.crypto.Fido2CryptoService
import com.chimali.fido2.domain.model.MakeCredentialOptions
import com.chimali.fido2.domain.model.PublicKeyCredentialParameters
import com.chimali.fido2.domain.model.PublicKeyCredentialRpEntity
import com.chimali.fido2.domain.model.PublicKeyCredentialUserEntity
import com.chimali.fido2.presentation.ui.components.ChimaliButton
import com.chimali.fido2.presentation.ui.components.ChimaliOutlinedButton
import com.chimali.fido2.presentation.ui.components.ChimaliTonalButton
import com.chimali.fido2.presentation.viewmodel.DevToolsEffect
import com.chimali.fido2.presentation.viewmodel.DevToolsIntent
import com.chimali.fido2.presentation.viewmodel.DevToolsUiState
import com.chimali.fido2.presentation.viewmodel.DevToolsViewModel
import com.chimali.fido2.presentation.viewmodel.Fido2HomeViewModel
import io.github.alexzhirkevich.qrose.options.QrBallShape
import io.github.alexzhirkevich.qrose.options.QrFrameShape
import io.github.alexzhirkevich.qrose.options.QrPixelShape
import io.github.alexzhirkevich.qrose.options.QrShapes
import io.github.alexzhirkevich.qrose.options.circle
import io.github.alexzhirkevich.qrose.options.roundCorners
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * T146b/c/d/e/f — Development / QA screen housing test utilities.
 *
 * Contains:
 * - Test registration trigger (pre-existing)
 * - [DEBUG ONLY] View Master Seed (biometric-gated) + QR code export
 * - [DEBUG ONLY] Recover from Seed (manual entry) + QR scan import
 *
 * All mnemonic UI sections are guarded by [BuildConfig.DEBUG] to prevent
 * accidental inclusion in release builds.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("FunctionNaming")
@Composable
fun DevelopmentToolsScreen(
    homeViewModel: Fido2HomeViewModel = koinViewModel(),
    devToolsViewModel: DevToolsViewModel = koinViewModel(),
) {
    val state by devToolsViewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Collect one-time effects
    LaunchedEffect(Unit) {
        devToolsViewModel.effects.collect { effect ->
            when (effect) {
                is DevToolsEffect.ShowSnackbar ->
                    snackbarHostState.showSnackbar(effect.message)
                is DevToolsEffect.RequestBiometric -> { /* handled inline below */ }
            }
        }
    }

    // Clear mnemonic on leaving the screen
    DisposableEffect(Unit) {
        onDispose { devToolsViewModel.onIntent(DevToolsIntent.ClearMnemonic) }
    }

    DevelopmentToolsContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onIntent = devToolsViewModel::onIntent,
        onHomeTestRegistration = homeViewModel::testRegistration,
    )
}

private val SCANNER_PREVIEW_HEIGHT = 280.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Suppress("FunctionNaming")
@Composable
internal fun DevelopmentToolsContent(
    state: DevToolsUiState,
    snackbarHostState: SnackbarHostState,
    onIntent: (DevToolsIntent) -> Unit,
    onHomeTestRegistration: (MakeCredentialOptions) -> Unit,
) {
    // 0 = ES256, 1 = ML-DSA-65
    var selectedAlgIndex by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Development Tools",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() },
                    )
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Default.BugReport,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Test & Debug Utilities",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { heading() },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            AlgorithmSelector(
                selectedAlgIndex = selectedAlgIndex,
                onAlgIndexChange = { selectedAlgIndex = it },
            )

            TestRegistrationTrigger(
                selectedAlgIndex = selectedAlgIndex,
                onHomeTestRegistration = onHomeTestRegistration,
            )

            // ── DEBUG ONLY: Mnemonic / Master Seed ───────────────────────────
            if (com.chimali.core.common.isDebug) {
                DebugMnemonicSection(
                    state = state,
                    snackbarHostState = snackbarHostState,
                    onIntent = onIntent,
                )
            } // end DEBUG
        }
    }
}

// ---------------------------------------------------------------------------
// Sub-composables
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Suppress("FunctionNaming")
@Composable
private fun DebugMnemonicSection(
    state: DevToolsUiState,
    snackbarHostState: SnackbarHostState,
    onIntent: (DevToolsIntent) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showQrCode by remember { mutableStateOf(false) }
    var showScanner by rememberSaveable { mutableStateOf(false) }
    var showRecoverForm by rememberSaveable { mutableStateOf(false) }

    val cameraLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                showScanner = true
            } else {
                scope.launch {
                    val result =
                        snackbarHostState.showSnackbar(
                            message = "Camera permission is required to scan QR codes.",
                            actionLabel = "Settings",
                            duration = SnackbarDuration.Long,
                        )
                    if (result == SnackbarResult.ActionPerformed) {
                        val intent =
                            android.content.Intent(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            ).apply { data = android.net.Uri.fromParts("package", context.packageName, null) }
                        context.startActivity(intent)
                    }
                }
            }
        }

    Column {
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Text(
            text = "⚠ Dev-only — Master Seed",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
        )

        if (state.mnemonicWords == null) {
            ChimaliOutlinedButton(
                onClick = {
                    (context as? FragmentActivity)?.let { activity ->
                        BiometricHelper.authenticate(
                            activity = activity,
                            title = "View Master Seed",
                            description = "Authenticate to view your BIP39 mnemonic.",
                        ) { onIntent(DevToolsIntent.LoadMnemonic) }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading,
            ) {
                Icon(Icons.Default.Visibility, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (state.isLoading) "Loading…" else "View Master Seed")
            }
        } else {
            MnemonicWordGrid(words = state.mnemonicWords)
            MnemonicActionRow(
                showQrCode = showQrCode,
                onCopy = { onIntent(DevToolsIntent.CopyToClipboard) },
                onToggleQr = { showQrCode = !showQrCode },
                onClear = {
                    onIntent(DevToolsIntent.ClearMnemonic)
                    showQrCode = false
                },
            )
            if (showQrCode) MnemonicQrCodeView(words = state.mnemonicWords)
        }

        state.error?.let { err ->
            Text(text = err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Recover from Seed",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { showRecoverForm = !showRecoverForm }) {
                Icon(
                    if (showRecoverForm) Icons.Default.VisibilityOff else Icons.Default.Refresh,
                    contentDescription = "Toggle recover form",
                )
            }
        }

        if (showRecoverForm) {
            RecoverSeedForm(
                snackbarHostState = snackbarHostState,
                onRequestCameraPermission = cameraLauncher::launch,
                showScanner = showScanner,
                onShowScanner = { showScanner = it },
                onIntent = onIntent,
            )
        }

        if (state.recoverSuccess) {
            Text(
                "✔ Mnemonic validated successfully!",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Suppress("FunctionNaming")
@Composable
private fun MnemonicActionRow(
    showQrCode: Boolean,
    onCopy: () -> Unit,
    onToggleQr: () -> Unit,
    onClear: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ChimaliOutlinedButton(
            onClick = onCopy,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterVertically),
            ) {
                Icon(
                    Icons.Default.CopyAll,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                Text("Copy", textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.CenterVertically))
            }
        }
        ChimaliOutlinedButton(
            onClick = onToggleQr,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterVertically),
            ) {
                Icon(
                    if (showQrCode) Icons.Default.VisibilityOff else Icons.Default.QrCode,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                Text(
                    if (showQrCode) "Hide QR" else "QR",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }
        ChimaliOutlinedButton(
            onClick = onClear,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterVertically),
            ) {
                Icon(
                    Icons.Default.VisibilityOff,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                Text("Clear", textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.CenterVertically))
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun RecoverSeedForm(
    snackbarHostState: SnackbarHostState,
    onRequestCameraPermission: (String) -> Unit,
    showScanner: Boolean,
    onShowScanner: (Boolean) -> Unit,
    onIntent: (DevToolsIntent) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showRationale by remember { mutableStateOf(false) }

    Box {
        Column {
            if (!showScanner) {
                ChimaliTonalButton(
                    onClick = {
                        val status = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                        when {
                            status == PackageManager.PERMISSION_GRANTED -> onShowScanner(true)
                            (context as? FragmentActivity)?.let {
                                androidx.core.app.ActivityCompat
                                    .shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
                            } == true -> showRationale = true
                            else -> onRequestCameraPermission(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Scan QR Code")
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(SCANNER_PREVIEW_HEIGHT)) {
                    MnemonicQrScanner(
                        onScan = { words ->
                            onShowScanner(false)
                            onIntent(DevToolsIntent.RecoverFromSeed(words))
                            scope.launch { snackbarHostState.showSnackbar("Scanned ${words.size} words.") }
                        },
                        onError = { errMsg ->
                            onShowScanner(false)
                            scope.launch {
                                val result =
                                    snackbarHostState.showSnackbar(
                                        message = errMsg,
                                        actionLabel = "Settings",
                                        duration = SnackbarDuration.Long,
                                    )
                                if (result == SnackbarResult.ActionPerformed) {
                                    val intent =
                                        android.content.Intent(
                                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        ).apply {
                                            data = android.net.Uri.fromParts("package", context.packageName, null)
                                        }
                                    context.startActivity(intent)
                                }
                            }
                        },
                    )
                }
                TextButton(onClick = { onShowScanner(false) }) { Text("Cancel Scan") }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "— or enter 24 words manually —",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ManualMnemonicEntryForm { words -> onIntent(DevToolsIntent.RecoverFromSeed(words)) }
        }

        if (showRationale) {
            AlertDialog(
                onDismissRequest = { showRationale = false },
                title = { Text("Camera Permission") },
                text = { Text("The camera is required to scan the recovery mnemonic QR code.") },
                confirmButton = {
                    TextButton(onClick = {
                        showRationale = false
                        onRequestCameraPermission(Manifest.permission.CAMERA)
                    }) {
                        Text("Allow")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRationale = false }) { Text("Cancel") }
                },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Sub-composables
// ---------------------------------------------------------------------------

@Suppress("FunctionNaming")
@Composable
private fun MnemonicWordGrid(words: List<String>) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(words) { index, word ->
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier =
                    Modifier
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(6.dp),
                        )
                        .semantics(mergeDescendants = true) { },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${index + 1}.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(24.dp),
                    )
                    Text(
                        text = word,
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                fontFamily = LegibilityType.AtkinsonFontFamily,
                            ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun MnemonicQrCodeView(words: List<String>) {
    val mnemonic = remember(words) { words.joinToString(" ") }
    val painter =
        rememberQrCodePainter(
            data = mnemonic,
            shapes =
                QrShapes(
                    ball = QrBallShape.circle(),
                    frame = QrFrameShape.roundCorners(.25f),
                    darkPixel = QrPixelShape.roundCorners(),
                ),
        )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.foundation.Image(
            painter = painter,
            contentDescription = "Master seed QR code",
            modifier = Modifier.size(240.dp),
        )
        Text(
            "Never screenshot this QR in a production scenario.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Suppress("FunctionNaming")
@Composable
private fun ManualMnemonicEntryForm(onSubmit: (List<String>) -> Unit) {
    val wordCount = 24
    val fields =
        remember {
            mutableStateListOf<TextFieldValue>().also { list ->
                repeat(wordCount) { list.add(TextFieldValue("")) }
            }
        }

    Column {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(fields) { index, value ->
                OutlinedTextField(
                    value = value,
                    onValueChange = { fields[index] = it },
                    label = { Text("${index + 1}", style = MaterialTheme.typography.labelSmall) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = LegibilityType.AtkinsonFontFamily),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        ChimaliButton(
            onClick = { onSubmit(fields.map { it.text.trim() }) },
            modifier = Modifier.fillMaxWidth(),
            enabled = fields.all { it.text.isNotBlank() },
        ) {
            Icon(Icons.Default.Key, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Recover Seed")
        }
    }
}

/** Thin helper to fire a biometric prompt without pulling in a ViewModel dependency. */
private object BiometricHelper {
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        description: String,
        onSuccess: () -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt =
            BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onSuccess()
                    }
                },
            )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setDescription(description)
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                )
                .build(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("FunctionNaming")
@Composable
private fun AlgorithmSelector(
    selectedAlgIndex: Int,
    onAlgIndexChange: (Int) -> Unit,
) {
    Column {
        Text(
            text = "Algorithm",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        val algOptions = listOf("ES256", "Ed25519", "ML-DSA-65")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            algOptions.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = selectedAlgIndex == index,
                    onClick = { onAlgIndexChange(index) },
                    shape = SegmentedButtonDefaults.itemShape(index, algOptions.size),
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
    }
}

@Suppress("FunctionNaming", "ForbiddenComment")
@Composable
private fun TestRegistrationTrigger(
    selectedAlgIndex: Int,
    onHomeTestRegistration: (MakeCredentialOptions) -> Unit,
) {
    Column {
        ChimaliOutlinedButton(
            onClick = {
                val mockUserId = "user_${System.currentTimeMillis()}"
                val algId =
                    when (selectedAlgIndex) {
                        0 -> Fido2CryptoService.COSE_ES256
                        1 -> Fido2CryptoService.COSE_ED25519
                        else -> Fido2CryptoService.COSE_ML_DSA_65
                    }
                val params =
                    when (selectedAlgIndex) {
                        0 -> PublicKeyCredentialParameters.createES256P256()
                        1 -> PublicKeyCredentialParameters.createEd25519()
                        else -> PublicKeyCredentialParameters.createMlDsa65()
                    }
                val mockOptions =
                    MakeCredentialOptions.create(
                        rp = PublicKeyCredentialRpEntity.create("webauthn.io", "WebAuthn.io (Test)"),
                        user =
                            PublicKeyCredentialUserEntity.create(
                                mockUserId.toByteArray(),
                                mockUserId,
                                "Chimali Test User",
                            ),
                        challenge = "challenge".toByteArray(),
                        pubKeyCredParams = params,
                        selectedAlgId = algId,
                    )
                onHomeTestRegistration(mockOptions)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Trigger Test Registration UI")
        }
        Text(
            text = "Simulates an incoming FIDO2 MakeCredential request from a PC host.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
