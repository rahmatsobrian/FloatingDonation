package siroha.floating.donation.model

import java.util.UUID

enum class OverlayType {
    WEB, IMAGE
}

data class OverlayConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val url: String = "",
    val type: OverlayType = OverlayType.WEB,
    val imagePath: String = "",
    val isActive: Boolean = false,
    val isLocked: Boolean = false,
    val isVisible: Boolean = true,
    val positionX: Int = 100,
    val positionY: Int = 100,
    val width: Int = 400,
    val height: Int = 300,
    val scale: Float = 1.0f,
    val opacity: Float = 1.0f,
    val keepPosition: Boolean = true,
    val alwaysOnTop: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
