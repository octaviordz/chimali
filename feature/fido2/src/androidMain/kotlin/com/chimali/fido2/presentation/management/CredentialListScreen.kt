package com.chimali.fido2.presentation.management

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import com.chimali.core.ui.R as CoreR
import com.chimali.fido2.domain.model.PasskeyCredential
import com.chimali.fido2.presentation.ui.BiometricPromptComponent
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * T121 — List Screen to show all FIDO2 Passkeys.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("FunctionNaming", "ForbiddenComment")
@Composable
fun CredentialListScreen(
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CredentialManagementViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var showBiometricPrompt by remember { mutableStateOf<PasskeyCredential?>(null) }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is CredentialManagementEffect.ShowToast -> {
                    snackbarHostState.showSnackbar(effect.message)
                }

                is CredentialManagementEffect.ShowUndoSnackbar -> {
                    snackbarHostState.showSnackbar(
                        message = effect.message,
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Long,
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.removalEvents.collect { credential ->
            val result =
                snackbarHostState.showSnackbar(
                    message = "Deleted passkey for ${credential.userName}",
                    actionLabel = "Undo",
                    duration = SnackbarDuration.Long,
                )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.onIntent(CredentialManagementIntent.UndoDelete(credential.id))
            } else {
                viewModel.onIntent(CredentialManagementIntent.CommitDelete(credential.id))
            }
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            "Passkeys",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateUp) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
                SearchBar(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    inputField = {
                        SearchBarDefaults.InputField(
                            query = searchQuery,
                            onQueryChange = {
                                searchQuery = it
                                viewModel.onIntent(CredentialManagementIntent.UpdateSearchQuery(it))
                            },
                            onSearch = { },
                            expanded = false,
                            onExpandedChange = { },
                            placeholder = { Text("Search passkeys") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            searchQuery = ""
                                            viewModel.onIntent(CredentialManagementIntent.UpdateSearchQuery(""))
                                        },
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                                    }
                                }
                            },
                        )
                    },
                    expanded = false,
                    onExpandedChange = { },
                ) { }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.isLoading && state.credentials.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (state.credentials.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val emptyMsg = if (searchQuery.isEmpty()) "No passkeys yet." else "No results for \"$searchQuery\""
                    Text(
                        text = emptyMsg,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val listState = rememberLazyListState()

                val isLoadMoreRequired by remember {
                    derivedStateOf {
                        val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                        (lastVisibleItem != null) &&
                            (lastVisibleItem.index >= (state.credentials.size - LOAD_THRESHOLD))
                    }
                }

                LaunchedEffect(isLoadMoreRequired) {
                    if (isLoadMoreRequired) {
                        viewModel.onIntent(CredentialManagementIntent.LoadNextPage)
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    items(state.credentials, key = { it.id.encoded }) { credential ->
                        CredentialSwipeToDismissBox(
                            credential = credential,
                            onPendingDelete = { credential ->
                                viewModel.onIntent(CredentialManagementIntent.PendingDelete(credential))
                            },
                            onSelect = { credential ->
                                viewModel.onIntent(CredentialManagementIntent.SelectCredential(credential))
                            },
                        )
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    state.credentialToDelete?.let { credential ->
        DeleteConfirmationDialog(
            title = "Delete Passkey?",
            message = "This will permanently remove the passkey for ${credential.userName} from this device.",
            onConfirm = {
                viewModel.onIntent(CredentialManagementIntent.DismissDialog)
                showBiometricPrompt = credential
            },
            onDismiss = { viewModel.onIntent(CredentialManagementIntent.DismissDialog) },
        )
    }

    // Biometric Auth for Deletion
    showBiometricPrompt?.let { credential ->
        BiometricPromptComponent(
            title = "Confirm Deletion",
            subtitle = "Verify your identity to delete the passkey for ${credential.rpId}",
            onSuccess = {
                showBiometricPrompt = null
                viewModel.onIntent(CredentialManagementIntent.ConfirmDelete(credential.id))
            },
            onError = { _, _ ->
                showBiometricPrompt = null
                scope.launch { snackbarHostState.showSnackbar("Authentication failed") }
            },
            onFallback = {
                showBiometricPrompt = null
                Logger.d { "CredentialList: Biometric prompt fallback/dismissed" }
            },
        )
    }

    // Details Modal
    state.selectedCredential?.let { credential ->
        CredentialDetailsScreen(
            credential = credential,
            onDismiss = { viewModel.onIntent(CredentialManagementIntent.DismissDialog) },
            onDelete = {
                viewModel.onIntent(CredentialManagementIntent.DismissDialog)
                viewModel.onIntent(CredentialManagementIntent.PendingDelete(credential))
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CredentialSwipeToDismissBox(
    credential: PasskeyCredential,
    onPendingDelete: (PasskeyCredential) -> Unit,
    onSelect: (PasskeyCredential) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState =
        rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                if (value != SwipeToDismissBoxValue.Settled) {
                    onPendingDelete(credential)
                    false // Handle visibility via ViewModel state
                } else {
                    false
                }
            },
        ) { totalDistance -> totalDistance * DISMISS_THRESHOLD }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            Box(modifier = Modifier.fillMaxSize()) {
                val targetValue = dismissState.targetValue
                val color by animateColorAsState(
                    targetValue =
                        when (targetValue) {
                            SwipeToDismissBoxValue.Settled -> MaterialTheme.colorScheme.surfaceVariant
                            else -> MaterialTheme.colorScheme.errorContainer
                        },
                    label = "bg_color",
                )
                val iconScale by animateFloatAsState(
                    targetValue = if (targetValue != SwipeToDismissBoxValue.Settled) 1.2f else 1.0f,
                    animationSpec = tween(durationMillis = 300),
                    label = "icon_scale",
                )

                val avdImage = AnimatedImageVector.animatedVectorResource(CoreR.drawable.avd_delete)
                val avdPainter =
                    rememberAnimatedVectorPainter(
                        animatedImageVector = avdImage,
                        atEnd = targetValue != SwipeToDismissBoxValue.Settled,
                    )

                Box(
                    modifier = Modifier.fillMaxSize().background(color).padding(horizontal = 20.dp),
                ) {
                    Icon(
                        painter = avdPainter,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier =
                            Modifier
                                .align(
                                    Alignment.CenterStart,
                                ).graphicsLayer(scaleX = iconScale, scaleY = iconScale),
                    )
                    Icon(
                        painter = avdPainter,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier =
                            Modifier
                                .align(
                                    Alignment.CenterEnd,
                                ).graphicsLayer(scaleX = iconScale, scaleY = iconScale),
                    )
                }
            }
        },
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        content = {
            CredentialItem(
                credential = credential,
                onClick = { onSelect(it) },
            )
        },
    )
}

private const val LOAD_THRESHOLD = 5
private const val DISMISS_THRESHOLD = 0.5f
