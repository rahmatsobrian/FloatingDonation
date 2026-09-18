package siroha.floating.donation.model

data class AppSettings(
    // Overlay defaults
    val defaultWidth: Int = 400,
    val defaultHeight: Int = 300,
    val defaultOpacity: Float = 1.0f,
    val defaultPositionX: Int = 100,
    val defaultPositionY: Int = 100,
    val rememberPosition: Boolean = true,
    val overlayAnimation: Boolean = true,

    // WebView
    val javaScriptEnabled: Boolean = true,
    val mediaPlaybackEnabled: Boolean = true,
    val cacheEnabled: Boolean = true,
    val cookiesEnabled: Boolean = true,

    // Bubble
    val bubbleConfig: BubbleConfig = BubbleConfig(),
    // Independen dari overlay — floating bubble bisa dinyalakan/dimatikan sendiri
    val bubbleEnabled: Boolean = true,

    // Notification
    val notificationControlsEnabled: Boolean = true,

    // Performance
    val hardwareAcceleration: Boolean = true,
    val webViewCache: Boolean = true,
    val reduceAnimation: Boolean = false,

    // Debug
    val debugLogging: Boolean = false,

    // Onboarding
    val onboardingCompleted: Boolean = false,

    // Max overlays (effectively unlimited)
    val maxOverlays: Int = Int.MAX_VALUE
)
