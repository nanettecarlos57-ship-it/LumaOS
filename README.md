# LumaOS

**Your Android. A New Experience.**

LumaOS is a native Android launcher inspired by useful interaction patterns from multiple iOS generations. It is built with Kotlin and Jetpack Compose; it is not a WebView.

- **Package:** `com.lumaos.launcher`
- **Current version:** `0.2.0`
- **Builder:** Nation Hub Tech
- **Minimum Android version:** Android 6.0 (API 23)

## Current features

### Home screen and app organization
- Discovers and launches installed Android apps.
- iPhone-inspired rounded-square presentation using each installed app's original icon.
- Custom dock and configurable 3-, 4- or 5-column app grid.
- Adjustable icon size and app-label visibility.
- Persistent app ordering and user-created folders.
- Favorites, hide/unhide controls and app launch protection.
- App Library search, A–Z / Z–A sorting, and All / Downloaded / System / Favorites filters.
- Long-press app actions for Favorite, Hide, App lock, App info and (for non-system apps) Uninstall.

### Personalization and widgets
- Native Android home-screen widgets through `AppWidgetHost`.
- Built-in wallpaper gradients and user-selected photo wallpaper.
- Midnight, AMOLED Black, Royal Purple and Ocean Teal theme styles.
- System, Serif and Monospace font choices.
- Settings for icon size, grid columns and app labels.

### iOS-inspired panels
- Control Center-style panel with shortcuts to Wi-Fi, Bluetooth, Airplane mode, Location, Display, Sound, Battery and Android Settings.
- Dynamic Island-inspired expandable activity capsule.
- Lock Screen visual preview.
- Notification Center with recent notifications grouped by app, expandable/collapsible groups, per-app history clearing, single-item dismissal, notification opening and supported notification actions.
- Inline text replies when a notification exposes a free-form Android `RemoteInput` action.
- App icon notification badges based on active notifications.
- Optional call-history viewer with call type, number, time and duration.
- Default-launcher setup shortcut, user-selected file opening and an About page with version and Nation Hub Tech credit.

## Notification access and privacy

Notification features require the user to explicitly grant **Notification Access** in Android settings. LumaOS does not enable this access silently.

When access is granted, LumaOS reads notification titles, message text and available action metadata to show the recent-notification list, badges and supported actions. The recent list is held in memory (up to 60 items); this feature does not send notification content to an external service. Android may revoke access at any time.

- Tapping a notification opens it through the notification's own content action when one is provided.
- Action buttons run the actions supplied by the posting app.
- Text reply is offered only when the posting app supplies a free-form `RemoteInput`.
- Dismiss asks Android's notification listener service to cancel that individual active notification.
- **Clear history** and **Clear app** remove items from LumaOS's local recent list; they do not clear the posting app's Android notification shade entries.

## App lock, call history and platform permissions

App Lock uses AndroidX BiometricPrompt with supported biometrics and, on supported Android versions, the device credential. It protects app launches made through LumaOS only. It does not prevent the same app from being opened from another launcher, a notification, a deep link or another Android system surface.

Call History requests `READ_CALL_LOG` only when the user opens that feature. This permission is restricted by Google Play policy; publishing a build with call-log access may require an eligible permitted use and Play approval. Review the current Play policy before distribution.

File browsing uses Android's document picker and receives access only to the file the user selects. It does not request broad storage access.

## Android limitations

LumaOS is a third-party launcher, not a replacement Android operating system.

- The Dynamic Island and Lock Screen are in-launcher experiences. They do not replace Android's secure lock screen or create a system-wide live-activity overlay.
- Control Center tiles open the relevant Android settings pages. Protected system switches remain controlled by Android.
- Some widget, notification-action and biometric capabilities depend on the Android version, device manufacturer, posting app and user-granted permissions.

## Build and install

Open the repository in Android Studio, allow Gradle sync to complete, then run the `app` configuration on an emulator or Android device.

GitHub Actions builds a debug APK on pushes to `main` and uploads it as the `LumaOS-debug-apk` workflow artifact. A debug APK is for testing; it is not a Play Store release build.

## Next development areas

- Accessibility semantics, screen-reader support and larger-text testing.
- Automated UI and regression tests for launcher navigation, folders, widgets and notification actions.
- Performance, memory and battery profiling on a range of Android devices.
- Device/version-aware polish for gestures, animations and system-setting shortcuts.
- Release signing, Play policy review and a production-ready distribution workflow.
