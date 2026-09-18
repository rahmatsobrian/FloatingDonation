package siroha.floating.donation.model

data class OverlayPreset(
    val name: String,
    val description: String,
    val defaultWidth: Int = 400,
    val defaultHeight: Int = 300,
    val iconName: String = "web"
) {
    companion object {
        val presets = listOf(
            OverlayPreset(
                "Donation Alert",
                "Display donation alerts from streaming platforms",
                400, 200, "volunteer_activism"
            ),
            OverlayPreset(
                "Media Share",
                "Show media share overlays",
                500, 400, "play_circle"
            ),
            OverlayPreset(
                "Leaderboard",
                "Display donation leaderboard",
                350, 500, "leaderboard"
            ),
            OverlayPreset(
                "Camera Frame",
                "Overlay a camera frame or border",
                300, 300, "photo_camera"
            ),
            OverlayPreset(
                "Logo",
                "Display a logo overlay",
                200, 200, "branding_watermark"
            ),
            OverlayPreset(
                "Custom Web Overlay",
                "Any custom web overlay URL",
                400, 300, "web"
            )
        )
    }
}
