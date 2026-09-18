package siroha.floating.donation.model

import java.util.UUID

data class AppLayout(
    val id: String = UUID.randomUUID().toString(),
    val overlayId: String = "",
    val packageName: String = "",
    val appName: String = "",
    val positionX: Int = 100,
    val positionY: Int = 100,
    val width: Int = 400,
    val height: Int = 300,
    val scale: Float = 1.0f,
    val opacity: Float = 1.0f,
    val isEnabled: Boolean = true
)
