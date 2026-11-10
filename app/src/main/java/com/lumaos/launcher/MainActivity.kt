package com.lumaos.launcher

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class LauncherApp(
    val label: String,
    val packageName: String,
    val icon: ImageBitmap
)

class MainActivity : ComponentActivity() {
    private var launcherApps by mutableStateOf<List<LauncherApp>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshInstalledApps()
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.rgb(8, 12, 24)
        window.decorView.systemUiVisibility =
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

        setContent {
            MaterialTheme {
                LumaLauncher(
                    apps = launcherApps,
                    onLaunchApp = ::launchApp
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshInstalledApps()
    }

    private fun refreshInstalledApps() {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        launcherApps = packageManager.queryIntentActivities(launcherIntent, 0)
            .mapNotNull { info ->
                val activityInfo = info.activityInfo ?: return@mapNotNull null
                val packageName = activityInfo.packageName
                LauncherApp(
                    label = info.loadLabel(packageManager)?.toString() ?: packageName,
                    packageName = packageName,
                    icon = info.loadIcon(packageManager).toImageBitmap()
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    private fun launchApp(packageName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launchIntent)
    }
}

private fun Drawable.toImageBitmap(): ImageBitmap {
    val width = intrinsicWidth.coerceAtLeast(1).coerceAtMost(192)
    val height = intrinsicHeight.coerceAtLeast(1).coerceAtMost(192)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    setBounds(0, 0, width, height)
    draw(canvas)
    return bitmap.asImageBitmap()
}

@Composable
private fun LumaLauncher(
    apps: List<LauncherApp>,
    onLaunchApp: (String) -> Unit
) {
    var showLibrary by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var clock by remember { mutableStateOf(currentTime()) }
    val background = Brush.verticalGradient(
        colors = listOf(Color(0xFF26375D), Color(0xFF10182D), Color(0xFF070A12))
    )

    LaunchedEffect(Unit) {
        while (true) {
            clock = currentTime()
            delay(15_000)
        }
    }

    BackHandler(enabled = showLibrary) {
        showLibrary = false
        searchQuery = ""
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .pointerInput(showLibrary) {
                var drag = 0f
                detectVerticalDragGestures(
                    onVerticalDrag = { _, amount -> drag += amount },
                    onDragEnd = {
                        if (!showLibrary && drag < -65f) showLibrary = true
                        if (showLibrary && drag > 65f) showLibrary = false
                        drag = 0f
                    }
                )
            }
            .padding(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 18.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 12.dp
            )
            .padding(horizontal = 22.dp)
    ) {
        AnimatedContent(
            targetState = showLibrary,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "launcher_pages"
        ) { libraryOpen ->
            if (libraryOpen) {
                AppLibrary(
                    apps = apps,
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onLaunchApp = onLaunchApp,
                    onBack = { showLibrary = false; searchQuery = "" }
                )
            } else {
                HomeScreen(
                    apps = apps,
                    clock = clock,
                    onLaunchApp = onLaunchApp,
                    onOpenLibrary = { showLibrary = true }
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    apps: List<LauncherApp>,
    clock: String,
    onLaunchApp: (String) -> Unit,
    onOpenLibrary: () -> Unit
) {
    val homeApps = apps.take(20)
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("LumaOS", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text("●  ●  ●  ▰", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
        }
        Spacer(Modifier.height(30.dp))
        Text(clock, color = Color.White, fontSize = 58.sp, fontWeight = FontWeight.Light)
        Text(
            SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()),
            color = Color(0xFFD4DCEE),
            fontSize = 14.sp
        )
        Spacer(Modifier.height(28.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            items(homeApps, key = { it.packageName }) { app ->
                AppIcon(app = app, onClick = { onLaunchApp(app.packageName) })
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White.copy(alpha = 0.13f))
                .padding(horizontal = 12.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            apps.take(3).forEach { app ->
                Image(
                    bitmap = app.icon,
                    contentDescription = app.label,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .clickable { onLaunchApp(app.packageName) }
                )
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(Color.White.copy(alpha = 0.15f))
                    .clickable(onClick = onOpenLibrary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Apps, contentDescription = "App Library", tint = Color.White)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Swipe up or tap the app grid to open your App Library",
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenLibrary),
            color = Color(0xFFBAC6DE),
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AppLibrary(
    apps: List<LauncherApp>,
    query: String,
    onQueryChange: (String) -> Unit,
    onLaunchApp: (String) -> Unit,
    onBack: () -> Unit
) {
    val filtered = apps.filter { it.label.contains(query, ignoreCase = true) }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("App Library", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Text("Done", color = Color(0xFFB9C9FF), modifier = Modifier.clickable(onClick = onBack))
        }
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.13f))
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = Color(0xFFDDE5F7))
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) Text("Search apps", color = Color(0xFFB3BED3))
                        inner()
                    }
                }
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "${filtered.size} applications",
            color = Color(0xFFB3BED3),
            fontSize = 12.sp
        )
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            items(filtered, key = { it.packageName }) { app ->
                AppIcon(app = app, onClick = { onLaunchApp(app.packageName) })
            }
        }
    }
}

@Composable
private fun AppIcon(app: LauncherApp, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Image(
            bitmap = app.icon,
            contentDescription = app.label,
            modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(17.dp))
        )
        Spacer(Modifier.height(6.dp))
        Text(
            app.label,
            color = Color.White,
            fontSize = 10.sp,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

private fun currentTime(): String =
    SimpleDateFormat("h:mm", Locale.getDefault()).format(Date())
