package com.lumaos.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

/**
 * User-enabled floating quick-access control. The overlay is shown only after
 * Android's explicit "display over other apps" permission has been granted.
 */
class AssistiveTouchOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var floatingButton: TextView? = null
    private var floatingParams: WindowManager.LayoutParams? = null
    private var menuView: View? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        startAsForegroundService()
        if (floatingButton == null) addFloatingButton()
        return START_STICKY
    }

    private fun startAsForegroundService() {
        val openLauncher = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_ASSISTIVE_ACTION, "home")
        val contentIntent = PendingIntent.getActivity(
            this,
            7201,
            openLauncher,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("LumaOS AssistiveTouch")
            .setContentText("Floating quick access is active")
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AssistiveTouch",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the user-enabled LumaOS floating shortcut available"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun addFloatingButton() {
        val size = dp(56)
        val button = TextView(this).apply {
            text = "◉"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(224, 54, 65, 86))
                setStroke(dp(1), Color.argb(190, 255, 255, 255))
            }
            elevation = dp(8).toFloat()
            contentDescription = "LumaOS AssistiveTouch"
        }
        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (resources.displayMetrics.widthPixels - size - dp(18)).coerceAtLeast(0)
            y = (resources.displayMetrics.heightPixels / 2 - size / 2).coerceAtLeast(dp(24))
        }

        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var moved = false

        button.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).toInt()
                    val dy = (event.rawY - downRawY).toInt()
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) moved = true
                    if (moved) {
                        val maxX = (resources.displayMetrics.widthPixels - size).coerceAtLeast(0)
                        val maxY = (resources.displayMetrics.heightPixels - size).coerceAtLeast(0)
                        params.x = (startX + dx).coerceIn(0, maxX)
                        params.y = (startY + dy).coerceIn(0, maxY)
                        runCatching { windowManager.updateViewLayout(view, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) showMenu()
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> true
            }
        }

        runCatching {
            windowManager.addView(button, params)
            floatingButton = button
            floatingParams = params
        }.onFailure {
            Toast.makeText(this, "LumaOS could not display the floating button.", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun showMenu() {
        if (menuView != null) return

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(116, 5, 8, 16))
            isClickable = true
            setOnClickListener { dismissMenu() }
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(16))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(48, 57, 76), Color.rgb(22, 28, 42))
            ).apply {
                cornerRadius = dp(28).toFloat()
                setStroke(dp(1), Color.argb(90, 255, 255, 255))
            }
            isClickable = true
            setOnClickListener { }
        }

        val heading = TextView(this).apply {
            text = "AssistiveTouch"
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val subtitle = TextView(this).apply {
            text = "LumaOS quick access"
            textSize = 12f
            setTextColor(Color.rgb(195, 205, 224))
            setPadding(0, dp(3), 0, dp(14))
        }
        val done = TextView(this).apply {
            text = "Done"
            textSize = 13f
            setTextColor(Color.rgb(190, 207, 255))
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(10))
            setOnClickListener { dismissMenu() }
        }
        card.addView(heading)
        card.addView(subtitle)
        card.addView(done)

        val firstRow = actionRow(
            Triple("⌂", "Home", "home"),
            Triple("◉", "Control Centre", "control_center"),
            Triple("▤", "Notifications", "notifications")
        )
        val secondRow = actionRow(
            Triple("◐", "Lock Screen", "lock_screen"),
            Triple("▦", "Widgets", "widgets"),
            Triple("⚙", "Settings", "settings")
        )
        card.addView(firstRow)
        card.addView(secondRow)

        root.addView(
            card,
            FrameLayout.LayoutParams(
                (resources.displayMetrics.widthPixels - dp(36)).coerceAtMost(dp(340)),
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        runCatching {
            windowManager.addView(root, params)
            menuView = root
        }.onFailure {
            Toast.makeText(this, "Unable to open AssistiveTouch.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun actionRow(
        first: Triple<String, String, String>,
        second: Triple<String, String, String>,
        third: Triple<String, String, String>
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            listOf(first, second, third).forEach { action ->
                addView(
                    actionTile(action.first, action.second, action.third),
                    LinearLayout.LayoutParams(0, dp(78), 1f).apply {
                        setMargins(dp(4), dp(4), dp(4), dp(4))
                    }
                )
            }
        }
    }

    private fun actionTile(symbol: String, label: String, action: String): TextView {
        return TextView(this).apply {
            text = "$symbol\n$label"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(Color.argb(150, 88, 101, 126))
                setStroke(dp(1), Color.argb(48, 255, 255, 255))
            }
            isClickable = true
            isFocusable = false
            setOnClickListener { dispatchAction(action) }
        }
    }

    private fun dispatchAction(action: String) {
        dismissMenu()
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_ASSISTIVE_ACTION, action)
        runCatching { startActivity(intent) }
            .onFailure { Toast.makeText(this, "Unable to open that LumaOS screen.", Toast.LENGTH_SHORT).show() }
    }

    private fun dismissMenu() {
        val view = menuView ?: return
        menuView = null
        runCatching { windowManager.removeView(view) }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        dismissMenu()
        floatingButton?.let { view -> runCatching { windowManager.removeView(view) } }
        floatingButton = null
        floatingParams = null
        stopForeground(true)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "luma_assistive_touch"
        private const val NOTIFICATION_ID = 7202
    }
}
