package siroha.floating.donation.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenUtilsTest {

    @Test
    fun `clampPosition keeps within bounds`() {
        val (x, y) = ScreenUtils.clampPosition(
            x = -10, y = -20, width = 100, height = 100,
            screenWidth = 1080, screenHeight = 1920
        )
        assertEquals(0, x)
        assertEquals(0, y)
    }

    @Test
    fun `clampPosition clamps to max`() {
        val (x, y) = ScreenUtils.clampPosition(
            x = 1000, y = 1900, width = 200, height = 200,
            screenWidth = 1080, screenHeight = 1920
        )
        assertEquals(880, x) // 1080 - 200
        assertEquals(1720, y) // 1920 - 200
    }

    @Test
    fun `clampPosition allows valid position`() {
        val (x, y) = ScreenUtils.clampPosition(
            x = 100, y = 200, width = 300, height = 300,
            screenWidth = 1080, screenHeight = 1920
        )
        assertEquals(100, x)
        assertEquals(200, y)
    }

    @Test
    fun `clampPosition handles overlay larger than screen`() {
        val (x, y) = ScreenUtils.clampPosition(
            x = 100, y = 200, width = 2000, height = 3000,
            screenWidth = 1080, screenHeight = 1920
        )
        assertEquals(0, x) // coerceAtLeast(0) on negative max
        assertEquals(0, y)
    }

    @Test
    fun `clampSize respects minimum`() {
        val (w, h) = ScreenUtils.clampSize(
            width = 50, height = 30,
            screenWidth = 1080, screenHeight = 1920
        )
        assertEquals(100, w) // min default
        assertEquals(100, h)
    }

    @Test
    fun `clampSize respects maximum screen size`() {
        val (w, h) = ScreenUtils.clampSize(
            width = 2000, height = 3000,
            screenWidth = 1080, screenHeight = 1920
        )
        assertEquals(1080, w)
        assertEquals(1920, h)
    }

    @Test
    fun `clampSize keeps valid size`() {
        val (w, h) = ScreenUtils.clampSize(
            width = 400, height = 300,
            screenWidth = 1080, screenHeight = 1920
        )
        assertEquals(400, w)
        assertEquals(300, h)
    }
}
