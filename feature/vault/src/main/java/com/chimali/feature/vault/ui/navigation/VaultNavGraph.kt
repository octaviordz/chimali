package com.chimali.feature.vault.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chimali.feature.vault.api.VaultIntent
import com.chimali.feature.vault.internal.VaultViewModel
import com.chimali.feature.vault.internal.payload.CreditCardPayload
import com.chimali.feature.vault.internal.payload.PasswordPayload
import com.chimali.feature.vault.internal.payload.SecureNotePayload
import com.chimali.feature.vault.ui.CreditCardDetailScreen
import com.chimali.feature.vault.ui.CreditCardEntryScreen
import com.chimali.feature.vault.ui.LabelManagerScreen
import com.chimali.feature.vault.ui.PasswordDetailScreen
import com.chimali.feature.vault.ui.PasswordEntryScreen
import com.chimali.feature.vault.ui.SecureNoteDetailScreen
import com.chimali.feature.vault.ui.SecureNoteEntryScreen
import com.chimali.feature.vault.ui.VaultListScreen
import java.util.UUID
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultNavGraph(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    viewModel: VaultViewModel = koinViewModel(),
) {
    // Only this nonsecret marker is saved. Restored editors have no surviving draft/identity owner.
    var previouslyComposed by rememberSaveable { mutableStateOf(false) }
    val restoredSession = remember { previouslyComposed }
    LaunchedEffect(Unit) {
        previouslyComposed = true
        if (restoredSession &&
            navController.currentDestination?.route in
            setOf(
                VaultDestinations.ENTRY_PASSWORD_ROUTE,
                VaultDestinations.ENTRY_CARD_ROUTE,
                VaultDestinations.ENTRY_NOTE_ROUTE,
            )
        ) {
            viewModel.processIntent(VaultIntent.AbandonMutation)
            viewModel.processIntent(VaultIntent.ClearSelectedItem)
            navController.popBackStack(VaultDestinations.LIST_ROUTE, false)
        }
    }

    val state by viewModel.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val listClearedForBackgroundState = remember(viewModel) { mutableStateOf(false) }
    var listClearedForBackground by listClearedForBackgroundState
    val showAddTypeSheetState = remember { mutableStateOf(false) }
    var showAddTypeSheet by showAddTypeSheetState

    val editingPasswordPayloadState = remember { mutableStateOf<Pair<UUID, PasswordPayload>?>(null) }
    var editingPasswordPayload by editingPasswordPayloadState
    val editingCreditCardPayloadState = remember { mutableStateOf<Pair<UUID, CreditCardPayload>?>(null) }
    var editingCreditCardPayload by editingCreditCardPayloadState
    val editingSecureNotePayloadState = remember { mutableStateOf<Pair<UUID, SecureNotePayload>?>(null) }
    var editingSecureNotePayload by editingSecureNotePayloadState

    DisposableEffect(viewModel) {
        onDispose {
            clearEditingPayloadCopies(
                editingPasswordPayload?.second,
                editingCreditCardPayload?.second,
                editingSecureNotePayload?.second,
            )
            viewModel.processIntent(VaultIntent.ClearSelectedItem)
            viewModel.processIntent(VaultIntent.AbandonMutation)
        }
    }

    val editingLabelIdsState = remember { mutableStateOf(emptySet<UUID>()) }
    var editingLabelIds by editingLabelIdsState

    ObserveVaultBackground(
        lifecycleOwner = lifecycleOwner,
        navController = navController,
        onStop = {
            clearVaultBackgroundOwners(
                viewModel,
                navController,
                listClearedForBackgroundState,
                showAddTypeSheetState,
                editingPasswordPayloadState,
                editingCreditCardPayloadState,
                editingSecureNotePayloadState,
                editingLabelIdsState,
            )
        },
        onStart = {
            if (listClearedForBackgroundState.value) {
                listClearedForBackgroundState.value = false
                viewModel.processIntent(VaultIntent.LoadItems(viewModel.state.value.selectedLabelId))
                viewModel.processIntent(VaultIntent.LoadLabels)
            }
        },
    )

    LaunchedEffect(Unit) {
        viewModel.processIntent(VaultIntent.LoadItems())
        viewModel.processIntent(VaultIntent.LoadLabels)
    }

    LaunchedEffect(state.mutationState) {
        if (state.mutationState == com.chimali.feature.vault.api.VaultMutationState.SUCCEEDED &&
            navController.currentBackStackEntry?.destination?.route != VaultDestinations.LIST_ROUTE
        ) {
            navController.popBackStack(VaultDestinations.LIST_ROUTE, false)
            clearEditingPayloadCopies(
                editingPasswordPayload?.second,
                editingCreditCardPayload?.second,
                editingSecureNotePayload?.second,
            )
            editingPasswordPayload = null
            editingCreditCardPayload = null
            editingSecureNotePayload = null
            viewModel.processIntent(VaultIntent.ResetMutation)
        }
    }

    NavHost(
        navController = navController,
        startDestination = VaultDestinations.LIST_ROUTE,
        modifier = modifier,
    ) {
        composable(VaultDestinations.LIST_ROUTE) {
            VaultListScreen(
                items = state.items,
                labels = state.labels,
                selectedLabelId = state.selectedLabelId,
                onItemClick = { item ->
                    navController.navigate(VaultDestinations.detailRoute(item.type, item.id))
                },
                onAddClick = { showAddTypeSheet = true },
                onLabelFilterClick = { labelId ->
                    viewModel.processIntent(VaultIntent.LoadItems(labelId))
                },
                onManageLabelsClick = {
                    navController.navigate(VaultDestinations.LABELS_ROUTE)
                },
                onOpenSettings = onOpenSettings,
                errorMessage = state.errorMessage,
            )

            if (showAddTypeSheet) {
                val sheetState = rememberModalBottomSheetState()
                ModalBottomSheet(
                    onDismissRequest = { showAddTypeSheet = false },
                    sheetState = sheetState,
                ) {
                    Column(modifier = Modifier.padding(bottom = 32.dp)) {
                        ListItem(
                            headlineContent = { Text("Password") },
                            leadingContent = { Icon(Icons.Default.Lock, contentDescription = null) },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showAddTypeSheet = false
                                        editingPasswordPayload = null
                                        navController.navigate(VaultDestinations.ENTRY_PASSWORD_ROUTE)
                                    },
                        )
                        ListItem(
                            headlineContent = { Text("Credit Card") },
                            leadingContent = { Icon(Icons.Default.CreditCard, contentDescription = null) },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showAddTypeSheet = false
                                        editingCreditCardPayload = null
                                        navController.navigate(VaultDestinations.ENTRY_CARD_ROUTE)
                                    },
                        )
                        ListItem(
                            headlineContent = { Text("Secure Note") },
                            leadingContent = { Icon(Icons.AutoMirrored.Filled.Note, contentDescription = null) },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showAddTypeSheet = false
                                        editingSecureNotePayload = null
                                        navController.navigate(VaultDestinations.ENTRY_NOTE_ROUTE)
                                    },
                        )
                    }
                }
            }
        }

        // Add / Edit Password
        composable(VaultDestinations.ENTRY_PASSWORD_ROUTE) {
            DisposableEffect(Unit) {
                onDispose {
                    clearEditingPayloadCopies(editingPasswordPayload?.second, null, null)
                    viewModel.processIntent(VaultIntent.AbandonMutation)
                }
            }
            PasswordEntryScreen(
                saveSucceeded = state.mutationState == com.chimali.feature.vault.api.VaultMutationState.SUCCEEDED,
                initialPayload = editingPasswordPayload?.second,
                isSaving = state.mutationState == com.chimali.feature.vault.api.VaultMutationState.PENDING,
                errorMessage = state.errorMessage,
                labels = state.labels,
                initialLabelIds = if (editingPasswordPayload != null) editingLabelIds else emptySet(),
                onSave = {},
                onSaveWithLabels = { payload, labelIds ->
                    val editingId = editingPasswordPayload?.first
                    viewModel.processIntent(
                        VaultIntent.SavePassword(id = editingId, payload = payload, labelIds = labelIds),
                    )
                },
                onCancel = {
                    viewModel.processIntent(VaultIntent.AbandonMutation)
                    editingPasswordPayload?.second?.clearMemory()
                    editingPasswordPayload = null
                    navController.popBackStack()
                },
            )
        }

        // Password Detail
        composable(
            route = VaultDestinations.DETAIL_PASSWORD_ROUTE,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { backStackEntry ->
            val idString = backStackEntry.arguments?.getString("id")
            val itemId = idString?.let { UUID.fromString(it) }

            RefreshDetailsOnResume(
                itemId,
                onResume = { itemId?.let { viewModel.processIntent(VaultIntent.DecryptItem(it)) } },
                onPause = { viewModel.processIntent(VaultIntent.ClearSelectedItem) },
            )
            val payload = state.selectedPasswordPayload
            if (payload != null && itemId != null && state.selectedItem?.id == itemId) {
                PasswordDetailScreen(
                    payload = payload,
                    onEdit = {
                        editingLabelIds = state.selectedItemLabelIds
                        editingPasswordPayload = Pair(itemId, payload.copyForEditing())
                        navController.navigate(VaultDestinations.ENTRY_PASSWORD_ROUTE)
                    },
                    onDelete = {
                        viewModel.processIntent(VaultIntent.DeleteItem(itemId))
                    },
                    onBack = {
                        viewModel.processIntent(VaultIntent.ClearSelectedItem)
                        navController.popBackStack()
                    },
                    onCopyPassword = {
                        viewModel.processIntent(VaultIntent.CopyPassword(it))
                    },
                    copyMessage = state.copyMessage,
                    onCopyMessage = { viewModel.processIntent(VaultIntent.ClearCopyMessage) },
                )
            } else if (state.errorMessage != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.errorMessage ?: "Unable to open item")
                        androidx.compose.material3.TextButton(
                            onClick = {
                                itemId?.let { viewModel.processIntent(VaultIntent.DecryptItem(it)) }
                            },
                        ) {
                            Text("Retry")
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        // Add / Edit Credit Card
        composable(VaultDestinations.ENTRY_CARD_ROUTE) {
            DisposableEffect(Unit) {
                onDispose {
                    clearEditingPayloadCopies(null, editingCreditCardPayload?.second, null)
                    viewModel.processIntent(VaultIntent.AbandonMutation)
                }
            }
            CreditCardEntryScreen(
                saveSucceeded = state.mutationState == com.chimali.feature.vault.api.VaultMutationState.SUCCEEDED,
                initialPayload = editingCreditCardPayload?.second,
                isSaving = state.mutationState == com.chimali.feature.vault.api.VaultMutationState.PENDING,
                errorMessage = state.errorMessage,
                labels = state.labels,
                initialLabelIds = if (editingCreditCardPayload != null) editingLabelIds else emptySet(),
                onSave = {},
                onSaveWithLabels = { payload, labelIds ->
                    val editingId = editingCreditCardPayload?.first
                    viewModel.processIntent(
                        VaultIntent.SaveCreditCard(id = editingId, payload = payload, labelIds = labelIds),
                    )
                },
                onCancel = {
                    viewModel.processIntent(VaultIntent.AbandonMutation)
                    editingCreditCardPayload?.second?.clearMemory()
                    editingCreditCardPayload = null
                    navController.popBackStack()
                },
            )
        }

        // Credit Card Detail
        composable(
            route = VaultDestinations.DETAIL_CARD_ROUTE,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { backStackEntry ->
            val idString = backStackEntry.arguments?.getString("id")
            val itemId = idString?.let { UUID.fromString(it) }

            RefreshDetailsOnResume(
                itemId,
                onResume = { itemId?.let { viewModel.processIntent(VaultIntent.DecryptItem(it)) } },
                onPause = { viewModel.processIntent(VaultIntent.ClearSelectedItem) },
            )
            val payload = state.selectedCreditCardPayload
            if (payload != null && itemId != null && state.selectedItem?.id == itemId) {
                CreditCardDetailScreen(
                    payload = payload,
                    onEdit = {
                        editingLabelIds = state.selectedItemLabelIds
                        editingCreditCardPayload = Pair(itemId, payload.copyForEditing())
                        navController.navigate(VaultDestinations.ENTRY_CARD_ROUTE)
                    },
                    onDelete = {
                        viewModel.processIntent(VaultIntent.DeleteItem(itemId))
                    },
                    onBack = {
                        viewModel.processIntent(VaultIntent.ClearSelectedItem)
                        navController.popBackStack()
                    },
                )
            } else if (state.errorMessage != null) {
                DetailErrorState(
                    message = state.errorMessage ?: "Unable to open item",
                    onRetry = { itemId?.let { viewModel.processIntent(VaultIntent.DecryptItem(it)) } },
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        // Add / Edit Secure Note
        composable(VaultDestinations.ENTRY_NOTE_ROUTE) {
            DisposableEffect(Unit) {
                onDispose {
                    clearEditingPayloadCopies(null, null, editingSecureNotePayload?.second)
                    viewModel.processIntent(VaultIntent.AbandonMutation)
                }
            }
            SecureNoteEntryScreen(
                saveSucceeded = state.mutationState == com.chimali.feature.vault.api.VaultMutationState.SUCCEEDED,
                initialPayload = editingSecureNotePayload?.second,
                isSaving = state.mutationState == com.chimali.feature.vault.api.VaultMutationState.PENDING,
                errorMessage = state.errorMessage,
                labels = state.labels,
                initialLabelIds = if (editingSecureNotePayload != null) editingLabelIds else emptySet(),
                onSave = {},
                onSaveWithLabels = { payload, labelIds ->
                    val editingId = editingSecureNotePayload?.first
                    viewModel.processIntent(
                        VaultIntent.SaveSecureNote(id = editingId, payload = payload, labelIds = labelIds),
                    )
                },
                onCancel = {
                    viewModel.processIntent(VaultIntent.AbandonMutation)
                    editingSecureNotePayload?.second?.clearMemory()
                    editingSecureNotePayload = null
                    navController.popBackStack()
                },
            )
        }

        // Secure Note Detail
        composable(
            route = VaultDestinations.DETAIL_NOTE_ROUTE,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { backStackEntry ->
            val idString = backStackEntry.arguments?.getString("id")
            val itemId = idString?.let { UUID.fromString(it) }

            RefreshDetailsOnResume(
                itemId,
                onResume = { itemId?.let { viewModel.processIntent(VaultIntent.DecryptItem(it)) } },
                onPause = { viewModel.processIntent(VaultIntent.ClearSelectedItem) },
            )
            val payload = state.selectedSecureNotePayload
            if (payload != null && itemId != null && state.selectedItem?.id == itemId) {
                SecureNoteDetailScreen(
                    payload = payload,
                    onEdit = {
                        editingLabelIds = state.selectedItemLabelIds
                        editingSecureNotePayload = Pair(itemId, payload.copyForEditing())
                        navController.navigate(VaultDestinations.ENTRY_NOTE_ROUTE)
                    },
                    onDelete = {
                        viewModel.processIntent(VaultIntent.DeleteItem(itemId))
                    },
                    onBack = {
                        viewModel.processIntent(VaultIntent.ClearSelectedItem)
                        navController.popBackStack()
                    },
                )
            } else if (state.errorMessage != null) {
                DetailErrorState(
                    message = state.errorMessage ?: "Unable to open item",
                    onRetry = { itemId?.let { viewModel.processIntent(VaultIntent.DecryptItem(it)) } },
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        // Label Manager
        composable(VaultDestinations.LABELS_ROUTE) {
            LabelManagerScreen(
                labels = state.labels,
                onCreateLabel = { name, colorHex ->
                    viewModel.processIntent(VaultIntent.CreateLabel(name, colorHex))
                },
                onDeleteLabel = { labelId ->
                    viewModel.processIntent(VaultIntent.DeleteLabel(labelId))
                },
                onBack = {
                    navController.popBackStack()
                },
                errorMessage = state.errorMessage,
            )
        }
    }
}

@Composable
private fun ObserveVaultBackground(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    navController: NavHostController,
    onStop: () -> Unit,
    onStart: () -> Unit,
) {
    val stop by rememberUpdatedState(onStop)
    val start by rememberUpdatedState(onStart)
    DisposableEffect(lifecycleOwner, navController) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> stop()
                    Lifecycle.Event.ON_START -> start()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

private fun clearVaultBackgroundOwners(
    viewModel: VaultViewModel,
    navController: NavHostController,
    listClearedForBackground: MutableState<Boolean>,
    showAddTypeSheet: MutableState<Boolean>,
    editingPasswordPayload: MutableState<Pair<UUID, PasswordPayload>?>,
    editingCreditCardPayload: MutableState<Pair<UUID, CreditCardPayload>?>,
    editingSecureNotePayload: MutableState<Pair<UUID, SecureNotePayload>?>,
    editingLabelIds: MutableState<Set<UUID>>,
) {
    listClearedForBackground.value = true
    viewModel.processIntent(VaultIntent.ClearListData)
    viewModel.processIntent(VaultIntent.ClearSelectedItem)
    viewModel.processIntent(VaultIntent.AbandonMutation)
    clearEditingPayloadCopies(
        editingPasswordPayload.value?.second,
        editingCreditCardPayload.value?.second,
        editingSecureNotePayload.value?.second,
    )
    editingPasswordPayload.value = null
    editingCreditCardPayload.value = null
    editingSecureNotePayload.value = null
    editingLabelIds.value = emptySet()
    showAddTypeSheet.value = false
    if (navController.currentBackStackEntry?.destination?.route != VaultDestinations.LIST_ROUTE) {
        navController.popBackStack(VaultDestinations.LIST_ROUTE, false)
    }
}

@Composable
private fun DetailErrorState(
    message: String,
    onRetry: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message)
            androidx.compose.material3.TextButton(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

/** FR-VAULT-026: a resumed detail session obtains fresh values, never a previously erased payload. */
@Composable
private fun RefreshDetailsOnResume(
    itemId: UUID?,
    onResume: () -> Unit,
    onPause: () -> Unit,
) {
    val owner = LocalLifecycleOwner.current
    val resume by rememberUpdatedState(onResume)
    val pause by rememberUpdatedState(onPause)
    DisposableEffect(owner, itemId) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    resume()
                } else if (event == Lifecycle.Event.ON_PAUSE) {
                    pause()
                }
            }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}
