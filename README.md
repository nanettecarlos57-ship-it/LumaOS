# LumaOS

**Your Android. A New Experience.**

LumaOS is an original native Android launcher inspired by useful interaction patterns from multiple iOS generations. It is built with Kotlin and Jetpack Compose; it is not a WebView.

## Implemented

- Native home screen that discovers and launches installed Android apps
- iPhone-inspired rounded-square app icon presentation and dock
- App Library with app search
- Persistent home-screen icon ordering and user-created folders
- Control Center-style panel with shortcuts to Android system settings
- Dynamic Island-inspired expandable activity capsule inside the launcher
- Lock Screen visual preview
- Notification Center backed by Android's notification-listener service
- GitHub Actions workflow that builds and uploads a debug APK

## Notification access

To show notifications from other apps, open Notification Center in LumaOS and choose **Enable notification access**. Android will open its system settings so the user can explicitly grant or revoke access. Notification contents are sensitive; LumaOS does not enable this access silently.

## Android support and platform limits

The project targets Android 6.0 (API 23) and newer. Individual capabilities depend on the Android version, device manufacturer and user-granted permissions.

LumaOS is a third-party launcher. Its Dynamic Island and Lock Screen screens are in-launcher experiences; they do not replace Android's secure lock screen or create a system-wide overlay. Protected system controls continue to be managed by Android.

## Build

Open the repository in Android Studio, allow Gradle sync to complete, then run the `app` configuration on an emulator or Android device. GitHub Actions also builds a debug APK on pushes to `main`.

## Next development areas

- Home-screen widgets and wallpaper selection
- More flexible icon, grid, dock and theme customization
- Better notification grouping and notification actions
- Additional Android-version-aware quick controls
- Accessibility, performance and device testing
- Optional premium customization without putting ads on core launcher surfaces
