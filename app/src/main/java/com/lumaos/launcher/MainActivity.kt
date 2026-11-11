package com.lumaos.launcher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.Manifest
import android.content.pm.PackageManager
import android.provider.CallLog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.graphics.Canvas as AndroidCanvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.BackHandler
import androidx.core.content.ContextCompat
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.LocalTextStyle
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class LauncherApp(
    val label: String,
    val packageName: String,
    val icon: ImageBitmap,
    val isSystem: Boolean
)

private data class LumaCall(val number: String, val type: Int, val date: Long, val duration: Long)

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

class MainActivity : FragmentActivity() {
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
                    icon = info.loadIcon(packageManager).toImageBitmap(),
                    isSystem = activityInfo.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }

        if (homeOrder.isEmpty()) {
            saveHomeOrder(launcherApps.map { it.packageName })
        } else {
            val valid = homeOrder.filter { token ->
                token.startsWith("folder:") || launcherApps.any { it.packageName == token }
            }
            val folderMembers = folders.flatMap { it.packages }.toSet()
            val knownApps = valid.filterNot { it.startsWith("folder:") }.toSet() + folderMembers
            val additions = launcherApps.asSequence()
                .filter { it.packageName !in knownApps }
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
    var showControlCenter by remember { mutableStateOf(false) }
    var showLockScreen by remember { mutableStateOf(false) }
    var showNotificationCenter by remember { mutableStateOf(false) }
    var showWidgets by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showCallLogs by remember { mutableStateOf(false) }
    var callLogs by remember { mutableStateOf<List<LumaCall>>(emptyList()) }
    var callLogPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) }
    val callLogPermissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        callLogPermission = granted
        if (granted) callLogs = readLumaCallLogs(context)
    }
    val prefs = remember { context.getSharedPreferences("luma_preferences", android.content.Context.MODE_PRIVATE) }
    var gridColumns by remember { mutableStateOf(prefs.getInt("grid_columns", 4).coerceIn(3, 5)) }
    var iconSize by remember { mutableStateOf(prefs.getInt("icon_size", 60).coerceIn(48, 76)) }
    var showAppLabels by remember { mutableStateOf(prefs.getBoolean("show_app_labels", true)) }
    var wallpaperStyle by remember { mutableStateOf(prefs.getInt("wallpaper_style", 0).coerceIn(0, 7)) }
    var fontScale by remember { mutableStateOf(prefs.getFloat("font_scale", 1.0f).coerceIn(0.8f, 1.4f)) }
    var themeStyle by remember { mutableStateOf(prefs.getInt("theme_style", 0).coerceIn(0, 3)) }
    var fontStyle by remember { mutableStateOf(prefs.getInt("font_style", 0).coerceIn(0, 2)) }
    var customWallpaperUri by remember { mutableStateOf(prefs.getString("custom_wallpaper_uri", null)) }
    var hiddenPackages by remember { mutableStateOf(prefs.getStringSet("hidden_packages", emptySet())?.toSet() ?: emptySet()) }
    var favoritePackages by remember { mutableStateOf(prefs.getStringSet("favorite_packages", emptySet())?.toSet() ?: emptySet()) }
    var lockedPackages by remember { mutableStateOf(prefs.getStringSet("locked_packages", emptySet())?.toSet() ?: emptySet()) }
    fun toggleAppLock(packageName: String) {
        lockedPackages = if (packageName in lockedPackages) lockedPackages - packageName else lockedPackages + packageName
        prefs.edit().putStringSet("locked_packages", lockedPackages).apply()
    }
    val launchAppWithLock: (String) -> Unit = { packageName ->
        if (packageName !in lockedPackages) {
            onLaunchApp(packageName)
        } else {
            val activity = context as? FragmentActivity
            if (activity == null) {
                Toast.makeText(context, "Unable to authenticate. App remains locked.", Toast.LENGTH_LONG).show()
            } else {
                val authenticators = if (android.os.Build.VERSION.SDK_INT >= 30)
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                else BiometricManager.Authenticators.BIOMETRIC_WEAK
                val manager = BiometricManager.from(activity)
                if (manager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
                    Toast.makeText(activity, "Set up fingerprint/face or a secure screen lock to open this protected app.", Toast.LENGTH_LONG).show()
                } else {
                    val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            super.onAuthenticationSucceeded(result)
                            onLaunchApp(packageName)
                        }
                    })
                    val builder = BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Unlock ${apps.firstOrNull { it.packageName == packageName }?.label ?: "app"}")
                        .setSubtitle("Authenticate to continue")
                    if (android.os.Build.VERSION.SDK_INT >= 30) {
                        builder.setAllowedAuthenticators(authenticators)
                    } else {
                        builder.setAllowedAuthenticators(authenticators).setNegativeButtonText("Cancel")
                    }
                    prompt.authenticate(builder.build())
                }
            }
        }
    }
    fun toggleFavoriteApp(packageName: String) {
        favoritePackages = if (packageName in favoritePackages) favoritePackages - packageName else favoritePackages + packageName
        prefs.edit().putStringSet("favorite_packages", favoritePackages).apply()
    }
    fun toggleHiddenApp(packageName: String) {
        hiddenPackages = if (packageName in hiddenPackages) hiddenPackages - packageName else hiddenPackages + packageName
        prefs.edit().putStringSet("hidden_packages", hiddenPackages).apply()
    }
    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            customWallpaperUri = uri.toString()
            prefs.edit().putString("custom_wallpaper_uri", uri.toString()).apply()
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val openIntent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            runCatching { context.startActivity(Intent.createChooser(openIntent, "Open file with")) }
        }
    }
    fun requestDefaultLauncher() {
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
                if (roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) {
                    context.startActivity(roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME))
                } else context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
            } else context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        }.onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) } }
    }
    val customWallpaper = remember(customWallpaperUri) {
        customWallpaperUri?.let { value ->
            runCatching { context.contentResolver.openInputStream(Uri.parse(value))?.use(BitmapFactory::decodeStream)?.asImageBitmap() }.getOrNull()
        }
    }
    var searchQuery by remember { mutableStateOf("") }
    var clock by remember { mutableStateOf(currentTime()) }
    val wallpaperPalettes = listOf(
        listOf(Color(0xFF26375D), Color(0xFF10182D), Color(0xFF070A12)),
        listOf(Color(0xFF4B2C70), Color(0xFF211536), Color(0xFF090711)),
        listOf(Color(0xFF075B68), Color(0xFF102D42), Color(0xFF070D18)),
        listOf(Color(0xFF6A3B35), Color(0xFF30202B), Color(0xFF0B0B12)),
        listOf(Color(0xFF173F38), Color(0xFF102820), Color(0xFF07130F)),
        listOf(Color(0xFF5A3150), Color(0xFF2D1B34), Color(0xFF100A18)),
        listOf(Color(0xFF315A83), Color(0xFF1B314E), Color(0xFF090F1D)),
        listOf(Color(0xFF72512E), Color(0xFF392A22), Color(0xFF140F0B))
    )
    val themePalettes = listOf(
        listOf(Color(0xFF26375D), Color(0xFF10182D), Color(0xFF070A12)),
        listOf(Color(0xFF101010), Color(0xFF050505), Color.Black),
        listOf(Color(0xFF4B2C70), Color(0xFF211536), Color(0xFF090711)),
        listOf(Color(0xFF075B68), Color(0xFF102D42), Color(0xFF070D18))
    )
    val background = Brush.verticalGradient(colors = wallpaperPalettes[wallpaperStyle].mapIndexed { index, color -> androidx.compose.ui.graphics.lerp(color, themePalettes[themeStyle][index % themePalettes[themeStyle].size], 0.45f) })
    val selectedFont = when (fontStyle) { 1 -> FontFamily.Serif; 2 -> FontFamily.Monospace; else -> FontFamily.Default }

    LaunchedEffect(Unit) {
        while (true) {
            clock = currentTime()
            delay(15_000)
        }
    }

    BackHandler(enabled = showControlCenter || showLockScreen || showNotificationCenter || showWidgets || showSettings || showCallLogs) {
        if (showControlCenter) {
            showControlCenter = false
        } else if (showLockScreen) {
            showLockScreen = false
        } else if (showNotificationCenter) {
            showNotificationCenter = false
        } else if (showWidgets) {
            showWidgets = false
        } else if (showCallLogs) {
            showCallLogs = false
        } else {
            showSettings = false
        }
    }

    val baseDensity = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(baseDensity.density, baseDensity.fontScale * fontScale)) {
    CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = selectedFont)) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .pointerInput(showControlCenter) {
                var drag = 0f
                detectVerticalDragGestures(
                    onVerticalDrag = { _, amount -> drag += amount },
                    onDragEnd = {
                        if (!showControlCenter && drag > 65f) showControlCenter = true
                        else if (showControlCenter && drag < -65f) {
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
                showControlCenter -> 2
                showLockScreen -> 3
                showNotificationCenter -> 4
                showWidgets -> 5
                showSettings -> 6
                showCallLogs -> 7
                else -> 0
            },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "launcher_pages"
        ) { page ->
            when (page) {
                2 -> ControlCenter(
                    onBack = { showControlCenter = false },
                    onOpenSystemSettings = onOpenSystemSettings
                )
                3 -> LockScreenPreview(clock = clock, onBack = { showLockScreen = false })
                6 -> LauncherSettingsPage(
                    themeStyle = themeStyle,
                    fontStyle = fontStyle,
                    fontScale = fontScale,
                    appVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.2.0",
                    versionCode = if (android.os.Build.VERSION.SDK_INT >= 28) context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode else context.packageManager.getPackageInfo(context.packageName, 0).versionCode.toLong(),
                    onThemeStyleChange = { themeStyle = it; prefs.edit().putInt("theme_style", it).apply() },
                    onFontStyleChange = { fontStyle = it; prefs.edit().putInt("font_style", it).apply() },
                    onFontScaleChange = { fontScale = it; prefs.edit().putFloat("font_scale", it).apply() },
                    onOpenCallLogs = {
                        showSettings = false
                        callLogPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
                        if (callLogPermission) callLogs = readLumaCallLogs(context)
                        showCallLogs = true
                    },
                    onSetDefaultLauncher = ::requestDefaultLauncher,
                    onBrowseFiles = { filePicker.launch(arrayOf("*/*")) },
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
                7 -> CallLogsPage(
                    calls = callLogs,
                    hasPermission = callLogPermission,
                    onRequestPermission = { callLogPermissionRequest.launch(Manifest.permission.READ_CALL_LOG) },
                    onRefresh = { callLogs = readLumaCallLogs(context) },
                    onBack = { showCallLogs = false }
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
                    onClear = { NotificationStore.clear() },
                    onClearApp = { NotificationStore.clearApp(it) },
                    onDismiss = { NotificationStore.dismiss(it) },
                    onOpenNotification = { item ->
                        item.contentIntent?.let { pendingIntent ->
                            runCatching { pendingIntent.send() }
                                .onFailure { Toast.makeText(context, "Unable to open this notification.", Toast.LENGTH_SHORT).show() }
                        }
                    },
                    onRunAction = { _, action, reply ->
                        val fillInIntent = Intent()
                        val remoteInputs = action.remoteInputs.filter { it.allowFreeFormInput }
                        if (!reply.isNullOrBlank() && remoteInputs.isNotEmpty()) {
                            val results = Bundle()
                            remoteInputs.forEach { results.putCharSequence(it.resultKey, reply) }
                            android.app.RemoteInput.addResultsToIntent(remoteInputs.toTypedArray(), fillInIntent, results)
                        }
                        runCatching { action.pendingIntent.send(context, 0, fillInIntent) }
                            .onFailure { Toast.makeText(context, "This notification action is no longer available.", Toast.LENGTH_SHORT).show() }
                    }
                )
                else -> HomeScreen(
                    apps = apps,
                    hiddenPackages = hiddenPackages,
                    badgeCounts = NotificationStore.badgeCounts,
                    homeOrder = homeOrder,
                    folders = folders,
                    clock = clock,
                    onLaunchApp = launchAppWithLock,
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
    }
}

@Composable
private fun HomeScreen(
    apps: List<LauncherApp>,
    hiddenPackages: Set<String>,
    badgeCounts: Map<String, Int>,
    homeOrder: List<String>,
    folders: List<AppFolder>,
    clock: String,
    onLaunchApp: (String) -> Unit,
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
            (!token.startsWith("folder:") && token !in folderMembers && token !in hiddenPackages && appByPackage.containsKey(token))
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
            apps.take(4).forEach { app ->
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
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Swipe down for Control Center • Swipe up to move between home pages",
            modifier = Modifier.fillMaxWidth(),
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
    themeStyle: Int,
    fontStyle: Int,
    fontScale: Float,
    appVersion: String,
    versionCode: Long,
    onThemeStyleChange: (Int) -> Unit,
    onFontStyleChange: (Int) -> Unit,
    onFontScaleChange: (Float) -> Unit,
    onOpenCallLogs: () -> Unit,
    onSetDefaultLauncher: () -> Unit,
    onBrowseFiles: () -> Unit,
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
        Text("Personalise the look, typography and spacing of your LumaOS home screen.", color = Color(0xFFD4DCEE), fontSize = 13.sp)
        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.12f)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("About LumaOS", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text("LumaOS • Your Android. A New Experience.", color = Color(0xFFD4DCEE), fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Version", color = Color(0xFFD4DCEE), fontSize = 12.sp); Text(appVersion, color = Color.White, fontSize = 12.sp) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Build", color = Color(0xFFD4DCEE), fontSize = 12.sp); Text(versionCode.toString(), color = Color.White, fontSize = 12.sp) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Builder", color = Color(0xFFD4DCEE), fontSize = 12.sp); Text("Nation Hub Tech", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Package", color = Color(0xFFD4DCEE), fontSize = 12.sp); Text("com.lumaos.launcher", color = Color.White, fontSize = 11.sp) }
            Text("Built with care by Nation Hub Tech.", color = Color(0xFFB9C9FF), fontSize = 11.sp)
        }
        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.12f)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Make LumaOS your default", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("Choose LumaOS as the Home app so pressing your phone's Home button opens this launcher.", color = Color(0xFFD4DCEE), fontSize = 12.sp)
            androidx.compose.material3.Button(onClick = onSetDefaultLauncher) { Text("Set as default launcher") }
        }
        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.12f)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("File access", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("Browse files, photos and documents using Android's secure file picker. LumaOS only receives access to files you select.", color = Color(0xFFD4DCEE), fontSize = 12.sp)
            androidx.compose.material3.OutlinedButton(onClick = onBrowseFiles) { Text("Browse files") }
        }
        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.12f)).clickable(onClick = onOpenCallLogs).padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Call history", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("View recent incoming, outgoing and missed calls.", color = Color(0xFFD4DCEE), fontSize = 12.sp)
            }
            Text("Open  ›", color = Color(0xFFB9C9FF), fontSize = 12.sp)
        }
        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.12f)).padding(16.dp)) {
            Text("Interface theme", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("Choose the overall look of LumaOS.", color = Color(0xFFD4DCEE), fontSize = 12.sp)
            listOf("Midnight", "AMOLED Black", "Royal Purple", "Ocean Teal").forEachIndexed { index, name ->
                Row(modifier = Modifier.fillMaxWidth().clickable { onThemeStyleChange(index) }.padding(vertical = 9.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(name, color = Color.White, fontSize = 14.sp)
                    if (themeStyle == index) Text("Selected", color = Color(0xFFB9C9FF), fontSize = 12.sp)
                }
            }
        }
        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.12f)).padding(16.dp)) {
            Text("Font style", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("Choose the typeface used throughout the launcher.", color = Color(0xFFD4DCEE), fontSize = 12.sp)
            listOf("System", "Serif", "Monospace").forEachIndexed { index, name ->
                Row(modifier = Modifier.fillMaxWidth().clickable { onFontStyleChange(index) }.padding(vertical = 9.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(name, color = Color.White, fontSize = 14.sp, fontFamily = when(index) { 1 -> FontFamily.Serif; 2 -> FontFamily.Monospace; else -> FontFamily.Default })
                    if (fontStyle == index) Text("Selected", color = Color(0xFFB9C9FF), fontSize = 12.sp)
                }
            }
        }
        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.12f)).padding(16.dp)) {
            Text("Font size", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("${(fontScale * 100).toInt()}% • Applies across the launcher", color = Color(0xFFD4DCEE), fontSize = 12.sp)
            androidx.compose.material3.Slider(value = fontScale, onValueChange = onFontScaleChange, valueRange = 0.8f..1.4f, steps = 11)
        }
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
            listOf("Midnight Blue", "Royal Purple", "Ocean Teal", "Warm Sunset", "Forest Green", "Plum Night", "Glacier Blue", "Golden Dusk").forEachIndexed { index, name ->
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
private fun CallLogsPage(
    calls: List<LumaCall>,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Call History", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("Done", color = Color(0xFFB9C9FF), modifier = Modifier.clickable(onClick = onBack))
        }
        Text("Recent calls stored by your phone", color = Color(0xFFD4DCEE), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp))
        if (!hasPermission) {
            Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.12f)).padding(18.dp)) {
                Text("Call log permission needed", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text("Allow access only if you want LumaOS to display your recent call history. Your call records stay on this device and are not uploaded by this feature.", color = Color(0xFFD4DCEE), fontSize = 12.sp)
                TextButton(onClick = onRequestPermission) { Text("Allow call log access") }
                Text("Note: READ_CALL_LOG is a restricted permission under Google Play policy. Publishing this feature may require Google Play approval or an eligible permitted use.", color = Color(0xFFFFD28A), fontSize = 11.sp)
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onRefresh) { Text("Refresh") }
            }
            if (calls.isEmpty()) {
                Text("No recent calls found.", color = Color(0xFFD4DCEE), fontSize = 13.sp)
            } else {
                androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(calls) { call ->
                        val kind = when (call.type) {
                            CallLog.Calls.INCOMING_TYPE -> "Incoming"
                            CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                            CallLog.Calls.MISSED_TYPE -> "Missed"
                            CallLog.Calls.REJECTED_TYPE -> "Rejected"
                            CallLog.Calls.BLOCKED_TYPE -> "Blocked"
                            else -> "Call"
                        }
                        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.12f)).padding(14.dp)) {
                            Text(call.number.ifBlank { "Unknown number" }, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(kind, color = if (call.type == CallLog.Calls.MISSED_TYPE) Color(0xFFFFA6A6) else Color(0xFFB9C9FF), fontSize = 12.sp)
                            Text(SimpleDateFormat("EEE, d MMM • h:mm a", Locale.getDefault()).format(Date(call.date)) + "  •  " + (call.duration / 60) + "m " + (call.duration % 60) + "s", color = Color(0xFFD4DCEE), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun readLumaCallLogs(context: android.content.Context): List<LumaCall> {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) return emptyList()
    val result = mutableListOf<LumaCall>()
    runCatching {
        context.contentResolver.query(CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION),
            null, null, CallLog.Calls.DATE + " DESC")?.use { cursor ->
            val number = cursor.getColumnIndex(CallLog.Calls.NUMBER)
            val type = cursor.getColumnIndex(CallLog.Calls.TYPE)
            val date = cursor.getColumnIndex(CallLog.Calls.DATE)
            val duration = cursor.getColumnIndex(CallLog.Calls.DURATION)
            while (cursor.moveToNext() && result.size < 100) {
                result.add(LumaCall(
                    number = if (number >= 0) cursor.getString(number).orEmpty() else "",
                    type = if (type >= 0) cursor.getInt(type) else 0,
                    date = if (date >= 0) cursor.getLong(date) else 0L,
                    duration = if (duration >= 0) cursor.getLong(duration) else 0L
                ))
            }
        }
    }
    return result
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
    onClear: () -> Unit,
    onClearApp: (String) -> Unit,
    onDismiss: (String) -> Unit,
    onOpenNotification: (LumaNotification) -> Unit,
    onRunAction: (LumaNotification, LumaNotificationAction, String?) -> Unit
) {
    var collapsedGroups by remember { mutableStateOf<Set<String>>(emptySet()) }
    var replyTarget by remember { mutableStateOf<Pair<LumaNotification, LumaNotificationAction>?>(null) }
    var replyText by remember { mutableStateOf("") }
    val groupedNotifications = notifications.groupBy { it.packageName }

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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Grouped by app", color = Color(0xFFD4DCEE), fontSize = 13.sp)
            if (notifications.isNotEmpty()) {
                TextButton(onClick = onClear) { Text("Clear history") }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (notifications.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                    .background(Color.White.copy(alpha = 0.12f)).padding(18.dp)
            ) {
                Text("No notifications yet", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Allow notification access to show notifications here. You control this permission in Android settings.",
                    color = Color(0xFFD4DCEE),
                    fontSize = 12.sp
                )
                TextButton(onClick = onEnableAccess) { Text("Enable notification access") }
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(groupedNotifications.entries.toList(), key = { it.key }) { entry ->
                    val packageName = entry.key
                    val appNotifications = entry.value
                    val isCollapsed = packageName in collapsedGroups
                    Column(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                            .background(Color.White.copy(alpha = 0.12f)).padding(13.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                collapsedGroups = if (isCollapsed) collapsedGroups - packageName
                                else collapsedGroups + packageName
                            },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    appNotifications.first().appName,
                                    color = Color(0xFFB9C9FF),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "${appNotifications.size} notification${if (appNotifications.size == 1) "" else "s"}",
                                    color = Color(0xFF9EABC4),
                                    fontSize = 10.sp
                                )
                            }
                            Text(if (isCollapsed) "Show" else "Hide", color = Color(0xFFB9C9FF), fontSize = 11.sp)
                        }
                        if (!isCollapsed) {
                            appNotifications.forEach { item ->
                                Spacer(Modifier.height(10.dp))
                                Column(
                                    modifier = Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.Black.copy(alpha = 0.16f))
                                        .clickable(enabled = item.contentIntent != null) { onOpenNotification(item) }
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        item.title.ifBlank { "Notification" },
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (item.message.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(item.message, color = Color(0xFFD4DCEE), fontSize = 12.sp)
                                    }
                                    Spacer(Modifier.height(5.dp))
                                    Text(item.time, color = Color(0xFF9EABC4), fontSize = 10.sp)
                                    if (item.actions.isNotEmpty() || item.contentIntent != null) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            item.actions.take(3).forEach { action ->
                                                TextButton(onClick = {
                                                    if (action.remoteInputs.any { it.allowFreeFormInput }) {
                                                        replyTarget = item to action
                                                        replyText = ""
                                                    } else {
                                                        onRunAction(item, action, null)
                                                    }
                                                }) {
                                                    Text(action.title, fontSize = 11.sp)
                                                }
                                            }
                                            TextButton(onClick = { onDismiss(item.id) }) {
                                                Text("Dismiss", fontSize = 11.sp)
                                            }
                                        }
                                    } else {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                            TextButton(onClick = { onDismiss(item.id) }) { Text("Dismiss", fontSize = 11.sp) }
                                        }
                                    }
                                }
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { onClearApp(packageName) }) {
                                Text("Clear app", fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    replyTarget?.let { (item, action) ->
        AlertDialog(
            onDismissRequest = { replyTarget = null },
            title = { Text("Reply to ${item.appName}") },
            text = {
                Column {
                    Text(item.title.ifBlank { "Notification" }, color = Color(0xFF68748A), fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        label = { Text("Your reply") },
                        minLines = 2,
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = replyText.isNotBlank(),
                    onClick = {
                        onRunAction(item, action, replyText.trim())
                        replyTarget = null
                        replyText = ""
                    }
                ) { Text("Send") }
            },
            dismissButton = {
                TextButton(onClick = { replyTarget = null; replyText = "" }) { Text("Cancel") }
            }
        )
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
    hiddenPackages: Set<String>,
    onToggleHidden: (String) -> Unit,
    favoritePackages: Set<String>,
    onToggleFavorite: (String) -> Unit,
    lockedPackages: Set<String>,
    onToggleAppLock: (String) -> Unit,
    gridColumns: Int,
    iconSize: Int,
    showAppLabels: Boolean,
    badgeCounts: Map<String, Int>,
    query: String,
    sortDescending: Boolean,
    onToggleSort: () -> Unit,
    onQueryChange: (String) -> Unit,
    onLaunchApp: (String) -> Unit,
    onBack: () -> Unit
) {
    var appFilter by remember { mutableStateOf("All") }
    var showHiddenApps by remember { mutableStateOf(false) }
    var selectedApp by remember { mutableStateOf<LauncherApp?>(null) }
    val filtered = apps.filter { app ->
        (if (showHiddenApps) app.packageName in hiddenPackages else app.packageName !in hiddenPackages) &&
            app.label.contains(query, ignoreCase = true) && when (appFilter) {
            "System" -> app.isSystem
            "Downloaded" -> !app.isSystem
            "Favorites" -> app.packageName in favoritePackages
            else -> true
        }
    }.sortedBy { it.label.lowercase(Locale.getDefault()) }
        .let { if (sortDescending) it.reversed() else it }
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
        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("All", "Downloaded", "System", "Favorites").forEach { filter ->
                val selected = appFilter == filter
                Text(filter, color = if (selected) Color.White else Color(0xFFB3BED3), fontSize = 12.sp,
                    modifier = Modifier.clip(RoundedCornerShape(18.dp))
                        .background(if (selected) Color(0xFF6650C8) else Color.White.copy(alpha = 0.10f))
                        .clickable { appFilter = filter }
                        .padding(horizontal = 13.dp, vertical = 8.dp))
            }
            Text(if (showHiddenApps) "Hidden" else "Visible", color = Color(0xFFB9C9FF), fontSize = 11.sp,
                modifier = Modifier.clickable { showHiddenApps = !showHiddenApps }.padding(6.dp))
        }
        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${filtered.size} applications", color = Color(0xFFB3BED3), fontSize = 12.sp)
            Text(if (sortDescending) "Z–A  ↓" else "A–Z  ↑", color = Color(0xFFB9C9FF), fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onToggleSort).padding(horizontal = 12.dp, vertical = 7.dp))
        }
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            items(filtered, key = { it.packageName }) { app ->
                AppIcon(app = app, onClick = { onLaunchApp(app.packageName) }, onLongClick = { selectedApp = app }, iconSize = iconSize, showLabel = showAppLabels, badgeCount = badgeCounts[app.packageName] ?: 0)
            }
        }
    }
    selectedApp?.let { app ->
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { selectedApp = null },
            title = { Text(app.label) },
            text = { Text(buildString {
                append(if (app.packageName in hiddenPackages) "This app is hidden from your home screen. " else if (app.isSystem) "System application. " else "Choose an action for this app. ")
                if (app.packageName in lockedPackages) append("Authentication is required before opening it.")
                else append("You can protect it with fingerprint, face, or device PIN/pattern where supported.")
            }) },
            confirmButton = {
                Row {
                    TextButton(onClick = { onToggleFavorite(app.packageName); selectedApp = null }) {
                        Text(if (app.packageName in favoritePackages) "Unfavorite" else "Favorite")
                    }
                    TextButton(onClick = { onToggleHidden(app.packageName); selectedApp = null }) {
                        Text(if (app.packageName in hiddenPackages) "Unhide" else "Hide")
                    }
                    TextButton(onClick = { onToggleAppLock(app.packageName); selectedApp = null }) {
                        Text(if (app.packageName in lockedPackages) "Unlock" else "App lock")
                    }
                    TextButton(onClick = {
                        selectedApp = null
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")))
                    }) { Text("App info") }
                }
            },
            dismissButton = {
                Row {
                    if (!app.isSystem) TextButton(onClick = {
                        selectedApp = null
                        context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")))
                    }) { Text("Uninstall") }
                    TextButton(onClick = { selectedApp = null }) { Text("Cancel") }
                }
            }
        )
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
