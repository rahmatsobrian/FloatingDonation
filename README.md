# Siroha Floating Donation

A lightweight Android floating overlay application that displays web-based donation alerts and image overlays on top of other apps and games.

## What It Does

Siroha Floating Donation is a **WebView overlay renderer/controller**. It is **NOT** a donation platform, payment system, or alert creator.

Users simply provide:
- **Overlay Name** — a label for the overlay
- **Overlay URL** — any HTTP/HTTPS URL (from Sociabuzz, Saweria, Trakteer, Streamlabs, StreamElements, or any custom URL)

The app opens the URL in a transparent floating WebView above other apps using Android's `SYSTEM_ALERT_WINDOW`.

## Features

### Core
- 🌐 **WebView Overlay** — Transparent floating WebView that renders any web URL
- 🖼️ **Image Overlay** — PNG, JPG, WEBP, and GIF image overlays with transparency support
- 🔒 **Lock/Unlock** — Lock mode passes touch through to apps below; Unlock mode allows overlay interaction
- 💬 **Floating Bubble** — Draggable bubble controller for quick overlay management
- 📱 **Notification Controls** — Show/Hide, Lock/Unlock, and Exit from the notification bar
- 🎯 **Per-App Layouts** — Save different overlay positions/sizes per app
- 📋 **Multi-Overlay** — Run up to 5 overlays simultaneously (configurable)
- 🎨 **Material 3 + Dynamic Color** — Modern UI following Material Design 3 guidelines

### Overlay Controls
- Drag to reposition
- Resize from bottom-right corner
- Adjustable opacity (0-100%)
- Show/Hide without stopping the service
- Reset position to defaults
- Screen boundary clamping (overlay never goes off-screen)
- Remember position across sessions

### Customization
- Width, Height, Position (X/Y)
- Opacity
- Lock/Unlock state
- Keep Position toggle
- Always On Top
- Per-overlay settings
- Overlay presets (Donation Alert, Media Share, Leaderboard, etc.)

### Settings
- **Overlay**: Default size, opacity, position, animations
- **WebView**: JavaScript, media playback, cache, cookies
- **Floating Bubble**: Size, opacity, auto-hide, snap-to-edge
- **Notification**: Control toggles
- **Performance**: Hardware acceleration, WebView cache, reduced animations
- **Debug**: Debug logging toggle

## Architecture

```
User
 ├── Overlay Name
 └── Overlay URL
          │
          ▼
   Siroha Floating Donation
          │
          ▼
      Android WebView
          │
          ▼
    Floating Window (SYSTEM_ALERT_WINDOW)
          │
          ▼
 Other Apps / Games
```

**No server. No backend. No API. No authentication.**

## Technical Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **Min SDK**: 29 (Android 10)
- **Target SDK**: 35
- **Build**: Gradle Kotlin DSL, JDK 17
- **Storage**: DataStore Preferences + Gson
- **Image Loading**: Coil (with GIF support)
- **Architecture**: Simple single-activity, no Hilt/Room/Navigation

## Project Structure

```
app/src/main/java/siroha/floating/donation/
├── MainActivity.kt              # Main activity & app composition
├── SirohaApplication.kt         # Application class
├── model/                       # Data models
│   ├── OverlayConfig.kt
│   ├── AppLayout.kt
│   ├── BubbleConfig.kt
│   ├── AppSettings.kt
│   ├── OverlayPreset.kt
│   └── OverlayStatus.kt
├── overlay/                     # Overlay engine
│   ├── OverlayWebView.kt       # WebView wrapper
│   ├── OverlayWindow.kt        # Floating window manager
│   └── FloatingBubble.kt       # Bubble controller
├── service/                     # Background service
│   ├── OverlayService.kt       # Foreground service
│   └── NotificationController.kt
├── storage/                     # Persistence
│   └── OverlayStorage.kt       # DataStore-based storage
├── ui/
│   ├── theme/Theme.kt          # Material 3 theme
│   ├── components/GroupedList.kt # Grouped list UI
│   └── screens/                 # UI screens
│       ├── OnboardingScreen.kt
│       ├── DashboardScreen.kt
│       ├── SettingsScreen.kt
│       ├── AppLayoutsScreen.kt
│       ├── AddEditOverlayDialog.kt
│       ├── AddImageOverlayDialog.kt
│       ├── OverlayCustomizationDialog.kt
│       └── AppLayoutDialog.kt
└── util/                        # Utilities
    ├── Logger.kt
    ├── UrlValidator.kt
    └── ScreenUtils.kt
```

## Permissions

| Permission | Purpose |
|---|---|
| `SYSTEM_ALERT_WINDOW` | Display overlay on top of other apps |
| `FOREGROUND_SERVICE` | Keep overlay service running |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Android 14+ foreground service type |
| `POST_NOTIFICATIONS` | Show notification controls |
| `INTERNET` | Load web overlay content |
| `ACCESS_NETWORK_STATE` | Check network connectivity |

## Lock / Unlock Behavior

### Unlocked
- Overlay can be dragged and resized
- Touch events are captured by the overlay
- Semi-transparent background shown for visibility
- Apps below do NOT receive touch

### Locked
- Overlay is display-only
- Touch events pass through to apps below
- No background indicator
- Game/app receives touch normally

## Building

### Prerequisites
- JDK 17
- Android SDK (compileSdk 35)

### Debug Build
```bash
./gradlew assembleDebug
```

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

### Debug Keystore

A debug keystore is generated automatically during CI builds. For local development, Gradle uses the default Android debug keystore, or you can generate a stable one:

```bash
mkdir -p keystore
keytool -genkeypair -v \
  -keystore keystore/debug.keystore \
  -alias androiddebugkey \
  -keyalg RSA -keysize 2048 -validity 36500 \
  -storepass android -keypass android \
  -dname "CN=Android Debug,O=Android,C=US"
```

> ⚠️ The debug keystore is for **development/testing only**. Never use it for production releases. A production release build should use a proper release signing configuration.

### Run Tests
```bash
./gradlew testDebugUnitTest
```

### Lint
```bash
./gradlew lintDebug
```

## CI/CD

GitHub Actions workflow at `.github/workflows/android.yml`:
- Triggers on push to `main`/`develop`, pull requests, and manual dispatch
- No repository secrets required
- Generates debug keystore automatically
- Runs unit tests, lint, and builds debug APK
- Uploads APK and reports as artifacts

## WebView Security

- Only HTTP/HTTPS URLs allowed
- SSL errors are not bypassed
- No JavaScript interface exposed
- No password saving
- No arbitrary file access
- Intent hijacking prevented
- URL validation on input

## Trade-offs & Notes

1. **Foreground Service Type**: Uses `specialUse` type as overlay display doesn't fit standard categories. This may require justification during Play Store review.

2. **Per-App Layout Detection**: The per-app layout feature stores layouts by package name. The user manually configures which app gets which layout. Automatic app detection would require `UsageStats` permission.

3. **WebView Limitations**: Some overlay platforms may not render perfectly in Android WebView due to platform-specific JavaScript or CSS. This is a limitation of WebView, not the app.

4. **Multi-Overlay Limit**: Default limit of 5 simultaneous overlays to prevent excessive resource usage. Configurable in settings.

5. **Audio**: WebView audio playback depends on Android's audio focus and volume settings. The app does not attempt to bypass audio restrictions.

## License

This project is proprietary software. All rights reserved.
