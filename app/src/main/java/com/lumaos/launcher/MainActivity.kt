package com.lumaos.launcher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.graphics.Canvas as AndroidCanvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class LauncherApp(
    val label: String,
    val packageName: String,
    val icon: ImageBitmap
)

private data class AppFolder(
    val id: String,
    val name: String,
    val packages: List<String>
)

private data class HomeTile(
    val id: String,
    val app: LauncherApp? = null,
    val folder: AppFolder? = null
)

class MainActivity : ComponentActivity() {
    private var launcherApps by mutableStateOf<List<LauncherApp>>(emptyList())
    private var homeOrder by mutableStateOf<List<String>>(emptyList())
    private var folders by mutableStateOf<List<AppFolder>>(emptyList())
    private val appWidgetHost by lazy { AppWidgetHost(this, 1024) }
    private val appWidgetManager by lazy { AppWidgetManager.getInstance(this) }
    private var widgetIds by mutableStateOf<List<Int>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadHomeLayout()
        widgetIds = getSharedPreferences("luma_widgets", MODE_PRIVATE).getString("ids", "")!!
            .split(",").mapNotNull { it.toIntOrNull() }
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
                    homeOrder = homeOrder,
                    folders = folders,
                    onLaunchApp = ::launchApp,
                    onSaveLayout = ::saveHomeOrder,
                    onSaveFolders = ::saveFolders,
                    onOpenSystemSettings = ::openSystemSettings,
                    widgetIds = widgetIds,
                    widgetHost = appWidgetHost,
                    widgetManager = appWidgetManager,
                    onAddWidget = ::pickWidget,
                    onRemoveWidget = ::removeWidget
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        runCatching { appWidgetHost.startListening() }
    }

    override fun onStop() {
        runCatching { appWidgetHost.stopListening() }
        super.onStop()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_WIDGET_PICK) {
            val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                ?: AppWidgetManager.INVALID_APPWIDGET_ID
            if (resultCode == RESULT_OK && id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val info = appWidgetManager.getAppWidgetInfo(id)
                if (info?.configure != null) {
                    val configure = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).setComponent(info.configure)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    startActivityForResult(configure, REQUEST_WIDGET_CONFIGURE)
                } else addWidgetId(id)
            } else if (id != AppWidgetManager.INVALID_APPWIDGET_ID) appWidgetHost.deleteAppWidgetId(id)
        } else if (requestCode == REQUEST_WIDGET_CONFIGURE) {
            val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                ?: AppWidgetManager.INVALID_APPWIDGET_ID
            if (resultCode == RESULT_OK && id != AppWidgetManager.INVALID_APPWIDGET_ID) addWidgetId(id)
            else if (id != AppWidgetManager.INVALID_APPWIDGET_ID) appWidgetHost.deleteAppWidgetId(id)
        }
    }

    private fun addWidgetId(id: Int) {
        widgetIds = (widgetIds + id).distinct()
        getSharedPreferences("luma_widgets", MODE_PRIVATE).edit()
            .putString("ids", widgetIds.joinToString(",")).apply()
    }

    private fun pickWidget() {
        val id = appWidgetHost.allocateAppWidgetId()
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        startActivityForResult(intent, REQUEST_WIDGET_PICK)
    }

    private fun removeWidget(id: Int) {
        widgetIds = widgetIds.filterNot { it == id }
        getSharedPreferences("luma_widgets", MODE_PRIVATE).edit()
            .putString("ids", widgetIds.joinToString(",")).apply()
        appWidgetHost.deleteAppWidgetId(id)
    }

    override fun onResume() {
        super.onResume()
        refreshInstalledApps()
    }

    private fun loadHomeLayout() {
        val prefs = getSharedPreferences("luma_home_layout", MODE_PRIVATE)
        homeOrder = prefs.getString("order", "")!!
            .split("|").filter { it.isNotBlank() }
        val saved = prefs.getString("folders", "[]") ?: "[]"
        folders = runCatching {
            val array = JSONArray(saved)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                val packages = item.getJSONArray("packages")
                AppFolder(
                    id = item.getString("id"),
                    name = item.getString("name"),
                    packages = (0 until packages.length()).map(packages::getString)
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun saveHomeOrder(order: List<String>) {
        homeOrder = order
        getSharedPreferences("luma_home_layout", MODE_PRIVATE)
            .edit().putString("order", order.joinToString("|")).apply()
    }

    private fun saveFolders(updated: List<AppFolder>) {
        folders = updated
        val array = JSONArray()
        updated.forEach { folder ->
            val item = JSONObject()
                .put("id", folder.id)
                .put("name", folder.name)
                .put("packages", JSONArray(folder.packages))
            array.put(item)
        }
        getSharedPreferences("luma_home_layout", MODE_PRIVATE)
            .edit().putString("folders", array.toString()).apply()
    }

    companion object {
        private const val REQUEST_WIDGET_PICK = 4101
        private const val REQUEST_WIDGET_CONFIGURE = 4102
    }

    private fun openSystemSettings(section: String) {
        val action = when (section) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "airplane" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        try {
            startActivity(Intent(action))
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
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

        if (homeOrder.isEmpty()) {
            saveHomeOrder(launcherApps.take(20).map { it.packageName })
        } else {
            val valid = homeOrder.filter { token ->
                token.startsWith("folder:") || launcherApps.any { it.packageName == token }
            }
            val folderMembers = folders.flatMap { it.packages }.toSet()
            val knownApps = valid.filterNot { it.startsWith("folder:") }.toSet() + folderMembers
            val additions = launcherApps.asSequence()
                .filter { it.packageName !in knownApps }
                .take((20 - knownApps.size).coerceAtLeast(0))
                .map { it.packageName }
                .toList()
            if (valid != homeOrder || additions.isNotEmpty()) saveHomeOrder(valid + additions)
        }
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
    homeOrder: List<String>,
    folders: List<AppFolder>,
    onLaunchApp: (String) -> Unit,
    onSaveLayout: (List<String>) -> Unit,
    onSaveFolders: (List<AppFolder>) -> Unit,
    onOpenSystemSettings: (String) -> Unit,
    widgetIds: List<Int>,
    widgetHost: AppWidgetHost,
    widgetManager: AppWidgetManager,
    onAddWidget: () -> Unit,
    onRemoveWidget: (Int) -> Unit
) {
    val context = LocalContext.current
    var showLibrary by remember { mutableStateOf(false) }
    var showControlCenter by remember { mutableStateOf(false) }
    var showLockScreen by remember { mutableStateOf(false) }
    var showNotificationCenter by remember { mutableStateOf(false) }
    var showWidgets by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val prefs = remember { context.getSharedPreferences("luma_preferences", android.content.Context.MODE_PRIVATE) }
    var gridColumns by remember { mutableStateOf(prefs.getInt("grid_columns", 4).coerceIn(3, 5)) }
    var iconSize by remember { mutableStateOf(prefs.getInt("icon_size", 60).coerceIn(48, 76)) }
    var showAppLabels by remember { mutableStateOf(prefs.getBoolean("show_app_labels", true)) }
    var wallpaperStyle by remember { mutableStateOf(prefs.getInt("wallpaper_style", 0).coerceIn(0, 3)) }
    var customWallpaperUri by remember { mutableStateOf(prefs.getString("custom_wallpaper_uri", null)) }
    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            customWallpaperUri = uri.toString()
            prefs.edit().putString("custom_wallpaper_uri", uri.toString()).apply()
        }
    }
    val customWallpaper = remember(customWallpaperUri) {
        customWallpaperUri?.let { value ->
            runCatching { context.contentResolver.openInputStream(Uri.parse(value))?.use(BitmapFactory::decodeStream)?.asImageBitmap() }.getOrNull()
        }
    }
    var searchQuery by remember { mutableStateOf("") }
    var clock by remember { mutableStateOf(currentTime()) }
    val wallpaperPalettes = listOf(listOf(Color(0xFF26375D), Color(0xFF10182D), Color(0xFF070A12)), listOf(Color(0xFF4B2C70), Color(0xFF211536), Color(0xFF090711)), listOf(Color(0xFF075B68), Color(0xFF102D42), Color(0xFF070D18)), listOf(Color(0xFF6A3B35), Color(0xFF30202B), Color(0xFF0B0B12)))
    val background = Brush.verticalGradient(colors = wallpaperPalettes[wallpaperStyle])

    LaunchedEffect(Unit) {
        while (true) {
            clock = currentTime()
            delay(15_000)
        }
    }

    BackHandler(enabled = showLibrary || showControlCenter || showLockScreen || showNotificationCenter || showWidgets || showSettings) {
        if (showLibrary) {
            showLibrary = false
            searchQuery = ""
        } else if (showControlCenter) {
            showControlCenter = false
        } else if (showLockScreen) {
            showLockScreen = false
        } else if (showNotificationCenter) {
            showNotificationCenter = false
        } else if (showWidgets) {
            showWidgets = false
        } else {
            showSettings = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .pointerInput(showLibrary, showControlCenter) {
                var drag = 0f
                detectVerticalDragGestures(
                    onVerticalDrag = { _, amount -> drag += amount },
                    onDragEnd = {
                        if (!showLibrary && !showControlCenter && drag < -65f) showLibrary = true
                        else if (!showLibrary && !showControlCenter && drag > 65f) showControlCenter = true
                        else if (showLibrary && drag > 65f) {
                            showLibrary = false
                            searchQuery = ""
                        } else if (showControlCenter && drag < -65f) {
                            showControlCenter = false
                        }
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
        if (customWallpaper != null) {
            Image(bitmap = customWallpaper!!, contentDescription = "Custom launcher wallpaper", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.18f), Color.Black.copy(alpha = 0.58f)))))
        }
        AnimatedContent(
            targetState = when {
                showLibrary -> 1
                showControlCenter -> 2
                showLockScreen -> 3
                showNotificationCenter -> 4
                showWidgets -> 5
                showSettings -> 6
                else -> 0
            },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "launcher_pages"
        ) { page ->
            when (page) {
                1 -> AppLibrary(
                    apps = apps,
                    query = searchQuery,
                    badgeCounts = NotificationStore.badgeCounts,
                    onQueryChange = { searchQuery = it },
                    onLaunchApp = onLaunchApp,
                    onBack = { showLibrary = false; searchQuery = "" }
                )
                2 -> ControlCenter(
                    onBack = { showControlCenter = false },
                    onOpenSystemSettings = onOpenSystemSettings
                )
                3 -> LockScreenPreview(clock = clock, onBack = { showLockScreen = false })
                6 -> LauncherSettingsPage(
                    gridColumns = gridColumns,
                    iconSize = iconSize,
                    showAppLabels = showAppLabels,
                    wallpaperStyle = wallpaperStyle,
                    hasCustomWallpaper = customWallpaperUri != null,
                    onPickWallpaper = { wallpaperPicker.launch(arrayOf("image/*")) },
                    onClearWallpaper = { customWallpaperUri = null; prefs.edit().remove("custom_wallpaper_uri").apply() },
                    onWallpaperStyleChange = { wallpaperStyle = it; prefs.edit().putInt("wallpaper_style", it).apply() },
                    onGridColumnsChange = { gridColumns = it; prefs.edit().putInt("grid_columns", it).apply() },
                    onIconSizeChange = { iconSize = it; prefs.edit().putInt("icon_size", it).apply() },
                    onShowLabelsChange = { showAppLabels = it; prefs.edit().putBoolean("show_app_labels", it).apply() },
                    onBack = { showSettings = false }
                )
                5 -> WidgetsPage(
                    widgetIds = widgetIds,
                    host = widgetHost,
                    manager = widgetManager,
                    onAdd = onAddWidget,
                    onRemove = onRemoveWidget,
                    onBack = { showWidgets = false }
                )
                4 -> NotificationCenter(
                    notifications = NotificationStore.notifications,
                    onBack = { showNotificationCenter = false },
                    onEnableAccess = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                    onClear = { NotificationStore.clear() }
                )
                else -> HomeScreen(
                    apps = apps,
                    badgeCounts = NotificationStore.badgeCounts,
                    homeOrder = homeOrder,
                    folders = folders,
                    clock = clock,
                    onLaunchApp = onLaunchApp,
                    onOpenLibrary = { showLibrary = true },
                    onOpenControlCenter = { showControlCenter = true },
                    onOpenLockScreen = { showLockScreen = true },
                    onOpenNotifications = { showNotificationCenter = true },
                    onOpenWidgets = { showWidgets = true },
                    onOpenSettings = { showSettings = true },
                    gridColumns = gridColumns,
                    iconSize = iconSize,
                    showAppLabels = showAppLabels,
                    onSaveLayout = onSaveLayout,
                    onSaveFolders = onSaveFolders
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    apps: List<LauncherApp>,
    badgeCounts: Map<String, Int>,
    homeOrder: List<String>,
    folders: List<AppFolder>,
    clock: String,
    onLaunchApp: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenControlCenter: () -> Unit,
    onOpenLockScreen: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenWidgets: () -> Unit,
    onOpenSettings: () -> Unit,
    gridColumns: Int,
    iconSize: Int,
    showAppLabels: Boolean,
    onSaveLayout: (List<String>) -> Unit,
    onSaveFolders: (List<AppFolder>) -> Unit
) {
    val appByPackage = apps.associateBy { it.packageName }
    val folderById = folders.associateBy { "folder:${it.id}" }
    val folderMembers = folders.flatMap { it.packages }.toSet()
    val visibleOrder = homeOrder.filter { token ->
        (token.startsWith("folder:") && folderById.containsKey(token)) ||
            (!token.startsWith("folder:") && token !in folderMembers && appByPackage.containsKey(token))
    }
    val tiles = visibleOrder.mapNotNull { token ->
        if (token.startsWith("folder:")) {
            folderById[token]?.let { HomeTile(token, folder = it) }
        } else {
            appByPackage[token]?.let { HomeTile(token, app = it) }
        }
    }

    var selectedTile by remember { mutableStateOf<HomeTile?>(null) }
    var folderSource by remember { mutableStateOf<LauncherApp?>(null) }
    var openFolder by remember { mutableStateOf<AppFolder?>(null) }
    var newFolderName by remember { mutableStateOf("Folder") }
    var showFolderNameDialog by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    var islandExpanded by remember { mutableStateOf(false) }

    fun moveTile(tileId: String, direction: Int) {
        val mutable = visibleOrder.toMutableList()
        val index = mutable.indexOf(tileId)
        val target = index + direction
        if (index >= 0 && target in mutable.indices) {
            val value = mutable.removeAt(index)
            mutable.add(target, value)
            onSaveLayout(mutable)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("LumaOS", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Settings", color = Color(0xFFB9C9FF), fontSize = 11.sp, modifier = Modifier.clickable(onClick = onOpenSettings))
                Text("Notifications", color = Color(0xFFB9C9FF), fontSize = 11.sp, modifier = Modifier.clickable(onClick = onOpenNotifications))
                Text("Widgets", color = Color(0xFFB9C9FF), fontSize = 11.sp, modifier = Modifier.clickable(onClick = onOpenWidgets))
                Text("Lock Screen", color = Color(0xFFB9C9FF), fontSize = 11.sp, modifier = Modifier.clickable(onClick = onOpenLockScreen))
                Text("Control Center", color = Color(0xFFB9C9FF), fontSize = 11.sp, modifier = Modifier.clickable(onClick = onOpenControlCenter))
            }
        }
        DynamicIsland(
            expanded = islandExpanded,
            onClick = { islandExpanded = !islandExpanded }
        )
        Spacer(Modifier.height(20.dp))
        Text(clock, color = Color.White, fontSize = 58.sp, fontWeight = FontWeight.Light)
        Text(
            SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()),
            color = Color(0xFFD4DCEE),
            fontSize = 14.sp
        )
        Spacer(Modifier.height(28.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            items(tiles, key = { it.id }) { tile ->
                if (tile.app != null) {
                    AppIcon(
                        app = tile.app,
                        onClick = { onLaunchApp(tile.app.packageName) },
                        onLongClick = { selectedTile = tile },
                        iconSize = iconSize,
                        showLabel = showAppLabels,
                        badgeCount = badgeCounts[tile.app.packageName] ?: 0
                    )
                } else if (tile.folder != null) {
                    FolderIcon(
                        folder = tile.folder,
                        apps = apps,
                        onClick = { openFolder = tile.folder },
                        onLongClick = { selectedTile = tile }
                    )
                }
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
                        .size(52.dp)
                        .shadow(4.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
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
            "Swipe up for App Library • Swipe down for Control Center",
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenLibrary),
            color = Color(0xFFBAC6DE),
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }

    selectedTile?.let { tile ->
        AlertDialog(
            onDismissRequest = { selectedTile = null },
            title = { Text(if (tile.folder != null) tile.folder.name else tile.app?.label ?: "Arrange app") },
            text = {
                Column {
                    Text("Change this icon's position or organise apps into folders.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { moveTile(tile.id, -1); selectedTile = null }) { Text("Move left") }
                        TextButton(onClick = { moveTile(tile.id, 1); selectedTile = null }) { Text("Move right") }
                    }
                    if (tile.app != null) {
                        TextButton(onClick = {
                            folderSource = tile.app
                            selectedTile = null
                            showAppPicker = true
                        }) { Text("Create folder") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedTile = null }) { Text("Done") }
            }
        )
    }

    if (showAppPicker && folderSource != null) {
        AlertDialog(
            onDismissRequest = { showAppPicker = false; folderSource = null },
            title = { Text("Choose another app") },
            text = {
                Column {
                    apps.filter { it.packageName != folderSource?.packageName && it.packageName !in folderMembers && it.packageName in visibleOrder }
                        .take(12).forEach { app ->
                            TextButton(onClick = {
                                newFolderName = "Folder"
                                showAppPicker = false
                                showFolderNameDialog = true
                                folderSource = folderSource
                                selectedTile = HomeTile(app.packageName, app = app)
                            }) { Text(app.label) }
                        }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAppPicker = false; folderSource = null }) { Text("Cancel") }
            }
        )
    }

    if (showFolderNameDialog && folderSource != null && selectedTile?.app != null) {
        AlertDialog(
            onDismissRequest = { showFolderNameDialog = false; folderSource = null; selectedTile = null },
            title = { Text("Name your folder") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    singleLine = true,
                    label = { Text("Folder name") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val source = folderSource!!
                    val second = selectedTile!!.app!!
                    val folder = AppFolder(
                        id = "folder_${System.currentTimeMillis()}",
                        name = newFolderName.trim().ifBlank { "Folder" },
                        packages = listOf(source.packageName, second.packageName)
                    )
                    val updatedFolders = folders + folder
                    val oldOrder = visibleOrder.toMutableList()
                    val insertAt = minOf(
                        oldOrder.indexOf(source.packageName).takeIf { it >= 0 } ?: oldOrder.size,
                        oldOrder.indexOf(second.packageName).takeIf { it >= 0 } ?: oldOrder.size
                    )
                    oldOrder.remove(source.packageName)
                    oldOrder.remove(second.packageName)
                    oldOrder.add(insertAt.coerceIn(0, oldOrder.size), "folder:${folder.id}")
                    onSaveFolders(updatedFolders)
                    onSaveLayout(oldOrder)
                    showFolderNameDialog = false
                    folderSource = null
                    selectedTile = null
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showFolderNameDialog = false; folderSource = null; selectedTile = null }) { Text("Cancel") }
            }
        )
    }

    openFolder?.let { folder ->
        val members = folder.packages.mapNotNull { packageName -> appByPackage[packageName] }
        AlertDialog(
            onDismissRequest = { openFolder = null },
            title = { Text(folder.name) },
            text = {
                Column {
                    members.forEach { app ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                onLaunchApp(app.packageName)
                                openFolder = null
                            }.padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(app.icon, contentDescription = app.label, modifier = Modifier.size(34.dp))
                            Text(app.label, color = Color(0xFF202638), modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { openFolder = null }) { Text("Close") } },
            dismissButton = {
                TextButton(onClick = {
                    onSaveFolders(folders.filterNot { it.id == folder.id })
                    onSaveLayout(visibleOrder.filterNot { it == "folder:${folder.id}" } + folder.packages)
                    openFolder = null
                }) { Text("Unpack") }
            }
        )
    }
}




@Composable
private fun LauncherSettingsPage(
    gridColumns: Int,
    iconSize: Int,
    showAppLabels: Boolean,
    wallpaperStyle: Int,
    hasCustomWallpaper: Boolean,
    onPickWallpaper: () -> Unit,
    onClearWallpaper: () -> Unit,
    onWallpaperStyleChange: (Int) -> Unit,
    onGridColumnsChange: (Int) -> Unit,
    onIconSizeChange: (Int) -> Unit,
    onShowLabelsChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Home Screen Settings", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Done", color = Color(0xFFB9C9FF), modifier = Modifier.clickable(onClick = onBack))
        }
        Text("Personalise the look and spacing of your LumaOS home screen.", color = Color(0xFFD4DCEE), fontSize = 13.sp)
        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.12f)).padding(16.dp)) {
            Text("App grid", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("Choose how many icons appear in each row.", color = Color(0xFFD4DCEE), fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp)) {
                (3..5).forEach { count ->
                    androidx.compose.material3.FilterChip(selected = gridColumns == count, onClick = { onGridColumnsChange(count) }, label = { Text("$count columns") })
                }
            }
        }
        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.12f)).padding(16.dp)) {
            Text("Icon size", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("$iconSize dp", color = Color(0xFFD4DCEE), fontSize = 12.sp)
            androidx.compose.material3.Slider(value = iconSize.toFloat(), onValueChange = { onIconSizeChange(it.toInt()) }, valueRange = 48f..76f)
        }
        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.12f)).padding(16.dp)) {
            Text("Wallpaper style", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("Choose a premium gradient or use a photo from your device.", color = Color(0xFFD4DCEE), fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)) {
                androidx.compose.material3.OutlinedButton(onClick = onPickWallpaper) { Text("Choose photo") }
                if (hasCustomWallpaper) androidx.compose.material3.TextButton(onClick = onClearWallpaper) { Text("Use gradient") }
            }
            listOf("Midnight Blue", "Royal Purple", "Ocean Teal", "Warm Sunset").forEachIndexed { index, name ->
                Row(modifier = Modifier.fillMaxWidth().clickable { onWallpaperStyleChange(index) }.padding(vertical = 9.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(name, color = Color.White, fontSize = 14.sp)
                    if (wallpaperStyle == index) Text("Selected", color = Color(0xFFB9C9FF), fontSize = 12.sp)
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.12f)).clickable { onShowLabelsChange(!showAppLabels) }.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Show app names", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("Display or hide labels below app icons.", color = Color(0xFFD4DCEE), fontSize = 12.sp)
            }
            androidx.compose.material3.Switch(checked = showAppLabels, onCheckedChange = onShowLabelsChange)
        }
    }
}

@Composable
private fun WidgetsPage(
    widgetIds: List<Int>,
    host: AppWidgetHost,
    manager: AppWidgetManager,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Widgets", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Text("Done", color = Color(0xFFB9C9FF), modifier = Modifier.clickable(onClick = onBack))
        }
        Spacer(Modifier.height(8.dp))
        Text("Add real widgets from apps installed on your phone.", color = Color(0xFFD4DCEE), fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        androidx.compose.material3.Button(onClick = onAdd) { Text("＋  Add widget") }
        Spacer(Modifier.height(12.dp))
        if (widgetIds.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(22.dp))
                    .background(Color.White.copy(alpha = 0.10f)).padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Your widget space is empty. Tap Add widget to choose a clock, calendar, weather, or another widget provided by an installed app.", color = Color(0xFFD4DCEE), textAlign = TextAlign.Center, fontSize = 13.sp)
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(widgetIds, key = { it }) { id ->
                    val info = manager.getAppWidgetInfo(id)
                    Column(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.10f)).padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(info?.loadLabel(LocalContext.current.packageManager) ?: "App widget", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Remove", color = Color(0xFFFFB4B4), fontSize = 12.sp, modifier = Modifier.clickable { onRemove(id) }.padding(6.dp))
                        }
                        if (info != null) {
                            val widgetHeight = info.minHeight.coerceAtLeast(120).coerceAtMost(420)
                            AndroidView(
                                factory = { ctx ->
                                    host.createView(ctx, id, info).apply { setAppWidget(id, info) }
                                },
                                modifier = Modifier.fillMaxWidth().height(widgetHeight.dp)
                            )
                        } else {
                            Text("This widget is no longer available. Remove it and add another.", color = Color(0xFFD4DCEE), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCenter(
    notifications: List<LumaNotification>,
    onBack: () -> Unit,
    onEnableAccess: () -> Unit,
    onClear: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Notification Center", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("Done", color = Color(0xFFB9C9FF), modifier = Modifier.clickable(onClick = onBack))
        }
        Spacer(Modifier.height(8.dp))
        Text("Your recent notifications", color = Color(0xFFD4DCEE), fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))
        if (notifications.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                    .background(Color.White.copy(alpha = 0.12f)).padding(18.dp)
            ) {
                Text("No notifications yet", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text("Allow notification access to show notifications here. You control this permission in Android settings.", color = Color(0xFFD4DCEE), fontSize = 12.sp)
                TextButton(onClick = onEnableAccess) { Text("Enable notification access") }
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onClear) { Text("Clear all") }
            }
            androidx.compose.foundation.lazy.LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(count = notifications.size, key = { index -> notifications[index].id }) { index ->
                    val item = notifications[index]
                    Column(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.13f)).padding(15.dp)
                    ) {
                        Text(item.appName, color = Color(0xFFB9C9FF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text(item.title.ifBlank { "Notification" }, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        if (item.message.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(item.message, color = Color(0xFFD4DCEE), fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(item.time, color = Color(0xFF9EABC4), fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun LockScreenPreview(clock: String, onBack: () -> Unit) {
    val date = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date())
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(34.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF536A91), Color(0xFF182641), Color(0xFF080D19))
                )
            )
            .clickable(onClick = onBack)
            .padding(horizontal = 24.dp, vertical = 34.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(date, color = Color.White.copy(alpha = 0.9f), fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            Text(clock, color = Color.White, fontSize = 76.sp, fontWeight = FontWeight.ExtraLight)
            Spacer(Modifier.height(30.dp))
            Box(
                modifier = Modifier
                    .size(74.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center
            ) {
                Text("L", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Light)
            }
            Spacer(Modifier.height(18.dp))
            Text("LumaOS", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Text("Your Android. A New Experience.", color = Color(0xFFD7E1F5), fontSize = 11.sp)
        }
        Text(
            "Preview only • Tap anywhere to return",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun DynamicIsland(
    expanded: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .padding(top = 14.dp)
            .width(if (expanded) 252.dp else 122.dp)
            .clip(CircleShape)
            .background(Color.Black)
            .clickable(onClick = onClick)
            .animateContentSize()
            .padding(horizontal = 16.dp, vertical = if (expanded) 13.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color(0xFF65D9FF))
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                if (expanded) "LumaOS Live Activity" else "LumaOS",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (expanded) {
                Spacer(Modifier.height(3.dp))
                Text("Your home screen is ready", color = Color(0xFFB8C4D9), fontSize = 10.sp)
            }
        }
    }
}
@Composable
private fun ControlCenter(
    onBack: () -> Unit,
    onOpenSystemSettings: (String) -> Unit
) {
    val controls = listOf(
        Triple("Wi-Fi", "📶", "Manage wireless networks") to "wifi",
        Triple("Bluetooth", "ᛒ", "Manage connected devices") to "bluetooth",
        Triple("Airplane mode", "✈", "Airplane mode settings") to "airplane",
        Triple("Location", "⌖", "Location access settings") to "location",
        Triple("Display", "☼", "Brightness and display settings") to "display",
        Triple("Sound", "♫", "Sound and vibration settings") to "sound",
        Triple("Battery", "▰", "Battery and power settings") to "battery",
        Triple("All settings", "⚙", "Open Android settings") to "settings"
    )

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Control Center", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Text("Done", color = Color(0xFFB9C9FF), modifier = Modifier.clickable(onClick = onBack))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Quick access to your device controls",
            color = Color(0xFFD4DCEE),
            fontSize = 13.sp
        )
        Spacer(Modifier.height(22.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(controls) { item ->
                val control = item.first
                ControlTile(
                    symbol = control.second,
                    title = control.first,
                    subtitle = control.third,
                    onClick = { onOpenSystemSettings(item.second) }
                )
            }
        }
        Text(
            "Protected switches are managed by Android. Tap a tile to open the matching system settings.",
            color = Color(0xFFBAC6DE),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )
    }
}

@Composable
private fun ControlTile(
    symbol: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(118.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(symbol, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Medium)
        Column {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Color(0xFFD4DCEE), fontSize = 10.sp, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderIcon(
    folder: AppFolder,
    apps: List<LauncherApp>,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val icons = folder.packages.mapNotNull { packageName -> apps.firstOrNull { it.packageName == packageName } }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            if (icons.isEmpty()) {
                Icon(Icons.Rounded.Folder, contentDescription = folder.name, tint = Color.White)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        icons.take(2).forEach { Image(it.icon, null, Modifier.size(20.dp)) }
                    }
                    if (icons.size > 2) {
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            icons.drop(2).take(2).forEach { Image(it.icon, null, Modifier.size(20.dp)) }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(folder.name, color = Color.White, fontSize = 10.sp, maxLines = 1, textAlign = TextAlign.Center)
    }
}

@Composable
private fun AppLibrary(
    apps: List<LauncherApp>,
    badgeCounts: Map<String, Int>,
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
        Text("${filtered.size} applications", color = Color(0xFFB3BED3), fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            items(filtered, key = { it.packageName }) { app ->
                AppIcon(app = app, onClick = { onLaunchApp(app.packageName) }, badgeCount = badgeCounts[app.packageName] ?: 0)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppIcon(
    app: LauncherApp,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    iconSize: Int = 60,
    showLabel: Boolean = true,
    badgeCount: Int = 0
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = { onLongClick?.invoke() }
        )
    ) {
        Box {
            Image(
                bitmap = app.icon,
                contentDescription = app.label,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(iconSize.dp)
                .shadow(5.dp, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp))
            )
            if (badgeCount > 0) {
                Box(
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-5).dp)
                        .size(if (badgeCount > 9) 22.dp else 18.dp)
                        .clip(CircleShape).background(Color(0xFFFF453A)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (badgeCount > 99) "99+" else badgeCount.toString(), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(6.dp))
            Text(app.label, color = Color.White, fontSize = 11.sp, maxLines = 1, textAlign = TextAlign.Center)
        }
    }
}

private fun currentTime(): String =
    SimpleDateFormat("h:mm", Locale.getDefault()).format(Date())
