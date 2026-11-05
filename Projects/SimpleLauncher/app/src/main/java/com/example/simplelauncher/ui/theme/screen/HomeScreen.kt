package com.example.simplelauncher.ui.theme.screen

import android.content.Intent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.simplelauncher.data.AppInfo
import androidx.compose.runtime.collectAsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeScreenViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    var showAppsSheet by remember { mutableStateOf(false) }
    var pendingRemoveApp by remember { mutableStateOf<AppInfo?>(null) }

    LaunchedEffect(Unit) { /* initial load already triggered in VM */ }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Home") },
                actions = {
                    // "Add app" button opens the Apps sheet.
                    androidx.compose.material3.TextButton(onClick = { showAppsSheet = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.height(4.dp))
                        Text("Add app")
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading apps…")
            }
        } else if (state.homeApps.isEmpty()) {
            // Empty state: prompt to add apps.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No home apps yet", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { showAppsSheet = true }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.height(4.dp))
                    Text("Add your first app")
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 108.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.homeApps, key = { it.packageName }) { app ->
                    HomeTile(
                        label = app.label,
                        onClick = { launchApp(context, app) },
                        onLongClick = { pendingRemoveApp = app },
                    )
                }
            }
        }
    }

    // Remove confirmation dialog.
    val removeTarget = pendingRemoveApp
    if (removeTarget != null) {
        AlertDialog(
            onDismissRequest = { pendingRemoveApp = null },
            title = { Text("Remove from Home") },
            text = { Text("\"${removeTarget.label}\" will be removed from your home screen.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.removeApp(removeTarget.packageName)
                    pendingRemoveApp = null
                }) { Text("Remove") }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingRemoveApp = null }) { Text("Cancel") }
            },
        )
    }

    // Apps sheet (add from here).
    if (showAppsSheet) {
        AppsBottomSheet(
            allApps = state.allApps,
            homePackageNames = state.homeApps.map { it.packageName }.toSet(),
            onAdd = viewModel::addApp,
            onDismiss = { showAppsSheet = false },
        )
    }
}

@Composable
private fun HomeTile(
    label: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .background(
                MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    },
                    onTap = {
                        onClick()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}


// ---- helper: launch an app by package name ----
private fun launchApp(context: android.content.Context, app: AppInfo) {
    val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
    if (intent != null) {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

// ---- Apps bottom sheet ----
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppsBottomSheet(
    allApps: List<AppInfo>,
    homePackageNames: Set<String>,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(Modifier.padding(bottom = 24.dp)) {
            // Search field (simple TextField).
            androidx.compose.foundation.layout.Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Search apps") },
                    singleLine = true,
                )
            }

            Spacer(Modifier.height(8.dp))

            val filtered = if (query.isBlank()) allApps
            else allApps.filter { it.label.contains(query, ignoreCase = true) }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                items(filtered.size) { index ->
                    val app = filtered[index]
                    val isHome = homePackageNames.contains(app.packageName)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = app.label,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        if (isHome) {
                            Text("On Home", color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelSmall)
                        } else {
                            Button(onClick = { onAdd(app.packageName) }) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.height(4.dp))
                                Text("Add")
                            }
                        }
                    }
                }
            }
        }
    }
}
