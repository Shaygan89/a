package com.example.mylauncher

import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

class MainActivity : ComponentActivity() {
    private val viewModel: LauncherViewModel by viewModels()
    private var resumeEpoch by mutableIntStateOf(0)
    private var requestedHomeRoleThisRun = false

    private val homeRoleRequest = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshApps()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyLauncherTheme {
                LauncherApp(
                    viewModel = viewModel,
                    resumeEpoch = resumeEpoch,
                    onOpenApp = ::openApp,
                    onOpenAppInfo = ::openAppInfo,
                    onUninstallApp = ::uninstallApp,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeEpoch += 1
        viewModel.refreshApps()

        val roleManager = getSystemService(RoleManager::class.java)
        val homeRoleAvailable = roleManager.isRoleAvailable(RoleManager.ROLE_HOME)
        val alreadyDefaultHome = roleManager.isRoleHeld(RoleManager.ROLE_HOME)

        if (homeRoleAvailable && !alreadyDefaultHome && !requestedHomeRoleThisRun) {
            requestedHomeRoleThisRun = true
            homeRoleRequest.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
        }
    }

    private fun openApp(app: AppInfo) {
        val launchIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = android.content.ComponentName(app.packageName, app.activityName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { startActivity(launchIntent) }
    }

    private fun openAppInfo(app: AppInfo) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${app.packageName}"),
        )
        runCatching { startActivity(intent) }
    }

    private fun uninstallApp(app: AppInfo) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
        runCatching { startActivity(intent) }
    }

}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LauncherApp(
    viewModel: LauncherViewModel,
    resumeEpoch: Int,
    onOpenApp: (AppInfo) -> Unit,
    onOpenAppInfo: (AppInfo) -> Unit,
    onUninstallApp: (AppInfo) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }
    var selectedAppIsOnHome by remember { mutableStateOf(false) }

    var entering by remember { mutableStateOf(true) }

    LaunchedEffect(resumeEpoch) {
        entering = true
        delay(260)
        entering = false
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                if (page == 1) viewModel.refreshApps()
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        AnimatedContent(
            targetState = uiState.isLoading,
            transitionSpec = {
                fadeIn(tween(160)) togetherWith fadeOut(tween(120))
            },
            label = "launcher-loading",
        ) { loading ->
            if (loading) {
                LoadingScreen()
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                    userScrollEnabled = true,
                    verticalAlignment = Alignment.Top,
                    pageSpacing = 0.dp,
                    key = { it },
                ) { page ->
                    when (page) {
                        0 -> HomePage(
                            homeApps = uiState.allApps.filter { it.packageName in uiState.homePackages },
                            onOpenApp = onOpenApp,
                            onLongPressApp = {
                                selectedApp = it
                                selectedAppIsOnHome = true
                            },
                        )

                        else -> AppsPage(
                            apps = uiState.allApps,
                            onOpenApp = onOpenApp,
                            onLongPressApp = {
                                selectedApp = it
                                selectedAppIsOnHome = it.packageName in uiState.homePackages
                            },
                        )
                    }
                }
            }
        }

        // A lightweight page indicator. No icons, no launcher chrome.
        PageIndicator(
            currentPage = pagerState.currentPage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 10.dp),
        )

        AnimatedVisibility(
            visible = entering,
            enter = fadeIn(tween(280)) + scaleIn(initialScale = 0.97f, animationSpec = tween(320)),
            exit = fadeOut(tween(220)) + scaleOut(targetScale = 1.01f, animationSpec = tween(220)),
            modifier = Modifier.fillMaxSize(),
        ) {
            // The launcher begins with this transparent scrim so the first frame feels intentional.
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {}
        }
    }

    if (selectedApp != null) {
        AppActionSheet(
            app = selectedApp!!,
            isOnHome = selectedAppIsOnHome,
            onDismiss = { selectedApp = null },
            onOpen = {
                selectedApp?.let(onOpenApp)
                selectedApp = null
            },
            onAdd = {
                selectedApp?.let { viewModel.addToHome(it.packageName) }
                selectedApp = null
            },
            onRemove = {
                selectedApp?.let { viewModel.removeFromHome(it.packageName) }
                selectedApp = null
            },
            onInfo = {
                selectedApp?.let(onOpenAppInfo)
                selectedApp = null
            },
            onUninstall = {
                selectedApp?.let(onUninstallApp)
                selectedApp = null
            },
        )
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Loading",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HomePage(
    homeApps: List<AppInfo>,
    onOpenApp: (AppInfo) -> Unit,
    onLongPressApp: (AppInfo) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        ChatGPTPanel(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        Spacer(Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            Text(
                text = "HOME APPS",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))

            if (homeApps.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "No apps selected",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "Swipe left to choose apps for HOME.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                AppNameList(
                    modifier = Modifier.weight(1f),
                    apps = homeApps,
                    onOpenApp = onOpenApp,
                    onLongPressApp = onLongPressApp,
                )
            }
        }
    }
}

@Composable
private fun ChatGPTPanel(
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(32.dp),
        tonalElevation = 3.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        ChatGPTWebView(modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun AppsPage(
    apps: List<AppInfo>,
    onOpenApp: (AppInfo) -> Unit,
    onLongPressApp: (AppInfo) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Text(
            text = "ALL APPS",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Swipe right for HOME. Hold an app name for actions.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        Spacer(Modifier.height(4.dp))

        AppNameList(
            modifier = Modifier.weight(1f),
            apps = apps,
            onOpenApp = onOpenApp,
            onLongPressApp = onLongPressApp,
        )
    }
}

@Composable
private fun AppNameList(
    modifier: Modifier = Modifier,
    apps: List<AppInfo>,
    onOpenApp: (AppInfo) -> Unit,
    onLongPressApp: (AppInfo) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 36.dp),
    ) {
        items(
            items = apps,
            key = { it.packageName },
        ) { app ->
            AppNameRow(
                app = app,
                onClick = { onOpenApp(app) },
                onLongClick = { onLongPressApp(app) },
            )
        }
    }
}

@Composable
private fun AppNameRow(
    app: AppInfo,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(0.985f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PageIndicator(
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(100.dp),
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IndicatorPill(active = currentPage == 0, label = "HOME")
            IndicatorPill(active = currentPage == 1, label = "APPS")
        }
    }
}

@Composable
private fun IndicatorPill(
    active: Boolean,
    label: String,
) {
    Surface(
        shape = RoundedCornerShape(100.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppActionSheet(
    app: AppInfo,
    isOnHome: Boolean,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onInfo: () -> Unit,
    onUninstall: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
            )

            ActionTextButton("OPEN", onOpen)
            if (isOnHome) {
                ActionTextButton("REMOVE FROM HOME", onRemove)
            } else {
                ActionTextButton("ADD TO HOME", onAdd)
            }
            ActionTextButton("APP INFO", onInfo)
            ActionTextButton("UNINSTALL", onUninstall)
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 15.dp),
            ) {
                Text("CANCEL")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ActionTextButton(
    text: String,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 15.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
