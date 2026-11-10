package com.lumaos.launcher

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LumaNotificationAction(
    val title: String,
    val pendingIntent: PendingIntent,
    val remoteInputs: List<RemoteInput>
)

data class LumaNotification(
    val id: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val message: String,
    val time: String,
    val contentIntent: PendingIntent?,
    val actions: List<LumaNotificationAction>
)

object NotificationStore {
    val notifications = mutableStateListOf<LumaNotification>()
    val badgeCounts = mutableStateMapOf<String, Int>()
    private var serviceRef: WeakReference<LumaNotificationListener>? = null

    fun attach(service: LumaNotificationListener) {
        serviceRef = WeakReference(service)
    }

    fun detach(service: LumaNotificationListener) {
        if (serviceRef?.get() === service) serviceRef = null
    }

    fun add(item: LumaNotification) {
        notifications.removeAll { it.id == item.id }
        notifications.add(0, item)
        while (notifications.size > 60) notifications.removeAt(notifications.lastIndex)
    }

    fun dismiss(key: String) {
        serviceRef?.get()?.dismissFromCenter(key)
        notifications.removeAll { it.id == key }
    }

    fun clearApp(packageName: String) {
        notifications.removeAll { it.packageName == packageName }
    }

    fun clear() = notifications.clear()

    fun updateBadgeCounts(active: Array<StatusBarNotification>?) {
        val counts = active.orEmpty()
            .filter { it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
            .groupingBy { it.packageName }
            .eachCount()
        badgeCounts.clear()
        badgeCounts.putAll(counts)
    }
}

class LumaNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationStore.attach(this)
        val active = activeNotifications
        active?.forEach(::publishNotification)
        NotificationStore.updateBadgeCounts(active)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        publishNotification(sbn)
        NotificationStore.updateBadgeCounts(activeNotifications)
    }

    private fun publishNotification(sbn: StatusBarNotification) {
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = sbn.notification.extras
        val packageName = sbn.packageName
        val appName = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.joinToString("\n") { it.toString() }
            .orEmpty()
        val message = (
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)
            )?.toString()?.takeIf { it.isNotBlank() } ?: textLines

        if (title.isBlank() && message.isBlank()) return

        val actions = sbn.notification.actions.orEmpty()
            .mapNotNull { action ->
                val actionTitle = action.title?.toString()?.trim().orEmpty()
                val actionIntent = action.actionIntent ?: return@mapNotNull null
                if (actionTitle.isBlank()) return@mapNotNull null
                LumaNotificationAction(
                    title = actionTitle,
                    pendingIntent = actionIntent,
                    remoteInputs = action.remoteInputs?.toList().orEmpty()
                )
            }
            .take(3)

        NotificationStore.add(
            LumaNotification(
                id = sbn.key,
                packageName = packageName,
                appName = appName,
                title = title,
                message = message,
                time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(sbn.postTime)),
                contentIntent = sbn.notification.contentIntent,
                actions = actions
            )
        )
    }

    fun dismissFromCenter(key: String) {
        runCatching { cancelNotification(key) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Keep recent items in the local history until the user clears or dismisses them.
        NotificationStore.updateBadgeCounts(activeNotifications)
    }

    override fun onDestroy() {
        NotificationStore.detach(this)
        super.onDestroy()
    }
}
