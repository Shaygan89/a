package com.example.epiclauncher

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.epiclauncher.ui.theme.EpicLauncherTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


data class LauncherApp(
    val packageName: String,
    val activityName: String,
    val label: String,
    val icon: android.graphics.drawable.Drawable
)


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {

            EpicLauncherTheme {

                EpicLauncherApp()
            }
        }
    }
}


@Composable
fun EpicLauncherApp() {

    val context = LocalContext.current

    var showAppPicker by remember {
        mutableStateOf(false)
    }

    var launchingApp by remember {
        mutableStateOf(false)
    }

    val favoritePackages =
        remember {

            mutableStateListOf<String>().apply {

                addAll(
                    FavoritesStore.load(context)
                )
            }
        }

    val installedApps =
        remember {

            getLaunchableApps(context)
        }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {

        Box(
            modifier = Modifier.fillMaxSize()
        ) {

            HomeScreen(
                favoritePackages = favoritePackages,
                installedApps = installedApps,

                onAddApp = {
                    showAppPicker = true
                },

                onLaunchApp = { app ->

                    launchingApp = true

                    android.os.Handler(
                        android.os.Looper.getMainLooper()
                    ).postDelayed({

                        launchApplication(
                            context,
                            app
                        )

                        launchingApp = false

                    }, 180)
                }
            )

            if (showAppPicker) {

                AppPicker(
                    apps = installedApps,
                    favoritePackages = favoritePackages,

                    onAdd = { app ->

                        if (
                            !favoritePackages.contains(
                                app.packageName
                            )
                        ) {

                            favoritePackages.add(
                                app.packageName
                            )

                            FavoritesStore.save(
                                context,
                                favoritePackages
                            )
                        }
                    },

                    onRemove = { app ->

                        favoritePackages.remove(
                            app.packageName
                        )

                        FavoritesStore.save(
                            context,
                            favoritePackages
                        )
                    },

                    onClose = {
                        showAppPicker = false
                    }
                )
            }

            if (launchingApp) {

                LaunchTransition()
            }
        }
    }
}


@Composable
fun HomeScreen(
    favoritePackages: List<String>,
    installedApps: List<LauncherApp>,
    onAddApp: () -> Unit,
    onLaunchApp: (LauncherApp) -> Unit
) {

    var currentTime by remember {

        mutableStateOf(
            SimpleDateFormat(
                "HH:mm",
                Locale.getDefault()
            ).format(Date())
        )
    }

    LaunchedEffect(Unit) {

        while (true) {

            currentTime =
                SimpleDateFormat(
                    "HH:mm",
                    Locale.getDefault()
                ).format(Date())

            delay(1000)
        }
    }

    val favoriteApps =
        installedApps.filter {

            favoritePackages.contains(
                it.packageName
            )
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                horizontal = 28.dp,
                vertical = 40.dp
            ),

        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        BatteryAndTime(
            time = currentTime
        )

        Spacer(
            modifier = Modifier.height(70.dp)
        )

        Column(
            modifier = Modifier.fillMaxWidth(),

            verticalArrangement =
                Arrangement.spacedBy(18.dp)
        ) {

            favoriteApps.forEachIndexed { index, app ->

                AnimatedLauncherApp(
                    app = app,
                    index = index,

                    onClick = {
                        onLaunchApp(app)
                    }
                )
            }

            AddApplicationButton(
                onClick = onAddApp
            )
        }
    }
}


@Composable
fun BatteryAndTime(
    time: String
) {

    val context = LocalContext.current

    var battery by remember {

        mutableIntStateOf(
            getBatteryLevel(context)
        )
    }

    DisposableEffect(Unit) {

        val receiver =
            object : BroadcastReceiver() {

                override fun onReceive(
                    context: Context?,
                    intent: Intent?
                ) {

                    val level =
                        intent?.getIntExtra(
                            BatteryManager.EXTRA_LEVEL,
                            -1
                        ) ?: -1

                    val scale =
                        intent?.getIntExtra(
                            BatteryManager.EXTRA_SCALE,
                            100
                        ) ?: 100

                    if (level >= 0) {

                        battery =
                            level * 100 / scale
                    }
                }
            }

        context.registerReceiver(
            receiver,
            IntentFilter(
                Intent.ACTION_BATTERY_CHANGED
            )
        )

        onDispose {

            context.unregisterReceiver(
                receiver
            )
        }
    }

    Column(
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        Text(
            text = time,

            fontSize = 72.sp,

            fontWeight =
                FontWeight.Light,

            letterSpacing = (-3).sp,

            color =
                MaterialTheme
                    .colorScheme
                    .onBackground
        )

        Spacer(
            modifier = Modifier.height(6.dp)
        )

        Text(
            text = "$battery%",

            fontSize = 17.sp,

            fontWeight =
                FontWeight.Medium,

            color =
                MaterialTheme
                    .colorScheme
                    .onBackground
                    .copy(alpha = 0.55f)
        )
    }
}


@Composable
fun AnimatedLauncherApp(
    app: LauncherApp,
    index: Int,
    onClick: () -> Unit
) {

    var visible by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(Unit) {

        delay(
            100L + index * 70L
        )

        visible = true
    }

    var pressed by remember {
        mutableStateOf(false)
    }

    val scale by animateFloatAsState(

        targetValue =
            if (pressed) 0.92f else 1f,

        animationSpec =
            tween(120),

        label = "appScale"
    )

    val blurAmount by animateFloatAsState(

        targetValue =
            if (visible) 0f else 18f,

        animationSpec =
            tween(
                durationMillis = 500,
                easing = FastOutSlowInEasing
            ),

        label = "appBlur"
    )

    val animatedScale by animateFloatAsState(

        targetValue =
            if (visible) 1f else 0.55f,

        animationSpec =
            tween(
                durationMillis = 500,
                easing = FastOutSlowInEasing
            ),

        label = "appFarScale"
    )

    val alpha by animateFloatAsState(

        targetValue =
            if (visible) 1f else 0f,

        animationSpec =
            tween(350),

        label = "appAlpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()

            .graphicsLayer {

                scaleX =
                    animatedScale * scale

                scaleY =
                    animatedScale * scale

                this.alpha = alpha
            }

            .blur(
                radius = blurAmount.dp,

                edgeTreatment =
                    BlurredEdgeTreatment.Unbounded
            )

            .clickable {

                pressed = true

                onClick()
            }

            .padding(
                vertical = 5.dp
            ),

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        AppIcon(
            app = app
        )

        Spacer(
            modifier = Modifier.size(18.dp)
        )

        Text(
            text = app.label,

            fontSize = 23.sp,

            fontWeight =
                FontWeight.Normal,

            color =
                MaterialTheme
                    .colorScheme
                    .onBackground
        )
    }
}


@Composable
fun AppIcon(
    app: LauncherApp
) {

    Image(

        bitmap =
            app.icon
                .toBitmap()
                .asImageBitmap(),

        contentDescription =
            app.label,

        modifier =
            Modifier.size(42.dp)
    )
}


@Composable
fun AddApplicationButton(
    onClick: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onClick()
            }
            .padding(
                vertical = 8.dp
            ),

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Icon(
            imageVector =
                Icons.Rounded.Add,

            contentDescription =
                "Add app",

            modifier =
                Modifier.size(40.dp),

            tint =
                MaterialTheme
                    .colorScheme
                    .onBackground
                    .copy(alpha = 0.35f)
        )

        Spacer(
            modifier =
                Modifier.size(18.dp)
        )

        Text(
            text = "Add application",

            fontSize = 20.sp,

            color =
                MaterialTheme
                    .colorScheme
                    .onBackground
                    .copy(alpha = 0.40f)
        )
    }
}


@Composable
fun AppPicker(
    apps: List<LauncherApp>,
    favoritePackages: List<String>,
    onAdd: (LauncherApp) -> Unit,
    onRemove: (LauncherApp) -> Unit,
    onClose: () -> Unit
) {

    Surface(
        modifier = Modifier.fillMaxSize(),

        color =
            MaterialTheme
                .colorScheme
                .background
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text(
                        text = "Applications",

                        fontSize = 32.sp,

                        fontWeight =
                            FontWeight.Medium
                    )

                    Text(
                        text =
                            "${favoritePackages.size} selected",

                        color =
                            MaterialTheme
                                .colorScheme
                                .onBackground
                                .copy(alpha = 0.5f)
                    )
                }

                IconButton(
                    onClick = onClose
                ) {

                    Icon(
                        imageVector =
                            Icons.Rounded.Close,

                        contentDescription =
                            "Close"
                    )
                }
            }

            Spacer(
                modifier =
                    Modifier.height(20.dp)
            )

            LazyColumn(
                verticalArrangement =
                    Arrangement.spacedBy(4.dp)
            ) {

                items(
                    items = apps,

                    key = {
                        it.packageName
                    }
                ) { app ->

                    val selected =
                        favoritePackages.contains(
                            app.packageName
                        )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {

                                if (selected) {
                                    onRemove(app)
                                } else {
                                    onAdd(app)
                                }
                            }
                            .padding(
                                vertical = 10.dp
                            ),

                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        AppIcon(app)

                        Spacer(
                            modifier =
                                Modifier.size(16.dp)
                        )

                        Text(
                            text = app.label,

                            modifier =
                                Modifier.weight(1f),

                            fontSize = 18.sp
                        )

                        TextButton(
                            onClick = {

                                if (selected) {
                                    onRemove(app)
                                } else {
                                    onAdd(app)
                                }
                            }
                        ) {

                            Text(
                                if (selected)
                                    "Remove"
                                else
                                    "Add"
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun LaunchTransition() {

    Box(
        modifier = Modifier
            .fillMaxSize()
            .blur(
                radius = 20.dp,

                edgeTreatment =
                    BlurredEdgeTreatment.Unbounded
            ),

        contentAlignment =
            Alignment.Center
    ) {

        Surface(
            modifier =
                Modifier.fillMaxSize(),

            color =
                MaterialTheme
                    .colorScheme
                    .background
                    .copy(alpha = 0.18f)
        ) {}
    }
}


fun getBatteryLevel(
    context: Context
): Int {

    val intent =
        context.registerReceiver(
            null,

            IntentFilter(
                Intent.ACTION_BATTERY_CHANGED
            )
        )

    val level =
        intent?.getIntExtra(
            BatteryManager.EXTRA_LEVEL,
            0
        ) ?: 0

    val scale =
        intent?.getIntExtra(
            BatteryManager.EXTRA_SCALE,
            100
        ) ?: 100

    return level * 100 / scale
}


fun getLaunchableApps(
    context: Context
): List<LauncherApp> {

    val packageManager =
        context.packageManager

    val intent =
        Intent(
            Intent.ACTION_MAIN
        ).apply {

            addCategory(
                Intent.CATEGORY_LAUNCHER
            )
        }

    val results =
        packageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_ALL
        )

    return results
        .mapNotNull { resolveInfo ->

            val activityInfo =
                resolveInfo.activityInfo
                    ?: return@mapNotNull null

            LauncherApp(

                packageName =
                    activityInfo.packageName,

                activityName =
                    activityInfo.name,

                label =
                    resolveInfo
                        .loadLabel(
                            packageManager
                        )
                        .toString(),

                icon =
                    resolveInfo
                        .loadIcon(
                            packageManager
                        )
            )
        }
        .distinctBy {
            it.packageName
        }
        .sortedBy {
            it.label.lowercase()
        }
}


fun launchApplication(
    context: Context,
    app: LauncherApp
) {

    val intent =
        Intent().apply {

            component =
                ComponentName(
                    app.packageName,
                    app.activityName
                )

            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
            )
        }

    try {

        context.startActivity(intent)

    } catch (e: Exception) {

        e.printStackTrace()
    }
}