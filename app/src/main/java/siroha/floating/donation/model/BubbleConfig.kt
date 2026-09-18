package siroha.floating.donation.model

data class BubbleConfig(
    val positionX: Int = 0,
    val positionY: Int = 200,
    val size: Int = 56,
    val opacity: Float = 1.0f,
    val autoHide: Boolean = false,
    val snapToEdge: Boolean = true
)
