package com.lumaos.launcher

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

data class LumaNotification(
    val id: Long,
    val appName: String,
    val title: String,
    val message: String,
    val time: String
)

object NotificationStore {
    val notifications = mutableStateListOf<LumaNotification>()
    private val ids = AtomicLong(0)

    fun add(item: LumaNotification) {
        notifications.removeAll { it.id == item.id }
        notifications.add(0, item)
        while (notifications.size > 60) notifications.removeAt(notifications.lastIndex)
    }

    fun clear() = notifications.clear()
    fun nextId(): Long = ids.incrementAndGet()
}

class LumaNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        activeNotifications?.forEach(::publishNotification)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        publishNotification(sbn)
    }

    private fun publishNotification(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val packageName = sbn.packageName
        val appName = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val message = (
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)
            )?.toString().orEmpty()

        if (title.isBlank() && message.isBlank()) return

        NotificationStore.add(
            LumaNotification(
                id = sbn.key.hashCode().toLong(),
                appName = appName,
                title = title,
                message = message,
                time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(sbn.postTime))
            )
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Keep the notification in the center as a recent item until the user clears it.
    }
}
