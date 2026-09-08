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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    val state by viewModel.state.collectAsState()
    var showAddTypeSheet by remember { mutableStateOf(false) }

    var editingPasswordPayload by remember { mutableStateOf<Pair<UUID, PasswordPayload>?>(null) }
    var editingCreditCardPayload by remember { mutableStateOf<Pair<UUID, CreditCardPayload>?>(null) }
    var editingSecureNotePayload by remember { mutableStateOf<Pair<UUID, SecureNotePayload>?>(null) }

    LaunchedEffect(Unit) {
        viewModel.processIntent(VaultIntent.LoadItems())
        viewModel.processIntent(VaultIntent.LoadLabels)
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
                    viewModel.processIntent(VaultIntent.DecryptItem(item.id))
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
            PasswordEntryScreen(
                initialPayload = editingPasswordPayload?.second,
                onSave = { payload ->
                    val editingId = editingPasswordPayload?.first
                    viewModel.processIntent(VaultIntent.SavePassword(id = editingId, payload = payload))
                    editingPasswordPayload = null
                    navController.popBackStack(VaultDestinations.LIST_ROUTE, false)
                },
                onCancel = {
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

            val payload = state.selectedPasswordPayload
            if (payload != null && itemId != null) {
                PasswordDetailScreen(
                    payload = payload,
                    onEdit = {
                        editingPasswordPayload = Pair(itemId, payload)
                        navController.navigate(VaultDestinations.ENTRY_PASSWORD_ROUTE)
                    },
                    onDelete = {
                        viewModel.processIntent(VaultIntent.DeleteItem(itemId))
                        navController.popBackStack(VaultDestinations.LIST_ROUTE, false)
                    },
                    onBack = {
                        viewModel.processIntent(VaultIntent.ClearSelectedItem)
                        navController.popBackStack()
                    },
                    onCopyPassword = {
                        viewModel.processIntent(VaultIntent.ClearClipboard) // Trigger copy through clipboard service
                    },
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        // Add / Edit Credit Card
        composable(VaultDestinations.ENTRY_CARD_ROUTE) {
            CreditCardEntryScreen(
                initialPayload = editingCreditCardPayload?.second,
                onSave = { payload ->
                    val editingId = editingCreditCardPayload?.first
                    viewModel.processIntent(VaultIntent.SaveCreditCard(id = editingId, payload = payload))
                    editingCreditCardPayload = null
                    navController.popBackStack(VaultDestinations.LIST_ROUTE, false)
                },
                onCancel = {
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

            val payload = state.selectedCreditCardPayload
            if (payload != null && itemId != null) {
                CreditCardDetailScreen(
                    payload = payload,
                    onEdit = {
                        editingCreditCardPayload = Pair(itemId, payload)
                        navController.navigate(VaultDestinations.ENTRY_CARD_ROUTE)
                    },
                    onDelete = {
                        viewModel.processIntent(VaultIntent.DeleteItem(itemId))
                        navController.popBackStack(VaultDestinations.LIST_ROUTE, false)
                    },
                    onBack = {
                        viewModel.processIntent(VaultIntent.ClearSelectedItem)
                        navController.popBackStack()
                    },
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        // Add / Edit Secure Note
        composable(VaultDestinations.ENTRY_NOTE_ROUTE) {
            SecureNoteEntryScreen(
                initialPayload = editingSecureNotePayload?.second,
                onSave = { payload ->
                    val editingId = editingSecureNotePayload?.first
                    viewModel.processIntent(VaultIntent.SaveSecureNote(id = editingId, payload = payload))
                    editingSecureNotePayload = null
                    navController.popBackStack(VaultDestinations.LIST_ROUTE, false)
                },
                onCancel = {
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

            val payload = state.selectedSecureNotePayload
            if (payload != null && itemId != null) {
                SecureNoteDetailScreen(
                    payload = payload,
                    onEdit = {
                        editingSecureNotePayload = Pair(itemId, payload)
                        navController.navigate(VaultDestinations.ENTRY_NOTE_ROUTE)
                    },
                    onDelete = {
                        viewModel.processIntent(VaultIntent.DeleteItem(itemId))
                        navController.popBackStack(VaultDestinations.LIST_ROUTE, false)
                    },
                    onBack = {
                        viewModel.processIntent(VaultIntent.ClearSelectedItem)
                        navController.popBackStack()
                    },
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
            )
        }
    }
}
