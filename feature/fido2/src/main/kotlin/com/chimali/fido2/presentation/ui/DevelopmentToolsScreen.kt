package com.chimali.fido2.presentation.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chimali.fido2.BuildConfig
import com.chimali.fido2.domain.model.MakeCredentialOptions
import com.chimali.fido2.domain.model.PublicKeyCredentialParameters
import com.chimali.fido2.domain.model.PublicKeyCredentialRpEntity
import com.chimali.fido2.domain.model.PublicKeyCredentialUserEntity
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
import android.content.pm.PackageManager
import com.chimali.core.ui.theme.LegibilityType

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
@Composable
fun DevelopmentToolsScreen(
    homeViewModel: Fido2HomeViewModel = hiltViewModel(),
    devToolsViewModel: DevToolsViewModel = hiltViewModel()
) {
    val state by devToolsViewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showQrCode by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var showRecoverForm by remember { mutableStateOf(false) }
    var cameraPermGranted by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> cameraPermGranted = granted; if (granted) showScanner = true }

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
        onIntent = devToolsViewModel::onIntent,
        onHomeTestRegistration = homeViewModel::testRegistration
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun DevelopmentToolsContent(
    state: DevToolsUiState,
    onIntent: (DevToolsIntent) -> Unit,
    onHomeTestRegistration: (MakeCredentialOptions) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showQrCode by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var showRecoverForm by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) showScanner = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Development Tools",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.BugReport,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Test & Debug Utilities",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { heading() }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── Registration flow test trigger ────────────────────────────────
            OutlinedButton(
                onClick = {
                    val mockUserId = "user_${System.currentTimeMillis()}"
                    val mockOptions = MakeCredentialOptions.create(
                        rp = PublicKeyCredentialRpEntity.create("webauthn.io", "WebAuthn.io (Test)"),
                        user = PublicKeyCredentialUserEntity.create(
                            mockUserId.toByteArray(), mockUserId, "Chimali Test User"
                        ),
                        challenge = "challenge".toByteArray(),
                        pubKeyCredParams = PublicKeyCredentialParameters.createES256P256()
                    )
                    onHomeTestRegistration(mockOptions)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Text("Trigger Test Registration UI")
            }
            Text(
                text = "Simulates an incoming FIDO2 MakeCredential request from a PC host.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ── DEBUG ONLY: Mnemonic / Master Seed ───────────────────────────
            if (BuildConfig.DEBUG) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    text = "⚠ Dev-only — Master Seed",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )

                // ── View Seed ────────────────────────────────────────────────
                if (state.mnemonicWords == null) {
                    OutlinedButton(
                        onClick = {
                            val activity = context as? FragmentActivity ?: return@OutlinedButton
                            BiometricHelper.authenticate(
                                activity = activity,
                                title = "View Master Seed",
                                description = "Authenticate to view your BIP39 mnemonic."
                            ) {
                                onIntent(DevToolsIntent.LoadMnemonic)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = MaterialTheme.shapes.large,
                        enabled = !state.isLoading
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.isLoading) "Loading…" else "View Master Seed")
                    }
                } else {
                    // Seed word grid
                    MnemonicWordGrid(words = state.mnemonicWords!!)

                    // QR code toggle and Copy
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = { onIntent(DevToolsIntent.CopyToClipboard) },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.large,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                                verticalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterVertically)
                            ) {
                                Icon(
                                    Icons.Default.CopyAll,
                                    contentDescription = null,
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                )
                                Text(
                                    "Copy",
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = { showQrCode = !showQrCode },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.large,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                                verticalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterVertically)
                            ) {
                                Icon(
                                    if (showQrCode) Icons.Default.VisibilityOff else Icons.Default.QrCode,
                                    contentDescription = null,
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                )
                                Text(
                                    if (showQrCode) "Hide QR" else "QR",
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = { onIntent(DevToolsIntent.ClearMnemonic); showQrCode = false },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.large,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                                verticalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterVertically)
                            ) {
                                Icon(
                                    Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                )
                                Text(
                                    "Clear",
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                )
                            }
                        }
                    }

                    if (showQrCode) {
                        MnemonicQrCodeView(words = state.mnemonicWords!!)
                    }
                }

                state.error?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // ── Recover Seed ─────────────────────────────────────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Recover from Seed",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showRecoverForm = !showRecoverForm }) {
                        Icon(
                            if (showRecoverForm) Icons.Default.VisibilityOff else Icons.Default.Refresh,
                            contentDescription = "Toggle recover form"
                        )
                    }
                }

                if (showRecoverForm) {
                    // QR scan button
                    if (!showScanner) {
                        FilledTonalButton(
                            onClick = {
                                val status = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                                if (status == PackageManager.PERMISSION_GRANTED) {
                                    showScanner = true
                                } else {
                                    cameraLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Scan QR Code")
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxWidth().height(280.dp)) {
                            MnemonicQrScanner(
                                onScanned = { words ->
                                    showScanner = false
                                    onIntent(DevToolsIntent.RecoverFromSeed(words))
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Scanned ${words.size} words.")
                                    }
                                },
                                onError = { errMsg ->
                                    showScanner = false
                                    scope.launch { snackbarHostState.showSnackbar(errMsg) }
                                }
                            )
                        }
                        TextButton(onClick = { showScanner = false }) {
                            Text("Cancel Scan")
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        "— or enter 24 words manually —",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ManualMnemonicEntryForm { words ->
                        onIntent(DevToolsIntent.RecoverFromSeed(words))
                    }
                }

                if (state.recoverSuccess) {
                    Text(
                        "✔ Mnemonic validated successfully!",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } // end DEBUG
        }
    }
}

// ---------------------------------------------------------------------------
// Sub-composables
// ---------------------------------------------------------------------------

@Composable
private fun MnemonicWordGrid(words: List<String>) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        itemsIndexed(words) { index, word ->
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(6.dp)
                    )
                    .semantics(mergeDescendants = true) { }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${index + 1}.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(24.dp)
                    )
                    Text(
                        text = word,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = LegibilityType.AtkinsonFontFamily),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun MnemonicQrCodeView(words: List<String>) {
    val mnemonic = remember(words) { words.joinToString(" ") }
    val painter = rememberQrCodePainter(
        data = mnemonic,
        shapes = QrShapes(
            ball = QrBallShape.circle(),
            frame = QrFrameShape.roundCorners(.25f),
            darkPixel = QrPixelShape.roundCorners()
        )
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.foundation.Image(
            painter = painter,
            contentDescription = "Master seed QR code",
            modifier = Modifier.size(240.dp)
        )
        Text(
            "Never screenshot this QR in a production scenario.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun ManualMnemonicEntryForm(onSubmit: (List<String>) -> Unit) {
    val wordCount = 24
    val fields = remember {
        mutableStateListOf<TextFieldValue>().also { list ->
            repeat(wordCount) { list.add(TextFieldValue("")) }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itemsIndexed(fields) { index, value ->
            OutlinedTextField(
                value = value,
                onValueChange = { fields[index] = it },
                label = { Text("${index + 1}", style = MaterialTheme.typography.labelSmall) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = LegibilityType.AtkinsonFontFamily),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    Spacer(Modifier.height(8.dp))
    Button(
        onClick = { onSubmit(fields.map { it.text.trim() }) },
        modifier = Modifier.fillMaxWidth(),
        enabled = fields.all { it.text.isNotBlank() }
    ) {
        Icon(Icons.Default.Key, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Recover Seed")
    }
}

/** Thin helper to fire a biometric prompt without pulling in a ViewModel dependency. */
private object BiometricHelper {
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        description: String,
        onSuccess: () -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setDescription(description)
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build()
        )
    }
}
