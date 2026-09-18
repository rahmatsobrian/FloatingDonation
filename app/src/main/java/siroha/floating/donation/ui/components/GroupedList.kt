package siroha.floating.donation.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// Radius besar dipakai untuk sisi luar grup & untuk state pressed (semua sisi)
private val LargeCorner: Dp = 24.dp
// Radius kecil dipakai untuk sisi yang "nyambung" ke item tetangga dalam grup
private val SmallCorner: Dp = 4.dp
// Jarak tipis antar item supaya tiap card tetap kebaca terpisah,
// tapi pola radiusnya tetap terasa "satu keluarga" (grouped)
private val ItemSpacing: Dp = 3.dp

enum class GroupedItemPosition {
    FIRST, MIDDLE, LAST, ONLY
}

private data class CornerRadii(
    val topStart: Dp,
    val topEnd: Dp,
    val bottomStart: Dp,
    val bottomEnd: Dp
)

private fun baseCornersFor(position: GroupedItemPosition): CornerRadii = when (position) {
    // Atas rounded besar, bawah kecil (nyambung ke item berikutnya)
    GroupedItemPosition.FIRST -> CornerRadii(LargeCorner, LargeCorner, SmallCorner, SmallCorner)
    // Rounded kecil di semua sisi (nyambung ke atas & bawah)
    GroupedItemPosition.MIDDLE -> CornerRadii(SmallCorner, SmallCorner, SmallCorner, SmallCorner)
    // Atas kecil (nyambung ke item sebelumnya), bawah rounded besar
    GroupedItemPosition.LAST -> CornerRadii(SmallCorner, SmallCorner, LargeCorner, LargeCorner)
    // Satu-satunya item dalam grup, rounded besar semua sisi
    GroupedItemPosition.ONLY -> CornerRadii(LargeCorner, LargeCorner, LargeCorner, LargeCorner)
}

@Composable
fun GroupedListSection(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp, top = 16.dp)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(ItemSpacing)
        ) {
            content()
        }
    }
}

/**
 * Satu baris list ala M3 (lihat https://m3.material.io/components/lists/specs).
 * Bentuk sudutnya mengikuti posisi dalam grup (besar di sisi luar, kecil di sisi
 * yang nyambung ke tetangga), dan saat item ditekan/ditahan sudutnya animasi
 * membulat penuh di semua sisi lalu kembali ke bentuk semula saat dilepas.
 */
@Composable
fun GroupedListItem(
    position: GroupedItemPosition = GroupedItemPosition.ONLY,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val base = baseCornersFor(position)
    val scope = rememberCoroutineScope()

    val animSpec = tween<Dp>(durationMillis = 200)
    val topStart by animateDpAsState(if (isPressed) LargeCorner else base.topStart, animSpec, label = "topStart")
    val topEnd by animateDpAsState(if (isPressed) LargeCorner else base.topEnd, animSpec, label = "topEnd")
    val bottomStart by animateDpAsState(if (isPressed) LargeCorner else base.bottomStart, animSpec, label = "bottomStart")
    val bottomEnd by animateDpAsState(if (isPressed) LargeCorner else base.bottomEnd, animSpec, label = "bottomEnd")

    val shape = RoundedCornerShape(
        topStart = topStart,
        topEnd = topEnd,
        bottomStart = bottomStart,
        bottomEnd = bottomEnd
    )

    // clickable() delays emitting the Press interaction (so it can bail out if this
    // turns out to be a scroll rather than a tap), which is why the rounding used to
    // only show up on a long hold inside the LazyColumn. We emit Press/Release
    // ourselves via pointerInput so the animation starts right on finger-down.
    val pressModifier = if (onClick != null) {
        Modifier
            .indication(interactionSource, ripple())
            .pointerInput(onClick) {
                detectTapGestures(
                    onPress = { offset ->
                        val press = PressInteraction.Press(offset)
                        scope.launch { interactionSource.emit(press) }
                        val released = tryAwaitRelease()
                        scope.launch {
                            interactionSource.emit(
                                if (released) PressInteraction.Release(press) else PressInteraction.Cancel(press)
                            )
                        }
                    },
                    onTap = { onClick() }
                )
            }
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .then(pressModifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

fun getItemPosition(index: Int, totalCount: Int): GroupedItemPosition {
    return when {
        totalCount == 1 -> GroupedItemPosition.ONLY
        index == 0 -> GroupedItemPosition.FIRST
        index == totalCount - 1 -> GroupedItemPosition.LAST
        else -> GroupedItemPosition.MIDDLE
    }
}
